package com.workflowx.user.dto;

import com.workflowx.user.entity.UserStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 用户分页查询条件（P2-03）。
 * keyword 匹配 username/email/nickname；分页参数约定与 docs/api/api-conventions.md 一致
 * （page 从 1 开始，size 默认 20、上限 100）。默认排序 created_at DESC, id DESC。
 */
public record UserPageQuery(
        String keyword,
        UserStatus status,
        @Min(value = 1, message = "page 最小为 1") Integer page,
        @Min(value = 1, message = "size 最小为 1")
        @Max(value = 100, message = "size 最大为 100") Integer size) {

    public long pageNum() {
        return page == null ? 1 : page;
    }

    public long pageSize() {
        return size == null ? 20 : size;
    }
}
