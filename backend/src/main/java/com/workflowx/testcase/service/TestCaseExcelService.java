package com.workflowx.testcase.service;

import com.workflowx.testcase.dto.TestCaseImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用例 Excel 导入导出（Phase A-⑦）。
 * 列契约（首行为表头）：用例编号 | 用例标题 | 目录 | 类型 | 优先级 | 状态 | 前置条件 | 测试步骤 | 预期结果。
 * 枚举接受中文标签或英文枚举值（如「功能」或 FUNCTIONAL）；编号列仅导出用，导入忽略。
 */
public interface TestCaseExcelService {

    /** 导出项目内全部用例为 xlsx 字节流（含目录名与中文枚举） */
    byte[] exportProjectCases(Long projectId, Long operatorId);

    /**
     * 导入用例：逐行创建（目录按名称匹配，不存在则创建），返回汇总与逐行错误。
     * 行级失败不影响其他行；标题为空/枚举非法等错误以「第 N 行：原因」返回。
     */
    TestCaseImportResultVO importProjectCases(Long projectId, MultipartFile file, Long operatorId);
}
