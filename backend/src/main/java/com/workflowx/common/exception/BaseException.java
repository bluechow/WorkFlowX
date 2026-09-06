package com.workflowx.common.exception;

/**
 * 异常基类：携带 HTTP 状态码与对外安全的错误信息。
 * 所有对外异常消息不得包含堆栈、SQL、内部实现细节（Master Prompt §10）。
 */
public abstract class BaseException extends RuntimeException {

    private final int status;

    protected BaseException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
