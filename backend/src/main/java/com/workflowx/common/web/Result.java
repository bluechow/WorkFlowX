package com.workflowx.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.workflowx.common.trace.TraceIdFilter;

import java.time.Instant;

/**
 * 统一响应结构（Master Prompt §9 / ADR-005）。
 * code 与 HTTP 状态码语义一致：成功 200，其余使用真实 HTTP 状态码（400/401/403/404/409/422/429/500）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Result<T>(int code, String message, T data, Instant timestamp, String traceId) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data, Instant.now(), TraceIdFilter.currentTraceId());
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> of(int code, String message, T data) {
        return new Result<>(code, message, data, Instant.now(), TraceIdFilter.currentTraceId());
    }
}
