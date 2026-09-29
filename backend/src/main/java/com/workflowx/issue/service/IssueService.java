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

    /** 关联列表（V17）：含双向（OUTGOING/INCOMING）与对端摘要 */
    java.util.List<com.workflowx.issue.vo.IssueLinkVO> listLinks(Long projectId, Long issueId);

    /** 建立关联（V17）：同项目、禁自关联、重复 409；返回最新关联列表 */
    java.util.List<com.workflowx.issue.vo.IssueLinkVO> link(Long projectId, Long issueId,
            com.workflowx.issue.dto.CreateIssueLinkRequest request, Long operatorId);

    /** 解除关联（V17）：任一端成员可解除 */
    void unlink(Long projectId, Long issueId, Long linkId, Long operatorId);

    /** 全局工作项分页（V17）：跨项目视图（all/assigned/todo/created），携带项目标识 */
    com.workflowx.common.web.PageVO<com.workflowx.issue.vo.WorkItemVO> pageMyWorkItems(
            Long userId, com.workflowx.issue.dto.WorkItemPageQuery query);

    /** 我的待办（Phase A-④）：跨项目聚合指派给我的未完结 Issue（上限 20，优先级降序） */
    java.util.List<com.workflowx.issue.dto.TodoIssueVO> myTodoIssues(Long userId);

    /** 更新：issueNo/projectId/reporterId 不可变；assignee 变更重新校验项目成员；severity 规则同创建 */
    IssueVO update(Long projectId, Long issueId, UpdateIssueRequest request, Long operatorId);

}
