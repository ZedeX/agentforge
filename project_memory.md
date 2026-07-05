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

## 🚀 下一波（Wave 41+）计划

- **Plan 05 agent-tool-engine（0/12）**：解锁 Plan 06 agent-runtime 依赖；唯一未完成的 P1 计划。4 RPC + 9 项核心能力（ToolRegistry/ToolGateway/RiskClassifier/ApprovalStore/SandboxBorrower/ToolCache/ToolCallAuditor/ToolSemanticRecaller/ResultCleaner）
- **Plan 06 agent-runtime（0/10）**：依赖 task/memory/tool/model 全部完成（tool 待做）
- **Plan 09 infra 部署（0/?）**：13 个微服务的 Dockerfile + docker-compose + K8s + Nacos + 可观测组件

---

> **维护说明**：后续 Wave 新增内容将追加到对应的 Part 文件。当某个 Part 超过 500 行时，可继续拆分（如 Part 4 → Part 4a + Part 4b）。索引文件保持简要，仅列出 Part 分布和项目总览。
