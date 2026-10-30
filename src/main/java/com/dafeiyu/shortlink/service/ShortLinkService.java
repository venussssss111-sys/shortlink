package com.dafeiyu.shortlink.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dafeiyu.shortlink.dto.CreateLinkRequest;
import com.dafeiyu.shortlink.dto.CreateLinkResponse;
import com.dafeiyu.shortlink.entity.ShortLink;

public interface ShortLinkService {

    /** 创建短链 */
    CreateLinkResponse create(CreateLinkRequest request);

    /** 解析短码，返回可跳转的原始链接；不存在/停用/过期会抛 BizException */
    String resolve(String code);

    /** 按短码查询，查不到返回 null */
    ShortLink getByCode(String code);

    /** 管理视角：带归属校验，不是自己的直接 404 */
    ShortLink getOwnedByCode(String code);

    /** 分页查询，keyword 可选（按短码或原始链接模糊匹配） */
    IPage<ShortLink> page(int page, int size, String keyword);

    /** 启用(1) / 停用(0) */
    void updateStatus(String code, int status);

    /** 删除 */
    void delete(String code);
}
