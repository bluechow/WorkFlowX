package com.workflowx.testcase.service.impl;

import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.project.entity.Project;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.testcase.dto.CreateTestCaseDirectoryRequest;
import com.workflowx.testcase.dto.CreateTestCaseRequest;
import com.workflowx.testcase.dto.TestCaseImportResultVO;
import com.workflowx.testcase.entity.TestCase;
import com.workflowx.testcase.entity.TestCasePriority;
import com.workflowx.testcase.entity.TestCaseStatus;
import com.workflowx.testcase.entity.TestCaseType;
import com.workflowx.testcase.mapper.TestCaseMapper;
import com.workflowx.testcase.service.TestCaseDirectoryService;
import com.workflowx.testcase.service.TestCaseExcelService;
import com.workflowx.testcase.vo.DirectoryVO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用例 Excel 导入导出实现（Phase A-⑦，Apache POI）。
 *
 * 契约：
 * - 列：用例编号（导出只读）| 用例标题* | 目录 | 类型 | 优先级 | 状态 | 前置条件 | 测试步骤 | 预期结果；
 * - 枚举双语兼容：单元格写中文标签或英文枚举值均可（导出统一中文，便于中文用户编辑）；
 * - 导入逐行独立：单行失败记录「第 N 行：原因」继续处理；目录按名称匹配，缺失即创建；
 * - 权限/成员校验由 Controller（authority）与 TestCaseService（成员）分层负责。
 */
@Service
@RequiredArgsConstructor
public class TestCaseExcelServiceImpl implements TestCaseExcelService {

    private static final String[] HEADERS = {
            "用例编号", "用例标题", "目录", "类型", "优先级", "状态", "前置条件", "测试步骤", "预期结果",
    };

    /** 导入资源上限（P1 加固）：行数与单元格字符数——防大文件/巨行拖垮服务 */
    private static final int MAX_ROWS = 1000;
    private static final int MAX_CELL_CHARS = 5000;

    private final TestCaseMapper testCaseMapper;
    private final TestCaseDirectoryService directoryService;
    private final com.workflowx.testcase.service.TestCaseService testCaseService;
    private final ProjectMapper projectMapper;

    // ===== 导出 =====

    @Override
    public byte[] exportProjectCases(Long projectId, Long operatorId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ResourceNotFoundException("project", projectId);
        }
        List<TestCase> cases = testCaseMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TestCase>()
                        .eq(TestCase::getProjectId, projectId)
                        .orderByAsc(TestCase::getTestcaseNo));
        Map<Long, String> dirNames = directoryNames(projectId, operatorId);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("用例库");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
                sheet.setColumnWidth(i, i == 1 || i >= 6 ? 30 * 256 : 14 * 256);
            }
            int r = 1;
            for (TestCase tc : cases) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(project.getKey() + "-TC-" + tc.getTestcaseNo());
                row.createCell(1).setCellValue(nvl(tc.getTitle()));
                row.createCell(2).setCellValue(tc.getDirectoryId() == null ? "" : dirNames.getOrDefault(tc.getDirectoryId(), ""));
                row.createCell(3).setCellValue(enumZh(TestCaseType.class, tc.getCaseType()));
                row.createCell(4).setCellValue(enumZh(TestCasePriority.class, tc.getPriority()));
                row.createCell(5).setCellValue(enumZh(TestCaseStatus.class, tc.getStatus()));
                row.createCell(6).setCellValue(nvl(tc.getPreconditions()));
                row.createCell(7).setCellValue(nvl(tc.getSteps()));
                row.createCell(8).setCellValue(nvl(tc.getExpected()));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(500, "导出 Excel 生成失败：" + e.getMessage());
        }
    }

    // ===== 导入 =====

    @Override
    @Transactional
    public TestCaseImportResultVO importProjectCases(Long projectId, MultipartFile file, Long operatorId) {
        List<TestCaseImportResultVO.RowError> failures = new ArrayList<>();
        int total = 0;
        int success = 0;

        // 目录名 → id 缓存（行间复用，避免重复查询/建目录）
        Map<String, Long> dirCache = new HashMap<>();
        for (DirectoryVO d : directoryService.list(projectId, operatorId)) {
            dirCache.put(d.name(), d.id());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            // P1 加固：行数上限（超出直接 413，拒绝整文件而非截断——调用方明确知道超限）
            if (sheet.getLastRowNum() > MAX_ROWS) {
                throw new BusinessException(413, "导入文件超过最大行数 " + MAX_ROWS + "，请分批导入");
            }
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isEmptyRow(row)) {
                    continue;
                }
                total++;
                try {
                    String title = text(row, 1);
                    if (title.isBlank()) {
                        throw new BusinessException(400, "用例标题不能为空");
                    }
                    if (title.length() > 200) {
                        throw new BusinessException(400, "用例标题最长 200 字符");
                    }
                    TestCaseType type = parseEnum(r, TestCaseType.class, text(row, 3), "类型", TestCaseType.FUNCTIONAL);
                    TestCasePriority priority = parseEnum(r, TestCasePriority.class, text(row, 4), "优先级", TestCasePriority.MEDIUM);
                    TestCaseStatus status = parseEnum(r, TestCaseStatus.class, text(row, 5), "状态", TestCaseStatus.DRAFT);
                    if (status == TestCaseStatus.DEPRECATED) {
                        throw new BusinessException(400, "状态不能为 已废弃");
                    }
                    Long directoryId = resolveDirectory(projectId, operatorId, text(row, 2), dirCache);

                    testCaseService.create(projectId, new CreateTestCaseRequest(
                            title, text(row, 6), text(row, 7), text(row, 8), type, priority, status, directoryId),
                            operatorId);
                    success++;
                } catch (Exception e) {
                    failures.add(new TestCaseImportResultVO.RowError(r + 1, e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new BusinessException(400, "无法读取 Excel 文件，请使用模板格式（.xlsx）");
        }
        return new TestCaseImportResultVO(total, success, failures.size(), failures);
    }

    private Long resolveDirectory(Long projectId, Long operatorId, String dirName, Map<String, Long> dirCache) {
        String name = dirName == null || dirName.isBlank() ? null : dirName.trim();
        if (name == null) {
            return null;    // 未分类
        }
        if (dirCache.containsKey(name)) {
            return dirCache.get(name);
        }
        // 不存在即创建（导入场景的便利语义；名称冲突由目录服务唯一性兜底）
        DirectoryVO created = directoryService.create(projectId, new CreateTestCaseDirectoryRequest(name, null), operatorId);
        dirCache.put(name, created.id());
        return created.id();
    }

    // ===== 枚举双语映射 =====

    private static final Map<TestCaseType, String> TYPE_ZH = Map.of(
            TestCaseType.FUNCTIONAL, "功能", TestCaseType.REGRESSION, "回归",
            TestCaseType.SMOKE, "冒烟", TestCaseType.SECURITY, "安全", TestCaseType.PERFORMANCE, "性能");
    private static final Map<TestCasePriority, String> PRIORITY_ZH = Map.of(
            TestCasePriority.LOW, "低", TestCasePriority.MEDIUM, "中",
            TestCasePriority.HIGH, "高", TestCasePriority.CRITICAL, "关键");
    private static final Map<TestCaseStatus, String> STATUS_ZH = Map.of(
            TestCaseStatus.DRAFT, "草稿", TestCaseStatus.ACTIVE, "启用", TestCaseStatus.DEPRECATED, "已废弃");

    private <E extends Enum<E>> String enumZh(Class<E> type, E value) {
        if (value == null) {
            return "";
        }
        if (type == TestCaseType.class) return TYPE_ZH.getOrDefault((TestCaseType) value, value.name());
        if (type == TestCasePriority.class) return PRIORITY_ZH.getOrDefault((TestCasePriority) value, value.name());
        return STATUS_ZH.getOrDefault((TestCaseStatus) value, value.name());
    }

    /** 双语解析：中文标签优先，英文枚举值兜底；空单元格返回默认值 */
    private <E extends Enum<E>> E parseEnum(int rowNumber, Class<E> type, String raw, String column, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String v = raw.trim();
        Map<String, E> table = new HashMap<>();
        if (type == TestCaseType.class) {
            for (TestCaseType e : TestCaseType.values()) table.put(TYPE_ZH.get(e), type.cast(e));
        } else if (type == TestCasePriority.class) {
            for (TestCasePriority e : TestCasePriority.values()) table.put(PRIORITY_ZH.get(e), type.cast(e));
        } else {
            for (TestCaseStatus e : TestCaseStatus.values()) table.put(STATUS_ZH.get(e), type.cast(e));
        }
        E byZh = table.get(v);
        if (byZh != null) {
            return byZh;
        }
        try {
            return Enum.valueOf(type, v.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, column + "「" + v + "」不合法（可用："
                    + String.join("/", table.keySet()) + "）");
        }
    }

    // ===== 单元格工具 =====

    private Map<Long, String> directoryNames(Long projectId, Long operatorId) {
        Map<Long, String> names = new HashMap<>();
        for (DirectoryVO d : directoryService.list(projectId, operatorId)) {
            names.put(d.id(), d.name());
        }
        return names;
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int c = 0; c < HEADERS.length; c++) {
            String v = text(row, c);
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String text(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return "";
        }
        String value;
        if (cell.getCellType() == CellType.NUMERIC) {
            double num = cell.getNumericCellValue();
            value = num == Math.floor(num) ? String.valueOf((long) num) : String.valueOf(num);
        } else {
            value = cell.toString();
        }
        // P1 加固：单元格字符上限（内容列本有 65535 上限，提前拦截巨行解析开销）
        if (value != null && value.length() > MAX_CELL_CHARS) {
            throw new BusinessException(413, "第 " + (row.getRowNum() + 1) + " 行第 " + (col + 1)
                    + " 列超过 " + MAX_CELL_CHARS + " 字符，请拆分内容");
        }
        return value == null ? "" : value.trim();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
