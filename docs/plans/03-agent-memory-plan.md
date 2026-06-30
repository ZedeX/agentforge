# agent-memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `agent-memory`（端口 8088 / gRPC 9088）模块补齐记忆系统全栈能力：MemoryService gRPC 服务（4 RPC：StoreMemory / RecallMemory / DistillMemory / GetMemoryStats），覆盖 MemoryExtractor 提取、MemoryDistiller 摘要、MemoryVectorStore 向量存储、ImportanceScorer 重要性评分、MemoryTtlManager TTL 过期、MemoryDeduper 去重、LongTermMemoryWriter 长期写入、EmbeddingClient 向量客户端八项核心能力。已有 16 个骨架文件（api 接口 + enums + model POJO），本计划 T1~T10 落地全部业务逻辑、JPA Entity、Milvus 对接与端到端集成测试，对齐 v5 审核 §6 P6-6 整改项与 doc 04-memory 设计。

**Architecture:** 单 Spring Boot 应用 `agent-memory`，对外暴露 gRPC 服务 MemoryService（端口 9088）+ Actuator（8088）。内部以 `memory_record` 表（MySQL 逻辑库 `agent_memory`）为结构化主存，Milvus 向量库承载语义召回（collection `agent_memory_vector`），形成 "结构化 + 向量化" 双层记忆。上游消费 agent-runtime 的会话事件与任务结果，下游被 agent-task-orchestrator / agent-runtime 检索。依赖 agent-proto（Protobuf 契约 `memory.proto` / `common.proto`）与 agent-common（MemoryType / MemoryStatus / ErrorCode / BusinessException）。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / grpc-spring-boot-starter 3.1.0.RELEASE（net.devh）/ spring-boot-starter-data-jpa / MySQL Connector 8 / Milvus SDK Java 2.3.4 / Caffeine 3.1.8（本地缓存）/ Jackson / Lombok / JUnit 5 / Mockito 5 / AssertJ 3.25.3 / Awaitility 4.2.0 / H2（MySQL 模式，测试备选）/ Testcontainers 1.19.7（Milvus + MySQL 集成测试可选）

---

## 设计文档对齐

| 项 | 来源 | 锁定值 |
|---|---|---|
| agent-memory HTTP / gRPC 端口 | doc 00-overview §3.1 | 8088（HTTP） / 9088（gRPC） |
| 逻辑库 | doc 01-database §0.4 / §2.1 | `agent_memory`（memory_record / memory_topic / memory_extract_log） |
| MemoryService gRPC 4 RPC | `agent-proto/src/main/proto/memory.proto` | StoreMemory / RecallMemory / DistillMemory / GetMemoryStats |
| proto 生成包名 | memory.proto / common.proto | `agentplatform.memory.v1` / `agentplatform.common.v1` |
| 记忆类型 4 类 | doc 04-memory §3.1 + MemoryType 枚举 | EPISODIC（情景）/ SEMANTIC（语义）/ PROCEDURAL（程序）/ REFLECTIVE（反思） |
| 记忆状态 4 态 | doc 04-memory §3.2 + MemoryStatus 枚举 | RAW（原始）/ ACTIVE（活跃）/ DISTILLED（蒸馏）/ ARCHIVED（归档）|
| 任务结果枚举 | doc 04-memory §3.3 + TaskOutcome 枚举 | SUCCESS / FAILURE / PARTIAL / TIMEOUT |
| 重要性评分维度 | doc 04-memory §4.2 | 5 维度加权：情感强度 / 频率 / 新颖度 / 任务相关性 / 时间衰减（权重 0.20/0.25/0.20/0.25/0.10） |
| 评分等级阈值 | doc 04-memory §4.3 | score≥0.7 → HIGH / 0.4≤score<0.7 → MEDIUM / score<0.4 → LOW |
| TTL 默认策略 | doc 04-memory §5.1 | RAW→ACTIVE：立即；ACTIVE→DISTILLED：7 天；DISTILLED→ARCHIVED：30 天；LOW 归档：3 天 |
| 蒸馏（Distill）触发条件 | doc 04-memory §6.1 | 同 tenantId + topic 下 ACTIVE 记忆 ≥20 条 或 总 token ≥8K |
| 蒸馏摘要模型 | doc 04-memory §6.3 | 调用 agent-model-gateway chat/completions（模型默认 gte-distill-v1，temperature 0.2） |
| Embedding 向量维度 | doc 04-memory §7.2 | 1024（调用 agent-model-gateway /embeddings 接口，模型 text-embedding-v3） |
| Milvus collection | doc 04-memory §7.3 | `agent_memory_vector`，字段：id / tenant_id / memory_id / vector(1024) / importance / status / created_at |
| Milvus 索引 | doc 04-memory §7.4 | IVF_FLAT，nlist=1024，MetricType=COSINE |
| Recall 召回参数 | doc 04-memory §8.2 | topK=10（默认可配）/ scoreThreshold=0.75 / 按重要性加权后排序 |
| 去重策略 | doc 04-memory §9.1 | (a) 完全相同 hash（SHA-256(content)）→ 丢弃；(b) 余弦相似度 ≥0.95 → 合并保留高 importance；(c) 0.85~0.95 → 标记关联 |
| 长期记忆写入门槛 | doc 04-memory §10.2 | importance≥MEDIUM 且 status=DISTILLED，或人工显式 promote |
| LongTermMemoryWriter 写入路径 | doc 04-memory §10.3 | 同步写 MySQL `memory_record`（status=ACTIVE）+ 异步投递 Milvus（异步刷写失败重试 3 次） |
| EmbeddingClient 接口 | doc 04-memory §7.5 | `float[] embed(String text, String tenantId)` + `List<float[]> embedBatch(List<String>, String tenantId)` |
| 错误码域 | doc 04-memory §12.4 | MEMORY_NOT_FOUND(404) / MEMORY_VECTOR_STORE_FAILURE(500) / EMBEDDING_SERVICE_FAILURE(503) / MEMORY_DISTILL_FAILED(500) / MEMORY_TTL_EXPIRED(410) / MEMORY_DUPLICATE(409) |
| gRPC 服务类签名 | doc 04-memory §11.1 | `@GrpcService extends MemoryServiceGrpc.MemoryServiceImplBase` |
| 配置参数 | doc 04-memory §13 | `memory.ttl.rawToActive=0` / `memory.ttl.activeToDistilled=7d` / `memory.ttl.distilledToArchived=30d` / `memory.recall.topK=10` / `memory.recall.scoreThreshold=0.75` / `memory.distill.triggerCount=20` / `memory.dedup.exactThreshold=1.0` / `memory.dedup.cosineHigh=0.95` / `memory.dedup.cosineLow=0.85` |
| 异常分级与重试 | doc 04-memory §12.3 | 嵌入服务超时：3 次指数退避（100/300/1000 ms）；Milvus 写入失败：3 次 + 死信；蒸馏失败：保留 ACTIVE 不降级 |
| ADR-002 | docs/adr/ADR-002-milvus-vs-pgvector.md | 选 Milvus 不选 pgvector：千万级向量 + IVF 索引性能更优 |
| 测试用例 | `docs/tests/unit-test-cases.md` §7 | UT-MEM-001~015 |
| v5 审核整改项 | `docs/tests/tdd-audit-report-v5.md` §6 P6-6 | 实现 agent-memory T1-T10 D2 +1.0 |

---

## 文件结构总览

### 已完成骨架文件（16 个，仅接口/POJO，无业务实现）

| 文件 | 当前状态 | 职责 |
|---|---|---|
| `agent-memory/pom.xml` | 占位 | Maven 配置（需补全依赖） |
| `agent-memory/src/main/resources/application.yml` | 占位 | 端口 + 数据源（需补全） |
| `agent-memory/src/main/java/com/agent/memory/MemoryApplication.java` | 占位 | Spring Boot 启动类（需补全 @SpringBootApplication + @EnableJpaRepositories） |
| `.../api/MemoryExtractor.java` | 接口 | 从 TaskResult / 会话事件提取 ExtractedMemory |
| `.../api/MemoryDistiller.java` | 接口 | 将多条 ACTIVE 记忆蒸馏为 DISTILLED 摘要 |
| `.../api/MemoryVectorStore.java` | 接口 | Milvus 向量写入 / 删除 / 检索 |
| `.../api/ImportanceScorer.java` | 接口 | 5 维度加权评分（HIGH/MEDIUM/LOW） |
| `.../api/MemoryTtlManager.java` | 接口 | TTL 状态流转 + 过期清理 |
| `.../api/MemoryDeduper.java` | 接口 | 完全 hash / 余弦相似度去重 |
| `.../api/LongTermMemoryWriter.java` | 接口 | 长期记忆同步 MySQL + 异步 Milvus |
| `.../api/EmbeddingClient.java` | 接口 | agent-model-gateway 嵌入接口客户端 |
| `.../enums/MemoryType.java` | 枚举 | EPISODIC / SEMANTIC / PROCEDURAL / REFLECTIVE |
| `.../enums/MemoryStatus.java` | 枚举 | RAW / ACTIVE / DISTILLED / ARCHIVED |
| `.../enums/TaskOutcome.java` | 枚举 | SUCCESS / FAILURE / PARTIAL / TIMEOUT |
| `.../model/MemoryRecord.java` | POJO | 记忆记录模型（需升级为 JPA @Entity） |
| `.../model/ExtractedMemory.java` | POJO | 提取产物（type / content / metadata / sourceTaskId） |
| `.../model/EmbeddingVector.java` | POJO | 向量包装（vector / dimension / model） |
| `.../model/MemoryTopic.java` | POJO | 主题（topic / keywords / tenantId） |
| `.../model/TaskResult.java` | POJO | 任务结果输入（taskId / outcome / steps / summary） |

### 待新增文件（T1~T10）

| 文件 | Task | 职责 |
|---|---|---|
| `.../config/MemoryProperties.java` | T1 | `@ConfigurationProperties("memory")` 配置类 |
| `.../config/MilvusClientConfig.java` | T1 | Milvus 连接 + collection 自动建表 |
| `.../config/EmbeddingClientConfig.java` | T1 | RestTemplate / WebClient bean |
| `.../repository/MemoryRecordRepository.java` | T2 | JPA Repository（findByTenantIdAndStatus / findByTopic / findExpiredBefore） |
| `.../model/MemoryRecord.java`（升级） | T2 | 加 @Entity / @Table / 23 字段映射 + 索引 |
| `.../model/MemoryExtractLog.java` | T2 | 提取日志 @Entity |
| `.../extractor/MemoryExtractorImpl.java` | T3 | 实现 MemoryExtractor |
| `.../extractor/MemoryExtractRule.java` | T3 | 规则配置（type→matcher 映射） |
| `.../distiller/MemoryDistillerImpl.java` | T4 | 实现 MemoryDistiller |
| `.../distiller/DistillPromptBuilder.java` | T4 | 构造蒸馏 prompt（输入多条 ACTIVE） |
| `.../embedding/EmbeddingClientImpl.java` | T5 | 实现 EmbeddingClient（HTTP 调用 agent-model-gateway） |
| `.../embedding/EmbeddingRequestBuilder.java` | T5 | 构造嵌入请求体 |
| `.../vectorstore/MemoryVectorStoreImpl.java` | T6 | 实现 MemoryVectorStore（Milvus SDK） |
| `.../vectorstore/MilvusSchemaBuilder.java` | T6 | collection schema 构造 |
| `.../scorer/ImportanceScorerImpl.java` | T7 | 实现 ImportanceScorer |
| `.../scorer/ImportanceDimensions.java` | T7 | 5 维度评分模型 |
| `.../ttl/MemoryTtlManagerImpl.java` | T8 | 实现 MemoryTtlManager |
| `.../ttl/MemoryTtlScheduler.java` | T8 | @Scheduled 定时扫描过期 |
| `.../dedup/MemoryDeduperImpl.java` | T9 | 实现 MemoryDeduper |
| `.../dedup/MemoryHasher.java` | T9 | SHA-256 hash 工具 |
| `.../longterm/LongTermMemoryWriterImpl.java` | T10 | 实现 LongTermMemoryWriter |
| `.../grpc/MemoryServiceGrpcImpl.java` | T10 | MemoryService gRPC 4 RPC 实现 |
| `.../grpc/MemoryRecordMapper.java` | T10 | proto ↔ JPA 映射 |
| `.../grpc/GrpcExceptionAdvice.java` | T10 | gRPC Status 异常翻译 |

---

## Tasks

### Task T1: 骨架补全（pom + application.yml + 启动类）

**目标：** 把 16 个骨架文件外的 Maven / 配置 / 启动类补齐到可启动状态（不验证业务，仅校验 Spring Context 加载）。

**Red：**
- [ ] 在 `agent-memory/src/test/java/com/agent/memory/MemoryApplicationTest.java` 写 `contextLoads()` 测试，断言 `applicationContext.getBean(MemoryApplication.class) != null`。
- [ ] 在 `application-test.yml` 配置 H2 MySQL 模式数据源 + 关闭 Milvus 自动建表开关（`memory.milvus.enabled=false`）。
- [ ] 运行测试，预期失败（pom 缺依赖 / application.yml 空）。

**Green：**
- [ ] 补全 `pom.xml`：parent（agent-parent）/ web / data-jpa / validation / mysql-connector / grpc-starter / milvus-sdk / caffeine / lombok / jackson / test（junit5 + mockito + assertj + awaitility + h2 + testcontainers）。
- [ ] 补全 `application.yml`：`server.port=8088` / `grpc.server.port=9088` / `grpc.client.GLOBAL.negotiationType=PLAINTEXT` / `spring.datasource.url=jdbc:mysql://localhost:3306/agent_memory` / `spring.jpa.hibernate.ddl-mode=validate` / `memory.*` 配置项（对齐 doc 04 §13）。
- [ ] 补全 `MemoryApplication.java`：`@SpringBootApplication(scanBasePackages="com.agent.memory")` + `@EnableJpaRepositories` + `@EnableScheduling`。
- [ ] 新建 `MemoryProperties.java`：`@ConfigurationProperties(prefix="memory")`，字段对齐 §13 全部配置。
- [ ] 新建 `MilvusClientConfig.java`：`@Bean MilvusServiceClient`（host/port 来自配置），条件 `@ConditionalOnProperty(name="memory.milvus.enabled", havingValue="true")`。
- [ ] 新建 `EmbeddingClientConfig.java`：`@Bean WebClient`（base url = `memory.embedding.baseUrl=http://agent-model-gateway:8080`）。
- [ ] 运行 `contextLoads()`，预期通过。

**Refactor：**
- [ ] 拆分 `application.yml` 为 `application.yml`（默认）+ `application-dev.yml` + `application-test.yml`。
- [ ] `MemoryProperties` 抽取内部静态类 `Ttl` / `Recall` / `Distill` / `Dedup` / `Embedding` / `Milvus`。

**Commit：** `feat(agent-memory): T1 scaffold pom/yml/startup, context loads`

---

### Task T2: MemoryRecord JPA Entity + Repository

**目标：** 把 `MemoryRecord` POJO 升级为 JPA @Entity，对应 `memory_record` 表，并补 `MemoryExtractLog` 实体与 Repository。

**Red：**
- [ ] 在 `MemoryRecordRepositoryTest.java`（@DataJpaTest + H2 MySQL 模式）写：
  - `saveAndFindById` 保存一条记录后按 id 查询，断言字段完整。
  - `findByTenantIdAndStatus` 返回指定租户 + 状态的记忆列表。
  - `findByTopic` 按 topic 查询。
  - `findExpiredBefore` 查找过期时间早于入参的 ACTIVE 记录。
  - 测试预期失败（实体未加 @Entity）。

**Green：**
- [ ] 升级 `MemoryRecord.java`：加 `@Entity / @Table(name="memory_record", indexes={...})`，字段对齐 doc 01-database §2.1：
  - `id`（Long，@Id @GeneratedValue）
  - `memoryId`（String，UUID，唯一索引）
  - `tenantId`（String，索引）
  - `userId`（String，可空）
  - `type`（@Enumerated STRING，MemoryType）
  - `status`（@Enumerated STRING，MemoryStatus）
  - `content`（@Lob / TEXT）
  - `summary`（String，蒸馏后摘要）
  - `topic`（String，索引）
  - `keywords`（String，JSON 数组）
  - `sourceTaskId`（String）
  - `outcome`（@Enumerated STRING，TaskOutcome）
  - `importance`（Double）
  - `importanceLevel`（String，HIGH/MEDIUM/LOW）
  - `contentHash`（String，SHA-256，索引）
  - `vectorId`（String，Milvus 主键）
  - `parentMemoryId`（String，蒸馏来源）
  - `childMemoryIds`（String，JSON 数组）
  - `ttlExpireAt`（Instant）
  - `distillCount`（Integer）
  - `recallCount`（Integer）
  - `lastRecalledAt`（Instant）
  - `metadata`（String，JSON）
  - `createdAt` / `updatedAt`（Instant，@Version 乐观锁）
- [ ] 新建 `MemoryExtractLog.java` @Entity：id / taskId / extractCount / failedCount / durationMs / createdAt。
- [ ] 新建 `MemoryRecordRepository.java`：`findByMemoryId` / `findByTenantIdAndStatus` / `findByTopic` / `findExpiredBefore(Instant, Pageable)` / `countByTenantIdAndStatus`。
- [ ] 新建 `MemoryExtractLogRepository.java`。
- [ ] 运行 Repository 测试，全绿。

**Refactor：**
- [ ] 抽出 `BaseEntity`（id / createdAt / updatedAt / @Version）供两个实体继承。
- [ ] 字段命名采用 `@Column(name="snake_case")` 显式映射。

**Commit：** `feat(agent-memory): T2 MemoryRecord + MemoryExtractLog JPA entities`

---

### Task T3: MemoryExtractorImpl 提取实现

**目标：** 实现 `MemoryExtractor` 接口，从 `TaskResult` 与会话事件中提取 `ExtractedMemory` 列表。

**Red：**
- [ ] 在 `MemoryExtractorImplTest.java` 写：
  - `extractFromTaskResult_success` 输入 TaskResult（outcome=SUCCESS / 3 步骤 / summary），断言返回 ≥1 条 ExtractedMemory，type=PROCEDURAL。
  - `extractFromTaskResult_failure_yields_reflective` outcome=FAILURE，断言产出 type=REFLECTIVE。
  - `extractFromConversationEvent` 输入对话事件，断言产出 type=EPISODIC。
  - `extract_empty_when_no_signal` 输入无信号内容，断言返回空列表。
  - `extract_filters_low_quality` 输入噪声文本，断言被规则过滤。

**Green：**
- [ ] 新建 `MemoryExtractRule.java`：规则配置（`type` / `pattern` / `minContentLength` / `keywordSet`）。
- [ ] 实现 `MemoryExtractorImpl.java`：
  - `extractFromTaskResult(TaskResult)`：按 outcome 分流 → SUCCESS 提取 PROCEDURAL（步骤序列 + 关键决策）；FAILURE 提取 REFLECTIVE（失败原因 + 反思）；PARTIAL 两类都产；TIMEOUT 产 REFLECTIVE。
  - `extractFromConversationEvent(ConversationEvent)`：从用户消息 + Agent 回复提取 EPISODIC 记忆（人物 / 时间 / 事件 / 关键事实）。
  - `extractFromSemantic(SemanticChunk)`：从文档块提取 SEMANTIC 记忆。
  - 内容过滤：长度 < 20 字符 / 命中黑名单关键词 / 重复相同 → 过滤。
- [ ] 注入 `MemoryExtractRule` 列表（通过 `@Autowired List<MemoryExtractRule>`）。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 把规则配置外部化到 `memory.extract.rules.yml` 或 `@ConfigurationProperties`。
- [ ] 提取公共方法 `applyRules(String content, MemoryType type)` 复用过滤逻辑。

**Commit：** `feat(agent-memory): T3 MemoryExtractorImpl extracts episodic/semantic/procedural/reflective`

---

### Task T4: MemoryDistillerImpl 摘要实现

**目标：** 实现 `MemoryDistiller` 接口：将同 tenantId + topic 下 ≥20 条 ACTIVE 记忆蒸馏为 1 条 DISTILLED 摘要记录。

**Red：**
- [ ] 在 `MemoryDistillerImplTest.java` 写：
  - `distill_whenActiveCountExceedsThreshold` 输入 25 条 ACTIVE，断言产出 1 条 DISTILLED（content=摘要 / parentMemoryIds=25 条 / source 记录 status 改为 ARCHIVED）。
  - `distill_skipWhenBelowThreshold` 输入 15 条 ACTIVE，断言抛 `MemoryDistillSkippedException` 或返回 Optional.empty()。
  - `distill_promptContainsAllInputs` 验证 prompt 包含全部 25 条 content（通过 ArgumentCaptor）。
  - `distill_failureDoesNotDegradeActive` 调用模型失败时，源 ACTIVE 记录状态不变。
  - `distill_assignsHighImportanceWhenAggregatedImportanceHigh` 聚合 importance 平均值 ≥0.7 时，DISTILLED 记忆 importance=HIGH。

**Green：**
- [ ] 新建 `DistillPromptBuilder.java`：构造系统 prompt（"请将以下 N 条 ACTIVE 记忆蒸馏为 1 条不超过 500 字的摘要..."）+ 用户 prompt（拼接 source contents）。
- [ ] 实现 `MemoryDistillerImpl.java`：
  - `distill(String tenantId, String topic)`：
    1. 查询 `findByTenantIdAndStatus(tenantId, ACTIVE)` 过滤 topic。
    2. 数量 < `memory.distill.triggerCount`（20）→ 跳过。
    3. 调用 `EmbeddingClient` 之外的模型 chat 接口（注入 `ModelGatewayClient`，端口 8080）。
    4. 蒸馏 prompt → 模型 → 摘要文本。
    5. 创建新 `MemoryRecord`，type=原 type 主导 / status=DISTILLED / content=摘要 / parentMemoryIds=原 ids / importance=聚合均值。
    6. 原 ACTIVE 记录 status→ARCHIVED（事务一致性）。
    7. 写 `MemoryExtractLog`。
  - `distillBatch(tenantId)`：遍历所有 topic 批量蒸馏。
- [ ] 注入 `ModelGatewayClient`（gRPC stub，调用 ChatCompletion）。
- [ ] 注入 `MemoryRecordRepository`。
- [ ] 异常处理：模型调用失败 → 抛 `MemoryDistillFailedException`，事务回滚（ACTIVE 状态不降级）。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 拆 `distill()` 为 `collectActiveMemories()` / `callDistillModel()` / `persistDistilledMemory()` / `archiveSourceMemories()` 四个私有方法。
- [ ] 加 `@Transactional` 边界，确保原子性。

**Commit：** `feat(agent-memory): T4 MemoryDistillerImpl summarizes ACTIVE memories via model gateway`

---

### Task T5: EmbeddingClientImpl 向量客户端

**目标：** 实现 `EmbeddingClient` 接口：调用 agent-model-gateway 的 `/v1/embeddings` 接口获取 1024 维向量。

**Red：**
- [ ] 在 `EmbeddingClientImplTest.java` 写（MockWebServer 模拟 gateway）：
  - `embed_single_returns1024DimVector` 单条文本，断言返回 float[1024]。
  - `embed_batch_returnsList` 批量 3 条，断言返回 List<float[]> size=3。
  - `embed_retryOnTimeout` MockWebServer 第一次 504 / 第二次 200，断言重试 1 次后成功。
  - `embed_throwOnMaxRetryExceeded` 3 次全 504，断言抛 `EmbeddingServiceFailureException`。
  - `embed_sendTenantIdHeader` 验证请求头包含 `X-Tenant-Id`。
  - `embed_emptyInput_returnsEmptyList` 边界 case。

**Green：**
- [ ] 新建 `EmbeddingRequestBuilder.java`：构造 OpenAI 兼容请求体 `{"model":"text-embedding-v3","input":[...]}`。
- [ ] 实现 `EmbeddingClientImpl.java`：
  - `embed(String text, String tenantId)`：POST `/v1/embeddings`，解析 `data[0].embedding` 转 float[]。
  - `embedBatch(List<String>, String tenantId)`：单请求并发 + 解析多条。
  - 重试：3 次指数退避（100/300/1000 ms），用 Spring Retry 或手写。
  - 超时：连接 2s / 读取 10s。
  - Header：`Authorization: Bearer ${memory.embedding.apiKey}` / `X-Tenant-Id`。
- [ ] 注入 `WebClient` + `MemoryProperties`。
- [ ] 异常：网关非 2xx → `EmbeddingServiceFailureException`（含 status / body）。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 把请求体解析抽 `EmbeddingResponseParser`。
- [ ] 加 Caffeine 本地缓存（key=text hash，TTL 1h），避免重复调用。

**Commit：** `feat(agent-memory): T5 EmbeddingClientImpl calls model gateway with retry + cache`

---

### Task T6: MemoryVectorStoreImpl Milvus 存储

**目标：** 实现 `MemoryVectorStore` 接口：Milvus collection 写入 / 删除 / top-K 检索。

**Red：**
- [ ] 在 `MemoryVectorStoreImplTest.java` 写（用 `@MockBean MilvusServiceClient` mock，或 Testcontainers 启动 Milvus）：
  - `insert_single_returnsVectorId` 写入一条，断言返回非空 vectorId。
  - `insert_batch_returnsList` 批量 5 条，断言 size=5。
  - `delete_byMemoryId` 删除指定 memory_id 的向量。
  - `search_topK_returnsRankedResults` 检索 topK=10，断言返回结果按 score 降序。
  - `search_filterByTenantAndStatus` 验证过滤表达式 `tenant_id == "t1" && status == "ACTIVE"`。
  - `search_scoreBelowThreshold_filtered` score < 0.75 的结果被过滤。

**Green：**
- [ ] 新建 `MilvusSchemaBuilder.java`：构造 collection schema（id INT64 PK / tenant_id VARCHAR / memory_id VARCHAR / vector FLOAT_VECTOR 1024 / importance FLOAT / status VARCHAR / created_at INT64 / vector_index_param IVF_FLAT nlist=1024 COSINE）。
- [ ] 实现 `MemoryVectorStoreImpl.java`：
  - `init()`（@PostConstruct）：若 `memory.milvus.autoCreateCollection=true` 且 collection 不存在 → 创建 + 建索引 + load。
  - `insert(MemoryRecord, float[] vector)`：构造 InsertParam.Field 列表 → milvusClient.insert() → 返回 vectorId（用 milvus 返回的 id 或自生成 UUID）。
  - `insertBatch(List<MemoryRecord>, List<float[]>)`：批量。
  - `delete(String memoryId)`：`delete >>= memory_id in ["..."]`。
  - `search(float[] queryVector, String tenantId, int topK, double scoreThreshold, MemoryStatus... statuses)`：构造 SearchParam（topK + filter 表达式 + metric COSINE）→ 调用 → 解析 score（Milvus 返回 distance，COSINE 距离需转换为相似度）→ 过滤 score ≥ threshold → 排序。
- [ ] 注入 `MilvusServiceClient` + `MemoryProperties`。
- [ ] 异常：Milvus 异常 → `MemoryVectorStoreFailureException`。
- [ ] 运行测试，全绿（Testcontainers 路径或 Mock 路径都过）。

**Refactor：**
- [ ] 抽 `MilvusFilterBuilder` 构造 filter 表达式（`tenant_id == "x" && status in [...]`）。
- [ ] 抽 `MilvusResponseMapper` 把 Milvus QueryResults 转 `MemoryVectorSearchHit`。

**Commit：** `feat(agent-memory): T6 MemoryVectorStoreImpl milvus insert/delete/search`

---

### Task T7: ImportanceScorerImpl 评分

**目标：** 实现 `ImportanceScorer` 接口：5 维度加权评分 → HIGH/MEDIUM/LOW 三档。

**Red：**
- [ ] 在 `ImportanceScorerImplTest.java` 写：
  - `score_allHighDimensions_returnsHigh` 5 维度都=1.0，断言 score≥0.7 且 level=HIGH。
  - `score_allMedium_returnsMedium` 5 维度都=0.5，断言 level=MEDIUM。
  - `score_allLow_returnsLow` 5 维度都=0.1，断言 level=LOW。
  - `score_weightsSumToOne` 验证权重和=1.0（0.20+0.25+0.20+0.25+0.10）。
  - `score_timeDecayReducesScore` 同样输入，时间从今天变到 30 天前，score 下降。
  - `score_taskRelevanceBoosts` 关键词命中任务上下文 → 相关性维度提升。

**Green：**
- [ ] 新建 `ImportanceDimensions.java`：5 维度（emotionIntensity / frequency / novelty / taskRelevance / timeDecay），各 0~1。
- [ ] 实现 `ImportanceScorerImpl.java`：
  - `score(MemoryRecord record, ScoringContext ctx)`：
    1. emotionIntensity：从 metadata 提取情感分数（如无则默认 0.5）。
    2. frequency：从 `findByTenantIdAndTopicAndContentHash` 计数 → 频率分（min(count/10, 1.0)）。
    3. novelty：与同 topic 最近 5 条做余弦相似度均值 → 1 - avg。
    4. taskRelevance：关键词命中率（context.taskKeywords ∩ record.keywords）。
    5. timeDecay：`exp(-Δt/30d)`（30 天衰减到 1/e）。
  - 加权求和 → score。
  - 阈值映射：≥0.7 HIGH / 0.4~0.7 MEDIUM / <0.4 LOW。
- [ ] 注入 `MemoryRecordRepository` + `EmbeddingClient`（用于 novelty 余弦计算，需要 vector）。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 把权重抽到 `MemoryProperties.Scorer.weights`（可配置）。
- [ ] 抽 `TimeDecayFunction` 工具类。

**Commit：** `feat(agent-memory): T7 ImportanceScorerImpl 5-dimension weighted scoring`

---

### Task T8: MemoryTtlManagerImpl TTL 管理

**目标：** 实现 `MemoryTtlManager` 接口 + 定时调度：自动流转 RAW→ACTIVE→DISTILLED→ARCHIVED + 过期清理。

**Red：**
- [ ] 在 `MemoryTtlManagerImplTest.java` 写：
  - `transition_rawToActive_immediate` 新建 RAW 记录，调用 `applyTtl(record)`，断言 status=ACTIVE。
  - `transition_activeToDistilled_after7d` ttlExpireAt=7 天前，断言触发蒸馏（mock distiller）。
  - `transition_distilledToArchived_after30d` ttlExpireAt=30 天前，断言 status→ARCHIVED。
  - `purgeExpiredLowArchived_after3d` LOW 归档 3 天后 → 调用 `delete(record)`。
  - `scheduledScan_picksUpExpired` 注入测试时钟（Clock）+ 调用 `scheduledScan()`，断言扫到 1 条过期。
  - `transition_idempotent` 重复调用不重复触发。

**Green：**
- [ ] 实现 `MemoryTtlManagerImpl.java`：
  - `applyTtl(MemoryRecord)`：根据当前状态 + ttlExpireAt 决定下一步动作。
    - RAW → ACTIVE：立即（创建时即调用，ttlExpireAt 设为下个阶段时间）。
    - ACTIVE → DISTILLED：到期触发 `MemoryDistiller.distill(tenantId, topic)`，成功后 status=DISTILLED，重设 ttlExpireAt=+30d。
    - DISTILLED → ARCHIVED：到期直接 status=ARCHIVED。
    - ARCHIVED + LOW + 超过 3 天 → 物理删除（同时删 Milvus 向量）。
  - `scheduledScan()`（@Scheduled fixedDelay=1h）：分页扫描 `findExpiredBefore(now)` → 逐条 `applyTtl`。
  - 注入 `MemoryDistiller` / `MemoryVectorStore` / `MemoryRecordRepository` / `Clock`（可注入测试用）。
- [ ] 新建 `MemoryTtlScheduler.java`：`@Scheduled(fixedDelayString="${memory.ttl.scanIntervalMs:3600000}")` 调用 `ttlManager.scheduledScan()`。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 抽 `TtlTransitionRule` 决策表（from / condition / action）。
- [ ] 加 `@Transactional` 边界 + 锁（防并发扫描重复处理同一条）。

**Commit：** `feat(agent-memory): T8 MemoryTtlManagerImpl ttl transitions + scheduled purge`

---

### Task T9: MemoryDeduperImpl 去重

**目标：** 实现 `MemoryDeduper` 接口：完全 hash 去重 + 余弦相似度合并。

**Red：**
- [ ] 在 `MemoryDeduperImplTest.java` 写：
  - `dedup_exactHashMatch_dropsDuplicate` 输入 2 条 content 相同的记录，第 2 条被丢弃。
  - `dedup_cosineHigh_mergesKeepingHighImportance` 余弦 ≥0.95，合并保留 importance 高的，content 拼接。
  - `dedup_cosineMid_marksRelated` 余弦 0.85~0.95，标记关联（childMemoryIds 追加）。
  - `dedup_belowThreshold_keepsAll` 余弦 <0.85，两条都保留。
  - `dedup_idempotent` 重复调用相同输入不产生额外变更。
  - `dedup_batch_returnsReport` 返回 DedupReport（dropped / merged / related / kept）。

**Green：**
- [ ] 新建 `MemoryHasher.java`：`sha256(String content)` 返回 hex 字符串。
- [ ] 实现 `MemoryDeduperImpl.java`：
  - `dedup(List<MemoryRecord> batch)`：
    1. 计算 hash → 同 hash 分组，组内只保留 createdAt 最早的，其余丢弃（dropped）。
    2. 对剩余记录两两计算余弦相似度（用 `EmbeddingClient.embed`，单条已缓存则复用）。
    3. ≥0.95：合并（content 拼接 / keywords 合集 / importance 取大 / 标记 parentMemoryIds）。
    4. 0.85~0.95：标记 relatedMemoryIds 双向追加。
    5. <0.85：保留。
    6. 返回 `DedupReport(dropped, merged, related, kept)`。
- [ ] 注入 `EmbeddingClient` + `MemoryRecordRepository`。
- [ ] 余弦相似度计算用 Apache Commons Math `CosineSimilarity` 或自实现（向量点积 / 模长）。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 批量获取 embedding 减少调用：`embedBatch(batch.map(content))`。
- [ ] 阈值抽到 `MemoryProperties.Dedup`。

**Commit：** `feat(agent-memory): T9 MemoryDeduperImpl hash + cosine deduplication`

---

### Task T10: LongTermMemoryWriterImpl + MemoryService gRPC 服务 + 集成测试

**目标：** 实现 LongTermMemoryWriter（长期写入路径）+ MemoryService gRPC 4 RPC + 端到端集成测试。

**Red：**
- [ ] 在 `MemoryServiceGrpcImplTest.java`（@SpringBootTest + gRPC in-process server）写：
  - `storeMemory_persistsToMySQLAndMilvus` 调用 StoreMemory RPC，断言 MySQL 落库 + Milvus 写入（mock）+ 返回 memory_id。
  - `storeMemory_appliesDedupAndImportance` StoreMemory 流程内自动 dedup + score。
  - `recallMemory_returnsTopKByVectorAndImportance` RecallMemory 输入 query → 返回 topK=10 按综合分排序。
  - `recallMemory_filtersByTenant` 跨租户不返回。
  - `distillMemory_triggersDistiller` DistillMemory RPC 触发蒸馏。
  - `getMemoryStats_returnsCountsByStatus` GetMemoryStats 返回各状态计数。
  - `storeMemory_invalidInput_throwsInvalidArgument` 校验失败 → INVALID_ARGUMENT。
  - `recallMemory_notFound_returnsEmpty` 查无结果 → 返回空列表（非错误）。

**Green：**
- [ ] 实现 `LongTermMemoryWriterImpl.java`：
  - `write(ExtractedMemory extracted, String tenantId)`：
    1. 构造 MemoryRecord（status=RAW / type / content / topic / sourceTaskId）。
    2. 计算 contentHash。
    3. 调用 `MemoryDeduper.dedup([newRecord] + 同 hash 已有记录)` → 若被丢弃则返回空。
    4. 调用 `ImportanceScorer.score(record, ctx)` → 写入 importance / level。
    5. 调用 `EmbeddingClient.embed(content, tenantId)` → 拿 vector。
    6. 同步：`MemoryRecordRepository.save(record)`（status=ACTIVE，applyTtl 已设）。
    7. 异步：`MemoryVectorStore.insert(record, vector)` → 失败重试 3 次，仍失败抛 `MemoryVectorStoreFailureException` 并标记 `record.vectorId=null` + 留待后台补偿。
    8. 返回 memoryId。
  - `writeBatch(List<ExtractedMemory>, tenantId)`：批量版本，先 dedup 整批 → 批量 embed → 批量 save + insert。
- [ ] 实现 `MemoryRecordMapper.java`：proto `Memory` ↔ JPA `MemoryRecord` 字段映射。
- [ ] 实现 `GrpcExceptionAdvice.java`（@ControllerAdvice 或 grpc interceptor）：
  - `BusinessException` → 对应 Status（按 ErrorCode 映射：MEMORY_NOT_FOUND→NOT_FOUND / MEMORY_DUPLICATE→ALREADY_EXISTS / EMBEDDING_SERVICE_FAILURE→UNAVAILABLE / 其他→INTERNAL with code）。
  - `IllegalArgumentException` → INVALID_ARGUMENT。
  - `MethodArgumentNotValidException` → INVALID_ARGUMENT。
- [ ] 实现 `MemoryServiceGrpcImpl.java`（`@GrpcService extends MemoryServiceGrpc.MemoryServiceImplBase`）：
  - `storeMemory(StoreMemoryRequest, StreamObserver<StoreMemoryResponse>)`：
    1. 校验 tenantId / content 非空（否则 INVALID_ARGUMENT）。
    2. 构造 `TaskResult` / `ConversationEvent` → `MemoryExtractor.extract(...)` → List<ExtractedMemory>。
    3. 对每条调 `LongTermMemoryWriter.write(...)`。
    4. 汇总 memoryIds → response。
  - `recallMemory(RecallMemoryRequest, StreamObserver<RecallMemoryResponse>)`：
    1. 校验 tenantId / query 非空。
    2. `EmbeddingClient.embed(query, tenantId)` → queryVector。
    3. `MemoryVectorStore.search(queryVector, tenantId, topK, scoreThreshold, ACTIVE, DISTILLED)` → hits。
    4. 按 `score * (0.5 + 0.5 * importance)` 综合排序。
    5. 取 topK → 映射为 proto Memory 列表 → response。
  - `distillMemory(DistillMemoryRequest, StreamObserver<DistillMemoryResponse>)`：
    1. 校验 tenantId / topic。
    2. `MemoryDistiller.distill(tenantId, topic)` → distilled MemoryRecord。
    3. 返回 distilled_id / source_count。
  - `getMemoryStats(GetMemoryStatsRequest, StreamObserver<GetMemoryStatsResponse>)`：
    1. 校验 tenantId。
    2. `repository.countByTenantIdAndStatus(tenantId, eachStatus)` → 计数表。
    3. 返回 stats。
- [ ] 运行全部 8 个测试，全绿。

**Refactor：**
- [ ] 把 `storeMemory` 拆 `extract` / `writeEach` / `buildResponse` 三步。
- [ ] 把校验逻辑抽 `MemoryRequestValidator`。
- [ ] RecallMemory 综合分排序抽 `MemoryRanker`。

**Commit：** `feat(agent-memory): T10 LongTermMemoryWriter + MemoryService gRPC + integration tests`

---

## 测试矩阵

### 单元测试（UT-MEM-001~015，参照 `docs/tests/unit-test-cases.md` §7）

| 用例 ID | 模块 | 测试方法 | 关键断言 |
|---|---|---|---|
| UT-MEM-001 | T2 | `MemoryRecordRepositoryTest.findByTenantIdAndStatus` | 返回正确租户 + 状态的记录 |
| UT-MEM-002 | T3 | `MemoryExtractorImplTest.extractFromTaskResult_success` | SUCCESS→PROCEDURAL 记忆 |
| UT-MEM-003 | T3 | `MemoryExtractorImplTest.extractFromTaskResult_failure_yields_reflective` | FAILURE→REFLECTIVE 记忆 |
| UT-MEM-004 | T4 | `MemoryDistillerImplTest.distill_whenActiveCountExceedsThreshold` | ≥20 条触发蒸馏产 DISTILLED |
| UT-MEM-005 | T4 | `MemoryDistillerImplTest.distill_failureDoesNotDegradeActive` | 模型失败时 ACTIVE 不变 |
| UT-MEM-006 | T5 | `EmbeddingClientImplTest.embed_retryOnTimeout` | 504 重试 1 次后成功 |
| UT-MEM-007 | T5 | `EmbeddingClientImplTest.embed_throwOnMaxRetryExceeded` | 3 次 504 → EmbeddingServiceFailureException |
| UT-MEM-008 | T6 | `MemoryVectorStoreImplTest.search_topK_returnsRankedResults` | topK=10 按 score 降序 |
| UT-MEM-009 | T7 | `ImportanceScorerImplTest.score_allHighDimensions_returnsHigh` | 5 维度 1.0 → HIGH |
| UT-MEM-010 | T7 | `ImportanceScorerImplTest.score_weightsSumToOne` | 权重和=1.0 |
| UT-MEM-011 | T8 | `MemoryTtlManagerImplTest.transition_activeToDistilled_after7d` | 7 天后触发蒸馏 |
| UT-MEM-012 | T9 | `MemoryDeduperImplTest.dedup_exactHashMatch_dropsDuplicate` | 相同 hash 丢弃 |
| UT-MEM-013 | T9 | `MemoryDeduperImplTest.dedup_cosineHigh_mergesKeepingHighImportance` | ≥0.95 合并 |
| UT-MEM-014 | T10 | `MemoryServiceGrpcImplTest.storeMemory_persistsToMySQLAndMilvus` | 双写成功 |
| UT-MEM-015 | T10 | `MemoryServiceGrpcImplTest.recallMemory_returnsTopKByVectorAndImportance` | topK 综合排序 |

### 集成测试（IT-MEM-001~005）

| 用例 ID | Task | 测试场景 | 依赖 |
|---|---|---|---|
| IT-MEM-001 | T10 | StoreMemory → RecallMemory 闭环：写入 5 条 → 召回 topK=3 验证返回 | H2 + Mock Milvus |
| IT-MEM-002 | T10 | StoreMemory → DistillMemory → RecallMemory 验证蒸馏后召回 | H2 + Mock Milvus |
| IT-MEM-003 | T8 | TTL 调度：插入 ACTIVE 7 天前 → 触发 scan → 状态流转 | H2 + Mock Distiller |
| IT-MEM-004 | T9 | 批量写入 50 条含 5 对重复 → DedupReport 数字正确 | H2 + Mock Embedding |
| IT-MEM-005 | T10 | GetMemoryStats 多状态计数 | H2 |

### 端到端测试（E2E-MEM-001~003，可选 Testcontainers）

| 用例 ID | 场景 | 依赖 |
|---|---|---|
| E2E-MEM-001 | Testcontainers MySQL + Milvus 启动 → StoreMemory → RecallMemory 真实链路 | Docker |
| E2E-MEM-002 | 真实 agent-model-gateway mock（WireMock）→ 蒸馏 + 嵌入链路 | WireMock + Docker |
| E2E-MEM-003 | TTL 调度跨夜场景（@DirtiesContext + 时钟注入）| H2 |

---

## 验收标准

### 1. 代码质量
- [ ] JaCoCo 行覆盖 ≥80%
- [ ] JaCoCo 分支覆盖 ≥70%
- [ ] 关键类（`MemoryServiceGrpcImpl` / `LongTermMemoryWriterImpl` / `MemoryVectorStoreImpl`）行覆盖 ≥85%
- [ ] Checkstyle / SpotBugs 无 ERROR 级别问题
- [ ] 无 `TODO` / `FIXME` 残留（除明确登记）

### 2. 功能完整性
- [ ] 4 RPC（StoreMemory / RecallMemory / DistillMemory / GetMemoryStats）全部实现并单测覆盖
- [ ] 8 API 实现（Extractor / Distiller / VectorStore / Scorer / TtlManager / Deduper / LongTermWriter / EmbeddingClient）全部覆盖
- [ ] TTL 定时调度可启停（配置开关）
- [ ] 蒸馏事务原子性（失败回滚）
- [ ] 去重幂等
- [ ] 异常分级 + 三级重试（嵌入服务 / Milvus 写入）
- [ ] 错误码全部对齐 doc 04-memory §12.4

### 3. 性能基线
- [ ] StoreMemory 单次 P99 < 500ms（不含模型网关耗时）
- [ ] RecallMemory topK=10 P99 < 200ms（Milvus 本地）
- [ ] EmbeddingClient 单条 P99 < 300ms（模型网关 mock 下）
- [ ] DistillMemory 20 条源 P99 < 3s（模型网关 mock 下）

### 4. 文档对齐
- [ ] doc 04-memory §3-§13 所有锁定值在代码中可追溯
- [ ] `docs/tests/unit-test-cases.md` §7 UT-MEM-001~015 全绿
- [ ] ADR-002（Milvus vs pgvector）选择已落地
- [ ] v5 审核 §6 P6-6 整改项关闭

### 5. 可观测性
- [ ] 关键路径加 Micrometer 指标（`memory.store.count` / `memory.recall.latency` / `memory.distill.count` / `memory.vector.insert.failure`）
- [ ] 关键路径加 Structured Logging（tenantId / memoryId / sourceTaskId 全链路）
- [ ] Actuator `/actuator/health` 包含 Milvus 健康检查

---

## Self-review checklist

执行计划前请确认：

- [ ] 已读 doc 04-memory 全文（§1-§13）+ doc 01-database §2.1（memory_record 表）+ doc 00-overview §3.1（端口）
- [ ] 已读 `agent-proto/src/main/proto/memory.proto`，4 RPC 请求/响应字段已确认
- [ ] 已读 `agent-common` 的 `MemoryType` / `MemoryStatus` / `ErrorCode` / `BusinessException` 与本模块枚举一致
- [ ] Milvus SDK 版本（2.3.4）与 docker-compose 中 Milvus 版本对齐（doc 00-overview §6）
- [ ] agent-model-gateway 已提供 `/v1/embeddings`（model=text-embedding-v3, dim=1024）+ `/v1/chat/completions`（distill 用）
- [ ] 与 Plan 04 task-orchestrator 协调：orchestrator 在 SUBTASK_RUNNING 完成后会调 `MemoryService.StoreMemory`，请求体字段已对齐
- [ ] 与 Plan 06 agent-runtime 协调：runtime 在 ReAct Loop 中会调 `MemoryService.RecallMemory` 拿历史经验注入 prompt
- [ ] T2 实体字段顺序与 DDL（doc 01-database §2.1）一致，避免漏字段
- [ ] T6 Milvus collection 字段类型与文档 §7.3 一致（vector FLOAT_VECTOR 1024 / id INT64 PK）
- [ ] T8 TTL 调度时间窗口（默认 1h）不与 orchestrator 高峰期冲突，可配置
- [ ] T10 异常映射表覆盖 doc 04 §12.4 全部错误码
- [ ] JaCoCo 配置已在 `agent-memory/pom.xml` 中开启（`prepare-agent` + `report`）
- [ ] 集成测试默认用 H2 MySQL 模式，Testcontainers 路径用 `@EnabledIfEnvironmentVariable` 守护
- [ ] 全部 commit message 遵循 `feat(agent-memory): TX ...` 格式
- [ ] 全部 task 完成后跑 `mvn clean verify` 全绿 + JaCoCo 报告达标
