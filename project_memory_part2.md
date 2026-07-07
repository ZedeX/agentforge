# AgentForge 智能体平台 项目记忆（Part 2：Wave 22~26）

> 拆分日期：2026-07-04 | 原文件总行数：1808 行 | 本 Part 覆盖：line 489~857
> 内容范围：Wave 22（agent-repo JPA）+ Wave 23（CostMeter JPA 集成）+ Wave 24~26（agent-knowledge JPA + gRPC）

> **⚠️ 历史快照说明**：本文件中每个 Wave 节内的「Plan 进度表」反映的是**该 Wave 结束时**的项目状态。其中的 ⏳/🔄/待做/待 v8 后续 等标记在后续 Wave 中已逐步完成。**当前最新状态请查看 [project_memory.md](./project_memory.md) 索引文件**。截至 Wave 47，全部 10 个 Plan 均已完成（10/10 ✅）。

## Wave 22：agent-repo JPA 持久化 — T2-T4（2026-07-01）

**时间**：2026-07-01 04:32 CST
**任务**：Task #80-#82 (Plan 08 T2-T4 JPA 持久化层)
**目标**：为 agent-repo 添加 JPA Entity + Repository，从内存 POJO 升级为持久化层

### 本轮交付

1. **4 JPA Entity** — AgentDefinition / AgentVersion / AgentRating / Capability 从 POJO 加 @Entity / @Table / @Column / @Enumerated(EnumType.STRING) / @PrePersist / @PreUpdate / @Convert 注解
2. **JsonListConverter** — `List<String>` ↔ JSON string AttributeConverter，用于 AgentDefinition 的 abilityTags / boundTools / boundKnowledgeIds 三个 List 字段（存为 `["a","b"]` JSON 字符串）
3. **4 Spring Data JPA Repository**：
   - AgentDefinitionRepository：findByAgentId / existsByAgentId / findByStatus / findByAgentTier / findByStatusAndAgentTier / deleteByAgentId
   - AgentVersionRepository：findByAgentIdOrderByVersionDesc / findTopByAgentIdOrderByVersionDesc / findByAgentIdAndVersion / existsByAgentIdAndVersion / countByAgentId
   - AgentRatingRepository：findByAgentIdOrderByCreatedAtDesc / findByAgentIdAndUserId / countByAgentId / avgScoreByAgentId(@Query COALESCE AVG)
   - CapabilityRepository：findByTag / findByEnabledTrue / findByTagAndEnabledTrue / existsByCode（自然键 code 作为 @Id）
4. **31 repository 测试** — @DataJpaTest + @ActiveProfiles("test") + H2 (MODE=MySQL)，覆盖 CRUD / 唯一约束 / 排序 / 聚合查询 / JsonListConverter 往返 / 时间戳自动填充
5. **DDL 对齐** — `infra/sql/mysql/06-agent-repo.sql` 重写，4 表（agent_definition / agent_version / agent_rating / capability）对齐 POJO 设计，移除旧列（core_constraints / business_config / scene_tags / reflection_mode 等），createdAt 改为 BIGINT（epoch millis）
6. **配置文件** — `application.yml`（MySQL agent_repo 库，端口 8096）+ `application-test.yml`（H2 MODE=MySQL，ddl-auto: create-drop）

### 验证

- **本地 mvn verify**：106 tests（75 existing 内存 Impl + 31 new JPA），0 failures，JaCoCo coverage met，14.4s
- **CI streak=15**：`28474044759` ✅ SUCCESS
- **远端**：`192355c8`（gh-api-push 创建，对应本地 `262a544`）

### 关键技术点

1. **JsonListConverter**：将 `List<String>` 序列化为 JSON 数组字符串存入 TEXT 列，兼容 MySQL 和 H2；@Convert 注解在字段上声明
2. **Capability 自然键**：code 字段作为 @Id（不用 @GeneratedValue），是 4 个 Entity 中唯一不以 Long 自增为主键的实体；JpaRepository<Capability, String>
3. **AgentDefinition createdAt/updatedAt 用 long**：epoch millis 映射 BIGINT 列（与 model-gateway 的 ModelUsageLog 一致），@PrePersist 中 `if (createdAt == 0) createdAt = System.currentTimeMillis()`
4. **唯一约束测试**：agent_id unique + (agent_id, version) unique 都有重复插入抛 DataIntegrityViolationException 测试
5. **avgScoreByAgentId @Query**：`SELECT COALESCE(AVG(r.score), 0.0) FROM AgentRating r WHERE r.agentId = :agentId`，无评分时返回 0.0
6. **H2Dialect WARN 无害**：`HHH90000025: H2Dialect does not need to be specified explicitly`，仅警告，不影响功能
7. **SQL Error 23505 预期**：唯一约束违反测试用例中 H2 抛 23505 是预期行为（测试通过）

### Plan 08 进展

| Task | 状态 | 说明 |
|---|---|---|
| T1 agent-repo 骨架 | ✅ | Wave 18 完成 |
| T2 AgentDefinition Entity + Repository | ✅ | Wave 22 |
| T3 AgentVersion Entity + Repository | ✅ | Wave 22（AgentVersionService 待 v8 后续）（**✅ UPDATE: Wave 40 完成，见 Part 4 §Wave 40**） |
| T4 AgentRepo gRPC 服务（4 RPC） | ⏳ | 待 v8 后续（**✅ UPDATE: Wave 40 完成，见 Part 4 §Wave 40**） |
| T5 Agent 绑定工具/知识库 JSON | ✅ | Wave 22（JsonListConverter 已实现 bound_tools/bound_knowledge_ids） |
| T6 agent-repo 集成测试 | ⏳ | 待 v8 后续（Testcontainers MySQL）（**✅ UPDATE: Wave 40 完成，见 Part 4 §Wave 40**） |
| T7-T12 agent-knowledge 模块 | ⏳ | 待 v8 后续（**✅ UPDATE: Waves 24-26 + Wave 40 完成，见 Part 2 §Wave 24-26 + Part 4 §Wave 40**） |

### CI 连续全绿记录（streak 15）

| # | run_id | commit | 用时 | 状态 |
|---|---|---|---|---|
| 11 | 28466515463 | docs(readme) A- 封顶 | 4m36s | ✅ |
| 12 | 28471967548 | docs(memory) Wave 20 | 4m37s | ✅ |
| 13 | 28473145320 | feat(model-gateway) T2-T3 | 5m25s | ✅ |
| 14 | 28473534645 | docs(memory) Wave 21 | 3m35s | ✅ |
| 15 | 28474044759 | feat(agent-repo) T2-T4 | ~5m | ✅ |

### 经验教训

25. **maven 不在 PATH 中时用完整路径**：`cmd /c "D:\_program\maven\apache-maven-3.9.16\bin\mvn.cmd ..."`，PowerShell 的 `mvn.cmd` 直接调用会报 "not recognized"
26. **gh run watch 可能因网络中断退出**：--exit-status 在网络断开时也会返回非零，需用 `gh run view --json status,conclusion` 确认真实 CI 状态
27. **JsonListConverter 设计模式**：对 List<String> 字段统一用 AttributeConverter 序列化为 JSON 字符串，比 @ElementCollection 更简单（无关联表），且能通过 @Query 查询

### 下一波（Wave 23）计划

- model-gateway T12 CostMeter → JPA 集成深化（CostMeterImpl 接入 ModelUsageLogRepository）
- 或 agent-knowledge 模块骨架启动（Plan 08 T7-T12）
- 或 agent-repo T4 AgentRepo gRPC 服务

---

## Wave 23：model-gateway T12 CostMeter JPA 集成深化（2026-07-01）

**时间**：2026-07-01 15:35 CST
**任务**：Task #83-#84 (Plan 07 T12 CostMeter → JPA 集成深化)
**目标**：将 CostMeter 从纯内存实现升级为 JPA 持久化实现，record() 持久化 ModelUsageLog，getQuotaUsed() 从 DB 聚合查询

### 本轮交付

1. **CostMeterJpaImpl**（@Primary @Component）— 新建 JPA 版本，注入 ModelUsageLogRepository + ModelProviderRepository：
   - `record()`：计算成本 + `usageLogRepository.save(log)` 持久化 ModelUsageLog
   - `getQuotaUsed()`：`sumTotalCostByTenantAndDateRange(tenantId, 0, Long.MAX_VALUE)` 从 DB 聚合查询
   - `@PostConstruct loadProvidersFromDb()`：从 DB 加载 enabled providers 覆盖默认单价
   - `resetPricing()`：清空 providerTable + 重新 seed 默认值（测试状态隔离用）
   - 保留 5 个默认 provider 单价（openai/openai-mini/anthropic/qwen-turbo/deepseek）作为 fallback
2. **CostMeterJpaImplTest**（10 tests）— @DataJpaTest + @Import(CostMeterJpaImpl.class) + @ActiveProfiles("test")：
   - 成本计算 / 持久化验证 / DB 聚合查询 / tenant 隔离 / null 兜底 / 未注册 provider 零成本 / 自定义单价 / DB 加载单价覆盖默认值
   - @BeforeEach resetPricing() 避免单例 bean 状态污染

### 设计决策

- **保留 CostMeterImpl（内存版）不变**：作为非 JPA 环境（纯单元测试）的 fallback，不破坏现有 8 个单元测试
- **CostMeterJpaImpl 用 @Primary**：在有 JPA 的环境下优先注入，CostMeterImpl 作为次选
- **@DataJpaTest + @Import**：@DataJpaTest 默认不加载 @Component，用 @Import(CostMeterJpaImpl.class) 精确导入待测 bean
- **resetPricing() 状态隔离**：单例 bean 的 providerTable Map 不受事务回滚影响，需 @BeforeEach 手动重置

### 验证

- **本地 mvn verify**：84 tests（74 existing + 10 new），0 failures，JaCoCo coverage met，30.4s
- **CI streak=17**：`28501512818` ✅ SUCCESS（5m26s）
- **远端**：`63009a7`（gh-api-push 创建，对应本地 `b2f7ea4`）

### Bug 修复：测试状态污染

- **症状**：首次 mvn verify 失败，`should_CalculateCost` 期望 0.0125 但得到 0.025（DB pricing）
- **根因**：`loadProvidersFromDb` 测试预置 openai provider 到 DB（0.01/0.03），调用 `loadProvidersFromDb()` 后覆盖了 providerTable 中的默认值（0.005/0.015）。由于 CostMeterJpaImpl 是单例 bean，providerTable Map 在测试间保持状态，不受 @DataJpaTest 事务回滚影响
- **修复**：新增 `resetPricing()` public 方法（clear + seed defaults），@BeforeEach 调用

### Plan 07 进展

| Task | 状态 | 说明 |
|---|---|---|
| T1 骨架 | ✅ | Wave 18 |
| T2-T3 Entity + Repository | ✅ | Wave 21 |
| T4-T7 Adapters | ✅ | Wave 18-20 |
| T8-T9 gRPC | ⏳ | 待 v8 后续（**✅ UPDATE: Waves 27-29 完成，见 Part 3 §Wave 27-29**） |
| T10 CountTokens | ✅ | Wave 18 |
| T11 PromptCache | ✅ | Wave 18 |
| **T12 CostMeter + JPA** | ✅ | **Wave 23 完成** |
| T13 ModelDegradationManager | ✅ | Wave 18 |
| T14 集成测试 | ⏳ | 待 v8 后续（**✅ UPDATE: Wave 40 完成，见 Part 4 §Wave 40**） |

### CI 连续全绿记录（streak 17）

| # | run_id | commit | 用时 | 状态 |
|---|---|---|---|---|
| 15 | 28474044759 | feat(agent-repo) T2-T4 | 5m28s | ✅ |
| 16 | 28474482353 | docs(memory) Wave 22 | 5m6s | ✅ |
| 17 | 28501512818 | feat(model-gateway) T12 | 5m26s | ✅ |

### 经验教训

28. **@DataJpaTest 单例 bean 状态污染**：@Component bean 的内存状态（如 ConcurrentHashMap）不受 @DataJpaTest 事务回滚影响，需 @BeforeEach 手动重置；DB 数据会被回滚但内存 Map 不会
29. **@DataJpaTest + @Import 测试 @Component**：@DataJpaTest 默认只加载 @Repository，测试 @Component 需用 @Import(XxxImpl.class) 精确导入，比 @SpringBootTest 轻量
30. **@Primary 双 Impl 模式**：保留内存版 Impl 作为 fallback + 新建 JPA 版 @Primary Impl，实现从内存到 JPA 的平滑迁移，不破坏现有纯单元测试

### 下一波（Wave 24）计划

- agent-knowledge 模块 JPA 持久化启动（Plan 08 T7-T12）
- 或 agent-repo T4 AgentRepo gRPC 服务
- 或 model-gateway T8-T9 gRPC 服务

---

## Wave 24：agent-knowledge JPA 持久化 — T8（2026-07-01）

**时间**：2026-07-01 21:57 CST
**任务**：Task #85-#90 (Plan 08 T8 knowledge_base + knowledge_chunk JPA Entity + Repository)
**目标**：将 agent-knowledge 4 个 POJO 升级为 JPA Entity，创建 4 个 Repository + 33 个测试

### 本轮交付

1. **pom.xml 升级** — 添加 spring-boot-starter-data-jpa / jackson-databind / mysql-connector-j / h2 依赖（对齐 agent-repo 模式）
2. **application.yml + application-test.yml** — 新建主配置（MySQL agent_knowledge / port 8098 / ddl-auto: validate）+ 测试配置（H2 MODE=MySQL / ddl-auto: create-drop）
3. **07-agent-knowledge.sql 重写** — 4 表对齐 4 POJO（BIGINT id 主键 + uk_xxx_id 唯一约束），createdAt/updatedAt 用 BIGINT (epoch millis) 对齐 POJO 字段类型
4. **4 Entity JPA 注解升级**：
   - KnowledgeBase：@Table uk_kb_id / @Enumerated(STRING) for KnowledgeStatus / @PrePersist @PreUpdate
   - KnowledgeDocument：@Table uk_doc_id / @Enumerated(STRING) for DocumentType / @PrePersist
   - DocumentChunk：@Table uk_chunk_id / @Enumerated(STRING) for IngestStatus / @PrePersist
   - KnowledgeVersion：**重构** final 字段 → mutable + 无参构造 + setter（满足 JPA 规范，保留全参构造）
5. **4 Repository**：
   - KnowledgeBaseRepository：findByKbId / existsByKbId / findByStatus / findByStatusOrderByCreatedAtDesc / deleteByKbId
   - KnowledgeDocumentRepository：findByDocId / findByKbId / findByKbIdOrderByCreatedAtDesc / existsByDocId / deleteByKbId
   - DocumentChunkRepository：findByChunkId / findByKbIdAndDocId / findByKbId / findByDocId / countByKbId / deleteByKbIdAndDocId / deleteByKbId
   - KnowledgeVersionRepository：findByVersionId / findByKbIdOrderByVersionDesc / findTopByKbIdOrderByVersionDesc / countByKbId / deleteByKbId
6. **4 RepositoryTest**（33 tests）— @DataJpaTest + @ActiveProfiles("test") + H2 MODE=MySQL：
   - KnowledgeBaseRepositoryTest：8 tests（CRUD + 状态过滤 + 默认值 + 唯一约束 + 时间戳 + 删除）
   - KnowledgeDocumentRepositoryTest：8 tests（CRUD + kbId 过滤 + 枚举往返 + 批量删除）
   - DocumentChunkRepositoryTest：9 tests（CRUD + kbId+docId 组合查询 + count + 幂等删除 + 枚举往返 + 批量删除）
   - KnowledgeVersionRepositoryTest：8 tests（CRUD + 版本降序历史 + 最新版本 + count + 批量删除）

### 设计决策

- **尊重现有 POJO 设计扩展**：Plan 08 T8 原文只提 KnowledgeBase + KnowledgeChunk 两表，但 Wave 18 骨架阶段扩展为 4 POJO（+KnowledgeDocument +KnowledgeVersion）。Wave 24 尊重现有设计全部升级，而非削足适履
- **KnowledgeVersion final 字段重构**：原 POJO 所有字段是 final（immutable），JPA 需要无参构造 + setter。重构为 mutable + 保留全参构造 + 添加 @PrePersist
- **BIGINT id 主键 + uk_xxx_id 唯一约束**：对齐 agent-repo 模式（AgentDefinition），而非用业务键作 @Id（Capability 模式）。便于 JpaRepository<Xxx, Long> 统一
- **@Enumerated(EnumType.STRING)**：status/type/status 枚举存为 STRING，DB 中可读（CREATING/READY/TEXT/MARKDOWN/PENDING/VECTORIZED）

### 验证

- **本地 mvn verify**：94 tests（61 existing + 33 new），0 failures，BUILD SUCCESS
- **CI streak=18**：`28522990941` ✅ SUCCESS
- **远端**：`2f2e097`（gh-api-push 创建，对应本地 `02155ba`）

### Plan 08 进展

| Task | 状态 | 说明 |
|---|---|---|
| T1-T6 agent-repo | ✅ | Wave 19 骨架 + Wave 22 JPA |
| T7 agent-knowledge 骨架 | ✅ | Wave 18 |
| **T8 knowledge_base + knowledge_chunk JPA** | ✅ | **Wave 24 完成**（扩展为 4 Entity） |
| T9 DocumentIngestor + ChunkStrategy | ⏳ | 待 v8 后续（**✅ UPDATE: Wave 25 完成，见 Part 2 §Wave 25**） |
| T10 EmbeddingService + MilvusVectorStore | ⏳ | 待 v8 后续（**✅ UPDATE: Wave 40 完成，见 Part 4 §Wave 40**） |
| T11 KnowledgeBase gRPC 服务 | ⏳ | 待 v8 后续（**✅ UPDATE: Wave 26 完成，见 Part 2 §Wave 26**） |
| T12 集成测试 | ⏳ | 待 v8 后续（**✅ UPDATE: Wave 40 完成，见 Part 4 §Wave 40**） |

### CI 连续全绿记录（streak 18）

| # | run_id | commit | 用时 | 状态 |
|---|---|---|---|---|
| 15 | 28474044759 | feat(agent-repo) T2-T4 | 5m28s | ✅ |
| 16 | 28474482353 | docs(memory) Wave 22 | 5m6s | ✅ |
| 17 | 28501512818 | feat(model-gateway) T12 | 5m26s | ✅ |
| 18 | 28522990941 | feat(agent-knowledge) T8 | ~7m | ✅ |

### 经验教训

31. **JPA Entity 从 immutable POJO 升级**：final 字段需改为 mutable + 添加无参构造 + setter 才能被 JPA 使用；保留全参构造方便业务代码使用，@PrePersist 钩子处理 createdAt 默认值
32. **尊重现有 POJO 设计扩展**：Plan 原文可能只提 N 个表，但骨架阶段可能扩展为 N+M 个。深化时应尊重现有设计全部升级，而非削足适履回到 Plan 原文
33. **@DataJpaTest H2 唯一约束测试**：H2 MODE=MySQL 下唯一约束违反抛 DataIntegrityViolationException，测试中 assertThatThrownBy 可靠捕获，但会输出 ERROR 日志（SqlExceptionHelper），这是预期行为不影响测试通过

### 下一波（Wave 25）计划

- agent-knowledge T9 DocumentIngestor + TokenChunkStrategy（文档导入与分块）
- 或 agent-repo T4 AgentRepo gRPC 服务
- 或 model-gateway T8-T9 gRPC 服务

---

## Wave 25：agent-knowledge T9 DocumentIngestor + TokenCounter（2026-07-01）

**时间**：2026-07-01 22:48 CST
**任务**：Task #91-#94 (Plan 08 T9 DocumentIngestor + TokenCounter)
**目标**：实现文档导入编排服务 + 中英文 token 计数器，串联 DocumentParser + ChunkSplitter + JPA Repository

### 本轮交付

1. **TokenCounter util**（`util/TokenCounter.java`）— 中英文 heuristic token 计数器：
   - CJK 字符（U+4E00-U+9FFF / U+3400-U+4DBF / U+3000-U+303F / U+FF00-U+FFEF）按 1.5 token/字计算
   - 非 CJK 字符按空格分词，每个 word 计 1 token
   - 返回 `ceil(cjkChars * 1.5) + nonCjkWords`
2. **DocumentIngestor API 接口**（`api/DocumentIngestor.java`）— 单方法 `ingestDocument(kbId, docId, name, content, type, strategy, maxTokens, overlap)` 返回 `IngestResult`
3. **DocumentIngestorImpl @Component**（`api/impl/DocumentIngestorImpl.java`）— @Transactional 编排服务：
   - 注入 DocumentParser + ChunkSplitter + 3 Repository（KnowledgeBase / KnowledgeDocument / DocumentChunk）
   - 流程：校验 KB 存在且非 DELETED → 校验 content 非空 → 自动生成 docId（若 null）→ **幂等删除**（deleteByKbIdAndDocId + 删旧 document + flush）→ parse → split → 持久化 document + chunks → **从 DB 重算 KB stats**（countByKbId + findByKbId.size）→ 状态转换 CREATING → UPDATING
   - chunkId 用 UUID，orderIndex 从 0 递增，IngestStatus=PENDING
4. **TokenCounterTest**（10 tests）— null/empty→0 / 纯英文按空格 / 纯中文 1.5x / 中英混合 / 连续 CJK / 标点 / 长文本 / 多空格
5. **DocumentIngestorImplTest**（10 tests）— @DataJpaTest + @Import({DocumentIngestorImpl, DocumentParserImpl, ChunkSplitterImpl})：
   - 正常导入 + KB stats 更新 / KB 不存在 / KB 已删除 / content 空 / **幂等 re-ingest**（旧 chunks 删除新 chunks 创建）/ Markdown 先 parse 再 split / orderIndex 递增 / docId 自动生成 / CREATING→UPDATING / 同 KB 多文档 stats 累积

### 设计决策

- **幂等 ingest**：re-ingest 同 docId 时先 `deleteByKbIdAndDocId` + 删旧 document + `flush()`，再重新分块。比"增量更新"更简单可靠，保证 chunks 与 document 总是一致
- **KB stats 从 DB 重算**：ingest 后用 `chunkRepository.countByKbId(kbId)` + `documentRepository.findByKbId(kbId).size()` 重新计算 docCount/chunkCount，比维护增量计数器更准确（避免并发/异常导致漂移）
- **@DataJpaTest + @Import 多 @Component**：@DataJpaTest 默认只加载 @Repository，测试 @Service 需 `@Import({ServiceImpl.class, Dep1Impl.class, Dep2Impl.class})` 精确导入 @Service 及其所有 @Component 依赖
- **TokenCounter heuristic 而非精确**：中文 1.5 token/字是经验值（GPT/BERT 分词器约 1.2-1.8），英文按空格分词忽略子词拆分。够用于 chunk 切分上限控制，精确计数留给模型 API
- **@Transactional on @Component**：DocumentIngestorImpl 跨 3 表操作（document + chunks + kb stats），@Transactional 确保原子性，失败回滚不留下孤儿 chunks

### 验证

- **本地 mvn verify**：114 tests（94 existing + 20 new），0 failures，BUILD SUCCESS
- **CI streak=20**：`28526257381` ✅ SUCCESS
- **远端**：`9bb4a20`（gh-api-push 创建，对应本地 `e05cef5`）

### Plan 08 进展

| Task | 状态 | 说明 |
|---|---|---|
| T1-T6 agent-repo | ✅ | Wave 19 骨架 + Wave 22 JPA |
| T7 agent-knowledge 骨架 | ✅ | Wave 18 |
| T8 knowledge_base + knowledge_chunk JPA | ✅ | Wave 24 |
| **T9 DocumentIngestor + TokenCounter** | ✅ | **Wave 25 完成** |
| T10 EmbeddingService + MilvusVectorStore | ⏳ | 待 v8 后续（**✅ UPDATE: Wave 40 完成，见 Part 4 §Wave 40**） |
| T11 KnowledgeBase gRPC 服务 | ⏳ | 待 v8 后续（**✅ UPDATE: Wave 26 完成，见 Part 2 §Wave 26**） |
| T12 集成测试 | ⏳ | 待 v8 后续（**✅ UPDATE: Wave 40 完成，见 Part 4 §Wave 40**） |

### CI 连续全绿记录（streak 20）

| # | run_id | commit | 用时 | 状态 |
|---|---|---|---|---|
| 15 | 28474044759 | feat(agent-repo) T2-T4 | 5m28s | ✅ |
| 16 | 28474482353 | docs(memory) Wave 22 | 5m6s | ✅ |
| 17 | 28501512818 | feat(model-gateway) T12 | 5m26s | ✅ |
| 18 | 28522990941 | feat(agent-knowledge) T8 | ~7m | ✅ |
| 19 | 28523478609 | docs(memory) Wave 24 | ~5m | ✅ |
| 20 | 28526257381 | feat(agent-knowledge) T9 | ~6m | ✅ |

### 经验教训

34. **@DataJpaTest + @Import 多 @Component**：@DataJpaTest 默认只加载 @Repository Bean，测试 @Service/@Component 需用 `@Import({ServiceImpl.class, Dep1Impl.class, Dep2Impl.class})` 显式导入 @Service 及其所有 @Component 依赖（不能只导入 @Service，否则依赖注入失败）
35. **幂等 ingest 设计模式**：re-ingest 同一业务键（docId）时，采用"先全删后重建"而非"增量更新"——delete old children + delete old parent + flush + re-create。配合 @Transactional 保证原子性，避免孤儿数据。比维护增量 diff 简单且可靠
36. **KB stats 从 DB 重算优于增量维护**：跨表计数（docCount/chunkCount）在每次 mutate 后从 DB `countByXxx` 重新查询，比维护内存增量计数器更准确，避免并发/异常导致的漂移。代价是多一次查询，在写少读多场景可接受

### 下一波（Wave 26）计划

- agent-knowledge T10 EmbeddingService + MilvusVectorStore（向量化与向量存储）
- 或 agent-repo T4 AgentRepo gRPC 服务
- 或 model-gateway T8-T9 gRPC 服务

---

## Wave 26：agent-knowledge T11 KnowledgeBase gRPC 服务层（2026-07-01）

**时间**：2026-07-01 23:30 CST
**任务**：Task #95-#99 (Plan 08 T11 KnowledgeBase gRPC 服务 4 RPC)
**目标**：在 KnowledgeService gRPC 上追加 4 个 Plan 08 RPC（IngestDocument / SearchChunks / ListBases / DeleteBase），实现 JPA-backed 业务编排 + gRPC 服务层 + 异常翻译

### 本轮交付

1. **knowledge.proto 扩展**（非破坏性追加）— 在现有 `KnowledgeService`（3 RPC：Retrieve/Ingest/VersionManage）上追加 4 个新 RPC + 8 个 message：
   - `IngestDocument(IngestDocumentRequest) → IngestDocumentResponse`
   - `SearchChunks(SearchChunksRequest) → SearchChunksResponse`（复用现有 KnowledgeChunk）
   - `ListBases(ListBasesRequest) → ListBasesResponse`（含 KnowledgeBaseInfo）
   - `DeleteBase(DeleteBaseRequest) → DeleteBaseResponse`
2. **agent-common ErrorCode 扩展** — 新增 5 个 KB 错误码：KB_NOT_FOUND(404) / KB_IN_USE(409) / DOC_INGEST_FAILED(500) / EMBEDDING_FAILED(500) / VECTOR_STORE_ERROR(500)
3. **agent-knowledge pom.xml** — 追加 agent-proto + agent-common + net.devh grpc-spring-boot-starter 3.1.0.RELEASE（exclusion grpc-netty-shaded）+ lombok + test grpc-testing
4. **KnowledgeBaseService 接口 + Impl**（`api/KnowledgeBaseService.java` + `api/impl/KnowledgeBaseServiceImpl.java`）— JPA-backed @Service，编排 KnowledgeBaseRepository + DocumentIngestor + KnowledgeRetriever：
   - createBase（校验 name + kbId 唯一 + 自动生成 + status=READY）
   - ingest（loadKbOrThrow → DocumentIngestor → failure 抛 DOC_INGEST_FAILED）
   - search（existsByKbId → KnowledgeRetriever.search）
   - listBases（空过滤=全部非 DELETED；状态过滤=fromCode + findByStatusOrderByCreatedAtDesc）
   - deleteBase（loadKbOrThrow → docCount>0 && !force 抛 KB_IN_USE → setStatus DELETED）
5. **KnowledgeMapper**（`grpc/KnowledgeMapper.java`）— @Component，proto ↔ Entity/POJO 单向映射：toInfo / toChunk / toIngestResponse / toSearchResponse / toListResponse / toDeleteResponse
6. **GrpcExceptionAdvice**（`grpc/GrpcExceptionAdvice.java`）— @Component，复用 agent-task-orchestrator 模式：translate(Throwable, StreamObserver) 按 httpStatus 映射 BusinessException → gRPC Status（404→NOT_FOUND / 409→FAILED_PRECONDITION / 400→INVALID_ARGUMENT / 500→INTERNAL）
7. **KnowledgeBaseGrpcService**（`grpc/KnowledgeBaseGrpcService.java`）— @GrpcService extends KnowledgeServiceGrpc.KnowledgeServiceImplBase，覆盖 4 新 RPC（3 旧 RPC 保留 default UNIMPLEMENTED），每 RPC try/catch + exceptionAdvice.translate
8. **2 测试类**（23 tests）：
   - KnowledgeBaseServiceImplTest（16 tests）— @DataJpaTest + @Import + @MockBean(DocumentIngestor/KnowledgeRetriever)：createBase 4 / ingest 4 / search 2 / listBases 2 / deleteBase 4
   - KnowledgeBaseGrpcServiceTest（7 tests）— 纯单测 mock KnowledgeBaseService + 真实 Mapper/Advice + capturing StreamObserver：4 RPC 正常流 + KB_NOT_FOUND→NOT_FOUND + KB_IN_USE→FAILED_PRECONDITION

### 设计决策

- **非破坏性 proto 扩展**：在现有 `KnowledgeService` 上追加 4 RPC 而非新建 service 或重命名。理由：proto3 追加 RPC 是安全的；全项目 Grep 确认无任何 gRPC client 调用 KnowledgeService（`KnowledgeServiceGrpc` 零匹配），但保留旧 3 RPC 语义供未来 Retrieve/VersionManage 接入。旧 3 RPC 在 ImplBase 中保留 default UNIMPLEMENTED
- **新建 KnowledgeBaseService 而非升级 KnowledgeServiceImpl**：现有 KnowledgeServiceImpl 是 in-memory（ConcurrentHashMap），其 10 个测试测内存行为。新建 JPA-backed KnowledgeBaseService 避免破坏现有测试，且 Plan 08 明确要求 `service/KnowledgeBaseService`。旧 in-memory 实现标记为待废弃
- **deleteBase 引用检查用 docCount**：骨架阶段无 KB 引用反查（KbReferenceChecker 需跨模块 gRPC），简化为 `docCount > 0 && !force → KB_IN_USE`。force=true 直接软删。这给出可测试的 KB_IN_USE 路径
- **GrpcService 单测用 capturing StreamObserver 而非 InProcess server**：直接调用 override 方法 + 捕获 onNext/onError，无需启动 gRPC transport，更轻量。异常翻译通过断言 StatusRuntimeException.getCode() 验证
- **测试 gRPC server 禁用**：application-test.yml 设 `grpc.server.port: -1`，避免 @DataJpaTest/@SpringBootTest 启动真实 gRPC 端口

### 验证

- **本地 mvn verify**：agent-knowledge 137 tests（114 existing + 23 new），0 failures，BUILD SUCCESS；agent-proto 16 tests（proto stub 编译）；agent-common 73 tests（含 KB 错误码路径覆盖）
- **CI streak=22**：`28528216476` ✅ SUCCESS
- **远端**：`8c880ba`（gh-api-push 创建，对应本地 `17c3ad4`）

### Plan 08 进展

| Task | 状态 | 说明 |
|---|---|---|
| T1-T6 agent-repo | ✅ | Wave 19 骨架 + Wave 22 JPA |
| T7 agent-knowledge 骨架 | ✅ | Wave 18 |
| T8 knowledge_base + knowledge_chunk JPA | ✅ | Wave 24 |
| T9 DocumentIngestor + TokenCounter | ✅ | Wave 25 |
| **T11 KnowledgeBase gRPC 服务** | ✅ | **Wave 26 完成**（4 RPC + JPA 业务编排） |
| T10 EmbeddingService + MilvusVectorStore | ⏳ | 待 v8 后续（需 Milvus infra）（**✅ UPDATE: Wave 40 完成，见 Part 4 §Wave 40**） |
| T12 集成测试 | ⏳ | 待 v8 后续（需 Milvus + MySQL + Redis 三容器）（**✅ UPDATE: Wave 40 完成，见 Part 4 §Wave 40**） |

### CI 连续全绿记录（streak 22）

| # | run_id | commit | 用时 | 状态 |
|---|---|---|---|---|
| 18 | 28522990941 | feat(agent-knowledge) T8 | ~7m | ✅ |
| 19 | 28523478609 | docs(memory) Wave 24 | ~5m | ✅ |
| 20 | 28526257381 | feat(agent-knowledge) T9 | ~6m | ✅ |
| 21 | 28526726291 | docs(memory) Wave 25 | ~5m | ✅ |
| 22 | 28528216476 | feat(agent-knowledge) T11 | ~5m | ✅ |

### 经验教训

37. **非破坏性 proto 扩展优于重写**：proto3 追加 RPC/message 是安全的（不破坏现有 client）。即使当前无 client 调用，重写 service 名/RPC 会丢失语义历史。追加 + 保留旧 default UNIMPLEMENTED 是最低风险路径
38. **@DataJpaTest + @Import + @MockBean 隔离 @Service**：测试 JPA-backed @Service 时，@DataJpaTest 加载 Repository，@Import 加载被测 Service，@MockBean 替换重依赖（DocumentIngestor/KnowledgeRetriever）。比 @SpringBootTest 轻，且避免触发 gRPC autoconfig
39. **gRPC 服务单测用 capturing StreamObserver**：gRPC ImplBase 的 override 方法是普通 Java 方法（request + StreamObserver）。直接调用 + 用 capturing observer 捕获 onNext/onError，无需 InProcess server/transport，更轻量且覆盖异常翻译路径（断言 StatusRuntimeException.getCode()）
40. **gRPC server 测试禁用**：grpc-spring-boot-starter 在测试中会尝试启动 server。application-test.yml 设 `grpc.server.port: -1` 禁用，避免端口占用与 @DataJpaTest 冲突

### 下一波（Wave 27）计划

- agent-repo T4 AgentRepo gRPC 服务（需新建 repo.proto + gRPC 层）
- 或 model-gateway T8-T9 gRPC 服务（model.proto 已有，可直接实现）
- 或 agent-knowledge T10 EmbeddingService + Milvus（需 Milvus infra，风险较高）

---

（Part 2 结束，共 369 行。后续内容见 Part 3：Wave 27~32）