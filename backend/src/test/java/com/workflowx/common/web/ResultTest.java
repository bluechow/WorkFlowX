package com.workflowx.common.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 统一响应结构单元测试（任务 1-7.1）。 */
class ResultTest {

    @Test
    void okShouldCarrySuccessCodeAndData() {
        Result<String> result = Result.ok("hello");
        assertEquals(200, result.code());
        assertEquals("success", result.message());
        assertEquals("hello", result.data());
        assertNotNull(result.timestamp());
    }

    @Test
    void ofShouldCarryErrorCodeWithoutData() {
        Result<Void> result = Result.of(404, "resource not found", null);
        assertEquals(404, result.code());
        assertEquals("resource not found", result.message());
        assertNull(result.data());
        assertNotNull(result.timestamp());
    }
}
