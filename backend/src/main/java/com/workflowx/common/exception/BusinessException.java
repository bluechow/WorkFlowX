package com.workflowx.common.exception;

/** 业务规则异常，默认 400。 */
public class BusinessException extends BaseException {

    public BusinessException(String message) {
        super(400, message);
    }

    public BusinessException(int status, String message) {
        super(status, message);
    }
}
