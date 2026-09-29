package com.workflowx.notification.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 通知列表查询（P9-05）。read null=全部；true=已读；false=未读。
 * 稳定排序 created_at DESC, id DESC（服务端固定，不开放排序参数）。
 */
public record NotificationPageQuery(
        Boolean read,

        /** 类型筛选（FP-7「@我的」= ISSUE_MENTIONED；可空=全部） */
        String type,

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
