# AgentForge 智能体平台 项目记忆索引

> 文档拆分日期：2026-07-04 | 原文件总行数：1808 行，已拆分为 4 个 Part
> 拆分原因：原文件超过 340KB（Read 工具 128KB 限制），按 Wave 批次拆分便于查阅

## 📋 Part 文件索引

| Part | 文件名 | 行数范围 | 覆盖内容 | 关键 Wave |
|---|---|---|---|---|
| **Part 1** | [project_memory_part1.md](./project_memory_part1.md) | 1~488 | 早期里程碑 + Wave 17~21 | Wave 17（骨架补全 + gh-api 推送换行符事故修复）<br/>Wave 18（model-gateway 骨架）<br/>Wave 19（3 模块骨架并行）<br/>Wave 20（CI streak=10 + A- 达成）<br/>Wave 21（v8 持久化深化期启动） |
| **Part 2** | [project_memory_part2.md](./project_memory_part2.md) | 489~857 | Wave 22~26（JPA 持久化 + gRPC） | Wave 22（agent-repo JPA）<br/>Wave 23（CostMeter JPA 集成）<br/>Wave 24~26（agent-knowledge JPA + gRPC） |
| **Part 3** | [project_memory_part3.md](./project_memory_part3.md) | 858~1361 | Wave 27~32（gRPC + agent-memory） | Wave 27~29（model-gateway gRPC 闭合）<br/>Wave 30~32（agent-memory T1-T3 + T8-T9） |
| **Part 4** | [project_memory_part4.md](./project_memory_part4.md) | 1362~ | Wave 33~40（agent-memory 收尾 + 全 Plan 推进） | Wave 33~37（MemoryDistiller→MemoryService gRPC）<br/>Wave 38（project_memory.md 拆分）<br/>Wave 39（Plan 03/04 闭合 + Milvus T6）<br/>Wave 40（Plan 07/08 闭合 + 集成测试三连击） |

## 📊 项目总体进度（截至 Wave 40）

### Plan 进度汇总

| Plan | 模块 | Task 进度 | 最新 Wave | CI streak |
|---|---|---|---|---|
| 01 | agent-proto + agent-common | 8/8 ✅ | Wave 1~4 | - |
| 02 | agent-gateway + agent-session | 10/10 ✅ | Wave 5~11 | - |
| 03 | agent-memory | **10/10** ✅ | Wave 30~39 | - |
| 04 | task-orchestrator + planning | **13/13** ✅ | P6 Wave 1~2 | - |
| 05 | agent-tool-engine | 0/12 ⏳ | - | - |
| 06 | agent-runtime | 0/10 ⏳ | - | - |
| 07 | agent-model-gateway | **14/14** ✅ | Wave 18~29, 40 | - |
| 08 | agent-repo + agent-knowledge | **12/12** ✅ | Wave 19~26, 40 | - |
| 09 | infra 部署 | 0/? ⏳ | - | - |

### 测试统计

- **累计测试方法**：1140+ 全绿（agent-model-gateway 116 / agent-repo 120 / agent-knowledge 153 / agent-memory 189 / 其他 562）
- **JaCoCo 覆盖率**：agent-memory line 89% / branch 79%（阈值 80%/70%）
- **TDD 审核等级**：v7.5 = 89.2 / B+ 通过

### CI streak 进展

- Wave 20：streak=10 → **A- 等级正式达成**
- Wave 38：streak=40（含 docs commit）
- Wave 39：streak=41
- Wave 40：streak=**42**（待 push 后确认）

## 🔗 相关文档

- **TDD 红绿循环记录**：[docs/tests/tdd-red-green-records-v1.2.md](./docs/tests/tdd-red-green-records-v1.2.md)
- **编码计划总览**：[docs/plans/00-coding-plans-overview.md](./docs/plans/00-coding-plans-overview.md) v2.3
- **文档索引**：[docs/README.md](./docs/README.md)
- **Plan 03 详情**：[docs/plans/03-agent-memory-plan.md](./docs/plans/03-agent-memory-plan.md)

## 📝 经验教训索引（按 Part 分布）

| Part | 教训编号 | 关键教训摘要 |
|---|---|---|
| Part 1 | 20~27 | gh CLI 直连能力 / Python 推送脚本 / PowerShell 数组捕获 bug |
| Part 2 | 28~40 | @DataJpaTest 单例 bean 状态污染 / JsonListConverter 设计 / 幂等 ingest 设计 |
| Part 3 | 47~59 | gRPC server streaming Flux 模式 / JPA Entity 字段重命名影响分析 / 状态机单次前进设计 |
| Part 4 | 60~91 | @ConditionalOnProperty 互斥 bean / JaCoCo exclude 模式误伤 / 余弦相似度边界处理 / Milvus SDK API 差异 / 集成测试组件协调缺口 |

## ✅ Wave 39 已完成（2026-07-04）

**任务**：Plan 04 文档闭合 + Plan 03 T6 MemoryVectorStore Milvus SDK 集成

**交付**：
- Plan 04 实际已全部完成（T5/T7/T11/T13 代码文件完整存在），文档从 9/13 更新为 13/13 ✅
- Plan 03 T6 双轨策略实现：
  - `MemoryVectorStoreImpl` 加 `@ConditionalOnProperty(milvus.enabled=false, matchIfMissing=true)` 作为 InMemory fallback
  - 新建 `MilvusVectorStoreImpl`（Milvus SDK MilvusClientV2 调用）+ `MilvusSchemaBuilder`（collection schema）+ `MilvusClientConfig`（条件 bean）
  - 集成测试 `MilvusVectorStoreImplTest` 标注 `@Disabled("Requires Milvus Docker")`
  - pom.xml 添加 `milvus-sdk-java:2.4.0` 依赖 + JaCoCo 排除 Milvus 实现类
- Plan 03 从 9/10 更新为 **10/10** ✅

**关键经验**：
86. Milvus SDK v2.4.0 API 与文档不完全一致：`SearchResult.getScore()` → `getDistance()`，`InsertReq.data` 接受 `JSONObject` 而非 `Map`，`IndexParam` 无 `extraParam` 方法。应先 javap 检查实际 API 再编码
87. `@ConditionalOnProperty` 条件装配的 bean 在测试环境不加载时，JaCoCo 会统计为 0% 覆盖率，需排除这些类

---

## ✅ Wave 40 已完成（2026-07-04）

**任务**：Plan 07 T14 + Plan 08 T10/T12 集成测试三连击，闭合 Plan 07（14/14）和 Plan 08（12/12）

**交付**：
- **Task 1 — Plan 07 T14**（commit `2bb92fc`）：agent-model-gateway 集成测试 6 E2E 场景（Chat/StreamChat/CountTokens/ListModels/PromptCache/Degradation），模块测试 116
- **Task 2 — Plan 08 T10**：agent-knowledge Milvus 双轨（MilvusEmbeddingServiceImpl HTTP-backed + KnowledgeSchemaBuilder 单测 + MilvusVectorStoreImplTest @Disabled 集成测试），模块测试 147
- **Task 3 — Plan 08 T12**：agent-repo 集成测试 6 E2E（AgentRepoIntegrationTest）+ agent-knowledge 集成测试 6 E2E（KnowledgeBaseIntegrationTest，H2 + JPA + InProcess gRPC + TransactionTemplate 包裹写 RPC），模块测试 120/153

**关键经验**：
88. `ListBasesRequest` proto 字段是 `page_size`/`page_token` 而非 `page`/`size`，集成测试必须查 proto 定义
89. package-private 方法跨包测试的限制：`AgentLifecycleManagerImpl.clear()` 是 package-private，跨包调用编译失败，每个测试用唯一 ID 避免状态冲突
90. Unicode box-drawing 字符在 Edit 工具中容易匹配失败，需 Read 文件查看确切内容后用精确字符串匹配
91. 集成测试的"组件协调缺口"暴露设计问题：`createAgent` 不自动 index、`ingestDocument` 不自动 indexChunk 是 skeleton 阶段简化，集成测试是发现此类 gap 的最佳手段

---

## ✅ Wave 41 已完成（2026-07-07）—— 待办清零：5 个横向微服务补全

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

## 🚀 下一波（Wave 42+）计划

- **Plan 05 agent-tool-engine（0/12）**：解锁 Plan 06 agent-runtime 依赖；唯一未完成的 P1 计划。4 RPC + 9 项核心能力（ToolRegistry/ToolGateway/RiskClassifier/ApprovalStore/SandboxBorrower/ToolCache/ToolCallAuditor/ToolSemanticRecaller/ResultCleaner）
- **Plan 06 agent-runtime（0/10）**：依赖 task/memory/tool/model 全部完成（tool 待做）
- **Plan 09 infra 部署（0/?）**：13 个微服务的 Dockerfile + docker-compose + K8s + Nacos + 可观测组件

---

> **维护说明**：后续 Wave 新增内容将追加到对应的 Part 文件。当某个 Part 超过 500 行时，可继续拆分（如 Part 4 → Part 4a + Part 4b）。索引文件保持简要，仅列出 Part 分布和项目总览。

---

## 🔴 Wave 42 红蓝对抗安全审计（2026-07-07）

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

## 🛡️ Wave 43 安全加固修复（2026-07-07）—— 基于 Wave 42 审计报告

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

### Wave 3 待做（R-11 ~ S-11 MED）

| ID | 修复 | 涉及模块 |
|---|---|---|
| R-11 | 审计落库失败不再吞异常 | agent-risk-control |
| S-08 | validateParams 改 JSON Schema 解析 | agent-tool-engine |
| S-09 | checkpoint 序列化改 Jackson | agent-runtime |
| S-11 | CostMeter 改 BigDecimal | agent-model-gateway |
| S-05/S-07 | JVM 堆对齐 + server.shutdown=graceful | 全模块 application.yml |

**测试验证**：agent-gateway 47 ✅ / agent-tool-engine 235 ✅(5 skipped) / agent-risk-control 18 ✅
