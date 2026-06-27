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

    /**
     * FN-009 整改：补 assertThrows 异常断言。
     *
     * <p>BusinessException(ErrorCode) 单参数构造函数第一行调用 {@code super(errorCode.getDefaultMessage())}，
     * 当 errorCode 为 null 时应抛 NullPointerException。原测试只覆盖正常构造路径，
     * 未断言 null 入参的失败行为，审计发现 FN-009 缺 assertThrows 用例，本测试填补。</p>
     */
    @Test
    void construct_withNullErrorCode_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new BusinessException((ErrorCode) null));
    }

    /**
     * FN-009 整改：补 assertThrows 异常断言。
     *
     * <p>BusinessException(ErrorCode, String, Map) 三参数构造函数中 details 为 null 会被兜底为空 Map
     * （第 30 行 {@code details == null ? Collections.emptyMap() : details}），但若 errorCode 为 null，
     * super(message) 之后赋值时不会抛错——但调用方后续调 {@code getErrorCode()} 取到 null，
     * 在使用 errorCode 时才会 NPE。这里验证构造本身允许 errorCode=null + message + details=null。</p>
     */
    @Test
    void construct_withNullErrorCodeButValidMessageAndDetails_doesNotThrow() {
        // 构造本身不抛，但使用 errorCode 时会 NPE
        // 显式转型为 Map 避免 (ErrorCode, String, Map) 与 (ErrorCode, String, Throwable) 重载歧义
        BusinessException ex = assertDoesNotThrow(
                () -> new BusinessException(null, "msg", (Map<String, Object>) null));
        assertNull(ex.getErrorCode());
        assertEquals("msg", ex.getMessage());
        assertNotNull(ex.getDetails());
        assertTrue(ex.getDetails().isEmpty());

        // 使用 errorCode 应抛 NPE，证明 errorCode=null 是无效入参
        assertThrows(NullPointerException.class,
                () -> ex.getErrorCode().getHttpStatus());
    }
}
