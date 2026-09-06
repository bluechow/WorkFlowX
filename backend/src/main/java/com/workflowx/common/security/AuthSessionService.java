package com.workflowx.common.security;

/**
 * 登录会话服务（P2-07）：单会话白名单模型。
 * Key: auth:session:{userId} → Value: 当前有效 JWT 的 jti，TTL 与 Access Token 一致。
 * 规则: 后登录覆盖先登录（旧 token 的 jti 不再匹配即失效）；不实现多设备会话列表。
 * 提供删除能力供 P2-09（登出）与后续禁用踢线复用。
 */
public interface AuthSessionService {

    /** 创建/覆盖用户会话（单次原子操作写入 value + TTL） */
    void createSession(Long userId, String jti);

    /** 获取当前会话 jti；无会话返回 null */
    String getSessionJti(Long userId);

    /** 判断给定 jti 是否为该用户当前有效会话（无会话/不匹配均返回 false） */
    boolean isCurrentSession(Long userId, String jti);

    /** 删除会话（登出/踢线） */
    void deleteSession(Long userId);
}
