package com.workflowx.common.exception;

/** 认证失败（未登录 / 凭证无效 / 过期），401。 */
public class AuthenticationException extends BaseException {

    public AuthenticationException(String message) {
        super(401, message);
    }
}
