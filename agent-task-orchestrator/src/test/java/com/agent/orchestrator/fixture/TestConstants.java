package com.agent.orchestrator.fixture;

/**
 * agent-task-orchestrator 模块测试公共常量（FIX-01 整改）。
 *
 * <p>集中维护测试用例中常用的 ID、状态、阈值等常量，避免散落在各测试类
 * 中造成不一致。所有常量均为 {@code public static final}，命名以业务语义
 * 优先（如 {@link #DEFAULT_TENANT_ID} 而非 TENANT_ID_1）。</p>
 *
 * <p>设计原则：</p>
 * <ol>
 *   <li>常量与生产代码 {@code TaskStatus} / {@code TaskPriority} 等枚举字符串值保持一致；</li>
 *   <li>不依赖 Spring Context，纯静态常量，可在任意测试类中引用；</li>
 *   <li>与 {@link TaskFixtures} / {@link DagFixtures} / {@link EventFixtures}
 *       共同构成 fixture 工厂层，参见
 *       {@code docs/tests/tdd-audit-framework.md} §3.5 FIX-01。</li>
 * </ol>
 *
 * @see TaskFixtures
 * @see DagFixtures
 * @see EventFixtures
 */
public final class TestConstants {

    // ============ 租户 / 用户 / Agent ============
    /** 默认租户 ID（与种子数据 infra/sql/mysql/11-seed-data.sql 中 t_001 对应）。 */
    public static final Long DEFAULT_TENANT_ID = 1001L;
    /** 默认用户 ID。 */
    public static final String DEFAULT_USER_ID = "u_001";
    /** 默认 Agent ID（与种子数据 a_2001 对应）。 */
    public static final Long DEFAULT_AGENT_ID = 2001L;
    /** 备用租户 ID（用于多租户隔离测试）。 */
    public static final Long ALT_TENANT_ID = 2002L;
    /** 备用用户 ID。 */
    public static final String ALT_USER_ID = "u_002";

    // ============ 任务 / DAG / 节点 ID ============
    /** 默认任务 ID 前缀（测试中可用 {@code DEFAULT_TASK_ID + n} 构造 tk_001 / tk_002 等）。 */
    public static final String DEFAULT_TASK_ID = "tk_001";
    /** 默认 DAG ID。 */
    public static final Long DEFAULT_DAG_ID = 1L;
    /** 默认 DAG 版本号。 */
    public static final Integer DEFAULT_DAG_VERSION = 1;
    /** 默认节点 ID 前缀（如 n_1 / n_2 / n_3）。 */
    public static final String DEFAULT_NODE_ID = "n_1";
    /** 默认子任务 ID 前缀（如 st_1 / st_2）。 */
    public static final String DEFAULT_SUBTASK_ID = "st_1";
    /** 默认事件 ID 前缀（如 ev_001）。 */
    public static final String DEFAULT_EVENT_ID = "ev_001";
    /** 默认 traceId（用于全链路追踪测试）。 */
    public static final String DEFAULT_TRACE_ID = "trace_001";

    // ============ 任务状态字符串值（对齐 com.agent.common.constant.TaskStatus）============
    /** 任务状态：PENDING 待启动。 */
    public static final String STATUS_PENDING = "PENDING";
    /** 任务状态：RUNNING 运行中。 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 任务状态：SUBTASK_RUNNING 子任务运行中。 */
    public static final String STATUS_SUBTASK_RUNNING = "SUBTASK_RUNNING";
    /** 任务状态：SUCCESS 成功。 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 任务状态：TIMEOUT 超时。 */
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    /** 任务状态：FAILED 失败。 */
    public static final String STATUS_FAILED = "FAILED";
    /** 任务状态：REPLANNING 重规划中。 */
    public static final String STATUS_REPLANNING = "REPLANNING";
    /** 任务状态：WAITING_HUMAN 等待人工。 */
    public static final String STATUS_WAITING_HUMAN = "WAITING_HUMAN";

    // ============ 子任务状态（MQ 消息体） ============
    /** 子任务状态：success 成功。 */
    public static final String SUBTASK_STATUS_SUCCESS = "success";
    /** 子任务状态：failed 失败。 */
    public static final String SUBTASK_STATUS_FAILED = "failed";
    /** 子任务状态：timeout 超时。 */
    public static final String SUBTASK_STATUS_TIMEOUT = "timeout";
    /** 子任务状态：require_review 待审核。 */
    public static final String SUBTASK_STATUS_REQUIRE_REVIEW = "require_review";

    // ============ 成本 / Token 阈值 ============
    /** 默认成本上限（分）：100 元 = 10000 分。 */
    public static final Long DEFAULT_COST_LIMIT_CENT = 10000L;
    /** 默认成本已用（分）：0。 */
    public static final Long DEFAULT_COST_USED_CENT = 0L;
    /** 默认 Token 已用：0。 */
    public static final Integer DEFAULT_TOKEN_USED = 0;
    /** 默认任务优先级：5（中等，对齐 TaskInstance.prePersist 默认值）。 */
    public static final Integer DEFAULT_PRIORITY = 5;
    /** 默认任务复杂度：2（对齐 doc 03-task-engine §3.1 复杂度评分 1~5）。 */
    public static final Integer DEFAULT_COMPLEXITY = 2;

    // ============ 节点类型（对齐 doc 03-task-engine §4.1 DagNode.nodeType 取值）============
    /** 节点类型：ATOMIC 原子任务。 */
    public static final String NODE_TYPE_ATOMIC = "ATOMIC";
    /** 节点类型：COMPOSITE 组合任务。 */
    public static final String NODE_TYPE_COMPOSITE = "COMPOSITE";
    /** 节点类型：DECISION 决策节点（F4/F5）。 */
    public static final String NODE_TYPE_DECISION = "DECISION";

    // ============ 边类型（对齐 DagEdge.edgeType 取值）============
    /** 边类型：DATA 数据依赖。 */
    public static final String EDGE_TYPE_DATA = "DATA";
    /** 边类型：LOGIC 逻辑依赖。 */
    public static final String EDGE_TYPE_LOGIC = "LOGIC";
    /** 边类型：NONE 无依赖（仅并行批次标记）。 */
    public static final String EDGE_TYPE_NONE = "NONE";

    private TestConstants() {
        // 工具类禁止实例化
    }
}
