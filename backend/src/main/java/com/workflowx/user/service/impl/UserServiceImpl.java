package com.workflowx.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.security.AuthSessionService;
import com.workflowx.common.web.PageVO;
import com.workflowx.user.dto.CreateUserRequest;
import com.workflowx.user.dto.UpdateUserRequest;
import com.workflowx.user.dto.UserPageQuery;
import com.workflowx.user.entity.User;
import com.workflowx.user.entity.UserStatus;
import com.workflowx.user.mapper.UserMapper;
import com.workflowx.user.service.PasswordService;
import com.workflowx.user.service.UserService;
import com.workflowx.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户业务服务实现（P2-03）。
 * 异常约定: 资源缺失 → ResourceNotFoundException(404)；唯一性冲突 → BusinessException(409)；
 * 数据库唯一约束作为并发下的最终防线，DuplicateKeyException 精准转换为 409，其余数据库异常继续上抛。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordService passwordService;
    private final AuthSessionService authSessionService;
    private final com.workflowx.audit.service.AuditService auditService;

    @Override
    public UserVO getById(Long id) {
        return UserVO.from(requireUser(id));
    }

    @Override
    public PageVO<UserVO> page(UserPageQuery query) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>();
        String keyword = query.keyword();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getEmail, keyword)
                    .or().like(User::getNickname, keyword));
        }
        if (query.status() != null) {
            wrapper.eq(User::getStatus, query.status());
        }
        // 稳定排序（与 P2-02 分页行为一致）
        wrapper.orderByDesc(User::getCreatedAt).orderByDesc(User::getId);

        Page<User> page = userMapper.selectPage(new Page<>(query.pageNum(), query.pageSize()), wrapper);
        return PageVO.of(page.convert(UserVO::from));
    }

    @Override
    @Transactional
    public UserVO create(CreateUserRequest request) {
        checkAvailable(User::getUsername, request.username(), null, "username");
        checkAvailable(User::getEmail, request.email(), null, "email");

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordService.encode(request.password()));
        user.setNickname(request.nickname());
        user.setStatus(UserStatus.ACTIVE);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发窗口下 DB 唯一约束兜底；仅吞唯一键冲突，其余数据库异常继续上抛
            throw new BusinessException(409, "用户信息与已有记录冲突");
        }
        auditService.record("USER", "CREATE", "user:" + user.getId(),
                "创建用户 " + request.username(), true, null);
        // 重新查询以带回 DB 维护的 created_at/updated_at
        return UserVO.from(requireUser(user.getId()));
    }

    @Override
    @Transactional
    public UserVO update(Long id, UpdateUserRequest request) {
        User user = requireUser(id);
        if (StringUtils.hasText(request.email())) {
            checkAvailable(User::getEmail, request.email(), id, "email");
            user.setEmail(request.email());
        }
        if (request.nickname() != null) {
            user.setNickname(request.nickname());
        }
        try {
            userMapper.updateById(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "用户信息与已有记录冲突");
        }
        return UserVO.from(requireUser(id));
    }

    @Override
    @Transactional
    public UserVO updateStatus(Long operatorId, Long targetUserId, UserStatus status) {
        // 防误操作规则（AI_TASKS P2-12 已记录）: 不能修改自己的状态
        if (operatorId != null && operatorId.equals(targetUserId)) {
            throw new BusinessException(400, "不能修改自己的状态");
        }
        User user = requireUser(targetUserId);
        user.setStatus(status);
        userMapper.updateById(user);
        // 禁用即踢线: 状态转为 DISABLED 时删除目标用户 Redis 会话，其现有 JWT 立即失效（P2-12 核心）
        if (status == UserStatus.DISABLED) {
            authSessionService.deleteSession(targetUserId);
        }
        // 恢复 ACTIVE 不自动创建会话，用户须重新登录
        auditService.record("USER", "STATUS", "user:" + targetUserId,
                "用户状态变更为 " + status, true, operatorId);
        return UserVO.from(requireUser(targetUserId));
    }

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResourceNotFoundException("user", id);
        }
        return user;
    }

    /** 唯一性预检查（更新场景排除自身；DB UNIQUE 为并发最终防线） */
    private void checkAvailable(SFunction<User, ?> column, String value, Long excludeId, String field) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().eq(column, value);
        if (excludeId != null) {
            wrapper.ne(User::getId, excludeId);
        }
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(409, field + " 已存在: " + value);
        }
    }
}
