package com.workflowx.common.exception;

/** 业务级校验失败（Bean Validation 之外的领域校验），422。 */
public class ValidationException extends BaseException {

    public ValidationException(String message) {
        super(422, message);
    }
}
