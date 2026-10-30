package com.dafeiyu.shortlink.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dafeiyu.shortlink.common.BizException;
import com.dafeiyu.shortlink.common.Result;
import com.dafeiyu.shortlink.dto.CreateLinkRequest;
import com.dafeiyu.shortlink.dto.CreateLinkResponse;
import com.dafeiyu.shortlink.entity.ShortLink;
import com.dafeiyu.shortlink.service.ShortLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链管理接口。接口定义见 docs/openapi.yaml。
 */
@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinkController {

    private final ShortLinkService shortLinkService;

    @PostMapping
    public Result<CreateLinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        return Result.ok(shortLinkService.create(request));
    }

    @GetMapping
    public Result<IPage<ShortLink>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(shortLinkService.page(page, size, keyword));
    }

    @GetMapping("/{code}")
    public Result<ShortLink> detail(@PathVariable String code) {
        // 详情属于「管理视角」，必须校验归属；跳转（resolve）才是公开的
        return Result.ok(shortLinkService.getOwnedByCode(code));
    }

    @PatchMapping("/{code}/status")
    public Result<Void> updateStatus(@PathVariable String code, @RequestParam int status) {
        if (status != 0 && status != 1) {
            throw BizException.badRequest("status 只能是 0 或 1");
        }
        shortLinkService.updateStatus(code, status);
        return Result.ok();
    }

    @DeleteMapping("/{code}")
    public Result<Void> delete(@PathVariable String code) {
        shortLinkService.delete(code);
        return Result.ok();
    }
}
