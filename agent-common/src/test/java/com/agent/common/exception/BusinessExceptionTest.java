package com.agent.common.exception;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BusinessExceptionTest {

    @Test
    void construct_withErrorCodeAndMessage_setsFields() {
        BusinessException ex = new BusinessException(ErrorCode.TASK_NOT_FOUND, "任务不存在");
        assertEquals(ErrorCode.TASK_NOT_FOUND, ex.getErrorCode());
        assertEquals("任务不存在", ex.getMessage());
        assertEquals(404, ex.getErrorCode().getHttpStatus());
        assertEquals("TASK_NOT_FOUND", ex.getErrorCode().getCode());
    }

    @Test
    void construct_withDetails_returnsDetailsMap() {
        Map<String, Object> details = Map.of("taskId", "tk_xxx");
        BusinessException ex = new BusinessException(ErrorCode.VALIDATION_FAILED, "参数错误", details);
        assertEquals(details, ex.getDetails());
        assertEquals("VALIDATION_FAILED", ex.getErrorCode().getCode());
        assertEquals(400, ex.getErrorCode().getHttpStatus());
    }

    @Test
    void construct_withCause_preservesCause() {
        Throwable cause = new RuntimeException("db connection lost");
        BusinessException ex = new BusinessException(ErrorCode.INTERNAL, "内部错误", cause);
        assertSame(cause, ex.getCause());
        assertEquals(500, ex.getErrorCode().getHttpStatus());
    }

    @Test
    void errorCode_forbiddenHasHttpStatus403() {
        assertEquals(403, ErrorCode.FORBIDDEN.getHttpStatus());
        assertEquals(403, ErrorCode.TOOL_RISK_DENIED.getHttpStatus());
    }

    @Test
    void errorCode_costBudgetExceededIs429() {
        assertEquals(429, ErrorCode.COST_BUDGET_EXCEEDED.getHttpStatus());
        assertEquals(429, ErrorCode.RATE_LIMITED.getHttpStatus());
    }

    @Test
    void errorCode_dagCycleDetectedIs409() {
        assertEquals(409, ErrorCode.DAG_CYCLE_DETECTED.getHttpStatus());
        assertEquals(409, ErrorCode.TASK_STATUS_CONFLICT.getHttpStatus());
    }

    @Test
    void errorCode_businessLogicErrorsAre500() {
        assertEquals(500, ErrorCode.COMPLETENESS_FAIL.getHttpStatus());
        assertEquals(500, ErrorCode.REPLAN_EXHAUSTED.getHttpStatus());
        assertEquals(500, ErrorCode.HALLUCINATION_SUSPECTED.getHttpStatus());
        assertEquals(500, ErrorCode.FACT_INCONSISTENCY.getHttpStatus());
    }

    @Test
    void errorCode_timeoutIs504() {
        assertEquals(504, ErrorCode.TIMEOUT.getHttpStatus());
        assertEquals(504, ErrorCode.TOOL_TIMEOUT.getHttpStatus());
        assertEquals(504, ErrorCode.MODEL_TIMEOUT.getHttpStatus());
    }

    @Test
    void errorCode_runtimeErrorsAre500() {
        assertEquals(500, ErrorCode.MAX_STEPS_EXCEEDED.getHttpStatus());
        assertEquals(500, ErrorCode.CONTEXT_WINDOW_EXHAUSTED.getHttpStatus());
        assertEquals(500, ErrorCode.MODEL_GATEWAY_ERROR.getHttpStatus());
    }
}
