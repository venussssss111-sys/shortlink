package com.dafeiyu.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dafeiyu.shortlink.common.BizException;
import com.dafeiyu.shortlink.common.UserContext;
import com.dafeiyu.shortlink.dto.ChangePasswordRequest;
import com.dafeiyu.shortlink.dto.LoginRequest;
import com.dafeiyu.shortlink.dto.LoginVO;
import com.dafeiyu.shortlink.dto.RegisterRequest;
import com.dafeiyu.shortlink.dto.UserVO;
import com.dafeiyu.shortlink.entity.User;
import com.dafeiyu.shortlink.mapper.UserMapper;
import com.dafeiyu.shortlink.service.AuthService;
import com.dafeiyu.shortlink.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 退出登录的黑名单前缀，value 随便，只靠 key 是否存在 */
    private static final String BLACK_PREFIX = "auth:black:";
    private static final long MAX_AVATAR_BYTES = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp");

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Value("${shortlink.upload.dir:./uploads}")
    private String uploadDir;

    // ------------------------------------------------------------------ 注册

    @Override
    public LoginVO register(RegisterRequest request) {
        // 先查一次是为了给出友好提示；唯一索引才是真正的防线（并发下只有数据库拦得住）
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw BizException.conflict("用户名已被占用");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        String salt = newSalt();
        user.setSalt(salt);
        user.setPassword(hash(salt, request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw BizException.conflict("用户名已被占用");
        }
        // 回查一次：created_at / updated_at 是数据库默认值，insert 不会回填到内存对象
        user = userMapper.selectById(user.getId());
        log.info("注册成功 username={}", user.getUsername());
        return issueToken(user);
    }

    // ------------------------------------------------------------------ 登录

    @Override
    public LoginVO login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        // 用户不存在和密码错误返回同一句话，不给撞库的人做用户枚举的机会
        if (user == null || !user.getPassword().equals(hash(user.getSalt(), request.getPassword()))) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        log.info("登录成功 username={}", user.getUsername());
        return issueToken(user);
    }

    @Override
    public void logout(String token) {
        JwtUtil.Claims claims = jwtUtil.verify(token);
        if (claims == null) {
            return;
        }
        long remain = claims.getExpireAt() - System.currentTimeMillis() / 1000;
        if (remain > 0) {
            // TTL 只给到 token 自己过期为止，黑名单不会无限涨
            redisTemplate.opsForValue().set(BLACK_PREFIX + claims.getJti(), "1", Duration.ofSeconds(remain));
        }
    }

    // ------------------------------------------------------------------ 我的

    @Override
    public UserVO me() {
        Long userId = UserContext.get();
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        return toVO(user);
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        Long userId = UserContext.get();
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        if (!user.getPassword().equals(hash(user.getSalt(), request.getOldPassword()))) {
            throw BizException.badRequest("原密码不对");
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw BizException.badRequest("新密码不能和原密码一样");
        }
        // 换密码必须换盐，否则同样的密码还是同一个 hash
        String salt = newSalt();
        user.setSalt(salt);
        user.setPassword(hash(salt, request.getNewPassword()));
        userMapper.updateById(user);
        log.info("修改密码 username={}", user.getUsername());
    }

    // ---------------------------------------------------------------- 头像

    @Override
    public String uploadAvatar(MultipartFile file) {
        Long userId = UserContext.get();
        if (userId == null) {
            throw BizException.unauthorized("未登录");
        }
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("文件为空");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw BizException.badRequest("头像不能超过 2MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw BizException.badRequest("只能上传图片");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw BizException.badRequest("只支持 png / jpg / jpeg / gif / webp");
        }
        // 文件名必须服务端生成：用上传者的文件名会有 ../ 穿越和覆盖风险
        String name = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
        Path dir = Paths.get(uploadDir, "avatar").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(name).toFile());
        } catch (IOException e) {
            log.error("头像保存失败", e);
            throw new BizException(500, "头像保存失败");
        }
        String url = "/uploads/avatar/" + name;
        User user = userMapper.selectById(userId);
        user.setAvatar(url);
        userMapper.updateById(user);
        return url;
    }

    // ---------------------------------------------------------------- 私有

    private LoginVO issueToken(User user) {
        boolean admin = user.getIsAdmin() != null && user.getIsAdmin() == 1;
        return new LoginVO(jwtUtil.sign(user.getId(), user.getUsername(), admin), toVO(user));
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    private static String newSalt() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String hash(String salt, String raw) {
        return DigestUtils.md5DigestAsHex((salt + raw).getBytes(StandardCharsets.UTF_8));
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
