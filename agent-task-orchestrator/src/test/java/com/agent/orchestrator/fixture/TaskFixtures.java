package com.agent.orchestrator.fixture;

import com.agent.orchestrator.model.TaskInstance;

import java.time.Instant;

import static com.agent.orchestrator.fixture.TestConstants.*;

/**
 * TaskInstance 测试公共 Fixture 工厂（FIX-01 整改，对齐 FN-011）。
 *
 * <p>用于在 {@code agent-task-orchestrator} 多个测试类（{@code SubtaskDoneHandlerTest}
 * / {@code TaskOrchestratorGrpcServiceTest} / {@code FixturesShowcaseTest} 等）中
 * 复用同一套默认值的 {@link TaskInstance} 构造逻辑，避免每个测试类各自维护
 * {@code task(...)} 私有方法造成散落与不一致。</p>
 *
 * <p>设计原则：</p>
 * <ol>
 *   <li>工厂方法以 {@code buildXxx} 命名（FIX-02 命名可读性）；</li>
 *   <li>复杂对象由基础 Fixture 组合而成（FIX-03 可组合复用）：
 *       {@link #buildComplexTask(String)} 调用 {@link #buildSimpleTask(String)}
 *       再覆盖更多字段，{@link #buildCostExceededTask(String)} 调用
 *       {@link #buildRunningTask(String)} 再设置成本字段；</li>
 *   <li>不设置 {@code createdAt / updatedAt}：生产环境由 JPA {@code @PrePersist}
 *       自动填充，测试若需校验时间字段应显式赋值；</li>
 *   <li>不依赖 Spring Context，纯静态工厂，可在 {@code @BeforeEach} 之外任意使用。</li>
 * </ol>
 *
 * <p>风格参考 {@code agent-session} 模块的
 * {@code com.agent.session.testinfra.fixture.SessionFixtures}（v2 报告 FN-011 整改产物）。</p>
 *
 * @see DagFixtures
 * @see EventFixtures
 * @see TestConstants
 */
public final class TaskFixtures {

    private TaskFixtures() {
        // 工具类禁止实例化
    }

    // ============ 基础 Fixture（其他工厂组合的基石） ============

    /**
     * 构造一个最简单的 TaskInstance：PENDING 状态、成本/token 清零、仅必填字段非空。
     *
     * <p>所有 buildXxx 工厂方法均以此为基础组合而成（FIX-03）。</p>
     *
     * @param taskId 任务 ID
     * @return 已填充默认值的 TaskInstance（可变，调用方可覆盖字段）
     */
    public static TaskInstance buildSimpleTask(String taskId) {
        return TaskInstance.builder()
                .taskId(taskId)
                .tenantId(DEFAULT_TENANT_ID)
                .userId(DEFAULT_USER_ID)
                .title("测试任务")
                .goal("测试目标")
                .complexity(DEFAULT_COMPLEXITY)
                .status(STATUS_PENDING)
                .taskSchema("{}")
                .priority(DEFAULT_PRIORITY)
                .replanCount(0)
                .costLimitCent(DEFAULT_COST_LIMIT_CENT)
                .costUsedCent(DEFAULT_COST_USED_CENT)
                .tokenUsed(DEFAULT_TOKEN_USED)
                .build();
    }

    // ============ 按状态构造的 Fixture（覆盖核心状态机节点） ============

    /**
     * 构造一个 PENDING 状态的任务（待启动）。
     *
     * @param taskId 任务 ID
     * @return PENDING 状态的 TaskInstance
     */
    public static TaskInstance buildPendingTask(String taskId) {
        return buildSimpleTask(taskId);
    }

    /**
     * 构造一个 SUBTASK_RUNNING 状态的任务（子任务运行中，最常用的测试场景）。
     *
     * <p>对应 UT-MQ-001~010 中 {@code SubtaskDoneHandler} 的典型前置状态。</p>
     *
     * @param taskId 任务 ID
     * @return SUBTASK_RUNNING 状态的 TaskInstance
     */
    public static TaskInstance buildRunningTask(String taskId) {
        TaskInstance task = buildSimpleTask(taskId);
        task.setStatus(STATUS_SUBTASK_RUNNING);
        task.setStartedAt(Instant.now());
        return task;
    }

    /**
     * 构造一个 SUCCESS 状态的已完成任务（终态）。
     *
     * @param taskId 任务 ID
     * @return SUCCESS 状态的 TaskInstance
     */
    public static TaskInstance buildSuccessTask(String taskId) {
        TaskInstance task = buildRunningTask(taskId);
        task.setStatus(STATUS_SUCCESS);
        task.setFinishedAt(Instant.now());
        task.setResultSummary("任务执行完成");
        return task;
    }

    /**
     * 构造一个 TIMEOUT 状态的任务（终态，成本超限或子任务超时触发）。
     *
     * @param taskId 任务 ID
     * @return TIMEOUT 状态的 TaskInstance
     */
    public static TaskInstance buildTimeoutTask(String taskId) {
        TaskInstance task = buildRunningTask(taskId);
        task.setStatus(STATUS_TIMEOUT);
        task.setFinishedAt(Instant.now());
        task.setErrorCode("COST_BUDGET_EXCEEDED");
        task.setErrorMsg("成本预算超限");
        return task;
    }

    /**
     * 构造一个 REPLANNING 状态的任务（重规划中，由 failed + MAX_RETRY_EXCEEDED 触发）。
     *
     * @param taskId 任务 ID
     * @return REPLANNING 状态的 TaskInstance
     */
    public static TaskInstance buildReplanningTask(String taskId) {
        TaskInstance task = buildRunningTask(taskId);
        task.setStatus(STATUS_REPLANNING);
        task.setReplanCount(1);
        return task;
    }

    /**
     * 构造一个 WAITING_HUMAN 状态的任务（等待人工审核，由 failed + AGENT_NOT_FOUND 触发）。
     *
     * @param taskId 任务 ID
     * @return WAITING_HUMAN 状态的 TaskInstance
     */
    public static TaskInstance buildWaitingHumanTask(String taskId) {
        TaskInstance task = buildRunningTask(taskId);
        task.setStatus(STATUS_WAITING_HUMAN);
        task.setErrorCode("AGENT_NOT_FOUND");
        task.setErrorMsg("找不到可用 Agent");
        return task;
    }

    // ============ 复杂 Fixture（组合基础 Fixture + 业务字段覆盖，FIX-03 演示） ============

    /**
     * 构造一个复杂任务：在 {@link #buildSimpleTask(String)} 基础上组合 sessionId、
     * dagId、agentId、parentTaskId、error_msg 等业务字段，用于端到端集成测试场景。
     *
     * <p>这是 FIX-03「复杂对象由基础 Fixture 组合而成」的典型示例：
     * 复杂对象不重新构造，而是从基础 Fixture 演化而来，确保默认值一致性。</p>
     *
     * @param taskId 任务 ID
     * @return 含完整业务字段的 TaskInstance
     */
    public static TaskInstance buildComplexTask(String taskId) {
        TaskInstance task = buildSimpleTask(taskId);
        // 组合更多业务字段（覆盖基础 Fixture 未设置的可选字段）
        task.setSessionId("sess_" + taskId);
        task.setDagId(DEFAULT_DAG_ID);
        task.setAgentId(DEFAULT_AGENT_ID);
        task.setParentTaskId("tk_parent");
        task.setTaskSchema("{\"version\":1,\"nodes\":[],\"edges\":[]}");
        return task;
    }

    /**
     * 构造一个成本即将超限的 SUBTASK_RUNNING 任务（costUsed 已接近 costLimit），
     * 用于 UT-MQ-004 成本超限场景测试。
     *
     * <p>组合关系：{@code buildRunningTask(taskId)} → 覆盖 costUsedCent 字段。</p>
     *
     * @param taskId        任务 ID
     * @param costUsedCent  已用成本（分）
     * @param costLimitCent 成本上限（分）
     * @return 成本即将超限的 TaskInstance
     */
    public static TaskInstance buildCostExceededTask(String taskId,
                                                     long costUsedCent,
                                                     long costLimitCent) {
        TaskInstance task = buildRunningTask(taskId);
        task.setCostUsedCent(costUsedCent);
        task.setCostLimitCent(costLimitCent);
        return task;
    }

    /**
     * 构造一个已耗尽重规划次数的任务（replanCount 达到上限 3），
     * 用于 UT-F5-002 重规划终止场景测试。
     *
     * @param taskId 任务 ID
     * @return replanCount=3 的 TaskInstance
     */
    public static TaskInstance buildReplanExhaustedTask(String taskId) {
        TaskInstance task = buildReplanningTask(taskId);
        task.setReplanCount(3);
        task.setErrorCode("MAX_RETRY_EXCEEDED");
        task.setErrorMsg("重规划次数耗尽");
        return task;
    }
}
