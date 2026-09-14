package com.workflowx.common.exception;

/** 已认证但违反数据级业务规则（如非资源所有者），403。与 Security 的 role/authority 拒绝语义一致。 */
public class ForbiddenException extends BaseException {

    public ForbiddenException(String message) {
        super(403, message);
    }
}
