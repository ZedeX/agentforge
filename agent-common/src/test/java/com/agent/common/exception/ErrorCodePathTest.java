package com.agent.common.exception;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P6-7 (COV-04)：错误码「触发路径」与「字段完整性」覆盖测试。
 *
 * <p>对 {@link ErrorCode} 中除 {@code OK} 外的全部错误码（共 29 个）逐个验证：
 * <ol>
 *   <li>字段完整性：{@code code} 字符串、{@code httpStatus}、{@code defaultMessage} 三者正确；</li>
 *   <li>构造回环：{@code new BusinessException(ErrorCode)} 单参构造后 {@code getMessage()} 应等于
 *       defaultMessage，{@code getErrorCode()} 应返回原枚举，{@code getDetails()} 应为空 Map；</li>
 *   <li>触发路径：模拟真实业务条件构造并抛出 {@link BusinessException}，断言其 errorCode 与 message 与预期一致。</li>
 * </ol>
 *
 * <p>本测试不重复 {@link BusinessExceptionTest} 已覆盖的「批量 HttpStatus 断言」用例，
 * 聚焦每个错误码的「字段完整性 + 触发路径」单测。</p>
 */
class ErrorCodePathTest {

    // ============ 断言辅助 ============

    /** 断言错误码三字段完整性：code 字符串、httpStatus、defaultMessage。 */
    private static void assertErrorCode(ErrorCode ec, String code, int httpStatus, String defaultMessage) {
        assertThat(ec.getCode()).isEqualTo(code);
        assertThat(ec.getHttpStatus()).isEqualTo(httpStatus);
        assertThat(ec.getDefaultMessage()).isEqualTo(defaultMessage);
    }

    /** 断言单参构造 {@code new BusinessException(ec)} 回环：message=defaultMessage、errorCode=原枚举、details 空。 */
    private static void assertRoundTrip(ErrorCode ec, String defaultMessage) {
        BusinessException ex = new BusinessException(ec);
        assertThat(ex.getErrorCode()).isSameAs(ec);
        assertThat(ex.getMessage()).isEqualTo(defaultMessage);
        assertThat(ex.getDetails()).isEmpty();
    }

    /** 断言触发路径：执行 trigger 应抛 BusinessException，且 errorCode 与 message 与预期一致。 */
    private static void assertTriggered(ThrowingCallable trigger, ErrorCode expected, String expectedMessage) {
        assertThatThrownBy(trigger)
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::getErrorCode, BusinessException::getMessage)
                .containsExactly(expected, expectedMessage);
    }

    // ============ 401 未认证 ============

    @Test
    @DisplayName("UNAUTHENTICATED：未携带 token 触发未认证错误码")
    void should_TriggerUnauthenticated_When_NoAuthToken() {
        ErrorCode ec = ErrorCode.UNAUTHENTICATED;
        assertErrorCode(ec, "UNAUTHENTICATED", 401, "未认证");
        assertRoundTrip(ec, "未认证");
        assertTriggered(() -> {
            String token = null;
            if (token == null) {
                throw new BusinessException(ec, "缺少认证 token");
            }
        }, ec, "缺少认证 token");
    }

    // ============ 403 无权限 ============

    @Test
    @DisplayName("FORBIDDEN：无权限触发禁止访问错误码")
    void should_TriggerForbidden_When_NoPermission() {
        ErrorCode ec = ErrorCode.FORBIDDEN;
        assertErrorCode(ec, "FORBIDDEN", 403, "无权限");
        assertRoundTrip(ec, "无权限");
        assertTriggered(() -> {
            boolean allowed = false;
            if (!allowed) {
                throw new BusinessException(ec, "无访问权限");
            }
        }, ec, "无访问权限");
    }

    @Test
    @DisplayName("TOOL_RISK_DENIED：工具风险评分过高触发工具风险被拒绝错误码")
    void should_TriggerToolRiskDenied_When_RiskScoreTooHigh() {
        ErrorCode ec = ErrorCode.TOOL_RISK_DENIED;
        assertErrorCode(ec, "TOOL_RISK_DENIED", 403, "工具风险被拒绝");
        assertRoundTrip(ec, "工具风险被拒绝");
        assertTriggered(() -> {
            int riskScore = 90;
            if (riskScore >= 80) {
                throw new BusinessException(ec, "工具风险评分过高");
            }
        }, ec, "工具风险评分过高");
    }

    // ============ 404 资源不存在 ============

    @Test
    @DisplayName("TASK_NOT_FOUND：任务查询为空触发任务不存在错误码")
    void should_TriggerTaskNotFound_When_TaskIdIsNull() {
        ErrorCode ec = ErrorCode.TASK_NOT_FOUND;
        assertErrorCode(ec, "TASK_NOT_FOUND", 404, "任务不存在");
        assertRoundTrip(ec, "任务不存在");
        assertTriggered(() -> {
            String taskId = null;
            if (taskId == null) {
                throw new BusinessException(ec, "任务不存在: null");
            }
        }, ec, "任务不存在: null");
    }

    @Test
    @DisplayName("AGENT_NOT_FOUND：Agent 查询为空触发 Agent 不存在错误码")
    void should_TriggerAgentNotFound_When_AgentIdIsNull() {
        ErrorCode ec = ErrorCode.AGENT_NOT_FOUND;
        assertErrorCode(ec, "AGENT_NOT_FOUND", 404, "Agent 不存在");
        assertRoundTrip(ec, "Agent 不存在");
        assertTriggered(() -> {
            String agentId = null;
            if (agentId == null) {
                throw new BusinessException(ec, "Agent 不存在: null");
            }
        }, ec, "Agent 不存在: null");
    }

    @Test
    @DisplayName("TOOL_NOT_FOUND：工具查询为空触发工具不存在错误码")
    void should_TriggerToolNotFound_When_ToolIdIsNull() {
        ErrorCode ec = ErrorCode.TOOL_NOT_FOUND;
        assertErrorCode(ec, "TOOL_NOT_FOUND", 404, "工具不存在");
        assertRoundTrip(ec, "工具不存在");
        assertTriggered(() -> {
            String toolId = null;
            if (toolId == null) {
                throw new BusinessException(ec, "工具不存在: null");
            }
        }, ec, "工具不存在: null");
    }

    // ============ 400 参数校验 ============

    @Test
    @DisplayName("VALIDATION_FAILED：校验不通过触发参数校验失败错误码")
    void should_TriggerValidationFailed_When_ValidationNotPassed() {
        ErrorCode ec = ErrorCode.VALIDATION_FAILED;
        assertErrorCode(ec, "VALIDATION_FAILED", 400, "参数校验失败");
        assertRoundTrip(ec, "参数校验失败");
        assertTriggered(() -> {
            boolean valid = false;
            if (!valid) {
                throw new BusinessException(ec, "参数校验失败");
            }
        }, ec, "参数校验失败");
    }

    @Test
    @DisplayName("PARAM_INVALID：参数非法触发参数非法错误码")
    void should_TriggerParamInvalid_When_ParamIsInvalid() {
        ErrorCode ec = ErrorCode.PARAM_INVALID;
        assertErrorCode(ec, "PARAM_INVALID", 400, "参数非法");
        assertRoundTrip(ec, "参数非法");
        assertTriggered(() -> {
            boolean invalid = true;
            if (invalid) {
                throw new BusinessException(ec, "参数非法: id 必填");
            }
        }, ec, "参数非法: id 必填");
    }

    @Test
    @DisplayName("CONTENT_BLOCKED：内容命中拦截规则触发内容被拦截错误码")
    void should_TriggerContentBlocked_When_ContentFiltered() {
        ErrorCode ec = ErrorCode.CONTENT_BLOCKED;
        assertErrorCode(ec, "CONTENT_BLOCKED", 400, "内容被拦截");
        assertRoundTrip(ec, "内容被拦截");
        assertTriggered(() -> {
            boolean blocked = true;
            if (blocked) {
                throw new BusinessException(ec, "内容被拦截");
            }
        }, ec, "内容被拦截");
    }

    // ============ 413 请求体过大 ============

    @Test
    @DisplayName("PAYLOAD_TOO_LARGE：请求体超过阈值触发请求体过大错误码")
    void should_TriggerPayloadTooLarge_When_PayloadExceedsLimit() {
        ErrorCode ec = ErrorCode.PAYLOAD_TOO_LARGE;
        assertErrorCode(ec, "PAYLOAD_TOO_LARGE", 413, "请求体过大");
        assertRoundTrip(ec, "请求体过大");
        assertTriggered(() -> {
            long bytes = 11_000_000L;
            if (bytes > 10_000_000L) {
                throw new BusinessException(ec, "请求体过大: " + bytes);
            }
        }, ec, "请求体过大: 11000000");
    }

    // ============ 409 状态冲突 ============

    @Test
    @DisplayName("TASK_STATUS_CONFLICT：非法状态迁移触发任务状态冲突错误码")
    void should_TriggerTaskStatusConflict_When_IllegalStatusTransition() {
        ErrorCode ec = ErrorCode.TASK_STATUS_CONFLICT;
        assertErrorCode(ec, "TASK_STATUS_CONFLICT", 409, "任务状态冲突");
        assertRoundTrip(ec, "任务状态冲突");
        assertTriggered(() -> {
            String from = "DONE";
            String to = "RUNNING";
            if ("DONE".equals(from) && "RUNNING".equals(to)) {
                throw new BusinessException(ec, "任务状态冲突");
            }
        }, ec, "任务状态冲突");
    }

    @Test
    @DisplayName("DAG_CYCLE_DETECTED：DAG 出现环触发 DAG 检测到环错误码")
    void should_TriggerDagCycleDetected_When_CycleFound() {
        ErrorCode ec = ErrorCode.DAG_CYCLE_DETECTED;
        assertErrorCode(ec, "DAG_CYCLE_DETECTED", 409, "DAG 检测到环");
        assertRoundTrip(ec, "DAG 检测到环");
        assertTriggered(() -> {
            boolean hasCycle = true;
            if (hasCycle) {
                throw new BusinessException(ec, "DAG 检测到环");
            }
        }, ec, "DAG 检测到环");
    }

    @Test
    @DisplayName("DAG_VERSION_CONFLICT：DAG 版本不一致触发 DAG 版本冲突错误码")
    void should_TriggerDagVersionConflict_When_VersionMismatch() {
        ErrorCode ec = ErrorCode.DAG_VERSION_CONFLICT;
        assertErrorCode(ec, "DAG_VERSION_CONFLICT", 409, "DAG 版本冲突");
        assertRoundTrip(ec, "DAG 版本冲突");
        assertTriggered(() -> {
            int current = 2;
            int expected = 3;
            if (current != expected) {
                throw new BusinessException(ec, "DAG 版本冲突");
            }
        }, ec, "DAG 版本冲突");
    }

    // ============ 429 限流 ============

    @Test
    @DisplayName("RATE_LIMITED：请求超过阈值触发限流错误码")
    void should_TriggerRateLimited_When_RequestsExceedLimit() {
        ErrorCode ec = ErrorCode.RATE_LIMITED;
        assertErrorCode(ec, "RATE_LIMITED", 429, "限流");
        assertRoundTrip(ec, "限流");
        assertTriggered(() -> {
            int requests = 101;
            if (requests > 100) {
                throw new BusinessException(ec, "限流");
            }
        }, ec, "限流");
    }

    @Test
    @DisplayName("QUOTA_EXCEEDED：用量达到配额触发配额超限错误码")
    void should_TriggerQuotaExceeded_When_UsageReachesQuota() {
        ErrorCode ec = ErrorCode.QUOTA_EXCEEDED;
        assertErrorCode(ec, "QUOTA_EXCEEDED", 429, "配额超限");
        assertRoundTrip(ec, "配额超限");
        assertTriggered(() -> {
            long used = 100L;
            long quota = 100L;
            if (used >= quota) {
                throw new BusinessException(ec, "配额超限");
            }
        }, ec, "配额超限");
    }

    @Test
    @DisplayName("COST_BUDGET_EXCEEDED：成本超出预算触发成本预算超限错误码")
    void should_TriggerCostBudgetExceeded_When_CostExceedsBudget() {
        ErrorCode ec = ErrorCode.COST_BUDGET_EXCEEDED;
        assertErrorCode(ec, "COST_BUDGET_EXCEEDED", 429, "成本预算超限");
        assertRoundTrip(ec, "成本预算超限");
        assertTriggered(() -> {
            double cost = 12.5;
            double budget = 10.0;
            if (cost > budget) {
                throw new BusinessException(ec, "成本预算超限");
            }
        }, ec, "成本预算超限");
    }

    // ============ 500 内部错误 ============

    @Test
    @DisplayName("INTERNAL：内部故障触发内部错误错误码")
    void should_TriggerInternal_When_InternalFailure() {
        ErrorCode ec = ErrorCode.INTERNAL;
        assertErrorCode(ec, "INTERNAL", 500, "内部错误");
        assertRoundTrip(ec, "内部错误");
        assertTriggered(() -> {
            boolean fail = true;
            if (fail) {
                throw new BusinessException(ec, "内部错误");
            }
        }, ec, "内部错误");
    }

    @Test
    @DisplayName("MODEL_GATEWAY_ERROR：模型网关不可用触发模型网关错误错误码")
    void should_TriggerModelGatewayError_When_GatewayDown() {
        ErrorCode ec = ErrorCode.MODEL_GATEWAY_ERROR;
        assertErrorCode(ec, "MODEL_GATEWAY_ERROR", 500, "模型网关错误");
        assertRoundTrip(ec, "模型网关错误");
        assertTriggered(() -> {
            boolean gatewayDown = true;
            if (gatewayDown) {
                throw new BusinessException(ec, "模型网关错误");
            }
        }, ec, "模型网关错误");
    }

    @Test
    @DisplayName("COMPLETENESS_FAIL：完整性校验未通过触发完整性校验失败错误码")
    void should_TriggerCompletenessFail_When_CompletenessNotMet() {
        ErrorCode ec = ErrorCode.COMPLETENESS_FAIL;
        assertErrorCode(ec, "COMPLETENESS_FAIL", 500, "完整性校验失败");
        assertRoundTrip(ec, "完整性校验失败");
        assertTriggered(() -> {
            boolean complete = false;
            if (!complete) {
                throw new BusinessException(ec, "完整性校验失败");
            }
        }, ec, "完整性校验失败");
    }

    @Test
    @DisplayName("REPLAN_EXHAUSTED：重规划次数达到上限触发重规划次数耗尽错误码")
    void should_TriggerReplanExhausted_When_ReplanCountReachesMax() {
        ErrorCode ec = ErrorCode.REPLAN_EXHAUSTED;
        assertErrorCode(ec, "REPLAN_EXHAUSTED", 500, "重规划次数耗尽");
        assertRoundTrip(ec, "重规划次数耗尽");
        assertTriggered(() -> {
            int count = 3;
            int maxReplans = 3;
            if (count >= maxReplans) {
                throw new BusinessException(ec, "重规划次数耗尽");
            }
        }, ec, "重规划次数耗尽");
    }

    @Test
    @DisplayName("HALLUCINATION_SUSPECTED：检测到幻觉触发疑似幻觉错误码")
    void should_TriggerHallucinationSuspected_When_HallucinationDetected() {
        ErrorCode ec = ErrorCode.HALLUCINATION_SUSPECTED;
        assertErrorCode(ec, "HALLUCINATION_SUSPECTED", 500, "疑似幻觉");
        assertRoundTrip(ec, "疑似幻觉");
        assertTriggered(() -> {
            boolean suspected = true;
            if (suspected) {
                throw new BusinessException(ec, "疑似幻觉");
            }
        }, ec, "疑似幻觉");
    }

    @Test
    @DisplayName("FACT_INCONSISTENCY：事实校验不一致触发事实不一致错误码")
    void should_TriggerFactInconsistency_When_FactNotConsistent() {
        ErrorCode ec = ErrorCode.FACT_INCONSISTENCY;
        assertErrorCode(ec, "FACT_INCONSISTENCY", 500, "事实不一致");
        assertRoundTrip(ec, "事实不一致");
        assertTriggered(() -> {
            boolean consistent = false;
            if (!consistent) {
                throw new BusinessException(ec, "事实不一致");
            }
        }, ec, "事实不一致");
    }

    @Test
    @DisplayName("MAX_STEPS_EXCEEDED：步数超过上限触发超过最大步数错误码")
    void should_TriggerMaxStepsExceeded_When_StepsExceedMax() {
        ErrorCode ec = ErrorCode.MAX_STEPS_EXCEEDED;
        assertErrorCode(ec, "MAX_STEPS_EXCEEDED", 500, "超过最大步数");
        assertRoundTrip(ec, "超过最大步数");
        assertTriggered(() -> {
            int steps = 21;
            int maxSteps = 20;
            if (steps > maxSteps) {
                throw new BusinessException(ec, "超过最大步数");
            }
        }, ec, "超过最大步数");
    }

    @Test
    @DisplayName("CONTEXT_WINDOW_EXHAUSTED：token 超过窗口上限触发上下文窗口耗尽错误码")
    void should_TriggerContextWindowExhausted_When_TokensExceedWindow() {
        ErrorCode ec = ErrorCode.CONTEXT_WINDOW_EXHAUSTED;
        assertErrorCode(ec, "CONTEXT_WINDOW_EXHAUSTED", 500, "上下文窗口耗尽");
        assertRoundTrip(ec, "上下文窗口耗尽");
        assertTriggered(() -> {
            long tokens = 130_000L;
            long max = 128_000L;
            if (tokens > max) {
                throw new BusinessException(ec, "上下文窗口耗尽");
            }
        }, ec, "上下文窗口耗尽");
    }

    // ============ 503 服务不可用 ============

    @Test
    @DisplayName("DEPENDENCY_DOWN：依赖服务不可用触发依赖服务不可用错误码")
    void should_TriggerDependencyDown_When_DependencyUnavailable() {
        ErrorCode ec = ErrorCode.DEPENDENCY_DOWN;
        assertErrorCode(ec, "DEPENDENCY_DOWN", 503, "依赖服务不可用");
        assertRoundTrip(ec, "依赖服务不可用");
        assertTriggered(() -> {
            boolean down = true;
            if (down) {
                throw new BusinessException(ec, "依赖服务不可用");
            }
        }, ec, "依赖服务不可用");
    }

    @Test
    @DisplayName("CIRCUIT_OPEN：熔断器开启触发熔断开启错误码")
    void should_TriggerCircuitOpen_When_CircuitBreakerOpen() {
        ErrorCode ec = ErrorCode.CIRCUIT_OPEN;
        assertErrorCode(ec, "CIRCUIT_OPEN", 503, "熔断开启");
        assertRoundTrip(ec, "熔断开启");
        assertTriggered(() -> {
            boolean open = true;
            if (open) {
                throw new BusinessException(ec, "熔断开启");
            }
        }, ec, "熔断开启");
    }

    // ============ 504 超时 ============

    @Test
    @DisplayName("TIMEOUT：操作超时触发超时错误码")
    void should_TriggerTimeout_When_OperationTimesOut() {
        ErrorCode ec = ErrorCode.TIMEOUT;
        assertErrorCode(ec, "TIMEOUT", 504, "超时");
        assertRoundTrip(ec, "超时");
        assertTriggered(() -> {
            boolean timeout = true;
            if (timeout) {
                throw new BusinessException(ec, "超时");
            }
        }, ec, "超时");
    }

    @Test
    @DisplayName("TOOL_TIMEOUT：工具调用超时触发工具调用超时错误码")
    void should_TriggerToolTimeout_When_ToolCallTimesOut() {
        ErrorCode ec = ErrorCode.TOOL_TIMEOUT;
        assertErrorCode(ec, "TOOL_TIMEOUT", 504, "工具调用超时");
        assertRoundTrip(ec, "工具调用超时");
        assertTriggered(() -> {
            boolean timeout = true;
            if (timeout) {
                throw new BusinessException(ec, "工具调用超时");
            }
        }, ec, "工具调用超时");
    }

    @Test
    @DisplayName("MODEL_TIMEOUT：模型调用超时触发模型调用超时错误码")
    void should_TriggerModelTimeout_When_ModelCallTimesOut() {
        ErrorCode ec = ErrorCode.MODEL_TIMEOUT;
        assertErrorCode(ec, "MODEL_TIMEOUT", 504, "模型调用超时");
        assertRoundTrip(ec, "模型调用超时");
        assertTriggered(() -> {
            boolean timeout = true;
            if (timeout) {
                throw new BusinessException(ec, "模型调用超时");
            }
        }, ec, "模型调用超时");
    }
}
