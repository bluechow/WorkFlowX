package com.workflowx.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
import com.workflowx.issue.dto.CreateCommentRequest;
import com.workflowx.issue.dto.CreateIssueRequest;
import com.workflowx.issue.dto.UpdateIssueRequest;
import com.workflowx.issue.entity.IssuePriority;
import com.workflowx.issue.entity.IssueStatus;
import com.workflowx.issue.entity.IssueType;
import com.workflowx.issue.service.CommentService;
import com.workflowx.issue.service.IssueService;
import com.workflowx.issue.vo.CommentVO;
import com.workflowx.issue.vo.IssueVO;
import com.workflowx.notification.entity.Notification;
import com.workflowx.notification.entity.NotificationType;
import com.workflowx.notification.mapper.NotificationMapper;
import com.workflowx.notification.service.NotificationService;
import com.workflowx.notification.vo.NotificationVO;
import com.workflowx.org.dto.AddOrganizationMemberRequest;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.entity.Organization;
import com.workflowx.org.mapper.OrganizationMapper;
import com.workflowx.org.service.OrganizationService;
import com.workflowx.project.dto.AddProjectMemberRequest;
import com.workflowx.project.dto.CreateProjectRequest;
import com.workflowx.project.entity.Project;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.service.ProjectMemberService;
import com.workflowx.project.service.ProjectService;
import com.workflowx.user.dto.CreateUserRequest;
import com.workflowx.user.entity.User;
import com.workflowx.user.mapper.UserMapper;
import com.workflowx.user.service.UserService;
import com.workflowx.issue.service.WorkflowService;
import com.workflowx.issue.entity.IssueStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Notification 领域集成测试（P9 Java；ADR-019）：真实 MySQL。
 * 覆盖: 数据（创建/查询/计数/已读/全部已读）、权限（self 资源隔离）、边界（幂等/空列表/分页/排序/404）、
 * 业务触发（assign/status/comment）、事务（主业务失败回滚无通知）。
 * 数据隔离: P9NT 前缀（组织/项目/用户/通知标题），用后清理（通知随 issue 无 FK——显式按前缀清）。
 */
@SpringBootTest
class NotificationServiceIntegrationTest {

    private static final String KEY_PREFIX = "P9NT";
    private static final String ORG_PREFIX = "ORG_P9NT_";
    private static final String USER_PREFIX = "p9nt_";
    private static final String TITLE_PREFIX = "P9NT-通知";

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private IssueService issueService;
    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private CommentService commentService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectMemberService projectMemberService;
    @Autowired
    private OrganizationService organizationService;
    @Autowired
    private UserService userService;
    @Autowired
    private NotificationMapper notificationMapper;
    @Autowired
    private com.workflowx.issue.mapper.IssueMapper issueMapper;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private OrganizationMapper organizationMapper;
    @Autowired
    private UserMapper userMapper;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .likeRight(Notification::getTitle, TITLE_PREFIX));
        issueMapper.delete(new LambdaQueryWrapper<com.workflowx.issue.entity.Issue>()
                .likeRight(com.workflowx.issue.entity.Issue::getTitle, "P9NT-"));
        projectMapper.delete(new LambdaQueryWrapper<Project>().likeRight(Project::getKey, KEY_PREFIX));
        organizationMapper.delete(new LambdaQueryWrapper<Organization>().likeRight(Organization::getCode, ORG_PREFIX));
        userMapper.delete(new LambdaQueryWrapper<User>().likeRight(User::getUsername, USER_PREFIX));
        createdUserIds.clear();
    }

    private Long createTestUser(String suffix) {
        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", "NotifPass@123", "nt-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    // ===== 数据能力 =====

    @Test
    void createShouldPersistUnreadNotification() {
        Long recipient = createTestUser("r1");
        notificationService.notifyIssueAssigned(KEY_PREFIX, 1L, TITLE_PREFIX + "-A", 999L, recipient, 1L);
        assertEquals(1L, notificationService.unreadCount(recipient));
        NotificationVO vo = notificationService.listMy(recipient, null, 1, 10).list().get(0);
        assertEquals(NotificationType.ISSUE_ASSIGNED, vo.type());
        assertEquals("ISSUE", vo.relatedType());
        assertEquals(999L, vo.relatedId());
        assertTrue(vo.content().contains(KEY_PREFIX + "-1"), "通知内容含业务编号");
        assertNotNull(vo.createdAt());
        assertNull(vo.readAt());
    }

    @Test
    void listMyShouldFilterByReadStateAndSortStable() throws InterruptedException {
        Long recipient = createTestUser("r2");
        for (int i = 1; i <= 3; i++) {
            notificationService.notifyIssueCommented(KEY_PREFIX, (long) i, TITLE_PREFIX + "-" + i, (long) i,
                    recipient, 1L);
            Thread.sleep(2);
        }
        PageVO<NotificationVO> all = notificationService.listMy(recipient, null, 1, 10);
        assertEquals(3L, all.total());
        assertTrue(all.list().get(0).createdAt().compareTo(all.list().get(2).createdAt()) >= 0,
                "created_at DESC 稳定排序");
        // 标记最早一条已读 → unread 筛选排除它
        Long firstId = all.list().get(2).id();
        notificationService.markRead(recipient, firstId);
        assertEquals(1L, notificationService.listMy(recipient, true, 1, 10).total());
        assertEquals(2L, notificationService.listMy(recipient, false, 1, 10).total());
        assertEquals(2L, notificationService.unreadCount(recipient));
    }

    @Test
    void markReadShouldBeIdempotent() {
        Long recipient = createTestUser("r3");
        notificationService.notifyIssueAssigned(KEY_PREFIX, 7L, TITLE_PREFIX + "-幂等", 77L, recipient, 1L);
        Long id = notificationService.listMy(recipient, false, 1, 1).list().get(0).id();
        notificationService.markRead(recipient, id);
        assertEquals(0L, notificationService.unreadCount(recipient));
        // 已读再已读：幂等成功且 read_at 不变语义（无异常）
        notificationService.markRead(recipient, id);
        assertEquals(0L, notificationService.unreadCount(recipient));
        assertEquals(1L, notificationService.listMy(recipient, true, 1, 1).total());
    }

    @Test
    void markAllReadShouldOnlyTouchOwnUnread() {
        Long mine = createTestUser("r4");
        Long other = createTestUser("r5");
        notificationService.notifyIssueCommented(KEY_PREFIX, 1L, TITLE_PREFIX + "-1", 1L, mine, 1L);
        notificationService.notifyIssueCommented(KEY_PREFIX, 2L, TITLE_PREFIX + "-2", 2L, mine, 1L);
        notificationService.notifyIssueCommented(KEY_PREFIX, 3L, TITLE_PREFIX + "-3", 3L, other, 1L);
        int updated = notificationService.markAllRead(mine);
        assertEquals(2, updated, "只影响本人未读");
        assertEquals(0L, notificationService.unreadCount(mine));
        assertEquals(1L, notificationService.unreadCount(other), "他人通知不受影响");
    }

    // ===== 权限/数据范围（self 资源）=====

    @Test
    void crossUserNotificationAccessShouldReturn404() {
        Long owner = createTestUser("r6");
        Long attacker = createTestUser("r7");
        notificationService.notifyIssueAssigned(KEY_PREFIX, 9L, TITLE_PREFIX + "-私有", 99L, owner, 1L);
        Long id = notificationService.listMy(owner, null, 1, 1).list().get(0).id();
        // 跨用户读取/修改 → 404 不泄露存在性
        assertThrows(ResourceNotFoundException.class, () -> notificationService.markRead(attacker, id));
        // owner 自己仍可操作
        notificationService.markRead(owner, id);
    }

    @Test
    void markReadMissingNotificationShouldReturn404() {
        Long recipient = createTestUser("r8");
        assertThrows(ResourceNotFoundException.class, () -> notificationService.markRead(recipient, 999999999L));
    }

    @Test
    void emptyListShouldReturnZeroTotal() {
        Long recipient = createTestUser("r9");
        PageVO<NotificationVO> page = notificationService.listMy(recipient, null, 1, 20);
        assertEquals(0L, page.total());
        assertEquals(0L, notificationService.unreadCount(recipient));
    }

    @Test
    void paginationShouldWorkAcrossPages() {
        Long recipient = createTestUser("r10");
        for (int i = 1; i <= 5; i++) {
            notificationService.notifyIssueCommented(KEY_PREFIX, (long) i, TITLE_PREFIX + "-分页" + i,
                    (long) i, recipient, 1L);
        }
        assertEquals(2, notificationService.listMy(recipient, null, 1, 2).list().size());
        assertEquals(2, notificationService.listMy(recipient, null, 2, 2).list().size());
        assertEquals(1, notificationService.listMy(recipient, null, 3, 2).list().size());
        assertEquals(5L, notificationService.listMy(recipient, null, 1, 2).total());
    }

    // ===== 业务触发 =====

    @Test
    void issueAssignmentShouldNotifyAssignee() {
        Long ownerId = createTestUser("owner");
        Long assignee = createTestUser("assignee");
        Long projectId = createOrgProjectWithMembers(ownerId, assignee);
        // 创建即分派（owner 操作，assignee 收通知；owner 不自通知）
        IssueVO issue = issueService.create(projectId, new CreateIssueRequest(TITLE_PREFIX + "-分派", null, IssueType.TASK, null, null, assignee, null, null), ownerId);
        assertEquals(1L, notificationService.listMy(assignee, null, 1, 10).total());
        assertEquals(0L, notificationService.listMy(ownerId, null, 1, 10).total());
        // 变更分派给 owner（owner 是操作者 → 不通知）
        issueService.update(projectId, issue.id(),
                new UpdateIssueRequest(null, null, null, null, 0L, null, null, null), ownerId);
        // 重新分派 assignee（先取消=0，再设回 assignee → assignee 收到第二条）
        issueService.update(projectId, issue.id(),
                new UpdateIssueRequest(null, null, null, null, assignee, null, null, null), ownerId);
        assertEquals(2L, notificationService.listMy(assignee, null, 1, 10).total(),
                "创建分派 + 重新分派各一条");
    }

    @Test
    void statusTransitionShouldNotifyAssigneeAndReporterExceptOperator() {
        Long ownerId = createTestUser("owner2");
        Long assignee = createTestUser("assignee2");
        Long projectId = createOrgProjectWithMembers(ownerId, assignee);
        IssueVO issue = issueService.create(projectId, new CreateIssueRequest(TITLE_PREFIX + "-流转", null, IssueType.TASK, null, null, assignee, null, null), ownerId);
        long beforeAssignee = notificationService.listMy(assignee, null, 1, 50).total();
        // owner 流转（owner=reporter）→ assignee 收到状态通知；owner 是操作者不收
        workflowService.transition(projectId, issue.id(), IssueStatus.OPEN, IssueStatus.IN_PROGRESS, ownerId);
        assertEquals(beforeAssignee + 1, notificationService.listMy(assignee, null, 1, 50).total());
        assertEquals(0L, notificationService.listMy(ownerId, null, 1, 10).total(), "操作者本人不收通知");
        NotificationVO vo = notificationService.listMy(assignee, null, 1, 1).list().get(0);
        assertEquals(NotificationType.ISSUE_STATUS_CHANGED, vo.type());
        assertTrue(vo.title().contains("IN_PROGRESS"));
    }

    @Test
    void commentCreateShouldNotifyAssigneeAndReporterExceptCommenter() {
        Long ownerId = createTestUser("owner3");
        Long assignee = createTestUser("assignee3");
        Long projectId = createOrgProjectWithMembers(ownerId, assignee);
        issueService.create(projectId, new CreateIssueRequest(TITLE_PREFIX + "-评论", null, IssueType.TASK, null, null, assignee, null, null), ownerId);
        // owner（=reporter）评论 → assignee 收通知，owner 不收
        commentService.create(projectId, issueIdOf(projectId),
                new CreateCommentRequest(TITLE_PREFIX + "-评论内容"), ownerId);
        assertEquals(2L, notificationService.listMy(assignee, null, 1, 10).total());
        assertEquals(0L, notificationService.listMy(ownerId, null, 1, 10).total());
        NotificationVO latest = notificationService.listMy(assignee, null, 1, 1).list().get(0);
        assertEquals(NotificationType.ISSUE_COMMENTED, latest.type());
    }

    // ===== 事务: 主业务失败回滚 → 无通知 =====

    @Test
    void failedBusinessShouldNotLeaveNotification() {
        Long ownerId = createTestUser("owner4");
        Long assignee = createTestUser("assignee4");
        Long projectId = createOrgProjectWithMembers(ownerId, assignee);
        // 非项目成员作为 assignee → create 失败（同事务内通知尚未产生；验证主链完整性）
        Long outsider = createTestUser("outsider4");
        assertThrows(Exception.class, () -> issueService.create(projectId, new CreateIssueRequest(TITLE_PREFIX + "-失败", null, IssueType.TASK, null, null, outsider, null, null), ownerId));
        assertEquals(0L, notificationService.listMy(outsider, null, 1, 10).total(),
                "主业务失败不产生通知");
        assertEquals(0L, notificationService.listMy(ownerId, null, 1, 10).total());
    }

    // ===== helpers =====

    private Long createOrgProjectWithMembers(Long ownerId, Long memberId) {
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + ownerId, ORG_PREFIX + ownerId, null), ownerId);
        Long orgId = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + ownerId)).getId();
        projectService.create(new CreateProjectRequest(
                "P9NT 项目", KEY_PREFIX + ownerId, orgId, null), ownerId);
        Long projectId = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + ownerId)).getId();
        organizationService.addMember(orgId, new AddOrganizationMemberRequest(memberId, "MEMBER", null));
        projectMemberService.addMember(projectId, new AddProjectMemberRequest(memberId, "MEMBER"), ownerId);
        return projectId;
    }

    private Long issueIdOf(Long projectId) {
        var page = issueService.page(projectId, new com.workflowx.issue.dto.IssuePageQuery(TITLE_PREFIX + "-评论", null, null, null, null, null, null, null, null, null, null, 1, 10));
        return page.list().get(0).id();
    }

}
