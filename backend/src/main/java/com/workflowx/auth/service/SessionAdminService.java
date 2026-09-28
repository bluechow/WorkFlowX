package com.workflowx.auth.service;

import com.workflowx.auth.dto.SessionVO;

import java.util.List;

/**
 * 在线会话管理（Phase A-⑤）：ADMIN 视角查看/踢下线。
 * 数据源为 Redis 单会话键（auth:session:{userId}），踢下线即删键——
 * 被踢用户的下一个请求将因会话缺失得到 401（fail-closed，P2-07）。
 */
public interface SessionAdminService {

    /** 列出全部在线会话（含剩余有效期与 self 标记） */
    List<SessionVO> listActiveSessions(Long operatorId);

    /** 踢下线：删除目标用户会话；不能踢自己（400） */
    void kick(Long operatorId, Long targetUserId);
}
