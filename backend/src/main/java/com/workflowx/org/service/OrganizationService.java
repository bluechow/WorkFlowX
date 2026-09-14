package com.workflowx.org.service;

import com.workflowx.common.web.PageVO;
import com.workflowx.org.dto.AddOrganizationMemberRequest;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.dto.UpdateOrganizationRequest;
import com.workflowx.org.vo.OrganizationMemberVO;
import com.workflowx.org.vo.OrganizationVO;

/**
 * 组织领域服务（P4-01 领域能力，P4-02 REST 化）。
 * 业务规则（ADR-013）: code 全局唯一；创建者即 OWNER（事务内写入成员表）；
 * 删除仅组织 OWNER（operatorId 校验，403）；成员管理 OWNER 不可移除/降级。
 */
public interface OrganizationService {

    /** 创建组织（operatorId 成为 OWNER），成员关系同事务写入 */
    OrganizationVO create(CreateOrganizationRequest request, Long operatorId);

    OrganizationVO getById(Long id);

    /** 分页查询（keyword 匹配 name/code） */
    PageVO<OrganizationVO> page(String keyword, int page, int size);

    OrganizationVO update(Long id, UpdateOrganizationRequest request);

    /** 删除组织（仅 OWNER；级联清理部门与成员） */
    void delete(Long id, Long operatorId);

    /** 添加成员（重复添加 409；部门必须属于该组织） */
    OrganizationMemberVO addMember(Long orgId, AddOrganizationMemberRequest request);

    /** 移除成员（OWNER 不可移除） */
    void removeMember(Long orgId, Long userId);

    /** 成员列表 */
    java.util.List<OrganizationMemberVO> listMembers(Long orgId);
}
