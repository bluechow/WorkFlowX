package com.workflowx.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.auth.dto.SessionVO;
import com.workflowx.auth.service.SessionAdminService;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.security.AuthSessionService;
import com.workflowx.common.security.AuthSessionServiceImpl;
import com.workflowx.rbac.service.UserRoleService;
import com.workflowx.user.entity.User;
import com.workflowx.user.mapper.UserMapper;
import com.workflowx.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 在线会话管理实现（Phase A-⑤）。
 * 扫描使用 SCAN（非 KEYS，不阻塞 Redis）；踢下线复用 AuthSessionService.deleteSession
 * （与登出/禁用踢线同一路径）；审计由 Controller 记录。
 */
@Service
@RequiredArgsConstructor
public class SessionAdminServiceImpl implements SessionAdminService {

    private final StringRedisTemplate redisTemplate;
    private final AuthSessionService authSessionService;
    private final UserMapper userMapper;
    private final UserRoleService userRoleService;

    @Override
    public List<SessionVO> listActiveSessions(Long operatorId) {
        List<Long> userIds = new ArrayList<>();
        // SCAN 增量遍历，避免 KEYS 阻塞；匹配 auth:session:{userId}
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(AuthSessionServiceImpl.SESSION_KEY_PREFIX + "*").count(200).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String id = key.substring(AuthSessionServiceImpl.SESSION_KEY_PREFIX.length());
                if (id.chars().allMatch(Character::isDigit)) {
                    userIds.add(Long.parseLong(id));
                }
            }
        } catch (DataAccessException e) {
            // Redis 不可达时按 fail-closed 语义应 401（Filter 层），此处列表查询直接上抛由全局处理
            throw e;
        }
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<SessionVO> result = new ArrayList<>();
        for (Long userId : userIds) {
            User user = users.get(userId);
            // 会话键存在但用户已删（清理窗口）：跳过，键会随 TTL 自然过期
            if (user == null) {
                continue;
            }
            Long ttl = redisTemplate.getExpire(AuthSessionServiceImpl.SESSION_KEY_PREFIX + userId);
            result.add(new SessionVO(
                    userId,
                    user.getUsername(),
                    user.getNickname(),
                    userRoleService.findRoleCodesByUserId(userId),
                    ttl,
                    userId.equals(operatorId)));
        }
        return result;
    }

    @Override
    public void kick(Long operatorId, Long targetUserId) {
        if (operatorId.equals(targetUserId)) {
            // 防误操作：踢自己等同自登出，走「退出登录」入口
            throw new BusinessException(400, "不能踢出自己的会话，请使用退出登录");
        }
        User user = userMapper.selectById(targetUserId);
        if (user == null) {
            throw new ResourceNotFoundException("user", targetUserId);
        }
        authSessionService.deleteSession(targetUserId);
    }
}
