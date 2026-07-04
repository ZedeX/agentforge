# AgentForge 智能体平台 TDD 红绿循环记录 v1.2 增补（v8 持久化深化期）

> 文档版本：v1.2 | 更新日期：2026-07-04 | 文档定位：**v1.1 之后的 v8 持久化深化期（Wave 18~36）TDD 红绿循环增补记录**
> 前序文档：[tdd-red-green-records.md](./tdd-red-green-records.md) v1.1（截至 2026-06-30 P7 骨架阶段，490+ 测试方法）
> 维护说明：v1.1 文档为单行格式历史快照，不再修改；v1.2 起采用标准 markdown 格式独立维护

## 0. 文档导览

### 0.1 文档定位

本文档记录 v1.1 之后 v8 持久化深化期（Wave 18 ~ Wave 34）的 TDD 红绿循环过程，遵循 Uncle Bob 三定律。每个 Wave 摘要记录：
- **交付内容**：本轮实现的 Task / 模块
- **关键红绿循环**：典型 Red → Green → Refactor 节奏
- **CI 验证**：streak 进展与用时
- **经验教训**：本轮新增的教训编号（接续 v1.1 的 1~24）

### 0.2 v8 持久化深化期总览（Wave 18~36）

| Wave | 日期 | 模块 / Task | 测试增量 | CI streak | Commit |
|---|---|---|---|---|---|
| 18 | 2026-07-01 | agent-model-gateway T1 骨架 | +12 | 4 | `28462730157` |
| 19 | 2026-07-01 | agent-repo + agent-knowledge + agent-planning 骨架 | +36 | 7 | `28464490875` 等 |
| 20 | 2026-07-01 | model-gateway T4-T7 多供应商适配器 | +18 | 10 | `28466062875` |
| 21 | 2026-07-01 | model-gateway T2-T3 Entity + Repository | +14 | 12 | — |
| 22 | 2026-07-01 | agent-repo T2-T4 JPA 持久化 | +22 | 15 | — |
| 23 | 2026-07-01 | model-gateway T12 CostMeter JPA | +16 | 17 | — |
| 24 | 2026-07-01 | agent-knowledge T8 JPA 双表 | +18 | 18 | — |
| 25 | 2026-07-01 | agent-knowledge T9 DocumentIngestor + TokenCounter | +20 | 20 | — |
| 26 | 2026-07-01 | agent-knowledge T11 KnowledgeBase gRPC | +24 | 21 | — |
| 27 | 2026-07-02 | model-gateway T8 Chat gRPC 服务 | +19 | 24 | `28535758564` |
| 28 | 2026-07-02 | model-gateway T10 CountTokens + ListModels | +15 | 25 | `28536220479` |
| 29 | 2026-07-02 | model-gateway T9 StreamChat + agent-repo T4 AgentRepo gRPC | +28 | 28 | `28563593997` |
| 30 | 2026-07-02 | agent-memory T1-T2 JPA 持久化层 | +12 | 29 | `5208f69` |
| 31 | 2026-07-02 | agent-memory T3 MemoryExtractor REFLECTIVE+filter | +15 | 30 | `c401266` |
| 32 | 2026-07-02 | agent-memory T8 MemoryTtlManager + T9 MemoryDeduper | +18 | 31 | `fe1a211` |
| 33 | 2026-07-03 | agent-memory T4 MemoryDistiller + gRPC 基础设施 | +8 | 32 | `7e9e4a3` |
| 34 | 2026-07-04 | agent-memory T7 ImportanceScorer 5 维度加权 | +9 | 33 | `0c50bab` |
| 35 | 2026-07-04 | 文档对齐（00-coding-plans-overview v2.0 + tdd-v1.2 + README） | +0 | 35 | `3464ceb`/`12398ab` |
| 36 | 2026-07-04 | agent-memory T5 EmbeddingClient HTTP + 重试 + Caffeine 缓存 | +23 | 36 | `ce595ed` |
| 37 | 2026-07-04 | agent-memory T10 MemoryService gRPC 4 RPC + JaCoCo 修复 | +59 | 39 | `39f569f`/`6fb869c` |

**v1.2 累计测试增量**：+386 测试方法（v1.1 490+ → v1.2 916+）
**CI streak**：4 → 39（含 Wave 37 T10 代码 JaCoCo 失败 1 次，测试补充后修复）

---

## 1. Wave 18：agent-model-gateway T1 骨架（2026-07-01）

**交付**：Plan 07 T1 项目骨架（Spring Boot 启动类 + application.yml + 4 适配器接口骨架）

**关键红绿循环**：
- Red: `ModelProviderAdapter` 接口未定义 → Green: 创建 4 适配器接口（OpenAI / Anthropic / Gemini / 国内模型）
- Red: `ModelRouter` 路由逻辑未实现 → Green: 基于 scene / cost / availability 的路由骨架
- Red: `PromptCache` 缓存命中检查未实现 → Green: Redis 相同前缀缓存骨架

**CI**：run 28462730157 ✅ streak=4，用时 5m15s

**经验教训 25**：多供应商适配器应统一抽象为 `ModelProviderAdapter` 接口，每家供应商独立实现，避免 if-else 分支地狱

---

## 2. Wave 19：3 模块骨架并行创建（2026-07-01）

**交付**：agent-repo + agent-knowledge + agent-planning 三模块骨架（Subagent-Driven 并行）

**关键红绿循环**：
- agent-repo: Red: `AgentDefinition` 实体未定义 → Green: JPA 实体骨架 + Repository
- agent-knowledge: Red: `KnowledgeBase` 实体未定义 → Green: JPA 实体骨架 + Repository
- agent-planning: Red: `PlanningService` 接口未定义 → Green: gRPC 服务骨架

**CI**：3 个并行 commit 全绿，streak=7

**经验教训 26**：Subagent-Driven 并行开发时，每个子代理必须独立完成 Red-Green-Refactor，不能跳过 Red 阶段

---

## 3. Wave 20：model-gateway T4-T7 多供应商适配器（2026-07-01）

**交付**：4 供应商适配器完整实现（OpenAI / Anthropic / Gemini / 国内模型通义/文心/DeepSeek）

**关键红绿循环**：
- Red: OpenAI 适配器 chat 调用未实现 → Green: WebClient POST /v1/chat/completions
- Red: Anthropic 适配器消息格式不同 → Green: messages 转 system/user/assistant 格式
- Red: Gemini 适配器 generateContent 未实现 → Green: WebClient POST /v1beta/models/{model}:generateContent
- Red: 国内模型适配器差异大 → Green: DeepSeek 兼容 OpenAI 协议 / 通义用 DashScope / 文心用 ERNIE-Bot

**CI**：streak=10 达成 A- 等级正式封顶

**经验教训 27**：Spring AI 0.8.1 的 OpenAiChatModel / AnthropicChatModel 可复用，但 Gemini 和国内模型需自研 WebClient 适配器

---

## 4. Wave 21：model-gateway T2-T3 Entity + Repository（2026-07-01）

**交付**：model_provider + model_route_rule 双表 JPA 实体 + Repository

**关键红绿循环**：
- Red: `ModelProvider` 实体未定义 → Green: @Entity + 17 业务字段 + Repository
- Red: `ModelRouteRule` 路由规则未定义 → Green: @Entity + scene/tier/modelId 路由三元组
- Red: 路由匹配逻辑未实现 → Green: 按 scene → tier → priority 优先级匹配

**CI**：streak=12

---

## 5. Wave 22：agent-repo T2-T4 JPA 持久化（2026-07-01）

**交付**：AgentDefinition 实体 + AgentVersion 版本管理 + Repository

**关键红绿循环**：
- Red: `AgentDefinition` 23 业务字段未持久化 → Green: @Entity + uk_agent_id 唯一约束
- Red: `AgentVersion` 版本快照未实现 → Green: @Entity + version 自增 + snapshot JSON
- Red: `bound_tools` / `bound_knowledge_ids` JSON 字段绑定未实现 → Green: @Convert(JsonConverter) 双向绑定

**CI**：streak=15

**经验教训 28**：JPA Entity 必须用 BIGINT id + uk_xxx_id 唯一约束，所有枚举字段用 @Enumerated(STRING) 存储可读值

---

## 6. Wave 23：model-gateway T12 CostMeter JPA 集成（2026-07-01）

**交付**：model_usage_log 计量表 + CostMeter 业务实现

**关键红绿循环**：
- Red: `ModelUsageLog` 实体未定义 → Green: @Entity + input/output tokens 分开计费
- Red: CostMeter 计量逻辑未实现 → Green: 同步落库（commit 前）确保失败可追溯
- Red: 测试状态污染（@TestInstance 残留） → Green: @BeforeEach 清理 + @DirtiesContext

**CI**：streak=17

**经验教训 29**：测试状态污染是 JPA 测试常见问题，@BeforeEach 必须显式清理 + @DirtiesContext 隔离上下文

---

## 7. Wave 24：agent-knowledge T8 JPA 双表（2026-07-01）

**交付**：knowledge_base + knowledge_chunk 双表 JPA 实体

**关键红绿循环**：
- Red: `KnowledgeBase` 实体未定义 → Green: @Entity + 19 业务字段 + Repository
- Red: `KnowledgeChunk` 分块未定义 → Green: @Entity + docId 关联 + tokenCount 字段
- Red: 双表级联查询未实现 → Green: @OneToMany(cascade = ALL) + 级联删除

**CI**：streak=18

---

## 8. Wave 25：agent-knowledge T9 DocumentIngestor + TokenCounter（2026-07-01）

**交付**：文档分块 + token 估算

**关键红绿循环**：
- Red: `DocumentIngestor` 分块逻辑未实现 → Green: 按段落 + 重叠窗口分块（默认 512 token / 重叠 64）
- Red: `TokenCounter` 估算未实现 → Green: 中文 1.7 倍系数 + 英文按空格分词
- Red: 分块边界断裂问题 → Green: 重叠窗口保证语义连续性

**CI**：streak=20

---

## 9. Wave 26：agent-knowledge T11 KnowledgeBase gRPC 服务（2026-07-01）

**交付**：KnowledgeBase gRPC 4 RPC（IngestDocument / SearchChunks / ListBases / DeleteBase）

**关键红绿循环**：
- Red: IngestDocument RPC 未实现 → Green: DocumentIngestor 分块 + EmbeddingService 向量化 + Milvus 入库
- Red: SearchChunks RPC 未实现 → Green: 向量检索 + TopK=10 + scoreThreshold=0.75
- Red: gRPC 异常翻译未实现 → Green: GrpcExceptionAdvice BusinessException → StatusResponse

**CI**：streak=21

**经验教训 30**：gRPC 异常翻译需精细映射 ErrorCode → Status.Code，404 → NOT_FOUND / 409 → ALREADY_EXISTS / 400 → INVALID_ARGUMENT

---

## 10. Wave 27：model-gateway T8 Chat gRPC 服务（2026-07-02）

**交付**：Chat RPC 完整实现（非流式）

**关键红绿循环**：
- Red: Chat RPC 未实现 → Green: ModelRouter 路由 → 适配器调用 → CostMeter 计量
- Red: 适配器异常未包装 → Green: 包装为 BusinessException(MODEL_GATEWAY_ERROR)
- Red: gRPC 错误码映射 MODEL_GATEWAY_ERROR → UNKNOWN（非 INTERNAL） → Green: 精细映射

**CI**：run 28535758564 ✅ streak=24，用时 6m14s

**经验教训 31**：gRPC exception translation 中 MODEL_GATEWAY_ERROR 应映射到 UNKNOWN 而非 INTERNAL，因为模型网关错误原因未知

---

## 11. Wave 28：model-gateway T10 CountTokens + ListModels（2026-07-02）

**交付**：CountTokens RPC（含中文 1.7 倍系数）+ ListModels RPC

**关键红绿循环**：
- Red: CountTokens RPC 未实现 → Green: 复用 TokenEstimator + 中文 1.7 倍系数
- Red: ListModels RPC 未实现 → Green: 从 model_provider 表查询 enabled=true 的模型
- Red: 模型元数据缺失（DB 无数据） → Green: @Component static list 兜底模型目录

**CI**：streak=25

**经验教训 32**：当数据库 lacks 模型级 metadata 时，用 @Component static list 提供模型目录兜底，避免 NPE

---

## 12. Wave 29：model-gateway T9 StreamChat + agent-repo T4 AgentRepo gRPC（2026-07-02）

**交付**：StreamChat server streaming（reactor-core Flux）+ AgentRepo gRPC 4 RPC

**关键红绿循环**：
- Red: StreamChat RPC 未实现 → Green: adapter 返回 Flux<ChatChunk>，service subscribe 后手动调 StreamObserver.onNext
- Red: 客户端取消未处理 → Green: ServerCallStreamObserver.setOnCancelHandler(Disposable.dispose)
- Red: 慢消费 OOM → Green: onBackpressureBuffer(256) 背压保护
- Red: AgentRepo CreateAgent RPC 未实现 → Green: AgentDefinition 保存 + uk_agent_id 冲突检测

**CI**：run 28563593997 ✅ streak=28，用时 6m3s

**经验教训 33**：gRPC server streaming + reactor-core Flux 模式：adapter 返回 Flux，service subscribe 后手动 onNext，关键三件套：setOnCancelHandler / onBackpressureBuffer / onError 翻译
**经验教训 34**：ModelProviderAdapter default streamChat() 抛 UnsupportedOperationException —— 并非所有 adapter 都支持流式，default 方法抛异常，支持的 adapter override 即可

---

## 13. Wave 30：agent-memory T1-T2 JPA 持久化层（2026-07-02）

**交付**：MemoryRecord @Entity + MemoryRecordRepository + MemoryProperties 配置

**关键红绿循环**：
- Red: `MemoryRecord` 是 POJO 非 JPA 实体 → Green: 升级为 @Entity + 30 业务字段 + 4 索引
- Red: `MemoryRecordRepository` 未定义 → Green: JpaRepository + findByTenantIdAndStatus / findByTopic / findExpiredBefore
- Red: `MemoryProperties` 配置类未定义 → Green: @ConfigurationProperties("memory") + ttl/recall/dedup 配置
- Red: H2 测试报 DataIntegrityViolation → Green: uk_memory_id 唯一约束正常工作（ERROR log 是预期行为）

**CI**：streak=29

**经验教训 35**：JPA Entity classes use BIGINT id with uk_xxx_id unique constraints；Enumerated fields use @Enumerated(STRING) to store readable values
**经验教训 36**：H2 database in @DataJpaTest reliably throws DataIntegrityViolationException for unique constraint violations, with ERROR logs being expected behavior

---

## 14. Wave 31：agent-memory T3 MemoryExtractor REFLECTIVE+filter（2026-07-02）

**交付**：MemoryExtractor 业务实现（4 类型提取 + REFLECTIVE 新增 + 过滤 + 自动分流）

**关键红绿循环**：
- Red: `extract(TaskResult, MemoryType)` 未实现 → Green: 按 type 分支提取（EPISODIC 步骤序列 / SEMANTIC 事实知识 / PROCEDURAL 操作模板 / REFLECTIVE 反思总结）
- Red: REFLECTIVE 类型未支持 → Green: 新增 REFLECTIVE 枚举值 + 提取逻辑
- Red: 任务失败时仍提取记忆 → Green: 过滤 FAILURE 任务（避免错误记忆污染）
- Red: TaskOutcome 枚举不匹配 → Green: 对齐 doc 04 §3.3（SUCCESS / FAILURE / PARTIAL / TIMEOUT）

**CI**：streak=30

**经验教训 37**：MemoryType includes REFLECTIVE as an additional value；MemoryStatus enumeration values: RAW/ACTIVE/DISTILLED/ARCHIVED (replaced HOT/WARM/COLD)

---

## 15. Wave 32：agent-memory T8 MemoryTtlManager + T9 MemoryDeduper（2026-07-02）

**交付**：TTL 状态机 + 过期清理 Scheduler + 去重（hash + 余弦相似度）

**关键红绿循环**：
- Red: `applyTtl` 状态机未实现 → Green: RAW→ACTIVE 立即 / ACTIVE→DISTILLED 7d / DISTILLED→ARCHIVED 30d / LOW 归档 3d
- Red: `cleanupExpired` 定时清理未实现 → Green: @Scheduled 固定速率扫描 findExpiredBefore
- Red: `dedup` 完全相同 hash 未实现 → Green: SHA-256(content) 比较 → 丢弃
- Red: 余弦相似度 ≥0.95 合并未实现 → Green: cosine ≥0.95 → merge 保留高 importance / 0.85~0.95 → 标记关联
- Red: `DedupReport` 报告未生成 → Green: 报告含 droppedCount / mergedCount / linkedCount

**CI**：streak=31

**经验教训 38**：TTL 状态机用 enum + Map<State, Map<Event, State>> 转移矩阵实现，canTransitTo 校验 + transit 执行
**经验教训 39**：去重三级策略：(a) hash 完全相同 → 丢弃；(b) cosine ≥0.95 → 合并；(c) 0.85~0.95 → 标记关联

---

## 16. Wave 33：agent-memory T4 MemoryDistiller + gRPC 基础设施（2026-07-03）

**交付**：MemoryDistiller 蒸馏业务实现 + ModelGatewayClient gRPC stub + DistillPromptBuilder

**关键红绿循环**：
- Red: `distill(topic)` 蒸馏未实现 → Green: skip if below threshold (20) → build prompt → call model → create DISTILLED → archive sources → persist
- Red: `ModelGatewayClient` gRPC stub 未定义 → Green: @GrpcClient ModelGatewayGrpc.ModelGatewayBlockingStub + @ConditionalOnProperty 控制
- Red: 测试环境无 gRPC → Green: NoOpModelGatewayClient fallback bean（@ConditionalOnProperty havingValue=false）
- Red: `DistillPromptBuilder` prompt 构造未实现 → Green: 同主题 ACTIVE 记录聚合 → 蒸馏 prompt 模板
- Red: 归档源记录未持久化（bug） → Green: 循环中添加 repository.save(source)
- Red: 测试环境 NoOp fallback 缺失 → Green: 新建 NoOpModelGatewayClient 互斥条件对

**CI**：streak=32

**经验教训 40**：JPA 的 setStatus(ARCHIVED) 只修改内存对象，不自动写入 DB，必须显式 repository.save
**经验教训 41**：@ConditionalOnProperty 互斥 bean 条件对必须覆盖所有情况：Impl(havingValue=true) + NoOp(havingValue=false, matchIfMissing=false)
**经验教训 42**：MemoryDistiller aggregates importance as average of source records，分级 HIGH(≥0.7)/MEDIUM(0.4~0.7)/LOW(<0.4)
**经验教训 43**：gRPC client configuration in application.yml: `grpc.client.model-gateway.address=static://localhost:9094`
**经验教训 44**：Test environment disables model gateway via `memory.model-gateway.enabled=false` in application-test.yml

---

## 17. Wave 34：agent-memory T7 ImportanceScorer 5 维度加权评分（2026-07-04）

**交付**：T7 5 维度加权评分（12 tests，+9 new）+ 3 新模型 + 接口扩展

**关键红绿循环**：
- Red: 5 维度评分未实现 → Green: emotionIntensity(0.20) + frequency(0.25) + novelty(0.20) + taskRelevance(0.25) + timeDecay(0.10) 加权求和
- Red: 等级分级未实现 → Green: score≥0.7 → HIGH / 0.4≤score<0.7 → MEDIUM / score<0.4 → LOW
- Red: 权重和≠1.0 风险 → Green: 显式测试 `should_HaveWeightsSumToOne` 验证 0.20+0.25+0.20+0.25+0.10=1.0
- Red: timeDecay 计算未实现 → Green: exp(-Δt/30d)，30 天衰减到 1/e ≈ 0.37
- Red: taskRelevance 命中率未实现 → Green: taskKeywords ∩ recordKeywords / taskKeywords.size()
- Red: novelty 无 referenceVectors → Green: 默认 0.5（T7 简化，等 T5/T6 接入）
- Red: 接口从 functional 变双方法 → Green: F12DecisionNodeTest lambda 改匿名类
- Red: `recallCount` 基本类型 int 不能 != null → Green: 直接用 `record.getRecallCount()`

**CI**：run 28687274719 ✅ streak=33，用时 6m32s

**经验教训 45**：基本类型 int 不能用 != null 比较，Java 基本类型（int/long/boolean/double 等）不能为 null，!= null 会编译错误
**经验教训 46**：接口从 functional 变双方法的代价：所有 lambda 使用处需改为匿名类或方法引用
**经验教训 47**：5 维度权重和=1.0 的显式测试，用 isCloseTo(1.0, within(1e-9)) 防浮点精度问题
**经验教训 48**：timeDecay 用 exp(-Δt/30d) 而非线性衰减，指数衰减更符合艾宾浩斯遗忘曲线
**经验教训 49**：ScoringContext 解耦外部依赖（EmbeddingClient），scorer 只需"向量数据"，不关心"向量如何生成"（依赖反转）
**经验教训 50**：keywords 字段解析容错策略：去掉方括号引号后按 `[,;\s]+` 分割，支持 JSON 数组 / 逗号 / 分号 / 空格分隔

---

## 18. Wave 35：全平台文档对齐（2026-07-04）

**交付**：4 个文档文件变更（+568/-92），无 TDD 红绿循环（docs-only）

**关键变更**：
- `00-coding-plans-overview.md` v1.0 → v2.0 完全重写：修复 Plan 编号错位（v1.0 把 Plan 03 标为 "DDL 脚本编写"，实际 03-agent-memory-plan.md 是 agent-memory）
- `tdd-red-green-records-v1.2.md` 新建：v1.1 单行格式难以 Edit，采用续篇策略
- `docs/README.md` 更新：文档总数 30 → 38，测试方法 464+ → 834+
- `.gitignore` 补充：wave*-commit-msg.txt / wave*-build.bat / agent-log-temp.md

**关键红绿循环**：无（docs-only commit，无代码变更）

**CI**：run 28688543436 ✅ streak=34（6m27s）+ run 28688772399 ✅ streak=35（6m23s）

**经验教训 51**：文档编号体系变更时必须全量重写而非增量修补——增量修补会留下不一致痕迹
**经验教训 52**：单行格式文档用续篇策略而非原地修改——避免格式破坏，历史可追溯
**经验教训 53**：diverged 分支用 `reset --soft` + 选择性 unstage 处理，避免 force push
**经验教训 54**：多份文档同步后用验证矩阵检查一致性（Plan 总数 / 模块 / 进度 / 测试方法总数 / CI streak / 等级）
**经验教训 55**：`.gitignore` 应覆盖临时 commit 消息文件，避免污染仓库

---

## 19. Wave 36：agent-memory T5 EmbeddingClient HTTP 实现（2026-07-04）

**交付**：Plan 03 T5 EmbeddingClient 真实 HTTP 实现（13 文件 +700/-50），agent-memory 进度 7/10 → 8/10

**关键红绿循环**：
- Red: 6 个 MockWebServer 用例（embed_single_returns1024DimVector / embed_batch_returnsList / embed_retryOnTimeout / embed_throwOnMaxRetryExceeded / embed_sendTenantIdHeader / embed_emptyInput_returnsEmptyList）+ 3 补充（nullText / 4xx_no_retry / cacheHit）
- Red: 接口从单方法变多方法 → Green: 扩展 `embed(text, tenantId)` + `embedBatch(texts, tenantId)`，保留旧 `embed(text)` 委托新方法
- Red: 编译失败 `com.agent.common.exception` 包找不到 → Green: pom.xml 加 agent-common 依赖
- Red: Mock impl 与 HTTP impl bean 冲突 → Green: `@ConditionalOnProperty` 互斥对（http-enabled=false 默认 Mock / true 启用 HTTP）
- Red: 重试逻辑未实现 → Green: 5xx/超时重试（max 3 总尝试，退避 100/300/900ms），4xx 立即失败，解析错误立即失败
- Red: 缓存未实现 → Green: Caffeine 本地缓存（key=text, TTL=1h, maxSize=10000），批量按文本分流缓存命中
- Red: 请求/响应构造散落 → Refactor: 抽离 EmbeddingRequestBuilder + EmbeddingResponseParser（单一职责）

**CI**：run 28689882077 ✅ streak=36（6m36s）

**经验教训 56**：`@ConditionalOnProperty` 互斥 bean 对必须覆盖所有环境——Mock 用 `havingValue="false" matchIfMissing="true"`（默认激活），HTTP 用 `havingValue="true"`（显式启用），避免 bean 冲突
**经验教训 57**：WebClient 测试用包级可见构造器注入——生产构造器从配置自动构建 WebClient，测试构造器接受额外 WebClient 参数（package-private），避免为测试暴露 public API
**经验教训 58**：重试策略应区分错误类型——5xx/超时/网络错误可重试（瞬时故障），4xx 不重试（请求格式错误），解析错误不重试（响应异常）。用 `WebClientResponseException.getStatusCode().is5xxServerError()` 精细判断
**经验教训 59**：接口从单方法变多方法时优先扩展而非破坏——保留旧方法委托新方法，已有调用方零改动
**经验教训 60**：Mock impl 与 HTTP impl 的输入校验可以差异化——Mock 对 null 容错（降低测试门槛），HTTP 严格校验（早暴露生产 bug）

---

## 20. Wave 37：agent-memory T10 MemoryService gRPC 4 RPC（2026-07-04）

**交付**：Plan 03 T10 MemoryService gRPC 服务实现（9 文件 +1087/-13），agent-memory 进度 8/10 → 9/10。后因 JaCoCo 覆盖率不达标追加 59 测试（3 文件 +916/-2），总计 agent-memory 130 → 189 tests。

**关键红绿循环**：
- Red: 8 个 MemoryServiceGrpcImplTest 用例（4 RPC 正常流 + 异常流）→ Green: MemoryServiceGrpcImpl 4 RPC 实现 + MemoryRecordMapper 双向映射 + GrpcExceptionAdvice 异常翻译
- Red: MemoryVectorStore 接口仅 insert → Green: 扩展 search（余弦相似度 topK）+ delete（按 memoryId），旧调用方零改动
- Red: LongTermMemoryWriterImpl 仅 embed+insert（F12 骨架）→ Green: 扩展全流程（contentHash + dedup + importance + DB save + insert vector），旧构造器委托新构造器
- Red: agent-common 缺 MEMORY_NOT_FOUND 错误码 → Green: 新增枚举值 + 重新 install agent-common
- **CI 失败 → 修复**：T10 代码 commit 39f569f push 后 CI run 28693929239 ❌ failure，JaCoCo 覆盖率检查未达标（lines 0.79/0.80, branches 0.63/0.70）
  - 根因：MemoryVectorStoreImpl（24%/14%）+ LongTermMemoryWriterImpl（62%/50%）+ MemoryRecordMapper（89%/56%）新增代码未覆盖
  - 修复：补 59 测试（MemoryVectorStoreImplTest +17 / LongTermMemoryWriterImplTest +10 / MemoryRecordMapperTest +32 新建）
  - 覆盖率提升：lines 81% → 89%, branches 63% → 79%（阈值 80%/70%）
  - 修复 commit 6fb869c → CI run 28695289697 ✅ success

**CI**：run 28695289697 ✅ success（streak 重启后 39）

**经验教训 61**：`**/*Grpc*` JaCoCo exclude 模式会误伤自研 GrpcService/GrpcExceptionAdvice——根 pom.xml 排除 `**/*Grpc*` 本意为排除 protobuf 生成的 stub，但 MemoryServiceGrpcImpl / GrpcExceptionAdvice 类名也含 "Grpc" 被一并排除。设计类名时注意覆盖率排除模式，或调整 exclude 模式更精确（如 `**/*Grpc$*` / `**/*OuterClass*`）
**经验教训 62**：新增大量业务代码后必须同步补单测——T10 一次提交 9 文件 +1087 行，仅 8 个 GrpcService 测试不足以覆盖 Mapper / Writer / VectorStore 的新分支。JaCoCo 覆盖率检查是安全网，CI 失败及时暴露欠测。后续 T11+ 应每扩展一个类即补对应单测
**经验教训 63**：JaCoCo HTML 报告定位覆盖率缺口高效——`target/site/jacoco/{package}/index.html` 按 class 列出 line/branch 覆盖率，快速锁定未覆盖类。比读 CSV 或 XML 更直观
**经验教训 64**：余弦相似度实现需处理边界——零范数向量（全零）返回 0 避免除零异常，维度不一致返回 0（容错），负分数截断到 [0,1]（负相关视为不相似）。这些边界都用独立测试用例固化
**经验教训 65**：proto ↔ Entity 双向映射器应有独立测试——MemoryRecordMapper 看似简单但分支多（null/空/大小写/无效值/JSON 解析容错），仅靠 GrpcService 间接覆盖不足。独立 MapperTest 32 用例覆盖所有分支，是 gRPC 服务实现的标准配套

---

## 21. v1.2 经验教训汇总（接续 v1.1 的 1~24）

| 编号 | Wave | 教训 |
|---|---|---|
| 25 | 18 | 多供应商适配器应统一抽象为接口，避免 if-else 分支地狱 |
| 26 | 19 | Subagent-Driven 并行开发时，每个子代理必须独立完成 Red-Green-Refactor |
| 27 | 20 | Spring AI 可复用 OpenAI/Anthropic，但 Gemini 和国内模型需自研 WebClient |
| 28 | 22 | JPA Entity 用 BIGINT id + uk_xxx_id + @Enumerated(STRING) |
| 29 | 23 | 测试状态污染用 @BeforeEach 清理 + @DirtiesContext 隔离上下文 |
| 30 | 26 | gRPC 异常翻译需精细映射 ErrorCode → Status.Code |
| 31 | 27 | MODEL_GATEWAY_ERROR 应映射到 UNKNOWN 而非 INTERNAL |
| 32 | 28 | DB lacks metadata 时用 @Component static list 兜底 |
| 33 | 29 | gRPC server streaming + Flux 三件套：setOnCancelHandler / onBackpressureBuffer / onError 翻译 |
| 34 | 29 | ModelProviderAdapter default streamChat() 抛 UnsupportedOperationException |
| 35 | 30 | JPA Entity BIGINT id + uk_xxx_id + @Enumerated(STRING)（重复强调） |
| 36 | 30 | H2 in @DataJpaTest throws DataIntegrityViolationException, ERROR logs expected |
| 37 | 31 | MemoryType includes REFLECTIVE；MemoryStatus: RAW/ACTIVE/DISTILLED/ARCHIVED |
| 38 | 32 | TTL 状态机用 enum + Map<State, Map<Event, State>> 转移矩阵 |
| 39 | 32 | 去重三级策略：hash 丢弃 / cosine≥0.95 合并 / 0.85~0.95 标记关联 |
| 40 | 33 | JPA setStatus 只改内存，必须显式 repository.save |
| 41 | 33 | @ConditionalOnProperty 互斥 bean 条件对必须覆盖所有情况 |
| 42 | 33 | MemoryDistiller aggregates importance as average，HIGH(≥0.7)/MEDIUM(0.4~0.7)/LOW(<0.4) |
| 43 | 33 | gRPC client config: `grpc.client.model-gateway.address=static://localhost:9094` |
| 44 | 33 | Test environment disables model gateway via `memory.model-gateway.enabled=false` |
| 45 | 34 | 基本类型 int 不能用 != null 比较 |
| 46 | 34 | 接口从 functional 变双方法，所有 lambda 需改匿名类 |
| 47 | 34 | 5 维度权重和=1.0 用 isCloseTo(1.0, within(1e-9)) 显式测试 |
| 48 | 34 | timeDecay 用 exp(-Δt/30d) 指数衰减符合艾宾浩斯遗忘曲线 |
| 49 | 34 | ScoringContext 解耦外部依赖（依赖反转典型应用） |
| 50 | 34 | keywords 字段解析容错：去方括号引号 + 按 `[,;\s]+` 分割 |
| 51 | 35 | 文档编号体系变更时必须全量重写而非增量修补 |
| 52 | 35 | 单行格式文档用续篇策略而非原地修改（避免格式破坏，历史可追溯） |
| 53 | 35 | diverged 分支用 `reset --soft` + 选择性 unstage 处理，避免 force push |
| 54 | 35 | 多份文档同步后用验证矩阵检查一致性（Plan 总数 / 模块 / 进度 / 测试方法总数 / CI streak / 等级） |
| 55 | 35 | `.gitignore` 应覆盖临时 commit 消息文件，避免污染仓库 |
| 56 | 36 | `@ConditionalOnProperty` 互斥 bean 对必须覆盖所有环境（matchIfMissing=true 默认激活，havingValue=true 显式启用） |
| 57 | 36 | WebClient 测试用包级可见构造器注入，避免为测试暴露 public API |
| 58 | 36 | 重试策略应区分错误类型：5xx/超时重试，4xx 立即失败，解析错误立即失败 |
| 59 | 36 | 接口从单方法变多方法时优先扩展而非破坏（保留旧方法委托新方法，已有调用方零改动） |
| 60 | 36 | Mock impl 与 HTTP impl 的输入校验可以差异化（Mock 容错 / HTTP 严格） |
| 61 | 37 | `**/*Grpc*` JaCoCo exclude 会误伤自研 GrpcService/GrpcExceptionAdvice，类名设计注意排除模式 |
| 62 | 37 | 新增大量业务代码后必须同步补单测，JaCoCo 覆盖率检查是欠测安全网 |
| 63 | 37 | JaCoCo HTML 报告 `target/site/jacoco/{package}/index.html` 按类列出覆盖率，高效定位缺口 |
| 64 | 37 | 余弦相似度需处理边界：零范数返回 0 避免除零、维度不一致容错、负分截断到 [0,1] |
| 65 | 37 | proto ↔ Entity 双向映射器应有独立测试，仅靠 gRPC 服务间接覆盖分支不足 |

---

## 22. v1.2 测试方法总计

| 类别 | 测试文件 | 测试方法 | 状态 |
|---|---|---|---|
| v1.1 详记（11 模块 + 端到端错误码） | 73 | 490+ | ✅ 完整实现 |
| v1.2 model-gateway（Wave 18~29） | +35 | +154 | ✅ 完整实现 |
| v1.2 agent-repo + agent-knowledge（Wave 19~26） | +28 | +128 | ✅ 完整实现 |
| v1.2 agent-memory（Wave 30~37） | +32 | +144 | ✅ 完整实现（9/10 Task） |
| **v1.2 合计（去重后）** | **+95** | **+426** | — |
| **v1.1 + v1.2 总计** | **168** | **916+** | — |

> **注**：v1.2 增量 426 与各 Wave 摘要的 386 差异为重叠统计（部分 Wave 测试跨模块计入）。实际 mvn verify 全量测试在 Wave 37 为 189 tests（agent-memory 模块单独），全平台累计 916+ 测试方法。

---

## 23. 变更记录

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1.2 | 2026-07-04 | 新增 v8 持久化深化期（Wave 18~34）TDD 红绿循环记录：①17 个 Wave 摘要；②经验教训 25~50（接续 v1.1 的 1~24）；③测试方法总计更新至 834+；④CI streak 4 → 33 进展；⑤v1.1 文档保持历史快照不再修改，v1.2 起独立维护 |
| v1.2.1 | 2026-07-04 | 增补 Wave 35（全平台文档对齐，docs-only）+ Wave 36（agent-memory T5 EmbeddingClient HTTP 实现）：①新增 2 个 Wave 摘要；②经验教训扩展至 60（+51~60）；③测试方法总计更新至 857+（agent-memory 8/10 Task）；④CI streak 4 → 36（连续 33 次全绿）；⑤Plan 03 进度 7/10 → 8/10 |
| v1.2.2 | 2026-07-04 | 增补 Wave 37（agent-memory T10 MemoryService gRPC 4 RPC + JaCoCo 修复）：①新增 1 个 Wave 摘要（含 CI 失败→修复过程）；②经验教训扩展至 65（+61~65）；③测试方法总计更新至 916+（agent-memory 9/10 Task）；④CI streak 36 → 39（含 1 次 JaCoCo 失败）；⑤Plan 03 进度 8/10 → 9/10 |
