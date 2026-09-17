package com.workflowx.issue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.issue.dto.CreateIssueRequest;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.entity.IssueStatus;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.issue.service.IssueService;
import com.workflowx.issue.service.WorkflowService;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.entity.Organization;
import com.workflowx.org.mapper.OrganizationMapper;
import com.workflowx.org.service.OrganizationService;
import com.workflowx.project.dto.CreateProjectRequest;
import com.workflowx.project.entity.Project;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.service.ProjectService;
import com.workflowx.user.dto.CreateUserRequest;
import com.workflowx.user.entity.User;
import com.workflowx.user.mapper.UserMapper;
import com.workflowx.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Workflow 并发竞争测试（P7-05，ADR-017）：
 * N 线程同时对同一 issue 提交 OPEN→IN_PROGRESS——条件 UPDATE（WHERE status=from）
 * 保证至多 1 成功，其余 409；不允许"两个请求都成功"的最后写入覆盖。
 * 数据隔离: 组织 ORG_P7CONC_*、项目 P7CONC*、用户 p7conc_ 前缀，用后清理。
 */
@SpringBootTest
class WorkflowConcurrencyTest {

    private static final String KEY_PREFIX = "P7CONC";
    private static final String ORG_PREFIX = "ORG_P7CONC_";
    private static final String USER_PREFIX = "p7conc_";

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private IssueService issueService;

    @Autowired
    private ProjectService projectService;

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

    private final List<Long> createdUserIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        issueMapper.delete(new LambdaQueryWrapper<Issue>().likeRight(Issue::getTitle, "P7-"));
        projectMapper.delete(new LambdaQueryWrapper<Project>().likeRight(Project::getKey, KEY_PREFIX));
        organizationMapper.delete(new LambdaQueryWrapper<Organization>().likeRight(Organization::getCode, ORG_PREFIX));
        userMapper.delete(new LambdaQueryWrapper<User>().likeRight(User::getUsername, USER_PREFIX));
        createdUserIds.clear();
    }

    private Long createOrgAndProject() {
        Long ownerId = createTestUser("owner");
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + "X", ORG_PREFIX + "X", null), ownerId);
        projectService.create(new CreateProjectRequest(
                "并发项目", KEY_PREFIX + "X", orgIdFor("X"), null), ownerId);
        return ownerId;
    }

    private Long createTestUser(String suffix) {
        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", "ConcPass@123", "conc-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    private Long orgIdFor(String suffix) {
        return organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + suffix)).getId();
    }

    private Long projectIdFor(String suffix) {
        return projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + suffix)).getId();
    }

    @Test
    void concurrentTransitionsOnSameFromStatusMustNotDoubleSucceed() throws Exception {
        Long ownerId = createOrgAndProject();
        Long projectId = projectIdFor("X");
        var created = issueService.create(projectId, new CreateIssueRequest(
                "P7-并发目标", "并发竞争测试", com.workflowx.issue.entity.IssueType.BUG,
                com.workflowx.issue.entity.IssuePriority.HIGH,
                com.workflowx.issue.entity.IssueSeverity.S1, null), ownerId);
        Long issueId = created.id();

        int threads = 8;
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    workflowService.transition(projectId, issueId,
                            IssueStatus.OPEN, IssueStatus.IN_PROGRESS, ownerId);
                    successes.incrementAndGet();
                } catch (com.workflowx.common.exception.BusinessException e) {
                    if (e.getStatus() == 409) {
                        conflicts.incrementAndGet();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        // 条件 UPDATE 保证: 至多 1 个请求成功完成 OPEN→IN_PROGRESS，其余 409
        assertEquals(1, successes.get(), "仅一个并发请求成功完成 OPEN→IN_PROGRESS");
        assertEquals(threads - 1, conflicts.get(), "其余并发请求必须 409");
        assertEquals(IssueStatus.IN_PROGRESS, issueMapper.selectById(issueId).getStatus());
    }
}
