package com.workflowx.user.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflowx.user.entity.User;
import com.workflowx.user.entity.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户 Mapper / 分页真实链路测试（P2-02）：Java → MyBatis-Plus → JDBC → MySQL → users。
 * 前置条件: 本地基础设施可用（compose MySQL :3307）。
 * 数据隔离: 测试用户统一使用 p2_mapper_test_ 前缀，用后清理，不污染 dev 种子数据（admin/user1）。
 * SQL 日志: 仅本测试类开启 mapper DEBUG（properties 注入），验证实际生成 ORDER BY + LIMIT/OFFSET 与 count。
 */
@SpringBootTest(properties = "logging.level.com.workflowx.user.mapper=debug")
class UserMapperTest {

    private static final String PREFIX = "p2_mapper_test_";

    /** 测试占位哈希（BCrypt 格式占位，非真实凭据；测试用户用后即删） */
    private static final String TEST_HASH = "$2a$10$SiyhSohOsoc29a9gAzxxD.CAhCxZP5p.z8rsNHixxoF1oSt9PcL1i";

    @Autowired
    private UserMapper userMapper;

    @AfterEach
    void cleanupTestUsers() {
        userMapper.delete(prefixWrapper());
    }

    private LambdaQueryWrapper<User> prefixWrapper() {
        return new LambdaQueryWrapper<User>().likeRight(User::getUsername, PREFIX);
    }

    /** 构造带稳定排序的分页查询（排序约定: created_at DESC, id DESC，由查询方负责） */
    private LambdaQueryWrapper<User> pagedPrefixWrapper() {
        return new LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, PREFIX)
                .orderByDesc(User::getCreatedAt)
                .orderByDesc(User::getId);
    }

    private User insertTestUser(String suffix, UserStatus status) {
        User user = new User();
        user.setUsername(PREFIX + suffix);
        user.setEmail(PREFIX + suffix + "@test.local");
        user.setPasswordHash(TEST_HASH);
        user.setNickname("mapper-test-" + suffix);
        user.setStatus(status);
        userMapper.insert(user);
        return user;
    }

    @Test
    void selectByIdShouldReturnSeededAdmin() {
        User admin = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
        assertNotNull(admin);
        User byId = userMapper.selectById(admin.getId());
        assertNotNull(byId);
        assertEquals("admin", byId.getUsername());
        assertEquals(UserStatus.ACTIVE, byId.getStatus());
        assertNotNull(byId.getCreatedAt());
        assertNotNull(byId.getUpdatedAt());
    }

    @Test
    void selectOneByUsernameShouldReturnUser1() {
        User user1 = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "user1"));
        assertNotNull(user1);
        assertEquals("user1@workflowx.local", user1.getEmail());
    }

    @Test
    void likeRightQueryShouldMatchPrefixOnly() {
        for (int i = 1; i <= 5; i++) {
            insertTestUser("like" + i, UserStatus.ACTIVE);
        }
        List<User> matched = userMapper.selectList(prefixWrapper());
        assertEquals(5, matched.size());
        assertTrue(matched.stream().allMatch(u -> u.getUsername().startsWith(PREFIX)));
        // 不应混入种子用户
        assertTrue(matched.stream().noneMatch(u -> u.getUsername().equals("admin")));
    }

    @Test
    void eqStatusShouldFilter() {
        insertTestUser("locked1", UserStatus.LOCKED);
        insertTestUser("locked2", UserStatus.LOCKED);
        insertTestUser("active1", UserStatus.ACTIVE);
        Long lockedCount = userMapper.selectCount(prefixWrapper().eq(User::getStatus, UserStatus.LOCKED));
        assertEquals(2L, lockedCount);
    }

    @Test
    void combinedUsernameAndStatusQueryShouldFilter() {
        insertTestUser("combo1", UserStatus.DISABLED);
        insertTestUser("combo2", UserStatus.ACTIVE);
        Long count = userMapper.selectCount(prefixWrapper()
                .eq(User::getStatus, UserStatus.DISABLED));
        assertEquals(1L, count);
    }

    @Test
    void paginationShouldReturnCorrectTotalAndRecords() {
        for (int i = 1; i <= 5; i++) {
            insertTestUser("page" + i, UserStatus.ACTIVE);
        }
        Page<User> page1 = userMapper.selectPage(new Page<>(1, 2), pagedPrefixWrapper());
        assertEquals(5L, page1.getTotal());
        assertEquals(2, page1.getRecords().size());

        Page<User> page2 = userMapper.selectPage(new Page<>(2, 2), pagedPrefixWrapper());
        assertEquals(5L, page2.getTotal());
        assertEquals(2, page2.getRecords().size());

        Page<User> page3 = userMapper.selectPage(new Page<>(3, 2), pagedPrefixWrapper());
        assertEquals(1, page3.getRecords().size());

        // 三页记录不应重叠
        long distinctIds = List.of(page1, page2, page3).stream()
                .flatMap(p -> p.getRecords().stream())
                .map(User::getId)
                .distinct()
                .count();
        assertEquals(5, distinctIds);
    }

    @Test
    void paginationBeyondLastPageShouldReturnEmptyRecordsWithTotal() {
        for (int i = 1; i <= 3; i++) {
            insertTestUser("over" + i, UserStatus.ACTIVE);
        }
        Page<User> page = userMapper.selectPage(new Page<>(99, 2), pagedPrefixWrapper());
        assertEquals(3L, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }

    @Test
    void paginationShouldHaveStableOrdering() {
        for (int i = 1; i <= 4; i++) {
            insertTestUser("sort" + i, UserStatus.ACTIVE);
        }
        List<Long> firstRun = userMapper.selectPage(new Page<>(1, 10), pagedPrefixWrapper())
                .getRecords().stream().map(User::getId).toList();
        List<Long> secondRun = userMapper.selectPage(new Page<>(1, 10), pagedPrefixWrapper())
                .getRecords().stream().map(User::getId).toList();
        assertEquals(firstRun, secondRun, "两次分页查询结果顺序必须一致（created_at DESC, id DESC）");
        // id DESC 兜底排序: 同批插入的记录按 id 降序出现
        List<Long> sortedDesc = firstRun.stream().sorted((a, b) -> Long.compare(b, a)).toList();
        assertEquals(sortedDesc, firstRun);
    }

    @Test
    void pageSizeOneShouldReturnSingleRecord() {
        for (int i = 1; i <= 3; i++) {
            insertTestUser("size" + i, UserStatus.ACTIVE);
        }
        Page<User> page = userMapper.selectPage(new Page<>(1, 1), pagedPrefixWrapper());
        assertEquals(3L, page.getTotal());
        assertEquals(1, page.getRecords().size());
    }

    @Test
    void nonexistentUsernameShouldReturnNothing() {
        assertTrue(userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "no_such_user_p2")).isEmpty());
        Page<User> page = userMapper.selectPage(new Page<>(1, 10),
                new LambdaQueryWrapper<User>().eq(User::getUsername, "no_such_user_p2"));
        assertEquals(0L, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }

    @Test
    void emptyLikePatternShouldMatchAllUsers() {
        // 行为说明: like("") 生成 LIKE '%%' 匹配全部——过滤条件的有意拼接（空值不加条件）属于 P2-03 Service 职责
        insertTestUser("empty1", UserStatus.ACTIVE);
        List<User> all = userMapper.selectList(new LambdaQueryWrapper<User>().like(User::getUsername, ""));
        assertTrue(all.size() >= 3, "应包含种子用户与测试用户，实际: " + all.size());
        assertTrue(all.stream().anyMatch(u -> u.getUsername().equals("admin")));
        assertTrue(all.stream().anyMatch(u -> u.getUsername().equals("user1")));
    }
}
