package com.dafeiyu.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户。注意：password 是 MD5(salt + 明文)，salt 每个用户独立。
 * 返回给前端时一定要把 password / salt 摘掉（用 UserVO，不要直接返回实体）。
 */
@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String salt;

    private String nickname;

    private String avatar;

    private String email;

    /** 1=管理员，可以跨用户查看别人的短链 */
    private Integer isAdmin;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
