package com.workflowx.user.service;

import com.workflowx.common.web.PageVO;
import com.workflowx.user.dto.CreateUserRequest;
import com.workflowx.user.dto.UpdateUserRequest;
import com.workflowx.user.dto.UserPageQuery;
import com.workflowx.user.entity.UserStatus;
import com.workflowx.user.vo.UserVO;

/**
 * 用户业务服务（P2-03）。
 * 业务校验（唯一性、存在性）与数据变更在此层完成；
 * 权限校验（谁能调用）属于 P2-05 之后的 Security/RBAC 层，本接口不掺业务外规则。
 * 本服务不提供物理删除用户（Master Prompt 约束）。
 */
public interface UserService {

    /** 按 ID 查询，不存在抛 ResourceNotFoundException(404) */
    UserVO getById(Long id);

    /** 分页查询：keyword 匹配 username/email/nickname，status 精确过滤，默认 created_at DESC, id DESC */
    PageVO<UserVO> page(UserPageQuery query);

    /** 创建用户：username/email 全局唯一（预检 + DB 唯一约束双防线），密码经 PasswordService 哈希 */
    UserVO create(CreateUserRequest request);

    /** 更新基本信息（仅 email/nickname；email 唯一性排除自身）；不允许改动 username/password */
    UserVO update(Long id, UpdateUserRequest request);

    /**
     * 更新状态（ACTIVE/DISABLED/LOCKED）。
     * 业务规则（P2-12，用户已确认）: 操作者不能修改自己的状态（operatorId == targetUserId → 400）；
     * 目标转为 DISABLED 时删除其 Redis 会话（禁用即踢线）；恢复 ACTIVE 不自动创建会话，需重新登录。
     * 允许 ADMIN 互禁；"最后一个可用 ADMIN 保护"暂不实现（记录为未决规则）。
     */
    UserVO updateStatus(Long operatorId, Long targetUserId, UserStatus status);
}
