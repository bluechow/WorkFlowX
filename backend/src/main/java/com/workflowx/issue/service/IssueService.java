package com.workflowx.issue.service;

import com.workflowx.common.web.PageVO;
import com.workflowx.issue.dto.CreateIssueRequest;
import com.workflowx.issue.dto.IssuePageQuery;
import com.workflowx.issue.dto.UpdateIssueRequest;
import com.workflowx.issue.vo.IssueVO;

/**
 * Issue 领域服务（P6-02）。
 * 数据级权限（ADR-016）: create/update/status/assignee 的操作者必须是**项目成员**（project_members）→ 403；
 * 读操作仅要求 issue:* authority。
 * 业务编号: issue_no 由服务端在事务内经 projects.issue_seq 行锁递增分配（禁止客户端传入）。
 * 状态: 本阶段仅枚举合法值校验，流转矩阵属 Phase 7（ADR-016）。
 */
public interface IssueService {

    /** 创建 Issue：reporter=operator；issue_no 事务内分配；assignee 须为项目成员；severity 仅 BUG */
    IssueVO create(Long projectId, CreateIssueRequest request, Long operatorId);

    /** 查询（校验 issue 归属于路径项目，防跨项目访问） */
    IssueVO getById(Long projectId, Long issueId);

    /** 分页查询（keyword=标题/描述，issueNo 精确，枚举与人员过滤，稳定排序） */
    PageVO<IssueVO> page(Long projectId, IssuePageQuery query);

    /** 看板查询（Phase A-②）：项目内全部 Issue，按最近活动排序，上限 500 条 */
    java.util.List<IssueVO> listForBoard(Long projectId);

    /** 更新：issueNo/projectId/reporterId 不可变；assignee 变更重新校验项目成员；severity 规则同创建 */
    IssueVO update(Long projectId, Long issueId, UpdateIssueRequest request, Long operatorId);

}
