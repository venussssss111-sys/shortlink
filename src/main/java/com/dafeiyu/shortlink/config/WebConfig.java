package com.dafeiyu.shortlink.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Value("${shortlink.upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // 只拦后端接口；短链跳转 /{code} 不在 /api 下，不受影响
                .addPathPatterns("/api/**")
                // 健康检查要免鉴权：Docker 探针拿不到 token
                .excludePathPatterns("/api/auth/login", "/api/auth/register", "/api/health")
                // 先鉴权再限流：限流的 key 要用到 UserContext 里的 userId
                .order(1);

        registry.addInterceptor(rateLimitInterceptor)
                // 登录/注册不能 exclude：它们才是最需要限流的（防密码爆破）
                .addPathPatterns("/api/**")
                // 探针被限流 → 容器被误判不健康 → 反复重启
                .excludePathPatterns("/api/health")
                .order(2);
    }

    /**
     * 把本地上传目录映射成静态资源：/uploads/avatar/xxx.png → {uploadDir}/avatar/xxx.png
     *
     * 生产环境应该换成对象存储（OSS / S3 / MinIO），本地磁盘在多实例部署时会打架。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            // 启动时就把目录建出来：目录不存在时 toUri() 不会补结尾的 "/"，
            // 而资源映射必须以 "/" 结尾，否则 URI.resolve() 会把最后一段替换掉（经典坑）
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("上传目录创建失败: " + dir, e);
        }
        String location = dir.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
