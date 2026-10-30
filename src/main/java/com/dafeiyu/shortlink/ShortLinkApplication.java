package com.dafeiyu.shortlink;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 短链接服务启动类
 */
@SpringBootApplication
@MapperScan("com.dafeiyu.shortlink.mapper")
@EnableScheduling   // 布隆过滤器每 30 秒重建一次，靠这个注解打开定时任务
public class ShortLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortLinkApplication.class, args);
    }
}
