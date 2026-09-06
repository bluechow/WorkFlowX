package com.workflowx.user.service;

import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
import com.workflowx.user.dto.CreateUserRequest;
import com.workflowx.user.dto.UpdateUserRequest;
import com.workflowx.user.dto.UserPageQuery;
import com.workflowx.user.entity.User;
import com.workflowx.user.entity.UserStatus;
import com.workflowx.user.mapper.UserMapper;
import com.workflowx.user.vo.UserVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UserService 集成测试（P2-03）：真实 MySQL 链路（Service → Mapper → MySQL）。
 * 数据隔离: 测试用户统一 p2_service_test_ 前缀，用后清理，不污染 dev 种子（admin/user1）。
 * 状态更新仅验证库内状态变更，登录联动（锁定/踢线）属后续认证任务（P2-08/P2-12）。
 */
@SpringBootTest
class UserServiceIntegrationTest {

    private static final String PREFIX = "p2_service_test_";

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private UserMapper userMapper;

    @AfterEach
    void cleanupTestUsers() {
        userMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, PREFIX));
    }

    // ===== 数据工厂 =====

    private UserVO createTestUser(String suffix, UserStatus status) {
        UserVO created = userService.create(new CreateUserRequest(
                PREFIX + suffix,
                PREFIX + suffix + "@test.local",
                "Password@123",
                "svc-" + suffix));
        if (status != UserStatus.ACTIVE) {
            // -1L: 测试工厂的系统操作者占位（规避"不能修改自己"守卫，非真实操作者）
            return userService.updateStatus(-1L, created.id(), status);
        }
        return created;
    }

    // ===== 查询 =====

    @Test
    void getByIdShouldReturnSeededAdmin() {
        UserVO admin = userService.page(new UserPageQuery("admin", null, 1, 10)).list().stream()
                .filter(u -> "admin".equals(u.username()))
                .findFirst().orElseThrow();
        UserVO byId = userService.getById(admin.id());
        assertEquals("admin", byId.username());
        assertEquals(UserStatus.ACTIVE, byId.status());
        assertNotNull(byId.createdAt());
    }

    @Test
    void getByIdShouldThrowNotFoundForMissingId() {
        assertThrows(ResourceNotFoundException.class, () -> userService.getById(999999999L));
    }

    @Test
    void pageShouldMatchKeywordOnUsernameEmailAndNickname() {
        createTestUser("kwu", UserStatus.ACTIVE);
        // username 含关键词
        userService.create(new CreateUserRequest(PREFIX + "kwtest-u2", PREFIX + "kwtest-u2@test.local", "Password@123", "n2"));
        // email 含关键词
        userService.create(new CreateUserRequest(PREFIX + "kwu3", PREFIX + "kwtest-u3@test.local", "Password@123", "n3"));
        // nickname 含关键词
        userService.create(new CreateUserRequest(PREFIX + "kwu4", PREFIX + "kwu4@test.local", "Password@123", "kwtest-nick"));

        PageVO<UserVO> page = userService.page(new UserPageQuery("kwtest", null, 1, 10));
        assertEquals(3, page.total(), "keyword 应命中 username/email/nickname 三处");
    }

    @Test
    void pageShouldFilterByStatus() {
        createTestUser("sta1", UserStatus.LOCKED);
        createTestUser("sta2", UserStatus.LOCKED);
        PageVO<UserVO> page = userService.page(new UserPageQuery(null, UserStatus.LOCKED, 1, 100));
        assertTrue(page.total() >= 2);
        assertTrue(page.list().stream().allMatch(u -> u.status() == UserStatus.LOCKED));
    }

    @Test
    void pageShouldCombineKeywordAndStatus() {
        createTestUser("combo1", UserStatus.LOCKED);
        createTestUser("combo2", UserStatus.DISABLED);
        PageVO<UserVO> page = userService.page(new UserPageQuery(PREFIX, UserStatus.LOCKED, 1, 10));
        assertEquals(1, page.total());
        assertEquals(PREFIX + "combo1", page.list().get(0).username());
    }

    @Test
    void pageShouldHaveStableOrdering() {
        createTestUser("sort1", UserStatus.ACTIVE);
        createTestUser("sort2", UserStatus.ACTIVE);
        PageVO<UserVO> run1 = userService.page(new UserPageQuery(PREFIX, null, 1, 10));
        PageVO<UserVO> run2 = userService.page(new UserPageQuery(PREFIX, null, 1, 10));
        assertEquals(run1.list().stream().map(UserVO::id).toList(),
                run2.list().stream().map(UserVO::id).toList(), "两次分页顺序必须一致");
    }

    @Test
    void pageShouldRespectPageSize() {
        createTestUser("ps1", UserStatus.ACTIVE);
        createTestUser("ps2", UserStatus.ACTIVE);
        createTestUser("ps3", UserStatus.ACTIVE);
        PageVO<UserVO> page = userService.page(new UserPageQuery(PREFIX, null, 1, 2));
        assertEquals(3, page.total());
        assertEquals(2, page.list().size());
        PageVO<UserVO> page2 = userService.page(new UserPageQuery(PREFIX, null, 2, 2));
        assertEquals(1, page2.list().size());
    }

    // ===== 创建 =====

    @Test
    void createShouldPersistBcryptHashNotPlainPassword() {
        String raw = "SvcPass@123";
        UserVO created = userService.create(new CreateUserRequest(
                PREFIX + "bcrypt", PREFIX + "bcrypt@test.local", raw, "hash-check"));

        assertNotNull(created.id());
        assertEquals(UserStatus.ACTIVE, created.status());
        String hashInDb = userMapper.selectById(created.id()).getPasswordHash();
        assertTrue(hashInDb.matches("^\\$2[aby]\\$10\\$[./A-Za-z0-9]{53}$"), "库内应为 BCrypt(10): " + hashInDb);
        assertTrue(!hashInDb.contains(raw), "库内不得出现明文密码");
        assertTrue(passwordService.matches(raw, hashInDb), "哈希应可验证原始密码");
    }

    @Test
    void createDuplicateUsernameWithDifferentCaseShouldReturn409() {
        // 行为记录: username 列为 utf8mb4_0900_ai_ci（大小写不敏感），大小写变体视为冲突（文档化语义）
        // 第二次创建使用不同 email，确保 409 确证来自 username 冲突
        createTestUser("CaseUser", UserStatus.ACTIVE);
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.create(
                new CreateUserRequest(PREFIX + "caseuser", PREFIX + "case-alt@test.local", "Password@123", "n")));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void createDuplicateUsernameShouldThrow409() {
        createTestUser("dupu", UserStatus.ACTIVE);
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.create(
                new CreateUserRequest(PREFIX + "dupu", PREFIX + "dupu-other@test.local", "Password@123", "n")));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void createDuplicateEmailShouldThrow409() {
        createTestUser("dupe", UserStatus.ACTIVE);
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.create(
                new CreateUserRequest(PREFIX + "dupe2", PREFIX + "dupe@test.local", "Password@123", "n")));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void createDuplicateSeedEmailShouldThrow409() {
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.create(
                new CreateUserRequest(PREFIX + "dupeadmin", "admin@workflowx.local", "Password@123", "n")));
        assertEquals(409, ex.getStatus());
    }

    // ===== 更新 =====

    @Test
    void updateShouldChangeEmailAndNickname() {
        UserVO created = createTestUser("upd", UserStatus.ACTIVE);
        UserVO updated = userService.update(created.id(),
                new UpdateUserRequest(PREFIX + "upd-new@test.local", "new-nick"));
        assertEquals(PREFIX + "upd-new@test.local", updated.email());
        assertEquals("new-nick", updated.nickname());
    }

    @Test
    void updateDuplicateEmailOfAnotherUserShouldThrow409() {
        UserVO first = createTestUser("upd-a", UserStatus.ACTIVE);
        UserVO second = createTestUser("upd-b", UserStatus.ACTIVE);
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.update(
                second.id(), new UpdateUserRequest(first.email(), "n")));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void updateKeepingOwnEmailShouldSucceed() {
        UserVO created = createTestUser("upd-self", UserStatus.ACTIVE);
        UserVO updated = userService.update(created.id(),
                new UpdateUserRequest(created.email(), "same-email-ok"));
        assertEquals(created.email(), updated.email());
        assertEquals("same-email-ok", updated.nickname());
    }

    @Test
    void updateMissingUserShouldThrow404() {
        assertThrows(ResourceNotFoundException.class,
                () -> userService.update(999999999L, new UpdateUserRequest("x@x.local", "n")));
    }

    @Test
    void updateMustNotChangeUsernameOrPasswordHash() {
        String raw = "OrigPass@123";
        UserVO created = userService.create(new CreateUserRequest(
                PREFIX + "nochange", PREFIX + "nochange@test.local", raw, "before"));
        String hashBefore = userMapper.selectById(created.id()).getPasswordHash();

        userService.update(created.id(), new UpdateUserRequest(PREFIX + "nochange2@test.local", "after"));

        User after = userMapper.selectById(created.id());
        assertEquals(PREFIX + "nochange", after.getUsername(), "username 不得被普通更新修改");
        assertEquals(hashBefore, after.getPasswordHash(), "password_hash 不得被普通更新修改");
        assertEquals(PREFIX + "nochange2@test.local", after.getEmail());
    }

    // ===== 状态 =====

    @Test
    void updateStatusShouldPersistAllThreeStates() {
        UserVO created = createTestUser("stcyc", UserStatus.ACTIVE);
        assertEquals(UserStatus.DISABLED, userService.updateStatus(-1L, created.id(), UserStatus.DISABLED).status());
        assertEquals(UserStatus.LOCKED, userService.updateStatus(-1L, created.id(), UserStatus.LOCKED).status());
        assertEquals(UserStatus.ACTIVE, userService.updateStatus(-1L, created.id(), UserStatus.ACTIVE).status());
        assertEquals(UserStatus.ACTIVE, userMapper.selectById(created.id()).getStatus());
    }

    @Test
    void updateStatusMissingUserShouldThrow404() {
        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateStatus(-1L, 999999999L, UserStatus.DISABLED));
    }

    @Test
    void updateStatusOnSelfShouldThrow400() {
        UserVO created = createTestUser("selfop", UserStatus.ACTIVE);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateStatus(created.id(), created.id(), UserStatus.DISABLED));
        assertEquals(400, ex.getStatus());
    }
}
