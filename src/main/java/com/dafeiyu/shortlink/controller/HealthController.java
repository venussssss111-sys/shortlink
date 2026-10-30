package com.dafeiyu.shortlink.controller;

import com.dafeiyu.shortlink.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查。
 *
 * 为什么要单独一个接口：Docker 的 HEALTHCHECK、K8s 的 liveness/readiness 探针，
 * 都需要一个「服务真的活着」的信号。用 /api/links 不行——它要鉴权，探针拿不到 token。
 *
 * 为什么既不鉴权也不限流：探针每 15 秒打一次，如果被限流拒绝，
 * 容器会被判定为不健康然后被反复重启，越重启越糟。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("UP");
    }
}
