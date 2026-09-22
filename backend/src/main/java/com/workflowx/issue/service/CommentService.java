package com.workflowx.issue.service;

import com.workflowx.common.web.PageVO;
import com.workflowx.issue.dto.CreateCommentRequest;
import com.workflowx.issue.dto.UpdateCommentRequest;
import com.workflowx.issue.vo.CommentVO;

/**
 * Issue 评论领域能力（P8-03；ADR-018）。
 * 权限双层: comment:* authority + 项目成员数据级；编辑/删除第三层为作者本人（ADMIN 不豁免，对齐 Phase 4/5 ownership 先例）。
 * 异常约定: issue/comment 缺失或跨资源 404；数据级/ownership 403；校验 422。
 */
public interface CommentService {

    CommentVO create(Long projectId, Long issueId, CreateCommentRequest request, Long operatorId);

    PageVO<CommentVO> page(Long projectId, Long issueId, long page, long size, Long operatorId);

    CommentVO getById(Long projectId, Long issueId, Long commentId, Long operatorId);

    CommentVO update(Long projectId, Long issueId, Long commentId, UpdateCommentRequest request, Long operatorId);

    void delete(Long projectId, Long issueId, Long commentId, Long operatorId);
}
