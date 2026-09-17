package com.workflowx.issue.service;

import com.workflowx.issue.entity.IssueStatus;
import com.workflowx.issue.vo.IssueVO;

import java.util.Set;

/**
 * Issue Workflow 领域服务（P7-02，ADR-017）。
 * 正式矩阵: OPEN→IN_PROGRESS→RESOLVED→TESTING→CLOSED（主链）；
 *          TESTING→REOPENED；REOPENED→IN_PROGRESS（失败回路）。CLOSED 为唯一终态。
 * 并发: 条件 UPDATE（WHERE status = fromStatus），失败 409——不允许最后写入覆盖。
 * 数据级: 操作者须为项目成员（沿 ADR-016.4）。
 */
public interface WorkflowService {

    /** 执行状态流转：非法流转/同状态/并发冲突 → 409；成功返回更新后 Issue */
    IssueVO transition(Long projectId, Long issueId, IssueStatus fromStatus, IssueStatus toStatus, Long operatorId);

    /** 指定状态的合法目标集合（无出边返回空集） */
    Set<IssueStatus> allowedTargets(IssueStatus from);
}
