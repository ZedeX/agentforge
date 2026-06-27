# AgentForge 智能体平台 测试数据与 Fixture

> 文档版本：v1.1 | 更新日期：2026-06-27 | 文档定位：**测试数据策略 + Fixture 示例 + Testcontainers 配置 + 性能/边界数据**
>
> 适用范围：AgentForge 平台全量测试的数据准备、Fixture 工厂、容器化测试基础设施、性能测试数据与边界值数据。
>
> 依赖文档：
> - [test-strategy.md](test-strategy.md) — 测试策略与数据策略
> - [01-database/database-schema-design.md](../01-database/database-schema-design.md) — 数据库表结构
> - [infra/sql/mysql/11-seed-data.sql](../../infra/sql/mysql/11-seed-data.sql) — 生产种子数据
> - [00-overview/tech-stack-and-architecture.md](../00-overview/tech-stack-and-architecture.md) — 中间件清单
> - [test-plan.md](test-plan.md) §6 — Testcontainers 容器矩阵与 Fixture 工厂

---

## 1. 测试数据策略

### 1.1 数据来源原则

AgentForge 测试数据遵循"合成优先、脱敏补充、隔离执行"三大原则：

| 数据类型 | 策略 | 适用场景 | 说明 |
|---|---|---|---|
| 合成数据 | 程序构造（Fixture 工厂） | 单元测试、功能测试 | 可控、可复现、无隐私风险 |
| 脱敏生产样本 | 生产数据脱敏后导出 | E2E 边界场景、性能测试 | 保留真实数据分布特征，去除敏感字段 |
| 批量生成数据 | 脚本批量构造 | 性能测试、压力测试 | 按量级生成（如 10 万条记忆向量） |
| DDL 种子数据 | 复用 `infra/sql/mysql/11-seed-data.sql` | 集成测试、E2E | 模型路由、工具注册表等基础数据 |

**禁止事项**：
- 禁止在测试中直接连接生产数据库
- 禁止使用未脱敏的生产数据
- 禁止在测试代码中硬编码真实密钥（使用 `WireMock` 桩或 `secret/data/...` 引用）

### 1.2 数据生命周期管理

| 阶段 | 操作 | 实现方式 |
|---|---|---|
| 测试前 | 初始化数据 | `@BeforeEach` + Fixture 工厂 + `@Sql` |
| 测试中 | 数据隔离 | 每方法独立事务 `@Transactional` + `@Rollback` |
| 测试后 | 清理副作用 | `@AfterEach` 清理 Redis Key、临时文件 |
| 类结束 | 销毁容器 | `@AfterAll` + Testcontainers 自动清理 |

### 1.3 数据隔离策略

| 隔离维度 | 策略 | 实现方式 |
|---|---|---|
| 测试类间 | 独立 Testcontainers 容器 | `@Container static` 每类一个实例 |
| 测试方法间 | 事务回滚 | `@Transactional` + `@Rollback(true)` |
| 多租户 | 独立 tenantId | Fixture 注入随机 `tn_test_{uuid}` |
| 数据库表 | `@Sql` 清理脚本 | `classpath:cleanup-{module}.sql` |
| Redis Key | Key 前缀隔离 | `test:{testId}:*`，测试后 `DEL` |

---

## 2. 测试用户与租户种子数据

### 2.1 测试租户与用户

```sql
-- 测试租户与用户种子数据（test-tenant-seed.sql）
-- 对应 doc 01-database §9 agent_risk 库

USE agent_risk;

-- 测试租户
INSERT INTO `tenant` (`id`, `tenant_code`, `name`, `status`, `plan`) VALUES
('tn_test_001', 'test-tenant-a', '测试租户A', 1, 'enterprise'),
('tn_test_002', 'test-tenant-b', '测试租户B', 1, 'standard'),
('tn_test_003', 'test-tenant-c', '测试租户C（金融高风险）', 1, 'enterprise');

-- 测试用户
INSERT INTO `user` (`id`, `tenant_id`, `user_code`, `name`, `roles`, `status`) VALUES
('u_test_001', 'tn_test_001', 'test-user-1', '测试用户1', '["admin"]', 1),
('u_test_002', 'tn_test_001', 'test-user-2', '测试用户2', '["operator"]', 1),
('u_test_003', 'tn_test_002', 'test-user-3', '测试用户3', '["viewer"]', 1),
('u_test_004', 'tn_test_003', 'test-user-4', '测试用户4（金融）', '["admin","compliance"]', 1),
('u_test_005', 'tn_test_003', 'test-auditor', '测试审批人', '["approver"]', 1),
('u_test_006', 'tn_test_003', 'test-second-auditor', '测试副审批人', '["approver"]', 1);

-- API Key（企业系统集成）
INSERT INTO `api_key` (`id`, `tenant_id`, `api_key`, `user_id`, `status`, `expire_at`) VALUES
('ak_test_001', 'tn_test_001', 'ak_test_xxx_001', 'u_test_001', 1, '2027-12-31 23:59:59'),
('ak_test_002', 'tn_test_003', 'ak_test_xxx_002', 'u_test_004', 1, '2027-12-31 23:59:59');
```

### 2.2 测试 JWT Token 构造

```java
// testinfra/fixture/TestTokenFixture.java
public class TestTokenFixture {
    private static final String TEST_SECRET = "test-secret-key-for-jwt-signing";

    public static String buildToken(String userId, String tenantId, String... roles) {
        return Jwts.builder()
            .setSubject(userId)
            .claim("tenantId", tenantId)
            .claim("roles", Arrays.asList(roles))
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600_000))
            .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes()))
            .compact();
    }

    // 预置常用 Token
    public static final String ADMIN_TOKEN = buildToken("u_test_001", "tn_test_001", "admin");
    public static final String OPERATOR_TOKEN = buildToken("u_test_002", "tn_test_001", "operator");
    public static final String FINANCE_TOKEN = buildToken("u_test_004", "tn_test_003", "admin", "compliance");
    public static final String APPROVER_TOKEN = buildToken("u_test_005", "tn_test_003", "approver");
    public static final String EXPIRED_TOKEN = buildExpiredToken("u_test_001", "tn_test_001");
}
```

---

## 3. 各模块测试 Fixture 示例

### 3.1 Task Fixture（任务数据）

```java
// testinfra/fixture/TaskFixture.java
public class TaskFixture {

    public static TaskInstance buildSimpleTask() {
        return TaskInstance.newBuilder()
            .setTaskId("tk_test_001")
            .setTenantId("tn_test_001")
            .setUserId("u_test_001")
            .setGoal("今天天气如何")
            .setComplexity(1)  // L1 简单
            .setStatus("PENDING")
            .setPriority(5)
            .setCostLimitCent(1000)
            .setSubmittedAt(Timestamp.newBuilder().setSeconds(1690000000).build())
            .build();
    }

    public static TaskInstance buildComplexTask() {
        return TaskInstance.newBuilder()
            .setTaskId("tk_test_002")
            .setTenantId("tn_test_001")
            .setUserId("u_test_001")
            .setGoal("生成行业调研报告并发邮件给 manager@xx.com")
            .setComplexity(3)  // L3 复杂
            .setStatus("PENDING")
            .setPriority(8)
            .setCostLimitCent(50000)
            .setConstraints("{\"deadline\":\"2026-06-28T00:00:00Z\",\"allowHumanFallback\":true}")
            .build();
    }

    public static TaskInstance buildR3Task() {
        return TaskInstance.newBuilder()
            .setTaskId("tk_test_003")
            .setTenantId("tn_test_003")  // 金融高风险租户
            .setUserId("u_test_004")
            .setGoal("删除过期客户数据")
            .setComplexity(2)
            .setStatus("RUNNING")
            .setRiskLevel("R3")
            .build();
    }
}
```

### 3.2 DAG Fixture（DAG 数据）

```java
// testinfra/fixture/DagFixture.java
public class DagFixture {

    public static Dag buildLinearDag() {
        // 线性 DAG: A → B → C
        return Dag.newBuilder()
            .setDagId("dag_test_001")
            .setTaskId("tk_test_002")
            .setVersion(1)
            .addNodes(DagNode.newBuilder().setNodeId("n1").setAgentId("ag_research").build())
            .addNodes(DagNode.newBuilder().setNodeId("n2").setAgentId("ag_analyze").build())
            .addNodes(DagNode.newBuilder().setNodeId("n3").setAgentId("ag_write").build())
            .addEdges(DagEdge.newBuilder().setFrom("n1").setTo("n2").build())
            .addEdges(DagEdge.newBuilder().setFrom("n2").setTo("n3").build())
            .build();
    }

    public static Dag buildParallelDag() {
        // 并行 DAG: n1/n2 无依赖并行，n3 依赖 n1+n2
        return Dag.newBuilder()
            .setDagId("dag_test_002")
            .setTaskId("tk_test_002")
            .setVersion(1)
            .addNodes(DagNode.newBuilder().setNodeId("n1").setAgentId("ag_research").build())
            .addNodes(DagNode.newBuilder().setNodeId("n2").setAgentId("ag_analyze").build())
            .addNodes(DagNode.newBuilder().setNodeId("n3").setAgentId("ag_write").build())
            .addEdges(DagEdge.newBuilder().setFrom("n1").setTo("n3").build())
            .addEdges(DagEdge.newBuilder().setFrom("n2").setTo("n3").build())
            .build();
    }

    public static Dag buildCyclicDag() {
        // 含环 DAG: A → B → C → A（应被环检测拒绝）
        return Dag.newBuilder()
            .setDagId("dag_test_003")
            .setTaskId("tk_test_fail")
            .setVersion(1)
            .addNodes(DagNode.newBuilder().setNodeId("n1").build())
            .addNodes(DagNode.newBuilder().setNodeId("n2").build())
            .addNodes(DagNode.newBuilder().setNodeId("n3").build())
            .addEdges(DagEdge.newBuilder().setFrom("n1").setTo("n2").build())
            .addEdges(DagEdge.newBuilder().setFrom("n2").setTo("n3").build())
            .addEdges(DagEdge.newBuilder().setFrom("n3").setTo("n1").build())  // 环
            .build();
    }
}
```

### 3.3 Memory Fixture（记忆数据）

```json
// fixture/memory-long-term.json
{
  "memories": [
    {
      "memoryId": "mem_test_001",
      "tenantId": "tn_test_001",
      "userId": "u_test_001",
      "type": "EPISODIC",
      "content": "用户偏好简洁回答，不喜欢冗长解释",
      "tags": ["preference", "style"],
      "importanceScore": 0.85,
      "embedding": [0.12, 0.34, 0.56, "..."],
      "createdAt": "2026-06-01T10:00:00Z",
      "ttlDays": 90,
      "status": "ACTIVE"
    },
    {
      "memoryId": "mem_test_002",
      "tenantId": "tn_test_001",
      "userId": "u_test_001",
      "type": "SEMANTIC",
      "content": "订单查询工具返回字段：订单号、金额、状态、时间",
      "tags": ["order", "tool-schema"],
      "importanceScore": 0.72,
      "embedding": [0.22, 0.44, 0.66, "..."],
      "createdAt": "2026-06-15T14:00:00Z",
      "ttlDays": 180,
      "status": "ACTIVE"
    },
    {
      "memoryId": "mem_test_003",
      "tenantId": "tn_test_001",
      "userId": "u_test_001",
      "type": "PROCEDURAL",
      "content": "周报生成模板：1.数据汇总 2.趋势分析 3.建议 4.发送",
      "tags": ["weekly-report", "template"],
      "importanceScore": 0.90,
      "embedding": [0.32, 0.54, 0.76, "..."],
      "createdAt": "2026-06-20T09:00:00Z",
      "ttlDays": 365,
      "status": "ACTIVE"
    }
  ]
}
```

### 3.4 Tool Fixture（工具数据）

```json
// fixture/tool-registry.json
{
  "tools": [
    {
      "toolId": "tl_test_001",
      "name": "query_order",
      "description": "查询订单状态",
      "sceneTags": ["order", "query"],
      "riskLevel": "R1",
      "executorType": "general",
      "avgCostCent": 5,
      "avgDurationMs": 200,
      "inputSchema": {
        "type": "object",
        "properties": {
          "orderId": {"type": "string"},
          "days": {"type": "integer", "minimum": 1, "maximum": 90}
        },
        "required": ["orderId"]
      },
      "outputSchema": {
        "type": "object",
        "properties": {
          "orders": {"type": "array"},
          "total": {"type": "integer"}
        }
      },
      "status": "ACTIVE"
    },
    {
      "toolId": "tl_test_002",
      "name": "send_email",
      "description": "发送邮件",
      "sceneTags": ["email", "notify"],
      "riskLevel": "R2",
      "executorType": "proxy",
      "avgCostCent": 2,
      "avgDurationMs": 500,
      "status": "ACTIVE"
    },
    {
      "toolId": "tl_test_003",
      "name": "delete_data",
      "description": "删除数据（不可回滚）",
      "sceneTags": ["data", "delete"],
      "riskLevel": "R3",
      "executorType": "sandbox",
      "avgCostCent": 10,
      "avgDurationMs": 1000,
      "requiresApproval": true,
      "requiresDualReview": true,
      "approvalWindowHours": 1,
      "status": "ACTIVE"
    }
  ]
}
```

### 3.5 Agent Fixture（Agent 定义数据）

```json
// fixture/agent-definition.json
{
  "agents": [
    {
      "agentId": "ag_test_001",
      "name": "通用问答Agent",
      "version": 1,
      "status": "published",
      "complexityTarget": "L1",
      "systemPrompt": "你是一个简洁的问答助手，回答要简短准确。",
      "modelTier": "light",
      "tools": [],
      "capabilities": ["chitchat", "single_qa"],
      "riskLevel": "low"
    },
    {
      "agentId": "ag_test_002",
      "name": "订单查询Agent",
      "version": 2,
      "status": "published",
      "complexityTarget": "L2",
      "systemPrompt": "你是订单查询助手，使用工具查询订单并汇总。",
      "modelTier": "middle",
      "tools": ["tl_test_001"],
      "capabilities": ["tool_call"],
      "riskLevel": "mid"
    },
    {
      "agentId": "ag_test_003",
      "name": "调研报告Agent",
      "version": 1,
      "status": "published",
      "complexityTarget": "L3",
      "systemPrompt": "你是行业调研专家，规划并生成完整报告。",
      "modelTier": "strong",
      "tools": ["tl_test_001", "tl_test_002"],
      "capabilities": ["complex_task"],
      "riskLevel": "high"
    }
  ]
}
```

### 3.6 Badcase Fixture（Badcase 数据）

```json
// fixture/badcase.json
{
  "badcases": [
    {
      "badcaseId": "bc_test_001",
      "tenantId": "tn_test_001",
      "taskId": "tk_test_002",
      "category": "hallucination",
      "severity": "high",
      "description": "Agent 编造了不存在的订单数据",
      "rootCause": "prompt 缺少事实约束，未强制来源标注",
      "status": "analyzed",
      "fixAction": "更新 systemPrompt，强制 [来源:xxx] 标注",
      "createdAt": "2026-06-25T10:00:00Z"
    },
    {
      "badcaseId": "bc_test_002",
      "tenantId": "tn_test_003",
      "taskId": "tk_test_003",
      "category": "tool_error",
      "severity": "medium",
      "description": "R3 工具参数非法未被前置拦截",
      "rootCause": "参数 Schema 校验规则缺失",
      "status": "analyzed",
      "fixAction": "补充 inputSchema 约束",
      "createdAt": "2026-06-26T15:00:00Z"
    }
  ]
}
```

### 3.7 Trace Fixture（链路追踪数据，新增）

```java
// testinfra/fixture/TraceFixture.java
public class TraceFixture {

    public static TraceContext buildRootTrace() {
        return TraceContext.newBuilder()
            .setTraceId("trace_test_" + UUID.randomUUID().toString().replace("-", ""))
            .setSpanId(UUID.randomUUID().toString().replace("-", "").substring(0, 16))
            .setTenantId("tn_test_001")
            .setUserId("u_test_001")
            .setSampled(true)
            .build();
    }

    public static TraceContext buildChildTrace(String parentTraceId, String parentSpanId) {
        return TraceContext.newBuilder()
            .setTraceId(parentTraceId)  // 子 span 继承 traceId
            .setSpanId(UUID.randomUUID().toString().replace("-", "").substring(0, 16))
            .setParentSpanId(parentSpanId)
            .setSampled(true)
            .build();
    }

    public static TraceContext buildNotSampled() {
        return TraceContext.newBuilder()
            .setTraceId("trace_notsampled_" + System.currentTimeMillis())
            .setSampled(false)
            .build();
    }
}
```

### 3.8 Knowledge Fixture（知识库数据，新增）

```java
// testinfra/fixture/KnowledgeFixture.java
public class KnowledgeFixture {

    public static KnowledgeBase buildSimpleKb() {
        return KnowledgeBase.newBuilder()
            .setKbId("kb_test_001")
            .setTenantId("tn_test_001")
            .setName("产品手册库")
            .setEmbeddingModel("bge-large-zh")
            .setVectorDim(1024)
            .setStatus("ACTIVE")
            .setCreatedAt(Timestamp.newBuilder().setSeconds(1690000000).build())
            .build();
    }

    public static KnowledgeDocument buildDocument() {
        return KnowledgeDocument.newBuilder()
            .setDocId("doc_test_001")
            .setKbId("kb_test_001")
            .setTitle("产品手册 v1.0")
            .setContent("退货政策：7 天无理由退货，需保留原包装...")
            .setSha256("a1b2c3d4e5f6...")
            .setStatus("INDEXED")
            .build();
    }

    public static KnowledgeChunk buildChunk(int idx) {
        return KnowledgeChunk.newBuilder()
            .setChunkId("chk_test_" + String.format("%03d", idx))
            .setDocId("doc_test_001")
            .setContent("第 " + idx + " 段：产品功能说明...")
            .setTokenCount(450)  // ≤512
            .setEmbedding(ByteString.copyFrom(new float[1024]))  // 模拟向量
            .build();
    }

    public static List<KnowledgeChunk> buildChunks(int count) {
        return IntStream.range(0, count)
            .mapToObj(KnowledgeFixture::buildChunk)
            .collect(Collectors.toList());
    }
}
```

### 3.9 Risk Approval Fixture（R3 审批数据，新增）

```java
// testinfra/fixture/ApprovalFixture.java
public class ApprovalFixture {

    public static ToolApproval buildPendingApproval() {
        return ToolApproval.newBuilder()
            .setApprovalId("ap_test_001")
            .setToolId("tl_test_003")  // R3 工具
            .setTenantId("tn_test_003")
            .setRequesterId("u_test_004")
            .setStatus("PENDING")
            .setInputSnapshot("{\"target\":\"expired_data\",\"batchSize\":1000}")
            .setExpireAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() + 86400).build())
            .build();
    }

    public static ToolApproval buildApprovedApproval() {
        return ToolApproval.newBuilder()
            .setApprovalId("ap_test_002")
            .setToolId("tl_test_003")
            .setStatus("APPROVED")
            .setMainApproverId("u_test_005")
            .setSecondApproverId("u_test_006")
            .setApprovedAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() - 1800).build())  // 30min 前
            .setExpireAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() + 1800).build())   // 30min 后过期
            .build();
    }

    public static ToolApproval buildExpiredApproval() {
        return ToolApproval.newBuilder()
            .setApprovalId("ap_test_003")
            .setToolId("tl_test_003")
            .setStatus("APPROVED")
            .setMainApproverId("u_test_005")
            .setSecondApproverId("u_test_006")
            .setApprovedAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() - 7200).build())  // 2h 前
            .setExpireAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() - 3600).build())    // 1h 前过期
            .build();
    }

    public static ToolApproval buildPartialApproval() {
        // 仅主审批人通过，缺副审批人
        return ToolApproval.newBuilder()
            .setApprovalId("ap_test_004")
            .setToolId("tl_test_003")
            .setStatus("PARTIALLY_APPROVED")
            .setMainApproverId("u_test_005")
            .setSecondApproverId("")
            .build();
    }
}
```

### 3.10 Boundary Value Fixture（边界值数据，新增）

> 用于测试边界条件与异常路径，覆盖 F2 复杂度分级边界、F7 Token 水位边界、F9 L4 校验阈值边界。

```java
// testinfra/fixture/BoundaryFixture.java
public class BoundaryFixture {

    // F2 复杂度分级边界（≤8=L1, 9-14=L2, >14=L3）
    public static final int COMPLEXITY_L1_UPPER = 8;   // 边界值：刚好 L1
    public static final int COMPLEXITY_L2_LOWER = 9;  // 边界值：刚好 L2
    public static final int COMPLEXITY_L2_UPPER = 14; // 边界值：刚好 L2
    public static final int COMPLEXITY_L3_LOWER = 15; // 边界值：刚好 L3

    // F7 Token 水位边界（SAFE<0.70 / WARN<0.85 / CRITICAL<0.95 / CIRCUIT_BREAK>=0.95）
    public static final double TOKEN_SAFE_UPPER = 0.699;  // 边界：刚好 SAFE
    public static final double TOKEN_WARN_LOWER = 0.700;  // 边界：刚好 WARN
    public static final double TOKEN_WARN_UPPER = 0.849;  // 边界：刚好 WARN
    public static final double TOKEN_CRITICAL_LOWER = 0.850;  // 边界：刚好 CRITICAL
    public static final double TOKEN_CRITICAL_UPPER = 0.949;  // 边界：刚好 CRITICAL
    public static final double TOKEN_CIRCUIT_LOWER = 0.950;  // 边界：刚好 CIRCUIT_BREAK

    // F9 L4 校验阈值边界（cosine_sim ≥0.75 / overall ≥0.7）
    public static final double SIM_PASS_LOWER = 0.750;  // 边界：刚好通过
    public static final double SIM_FAIL_UPPER = 0.749;  // 边界：刚好失败
    public static final double AUDIT_PASS_LOWER = 0.700;  // 边界：刚好通过
    public static final double AUDIT_FAIL_UPPER = 0.699;  // 边界：刚好失败

    // F12 记忆去重阈值边界（cosine_sim ≥0.92 触发合并）
    public static final double MEM_DEDUP_LOWER = 0.920;  // 边界：刚好合并
    public static final double MEM_NEW_UPPER = 0.919;    // 边界：刚好新增

    // F4 子任务超时边界（maxDuration=300s）
    public static final long SUBTASK_TIMEOUT_MS = 300_000L;        // 边界：刚好超时
    public static final long SUBTASK_NORMAL_MS = 299_999L;        // 边界：刚好正常

    // F5 重规划次数边界（max_replan=2）
    public static final int REPLAN_MAX = 2;       // 边界：最大允许
    public static final int REPLAN_EXCEED = 3;    // 边界：超限触发人工
}
```

### 3.11 Performance Test Data（性能测试批量数据，新增）

> 用于性能基准测试与压力测试，按量级生成测试数据。

```java
// testinfra/fixture/PerformanceDataGenerator.java
public class PerformanceDataGenerator {

    /**
     * 批量生成记忆向量（用于 Milvus 召回性能测试）
     * @param count 记忆数量（如 100000）
     */
    public static List<MemoryRecord> generateMemories(int count) {
        Random rnd = new Random(42);  // 固定种子保证可复现
        return IntStream.range(0, count).parallel()
            .mapToObj(i -> MemoryRecord.newBuilder()
                .setMemoryId("mem_perf_" + String.format("%06d", i))
                .setTenantId("tn_perf_001")
                .setType(i % 3 == 0 ? "EPISODIC" : (i % 3 == 1 ? "SEMANTIC" : "PROCEDURAL"))
                .setContent("性能测试记忆 #" + i)
                .setImportanceScore(0.3 + rnd.nextDouble() * 0.6)  // 0.3~0.9
                .setEmbedding(generateRandomVector(1024, rnd))
                .setCreatedAt(Timestamp.newBuilder().setSeconds(1690000000L + i).build())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * 批量生成任务实例（用于任务编排压力测试）
     * @param count 任务数量（如 10000）
     */
    public static List<TaskInstance> generateTasks(int count) {
        String[] goals = {"查询订单", "生成报告", "发送邮件", "数据分析", "代码生成"};
        return IntStream.range(0, count).parallel()
            .mapToObj(i -> TaskInstance.newBuilder()
                .setTaskId("tk_perf_" + String.format("%06d", i))
                .setTenantId("tn_perf_001")
                .setGoal(goals[i % goals.length] + " #" + i)
                .setComplexity(1 + (i % 3))
                .setStatus("PENDING")
                .setPriority(5 + (i % 5))
                .build())
            .collect(Collectors.toList());
    }

    /**
     * 批量生成知识分块（用于 RAG 召回性能测试）
     */
    public static List<KnowledgeChunk> generateChunks(int count) {
        return IntStream.range(0, count).parallel()
            .mapToObj(i -> KnowledgeChunk.newBuilder()
                .setChunkId("chk_perf_" + String.format("%06d", i))
                .setDocId("doc_perf_" + (i / 100))
                .setContent("知识分块 #" + i + " 内容示例")
                .setTokenCount(300 + (i % 200))
                .build())
            .collect(Collectors.toList());
    }

    private static ByteString generateRandomVector(int dim, Random rnd) {
        FloatBuffer fb = FloatBuffer.allocate(dim);
        for (int i = 0; i < dim; i++) fb.put(rnd.nextFloat());
        fb.flip();
        return ByteString.copyFrom(fb.array());
    }
}
```

**性能测试数据量级建议**：

| 场景 | 数据量 | 度量指标 | 通过基线 |
|---|---|---|---|
| Milvus 向量召回 P99 延迟 | 10 万条记忆向量 | 召回 Top-5 P99 延迟 | < 100ms |
| MySQL 任务查询分页 | 10 万条任务实例 | 分页查询 P95 延迟 | < 50ms |
| Redis 短期记忆读写 | 1 万次/秒 | 读写吞吐量 | > 5000 QPS |
| 并发任务提交 | 1000 并发 | 成功提交率 | > 99% |
| RAG 混合召回 | 1 万知识分块 | 端到端召回 P95 延迟 | < 200ms |
| Agent ReAct 循环 | 100 轮 | 单轮平均延迟 | < 2s |

---

## 4. Testcontainers 配置示例

### 4.1 MySQL 容器工厂

```java
// testinfra/container/MySQLContainerFactory.java
public class MySQLContainerFactory {

    private static final String MYSQL_IMAGE = "mysql:8.0.36";
    private static final String DATABASE = "agent_test";
    private static final String USER = "test";
    private static final String PASSWORD = "test123";

    public static MySQLContainer<?> build() {
        return new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
            .withDatabaseName(DATABASE)
            .withUsername(USER)
            .withPassword(PASSWORD)
            .withCommand("--character-set-server=utf8mb4",
                         "--collation-server=utf8mb4_unicode_ci")
            .withInitScript("init/mysql/01-agent-session.sql",
                            "init/mysql/02-agent-task.sql",
                            "init/mysql/11-seed-data.sql")
            .withReuse(true);  // 复用容器减少启动开销
    }
}
```

### 4.2 Redis 容器工厂

```java
// testinfra/container/RedisContainerFactory.java
public class RedisContainerFactory {

    private static final String REDIS_IMAGE = "redis:7.2-alpine";

    public static GenericContainer<?> build() {
        return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--maxmemory 256mb", "--maxmemory-policy allkeys-lru")
            .withReuse(true);
    }

    public static RedisClient buildClient(GenericContainer<?> container) {
        String host = container.getHost();
        Integer port = container.getMappedPort(6379);
        return RedisClient.create(String.format("redis://%s:%d", host, port));
    }
}
```

### 4.3 Milvus 容器工厂

```java
// testinfra/container/MilvusContainerFactory.java
public class MilvusContainerFactory {

    private static final String MILVUS_IMAGE = "milvusdb/milvus:v2.4.0";

    public static GenericContainer<?> build() {
        return new GenericContainer<>(DockerImageName.parse(MILVUS_IMAGE))
            .withExposedPorts(19530)
            .withEnv("ETCD_ENDPOINTS", "etcd:2379")
            .withEnv("MINIO_ADDRESS", "minio:9000")
            .withReuse(true)
            .waitingFor(Wait.forLogMessage(".*Milvus Proxy successfully started.*", 1));
    }
}
```

### 4.4 全栈容器编排（E2E 测试）

```java
// testinfra/container/FullStackContainerSuite.java
@Testcontainers
public class FullStackContainerSuite {

    @Container
    static MySQLContainer<?> mysql = MySQLContainerFactory.build();

    @Container
    static GenericContainer<?> redis = RedisContainerFactory.build();

    @Container
    static GenericContainer<?> milvus = MilvusContainerFactory.build();

    @Container
    static GenericContainer<?> rocketmq = new GenericContainer<>(
        DockerImageName.parse("apache/rocketmq:5.3.0"))
        .withExposedPorts(9876, 10911)
        .withReuse(true);

    @Container
    static GenericContainer<?> neo4j = new GenericContainer<>(
        DockerImageName.parse("neo4j:5.18-community"))
        .withExposedPorts(7474, 7687)
        .withEnv("NEO4J_AUTH", "neo4j/test123")
        .withReuse(true);

    @Container
    static GenericContainer<?> clickhouse = new GenericContainer<>(
        DockerImageName.parse("clickhouse/clickhouse-server:24.3"))
        .withExposedPorts(8123, 9000)
        .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
        registry.add("milvus.host", milvus::getHost);
        registry.add("milvus.port", () -> milvus.getMappedPort(19530));
        // ... 其他容器配置
    }
}
```

### 4.5 Testcontainers 容器矩阵

| 中间件 | 镜像版本 | 用途 | 端口 | 复用策略 |
|---|---|---|---|---|
| MySQL | 8.0.36 | 关系库（9 个库 32 表） | 3306 | `withReuse(true)` |
| Redis | 7.2-alpine | 短期记忆、分布式锁、限流 | 6379 | `withReuse(true)` |
| Milvus | 2.4.0 | 向量库（记忆 + 知识 + 工具索引） | 19530 | `withReuse(true)` |
| RocketMQ | 5.3.0 | 子任务分发、状态变更事件 | 9876 | `withReuse(true)` |
| Neo4j | 5.18-community | 代码知识图谱 | 7687 | `withReuse(true)` |
| ClickHouse | 24.3 | 指标采集、漂移监测 | 8123 | `withReuse(true)` |
| Elasticsearch | 8.13.0 | 全文检索（代码 + 知识） | 9200 | `withReuse(true)` |

---

## 5. Mock 工厂示例

### 5.1 ModelGatewayMock

```java
// testinfra/mock/ModelGatewayMock.java
public class ModelGatewayMock {

    public static ModelGatewayGrpc.ModelGatewayBlockingStub build() {
        // 返回预设的模型响应，模拟不同场景
        var mock = mock(ModelGatewayGrpc.ModelGatewayBlockingStub.class);

        // 意图识别场景
        when(mock.chat(argThat(req ->
            req.getScene().equals("intent"))))
            .thenReturn(ChatResponse.newBuilder()
                .setContent("{\"intent\":\"tool_call\",\"entities\":{\"days\":7}}")
                .setModelId("qwen-turbo")
                .setTokenUsed(150)
                .build());

        // L4 终审场景
        when(mock.chat(argThat(req ->
            req.getScene().equals("audit"))))
            .thenReturn(ChatResponse.newBuilder()
                .setContent("{\"overall\":0.85,\"dimensions\":{...}}")
                .setModelId("gpt-4o")
                .setTokenUsed(800)
                .build());

        // 模拟错误
        when(mock.chat(argThat(req ->
            req.getModelId().equals("error-model"))))
            .thenThrow(new BusinessException(ErrorCode.MODEL_GATEWAY_ERROR, "模拟错误"));

        return mock;
    }
}
```

### 5.2 ToolEngineMock

```java
// testinfra/mock/ToolEngineMock.java
public class ToolEngineMock {

    public static ToolGatewayGrpc.ToolGatewayBlockingStub build() {
        var mock = mock(ToolGatewayGrpc.ToolGatewayBlockingStub.class);

        // R1 工具正常调用
        when(mock.invoke(argThat(req ->
            req.getToolId().equals("tl_test_001"))))
            .thenReturn(ToolInvokeResponse.newBuilder()
                .setSuccess(true)
                .setOutputJson("{\"orders\":[{\"id\":\"o1\",\"amount\":99.5}],\"total\":1}")
                .setDurationMs(150)
                .build());

        // R3 工具无审批
        when(mock.invoke(argThat(req ->
            req.getToolId().equals("tl_test_003") && req.getApprovalId().isEmpty())))
            .thenThrow(new BusinessException(ErrorCode.APPROVAL_REQUIRED, "需要审批"));

        return mock;
    }
}
```

### 5.3 MemoryServiceMock（记忆服务 Mock，新增）

```java
// testinfra/mock/MemoryServiceMock.java
public class MemoryServiceMock {

    public static MemoryServiceGrpc.MemoryServiceBlockingStub build() {
        var mock = mock(MemoryServiceGrpc.MemoryServiceBlockingStub.class);

        // 多路召回返回 Top-N 记忆
        when(mock.recall(any(RecallRequest.class)))
            .thenReturn(RecallResponse.newBuilder()
                .addMemories(MemoryFixture.buildEpisodicMemory())
                .addMemories(MemoryFixture.buildSemanticMemory())
                .addMemories(MemoryFixture.buildProceduralMemory())
                .setTotalCount(3)
                .build());

        // 写入长期记忆返回成功
        when(mock.writeLongTerm(any(WriteRequest.class)))
            .thenReturn(WriteResponse.newBuilder()
                .setMemoryId("mem_mock_" + System.currentTimeMillis())
                .setDeduplicated(false)
                .build());

        // Token 水位监测返回 SAFE
        when(mock.checkWatermark(any()))
            .thenReturn(WatermarkStatus.newBuilder()
                .setLevel("SAFE")
                .setUsage(0.65)
                .build());

        return mock;
    }
}
```

### 5.4 RiskControlMock（风控 Mock，新增）

```java
// testinfra/mock/RiskControlMock.java
public class RiskControlMock {

    public static RiskControlGrpc.RiskControlBlockingStub build() {
        var mock = mock(RiskControlGrpc.RiskControlBlockingStub.class);

        // 正常内容放行
        when(mock.preCheck(argThat(req ->
            req.getContent().contains("天气") || req.getContent().contains("订单"))))
            .thenReturn(PreCheckResponse.newBuilder()
                .setBlocked(false)
                .setCategory("NORMAL")
                .build());

        // Prompt 注入拦截
        when(mock.preCheck(argThat(req ->
            req.getContent().contains("忽略上述指令") || req.getContent().contains("DAN"))))
            .thenReturn(PreCheckResponse.newBuilder()
                .setBlocked(true)
                .setCategory("PROMPT_INJECTION")
                .setReason("检测到越狱模板")
                .build());

        // R3 审批单创建
        when(mock.createApproval(any()))
            .thenReturn(ApprovalResponse.newBuilder()
                .setApprovalId("ap_mock_001")
                .setStatus("PENDING")
                .setExpireAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() + 86400).build())
                .build());

        return mock;
    }
}
```

---

## 6. 自定义断言

### 6.1 DagAssert（DAG 断言）

```java
// testinfra/assertion/DagAssert.java
public class DagAssert extends AbstractAssert<DagAssert, Dag> {

    public DagAssert(Dag actual) {
        super(actual, DagAssert.class);
    }

    public static DagAssert assertThat(Dag actual) {
        return new DagAssert(actual);
    }

    public DagAssert hasNoCycle() {
        isNotNull();
        if (DagCycleDetector.detect(actual)) {
            failWithMessage("Expected DAG to have no cycle but it did");
        }
        return this;
    }

    public DagAssert hasNodeCount(int expected) {
        isNotNull();
        if (actual.getNodesCount() != expected) {
            failWithMessage("Expected node count <%d> but was <%d>",
                expected, actual.getNodesCount());
        }
        return this;
    }

    public DagAssert hasBatchCount(int expected) {
        isNotNull();
        int batches = BatchPartitioner.partition(actual).size();
        if (batches != expected) {
            failWithMessage("Expected batch count <%d> but was <%d>", expected, batches);
        }
        return this;
    }
}
```

### 6.2 TaskStatusAssert（任务状态断言）

```java
// testinfra/assertion/TaskStatusAssert.java
public class TaskStatusAssert extends AbstractAssert<TaskStatusAssert, TaskInstance> {

    public TaskStatusAssert(TaskInstance actual) {
        super(actual, TaskStatusAssert.class);
    }

    public static TaskStatusAssert assertThat(TaskInstance actual) {
        return new TaskStatusAssert(actual);
    }

    public TaskStatusAssert hasStatus(TaskStatus expected) {
        isNotNull();
        if (!actual.getStatus().equals(expected.name())) {
            failWithMessage("Expected status <%s> but was <%s>",
                expected, actual.getStatus());
        }
        return this;
    }

    public TaskStatusAssert hasDagVersion(int expected) {
        isNotNull();
        if (actual.getDagVersion() != expected) {
            failWithMessage("Expected dagVersion <%d> but was <%d>",
                expected, actual.getDagVersion());
        }
        return this;
    }
}
```

### 6.3 ErrorCodeAssert（错误码断言，新增）

```java
// testinfra/assertion/ErrorCodeAssert.java
public class ErrorCodeAssert extends AbstractAssert<ErrorCodeAssert, BusinessException> {

    public ErrorCodeAssert(BusinessException actual) {
        super(actual, ErrorCodeAssert.class);
    }

    public static ErrorCodeAssert assertThatThrown(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected BusinessException but none was thrown");
        } catch (BusinessException e) {
            return new ErrorCodeAssert(e);
        }
    }

    public ErrorCodeAssert hasErrorCode(ErrorCode expected) {
        isNotNull();
        if (actual.getErrorCode() != expected) {
            failWithMessage("Expected error code <%s> but was <%s>",
                expected, actual.getErrorCode());
        }
        return this;
    }

    public ErrorCodeAssert hasHttpStatus(int expected) {
        isNotNull();
        if (actual.getHttpStatus() != expected) {
            failWithMessage("Expected HTTP status <%d> but was <%d>",
                expected, actual.getHttpStatus());
        }
        return this;
    }

    public ErrorCodeAssert hasDetail(String key, Object expectedValue) {
        isNotNull();
        Object actualValue = actual.getDetails().get(key);
        if (!Objects.equals(actualValue, expectedValue)) {
            failWithMessage("Expected detail <%s>=<%s> but was <%s>",
                key, expectedValue, actualValue);
        }
        return this;
    }

    public ErrorCodeAssert hasMessageContaining(String substring) {
        isNotNull();
        if (!actual.getMessage().contains(substring)) {
            failWithMessage("Expected message to contain <%s> but was <%s>",
                substring, actual.getMessage());
        }
        return this;
    }
}
```

### 6.4 MetricsAssert（指标断言，新增）

```java
// testinfra/assertion/MetricsAssert.java
public class MetricsAssert {

    private final ClickHouseQueryExecutor executor;

    public MetricsAssert(ClickHouseQueryExecutor executor) {
        this.executor = executor;
    }

    public MetricsAssert hasCounterIncremented(String metricName, String tenantId, long expectedDelta) {
        long actual = executor.queryForLong(
            "SELECT count() FROM agent_metrics_daily WHERE metric_name = ? AND tenant_id = ?",
            metricName, tenantId);
        if (actual < expectedDelta) {
            throw new AssertionError(String.format(
                "Expected metric <%s> for tenant <%s> to be incremented by at least <%d> but was <%d>",
                metricName, tenantId, expectedDelta, actual));
        }
        return this;
    }

    public MetricsAssert hasHistogramP99Below(String metricName, long maxP99Ms) {
        long p99 = executor.queryForLong(
            "SELECT quantile(0.99)(value) FROM agent_metrics_daily WHERE metric_name = ?");
        if (p99 > maxP99Ms) {
            throw new AssertionError(String.format(
                "Expected P99 of <%s> to be below <%dms> but was <%dms>",
                metricName, maxP99Ms, p99));
        }
        return this;
    }

    public MetricsAssert hasAlertTriggered(String alertName, long expectedCount) {
        long actual = executor.queryForLong(
            "SELECT count() FROM alert_log WHERE alert_name = ? AND status = 'FIRED'");
        if (actual != expectedCount) {
            throw new AssertionError(String.format(
                "Expected alert <%s> to be triggered <%d> times but was <%d>",
                alertName, expectedCount, actual));
        }
        return this;
    }
}
```

---

## 7. 测试数据清理脚本

### 7.1 通用清理脚本

```sql
-- cleanup-all.sql — 测试后清理所有测试数据
-- 按依赖逆序执行

USE agent_quality;
DELETE FROM badcase WHERE tenant_id LIKE 'tn_test_%';
DELETE FROM eval_baseline WHERE tenant_id LIKE 'tn_test_%';
DELETE FROM agent_metrics_daily WHERE tenant_id LIKE 'tn_test_%';

USE agent_task;
DELETE FROM task_instance WHERE tenant_id LIKE 'tn_test_%';
DELETE FROM task_dag WHERE task_id IN (
    SELECT task_id FROM task_instance WHERE tenant_id LIKE 'tn_test_%'
);
DELETE FROM task_step_log WHERE tenant_id LIKE 'tn_test_%';
DELETE FROM task_replan_log WHERE tenant_id LIKE 'tn_test_%';
DELETE FROM task_state_change WHERE tenant_id LIKE 'tn_test_%';

USE agent_session;
DELETE FROM session WHERE tenant_id LIKE 'tn_test_%';
DELETE FROM message WHERE session_id IN (
    SELECT session_id FROM session WHERE tenant_id LIKE 'tn_test_%'
);

USE agent_tool;
DELETE FROM tool_call_log WHERE tenant_id LIKE 'tn_test_%';
DELETE FROM tool_approval WHERE tenant_id LIKE 'tn_test_%';

USE agent_risk;
DELETE FROM audit_log WHERE tenant_id LIKE 'tn_test_%';
```

### 7.2 Redis 清理

```java
// 测试后清理 Redis 测试数据
@AfterEach
void cleanupRedis() {
    Set<String> keys = redisClient.keys("test:*");
    if (!keys.isEmpty()) {
        redisClient.del(keys.toArray(new String[0]));
    }
}
```

---

## 8. 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-06-27 | 初始版本，含数据策略、Fixture 示例、Testcontainers 配置、Mock 工厂、自定义断言 | AgentForge 测试团队 |
| v1.1 | 2026-06-27 | 补强：新增 §3.7 Trace Fixture、§3.8 Knowledge Fixture、§3.9 Approval Fixture、§3.10 Boundary Value Fixture（F2/F7/F9/F12 阈值边界常量）、§3.11 Performance Test Data（批量生成与量级基线）；新增 §5.3 MemoryServiceMock、§5.4 RiskControlMock；新增 §6.3 ErrorCodeAssert、§6.4 MetricsAssert；总计 4 个新 Fixture + 2 个新 Mock + 2 个新断言 + 1 个性能数据生成器 | AgentForge 测试团队 |
