package com.workflowx.issue.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.PageVO;
import com.workflowx.common.web.Result;
import com.workflowx.issue.dto.AttachmentPageQuery;
import com.workflowx.issue.service.AttachmentService;
import com.workflowx.issue.vo.AttachmentVO;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Issue 附件接口（P8-09~11; ADR-018）。
 * 上传 multipart/form-data（字段 file）；下载由后端鉴权后流式读取 MinIO（不暴露对象存储地址、不用永久 URL）。
 * 权限三层: attachment:* authority（V12）+ 项目成员数据级 + 上传者本人 ownership（删除）。
 */
@Tag(name = "Issue Attachments", description = "Issue 附件（P8，元数据入库/二进制存 MinIO）")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/issues/{issueId}/attachments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping
    @PreAuthorize("hasAuthority('attachment:upload')")
    public ResponseEntity<Result<AttachmentVO>> upload(@PathVariable Long projectId, @PathVariable Long issueId,
                                                       @RequestPart("file") MultipartFile file,
                                                       @AuthenticationPrincipal JwtPayload operator) {
        AttachmentVO created = attachmentService.upload(projectId, issueId, file, operator.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.of(201, "created", created));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('attachment:list')")
    public Result<PageVO<AttachmentVO>> page(@PathVariable Long projectId, @PathVariable Long issueId,
                                             @Valid AttachmentPageQuery query,
                                             @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(attachmentService.page(projectId, issueId, query.pageNum(), query.pageSize(), operator.userId()));
    }

    /** 后端鉴权后同步读取对象内容；filename* 按 RFC 5987 编码以支持中文文件名。
     * 同步 byte[]（上限 attachment.max-size-bytes）：避免 StreamingResponseBody 异步写出污染 keep-alive 连接。 */
    @GetMapping("/{attachmentId}/download")
    @PreAuthorize("hasAuthority('attachment:get')")
    public ResponseEntity<byte[]> download(@PathVariable Long projectId, @PathVariable Long issueId,
                                           @PathVariable Long attachmentId,
                                           @AuthenticationPrincipal JwtPayload operator) {
        AttachmentService.DownloadResult download =
                attachmentService.download(projectId, issueId, attachmentId, operator.userId());
        String encodedName = URLEncoder.encode(download.metadata().fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        byte[] content;
        try (var in = download.object().stream()) {
            content = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("读取附件内容失败: " + e.getMessage(), e);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .contentLength(content.length)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAuthority('attachment:delete')")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long issueId,
                               @PathVariable Long attachmentId,
                               @AuthenticationPrincipal JwtPayload operator) {
        attachmentService.delete(projectId, issueId, attachmentId, operator.userId());
        return Result.ok(null);
    }
}
