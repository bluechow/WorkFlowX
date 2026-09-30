package com.workflowx.testcase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.service.OrganizationService;
import com.workflowx.project.dto.CreateProjectRequest;
import com.workflowx.project.service.ProjectService;
import com.workflowx.testcase.dto.CreateTestCaseRequest;
import com.workflowx.testcase.entity.TestCasePriority;
import com.workflowx.testcase.entity.TestCaseType;
import com.workflowx.testcase.service.TestCaseService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用例 Excel 导入导出（Phase A-⑦）集成测试。
 * 场景：导出（含中文枚举与编号）→ 构造含合法/非法行的导入文件 → 逐行结果断言。
 * 前缀 EX- 隔离。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TestCaseExcelIntegrationTest {

    private static final String ORG_PREFIX = "ex_org_";
    private static final String KEY_PREFIX = "EX";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private TestCaseService testCaseService;

    private Long projectId;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", "Admin@123456");
        // 组织 code 全局唯一：时间戳防跨运行残留
        Long orgId = organizationService.create(
                new CreateOrganizationRequest("Excel 组织", ORG_PREFIX + System.currentTimeMillis(), null),
                1L).id();
        projectId = projectService.create(
                new CreateProjectRequest("Excel 项目", KEY_PREFIX + (System.currentTimeMillis() % 100000), orgId, null),
                1L).id();
    }

    @Autowired
    private com.workflowx.org.mapper.OrganizationMapper orgMapper;

    @AfterEach
    void cleanup() {
        // 组织删除级联项目/用例/目录
        orgMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.org.entity.Organization>()
                .likeRight(com.workflowx.org.entity.Organization::getCode, ORG_PREFIX));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        return (String) ((java.util.Map<?, ?>) response.get("data")).get("accessToken");
    }

    @Test
    void exportThenImportRoundTrip_withRowLevelErrors() throws Exception {
        // 预置两条用例（不同目录/枚举）
        testCaseService.create(projectId, new CreateTestCaseRequest(
                "登录成功用例", "前置：账号存在", "1. 输入密码\n2. 点击登录", "进入首页",
                TestCaseType.SMOKE, TestCasePriority.HIGH, null, null), 1L);
        testCaseService.create(projectId, new CreateTestCaseRequest(
                "课程检索用例", null, null, null,
                TestCaseType.FUNCTIONAL, TestCasePriority.LOW, null, null), 1L);

        // ===== 导出 =====
        byte[] xlsx = mockMvc.perform(get("/api/v1/projects/{id}/testcases/export", projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("用例编号", sheet.getRow(0).getCell(0).getStringCellValue());
            assertTrue(sheet.getRow(1).getCell(0).getStringCellValue().startsWith(KEY_PREFIX));
            assertEquals("冒烟", sheet.getRow(1).getCell(3).getStringCellValue());   // 中文枚举
            assertEquals("功能", sheet.getRow(2).getCell(3).getStringCellValue());
            assertEquals("高", sheet.getRow(1).getCell(4).getStringCellValue());
        }

        // 未认证导出 → 401
        mockMvc.perform(get("/api/v1/projects/{id}/testcases/export", projectId))
                .andExpect(status().isUnauthorized());

        // ===== 导入：2 合法行（其一目录不存在将自动创建）+ 2 非法行 =====
        byte[] importFile;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("用例库");
            Row head = sheet.createRow(0);
            for (int i = 0; i < 9; i++) head.createCell(i);
            // 行 1：合法（新目录「导入目录」+ 中文枚举）
            Row r1 = sheet.createRow(1);
            r1.createCell(1).setCellValue("导入用例甲");
            r1.createCell(2).setCellValue("导入目录");
            r1.createCell(3).setCellValue("回归");
            r1.createCell(4).setCellValue("关键");
            r1.createCell(5).setCellValue("启用");
            r1.createCell(6).setCellValue("前置条件文本");
            // 行 2：合法（英文枚举兜底）
            Row r2 = sheet.createRow(2);
            r2.createCell(1).setCellValue("Import case B");
            r2.createCell(3).setCellValue("SECURITY");
            r2.createCell(4).setCellValue("CRITICAL");
            // 行 3：标题为空 → 行级错误
            sheet.createRow(3).createCell(3).setCellValue("功能");
            // 行 4：类型非法 → 行级错误
            Row r4 = sheet.createRow(4);
            r4.createCell(1).setCellValue("类型非法用例");
            r4.createCell(3).setCellValue("不存在的类型");
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            wb.write(bos);
            importFile = bos.toByteArray();
        }

        MvcResult importResult = mockMvc.perform(multipart("/api/v1/projects/{id}/testcases/import", projectId)
                        .file(new MockMultipartFile("file", "cases.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", importFile))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        var response = objectMapper.readValue(importResult.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) response.get("data");
        assertEquals(4, ((Number) data.get("totalRows")).intValue());
        assertEquals(2, ((Number) data.get("successCount")).intValue());
        assertEquals(2, ((Number) data.get("failureCount")).intValue());
        @SuppressWarnings("unchecked")
        var failures = (List<java.util.Map<String, Object>>) data.get("failures");
        assertTrue(String.valueOf(failures.get(0).get("message")).contains("用例标题不能为空"));
        assertTrue(String.valueOf(failures.get(1).get("message")).contains("不合法"));

        // 导入的用例真实落库（2 条成功 + 目录自动创建）
        var cases = testCaseService.page(projectId,
                new com.workflowx.testcase.dto.TestCasePageQuery(null, null, null, null, null, 1, 50), 1L);
        assertEquals(4, cases.total());   // 预置 2 + 导入 2
    }

    /** P1 加固回归：行数超限 413；超长单元格 413；空文件（无表头行）安全返回 0 行 */
    @Test
    void importResourceLimits() throws Exception {
        // 1001 行 → 413（整文件拒绝）
        byte[] tooMany;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("用例库");
            sheet.createRow(0).createCell(1).setCellValue("标题");
            for (int r = 1; r <= 1001; r++) {
                Row row = sheet.createRow(r);
                row.createCell(1).setCellValue("批量用例 " + r);
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            wb.write(bos);
            tooMany = bos.toByteArray();
        }
        mockMvc.perform(multipart("/api/v1/projects/{id}/testcases/import", projectId)
                        .file(new MockMultipartFile("file", "many.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", tooMany))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isPayloadTooLarge());

        // 单元格 5001 字符 → 413
        byte[] bigCell;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("用例库");
            sheet.createRow(0).createCell(1).setCellValue("标题");
            Row row = sheet.createRow(1);
            row.createCell(1).setCellValue("含巨单元格的用例");
            row.createCell(6).setCellValue("x".repeat(5001));
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            wb.write(bos);
            bigCell = bos.toByteArray();
        }
        // 巨单元格属行级问题：整文件 200，该行进 failures（与行级错误语义一致）
        mockMvc.perform(multipart("/api/v1/projects/{id}/testcases/import", projectId)
                        .file(new MockMultipartFile("file", "big.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bigCell))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.totalRows").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.failureCount").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.failures[0].message")
                        .value(org.hamcrest.Matchers.containsString("超过 5000 字符")));

        // 空 workbook（0 行）→ 200 + 0 行处理
        byte[] empty;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("用例库");
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            wb.write(bos);
            empty = bos.toByteArray();
        }
        mockMvc.perform(multipart("/api/v1/projects/{id}/testcases/import", projectId)
                        .file(new MockMultipartFile("file", "empty.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", empty))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.totalRows").value(0));
    }
}
