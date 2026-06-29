package com.agent.orchestrator.fixture;

import com.agent.orchestrator.mq.event.StateChangeEvent;
import com.agent.orchestrator.mq.event.SubtaskCancelEvent;
import com.agent.orchestrator.mq.event.SubtaskDoneEvent;
import com.agent.orchestrator.mq.event.SubtaskExecuteEvent;

import java.util.List;
import java.util.Map;

import static com.agent.orchestrator.fixture.TestConstants.*;

/**
 * MQ 事件测试公共 Fixture 工厂（FIX-01 整改，对齐 FN-011）。
 *
 * <p>集中管理 4 个 MQ 事件的默认值构造逻辑：</p>
 * <ul>
 *   <li>{@link SubtaskExecuteEvent} — 子任务分发事件（topic: task.subtask.execute）</li>
 *   <li>{@link SubtaskDoneEvent} — 子任务完成事件（topic: task.subtask.done，4 种 status）</li>
 *   <li>{@link StateChangeEvent} — 状态变更广播事件（topic: task.state.change）</li>
 *   <li>{@link SubtaskCancelEvent} — 子任务取消事件（topic: task.subtask.cancel）</li>
 * </ul>
 *
 * <p>设计原则：</p>
 * <ol>
 *   <li>工厂方法以 {@code buildXxx} 命名（FIX-02）；</li>
 *   <li>复杂事件由基础 Fixture 组合而成（FIX-03）：
 *       {@link #buildFailedDoneEvent(String, String, String)} 在
 *       {@link #buildSimpleDoneEvent(String, String)} 基础上覆盖 status/errorCode；</li>
 *   <li>不依赖 Spring Context，纯静态工厂；</li>
 *   <li>常量引用 {@link TestConstants}，保证跨 fixture 一致性。</li>
 * </ol>
 *
 * @see TaskFixtures
 * @see DagFixtures
 * @see TestConstants
 */
public final class EventFixtures {

    /** 事件类型：子任务分发。 */
    public static final String EVENT_TYPE_EXECUTE = "task.subtask.execute";
    /** 事件类型：子任务完成。 */
    public static final String EVENT_TYPE_DONE = "task.subtask.done";
    /** 事件类型：子任务取消。 */
    public static final String EVENT_TYPE_CANCEL = "task.subtask.cancel";

    /** 默认事件时间（ISO-8601）。 */
    public static final String DEFAULT_EVENT_TIME = "2026-06-29T12:00:00Z";
    /** 默认 maxRetries：3。 */
    public static final Integer DEFAULT_MAX_RETRIES = 3;
    /** 默认 timeoutMs：30000（30 秒）。 */
    public static final Integer DEFAULT_TIMEOUT_MS = 30000;
    /** 默认 modelTier：standard。 */
    public static final String DEFAULT_MODEL_TIER = "standard";
    /** 默认 requireHumanReview：false。 */
    public static final Boolean DEFAULT_REQUIRE_HUMAN_REVIEW = false;
    /** 默认 costBudgetCent：5000 分（50 元）。 */
    public static final Long DEFAULT_COST_BUDGET_CENT = 5000L;
    /** 默认 tokenUsed：100。 */
    public static final Integer DEFAULT_TOKEN_USED = 100;
    /** 默认 durationMs：1000（1 秒）。 */
    public static final Integer DEFAULT_DURATION_MS = 1000;

    private EventFixtures() {
        // 工具类禁止实例化
    }

    // ============ SubtaskExecuteEvent Fixture ============

    /**
     * 构造一个最简单的 SubtaskExecuteEvent：仅必填字段非空。
     *
     * @param taskId 任务 ID
     * @param nodeId 节点 ID
     * @return 已填充默认值的 SubtaskExecuteEvent
     */
    public static SubtaskExecuteEvent buildSimpleExecuteEvent(String taskId, String nodeId) {
        return SubtaskExecuteEvent.builder()
                .eventId(DEFAULT_EVENT_ID)
                .eventType(EVENT_TYPE_EXECUTE)
                .eventTime(DEFAULT_EVENT_TIME)
                .traceId(DEFAULT_TRACE_ID)
                .tenantId(DEFAULT_TENANT_ID)
                .taskId(taskId)
                .dagId(DEFAULT_DAG_ID)
                .dagVersion(DEFAULT_DAG_VERSION)
                .nodeId(nodeId)
                .subtaskId(DEFAULT_SUBTASK_ID)
                .agentId(DEFAULT_AGENT_ID)
                .title("子任务 " + taskId + ":" + nodeId)
                .abilityTags(List.of("coding", "review"))
                .inputs(Map.of("input", "data"))
                .config(SubtaskExecuteEvent.SubtaskConfig.builder()
                        .maxRetries(DEFAULT_MAX_RETRIES)
                        .timeoutMs(DEFAULT_TIMEOUT_MS)
                        .modelTier(DEFAULT_MODEL_TIER)
                        .requireHumanReview(DEFAULT_REQUIRE_HUMAN_REVIEW)
                        .build())
                .deadline("2026-06-29T23:59:59Z")
                .costBudgetCent(DEFAULT_COST_BUDGET_CENT)
                .build();
    }

    /**
     * 构造一个 requireHumanReview=true 的 SubtaskExecuteEvent（需人工审核场景）。
     *
     * @param taskId 任务 ID
     * @param nodeId 节点 ID
     * @return requireHumanReview=true 的 SubtaskExecuteEvent
     */
    public static SubtaskExecuteEvent buildHumanReviewExecuteEvent(String taskId, String nodeId) {
        SubtaskExecuteEvent event = buildSimpleExecuteEvent(taskId, nodeId);
        event.getConfig().setRequireHumanReview(true);
        event.getConfig().setModelTier("premium");
        return event;
    }

    // ============ SubtaskDoneEvent Fixture（4 种 status 变体） ============

    /**
     * 构造一个最简单的 SubtaskDoneEvent：仅必填字段非空，status 由参数指定。
     *
     * <p>所有 buildXxxDoneEvent 工厂方法均以此为基础组合而成（FIX-03）。</p>
     *
     * @param eventId 事件 ID
     * @param taskId  任务 ID
     * @param status  子任务状态（success / failed / timeout / require_review）
     * @return 已填充默认值的 SubtaskDoneEvent
     */
    public static SubtaskDoneEvent buildSimpleDoneEvent(String eventId, String taskId, String status) {
        return SubtaskDoneEvent.builder()
                .eventId(eventId)
                .eventType(EVENT_TYPE_DONE)
                .eventTime(DEFAULT_EVENT_TIME)
                .traceId(DEFAULT_TRACE_ID)
                .tenantId(DEFAULT_TENANT_ID)
                .taskId(taskId)
                .subtaskId(DEFAULT_SUBTASK_ID)
                .nodeId(DEFAULT_NODE_ID)
                .status(status)
                .outputs(Map.of())
                .tokenUsed(DEFAULT_TOKEN_USED)
                .costCent(100L)
                .durationMs(DEFAULT_DURATION_MS)
                .build();
    }

    /**
     * 构造一个 success 状态的 SubtaskDoneEvent。
     *
     * @param eventId 事件 ID
     * @param taskId  任务 ID
     * @return success 状态的 SubtaskDoneEvent
     */
    public static SubtaskDoneEvent buildSuccessDoneEvent(String eventId, String taskId) {
        SubtaskDoneEvent event = buildSimpleDoneEvent(eventId, taskId, SUBTASK_STATUS_SUCCESS);
        event.setOutputs(Map.of("result", "done", "tokensUsed", 100));
        return event;
    }

    /**
     * 构造一个 failed 状态的 SubtaskDoneEvent。
     *
     * @param eventId   事件 ID
     * @param taskId    任务 ID
     * @param errorCode 错误码（如 MAX_RETRY_EXCEEDED / AGENT_NOT_FOUND / UNKNOWN_ERROR）
     * @return failed 状态的 SubtaskDoneEvent
     */
    public static SubtaskDoneEvent buildFailedDoneEvent(String eventId, String taskId, String errorCode) {
        SubtaskDoneEvent event = buildSimpleDoneEvent(eventId, taskId, SUBTASK_STATUS_FAILED);
        event.setErrorCode(errorCode);
        event.setErrorMsg("子任务执行失败: " + errorCode);
        return event;
    }

    /**
     * 构造一个 timeout 状态的 SubtaskDoneEvent。
     *
     * @param eventId 事件 ID
     * @param taskId  任务 ID
     * @return timeout 状态的 SubtaskDoneEvent
     */
    public static SubtaskDoneEvent buildTimeoutDoneEvent(String eventId, String taskId) {
        SubtaskDoneEvent event = buildSimpleDoneEvent(eventId, taskId, SUBTASK_STATUS_TIMEOUT);
        event.setErrorCode("EXECUTION_TIMEOUT");
        event.setErrorMsg("子任务执行超时");
        event.setDurationMs(DEFAULT_TIMEOUT_MS);
        return event;
    }

    /**
     * 构造一个 require_review 状态的 SubtaskDoneEvent（待人工审核）。
     *
     * @param eventId 事件 ID
     * @param taskId  任务 ID
     * @return require_review 状态的 SubtaskDoneEvent
     */
    public static SubtaskDoneEvent buildRequireReviewDoneEvent(String eventId, String taskId) {
        SubtaskDoneEvent event = buildSimpleDoneEvent(eventId, taskId, SUBTASK_STATUS_REQUIRE_REVIEW);
        event.setOutputs(Map.of("reviewRequired", true));
        return event;
    }

    // ============ StateChangeEvent Fixture ============

    /**
     * 构造一个 StateChangeEvent（任务状态变更广播）。
     *
     * @param taskId     任务 ID
     * @param fromStatus 原状态
     * @param toStatus   新状态
     * @return 已填充默认值的 StateChangeEvent
     */
    public static StateChangeEvent buildStateChangeEvent(String taskId,
                                                         String fromStatus,
                                                         String toStatus) {
        return StateChangeEvent.builder()
                .taskId(taskId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .trigger("auto")
                .operator(DEFAULT_USER_ID)
                .reason("状态流转 " + fromStatus + " -> " + toStatus)
                .traceId(DEFAULT_TRACE_ID)
                .tenantId(DEFAULT_TENANT_ID)
                .build();
    }

    /**
     * 构造一个 PENDING → RUNNING 的 StateChangeEvent。
     *
     * @param taskId 任务 ID
     * @return PENDING → RUNNING 的 StateChangeEvent
     */
    public static StateChangeEvent buildPendingToRunningEvent(String taskId) {
        return buildStateChangeEvent(taskId, STATUS_PENDING, STATUS_RUNNING);
    }

    /**
     * 构造一个 RUNNING → SUCCESS 的 StateChangeEvent。
     *
     * @param taskId 任务 ID
     * @return RUNNING → SUCCESS 的 StateChangeEvent
     */
    public static StateChangeEvent buildRunningToSuccessEvent(String taskId) {
        return buildStateChangeEvent(taskId, STATUS_RUNNING, STATUS_SUCCESS);
    }

    // ============ SubtaskCancelEvent Fixture ============

    /**
     * 构造一个 SubtaskCancelEvent。
     *
     * @param eventId 事件 ID
     * @param taskId  任务 ID
     * @param reason  取消原因
     * @return 已填充默认值的 SubtaskCancelEvent
     */
    public static SubtaskCancelEvent buildSubtaskCancelEvent(String eventId, String taskId, String reason) {
        return SubtaskCancelEvent.builder()
                .eventId(eventId)
                .eventType(EVENT_TYPE_CANCEL)
                .eventTime(DEFAULT_EVENT_TIME)
                .traceId(DEFAULT_TRACE_ID)
                .tenantId(DEFAULT_TENANT_ID)
                .taskId(taskId)
                .nodeId(DEFAULT_NODE_ID)
                .subtaskId(DEFAULT_SUBTASK_ID)
                .reason(reason)
                .build();
    }

    /**
     * 构造一个用户主动取消的 SubtaskCancelEvent。
     *
     * @param eventId 事件 ID
     * @param taskId  任务 ID
     * @return 用户取消的 SubtaskCancelEvent
     */
    public static SubtaskCancelEvent buildUserCancelEvent(String eventId, String taskId) {
        return buildSubtaskCancelEvent(eventId, taskId, "用户主动取消");
    }
}
