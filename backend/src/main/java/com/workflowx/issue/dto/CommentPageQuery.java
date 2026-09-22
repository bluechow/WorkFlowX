package com.workflowx.issue.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 评论分页参数（P8-04）；排序固定 created_at ASC（会话时间线）。 */
public record CommentPageQuery(
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
