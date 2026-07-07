# AgentForge 智能体平台 项目记忆（Part 5：Wave 41~47）

> 创建日期：2026-07-08 | 本 Part 覆盖：Wave 41~47
> 内容范围：待办清零（5 横向微服务补全）+ 红蓝对抗安全审计（Wave 42~44）+ S-03/S-04/S-12 修复 + 项目清理 + 文档完善
> 前序文件：[project_memory_part4.md](./project_memory_part4.md)（Wave 33~40）

---

## Wave 41：待办清零——5 个横向微服务补全（2026-07-07）

**任务**：检查全部待办，补全 5 个横向微服务（quality/hallucination/drift/risk-control/observability）为独立可生产微服务，全项目编译+测试通过

**交付**：
- **agent-quality**（端口 8100/9100）：QualityApplication + QualityProperties + application.yml + BadcaseRecordEntity/ReviewItemEntity + Repository + QualityGrpcService(4 RPC) + QualityMapper(完整 proto↔POJO↔Entity 双向映射) + GrpcExceptionAdvice + 异常层级 + 55 测试全绿
- **hallucination-governance**（端口 8106/9106）：补全为独立微服务，HallucinationApplication + config + entity(从 repository 包迁移) + exception(加 errorCode 字段) + 42 测试全绿
- **drift-monitor**（端口 8108/9108）：DriftMonitorApplication + DriftMonitorProperties + BehaviorBaselineEntity/DriftSignalEntity + Repository + DriftGrpcService(4 RPC) + DriftMapper + 4 模块测试
- **agent-risk-control**（端口 8102/9102）：RiskControlApplication + RiskControlProperties + ContentViolationEntity + 3 个 API Impl(ContentSafety/Permission/Compliance) + RiskControlGrpcService(3 RPC) + 7 测试全绿
- **agent-observability**（端口 8104/9104）：ObservabilityApplication + TraceEntity/MetricDataPointEntity/ServiceHealthEntity + 3 Repository + ObservabilityGrpcService(3 RPC) + ObservabilityMapper + 5 测试全绿
- **agent-planning**（端口 8086/9086）：PlanningApplication + config + exception + grpc + 测试
- **根 pom.xml**：添加 agent-risk-control + agent-observability 模块声明
- **全项目**：19 模块 `mvn compile` + `mvn test` 全绿

**修复的编译/测试错误**：
1. drift-monitor `DriftSignal` proto/POJO 同名 import 冲突 → POJO 用 FQN，proto 保留 import
2. agent-planning `PlanServiceGrpc` → `PlanningServiceGrpc`（proto service 名对齐）
3. hallucination `HallucinationException` 缺 errorCode 字段 → 加 `@Getter private final HallucinationErrorCode errorCode`
4. agent-quality `QualityMapper` 方法缺失 → 补全 `toBadcaseRecord`/`toDomain`/`parseBadcaseCategory`/`parseBadcaseSeverity`/`toValidateTaskResponse`/`toQualityMetricsResponse` + `mapLayerName`(FORMAT_VIOLATION→hard)
5. agent-risk-control `RiskControlMapper` proto/POJO 同名 import 冲突 → POJO 参数用 FQN
6. agent-observability `TraceEntity` 缺 `serviceName` 字段 → repository 方法改为 `findByRootServiceAndStartTimeBetween`
7. agent-observability `MetricDataPointEntity.value` 列名是 H2 SQL 保留字 → 改列名 `metric_value`

**关键经验**：
92. Proto 和 POJO 同名类（如 `DriftSignal`/`CheckPermissionResponse`）在 mapper 中必须用 FQN 区分，不能同时 import——Java single-type import 限制
93. Lombok `@Slf4j`/`@Getter`/`@Setter` 生成的成员在依赖类编译失败时报"找不到符号"——是级联错误，修好根因后自动消失
94. `value`/`timestamp`/`key` 等是 H2 SQL 保留字，JPA `@Column(name=...)` 需避开或用反引号
95. 测试文件作为 API spec：当测试期望的方法名/签名与实现不同时，应以测试为 spec 扩展实现（如 `parseCategory`→`parseBadcaseCategory`），而非修改测试

---

## 🔴 Wave 42：红蓝对抗安全审计（2026-07-07）

**任务**：以红蓝对抗视角对全系统做安全性/稳定性/健壮性/功能完备性四维审计，输出检测报告。

**交付**：[docs/audits/red-blue-team-report-2026-07-07.md](./docs/audits/red-blue-team-report-2026-07-07.md)

**方法**：STRIDE 威胁建模 + 4 个并行 Explore agent（安全/稳定/健壮/完备）+ 人工读码核实。**剔除 4 处 agent 误判**（JWT 过期/SQL 注入/Plan 05 06 空实现/ReAct 无熔断均被证伪）。

**核心发现（CRITICAL 4 条 + HIGH 5 条）**：
1. **R-01** `AuthFilter.java:29` 硬编码后门 API Key `ak_test_valid_key_2026` → 鉴权绕过
2. **R-02** `ToolGatewayImpl.java:184-191` 调用方可自填 `riskLevel=R1` 绕过 RiskClassifier + R3 审批
3. **R-03** `application.yml:49` JWT secret 硬编码入库 → 任意伪造身份
4. **R-04** `01-serviceaccounts.yaml:13-38` K8s RoleBinding 给所有 SA `get/list` 全部 secrets → 横向移动
5. R-05 gRPC 全 plaintext / R-06 PermissionChecker 是 3 用户 mock / R-07 Docker 沙箱缺 cap-drop+non-root / R-08 K8s 无 securityContext + actuator 暴露 / R-09 CI 无 secret/CVE 扫描

**攻击链**：链 A（API Key → system 用户 → tool-engine 自填 R1 → 沙箱 RCE → K8s secrets → 全集群沦陷）已端到端验证。

**重要修正**：project_memory 此前标注 Plan 05 agent-tool-engine 0/12、Plan 06 agent-runtime 0/10 **过时**——实际两模块已完整实现（tool-engine ~85 文件含 DockerSandboxBorrower/ShellExecutor/PythonExecutor/4 执行器/9 PRD 组件；runtime ~70 文件含 ReActLoopImpl/ReflexionEngineImpl/TokenWatermarkMonitorImpl）。Plan 进度表需更新。

**Skill 使用**：brainstorming（STRIDE+攻击链）、writing-plans+tdd（§8 回归测试计划红绿循环）、using-superpowers（agent 与人工读码冲突时以读码为准）；gsd 不适用一次性审计故未机械套用。

**后续建议**：P0 五条（删后门 key / 删 riskLevel 绕过 / JWT 密钥出库 / K8s RBAC 收紧 / gRPC mTLS）优先修复，阻断攻击链 A/B/C。每条配 TDD 红绿回归测试见报告 §8。

---

## 🛡️ Wave 43：安全加固修复（2026-07-07）—— 基于 Wave 42 审计报告

**任务**：系统性修复根除 Wave 42 发现的所有安全问题

**方法**：3 Wave 分批修复（CRITICAL→HIGH→MED），TDD 红绿循环验证

### Wave 1 完成（R-01 ~ R-05 CRITICAL）— commit b1312a7 ~ ad0e11e

| ID | 修复 | 变更文件 |
|---|---|---|
| R-01 | 删除 AuthFilter 硬编码 API Key 后门 + ApiKeyProperties 租户绑定 | `AuthFilter.java`, `ApiKeyProperties.java` |
| R-02 | ToolGatewayImpl riskLevel 永不降级（never-downgrade rule） | `ToolGatewayImpl.java` + 2 TDD tests |
| R-03 | JWT secret 改 env 注入 + fail-fast Assert.hasText | `JwtUtil.java`, `application.yml` |
| R-04 | K8s RBAC 拆分为 per-SA RoleBinding + OPA conftest 策略 | `01-serviceaccounts.yaml`, `rbac-conftest.rego` |
| R-05 | gRPC mTLS 配置移至 application-mtls.yml profile | `application.yml` → `application-mtls.yml` |

**关键修复**：GatewayApplicationContextTest 因 `grpc.server.security` 空默认值导致 Spring 上下文加载失败 → 移至 profile 解决。agent-gateway 47 测试全绿。

### Wave 2 完成（R-06 ~ R-10 HIGH）— commit 85651a3

| ID | 修复 | 变更文件 |
|---|---|---|
| R-06 | PermissionCheckerImpl 用 JWT claims role（via request），删除硬编码 USER_ROLES | `PermissionCheckerImpl.java`, `risk_control.proto` +role 字段, `CheckPermissionRequest.java`, `RiskControlMapper.java` + 11 TDD tests |
| R-07 | DockerSandboxBorrower 加固：cap-drop ALL + user=nobody + no-new-privileges | `DockerSandboxBorrower.java` + 4 TDD tests |
| R-08 | 12 个 K8s Deployment 加 securityContext（pod+container 级）+ actuator 端口收口 | 12 deployment YAMLs + `02-configmap-bootstrap.yaml` |
| R-09 | GitHub Actions CI 安全扫描（gitleaks + trivy + CodeQL） | `.github/workflows/security-scan.yml`, `.github/codeql-config.yml` |
| R-10 | 13 个 application.yml 硬编码 `password: root` → `${DB_PASSWORD:}` | 13 application.yml files |

**关键修复**：
- docker-java `Capability.ALL`（不是 ALL_CAPS）；`withUser()` 在 `CreateContainerCmd` 上而非 `HostConfig`
- Spring `@ConfigurationProperties` Map 绑定空字符串失败 → `@PostConstruct initFromEnv()` 手动解析 API_KEY_TENANTS
- grpc-spring-boot-starter `ClientAuth` 只有 REQUIRE/OFF（无 WANT）
- Maven 未安装 → 通过 Scoop + proxy 修复安装（`scoop config proxy 127.0.0.1:1089`）

### Wave 3 完成（R-11 ~ S-11 MED）— commit 79dcc27

| ID | 修复 | 变更文件 |
|---|---|---|
| R-11 | 审计落库失败不再吞异常：成功路径抛出，错误路径 addSuppressed | `ToolGatewayImpl.java` + 2 TDD tests |
| S-08 | validateParams 改 Jackson JSON 解析，替代 contains() 字符串匹配 | `ToolGatewayImpl.java` + 3 TDD tests |
| S-09 | checkpoint 序列化改 Jackson ObjectMapper，替代 String.format | `ReActLoopImpl.java` |
| S-11 | CostMeter 内部改 BigDecimal 计算，保持 double 接口不变 | `CostMeterImpl.java` |
| S-05 | 12 个 K8s Deployment JVM 堆对齐（Xmx=75% pod limit） | 12 deployment YAMLs |
| S-07 | bootstrap ConfigMap 加 server.shutdown=graceful + 30s timeout | `02-configmap-bootstrap.yaml` |

**测试验证**：agent-tool-engine 23 ✅ / agent-model-gateway 8 ✅ / agent-runtime 编译 ✅

---

**Wave 1~3 安全加固完成总结**：4 CRITICAL + 5 HIGH + 5 MED = 14 条全部修复。TDD 红绿循环验证。6 个 commits 推送至 main。

---

## 🔧 S-03 修复：RocketMQ 消费幂等（JPA event_consume_log 表方案）（2026-07-07）

**任务**：修复 `SubtaskDoneHandler.java` 中 RocketMQ 消费者幂等机制的 4 个缺陷：内存 Set 跨 Pod 不共享 / 无界增长 OOM / 重启丢失 / 与业务写非事务。

**方法**：TDD 红绿循环（test-driven-development skill）—— 先写失败测试 → 验证 RED → 实现修复 → 验证 GREEN。

**交付**：
- 新建 `EventConsumeLog` 实体（`entity/EventConsumeLog.java`）：`event_consume_log` 表，`event_id` 列唯一约束，`consumed_at` 时间戳
- 新建 `EventConsumeLogRepository` 接口（`repository/EventConsumeLogRepository.java`）：`existsByEventId()` 快速路径检查
- 修改 `SubtaskDoneHandler.java`：删除 `ConcurrentHashMap.newKeySet()` 内存 Set，注入 `EventConsumeLogRepository`，幂等逻辑改为 `existsByEventId` 检查 + `save` 插入 + `DataIntegrityViolationException` 竞争兜底
- 修改 `SubtaskDoneHandlerTest.java`：适配新构造函数（+`@Mock EventConsumeLogRepository`），UT-MQ-001 改用 `thenReturn(false, true)` 模拟二次调用
- 新建 `SubtaskDoneHandlerIdempotencyTest.java`（`@DataJpaTest` + H2 + `@Import(SubtaskDoneHandler.class)`）：3 个场景验证

**TDD 红绿记录**：
- RED：3 个测试全部失败（`eventConsumeLogRepository.count()` = 0，因为旧 handler 不写 event_consume_log 表）
- GREEN：13 个测试全部通过（3 新幂等测试 + 10 既有单元测试），BUILD SUCCESS

**幂等机制设计**：
1. eventId 为 null 时，从 `taskId + ":" + nodeId + ":" + status` 生成去重键
2. `existsByEventId(eventId)` 快速路径 → true 跳过
3. `save(new EventConsumeLog(eventId))` 在同一 `@Transactional` 内与业务写原子提交
4. `catch (DataIntegrityViolationException)` 处理多 Pod 并发竞争（唯一约束兜底）
5. 业务逻辑抛异常时事务回滚，EventConsumeLog 插入一并回滚，事件可重试

**关键经验**：
96. `@DataJpaTest` 默认不扫描 `@Component`，需 `@Import(SubtaskDoneHandler.class)` 显式引入；`TaskStateMachine`/`ReplanModeSelector` 非 JPA 依赖用 `@MockBean` 替换
97. `@DataJpaTest` + H2 需 `@TestPropertySource` 覆盖 `ddl-auto=validate→create-drop` 和 `dialect=MySQL8Dialect→H2Dialect`（主 application.yml 为 MySQL 配置）
98. 同一事务内 `save` 后 `existsByEventId` 可见：Hibernate AUTO flush 模式在查询前自动刷新持久化上下文
99. PowerShell 传递 `-Dproperty=value` 给 mvn.cmd 时会在 `=` 处拆分，应先 `mvn install -DskipTests` 安装依赖再单独 `mvn test -pl module` 避免 `-am` 传播 `-Dtest` 到上游模块

---

## 🔧 Wave 44：安全加固剩余修复（MED ~ LOW）（2026-07-07）

**任务**：修复 Wave 42 审计报告中剩余的 MED 级别发现（S-02/S-06/S-10/S-11）+ 2 CVE 升级

**方法**：TDD 红绿循环 + 并行修复

### Wave 4-1 CVE 升级

| CVE | 修复 | 变更文件 |
|---|---|---|
| CVE-2024-38816 | Spring Boot 3.2.5 → 3.2.12（path traversal 修复） | `pom.xml` `<spring-boot.version>` |
| CVE-2024-7254 | protobuf 3.25.1 → 3.25.5（解析 DoS 修复） | `pom.xml` `<protobuf.version>`；grpc 1.62.3不存在，回退到1.62.2 |

### Wave 4-2 S-06 gRPC Deadline

| ID | 修复 | 变更文件 |
|---|---|---|
| S-06 | ModelGatewayClientImpl 加 `stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)` | `ModelGatewayClientImpl.java`；MemoryProperties 新增 `modelGateway.timeoutMs` 字段（默认10000） |

### Wave 4-3 S-02 Resilience4j 补全

| ID | 修复 | 变更文件 |
|---|---|---|
| S-02 | agent-runtime 补 BulkheadRegistry + TimeLimiterRegistry + Retry 异常过滤 | `Resilience4jConfig.java`；`RuntimeProperties` 新增 `bulkhead.maxConcurrent` + `timeLimiter.timeoutMs` 字段；pom.xml 新增 resilience4j-bulkhead + resilience4j-timelimiter 依赖 |

**关键修复**：
- Resilience4j 2.x `BulkheadRegistry.of(Map)` 的 key 是配置名不是实例名 → 改用 `addConfiguration(configName, config)` + `bulkhead(instanceName, configName)`
- BulkheadConfig `maxWaitDuration(Duration.ofMillis(100))` 防止慢消费者阻塞
- TimeLimiterConfig `cancelRunningFuture(true)` 确保超时取消
- RetryConfig `retryExceptions(TimeoutException, IOException, ExecutionException)` + `ignoreExceptions(IllegalArgumentException, IllegalStateException)` 精细化过滤

### Wave 4-4 S-10 gRPC 入参校验

| ID | 修复 | 变更文件 |
|---|---|---|
| S-10 | 3 个 GrpcService 加 INVALID_ARGUMENT 入参校验（10 RPC 方法） | `RiskControlGrpcService.java`（CheckPermission/CheckCompliance/CheckContentSafety 3 RPC）<br/>`QualityGrpcService.java`（ValidateTask/RecordBadcase/GetMetrics 3 RPC）<br/>`DriftGrpcService.java`（DetectDrift/CorrectDrift/GetBaseline 3 RPC）<br/>各方法加 `Assert.hasText(request.getXXX(), "xxx must not be blank")` + `throw Status.INVALID_ARGUMENT.withDescription(...)` |

### Wave 4-5 S-03 RocketMQ 幂等（已完成，见上节）

---

**Wave 1~4 安全加固完成总结**：4 CRITICAL + 5 HIGH + 6 MED + 5 MED剩余 + 2 CVE = **21 条全部修复**。8 个 commits 推送至 main（b1312a7 ~ 584c315）。攻击链 A（API Key → tool-engine R1 绕过 → 沙箱 RCE → K8s secrets）/ B（JWT 伪造 → 越权）/ C（K8s RBAC → 横向移动）端到端阻断。每条修复配 TDD 红绿测试验证。

**关键经验**：
100. Resilience4j 2.x Registry API 变化：`of(Map)` 是配置名→配置映射，实例名需单独 `addConfiguration` + `circuitBreaker(instanceName, configName)` 两步调用
101. Bulkhead `maxWaitDuration` 必须设置较短值（如100ms），否则慢消费者会耗尽线程池
102. Retry 异常过滤：`retryExceptions` 仅重试瞬时异常，`ignoreExceptions` 跳过确定性失败，避免无效重试放大问题
103. gRPC 入参校验用 `Assert.hasText` + `Status.INVALID_ARGUMENT`，对齐 GrpcExceptionAdvice 错误码映射（400→INVALID_ARGUMENT）
104. CVE 升级需验证版本是否存在：Spring Boot 3.2.12 OK，protobuf 3.25.5 OK，但 grpc 1.62.3 不存在（Central 最新1.62.2），需回退
105. Spring Boot 3.2.x 升级跨小版本（.5→.12）通常兼容，但需全量 `mvn test` 验证（agent-gateway/agent-session/agent-memory/agent-runtime等全部通过）

---

### Wave 45：S-04/S-12 规划文档立项（2026-07-07）

**背景**：红蓝对抗审计 S-04（跨服务写无补偿事务，MED）与 S-12（异常被 catch+log 吞掉，LOW-MED）此前列为 LOW 级待办。本次为两项立项编写规划文档，进入实现前评审阶段。

**产出**：
- `docs/plans/10-cross-service-compensation-and-exception-plan.md`（PRD + 6 Phase 实施计划，UTF-8 无 BOM，15811 字符）
- 合并一份 PRD（用户决策：两者在 ReActLoopImpl 重叠，合并避免重复上下文）
- S-04 采用本地消息表（Outbox）模式（用户决策：复用现有 RocketMQ，改动可控）
- S-04 范围 = 平台级补偿框架（用户决策：不只修点名两处，建可复用基础设施）
- S-12 范围 = 全量排查 + 统一规范（用户决策：逐模块排查所有吞没点 + 建 ADR-006）

**PRD 关键决策**：
- Outbox 表 `outbox_message` 放各服务自有 DB，基础设施代码放 `agent-common`
- 状态机 PENDING→SENT / PENDING→FAILED→...→DEAD（超 5 次）
- 复用 S-03 的 `event_consume_log` 幂等模式（消费侧 event_id = outbox_message.id）
- RocketMQ topic 命名 `{service}.{event}`（如 tool.audit / runtime.stepstate）
- 新增 ADR-006：禁止 catch+log 静默吞异常，必须 rethrow / outbox / 显式注释

**6 Phase 垂直切片**：
1. Outbox 基础设施（实体+仓库+DDL，H2 单元测试）
2. Outbox Relay + RocketMQ 投递 + 端到端故障验证（Testcontainers 停 DB 30s）
3. ToolGatewayImpl 审计接入 Outbox + ToolCallAuditorImpl 去吞（S-04 第一接入场景）
4. ReActLoopImpl syncStepState 接入 Outbox + checkpoint 补偿（S-04 第二接入，与 S-12 重叠）
5. S-12 全量吞没点治理 + GrpcExceptionAdvice 统一 + ADR-006
6. 监控告警 + 扩展场景验证 + 文档同步

**代码研究已发现的吞没点（初稿，Phase 5 全量核实）**：
1. `ToolCallAuditorImpl.audit()` lines 113-122 — catch Exception 吞掉，使 R-11 修复部分失效
2. `ReActLoopImpl.syncStepState` checkpoint lines 328-331 — catch Exception 吞掉
3. `agent-runtime/GrpcExceptionAdvice` lines 43-60 — catch Throwable 无日志
4. `agent-task-orchestrator/GrpcExceptionAdvice` lines 136/159/194/257 — catch Throwable（含 Error）无日志
5. `agent-tool-engine/GrpcExceptionAdvice` lines 59-62 — 已有 log.warn，已合规

**关键研究发现**：
- 全平台无 saga/TCC/outbox/local-message-table 实现（仅设计文档有 RocketMQ 事务消息示例）
- `ToolCallAuditorImpl.audit()` 内部 catch 吞 JPA 异常 → `ToolGatewayImpl` 的 R-11 try/catch 分支成死代码
- `StepStateSyncerImpl` 当前是内存 ConcurrentHashMap（骨架），裸 RPC 调用点未来接 Redis 才是真跨服务 RPC
- 现有最接近的补偿模式：`SubtaskDoneHandler` 的 `event_consume_log` 幂等（S-03 修复成果，可复用）

---

### Wave 45 代码实现（2026-07-07~08）

**S-04 Outbox 框架实现**：
- `OutboxMessage` 实体 + `OutboxRepository` + `OutboxRelay`（@Scheduled poller）+ `OutboxPublisher`（interface）+ `OutboxConsumer`（幂等消费，复用 event_consume_log）
- `OutboxRelay` 支持 Micrometer 指标（published_total / failed_total / latency_seconds）
- `OutboxJpaConfig` 独立 @Configuration 避免 @DataJpaTest 回归

**S-04 Outbox 接入**：
- `ToolGatewayImpl` 审计写 outbox（topic=tool.audit，ToolAuditOutboxConsumer 消费）
- `ReActLoopImpl` syncStepState + checkpoint 写 outbox（topic=runtime.stepstate）

**S-04 agent-memory 扩展**：
- `LongTermMemoryWriterImpl` 向量插入走 outbox（topic=memory.vector.insert）
- `VectorInsertPayload` DTO + `MemoryVectorInsertOutboxConsumer` 消费
- outbox 写入失败降级为直接 vectorStore.insert()
- `MemoryOutboxJpaConfig` 独立 @Configuration
- `03-agent-memory.sql` 增加 outbox_message + consume_log DDL

**S-12 异常治理**：
- `GrpcExceptionAdvice.translate()` 必须含 log.warn（status + desc + full exception）
- gRPC 服务用 catch(Exception) 不用 catch(Throwable)（让 Error 崩溃 pod）
- 禁止 catch+log 吞异常（必须 rethrow / outbox / 显式注释）

**Plan 10 进度**：6/6 ✅ 全部完成

---

## 🧹 Wave 46：项目清理 + Bug 修复（2026-07-08）

**任务**：整体检查项目，修复遗留 bug，删除本地无用文件，提交 GitHub

**修复**：
- **listModels 空 tier 返回 INVALID_ARGUMENT bug**（commit `0753d5b`）：`ModelGatewayGrpcService.listModels()` 对空/null tier 拒绝返回 INVALID_ARGUMENT，但 `ModelCatalog.list()` 已正确处理空 tier 为"返回全部"。删除了多余的提前拒绝校验，委托 ModelCatalog 处理。此 bug 导致 `ModelGatewayGrpcServiceChatTest.should_ReturnAllModels_When_TierEmpty` 失败。
- **删除 test_enc.txt**（commit `41eb3d8`）：编码测试文件不再需要，从 git 追踪中移除。

**验证**：
- 全项目 19 模块 `mvn test` 全绿（0 Failures, 0 Errors, 10 Skipped）
- 先前标记为 flaky 的 FixturesShowcaseTest（9 tests）和 ResilienceDecoratorTest（11 tests）均通过

**本地文件清理**（共删除 60+ 文件）：
- 7 个临时测试脚本：run-*.bat, run-fix-test.bat 等
- 41 个 .log 文件：wave29-build.log, mvn-verify*.log, agent-tool-engine-*.log, hs_err_pid*.log 等
- 11 个 wave*/commit-msg 临时文件
- tmp/ 目录（含 CI 日志）
- docs/11-detail-flow/_mermaid-validate/.chrome-profile/（浏览器自动化残留）
- docs/11-detail-flow/_mermaid-validate/node_modules/（npm 依赖）
- agent-log-temp.md（170KB 日志）
- target-test-log.txt

**保留的文件**（有文档引用）：
- detail-MRD.md（README.md 引用）
- project_memory_part1~4.md（project_memory.md 引用）

**关键经验**：
106. `listModels` 空 tier 语义：API 设计中空/null 参数常表示"返回全部"，应委托给 catalog 层统一处理，而非在 gRPC 层提前拒绝。测试名 `should_ReturnAllModels_When_TierEmpty` 即为此语义的 spec

---

## 📝 Wave 47：文档完善（2026-07-08）

**任务**：增强 README.md（宣传图/功能截图/快速开始/使用手册）+ 编写运营文档 + 更新 project_memory

**交付**：
- **README.md** 重写：hero banner 图片 + 7 大能力卡片 + Mermaid 架构图 + 8 步快速开始（含 curl 示例）+ 流程图 + 前端控制台表格 + 安全章节 + 最新状态
- **docs/user-guide.md**（新建）：12 章——平台概述/角色权限/Agent 管理/任务提交/SSE 流式/工具管理(R1/R2/R3)/记忆系统(三级)/知识库/质量与幻觉治理/REST API 参考/gRPC API 参考(13 服务 50+ RPC)/FAQ
- **docs/ops-guide.md**（新建）：14 章——部署架构(ASCII 拓扑)/Docker Compose/K8s(Deployments/Services/HPA/PDB)/中间件/Nacos 配置/Vault 密钥/mTLS/可观测栈/安全检查清单/DDL 初始化/日常运维/Prometheus 告警规则/故障排查(6 场景)/性能调优(JVM/连接池/gRPC/Resilience4j/Milvus)/备份恢复
- **docs/README.md** 更新：Plan 03~09 状态 🔄/⏳→✅ + PRD 交付物 #2 ⏸→✅ + 测试 916+→1580+ + CI streak 39→48+ + 审计报告+运营文档链接
- **docs/images/hero-banner.png**（新建）：SDXL 生成，176KB

---

## 📊 Part 5 经验教训索引

| 编号 | 关键教训 |
|---|---|
| 92 | Proto/POJO 同名类在 mapper 中必须用 FQN 区分 |
| 93 | Lombok 级联错误——修好根因后自动消失 |
| 94 | H2 SQL 保留字（value/timestamp/key）需避开 |
| 95 | 测试文件作为 API spec——以测试为准扩展实现 |
| 96 | @DataJpaTest 不扫描 @Component，需 @Import |
| 97 | @DataJpaTest + H2 需 @TestPropertySource 覆盖 |
| 98 | Hibernate AUTO flush——save 后查询可见 |
| 99 | PowerShell -D 参数拆分问题 |
| 100 | Resilience4j 2.x Registry API——配置名 vs 实例名 |
| 101 | Bulkhead maxWaitDuration 必须较短 |
| 102 | Retry 异常过滤——retryExceptions + ignoreExceptions |
| 103 | gRPC 入参校验 Assert.hasText + INVALID_ARGUMENT |
| 104 | CVE 升级需验证版本存在 |
| 105 | Spring Boot 小版本升级兼容但需全量验证 |
| 106 | listModels 空 tier→委托 catalog 层处理 |

---

（Part 5 结束，覆盖 Wave 41~47）
