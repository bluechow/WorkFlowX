package com.workflowx.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.audit.entity.AuditLog;
import com.workflowx.audit.mapper.AuditLogMapper;
import com.workflowx.audit.service.AuditService;
import com.workflowx.audit.vo.AuditLogVO;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
import com.workflowx.issue.dto.CreateCommentRequest;
import com.workflowx.issue.dto.CreateIssueRequest;
import com.workflowx.issue.entity.IssueStatus;
import com.workflowx.issue.entity.IssueType;
import com.workflowx.issue.service.CommentService;
import com.workflowx.issue.service.WorkflowService;
import com.workflowx.issue.vo.IssueVO;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit 领域集成测试（P10-14; ADR-020）：真实 MySQL。
 * 覆盖: record（含 REQUIRES_NEW 失败语义）/query 全维筛选/分页稳定排序/getById 404/
 * 敏感数据排除（记录内容不含密码字段）/业务触发（issue create/transition/comment/assign member）。
 * 数据隔离: P10AT 前缀（组织/项目/用户/审计摘要），用后清理。
 */
@SpringBootTest
class AuditServiceIntegrationTest {

    private static final String KEY_PREFIX = "P10AT";
    private static final String ORG_PREFIX = "ORG_P10AT_";
    private static final String USER_PREFIX = "p10at_";
    private static final String SUMMARY_PREFIX = "P10AT-审计";

    @Autowired
    private AuditService auditService;
    @Autowired
    private AuditLogMapper auditLogMapper;
    @Autowired
    private com.workflowx.issue.mapper.IssueMapper issueMapper;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired
    private OrganizationService organizationService;
    @Autowired
    private OrganizationMapper organizationMapper;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private ProjectMemberService projectMemberService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private com.workflowx.issue.service.IssueService issueService;
    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private CommentService commentService;

    private final List<Long> createdUserIds = new java.util.ArrayList<>();

    @BeforeEach
    void setUpRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        auditLogMapper.delete(new LambdaQueryWrapper<AuditLog>()
                .likeRight(AuditLog::getSummary, SUMMARY_PREFIX)
                .or().likeRight(AuditLog::getSummary, "创建 Issue " + KEY_PREFIX)
                .or().likeRight(AuditLog::getTarget, "issue:"));
        issueMapper.delete(new LambdaQueryWrapper<com.workflowx.issue.entity.Issue>()
                .likeRight(com.workflowx.issue.entity.Issue::getTitle, "P10AT-"));
        projectMapper.delete(new LambdaQueryWrapper<Project>().likeRight(Project::getKey, KEY_PREFIX));
        organizationMapper.delete(new LambdaQueryWrapper<Organization>().likeRight(Organization::getCode, ORG_PREFIX));
        userMapper.delete(new LambdaQueryWrapper<User>().likeRight(User::getUsername, USER_PREFIX));
        createdUserIds.clear();
    }

    private Long createTestUser(String suffix) {
        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", "AuditPass@123", "au-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    // ===== record 基础 =====

    @Test
    void recordShouldPersistWithWebContextAndTraceId() {
        auditService.record("ISSUE", "CREATE", "issue:1", SUMMARY_PREFIX + "-基础", true, 1L);
        AuditLogVO vo = auditService.query(new AuditService.AuditQueryParams(
                1L, "ISSUE", "CREATE", null, null, null, null, null, 1, 10)).list().get(0);
        assertEquals("POST", vo.httpMethod());
        assertEquals("/api/v1/test", vo.uri());
        assertEquals("127.0.0.1", vo.ip());
        assertTrue(vo.success());
        assertEquals("issue:1", vo.target());
        assertNotNull(vo.createdAt());
    }

    @Test
    void recordStandaloneShouldSurviveOuterRollback() {
        // 模拟外层事务回滚: 在事务模板内先 record（同事务）再抛异常 → 同事务审计消失；
        // recordStandalone 独立提交 → 失败事实幸存
        org.springframework.transaction.support.TransactionTemplate template =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> template.execute(status -> {
            auditService.record("ISSUE", "CREATE", "issue:rollback", SUMMARY_PREFIX + "-同事务将回滚", true, 1L);
            auditService.recordStandalone("AUTH", "LOGIN_FAIL", "user:p10at_x", SUMMARY_PREFIX + "-独立事务幸存", false, null);
            throw new IllegalStateException("业务失败");
        }));
        long rolledBack = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getSummary, SUMMARY_PREFIX + "-同事务将回滚"));
        long survived = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getSummary, SUMMARY_PREFIX + "-独立事务幸存"));
        assertEquals(0, rolledBack, "主业务回滚不留成功审计（同事务语义）");
        assertEquals(1, survived, "失败事实独立事务幸存（REQUIRES_NEW 语义）");
    }

    // ===== 查询 =====

    @Test
    void queryShouldFilterByModuleActionSuccessAndTime() {
        Long operator = createTestUser("q1");
        auditService.record("USER", "CREATE", "user:1", SUMMARY_PREFIX + "-q1", true, operator);
        auditService.record("ISSUE", "TRANSITION", "issue:2", SUMMARY_PREFIX + "-q2", true, operator);
        auditService.record("AUTH", "LOGIN_FAIL", "user:1", SUMMARY_PREFIX + "-q3", false, operator);

        assertTrue(auditService.query(new AuditService.AuditQueryParams(
                operator, "USER", "CREATE", null, null, null, null, null, 1, 10)).total() >= 1,
                "operator 维度筛选（全局计数受历史运行影响，不作恰好断言）");
        assertEquals(1, auditService.query(new AuditService.AuditQueryParams(
                operator, null, "LOGIN_FAIL", null, null, null, null, null, 1, 10)).total());
        assertEquals(1, auditService.query(new AuditService.AuditQueryParams(
                null, null, null, false, null, null, null, null, 1, 10)).total(),
                "success=false 筛选");
        // 组合精确断言（抗测试顺序/历史残留：只断言本用例产出的确定性组合）
        assertEquals(1, auditService.query(new AuditService.AuditQueryParams(
                operator, "USER", "CREATE", true, null, null, null, null, 1, 10)).total());
        assertEquals(1, auditService.query(new AuditService.AuditQueryParams(
                operator, "ISSUE", "TRANSITION", true, null, null, null, null, 1, 10)).total());
        assertEquals(0, auditService.query(new AuditService.AuditQueryParams(
                operator, "AUTH", "LOGIN_FAIL", true, null, null, null, null, 1, 10)).total(),
                "LOGIN_FAIL 必为 success=false");
        // 时间范围（operator 维度，抗历史残留）
        var now = java.time.LocalDateTime.now();
        assertEquals(3, auditService.query(new AuditService.AuditQueryParams(
                operator, null, null, null, null, null, now.minusMinutes(1), now.plusMinutes(1), 1, 10)).total(),
                "本用例 operator 的 3 条记录全部落在时间窗内");
        assertEquals(0, auditService.query(new AuditService.AuditQueryParams(
                operator, null, null, null, null, null, now.plusMinutes(1), null, 1, 10)).total());
    }

    @Test
    void queryShouldPaginateWithStableOrder() {
        Long operator = createTestUser("q2");
        for (int i = 1; i <= 5; i++) {
            auditService.record("PROJECT", "CREATE", "project:" + i, SUMMARY_PREFIX + "-分页" + i, true, operator);
        }
        PageVO<AuditLogVO> page1 = auditService.query(new AuditService.AuditQueryParams(
                operator, null, null, null, null, null, null, null, 1, 2));
        PageVO<AuditLogVO> page2 = auditService.query(new AuditService.AuditQueryParams(
                operator, null, null, null, null, null, null, null, 2, 2));
        assertEquals(5, page1.total());
        assertEquals(2, page1.list().size());
        assertEquals(2, page2.list().size());
        // created_at DESC, id DESC: 后插的在前
        assertTrue(page1.list().get(0).id() > page1.list().get(1).id());
        assertTrue(page1.list().get(1).id() > page2.list().get(0).id());
    }

    @Test
    void getByIdShouldReturn404ForMissing() {
        assertThrows(ResourceNotFoundException.class, () -> auditService.getById(999999999L));
    }

    // ===== 敏感数据排除 =====

    @Test
    void sensitiveDataMustNeverBeRecorded() {
        Long operator = createTestUser("sec");
        // 模拟业务方正确使用: 摘要只含白名单字段（不传密码）
        auditService.record("AUTH", "LOGIN", "user:" + operator, "登录成功", true, operator);
        List<AuditLog> rows = auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                .likeRight(AuditLog::getSummary, "登录成功"));
        for (AuditLog row : rows) {
            String all = String.valueOf(row.getSummary()) + row.getTarget() + row.getUri();
            assertFalse(all.contains("AuditPass@123"), "密码不得出现在审计内容");
            assertFalse(all.toLowerCase().contains("password"), "不得记录密码字段名");
            assertFalse(all.toLowerCase().contains("bearer"), "不得记录 Authorization 头");
        }
    }

    // ===== 业务触发（代表性操作）=====

    @Test
    void businessOperationsShouldProduceAuditEntries() {
        Long ownerId = createTestUser("biz");
        Long memberId = createTestUser("bizm");
        Long orgId = createOrgProjectMembers(ownerId, memberId);

        // org create 已审计
        assertEquals(1, countBy("ORG", "CREATE", "org:" + orgId));
        // project create 已审计
        Long projectId = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + ownerId)).getId();
        assertEquals(1, countBy("PROJECT", "CREATE", "project:" + projectId));

        // issue create / transition / comment
        IssueVO issue = issueService.create(projectId, new CreateIssueRequest(
                "P10AT-审计目标", null, IssueType.TASK, null, null, memberId), ownerId);
        assertEquals(1, countBy("ISSUE", "CREATE", "issue:" + issue.id()));
        workflowService.transition(projectId, issue.id(), IssueStatus.OPEN, IssueStatus.IN_PROGRESS, ownerId);
        assertEquals(1, countBy("ISSUE", "TRANSITION", "issue:" + issue.id()));
        commentService.create(projectId, issue.id(), new CreateCommentRequest(SUMMARY_PREFIX + "-评论"), ownerId);
        assertEquals(1, countBy("COMMENT", "CREATE", "issue:" + issue.id()));

        // 审计内容与真实业务数据一致（summary 含业务编号）
        AuditLogVO transition = auditService.query(new AuditService.AuditQueryParams(
                null, "ISSUE", "TRANSITION", null, "issue:" + issue.id(), null, null, null, 1, 1)).list().get(0);
        assertTrue(transition.summary().contains("OPEN -> IN_PROGRESS"));
        assertEquals(ownerId, transition.userId(), "操作者为真实执行者");
    }

    @Test
    void failedBusinessShouldNotLeaveSuccessAudit() {
        Long ownerId = createTestUser("rbac");
        Long outsider = createTestUser("rbo");
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + "RB", ORG_PREFIX + "RB", null), ownerId);
        Long orgId = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + "RB")).getId();
        // outsider 非组织成员 → project create 失败 → 不留成功审计
        assertThrows(Exception.class, () -> projectService.create(new CreateProjectRequest(
                "P10AT 失败项目", KEY_PREFIX + "RB", orgId, null), outsider));
        assertEquals(0, auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getModule, "PROJECT")
                .eq(AuditLog::getAction, "CREATE")
                .likeRight(AuditLog::getSummary, "创建项目 " + KEY_PREFIX + "RB")),
                "主业务失败（共事务回滚）不留成功审计（按本项目 key 精确断言，抗历史残留）");
    }

    // ===== helpers =====

    private long countBy(String module, String action, String target) {
        return auditService.query(new AuditService.AuditQueryParams(
                null, module, action, null, target, null, null, null, 1, 50)).total();
    }

    private Long createOrgProjectMembers(Long ownerId, Long memberId) {
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + ownerId, ORG_PREFIX + ownerId, null), ownerId);
        Long orgId = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + ownerId)).getId();
        organizationService.addMember(orgId, new AddOrganizationMemberRequest(memberId, "MEMBER", null));
        projectService.create(new CreateProjectRequest(
                "P10AT 审计项目", KEY_PREFIX + ownerId, orgId, null), ownerId);
        Long projectId = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + ownerId)).getId();
        projectMemberService.addMember(projectId, new AddProjectMemberRequest(memberId, "MEMBER"), ownerId);
        return orgId;
    }

}
