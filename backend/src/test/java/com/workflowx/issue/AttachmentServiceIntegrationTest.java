package com.workflowx.issue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.storage.StorageService;
import com.workflowx.common.web.PageVO;
import com.workflowx.issue.dto.CreateIssueRequest;
import com.workflowx.issue.entity.Attachment;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.entity.IssuePriority;
import com.workflowx.issue.entity.IssueType;
import com.workflowx.issue.mapper.AttachmentMapper;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.issue.service.AttachmentService;
import com.workflowx.issue.service.IssueService;
import com.workflowx.issue.vo.AttachmentVO;
import com.workflowx.issue.vo.IssueVO;
import com.workflowx.org.dto.AddOrganizationMemberRequest;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.entity.Organization;
import com.workflowx.org.mapper.OrganizationMapper;
import com.workflowx.org.service.OrganizationService;
import com.workflowx.project.dto.CreateProjectRequest;
import com.workflowx.project.entity.Project;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.service.ProjectMemberService;
import com.workflowx.project.service.ProjectService;
import com.workflowx.user.dto.CreateUserRequest;
import com.workflowx.user.entity.User;
import com.workflowx.user.mapper.UserMapper;
import com.workflowx.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Attachment 领域集成测试（P8-12）：真实 MySQL + 真实 MinIO（禁止 mock MinIO）。
 * 数据隔离: 项目 P8ATT key 前缀、组织 ORG_P8ATT_、用户 p8att_、文件名 p8att-*，用后清理（DB 级联 + MinIO 对象逐个删除并断言）。
 * 一致性断言: 上传后对象必须存在；删除后对象必须不存在（DB 元数据与 MinIO 对象一一核对）。
 */
@SpringBootTest
class AttachmentServiceIntegrationTest {

    private static final String KEY_PREFIX = "P8ATT";
    private static final String ORG_PREFIX = "ORG_P8ATT_";
    private static final String USER_PREFIX = "p8att_";

    @Autowired
    private AttachmentService attachmentService;
    @Autowired
    private StorageService storageService;
    @Autowired
    private IssueService issueService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectMemberService projectMemberService;
    @Autowired
    private OrganizationService organizationService;
    @Autowired
    private UserService userService;
    @Autowired
    private AttachmentMapper attachmentMapper;
    @Autowired
    private IssueMapper issueMapper;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private OrganizationMapper organizationMapper;
    @Autowired
    private UserMapper userMapper;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<String> createdObjectKeys = new ArrayList<>();
    private Long ownerId;
    private Long projectId;
    private Long issueId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        ownerId = createTestUser("owner");
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + "MAIN", ORG_PREFIX + "MAIN", null), ownerId);
        Long orgId = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + "MAIN")).getId();
        projectService.create(new CreateProjectRequest(
                "P8 附件项目", KEY_PREFIX + "MAIN", orgId, null), ownerId);
        projectId = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + "MAIN")).getId();
        IssueVO issue = issueService.create(projectId, new CreateIssueRequest(
                "P8-承载附件的 Issue", null, IssueType.TASK, IssuePriority.MEDIUM, null, null, null, null), ownerId);
        issueId = issue.id();
    }

    @AfterEach
    void cleanup() {
        // MinIO 测试对象逐个删除并断言不存在（bucket 无测试对象残留）
        for (String objectKey : createdObjectKeys) {
            storageService.delete(objectKey);
            assertFalse(storageService.exists(objectKey), "清理后对象不应存在: " + objectKey);
        }
        createdObjectKeys.clear();
        // DB: attachments 随 issue FK 级联删除，逐层回收上游
        issueMapper.delete(new LambdaQueryWrapper<Issue>().likeRight(Issue::getTitle, "P8-"));
        projectMapper.delete(new LambdaQueryWrapper<Project>().likeRight(Project::getKey, KEY_PREFIX));
        organizationMapper.delete(new LambdaQueryWrapper<Organization>().likeRight(Organization::getCode, ORG_PREFIX));
        userMapper.delete(new LambdaQueryWrapper<User>().likeRight(User::getUsername, USER_PREFIX));
        createdUserIds.clear();
        ownerId = null;
        projectId = null;
        issueId = null;
        memberId = null;
    }

    private Long createTestUser(String suffix) {
        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", "Attach@123", "att-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    private void addMember(Long userId) {
        // ADR-015: 组织成员前置——先入组织再加入项目
        organizationService.addMember(orgId(), new AddOrganizationMemberRequest(userId, "MEMBER", null));
        projectMemberService.addMember(projectId,
                new com.workflowx.project.dto.AddProjectMemberRequest(userId, "MEMBER"), ownerId);
    }

    private Long orgId() {
        return organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + "MAIN")).getId();
    }

    private AttachmentVO upload(byte[] bytes, String filename, String contentType) {
        AttachmentVO vo = attachmentService.upload(projectId, issueId,
                new MockMultipartFile("file", filename, contentType, bytes), ownerId);
        createdObjectKeys.add(vo.id() == null ? null : objectKeyOf(vo.id()));
        return vo;
    }

    private String objectKeyOf(Long attachmentId) {
        return attachmentMapper.selectById(attachmentId).getObjectKey();
    }

    // ===== 上传与元数据 =====

    @Test
    void uploadShouldStoreObjectAndMetadata() {
        byte[] payload = "p8att hello attachment".getBytes(StandardCharsets.UTF_8);
        AttachmentVO vo = upload(payload, "p8att-note.txt", "text/plain");

        assertEquals("p8att-note.txt", vo.fileName(), "原始文件名保留为元数据");
        assertEquals(payload.length, vo.fileSize());
        assertEquals(ownerId, vo.uploaderId(), "uploader 自动绑定操作者");
        assertTrue(vo.contentType().startsWith("text/"));

        String objectKey = objectKeyOf(vo.id());
        assertTrue(objectKey.startsWith("issues/" + issueId + "/"), "对象键服务端生成且按 Issue 前缀隔离");
        assertTrue(storageService.exists(objectKey), "DB 元数据与 MinIO 对象一致（对象存在）");
    }

    @Test
    void uploadShouldBindUploaderAndRejectOutsider() {
        Long outsider = createTestUser("outsider");
        assertThrows(ForbiddenException.class, () -> attachmentService.upload(projectId, issueId,
                new MockMultipartFile("file", "p8att-x.txt", "text/plain", "x".getBytes()), outsider));
    }

    // ===== 下载 =====

    @Test
    void downloadShouldReturnIdenticalBytes() {
        byte[] payload = "p8att binary \u00e9\u00e8 content 0123".getBytes(StandardCharsets.UTF_8);
        AttachmentVO vo = upload(payload, "p8att-data.txt", "text/plain");
        AttachmentService.DownloadResult download =
                attachmentService.download(projectId, issueId, vo.id(), ownerId);
        try (var in = download.object().stream()) {
            assertArrayEquals(payload, in.readAllBytes(), "下载内容与上传逐字节一致");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        assertEquals(payload.length, download.object().size());
        assertEquals(vo.fileName(), download.metadata().fileName());
    }

    // ===== 文件安全策略 =====

    @Test
    void uploadShouldRejectForbiddenExtension() {
        assertThrows(BusinessException.class, () -> upload("MZ".getBytes(), "p8att-evil.exe", "application/octet-stream"),
                "exe 不在白名单 → 422");
        assertThrows(BusinessException.class, () -> upload("<svg/>".getBytes(), "p8att-x.svg", "image/svg+xml"),
                "svg 不在白名单 → 422");
        assertThrows(BusinessException.class, () -> upload("alert".getBytes(), "p8att-x.js", "text/javascript"),
                "js 不在白名单 → 422");
    }

    @Test
    void uploadShouldRejectEmptyAndOversizedFile() {
        assertThrows(BusinessException.class, () -> upload(new byte[0], "p8att-empty.txt", "text/plain"),
                "空文件 → 422");
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        assertThrows(BusinessException.class, () -> upload(oversized, "p8att-big.txt", "text/plain"),
                "10MB+1 → 413");
    }

    @Test
    void uploadShouldSanitizePathTraversalFilename() {
        AttachmentVO vo = upload("safe".getBytes(StandardCharsets.UTF_8),
                "../../etc/p8att-passwd.txt", "text/plain");
        assertEquals("p8att-passwd.txt", vo.fileName(), "路径分量被剥离");
        assertFalse(vo.fileName().contains(".."), "文件名不含 ..");
        assertFalse(objectKeyOf(vo.id()).contains(".."), "对象键不含 ..");
    }

    @Test
    void uploadShouldAllowWhitelistedTypes() {
        assertTrue(upload(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, "p8att-img.png", "image/png").id() != null);
        assertTrue(upload("%PDF-1.4".getBytes(), "p8att-doc.pdf", "application/pdf").id() != null);
        assertTrue(upload("PK".getBytes(), "p8att-archive.zip", "application/zip").id() != null);
    }

    // ===== 删除一致性 =====

    @Test
    void deleteShouldRemoveObjectAndMetadata() {
        AttachmentVO vo = upload("to-delete".getBytes(StandardCharsets.UTF_8), "p8att-del.txt", "text/plain");
        String objectKey = objectKeyOf(vo.id());
        attachmentService.delete(projectId, issueId, vo.id(), ownerId);
        assertFalse(storageService.exists(objectKey), "删除后 MinIO 对象不存在");
        assertNull(attachmentMapper.selectById(vo.id()), "删除后元数据不存在");
        createdObjectKeys.remove(objectKey);
    }

    @Test
    void deleteShouldRejectNonUploader() {
        memberId = createTestUser("member");
        addMember(memberId);
        AttachmentVO vo = upload("keep".getBytes(StandardCharsets.UTF_8), "p8att-keep.txt", "text/plain");
        // 项目成员但非上传者 → 403；对象仍在（一致性未被破坏）
        assertThrows(ForbiddenException.class,
                () -> attachmentService.delete(projectId, issueId, vo.id(), memberId));
        assertTrue(storageService.exists(objectKeyOf(vo.id())), "拒绝删除后对象仍存在");
    }

    // ===== 资源边界 =====

    @Test
    void operationsShouldRejectCrossProjectAndMissingIssue() {
        assertThrows(ResourceNotFoundException.class, () -> attachmentService.upload(999999999L, issueId,
                new MockMultipartFile("file", "p8att-x.txt", "text/plain", "x".getBytes()), ownerId));
        assertThrows(ResourceNotFoundException.class, () -> attachmentService.upload(projectId, 999999999L,
                new MockMultipartFile("file", "p8att-x.txt", "text/plain", "x".getBytes()), ownerId));
    }

    @Test
    void pageShouldListAttachmentsForMembers() {
        upload("a1".getBytes(), "p8att-a1.txt", "text/plain");
        upload("a2".getBytes(), "p8att-a2.txt", "text/plain");
        PageVO<AttachmentVO> page = attachmentService.page(projectId, issueId, 1, 10, ownerId);
        assertEquals(2, page.total());
        assertTrue(page.list().get(0).fileName().endsWith(".txt"));
    }
}
