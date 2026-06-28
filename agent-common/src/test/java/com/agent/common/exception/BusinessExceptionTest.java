package com.agent.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessExceptionTest {

    @Test
    @DisplayName("使用 ErrorCode 与 message 构造 BusinessException 应正确设置字段")
    void should_SetFields_When_ConstructedWithErrorCodeAndMessage() {
        BusinessException ex = new BusinessException(ErrorCode.TASK_NOT_FOUND, "任务不存在");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("任务不存在");
        assertThat(ex.getErrorCode().getHttpStatus()).isEqualTo(404);
        assertThat(ex.getErrorCode().getCode()).isEqualTo("TASK_NOT_FOUND");
    }

    @Test
    @DisplayName("使用 details 构造 BusinessException 应返回 details Map")
    void should_ReturnDetailsMap_When_ConstructedWithDetails() {
        Map<String, Object> details = Map.of("taskId", "tk_xxx");
        BusinessException ex = new BusinessException(ErrorCode.VALIDATION_FAILED, "参数错误", details);
        assertThat(ex.getDetails()).isEqualTo(details);
        assertThat(ex.getErrorCode().getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(ex.getErrorCode().getHttpStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("使用 cause 构造 BusinessException 应保留 cause")
    void should_PreserveCause_When_ConstructedWithCause() {
        Throwable cause = new RuntimeException("db connection lost");
        BusinessException ex = new BusinessException(ErrorCode.INTERNAL, "内部错误", cause);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getErrorCode().getHttpStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("FORBIDDEN 与 TOOL_RISK_DENIED 的 HTTP 状态码应为 403")
    void should_ReturnHttpStatus403_When_ErrorCodeIsForbidden() {
        assertThat(ErrorCode.FORBIDDEN.getHttpStatus()).isEqualTo(403);
        assertThat(ErrorCode.TOOL_RISK_DENIED.getHttpStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("COST_BUDGET_EXCEEDED 与 RATE_LIMITED 的 HTTP 状态码应为 429")
    void should_ReturnHttpStatus429_When_ErrorCodeIsCostBudgetExceeded() {
        assertThat(ErrorCode.COST_BUDGET_EXCEEDED.getHttpStatus()).isEqualTo(429);
        assertThat(ErrorCode.RATE_LIMITED.getHttpStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("DAG_CYCLE_DETECTED 与 TASK_STATUS_CONFLICT 的 HTTP 状态码应为 409")
    void should_ReturnHttpStatus409_When_ErrorCodeIsDagCycleDetected() {
        assertThat(ErrorCode.DAG_CYCLE_DETECTED.getHttpStatus()).isEqualTo(409);
        assertThat(ErrorCode.TASK_STATUS_CONFLICT.getHttpStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("业务逻辑类错误的 HTTP 状态码应为 500")
    void should_ReturnHttpStatus500_When_ErrorCodeIsBusinessLogicError() {
        assertThat(ErrorCode.COMPLETENESS_FAIL.getHttpStatus()).isEqualTo(500);
        assertThat(ErrorCode.REPLAN_EXHAUSTED.getHttpStatus()).isEqualTo(500);
        assertThat(ErrorCode.HALLUCINATION_SUSPECTED.getHttpStatus()).isEqualTo(500);
        assertThat(ErrorCode.FACT_INCONSISTENCY.getHttpStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("TIMEOUT 类错误的 HTTP 状态码应为 504")
    void should_ReturnHttpStatus504_When_ErrorCodeIsTimeout() {
        assertThat(ErrorCode.TIMEOUT.getHttpStatus()).isEqualTo(504);
        assertThat(ErrorCode.TOOL_TIMEOUT.getHttpStatus()).isEqualTo(504);
        assertThat(ErrorCode.MODEL_TIMEOUT.getHttpStatus()).isEqualTo(504);
    }

    @Test
    @DisplayName("运行时类错误的 HTTP 状态码应为 500")
    void should_ReturnHttpStatus500_When_ErrorCodeIsRuntimeError() {
        assertThat(ErrorCode.MAX_STEPS_EXCEEDED.getHttpStatus()).isEqualTo(500);
        assertThat(ErrorCode.CONTEXT_WINDOW_EXHAUSTED.getHttpStatus()).isEqualTo(500);
        assertThat(ErrorCode.MODEL_GATEWAY_ERROR.getHttpStatus()).isEqualTo(500);
    }

    /**
     * FN-009 整改：补 assertThrows 异常断言。
     *
     * <p>BusinessException(ErrorCode) 单参数构造函数第一行调用 {@code super(errorCode.getDefaultMessage())}，
     * 当 errorCode 为 null 时应抛 NullPointerException。原测试只覆盖正常构造路径，
     * 未断言 null 入参的失败行为，审计发现 FN-009 缺 assertThrows 用例，本测试填补。</p>
     */
    @Test
    @DisplayName("使用 null ErrorCode 构造时应抛 NullPointerException")
    void should_ThrowNullPointerException_When_ConstructedWithNullErrorCode() {
        assertThatThrownBy(() -> new BusinessException((ErrorCode) null))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * FN-009 整改：补 assertThrows 异常断言。
     *
     * <p>BusinessException(ErrorCode, String, Map) 三参数构造函数中 details 为 null 会被兜底为空 Map
     * （第 30 行 {@code details == null ? Collections.emptyMap() : details}），但若 errorCode 为 null，
     * super(message) 之后赋值时不会抛错——但若调用方后续调 {@code getErrorCode()} 取到 null，
     * 在使用 errorCode 时才会 NPE。这里验证构造本身允许 errorCode=null + message + details=null。</p>
     *
     * <p><b>P6-4 注</b>：原 {@code assertDoesNotThrow} 已替换为直接构造。
     * AssertJ 的 {@code assertThatCode(...).doesNotThrowAnyException()} 不返回被测对象本身，
     * 而若构造抛出，JUnit 会让测试用例直接失败，行为与 {@code assertDoesNotThrow} 等价，
     * 故此处直接构造以保持断言语义等价且代码更简洁。</p>
     */
    @Test
    @DisplayName("errorCode=null 但 message/details 合法时构造本身不应抛出，使用 errorCode 时抛 NPE")
    void should_NotThrowWhenConstructing_When_ErrorCodeIsNullButMessageAndDetailsValid() {
        // 构造本身不抛 (若抛出则测试直接失败，与原 assertDoesNotThrow 行为等价)
        // 显式转型为 Map 避免 (ErrorCode, String, Map) 与 (ErrorCode, String, Throwable) 重载歧义
        BusinessException ex = new BusinessException(null, "msg", (Map<String, Object>) null);
        assertThat(ex.getErrorCode()).isNull();
        assertThat(ex.getMessage()).isEqualTo("msg");
        assertThat(ex.getDetails()).isNotNull();
        assertThat(ex.getDetails().isEmpty()).isTrue();

        // 使用 errorCode 应抛 NPE，证明 errorCode=null 是无效入参
        assertThatThrownBy(() -> ex.getErrorCode().getHttpStatus())
                .isInstanceOf(NullPointerException.class);
    }
}
