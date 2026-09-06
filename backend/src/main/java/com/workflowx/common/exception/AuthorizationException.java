package com.workflowx.common.exception;

/** 授权失败（权限不足），403。 */
public class AuthorizationException extends BaseException {

    public AuthorizationException(String message) {
        super(403, message);
    }
}
