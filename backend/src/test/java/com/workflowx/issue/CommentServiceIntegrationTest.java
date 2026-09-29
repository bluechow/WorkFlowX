package com.workflowx.issue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
import com.workflowx.issue.dto.CreateCommentRequest;
import com.workflowx.issue.dto.CreateIssueRequest;
import com.workflowx.issue.dto.UpdateCommentRequest;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.entity.IssuePriority;
import com.workflowx.issue.entity.IssueSeverity;
import com.workflowx.issue.entity.IssueType;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.issue.service.CommentService;
import com.workflowx.issue.service.IssueService;
import com.workflowx.issue.vo.CommentVO;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comment 领域集成测试（P8-12）：真实 MySQL。
 * 数据隔离: 项目 P8COM key 前缀、组织 ORG_P8COM_ 前缀、用户 p8com_ 前缀、评论内容 P8- 前缀，用后清理。
 * ownership 规则（ADR-018）: 编辑/删除仅作者本人；ADMIN 非 owner 同样 403（服务级不豁免）。
 */
@SpringBootTest
class CommentServiceIntegrationTest {

    private static final String KEY_PREFIX = "P8COM";
    private static final String ORG_PREFIX = "ORG_P8COM_";
    private static final String USER_PREFIX = "p8com_";
    private static final String CONTENT_PREFIX = "P8-评论";

    @Autowired
    private CommentService commentService;
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
    private IssueMapper issueMapper;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private OrganizationMapper organizationMapper;
    @Autowired
    private UserMapper userMapper;

    private final List<Long> createdUserIds = new ArrayList<>();
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
                "P8 评论项目", KEY_PREFIX + "MAIN", orgId, null), ownerId);
        projectId = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + "MAIN")).getId();
        IssueVO issue = issueService.create(projectId, new CreateIssueRequest(
                "P8-承载评论的 Issue", null, IssueType.TASK, IssuePriority.MEDIUM, null, null, null, null), ownerId);
        issueId = issue.id();
    }

    @AfterEach
    void cleanup() {
        // comments/attachments 随 issue 级联删除（V12 FK CASCADE），再逐层回收上游
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
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", "Comment@123", "com-" + suffix));
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

    private CommentVO createComment(Long authorId, String suffix) {
        return commentService.create(projectId, issueId,
                new CreateCommentRequest(CONTENT_PREFIX + "-" + suffix), authorId);
    }

    // ===== 创建 =====

    @Test
    void createShouldBindAuthorToOperator() {
        CommentVO comment = createComment(ownerId, "绑定作者");
        assertEquals(ownerId, comment.authorId(), "author 自动绑定操作者，DTO 无 authorId 字段不可伪造");
        assertEquals(issueId, comment.issueId());
        assertTrue(comment.content().startsWith(CONTENT_PREFIX));
    }

    @Test
    void createShouldRejectNonProjectMember() {
        Long outsider = createTestUser("outsider");
        assertThrows(ForbiddenException.class,
                () -> createComment(outsider, "外人"));
    }

    @Test
    void createShouldRejectWhenIssueOrProjectMissing() {
        assertThrows(ResourceNotFoundException.class,
                () -> commentService.create(projectId, 999999999L,
                        new CreateCommentRequest(CONTENT_PREFIX), ownerId));
        assertThrows(ResourceNotFoundException.class,
                () -> commentService.create(999999999L, issueId,
                        new CreateCommentRequest(CONTENT_PREFIX), ownerId));
    }

    @Test
    void createShouldRejectCrossProjectIssueId() {
        Long otherOwner = createTestUser("other");
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + "B", ORG_PREFIX + "B", null), otherOwner);
        Long orgB = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + "B")).getId();
        projectService.create(new CreateProjectRequest(
                "P8 另一项目", KEY_PREFIX + "B", orgB, null), otherOwner);
        Long projectB = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + "B")).getId();
        IssueVO issueB = issueService.create(projectB, new CreateIssueRequest(
                "P8-B 项目 Issue", null, IssueType.TASK, null, null, null, null, null), otherOwner);
        // projectA + issueB 拼接 → 404（不泄露存在性）
        assertThrows(ResourceNotFoundException.class,
                () -> commentService.create(projectId, issueB.id(),
                        new CreateCommentRequest(CONTENT_PREFIX), ownerId));
    }

    // ===== 查询 =====

    @Test
    void pageShouldOrderByCreatedAtAscAndPaginate() {
        for (int i = 1; i <= 3; i++) {
            createComment(ownerId, "序" + i);
        }
        PageVO<CommentVO> page = commentService.page(projectId, issueId, 1, 2, ownerId);
        assertEquals(3, page.total());
        assertEquals(2, page.list().size());
        assertTrue(page.list().get(0).createdAt()
                .compareTo(page.list().get(1).createdAt()) <= 0, "按创建时间正序");
    }

    @Test
    void pageShouldRequireMembership() {
        createComment(ownerId, "查询");
        Long outsider = createTestUser("reader");
        assertThrows(ForbiddenException.class,
                () -> commentService.page(projectId, issueId, 1, 20, outsider));
    }

    // ===== 更新 =====

    @Test
    void updateShouldSucceedForAuthor() {
        CommentVO comment = createComment(ownerId, "待编辑");
        CommentVO updated = commentService.update(projectId, issueId, comment.id(),
                new UpdateCommentRequest(CONTENT_PREFIX + "-编辑后"), ownerId);
        assertEquals(CONTENT_PREFIX + "-编辑后", updated.content());
    }

    @Test
    void updateShouldRejectNonAuthorEvenProjectMember() {
        memberId = createTestUser("member");
        addMember(memberId);
        CommentVO comment = createComment(ownerId, "作者评论");
        // 项目成员但非作者 → 403（ownership 第三层）
        assertThrows(ForbiddenException.class, () -> commentService.update(projectId, issueId, comment.id(),
                new UpdateCommentRequest(CONTENT_PREFIX + "-越权"), memberId));
    }

    // ===== 删除 =====

    @Test
    void deleteShouldRemoveForAuthor() {
        CommentVO comment = createComment(ownerId, "待删除");
        commentService.delete(projectId, issueId, comment.id(), ownerId);
        assertThrows(ResourceNotFoundException.class,
                () -> commentService.getById(projectId, issueId, comment.id(), ownerId),
                "删除后真实不存在（无软删除）");
    }

    @Test
    void deleteShouldRejectNonAuthor() {
        memberId = createTestUser("member2");
        addMember(memberId);
        CommentVO comment = createComment(ownerId, "不能删别人的");
        assertThrows(ForbiddenException.class,
                () -> commentService.delete(projectId, issueId, comment.id(), memberId));
    }

    // ===== 跨资源 =====

    @Test
    void getShouldRejectCommentFromOtherIssue() {
        CommentVO comment = createComment(ownerId, "跨 Issue");
        IssueVO otherIssue = issueService.create(projectId, new CreateIssueRequest(
                "P8-另一个 Issue", null, IssueType.TASK, null, null, null, null, null), ownerId);
        // issueA 路径 + 属于 issueB 的 commentId → 404
        assertThrows(ResourceNotFoundException.class,
                () -> commentService.getById(projectId, otherIssue.id(), comment.id(), ownerId));
    }

    // ===== 内容边界 =====

    @Test
    void createShouldAcceptLongContentWithinLimit() {
        String content = CONTENT_PREFIX + "-长文本" + "字".repeat(9991);
        CommentVO comment = commentService.create(projectId, issueId,
                new CreateCommentRequest(content), ownerId);
        assertEquals(10000, comment.content().length(), "10000 字符上限内应成功（ADR-018）");
    }
}
