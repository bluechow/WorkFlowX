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

    /** 更新状态（ACTIVE/DISABLED/LOCKED）；仅改库内状态，不含登录联动逻辑（后续任务） */
    UserVO updateStatus(Long id, UserStatus status);
}
