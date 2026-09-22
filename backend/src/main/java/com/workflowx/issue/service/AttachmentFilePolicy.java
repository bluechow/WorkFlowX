package com.workflowx.issue.service;

import com.workflowx.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 附件文件安全策略（P8-08; ADR-018）。
 * 安全基线:
 * - 不信任 Content-Type（仅作元数据记录）；放行依据 = 扩展名白名单
 * - 白名单外（exe/dll/bat/cmd/sh/js/html/svg 等）一律 422
 * - 文件名: 去路径分量、去控制字符、非空、限长（服务端截断），原始名不参与对象键
 * - objectKey 服务端生成: issues/{issueId}/{uuid}-{safeName}（uuid 防碰撞，safeName 限长保 VARCHAR(200)）
 * - 大小: 0 < size <= attachment.max-size-bytes（超限 413）
 */
@Component
@RequiredArgsConstructor
public class AttachmentFilePolicy {

    /** 上传白名单（ADR-018）：文档/图片/压缩包；刻意排除可执行与脚本类型 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "txt", "doc", "docx", "xls", "xlsx", "zip");

    /** 清洗后文件名长度上限（object_key VARCHAR(200) 预算内） */
    private static final int SAFE_NAME_MAX = 120;

    private final AttachmentProperties properties;

    /** 校验大小并返回通过清洗的文件名；任何不合规都抛业务异常（413/422）。 */
    public ValidatedFile validate(long size, String originalFilename, String declaredContentType) {
        if (size <= 0) {
            throw new BusinessException(422, "附件不能为空文件");
        }
        if (size > properties.getMaxSizeBytes()) {
            throw new BusinessException(413, "附件超过大小限制（" + (properties.getMaxSizeBytes() / 1024 / 1024) + "MB）");
        }
        String safeName = sanitizeFilename(originalFilename);
        String extension = extractExtension(safeName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(422, "不支持的附件类型: " + extension);
        }
        return new ValidatedFile(safeName, extension, size,
                declaredContentType == null || declaredContentType.isBlank() ? "application/octet-stream" : declaredContentType);
    }

    /** 生成服务端对象键：issues/{issueId}/{uuid}-{safeName}。 */
    public String buildObjectKey(Long issueId, String safeName) {
        return "issues/" + issueId + "/" + UUID.randomUUID() + "-" + safeName;
    }

    /** 去路径分量与控制字符、trim、限长；清洗后为空即拒绝（防空 filename/纯路径攻击）。 */
    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            throw new BusinessException(422, "附件文件名不能为空");
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\x00-\\x1f\\x7f]", "");
        name = name.trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            throw new BusinessException(422, "附件文件名非法");
        }
        if (name.length() > SAFE_NAME_MAX) {
            String ext = extractExtension(name);
            String stem = name.substring(0, SAFE_NAME_MAX - ext.length() - 1);
            name = stem + "." + ext;
        }
        return name;
    }

    private String extractExtension(String safeName) {
        int dot = safeName.lastIndexOf('.');
        if (dot < 0 || dot == safeName.length() - 1) {
            throw new BusinessException(422, "附件缺少扩展名");
        }
        return safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record ValidatedFile(String safeName, String extension, long size, String contentType) {
    }
}
