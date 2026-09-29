package com.workflowx.issue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.storage.StorageService;
import com.workflowx.common.web.PageVO;
import com.workflowx.issue.entity.Attachment;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.mapper.AttachmentMapper;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.issue.service.AttachmentFilePolicy;
import com.workflowx.issue.service.AttachmentService;
import com.workflowx.issue.vo.AttachmentVO;
import com.workflowx.project.entity.Project;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.mapper.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 附件领域实现（P8-07~11; ADR-018）。
 * 校验链与评论一致: project 404 → issue 404 → 项目成员 403 → ownership 403（删除）。
 * 一致性:
 * - 上传: 校验 → 生成 objectKey → MinIO → insert 元数据；insert 失败补偿删对象，补偿失败记 ERROR（不吞异常）
 * - 删除: 先删 MinIO 对象（失败则元数据保留并抛错），再删元数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private final AttachmentMapper attachmentMapper;
    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final StorageService storageService;
    private final AttachmentFilePolicy filePolicy;
    private final com.workflowx.audit.service.AuditService auditService;
    private final com.workflowx.activity.service.ActivityService activityService;

    @Override
    public AttachmentVO upload(Long projectId, Long issueId, MultipartFile file, Long operatorId) {
        requireIssueInProject(projectId, issueId);
        requireProjectMembership(projectId, operatorId);
        if (file == null || file.isEmpty()) {
            throw new com.workflowx.common.exception.BusinessException(422, "附件内容不能为空");
        }
        AttachmentFilePolicy.ValidatedFile validated =
                filePolicy.validate(file.getSize(), file.getOriginalFilename(), file.getContentType());
        String objectKey = filePolicy.buildObjectKey(issueId, validated.safeName());

        try (var stream = file.getInputStream()) {
            storageService.upload(objectKey, stream, validated.size(), validated.contentType());
        } catch (IOException e) {
            throw new IllegalStateException("读取上传流失败: " + e.getMessage(), e);
        }

        Attachment attachment = new Attachment();
        attachment.setIssueId(issueId);
        attachment.setObjectKey(objectKey);
        attachment.setFileName(validated.safeName());
        attachment.setFileSize(validated.size());
        attachment.setContentType(validated.contentType());
        attachment.setUploaderId(operatorId);
        try {
            attachmentMapper.insert(attachment);
        } catch (RuntimeException e) {
            compensateOrphanObject(objectKey, e);
            throw e;
        }
        auditService.record("ATTACHMENT", "UPLOAD", "issue:" + issueId,
                "上传附件 " + validated.safeName() + "（" + validated.size() + "B）", true, operatorId);
        
        activityService.record(projectId, issueId, operatorId, com.workflowx.activity.entity.Activity.Action.ATTACHMENT,
                "issue:" + issueId, "上传了附件 " + validated.safeName());
return AttachmentVO.from(attachmentMapper.selectById(attachment.getId()));
    }

    @Override
    public PageVO<AttachmentVO> page(Long projectId, Long issueId, long page, long size, Long operatorId) {
        requireIssueInProject(projectId, issueId);
        requireProjectMembership(projectId, operatorId);
        LambdaQueryWrapper<Attachment> wrapper = new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getIssueId, issueId)
                .orderByAsc(Attachment::getCreatedAt)
                .orderByAsc(Attachment::getId);
        IPage<Attachment> result = attachmentMapper.selectPage(new Page<>(page, size), wrapper);
        return PageVO.of(result.convert(AttachmentVO::from));
    }

    @Override
    public DownloadResult download(Long projectId, Long issueId, Long attachmentId, Long operatorId) {
        requireIssueInProject(projectId, issueId);
        requireProjectMembership(projectId, operatorId);
        Attachment attachment = requireAttachmentInIssue(issueId, attachmentId);
        StorageService.StoredObject object = storageService.download(attachment.getObjectKey());
        return new DownloadResult(AttachmentVO.from(attachment), object);
    }

    @Override
    public void delete(Long projectId, Long issueId, Long attachmentId, Long operatorId) {
        requireIssueInProject(projectId, issueId);
        requireProjectMembership(projectId, operatorId);
        Attachment attachment = requireAttachmentInIssue(issueId, attachmentId);
        requireUploader(attachment, operatorId);
        // 先删对象: 对象删除失败 → 元数据保留（避免库内记录指向已失效文件的悬空语义），异常向上传播
        storageService.delete(attachment.getObjectKey());
        attachmentMapper.deleteById(attachmentId);
        auditService.record("ATTACHMENT", "DELETE", "issue:" + issueId,
                "删除附件 " + attachment.getFileName(), true, operatorId);
    }

    /** DB insert 失败时补偿删除已上传对象；补偿失败记 ERROR 日志（孤儿对象人工可查），不吞原始异常。 */
    private void compensateOrphanObject(String objectKey, RuntimeException cause) {
        try {
            storageService.delete(objectKey);
            log.warn("附件元数据写入失败，已补偿删除对象 {}", objectKey);
        } catch (RuntimeException cleanupError) {
            log.error("附件元数据写入失败且补偿删除对象失败，存在孤儿对象 {}: {}",
                    objectKey, cleanupError.getMessage(), cleanupError);
        }
    }

    /** attachment 必须属于指定 issue，跨 issue/跨项目访问一律 404。 */
    private Attachment requireAttachmentInIssue(Long issueId, Long attachmentId) {
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !attachment.getIssueId().equals(issueId)) {
            throw new ResourceNotFoundException("attachment", attachmentId);
        }
        return attachment;
    }

    /** ownership: 仅上传者本人可删除；ADMIN 非 owner 同样 403（ADR-018）。 */
    private void requireUploader(Attachment attachment, Long operatorId) {
        if (!attachment.getUploaderId().equals(operatorId)) {
            throw new ForbiddenException("仅附件上传者可删除该附件");
        }
    }

    private void requireIssueInProject(Long projectId, Long issueId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ResourceNotFoundException("project", projectId);
        }
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null || !issue.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("issue", issueId);
        }
    }

    private void requireProjectMembership(Long projectId, Long operatorId) {
        if (projectMemberMapper.findMember(projectId, operatorId) == null) {
            throw new ForbiddenException("仅项目成员可访问该项目的附件");
        }
    }
}
