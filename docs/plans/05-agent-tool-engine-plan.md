# agent-tool-engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `agent-tool-engine`（端口 8090 / gRPC 9090）模块补齐工具引擎全栈能力：ToolEngine gRPC 服务（4 RPC：CallTool / RegisterTool / ListTools / GetToolMeta），覆盖 ToolRegistry 注册、ToolGateway 网关、RiskClassifier 风险分类、ApprovalStore 人工审批、SandboxBorrower Docker 沙箱借用、ToolCache Redis 缓存、ToolCallAuditor 审计、ToolSemanticRecaller 语义召回、ResultCleaner 结果清洗九项核心能力。已有 24 个骨架文件（api 接口 + enums + exception + model POJO），本计划 T1~T12 落地全部业务逻辑、JPA Entity、Docker 沙箱对接、Redis 缓存与端到端集成测试，对齐 v5 审核 §6 P6-6 整改项与 doc 05-tool-engine 设计。

**Architecture:** 单 Spring Boot 应用 `agent-tool-engine`，对外暴露 gRPC 服务 ToolEngine（端口 9090）+ Actuator（8090）。内部以 `tool_meta` 表（MySQL 逻辑库 `agent_tool`）为工具元数据主存，Redis 承载 ToolCache（短期缓存调用结果 + rate limiter），Docker 提供沙箱执行环境（高危工具隔离执行），`tool_call_audit_log` 表记录所有调用审计。上游接收 agent-runtime 的 ToolCallRequest，下游对接外部工具（HTTP / Shell / Python / MCP 协议）。依赖 agent-proto（Protobuf 契约 `tool.proto` / `common.proto`）与 agent-common（ToolRiskLevel / ToolCallStatus / ErrorCode / BusinessException）。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / grpc-spring-boot-starter 3.1.0.RELEASE（net.devh）/ spring-boot-starter-data-jpa / spring-boot-starter-data-redis / MySQL Connector 8 / docker-java 3.3.0（沙箱）/ Caffeine 3.1.8（本地二级缓存）/ Jackson / Lombok / JUnit 5 / Mockito 5 / AssertJ 3.25.3 / Awaitility 4.2.0 / H2（MySQL 模式，测试备选）/ jedis-mock（测试备选）/ Testcontainers 1.19.7（可选 Docker 集成路径）/ WireMock 3.x（外部工具 mock）

---

## 设计文档对齐

| 项 | 来源 | 锁定值 |
|---|---|---|
| agent-tool-engine HTTP / gRPC 端口 | doc 00-overview §3.1 | 8090（HTTP） / 9090（gRPC） |
| 逻辑库 | doc 01-database §0.4 / §2.2 | `agent_tool`（tool_meta / tool_call_audit_log / tool_approval / tool_quota） |
| ToolEngine gRPC 4 RPC | `agent-proto/src/main/proto/tool.proto` | CallTool / RegisterTool / ListTools / GetToolMeta |
| proto 生成包名 | tool.proto / common.proto | `agentplatform.tool.v1` / `agentplatform.common.v1` |
| 执行器类型 4 类 | doc 05-tool-engine §3.1 + ExecutorType 枚举 | HTTP_API / SHELL / PYTHON / MCP |
| 副作用分类 | doc 05-tool-engine §3.2 + SideEffect 枚举 | NONE / READ_ONLY / WRITE_LOCAL / WRITE_EXTERNAL / DESTRUCTIVE |
| 调用状态 5 态 | doc 05-tool-engine §3.3 + ToolCallStatus 枚举 | PENDING / APPROVING / RUNNING / SUCCESS / FAILED / TIMEOUT |
| 风险等级 3 级 | doc 05-tool-engine §4.1 + ToolRiskLevel 枚举 | R1（低）/ R2（中）/ R3（高） |
| 风险分级规则 | doc 05-tool-engine §4.2 | R1：SideEffect ∈ {NONE, READ_ONLY}；R2：{WRITE_LOCAL}；R3：{WRITE_EXTERNAL, DESTRUCTIVE}；额外：R3 + 涉及 PII → 强制审批 |
| 审批策略 | doc 05-tool-engine §4.3 | R1 → 自动放行；R2 → 同租户最近 1h 内已批过且参数同 → 自动放行；R3 → 强制人工 |
| 审批 SLA | doc 05-tool-engine §4.4 | R2 默认 5min 超时 → 拒绝；R3 默认 30min 超时 → 拒绝；可配置 |
| Docker 沙箱规格 | doc 05-tool-engine §5.2 | image=`agent-sandbox:latest` / cpu=1.0 / memory=512MB / network=none（除 R1 工具）/ tmpfs=/tmp 64MB / 挂载 /workspace 只读 / 超时 60s |
| 沙箱借用流程 | doc 05-tool-engine §5.3 | borrow(spec) → 等待 30s 启动 → exec → capture stdout/stderr → return（销毁容器） |
| 沙箱池 | doc 05-tool-engine §5.4 | 预热池大小 5（可配） / 最大并发 20 / 空闲 10min 销毁 |
| Redis ToolCache key 命名 | doc 05-tool-engine §6.2 | `tool:cache:{toolId}:{paramsHash}:{tenantId}` TTL=300s（默认） |
| 缓存策略 | doc 05-tool-engine §6.3 | 仅 R1 + READ_ONLY 启用；显式 noCache=true 跳过；命中后返回 cachehit=true |
| Rate Limiter | doc 05-tool-engine §6.5 | 同 tenantId + toolId 每秒最多 N 次（默认 10），Redis 令牌桶 |
| 审计字段 | doc 05-tool-engine §7.1 | tool_call_audit_log：call_id / tenant_id / agent_id / tool_id / params_hash / status / risk_level / started_at / ended_at / duration_ms / cost_tokens / exit_code / error_message / sandbox_container_id / approver_id / cache_hit |
| 审计延迟要求 | doc 05-tool-engine §7.2 | 同步落库（commit 前），不强异步，确保失败可追溯 |
| 语义召回 | doc 05-tool-engine §8.2 | 调用 agent-memory RecallMemory（type=PROCEDURAL）→ 返回历史相似工具调用结果 → 排序后 topK=3 返回给 agent prompt |
| 语义召回 fallback | doc 05-tool-engine §8.4 | memory 服务不可用 → 降级到关键词匹配（tool description 含义） |
| 结果清洗 | doc 05-tool-engine §9.1 | (a) 截断超长输出（默认 8KB）；(b) 脱敏 PII（手机号 / 邮箱 / 身份证 / API key）；(c) 移除 ANSI 控制字符；(d) 修剪 trailing whitespace |
| 脱敏规则 | doc 05-tool-engine §9.2 | 手机号 `1[3-9]\d{9}` → `1**********`；邮箱 → `***@***`；API key（sk-开头 48 字符）→ `sk-****` |
| 工具注册字段 | doc 05-tool-engine §10.1 | toolId / name / description / version / executorType / endpoint / schema(JSON Schema) / sideEffect / riskLevel（可被 classifier 覆盖） / enabled / ownerTenantId / createdAt |
| 工具 schema | doc 05-tool-engine §10.3 | JSON Schema 2020-12，定义 params + returns + examples |
| 错误码域 | doc 05-tool-engine §12.4 | TOOL_NOT_FOUND(404) / TOOL_DISABLED(403) / TOOL_VALIDATION_FAILED(400) / TOOL_APPROVAL_REQUIRED(403) / TOOL_APPROVAL_TIMEOUT(408) / TOOL_QUOTA_EXHAUSTED(429) / TOOL_SANDBOX_FAILURE(500) / TOOL_EXECUTION_TIMEOUT(504) / TOOL_CACHE_MISS(404) |
| gRPC 服务类签名 | doc 05-tool-engine §11.1 | `@GrpcService extends ToolEngineGrpc.ToolEngineImplBase` |
| 配置参数 | doc 05-tool-engine §13 | `tool.cache.ttlSeconds=300` / `tool.cache.maxEntries=10000` / `tool.ratelimit.defaultQps=10` / `tool.sandbox.pool.size=5` / `tool.sandbox.pool.maxConcurrent=20` / `tool.sandbox.idleTimeoutMs=600000` / `tool.sandbox.execTimeoutMs=60000` / `tool.approval.r2.timeoutMs=300000` / `tool.approval.r3.timeoutMs=1800000` / `tool.cleaner.maxBytes=8192` |
| 异常分级与重试 | doc 05-tool-engine §12.3 | 工具调用瞬时失败：2 次（指数退避 100/300 ms）；沙箱启动失败：3 次；审批超时：不可重试（用户决策） |
| ADR-003 | docs/adr/ADR-003-sandbox-strategy.md | 选 Docker 不选 Firecracker：开发期足够，生产再升 microVM |
| 测试用例 | `docs/tests/unit-test-cases.md` §8 | UT-TOOL-001~020 |
| v5 审核整改项 | `docs/tests/tdd-audit-report-v5.md` §6 P6-6 | 实现 agent-tool-engine T1-T12 D2 +1.0 |

---

## 文件结构总览

### 已完成骨架文件（24 个，仅接口/枚举/异常/POJO，无业务实现）

| 文件 | 当前状态 | 职责 |
|---|---|---|
| `agent-tool-engine/pom.xml` | 占位 | Maven 配置（需补全依赖） |
| `agent-tool-engine/src/main/resources/application.yml` | 占位 | 端口 + 数据源 + Redis + Docker（需补全） |
| `agent-tool-engine/src/main/java/com/agent/tool/engine/ToolEngineApplication.java` | 占位 | Spring Boot 启动类（需补全） |
| `.../api/ToolRegistry.java` | 接口 | 工具注册 / 查询 / 启停 |
| `.../api/ToolGateway.java` | 接口 | 调用入口（含审批/沙箱/缓存/审计编排） |
| `.../api/RiskClassifier.java` | 接口 | 风险等级分类（R1/R2/R3） |
| `.../api/ApprovalStore.java` | 接口 | 人工审批存取 + 等待 |
| `.../api/SandboxBorrower.java` | 接口 | Docker 沙箱借用 |
| `.../api/ToolCache.java` | 接口 | Redis 缓存读 / 写 |
| `.../api/ToolCallAuditor.java` | 接口 | 审计日志落库 |
| `.../api/ToolSemanticRecaller.java` | 接口 | 语义召回历史调用 |
| `.../api/ResultCleaner.java` | 接口 | 结果截断 + 脱敏 |
| `.../enums/ExecutorType.java` | 枚举 | HTTP_API / SHELL / PYTHON / MCP |
| `.../enums/SideEffect.java` | 枚举 | NONE / READ_ONLY / WRITE_LOCAL / WRITE_EXTERNAL / DESTRUCTIVE |
| `.../enums/ToolCallStatus.java` | 枚举 | PENDING / APPROVING / RUNNING / SUCCESS / FAILED / TIMEOUT |
| `.../enums/ToolRiskLevel.java` | 枚举 | R1 / R2 / R3 |
| `.../exception/ToolEngineException.java` | 基类 | 工具引擎异常基类 |
| `.../exception/ToolValidationException.java` | 子类 | 参数校验失败 |
| `.../exception/ToolApprovalException.java` | 子类 | 审批相关（含超时） |
| `.../exception/ToolQuotaExhaustedException.java` | 子类 | 限流/配额耗尽 |
| `.../model/ToolMeta.java` | POJO | 工具元数据（需升级为 JPA @Entity） |
| `.../model/ToolSchema.java` | POJO | JSON Schema 包装 |
| `.../model/ToolCallRequest.java` | POJO | 调用请求 |
| `.../model/ToolCallResult.java` | POJO | 调用结果 |
| `.../model/ApprovalRecord.java` | POJO | 审批记录（需升级为 @Entity） |
| `.../model/ToolCallAuditLog.java` | POJO | 审计日志（需升级为 @Entity） |
| `.../model/ToolRecallResult.java` | POJO | 召回结果 |

### 待新增文件（T1~T12）

| 文件 | Task | 职责 |
|---|---|---|
| `.../config/ToolEngineProperties.java` | T1 | `@ConfigurationProperties("tool")` |
| `.../config/RedisConfig.java` | T1 | RedisTemplate / StringRedisTemplate bean |
| `.../config/DockerClientConfig.java` | T1 | docker-java DockerClient bean |
| `.../config/MemoryClientConfig.java` | T1 | gRPC stub（agent-memory MemoryService）|
| `.../repository/ToolMetaRepository.java` | T2 | JPA Repository |
| `.../repository/ApprovalRecordRepository.java` | T2 | JPA Repository |
| `.../repository/ToolCallAuditLogRepository.java` | T2 | JPA Repository |
| `.../model/ToolMeta.java`（升级） | T2 | @Entity |
| `.../model/ApprovalRecord.java`（升级） | T2 | @Entity |
| `.../model/ToolCallAuditLog.java`（升级） | T2 | @Entity |
| `.../registry/ToolRegistryImpl.java` | T3 | 实现 ToolRegistry |
| `.../registry/ToolSchemaValidator.java` | T3 | JSON Schema 校验（用 networknt json-schema-validator） |
| `.../risk/RiskClassifierImpl.java` | T4 | 实现 RiskClassifier |
| `.../risk/RiskRule.java` | T4 | 规则模型 |
| `.../approval/ApprovalStoreImpl.java` | T5 | 实现 ApprovalStore |
| `.../approval/ApprovalWaiter.java` | T5 | 阻塞等待（CompletableFuture + Redis pub/sub） |
| `.../sandbox/SandboxBorrowerImpl.java` | T6 | 实现 SandboxBorrower（docker-java） |
| `.../sandbox/SandboxPool.java` | T6 | 预热池 + 借还管理 |
| `.../sandbox/SandboxSpec.java` | T6 | 沙箱规格（image / cpu / memory / network / mounts） |
| `.../cache/ToolCacheImpl.java` | T7 | 实现 ToolCache（Redis + Caffeine 二级） |
| `.../cache/CacheKeyBuilder.java` | T7 | key 命名（toolId/paramsHash/tenantId） |
| `.../cache/ParamsHasher.java` | T7 | SHA-256(params) |
| `.../gateway/ToolGatewayImpl.java` | T8 | 实现 ToolGateway（编排：classify→approve→cache→sandbox→audit→clean） |
| `.../gateway/HttpExecutor.java` | T8 | HTTP_API 类型执行器 |
| `.../gateway/ShellExecutor.java` | T8 | SHELL 类型执行器（沙箱内） |
| `.../gateway/PythonExecutor.java` | T8 | PYTHON 类型执行器（沙箱内） |
| `.../gateway/McpExecutor.java` | T8 | MCP 协议执行器（占位 + 接口预留） |
| `.../gateway/RateLimiter.java` | T8 | Redis 令牌桶限流 |
| `.../audit/ToolCallAuditorImpl.java` | T9 | 实现 ToolCallAuditor |
| `.../audit/AuditContextBuilder.java` | T9 | 构造审计上下文 |
| `.../recall/ToolSemanticRecallerImpl.java` | T10 | 实现 ToolSemanticRecaller |
| `.../recall/MemoryServiceClient.java` | T10 | gRPC 调用 agent-memory |
| `.../recall/KeywordFallbackRecaller.java` | T10 | memory 不可用降级 |
| `.../cleaner/ResultCleanerImpl.java` | T11 | 实现 ResultCleaner |
| `.../cleaner/PiiRedactor.java` | T11 | 正则脱敏 |
| `.../cleaner/AnsiStripper.java` | T11 | ANSI 控制符清除 |
| `.../grpc/ToolEngineGrpcImpl.java` | T12 | ToolEngine gRPC 4 RPC 实现 |
| `.../grpc/ToolCallMapper.java` | T12 | proto ↔ JPA 映射 |
| `.../grpc/GrpcExceptionAdvice.java` | T12 | gRPC Status 异常翻译 |

---

## Tasks

### Task T1: 骨架补全（pom + application.yml + 启动类）

**目标：** 把 24 个骨架文件外的 Maven / 配置 / 启动类补齐到可启动状态。

**Red：**
- [ ] 在 `ToolEngineApplicationTest.java` 写 `contextLoads()` 测试，断言 Spring Context 加载成功。
- [ ] 在 `application-test.yml` 配置 H2 MySQL 模式 + jedis-mock + 关闭 docker 客户端（`tool.docker.enabled=false`）。
- [ ] 运行测试，预期失败（pom 缺依赖 / yml 空）。

**Green：**
- [ ] 补全 `pom.xml`：parent / web / data-jpa / data-redis / validation / mysql-connector / grpc-starter / docker-java / caffeine / jackson / networknt-json-schema-validator / lombok / test 全套。
- [ ] 补全 `application.yml`：`server.port=8090` / `grpc.server.port=9090` / `grpc.client.GLOBAL.negotiationType=PLAINTEXT` / MySQL agent_tool / Redis / Docker host 配置 / `tool.*` 全部配置项（对齐 §13）。
- [ ] 补全 `ToolEngineApplication.java`：`@SpringBootApplication(scanBasePackages="com.agent.tool.engine")` + `@EnableJpaRepositories` + `@EnableScheduling` + `@EnableCaching`。
- [ ] 新建 `ToolEngineProperties.java`：`@ConfigurationProperties(prefix="tool")`，字段对齐 §13，内部静态类 `Cache` / `RateLimit` / `Sandbox` / `Approval` / `Cleaner` / `Docker`。
- [ ] 新建 `RedisConfig.java`：`RedisTemplate<String, Object>`（Jackson serializer） / `StringRedisTemplate`。
- [ ] 新建 `DockerClientConfig.java`：`@Bean DockerClient`（用 `DefaultDockerClientConfig` + `LocalConfiguration`），条件 `@ConditionalOnProperty(name="tool.docker.enabled", havingValue="true", matchIfMissing=true)`。
- [ ] 新建 `MemoryClientConfig.java`：gRPC stub bean（`MemoryServiceBlockingStub` / `MemoryServiceFutureStub`），指向 agent-memory:9088。
- [ ] 运行 `contextLoads()`，预期通过。

**Refactor：**
- [ ] 拆分 `application.yml` 为 `application.yml` + `application-dev.yml` + `application-test.yml`。
- [ ] `ToolEngineProperties.Sandbox` 字段命名清晰（`poolSize` / `maxConcurrent` / `idleTimeoutMs` / `execTimeoutMs`）。

**Commit：** `feat(agent-tool-engine): T1 scaffold pom/yml/startup, context loads`

---

### Task T2: ToolMeta / ApprovalRecord / ToolCallAuditLog JPA Entity + Repository

**目标：** 把 3 个 POJO 升级为 JPA @Entity，对应 `tool_meta` / `tool_approval` / `tool_call_audit_log` 表，并补 Repository。

**Red：**
- [ ] 在 `ToolMetaRepositoryTest.java` / `ApprovalRecordRepositoryTest.java` / `ToolCallAuditLogRepositoryTest.java`（@DataJpaTest + H2 MySQL 模式）写：
  - `ToolMetaRepositoryTest.saveAndFindByToolId` 保存 ToolMeta 后按 toolId 查询，断言字段完整。
  - `findByOwnerTenantIdAndEnabledTrue` 查询租户已启用工具。
  - `findByNameContaining` 名称模糊查询。
  - `ApprovalRecordRepositoryTest.findByCallId` 按 callId 查审批记录。
  - `findByApproverIdAndStatus` 按 approver 查待审。
  - `updateStatusById` 单条更新状态（modifying query）。
  - `ToolCallAuditLogRepositoryTest.findByTenantIdAndCallId` 查审计记录。
  - `findByStartedAtBetween` 按时间区间查询。
  - 测试预期失败（实体未加 @Entity）。

**Green：**
- [ ] 升级 `ToolMeta.java` 为 @Entity，字段对齐 doc 01-database §2.2：
  - `id`（Long，@Id @GeneratedValue）
  - `toolId`（String，UUID，唯一索引）
  - `name`（String）
  - `description`（String，TEXT）
  - `version`（String）
  - `executorType`（@Enumerated STRING）
  - `endpoint`（String）
  - `schemaJson`（@Lob，JSON Schema）
  - `sideEffect`（@Enumerated STRING）
  - `riskLevel`（@Enumerated STRING，可被 classifier 覆盖）
  - `enabled`（Boolean）
  - `ownerTenantId`（String，索引）
  - `cacheable`（Boolean）
  - `rateLimitQps`（Integer，可空，null 用默认）
  - `createdAt` / `updatedAt`（Instant，@Version）
- [ ] 升级 `ApprovalRecord.java` 为 @Entity：
  - `id` / `callId`（索引）/ `tenantId` / `agentId` / `toolId` / `riskLevel` / `paramsHash` / `status`（PENDING / APPROVED / REJECTED / TIMEOUT）/ `approverId`（可空）/ `reason` / `createdAt` / `decidedAt` / `expireAt`。
- [ ] 升级 `ToolCallAuditLog.java` 为 @Entity，字段对齐 §7.1 全部审计字段。
- [ ] 新建 3 个 Repository 接口 + 各 4~6 个查询方法。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 抽 `BaseEntity`（id / createdAt / updatedAt / @Version）。
- [ ] 显式 `@Column(name="snake_case")`。

**Commit：** `feat(agent-tool-engine): T2 ToolMeta + Approval + AuditLog JPA entities`

---

### Task T3: ToolRegistryImpl 注册实现

**目标：** 实现 `ToolRegistry` 接口：工具注册 / 启停 / 查询 / schema 校验。

**Red：**
- [ ] 在 `ToolRegistryImplTest.java` 写：
  - `register_newTool_persists` 注册新工具，断言返回 toolId + DB 落库。
  - `register_duplicateName_throws` 同租户重名 → 抛 `ToolValidationException`。
  - `register_invalidSchema_throws` JSON Schema 不合规 → 抛 `ToolValidationException`。
  - `enable_toggle` 启用 / 禁用切换。
  - `findById_exists` / `findById_notFound_throws`。
  - `listByTenant_returnsEnabledOnly` 默认只返回 enabled。
  - `listByTenant_includeDisabled` 包含 disabled。
  - `update_existingTool_bumpsVersion` 更新时 version 自增。

**Green：**
- [ ] 新建 `ToolSchemaValidator.java`：用 `JsonSchemaFactory.getInstance(VersionFlag.V202012)` 校验 schema 自身合规 + schema 可校验任意 params 实例。
- [ ] 实现 `ToolRegistryImpl.java`：
  - `register(RegisterToolCommand)`：
    1. 校验 schema 合规（ToolSchemaValidator）。
    2. 校验 name 在 ownerTenantId 下唯一。
    3. 风险等级可由调用方提供，否则由 RiskClassifier 计算覆盖（依赖注入 RiskClassifier，T4 完成后接入；T3 阶段先用声明的 riskLevel）。
    4. 构造 ToolMeta（toolId=UUID / enabled=true / version="1.0.0"）。
    5. `repository.save(meta)` → 返回 toolId。
  - `update(toolId, UpdateToolCommand)`：保留原 toolId，version bump（按 semver minor +1 或 patch +1，简化为 "1.0.{patch+1}"）。
  - `enable(toolId)` / `disable(toolId)`：单字段 update。
  - `findById(toolId)`：Optional<ToolMeta>，notFound 时抛 `ToolNotFoundException`。
  - `listByTenant(tenantId, includeDisabled)`：查询并返回 List。
  - `getSchema(toolId)`：返回 ToolSchema POJO。
- [ ] 注入 `ToolMetaRepository` + `ToolSchemaValidator`。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 拆 `register` 为 `validate` / `buildMeta` / `persist` 三步。
- [ ] 校验失败统一抛 `ToolValidationException` 含字段名 + 错误信息。

**Commit：** `feat(agent-tool-engine): T3 ToolRegistryImpl register/enable/list with schema validation`

---

### Task T4: RiskClassifierImpl 风险分类

**目标：** 实现 `RiskClassifier` 接口：基于 sideEffect + params + 上下文给出 R1/R2/R3 + 是否需要审批。

**Red：**
- [ ] 在 `RiskClassifierImplTest.java` 写：
  - `classify_readOnly_returnsR1` SideEffect=READ_ONLY → R1。
  - `classify_writeLocal_returnsR2` WRITE_LOCAL → R2。
  - `classify_writeExternal_returnsR3` WRITE_EXTERNAL → R3。
  - `classify_destructive_returnsR3` DESTRUCTIVE → R3。
  - `classify_r3WithPii_requiresApproval` R3 + 参数含手机号/邮箱 → 强制审批（即使工具声明 riskLevel=R2）。
  - `classify_overridesToolDeclaredLevel_whenHigher` classifier 计算更高级别时覆盖工具声明。
  - `classify_neverDowngrades` classifier 不会把声明的 R3 降到 R2。
  - `classify_approvalRequired_r1_false` R1 → false。
  - `classify_approvalRequired_r3_true` R3 → true。
  - `classify_approvalRequired_r2_recentApproval_skips` R2 + 同租户 1h 内同 toolId + 同 paramsHash 已批过 → false。

**Green：**
- [ ] 新建 `RiskRule.java`：规则模型（condition + action）。
- [ ] 实现 `RiskClassifierImpl.java`：
  - `classify(ToolMeta meta, ToolCallRequest request)`：
    1. base level = meta.sideEffect 映射（NONE/READ_ONLY→R1 / WRITE_LOCAL→R2 / WRITE_EXTERNAL,DESTRUCTIVE→R3）。
    2. 取 max(base, meta.riskLevel)（不降级原则）。
    3. PII 检测：扫描 params JSON，命中正则 → 强制 R3。
    4. 涉及 external network（HTTP_API 且 host 非 allowlist）→ 强制 R3。
    5. 返回 `RiskAssessment(level, requiresApproval, reason)`。
  - `requiresApproval(RiskAssessment)`：
    - R1 → false。
    - R2 → true 默认，但若 `approvalStore.findRecentApproved(tenantId, toolId, paramsHash, Duration.ofHours(1))` 命中 → false。
    - R3 → true 始终。
- [ ] 注入 `ApprovalRecordRepository`。
- [ ] PII 正则用 `Pattern` 常量缓存（手机号 / 邮箱 / 身份证 / API key）。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 抽 `PiiDetector` 单独类。
- [ ] 抽 `HostAllowlistChecker` 单独类（HTTP_API host 校验）。

**Commit：** `feat(agent-tool-engine): T4 RiskClassifierImpl R1/R2/R3 classification with PII boost`

---

### Task T5: ApprovalStoreImpl 人工审批

**目标：** 实现 `ApprovalStore` 接口：提交审批请求 + 阻塞等待结果 + 超时处理。

**Red：**
- [ ] 在 `ApprovalStoreImplTest.java` 写（jedis-mock + CompletableFuture）：
  - `submit_persistsRecord` 提交审批，断言 DB 落 PENDING 记录。
  - `submit_returnsCallId` 返回 callId。
  - `await_returnsApproved_whenApprovedBeforeTimeout` 在另一线程模拟 approve → await 在超时前返回 APPROVED。
  - `await_returnsTimeout_whenExceedsSla` 无 approve → await 在 SLA 后返回 TIMEOUT。
  - `await_returnsRejected_whenRejected` 模拟 reject → 返回 REJECTED。
  - `approve_updatesRecordAndNotifies` 调用 `approve(callId, approverId, reason)` → DB 更新 + 通过 Redis pub/sub 通知等待方。
  - `approve_idempotent` 重复 approve 同一 callId 抛 `ToolApprovalException`（已决策）。
  - `findPendingByApprover` 按 approver 查待审批列表。
  - `cleanupExpiredRecords` 超过 24h 的 PENDING 标记 TIMEOUT。

**Green：**
- [ ] 新建 `ApprovalWaiter.java`：用 `ConcurrentHashMap<String, CompletableFuture<ApprovalDecision>>` + Redis pub/sub 订阅 `tool:approval:{callId}` channel。
- [ ] 实现 `ApprovalStoreImpl.java`：
  - `submit(ApprovalRequest)`：构造 ApprovalRecord（status=PENDING / expireAt=now+SLA）→ save → 返回 callId。
  - `await(callId, Duration timeout)`：
    1. 创建 CompletableFuture（若不存在）。
    2. `future.get(timeout.toMillis(), MS)`。
    3. 超时 → 调 `markTimeout(callId)` → 返回 `Decision.TIMEOUT`。
    4. 异常 → 返回对应 Decision。
  - `approve(callId, approverId, reason)`：
    1. 查 record，若 status != PENDING → 抛 `ToolApprovalException("already decided")`。
    2. update status=APPROVED / approverId / decidedAt=now / reason。
    3. 通过 Redis publish `tool:approval:{callId}` → APPROVED。
  - `reject(callId, approverId, reason)`：同上但 status=REJECTED。
  - `findPendingByApprover(approverId)`：Repository 查询。
  - `cleanupExpired()`（@Scheduled fixedDelay=5min）：扫描 PENDING 且 expireAt < now → 标记 TIMEOUT + publish。
- [ ] 注入 `ApprovalRecordRepository` + `StringRedisTemplate`（pub/sub）+ `ApprovalWaiter`。
- [ ] 异常：审批已决策 → `ToolApprovalException`；SLA 超时 → `ToolApprovalTimeoutException`（继承 ToolApprovalException）。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] CompletableFuture 注册用 `computeIfAbsent` 防并发。
- [ ] Redis pub/sub listener 用 `MessageListenerAdapter` + 单例订阅 pattern `tool:approval:*`。

**Commit：** `feat(agent-tool-engine): T5 ApprovalStoreImpl submit/await/approve with redis pub/sub`

---

### Task T6: SandboxBorrowerImpl Docker 沙箱

**目标：** 实现 `SandboxBorrower` 接口：Docker 沙箱池预热 + 借用 + 执行 + 销毁。

**Red：**
- [ ] 在 `SandboxBorrowerImplTest.java` 写（用 `@MockBean DockerClient` mock，或 Testcontainers）：
  - `borrow_returnsSandboxInstance` 借用一个沙箱，断言返回非空 containerId。
  - `borrow_warmPool_reusesContainer` 池中已有空闲容器 → 复用。
  - `borrow_coldStart_createsNewContainer` 池空 → 创建新容器。
  - `borrow_blocks_whenPoolExhausted` 池满（maxConcurrent）→ 阻塞等待，超时抛 `ToolSandboxFailureException`。
  - `exec_runsCommand_returnsStdout` exec "echo hello" → 返回 stdout="hello\n"。
  - `exec_timesOut_killsContainer` 超过 execTimeoutMs → 杀容器 + 抛 `ToolExecutionTimeoutException`。
  - `return_releasesToPool_orDestroys` 空闲 < poolSize → 还池；否则销毁。
  - `return_destroysIfExpired` 容器空闲超 10min → 销毁。
  - `cleanup_destroysIdleExpired` @Scheduled 扫描销毁过期空闲容器。
  - `exec_capturesStderrAndExitCode` 返回 stderr + exitCode。

**Green：**
- [ ] 新建 `SandboxSpec.java`：image / cpu / memory / network（none/bridge）/ tmpfs / mounts / env / timeoutMs。
- [ ] 新建 `SandboxPool.java`：`ConcurrentLinkedDeque<SandboxInstance>` + `Semaphore(maxConcurrent)`。
- [ ] 实现 `SandboxBorrowerImpl.java`：
  - `init()`（@PostConstruct）：预热 poolSize 个容器（异步创建）。
  - `borrow(SandboxSpec spec)`：
    1. `semaphore.tryAcquire(borrowTimeout)` → 失败抛 `ToolSandboxFailureException("pool exhausted")`。
    2. 优先从 pool 取匹配 spec 的空闲容器（若 spec 一致）。
    3. 池空 → 创建新容器：
       - `CreateContainerCmd` 设置 image / HostConfig（cpu / memory / tmpfs / binds / networkMode=none）。
       - `startContainerCmd` 启动 → 等待 ready（健康检查 ping）。
    4. 返回 `SandboxInstance(containerId, createdAt, spec)`。
  - `exec(SandboxInstance, String[] command, Map<String,String> env, long timeoutMs)`：
    1. `execCreateCmd` + `execStartCmd` + `logFollowing` 异步读 stdout/stderr。
    2. `CompletableFuture.get(timeoutMs)` → 超时则 `killContainerCmd` → 抛 `ToolExecutionTimeoutException`。
    3. 返回 `SandboxExecResult(stdout, stderr, exitCode, durationMs)`。
  - `release(SandboxInstance)`：
    1. 若空闲数 < poolSize 且 spec 匹配 → 还池 + 更新 lastUsedAt。
    2. 否则 `removeContainerCmd(force=true)` 销毁。
  - `cleanupExpired()`（@Scheduled fixedDelay=1min）：扫描 pool 中 lastUsedAt 早于 idleTimeoutMs 的 → 销毁。
  - `destroy()`（@PreDestroy）：销毁所有容器。
- [ ] 注入 `DockerClient` + `ToolEngineProperties.Sandbox`。
- [ ] 异常：Docker 异常 → `ToolSandboxFailureException`；执行超时 → `ToolExecutionTimeoutException`。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 抽 `SandboxFactory`（封装容器创建）。
- [ ] spec 匹配比较抽 `SandboxSpecMatcher`。

**Commit：** `feat(agent-tool-engine): T6 SandboxBorrowerImpl docker pool + exec + cleanup`

---

### Task T7: ToolCacheImpl Redis 缓存

**目标：** 实现 `ToolCache` 接口：Redis 一级 + Caffeine 二级缓存。

**Red：**
- [ ] 在 `ToolCacheImplTest.java` 写（jedis-mock + Caffeine）：
  - `put_writesToRedis` 写入 → Redis 有值。
  - `get_hit_returnsResult` 写入后读 → 命中。
  - `get_miss_returnsEmpty` 未写入 → Optional.empty()。
  - `get_expired_returnsEmpty` TTL 过期后 → empty（用短 TTL + Awaitility）。
  - `get_hitPopulatesCaffeine` Redis 命中后写入 Caffeine，第二次 get 走 Caffeine（断言 Redis 调用次数不变）。
  - `invalidate_byToolId` 删除某 toolId 全部缓存（scan + del）。
  - `invalidate_byToolIdAndTenant` 删除指定 toolId + tenantId 缓存。
  - `cacheKey_isDeterministic` 相同 params → 相同 key。
  - `put_respectsMaxEntries` 超过 maxEntries 时 Caffeine LRU 驱逐。

**Green：**
- [ ] 新建 `CacheKeyBuilder.java`：`build(toolId, paramsHash, tenantId)` → `tool:cache:{toolId}:{paramsHash}:{tenantId}`。
- [ ] 新建 `ParamsHasher.java`：`hash(Map<String,Object> params)` → SHA-256(JSON-canonicalized(params))。
- [ ] 实现 `ToolCacheImpl.java`：
  - `get(toolId, paramsHash, tenantId)`：
    1. 先查 Caffeine（同步）→ 命中返回。
    2. 再查 Redis（`opsForValue().get(key)`）→ 命中则回填 Caffeine → 返回。
    3. 未命中返回 Optional.empty()。
  - `put(toolId, paramsHash, tenantId, ToolCallResult result, Duration ttl)`：
    1. Redis `set(key, json, ttl)`。
    2. Caffeine `put(key, result)`。
  - `invalidate(toolId)`：Redis `scan` pattern `tool:cache:{toolId}:*` + 批量 del；Caffeine 也清。
  - `invalidate(toolId, tenantId)`：同上带 tenant。
- [ ] 注入 `RedisTemplate<String, Object>` + `ToolEngineProperties.Cache` + 自建 `CaffeineCacheManager`（maxEntries + expireAfterAccess）。
- [ ] 异常：Redis 异常 → 降级只用 Caffeine（log warning），不抛。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 把 Caffeine cache 抽 `LocalTierCache` 单独类。
- [ ] Redis scan 用 `RedisConnection.scan(ScanOptions)` 而非 keys（性能）。

**Commit：** `feat(agent-tool-engine): T7 ToolCacheImpl redis + caffeine two-tier cache`

---

### Task T8: ToolGatewayImpl 网关编排 + Executor 实现

**目标：** 实现 `ToolGateway` 接口：编排 RiskClassifier → ApprovalStore → RateLimiter → ToolCache → Sandbox/Executor → ResultCleaner → ToolCallAuditor 全链路。

**Red：**
- [ ] 在 `ToolGatewayImplTest.java` 写（@MockBean 各依赖）：
  - `call_r1_readOnly_callsExecutor_returnsResult` R1 + READ_ONLY 全流程：直接执行 → 返回结果。
  - `call_r1_cacheHit_skipsExecutor` 缓存命中 → 不调用 executor。
  - `call_r2_noRecentApproval_blocksUntilApproved` R2 + 无近期审批 → 进入 APPROVING → mock approve → 执行。
  - `call_r3_skipsCache_callsExecutorInSandbox` R3 + DESTRUCTIVE → 不走缓存 → 走沙箱执行。
  - `call_rateLimited_throwsQuotaExhausted` 限流触发 → 抛 `ToolQuotaExhaustedException`。
  - `call_toolDisabled_throws` 工具 enabled=false → 抛 `ToolDisabledException`。
  - `call_validationFailed_throws` params 不符合 schema → 抛 `ToolValidationException`。
  - `call_executorFailed_recordsAuditAndThrows` 执行失败 → 审计落库 + 抛 `ToolEngineException`。
  - `call_executorTimeout_killsSandbox_recordsTimeout` 超时 → 杀沙箱 + 审计 status=TIMEOUT。
  - `call_writesAudit_always` 无论成败都落审计（verify audit.save called）。
  - `call_appliesResultCleaner` 结果经过清洗（截断 + 脱敏）。
  - `call_populatesCacheOnSuccess` 成功后写缓存（若可缓存）。
  - `call_includesRecallerHints` 调用前先调 ToolSemanticRecaller 拿历史 hint（注入 prompt）。

**Green：**
- [ ] 新建 `HttpExecutor.java`：HTTP_API 类型，用 WebClient POST/GET，超时 30s，返回 stdout=response body。
- [ ] 新建 `ShellExecutor.java`：SHELL 类型，borrow 沙箱 → exec 命令 → 返回 stdout/stderr/exitCode。
- [ ] 新建 `PythonExecutor.java`：PYTHON 类型，borrow 沙箱 → 写入 script.py 文件 → exec `python script.py` → 返回 stdout。
- [ ] 新建 `McpExecutor.java`：MCP 类型，占位实现（接口预留，T12 完成）。
- [ ] 新建 `RateLimiter.java`：Redis 令牌桶（Lua 脚本原子取令牌）。
- [ ] 实现 `ToolGatewayImpl.java`：
  - `call(ToolCallRequest request)`：
    1. **校验阶段**：参数 schema 校验（ToolSchemaValidator）→ 失败抛 ToolValidationException。
    2. **加载工具**：`registry.findById(toolId)` → enabled=false 抛 ToolDisabledException。
    3. **限流**：`rateLimiter.tryAcquire(tenantId, toolId)` → 失败抛 ToolQuotaExhaustedException。
    4. **风险分类**：`riskClassifier.classify(meta, request)` → RiskAssessment。
    5. **召回 hint**：`recaller.recall(tenantId, toolId, params)` → 历史调用经验（注入到执行 prompt，可选）。
    6. **审批**：若 requiresApproval → `approvalStore.submit + await` → REJECTED/TIMEOUT 抛异常。
    7. **缓存读**：若 cacheable + R1 + READ_ONLY → `cache.get(...)` → 命中直接返回（cachehit=true）。
    8. **执行**：
       - HTTP_API → HttpExecutor。
       - SHELL → SandboxBorrower + ShellExecutor。
       - PYTHON → SandboxBorrower + PythonExecutor。
       - MCP → McpExecutor。
       - 包 try/catch + timeout。
    9. **清洗**：`resultCleaner.clean(rawResult)` → 截断 + 脱敏。
    10. **审计**：`auditor.record(callId, ...)` 同步落库（status=SUCCESS/FAILED/TIMEOUT）。
    11. **缓存写**：若 cacheable + 成功 → `cache.put(...)`。
    12. 返回 `ToolCallResult(callId, status, stdout, stderr, exitCode, durationMs, cachehit, auditId)`。
- [ ] 注入全部依赖（Registry / Classifier / ApprovalStore / SandboxBorrower / Cache / Auditor / Recaller / Cleaner / RateLimiter / 4 个 Executor）。
- [ ] 异常映射：每种失败对应特定异常（doc §12.4 错误码）。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 把 `call()` 拆为 `validate` / `loadTool` / `acquirePermit` / `assessRisk` / `awaitApproval` / `tryCache` / `execute` / `clean` / `audit` / `cacheWrite` 10 个私有方法。
- [ ] 加 `@Transactional`（仅审计落库部分），执行部分不进事务。
- [ ] 加 Micrometer timer：`tool.call.duration{toolId, status}`。

**Commit：** `feat(agent-tool-engine): T8 ToolGatewayImpl orchestrates risk/approval/cache/sandbox/audit`

---

### Task T9: ToolCallAuditorImpl 审计

**目标：** 实现 `ToolCallAuditor` 接口：同步落库审计日志 + 查询接口。

**Red：**
- [ ] 在 `ToolCallAuditorImplTest.java` 写：
  - `record_success_persistsAllFields` 调用 record(status=SUCCESS) → DB 落库，断言全部 16 个字段对。
  - `record_failed_persistsErrorMessage` status=FAILED → errorMessage 落库。
  - `record_timeout_persistsStatus` status=TIMEOUT → 落库。
  - `record_isTransactional_failureRollsBack` 审计与业务在同一事务 → 业务失败时审计也回滚（验证：审计一定在业务提交后单独事务写，本测试验证 NOT rollback）。
  - `record_async_returnsImmediately` record 方法不阻塞（同步落库但快速返回）。
  - `findByCallId_returnsRecord` 查询。
  - `findByTenantIdAndTimeRange` 按租户 + 时间区间分页查询。
  - `findByTenantIdAndToolId` 按租户 + 工具查询。
  - `countByStatus` 统计各状态数量。

**Green：**
- [ ] 新建 `AuditContextBuilder.java`：从 ToolCallRequest + RiskAssessment + SandboxInstance + ToolCallResult 构造 `ToolCallAuditLog` POJO。
- [ ] 实现 `ToolCallAuditorImpl.java`：
  - `record(ToolCallRequest request, RiskAssessment assessment, SandboxInstance sandbox, ToolCallResult result, ApprovalRecord approval)`：
    1. 构造 `ToolCallAuditLog`：call_id / tenant_id / agent_id / tool_id / params_hash / status / risk_level / started_at / ended_at / duration_ms / cost_tokens / exit_code / error_message / sandbox_container_id / approver_id / cache_hit。
    2. `repository.save(log)`（同步事务，`@Transactional(propagation=REQUIRES_NEW)` 确保独立提交）。
    3. 返回 auditLogId。
  - `findByCallId(callId)`：Optional<ToolCallAuditLog>。
  - `findByTenantIdAndTimeRange(tenantId, Instant from, Instant to, Pageable)`：分页查询。
  - `findByTenantIdAndToolId(tenantId, toolId, Pageable)`：分页查询。
  - `countByStatus(tenantId, ToolCallStatus)`：count 查询。
- [ ] 注入 `ToolCallAuditLogRepository`。
- [ ] 异常处理：审计落库失败必须 log error 但不影响主流程（catch + log）。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 抽 `AuditLogMapper`（DTO ↔ Entity）。
- [ ] `@Transactional(REQUIRES_NEW)` 确保审计独立提交（即使主业务回滚，审计仍可见）。

**Commit：** `feat(agent-tool-engine): T9 ToolCallAuditorImpl sync audit log persistence`

---

### Task T10: ToolSemanticRecallerImpl 语义召回

**目标：** 实现 `ToolSemanticRecaller` 接口：调用 agent-memory RecallMemory 拿历史相似工具调用经验，失败降级到关键词匹配。

**Red：**
- [ ] 在 `ToolSemanticRecallerImplTest.java` 写（mock gRPC stub）：
  - `recall_returnsMemoryHits_whenMemoryServiceUp` mock memory 返回 3 条 PROCEDURAL 记忆 → 返回 List<ToolRecallResult> size=3。
  - `recall_mapsMemoryToRecallResult` 字段映射正确（content / sourceTaskId / importance / score）。
  - `recall_filtersByImportanceBelowThreshold` importance < 0.4 过滤。
  - `recall_sortsByScoreDesc` 按 score 降序。
  - `recall_topK_default3` 默认 topK=3。
  - `recall_fallsBackToKeywordMatch_whenMemoryServiceDown` gRPC 抛 UNAVAILABLE → 降级 KeywordFallbackRecaller。
  - `recall_fallsBackToKeywordMatch_whenMemoryServiceTimesOut` 超时 → 降级。
  - `recall_returnsEmpty_whenNoHits` 查无 → 返回空列表（不抛异常）。

**Green：**
- [ ] 新建 `MemoryServiceClient.java`：封装 `MemoryServiceBlockingStub`，调用 `recallMemory(RecallMemoryRequest)`，超时 2s。
- [ ] 新建 `KeywordFallbackRecaller.java`：用 ToolMeta.description + params 关键词在 ToolCallAuditLog 中模糊匹配（`LIKE %keyword%`）取 topK=3。
- [ ] 实现 `ToolSemanticRecallerImpl.java`：
  - `recall(String tenantId, String toolId, Map<String,Object> params, int topK)`：
    1. 构造查询 query：toolId + description 关键词 + params JSON 序列化。
    2. 调用 `memoryServiceClient.recallMemory(tenantId, query, topK, "PROCEDURAL")`。
    3. 成功 → 过滤 importance < 0.4 → 按 score 降序 → 取 topK → 映射为 ToolRecallResult。
    4. 失败（UNAVAILABLE / DEADLINE_EXCEEDED）→ log warning + 调 `keywordFallbackRecaller.recall(...)`。
  - `recallDefault3(tenantId, toolId, params)`：默认 topK=3。
- [ ] 注入 `MemoryServiceClient` + `KeywordFallbackRecaller`。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 抽 `RecallQueryBuilder` 构造查询文本。
- [ ] 加 Caffeine 短期缓存（key=tenantId+toolId+paramsHash，TTL=60s）避免重复召回。

**Commit：** `feat(agent-tool-engine): T10 ToolSemanticRecallerImpl memory recall with keyword fallback`

---

### Task T11: ResultCleanerImpl 结果清洗

**目标：** 实现 `ResultCleaner` 接口：截断 + 脱敏 + ANSI 清除 + whitespace 修剪。

**Red：**
- [ ] 在 `ResultCleanerImplTest.java` 写：
  - `clean_truncatesOverLong` 输入 10KB → 截断到 8KB + 末尾追加 `...[truncated 2048 bytes]`。
  - `clean_redactsPhone` "联系 13800138000" → "联系 1**********"。
  - `clean_redactsEmail` "user@example.com" → "***@***"。
  - `clean_redactsApiKey` "sk-abc123...(48 chars)" → "sk-****"。
  - `clean_redactsIdCard` "110101199001011234" → "******************"。
  - `clean_stripsAnsi` "hello\x1B[31mworld\x1B[0m" → "helloworld"。
  - `clean_trimsTrailingWhitespace` "hello   \n" → "hello"。
  - `clean_preservesNewlines` 多行内容保留换行。
  - `clean_appliesAllInOrder` 同时含 ANSI + PII + 超长 → 按顺序：strip ANSI → redact PII → truncate → trim。
  - `clean_emptyInput_returnsEmpty` 边界 case。
  - `clean_configurableMaxBytes` 改配置 maxBytes=1024 → 截断到 1024。

**Green：**
- [ ] 新建 `PiiRedactor.java`：
  - 正则常量：PHONE `1[3-9]\d{9}` / EMAIL `[\w.+-]+@[\w-]+\.[\w.-]+` / API_KEY `sk-[A-Za-z0-9]{40,}` / ID_CARD `\d{17}[\dXx]`。
  - `redact(String text)`：依次替换为掩码。
- [ ] 新建 `AnsiStripper.java`：正则 `\x1B\[[0-9;]*[A-Za-z]` 替换为空。
- [ ] 实现 `ResultCleanerImpl.java`：
  - `clean(String raw)`：
    1. `ansiStripper.strip(raw)` → 去 ANSI。
    2. `piiRedactor.redact(stripped)` → 脱敏。
    3. `truncate(redacted, maxBytes)` → 截断（按 UTF-8 字节，避免截断中文字符半截，末尾追加 `...[truncated N bytes]`）。
    4. `trim()` → 修剪 trailing whitespace。
    5. 返回 `CleanedResult(content, originalBytes, truncatedBytes, redactionCount)`。
  - `cleanBatch(List<String>)`：批量清洗。
- [ ] 注入 `ToolEngineProperties.Cleaner`。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 截断逻辑抽 `ByteTruncator` 工具类（按字符边界截断）。
- [ ] PII 正则常量外部化到 `tool.cleaner.pii.patterns`（可选）。

**Commit：** `feat(agent-tool-engine): T11 ResultCleanerImpl truncate + redact + ansi strip`

---

### Task T12: ToolEngine gRPC 服务 + 集成测试

**目标：** 实现 ToolEngine gRPC 4 RPC + 端到端集成测试。

**Red：**
- [ ] 在 `ToolEngineGrpcImplTest.java`（@SpringBootTest + gRPC in-process server + 全依赖 mock 或真实 H2）写：
  - `callTool_r1_readOnly_returnsResult` 调用 CallTool RPC（mock executor 返回）→ 断言 status=SUCCESS / stdout 非空。
  - `callTool_r1_cacheHit_returnsCached` 第二次相同请求 → cachehit=true。
  - `callTool_r3_requiresApproval_returnsForbidden` R3 工具未审批 → gRPC Status PERMISSION_DENIED。
  - `callTool_invalidParams_returnsInvalidArgument` params 不符合 schema → INVALID_ARGUMENT。
  - `callTool_toolNotFound_returnsNotFound` toolId 不存在 → NOT_FOUND。
  - `callTool_toolDisabled_returnsPermissionDenied` enabled=false → PERMISSION_DENIED。
  - `callTool_quotaExhausted_returnsResourceExhausted` 限流触发 → RESOURCE_EXHAUSTED。
  - `registerTool_new_returnsToolId` 注册新工具 → 返回 toolId。
  - `registerTool_duplicateName_returnsAlreadyExists` 重名 → ALREADY_EXISTS。
  - `listTools_returnsEnabledOnly` 默认只返回 enabled。
  - `listTools_includeDisabled` 包含 disabled。
  - `getToolMeta_returnsMeta` GetToolMeta 返回 ToolMeta proto。
  - `getToolMeta_notFound_returnsNotFound` → NOT_FOUND。
  - `callTool_writesAuditLog` 验证审计落库（verify auditRepository.save called）。
  - `callTool_appliesResultCleaner` 验证 stdout 经过脱敏（输出含 `***@***`）。

**Green：**
- [ ] 新建 `ToolCallMapper.java`：proto `ToolCallRequest` / `ToolCallResult` / `ToolMeta` ↔ JPA Entity + DTO 双向映射。
- [ ] 新建 `GrpcExceptionAdvice.java`（gRPC interceptor 或 @ControllerAdvice 风格）：
  - `ToolNotFoundException` → NOT_FOUND
  - `ToolDisabledException` → PERMISSION_DENIED
  - `ToolValidationException` → INVALID_ARGUMENT
  - `ToolApprovalRequiredException` → PERMISSION_DENIED（含 metadata: approval_call_id）
  - `ToolApprovalTimeoutException` → DEADLINE_EXCEEDED
  - `ToolQuotaExhaustedException` → RESOURCE_EXHAUSTED
  - `ToolSandboxFailureException` → INTERNAL
  - `ToolExecutionTimeoutException` → DEADLINE_EXCEEDED
  - `ToolEngineException`（基类）→ INTERNAL with code
- [ ] 实现 `ToolEngineGrpcImpl.java`（`@GrpcService extends ToolEngineGrpc.ToolEngineImplBase`）：
  - `callTool(CallToolRequest, StreamObserver<CallToolResponse>)`：
    1. 用 ToolCallMapper 转 `ToolCallRequest` DTO。
    2. 调 `toolGateway.call(...)` → `ToolCallResult`。
    3. 映射为 proto `CallToolResponse` → `onNext` + `onCompleted`。
    4. 异常由 advice 拦截。
  - `registerTool(RegisterToolRequest, StreamObserver<RegisterToolResponse>)`：
    1. 转 `RegisterToolCommand` → `toolRegistry.register(...)` → toolId。
    2. 返回 `RegisterToolResponse{toolId, version}`。
  - `listTools(ListToolsRequest, StreamObserver<ListToolsResponse>)`：
    1. 取 tenantId + includeDisabled flag。
    2. `toolRegistry.listByTenant(...)` → List<ToolMeta>。
    3. 映射为 proto 列表 → response。
  - `getToolMeta(GetToolMetaRequest, StreamObserver<GetToolMetaResponse>)`：
    1. `toolRegistry.findById(toolId)` → Optional<ToolMeta>。
    2. notFound → 抛 ToolNotFoundException（由 advice 转 NOT_FOUND）。
    3. 映射为 proto → response。
- [ ] 运行全部 15 个测试，全绿。

**Refactor：**
- [ ] 把 `callTool` 拆 `unmarshal` / `delegate` / `marshal` 三步。
- [ ] 校验抽 `ToolCallRequestValidator`。
- [ ] Mapper 用 MapStruct（可选，简化样板）。

**Commit：** `feat(agent-tool-engine): T12 ToolEngine gRPC 4 RPC + integration tests`

---

## 测试矩阵

### 单元测试（UT-TOOL-001~020，参照 `docs/tests/unit-test-cases.md` §8）

| 用例 ID | 模块 | 测试方法 | 关键断言 |
|---|---|---|---|
| UT-TOOL-001 | T2 | `ToolMetaRepositoryTest.findByOwnerTenantIdAndEnabledTrue` | 租户 + 启用过滤 |
| UT-TOOL-002 | T3 | `ToolRegistryImplTest.register_newTool_persists` | 注册返回 toolId + 落库 |
| UT-TOOL-003 | T3 | `ToolRegistryImplTest.register_invalidSchema_throws` | schema 不合规拒绝 |
| UT-TOOL-004 | T4 | `RiskClassifierImplTest.classify_destructive_returnsR3` | DESTRUCTIVE → R3 |
| UT-TOOL-005 | T4 | `RiskClassifierImplTest.classify_r3WithPii_requiresApproval` | PII 强制 R3 + 审批 |
| UT-TOOL-006 | T4 | `RiskClassifierImplTest.classify_neverDowngrades` | 不降级原则 |
| UT-TOOL-007 | T5 | `ApprovalStoreImplTest.await_returnsApproved_whenApprovedBeforeTimeout` | 审批通过 |
| UT-TOOL-008 | T5 | `ApprovalStoreImplTest.await_returnsTimeout_whenExceedsSla` | 审批超时 |
| UT-TOOL-009 | T6 | `SandboxBorrowerImplTest.exec_timesOut_killsContainer` | 沙箱超时杀容器 |
| UT-TOOL-010 | T6 | `SandboxBorrowerImplTest.borrow_blocks_whenPoolExhausted` | 池满阻塞 |
| UT-TOOL-011 | T7 | `ToolCacheImplTest.get_hitPopulatesCaffeine` | 二级缓存回填 |
| UT-TOOL-012 | T7 | `ToolCacheImplTest.invalidate_byToolId` | 工具级失效 |
| UT-TOOL-013 | T8 | `ToolGatewayImplTest.call_r1_cacheHit_skipsExecutor` | 缓存命中跳执行 |
| UT-TOOL-014 | T8 | `ToolGatewayImplTest.call_r3_requiresApproval` | R3 强制审批 |
| UT-TOOL-015 | T8 | `ToolGatewayImplTest.call_writesAudit_always` | 审计必落库 |
| UT-TOOL-016 | T9 | `ToolCallAuditorImplTest.record_success_persistsAllFields` | 16 字段完整 |
| UT-TOOL-017 | T10 | `ToolSemanticRecallerImplTest.recall_fallsBackToKeywordMatch_whenMemoryServiceDown` | memory 不可用降级 |
| UT-TOOL-018 | T11 | `ResultCleanerImplTest.clean_redactsPhone` | 手机号脱敏 |
| UT-TOOL-019 | T11 | `ResultCleanerImplTest.clean_truncatesOverLong` | 超长截断 |
| UT-TOOL-020 | T12 | `ToolEngineGrpcImplTest.callTool_r1_readOnly_returnsResult` | gRPC 端到端 |

### 集成测试（IT-TOOL-001~006）

| 用例 ID | Task | 测试场景 | 依赖 |
|---|---|---|---|
| IT-TOOL-001 | T12 | RegisterTool → CallTool → ListTools → GetToolMeta 全流程 | H2 + Mock executor |
| IT-TOOL-002 | T12 | R3 工具 CallTool → ApprovalStore → Approve → 执行 | H2 + Mock sandbox + Mock executor |
| IT-TOOL-003 | T7 | 连续 100 次相同 CallTool → 第 2 次起 cachehit=true | H2 + jedis-mock |
| IT-TOOL-004 | T6 | SHELL 工具在沙箱执行 `echo $TEST` 验证 env 注入 | Testcontainers Docker（可选） |
| IT-TOOL-005 | T8 | Rate limiter：1 秒内 11 次调用 → 第 11 次 RESOURCE_EXHAUSTED | jedis-mock |
| IT-TOOL-006 | T9 | CallTool 失败 → 审计 status=FAILED + errorMessage 落库 | H2 |

### 端到端测试（E2E-TOOL-001~003，可选 Testcontainers）

| 用例 ID | 场景 | 依赖 |
|---|---|---|
| E2E-TOOL-001 | Testcontainers MySQL + Redis + Docker → 真实 SHELL 工具调用全链路 | Docker |
| E2E-TOOL-002 | WireMock 模拟外部 HTTP 工具 → HTTP_API 全链路 + 限流 + 缓存 | WireMock + Docker |
| E2E-TOOL-003 | agent-memory mock（gRPC in-process）→ RecallMemory hint 注入流程 | gRPC in-process |

---

## 验收标准

### 1. 代码质量
- [ ] JaCoCo 行覆盖 ≥80%
- [ ] JaCoCo 分支覆盖 ≥70%
- [ ] 关键类（`ToolGatewayImpl` / `SandboxBorrowerImpl` / `RiskClassifierImpl` / `ToolEngineGrpcImpl`）行覆盖 ≥85%
- [ ] Checkstyle / SpotBugs 无 ERROR
- [ ] 无 `TODO` / `FIXME` 残留（除明确登记）

### 2. 功能完整性
- [ ] 4 RPC（CallTool / RegisterTool / ListTools / GetToolMeta）全部实现并单测覆盖
- [ ] 9 API 实现（Registry / Gateway / Classifier / ApprovalStore / SandboxBorrower / Cache / Auditor / Recaller / Cleaner）全部覆盖
- [ ] 4 种 Executor（HTTP / Shell / Python / MCP）实现（MCP 可占位 + 接口完备）
- [ ] RiskClassifier 3 级 + PII 升级 + 不降级原则
- [ ] ApprovalStore pub/sub + 超时 + 幂等
- [ ] ToolCache Redis + Caffeine 两级
- [ ] ToolGateway 编排链路完整（10 步）
- [ ] 错误码全部对齐 doc 05 §12.4

### 3. 性能基线
- [ ] CallTool R1 + cache miss P99 < 500ms（不含外部工具耗时）
- [ ] CallTool R1 + cache hit P99 < 50ms
- [ ] ListTools 100 个工具 P99 < 100ms
- [ ] SandboxBorrower.borrow 冷启动 P99 < 5s（Docker 本地）
- [ ] SandboxBorrower.borrow 池热 P99 < 100ms

### 4. 文档对齐
- [ ] doc 05-tool-engine §3-§13 所有锁定值在代码中可追溯
- [ ] `docs/tests/unit-test-cases.md` §8 UT-TOOL-001~020 全绿
- [ ] ADR-003（Docker vs Firecracker）选择已落地
- [ ] v5 审核 §6 P6-6 整改项关闭

### 5. 可观测性
- [ ] Micrometer 指标：`tool.call.count{toolId, status}` / `tool.call.duration{toolId}` / `tool.cache.hit{toolId}` / `tool.sandbox.borrow.duration` / `tool.approval.await.duration{riskLevel}`
- [ ] Structured Logging（tenantId / callId / toolId 全链路）
- [ ] Actuator `/actuator/health` 包含 Redis + Docker 健康检查

---

## Self-review checklist

执行计划前请确认：

- [ ] 已读 doc 05-tool-engine 全文（§1-§13）+ doc 01-database §2.2（tool_meta / tool_call_audit_log / tool_approval 表）+ doc 00-overview §3.1（端口）
- [ ] 已读 `agent-proto/src/main/proto/tool.proto`，4 RPC 请求/响应字段已确认
- [ ] 已读 `agent-common` 的 `ToolRiskLevel` / `ToolCallStatus` / `ErrorCode` / `BusinessException` 与本模块枚举一致
- [ ] docker-java 版本（3.3.0）与运行环境 Docker daemon 兼容
- [ ] Redis 版本（≥6.0）支持 scan + pub/sub + Lua 脚本（RateLimiter 用）
- [ ] agent-memory 服务（Plan 03）已提供 RecallMemory RPC，与 T10 MemoryServiceClient 对齐
- [ ] 与 Plan 04 task-orchestrator 协调：orchestrator 不直接调工具，通过 agent-runtime 调；确保 ToolEngine 在 orchestrator 启动前/后均可启动（无强依赖）
- [ ] 与 Plan 06 agent-runtime 协调：runtime 在 ReAct Act 阶段调 `ToolEngine.CallTool`，请求体字段已对齐（agentId / taskId / toolId / params）
- [ ] T2 实体字段顺序与 DDL（doc 01-database §2.2）一致
- [ ] T6 沙箱 image `agent-sandbox:latest` 已在 doc 00-overview §6 docker-compose 中预构建
- [ ] T8 RateLimiter Lua 脚本通过 `RedisScript` 加载，避免每次传输
- [ ] T10 MemoryServiceClient 超时 2s，避免 RecallMemory 阻塞主流程
- [ ] T12 异常映射表覆盖 doc 05 §12.4 全部错误码
- [ ] JaCoCo 配置已在 `agent-tool-engine/pom.xml` 中开启
- [ ] 集成测试默认用 H2 MySQL 模式 + jedis-mock + 关闭 Docker，Testcontainers 路径用 `@EnabledIfEnvironmentVariable` 守护
- [ ] 全部 commit message 遵循 `feat(agent-tool-engine): TX ...` 格式
- [ ] 全部 task 完成后跑 `mvn clean verify` 全绿 + JaCoCo 报告达标
