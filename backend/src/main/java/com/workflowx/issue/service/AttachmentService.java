package com.workflowx.issue.service;

import com.workflowx.common.storage.StorageService;
import com.workflowx.common.web.PageVO;
import com.workflowx.issue.vo.AttachmentVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * Issue 附件领域能力（P8-07~11；ADR-018）。
 * 权限双层: attachment:* authority + 项目成员数据级；删除第三层为上传者本人（ADMIN 不豁免）。
 * 一致性: 上传 MinIO 成功后写元数据，写库失败补偿删除对象；删除先删对象后删元数据（对象删除失败则元数据保留）。
 */
public interface AttachmentService {

    AttachmentVO upload(Long projectId, Long issueId, MultipartFile file, Long operatorId);

    PageVO<AttachmentVO> page(Long projectId, Long issueId, long page, long size, Long operatorId);

    DownloadResult download(Long projectId, Long issueId, Long attachmentId, Long operatorId);

    void delete(Long projectId, Long issueId, Long attachmentId, Long operatorId);

    /** 下载句柄：元数据 + 对象流（调用方负责关闭流）。 */
    record DownloadResult(AttachmentVO metadata, StorageService.StoredObject object) {
    }
}
