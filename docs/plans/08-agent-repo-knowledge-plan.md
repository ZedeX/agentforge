# agent-repo + agent-knowledge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `agent-repo`（端口 8096）+ `agent-knowledge`（端口 8098）两模块补齐 Agent 仓库管理（CRUD / 版本 / 快照 / 绑定工具知识库）+ 知识库管理（文档导入 / 分块 / 向量化 / 检索 / 重排）的全部能力。包含两个独立 Spring Boot 应用、JPA Entity 建模、Protobuf gRPC 契约对接、Milvus 向量存储集成、Testcontainers 端到端集成测试。本计划从 0 起步，T1~T12 共 12 个 Task，对齐 v5 审核 §6 P6-8 整改项与设计文档 doc 00-overview §3.1 端口规划。

**Architecture:** 两个独立 Spring Boot 应用，无相互直接调用（仅通过 KB id 与 Agent 元数据上的 JSON 字段建立逻辑绑定）：

- `agent-repo`（端口 8096 HTTP / 9098 gRPC）管理 `agent_definition` + `agent_version` 两表（doc 01-database §6），对外暴露 gRPC 服务 `AgentRepo`（4 RPC：CreateAgent / GetAgent / UpdateAgent / ListAgents），支持 Agent 元数据 CRUD、版本快照回滚、`bound_tools` / `bound_knowledge_ids` JSON 字段绑定工具与知识库。
- `agent-knowledge`（端口 8098 HTTP / 9100 gRPC）管理 `knowledge_base` + `knowledge_chunk` 两表（doc 01-database §7），对外暴露 gRPC 服务 `KnowledgeBase`（4 RPC：IngestDocument / SearchChunks / ListBases / DeleteBase），内部通过 `DocumentIngestor` 完成文档分块、`EmbeddingService` 完成向量化、`MilvusVectorStore` 完成向量存取与 TopK 检索，可选 BM25 重排（MMR 简化策略）。

两模块均依赖 `agent-proto`（Protobuf 契约，含 `repo.proto` / `knowledge.proto` / `common.proto`）与 `agent-common`（ErrorCode / BusinessException / PageResult）。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / grpc-spring-boot-starter 3.1.0.RELEASE（net.devh）/ JPA + MySQL 8 / Milvus Java SDK 2.3.4 / Jedis 5.1.0（缓存知识库元数据）/ JUnit 5 / Mockito 5 / AssertJ 3.25.3 / Awaitility / H2（MySQL 模式，UT 备选）/ Testcontainers 1.19.7（Milvus + MySQL 集成测试）/ Flyway（DDL 迁移备选）

---

## 设计文档对齐

| 项 | 来源 | 锁定值 |
|---|---|---|
| agent-repo 端口 | doc 00-overview §3.1 | 8096（HTTP） / 9098（gRPC） |
| agent-knowledge 端口 | doc 00-overview §3.1 | 8098（HTTP） / 9100（gRPC） |
| agent-repo 逻辑库 | doc 01-database §0.4 / §6 | `agent_repo`（agent_definition / agent_version） |
| agent-knowledge 逻辑库 | doc 01-database §0.4 / §7 | `agent_knowledge`（knowledge_base / knowledge_chunk） |
| AgentRepo gRPC 4 RPC | `agent-proto/src/main/proto/repo.proto` | CreateAgent / GetAgent / UpdateAgent / ListAgents |
| KnowledgeBase gRPC 4 RPC | `agent-proto/src/main/proto/knowledge.proto` | IngestDocument / SearchChunks / ListBases / DeleteBase |
| proto 生成包名 | repo.proto / knowledge.proto / common.proto | `agentplatform.repo.v1` / `agentplatform.knowledge.v1` / `agentplatform.common.v1` |
| agent_definition 字段 | doc 01-database §6.1 | agent_id / name / description / ability_tags(JSON) / scene_tags(JSON) / system_prompt / core_constraints / business_config(JSON) / model_tier / max_steps / max_token / bound_tools(JSON) / bound_knowledge_ids(JSON) / reflection_mode / status / version |
| agent_version 字段 | doc 01-database §6.2 | agent_id / version / snapshot(JSON) / change_log |
| knowledge_base 字段 | doc 01-database §7.1 | kb_id / name / description / doc_count / chunk_count / embedding_model / dimension / status |
| knowledge_chunk 字段 | doc 01-database §7.2 | chunk_id / doc_id / kb_id / content / token_count / embedding_id / status |
| Agent 状态枚举 | doc 06-agent-repo §2.3 | DRAFT / PUBLISHED / DEPRECATED / ARCHIVED |
| Agent 版本策略 | doc 06-agent-repo §4.2 | 每次发布生成快照（snapshot JSON 含完整定义）+ change_log；最大保留 20 个历史版本 |
| 知识库状态枚举 | doc 07-knowledge §3.2 | CREATING / READY / UPDATING / ERROR / DELETED |
| 分块策略 | doc 07-knowledge §4.1 | 默认 token 上限 512（可配 `knowledge.chunk.maxTokens`），重叠 64 token；支持按段落 / 固定长度两种切分 |
| 向量化模型 | doc 07-knowledge §5.2 | 默认 `bge-large-zh-v1.5`，维度 1024；通过 `EmbeddingService` 抽象支持切换 OpenAI / 本地 Sentence-Transformers |
| Milvus collection 命名 | doc 07-knowledge §6.1 | `kb_{kb_id}`，主键 chunk_id，HNSW 索引（M=16 / efConstruction=200） |
| 检索 TopK | doc 07-knowledge §7.1 | 默认 topK=5，可配 `knowledge.search.defaultTopK`；MMR 多样性重排 λ=0.5 |
| 错误码域 | doc 06 §6 / doc 07 §6 | AGENT_NOT_FOUND(404) / AGENT_VERSION_CONFLICT(409) / KB_NOT_FOUND(404) / KB_IN_USE(409) / DOC_INGEST_FAILED(500) / EMBEDDING_FAILED(500) / VECTOR_STORE_ERROR(500) |
| gRPC 服务类签名 | doc 06 §5 / doc 07 §5 | `@GrpcService extends AgentRepoGrpc.AgentRepoImplBase` / `extends KnowledgeBaseGrpc.KnowledgeBaseImplBase` |
| 配置参数 | doc 06 §7 / doc 07 §8 | `knowledge.chunk.maxTokens=512` / `knowledge.chunk.overlap=64` / `knowledge.search.defaultTopK=5` / `knowledge.embedding.model=bge-large-zh-v1.5` / `knowledge.embedding.dimension=1024` / `knowledge.milvus.host=milvus` / `knowledge.milvus.port=19530` |
| 测试用例 | `docs/tests/unit-test-cases.md` §11 / §12 | UT-REPO-001~006 / UT-KB-001~006 |
| v5 审核整改项 | `docs/tests/tdd-audit-report-v5.md` §6 P6-8 | 实现 agent-repo + agent-knowledge 两模块（CRUD / 版本快照 / 文档分块 / 向量化 / 检索）D3 +1.5 |

---

## 文件结构总览

### agent-repo 模块（T1~T6）

```
agent-repo/
├── pom.xml                                                # T1
├── src/main/resources/
│   ├── application.yml                                    # T1
│   └── db/migration/V1__init_agent_repo.sql               # T1（agent_definition + agent_version）
└── src/main/java/com/agent/repo/
    ├── AgentRepoApplication.java                          # T1
    ├── config/
    │   └── AgentRepoProperties.java                        # T1
    ├── model/
    │   ├── AgentDefinition.java                            # T2 @Entity
    │   ├── AgentVersion.java                               # T3 @Entity
    │   ├── AgentStatus.java                                # T2 枚举（DRAFT/PUBLISHED/DEPRECATED/ARCHIVED）
    │   ├── ReflectionMode.java                             # T2 枚举（NONE/PERIODIC/ON_FAILURE）
    │   └── ModelTier.java                                  # T2 枚举（LITE/STANDARD/ADVANCED）
    ├── repository/
    │   ├── AgentDefinitionRepository.java                  # T2
    │   └── AgentVersionRepository.java                     # T3
    ├── service/
    │   ├── AgentRepoService.java                           # T4 业务编排
    │   ├── AgentVersionService.java                         # T3 版本快照管理
    │   └── AgentBindingService.java                         # T5 工具/知识库绑定
    ├── grpc/
    │   ├── AgentRepoGrpcService.java                       # T4 @GrpcService
    │   ├── AgentMapper.java                                # T4 proto ↔ Entity 映射
    │   └── GrpcExceptionAdvice.java                        # T4 异常翻译
    └── util/
        └── JsonUtils.java                                  # T2 JSON 字段序列化辅助
└── src/test/java/com/agent/repo/
    ├── model/AgentDefinitionTest.java                       # T2 单测
    ├── service/AgentVersionServiceTest.java                # T3 单测
    ├── service/AgentBindingServiceTest.java                # T5 单测
    ├── grpc/AgentRepoGrpcServiceTest.java                  # T4 gRPC InProcess 单测
    └── integration/AgentRepoIntegrationTest.java           # T6 Testcontainers MySQL
```

### agent-knowledge 模块（T7~T12）

```
agent-knowledge/
├── pom.xml                                                # T7
├── src/main/resources/
│   ├── application.yml                                    # T7
│   └── db/migration/V1__init_agent_knowledge.sql          # T7（knowledge_base + knowledge_chunk）
└── src/main/java/com/agent/knowledge/
    ├── AgentKnowledgeApplication.java                     # T7
    ├── config/
    │   ├── KnowledgeProperties.java                        # T7
    │   └── MilvusConfig.java                               # T10
    ├── model/
    │   ├── KnowledgeBase.java                              # T8 @Entity
    │   ├── KnowledgeChunk.java                             # T8 @Entity
    │   ├── KnowledgeStatus.java                            # T8 枚举
    │   └── IngestStatus.java                               # T9 枚举
    ├── repository/
    │   ├── KnowledgeBaseRepository.java                    # T8
    │   └── KnowledgeChunkRepository.java                   # T8
    ├── service/
    │   ├── KnowledgeBaseService.java                       # T11 业务编排
    │   ├── DocumentIngestor.java                           # T9 文档导入与分块
    │   ├── ChunkStrategy.java                              # T9 分块策略接口
    │   ├── TokenChunkStrategy.java                         # T9 token 上限分块实现
    │   ├── EmbeddingService.java                           # T10 向量化接口
    │   ├── DefaultEmbeddingService.java                    # T10 默认实现（占位 mock 或调外部 API）
    │   ├── VectorStoreService.java                         # T10 向量存储抽象
    │   ├── MilvusVectorStore.java                          # T10 Milvus 实现
    │   └── SearchResult.java                               # T10 检索结果 POJO
    ├── grpc/
    │   ├── KnowledgeBaseGrpcService.java                   # T11 @GrpcService
    │   ├── KnowledgeMapper.java                            # T11 proto ↔ Entity 映射
    │   └── GrpcExceptionAdvice.java                        # T11 异常翻译
    └── util/
        ├── TokenCounter.java                                # T9 token 计数（简易 heuristic）
        └── MmrReranker.java                                # T10 MMR 多样性重排
└── src/test/java/com/agent/knowledge/
    ├── service/DocumentIngestorTest.java                   # T9 单测
    ├── service/TokenChunkStrategyTest.java                 # T9 单测
    ├── service/EmbeddingServiceTest.java                   # T10 单测
    ├── service/MilvusVectorStoreTest.java                  # T10 单测（Testcontainers）
    ├── service/MmrRerankerTest.java                        # T10 单测
    ├── grpc/KnowledgeBaseGrpcServiceTest.java              # T11 gRPC InProcess 单测
    └── integration/AgentKnowledgeIntegrationTest.java     # T12 Testcontainers Milvus + MySQL
```

---

## Tasks 总览

| Task | 模块 | 一句话描述 | 依赖 |
|---|---|---|---|
| T1 | agent-repo | 项目骨架（pom.xml + application.yml + 启动类 + Flyway） | agent-proto / agent-common |
| T2 | agent-repo | agent_definition JPA Entity + Repository + JSON 字段处理 | T1 |
| T3 | agent-repo | agent_version 版本快照表 + AgentVersionService | T2 |
| T4 | agent-repo | AgentRepo gRPC 服务（4 RPC）+ 异常翻译 | T2 / T3 |
| T5 | agent-repo | Agent 绑定工具 / 知识库（bound_tools / bound_knowledge_ids JSON） | T4 |
| T6 | agent-repo | 集成测试（Testcontainers MySQL） | T1~T5 |
| T7 | agent-knowledge | 项目骨架（pom.xml + application.yml + 启动类 + Flyway） | agent-proto / agent-common |
| T8 | agent-knowledge | knowledge_base + knowledge_chunk JPA Entity + Repository | T7 |
| T9 | agent-knowledge | 文档导入与分块（DocumentIngestor + TokenChunkStrategy） | T8 |
| T10 | agent-knowledge | 向量化与 Milvus 写入（EmbeddingService + MilvusVectorStore + MMR） | T9 |
| T11 | agent-knowledge | KnowledgeBase gRPC 服务（4 RPC）+ 异常翻译 | T9 / T10 |
| T12 | agent-knowledge | 集成测试（Testcontainers Milvus + MySQL） | T7~T11 |

---

## Task T1：agent-repo 项目骨架

**Red：** 项目可启动但无业务逻辑。先建立可运行的 Spring Boot 工程壳。

- [ ] **Red-1** 在父 pom 的 `<modules>` 下追加 `<module>agent-repo</module>`
- [ ] **Red-2** 创建 `agent-repo/pom.xml`，继承根 pom，声明依赖：
  - `spring-boot-starter-web`
  - `spring-boot-starter-data-jpa`
  - `mysql-connector-j`
  - `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE`
  - `com.agentplatform:agent-proto`（proto 契约包）
  - `com.agentplatform:agent-common`（异常 / ErrorCode / PageResult）
  - `org.flywaydb:flyway-mysql`
  - `org.projectlombok:lombok`
  - `com.fasterxml.jackson.core:jackson-databind`（JSON 字段处理）
  - `org.springframework.boot:spring-boot-starter-test`（test scope）
  - `com.h2database:h2`（test scope）
  - `org.testcontainers:mysql`（test scope）
- [ ] **Red-3** 创建 `agent-repo/src/main/resources/application.yml`：
  - server.port=8096
  - spring.application.name=agent-repo
  - spring.datasource.url=jdbc:mysql://mysql:3306/agent_repo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
  - spring.datasource.username/password=${MYSQL_USER}/${MYSQL_PASSWORD}
  - spring.jpa.hibernate.ddl-auto=validate
  - spring.flyway.enabled=true / locations=classpath:db/migration
  - grpc.server.port=9098
  - agent-repo.version.maxHistory=20
- [ ] **Red-4** 创建 `db/migration/V1__init_agent_repo.sql`，按 doc 01-database §6 建立 `agent_definition` 与 `agent_version` 两表（含全部业务字段、JSON 列、索引：idx_status_name / idx_agent_id_version）
- [ ] **Red-5** 创建 `AgentRepoApplication.java`（`@SpringBootApplication`）与 `AgentRepoProperties.java`（`@ConfigurationProperties("agent-repo")`）
- [ ] **Red-6** 写冒烟测试 `AgentRepoApplicationTests.contextLoads()` —— 当前预期 FAIL（缺 Entity / Repository）

**Green：**

- [ ] **Green-1** 让 `contextLoads()` 通过：仅保证 Spring 容器能起来（H2 兼容 MySQL 模式，临时禁用 Flyway）
- [ ] **Green-2** 启动一次（`mvn -pl agent-repo spring-boot:run`），日志确认 gRPC server 监听 9098、HTTP 监听 8096

**Refactor：**

- [ ] **Refactor-1** 将 `AgentRepoProperties` 字段加 `@Validated` 与 `@Min/@Max` 约束
- [ ] **Refactor-2** 整理 pom：将 `grpc-spring-boot-starter` 版本号统一到父 pom `dependencyManagement` 对齐其他模块

**Commit：** `feat(agent-repo): T1 scaffold — pom + application.yml + Flyway V1 + 启动类`

---

## Task T2：agent_definition JPA Entity + Repository

**Red：** 先写 entity 字段映射与 repository 查询测试，再实现。

- [ ] **Red-1** 写 `AgentDefinitionTest`：
  - 给定一个含全部字段的 AgentDefinition，保存到 H2，再查回来，断言字段一一对应
  - 给定 ability_tags=`["代码生成","代码审查"]`（JSON 字符串），断言入库后通过 JsonUtils 反序列化为 List<String> 正确
  - 断言 status 默认值 = DRAFT，version 默认值 = 1
- [ ] **Red-2** 写 `AgentDefinitionRepositoryTest`：
  - `findByName` 返回 Optional
  - `findByStatusOrderByCreatedAtDesc` 分页查询
  - `existsByAgentId` 唯一性校验

**Green：**

- [ ] **Green-1** 创建 `model/AgentStatus.java` 枚举（DRAFT / PUBLISHED / DEPRECATED / ARCHIVED）
- [ ] **Green-2** 创建 `model/ReflectionMode.java`（NONE / PERIODIC / ON_FAILURE）与 `model/ModelTier.java`（LITE / STANDARD / ADVANCED）
- [ ] **Green-3** 创建 `model/AgentDefinition.java` `@Entity`，表名 `agent_definition`，含全部 16 个字段（doc 01 §6.1）：
  ```java
  @Entity
  @Table(name = "agent_definition", indexes = {
      @Index(name = "idx_status_name", columnList = "status,name"),
      @Index(name = "idx_agent_id_version", columnList = "agent_id,version")
  })
  @Data @NoArgsConstructor @AllArgsConstructor @Builder
  public class AgentDefinition {
      @Id @Column(name = "agent_id", length = 64)
      private String agentId;
      @Column(nullable = false, length = 128)
      private String name;
      @Column(length = 512)
      private String description;
      @Column(name = "ability_tags", columnDefinition = "JSON")
      private String abilityTags;          // JSON array string
      @Column(name = "scene_tags", columnDefinition = "JSON")
      private String sceneTags;
      @Column(name = "system_prompt", columnDefinition = "TEXT")
      private String systemPrompt;
      @Column(name = "core_constraints", columnDefinition = "TEXT")
      private String coreConstraints;
      @Column(name = "business_config", columnDefinition = "JSON")
      private String businessConfig;
      @Enumerated(EnumType.STRING) @Column(name = "model_tier", length = 16)
      private ModelTier modelTier;
      @Column(name = "max_steps")
      private Integer maxSteps;
      @Column(name = "max_token")
      private Integer maxToken;
      @Column(name = "bound_tools", columnDefinition = "JSON")
      private String boundTools;
      @Column(name = "bound_knowledge_ids", columnDefinition = "JSON")
      private String boundKnowledgeIds;
      @Enumerated(EnumType.STRING) @Column(name = "reflection_mode", length = 16)
      private ReflectionMode reflectionMode;
      @Enumerated(EnumType.STRING) @Column(length = 16, nullable = false)
      private AgentStatus status;
      @Column(nullable = false)
      private Integer version;
  }
  ```
- [ ] **Green-4** 创建 `util/JsonUtils.java`：`toJson(List<String>)` / `fromJson(String)` 静态方法，封装 Jackson `ObjectMapper` 单例
- [ ] **Green-5** 创建 `repository/AgentDefinitionRepository.java`：
  ```java
  public interface AgentDefinitionRepository extends JpaRepository<AgentDefinition, String> {
      Optional<AgentDefinition> findByName(String name);
      Page<AgentDefinition> findByStatusOrderByCreatedAtDesc(AgentStatus status, Pageable pageable);
      boolean existsByAgentId(String agentId);
  }
  ```
- [ ] **Green-6** 全部测试通过

**Refactor：**

- [ ] **Refactor-1** JSON 字段访问在 Entity 中提供 helper：`getAbilityTagsList()` / `setAbilityTagsList(List<String>)`，避免 service 层直接拼字符串
- [ ] **Refactor-2** 抽 `@MappedSuperclass BaseEntity`（created_at / updated_at / created_by / updated_by）给后续模块复用

**Commit：** `feat(agent-repo): T2 agent_definition entity + repository`

---

## Task T3：agent_version 版本快照表

**Red：** 测试 AgentVersionService 在 UpdateAgent 时自动生成版本快照。

- [ ] **Red-1** 写 `AgentVersionServiceTest`：
  - 给定已存在 Agent（version=1），执行 `snapshot(agentId)`，断言 agent_version 表新增一条记录，version=1，snapshot JSON 包含全部业务字段
  - 重复 snapshot 同一 version，断言抛 `AgentVersionConflictException`
  - 同一 agent 调用 snapshot 超过 `maxHistory=20` 次，断言最旧记录被淘汰（FIFO）
- [ ] **Red-2** 写 `AgentVersionRepositoryTest`：
  - `findByAgentIdOrderByVersionDesc(agentId)` 返回分页
  - `findByAgentIdAndVersion(agentId, version)` 精确查询

**Green：**

- [ ] **Green-1** 创建 `model/AgentVersion.java`：
  ```java
  @Entity
  @Table(name = "agent_version",
         indexes = @Index(name = "idx_agent_version", columnList = "agent_id,version"))
  @Data @NoArgsConstructor @AllArgsConstructor @Builder
  public class AgentVersion {
      @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
      private Long id;
      @Column(name = "agent_id", nullable = false, length = 64)
      private String agentId;
      @Column(nullable = false)
      private Integer version;
      @Column(columnDefinition = "JSON", nullable = false)
      private String snapshot;     // 完整 AgentDefinition 序列化
      @Column(name = "change_log", columnDefinition = "TEXT")
      private String changeLog;
      @Column(name = "created_at")
      private LocalDateTime createdAt;
  }
  ```
- [ ] **Green-2** 创建 `repository/AgentVersionRepository.java`：
  ```java
  public interface AgentVersionRepository extends JpaRepository<AgentVersion, Long> {
      Page<AgentVersion> findByAgentIdOrderByVersionDesc(String agentId, Pageable pageable);
      Optional<AgentVersion> findByAgentIdAndVersion(String agentId, Integer version);
      long countByAgentId(String agentId);
      @Modifying
      @Query("DELETE FROM AgentVersion av WHERE av.id IN (" +
             "SELECT av2.id FROM AgentVersion av2 WHERE av2.agentId = :agentId " +
             "ORDER BY av2.version ASC LIMIT :limit)")
      void deleteOldest(@Param("agentId") String agentId, @Param("limit") int limit);
  }
  ```
- [ ] **Green-3** 创建 `service/AgentVersionService.java`：
  - `snapshot(AgentDefinition agent, String changeLog)`：序列化 agent 为 JSON → 写入 agent_version，version = agent.getVersion()；若 count > maxHistory 则淘汰最旧
  - `listVersions(agentId, pageable)` / `getVersion(agentId, version)` / `rollback(agentId, version)`（用 snapshot 反序列化覆盖当前 agent_definition）

**Refactor：**

- [ ] **Refactor-1** 抽 `AgentSnapshotSerializer` 类，独立负责 AgentDefinition ↔ JSON 序列化（含字段白名单、空值处理），便于后续跨模块复用
- [ ] **Refactor-2** 在 service 层加 `@Transactional` 边界，确保快照写入与版本号自增原子

**Commit：** `feat(agent-repo): T3 agent_version snapshot + version service`

---

## Task T4：AgentRepo gRPC 服务（4 RPC）

**Red：** 先写 gRPC 服务端契约测试，确保 4 RPC 全部可调用。

- [ ] **Red-1** 写 `AgentRepoGrpcServiceTest`（InProcess gRPC server）：
  - `CreateAgent`：传入完整字段 → 返回 agent_id；同一 name 再调一次 → 返回 `ALREADY_EXISTS`
  - `GetAgent`：传入存在的 agent_id → 返回完整字段；不存在 → 返回 `NOT_FOUND`
  - `UpdateAgent`：传入存在的 agent_id + 修改后字段 → 返回更新后 agent，version 自增；不存在 → `NOT_FOUND`；状态为 PUBLISHED 时禁止直接改 → `FAILED_PRECONDITION`
  - `ListAgents`：传入分页参数 → 返回分页结果；按 status 过滤
- [ ] **Red-2** 写 `GrpcExceptionAdviceTest`：断言 `BusinessException` 被正确翻译为对应 gRPC Status（NOT_FOUND / ALREADY_EXISTS / FAILED_PRECONDITION / INTERNAL）

**Green：**

- [ ] **Green-1** 创建 `grpc/AgentRepoGrpcService.java`：
  ```java
  @GrpcService
  @Slf4j
  public class AgentRepoGrpcService extends AgentRepoGrpc.AgentRepoImplBase {
      private final AgentRepoService service;
      private final AgentMapper mapper;

      @Override
      public void createAgent(CreateAgentRequest req,
                              StreamObserver<AgentResponse> resp) {
          AgentDefinition entity = mapper.toEntity(req);
          AgentDefinition saved = service.createAgent(entity);
          resp.onNext(mapper.toResponse(saved));
          resp.onCompleted();
      }

      @Override public void getAgent(GetAgentRequest req, StreamObserver<AgentResponse> resp);
      @Override public void updateAgent(UpdateAgentRequest req, StreamObserver<AgentResponse> resp);
      @Override public void listAgents(ListAgentsRequest req, StreamObserver<ListAgentsResponse> resp);
  }
  ```
- [ ] **Green-2** 创建 `service/AgentRepoService.java`：
  - `createAgent(entity)`：校验 name 唯一性（抛 `AgentAlreadyExistsException`）→ 默认 status=DRAFT / version=1 → 保存 → 异步 snapshot 初始版本（changeLog="initial"）
  - `getAgent(agentId)`：查不到抛 `AgentNotFoundException`
  - `updateAgent(entity)`：先查现有 → 校验 status != PUBLISHED / ARCHIVED → version 自增 → 调用 `AgentVersionService.snapshot` 保存旧版本快照 → 保存新 entity → 返回新值
  - `listAgents(filter, pageable)`：组合 status / name 模糊查询
- [ ] **Green-3** 创建 `grpc/AgentMapper.java`：proto `CreateAgentRequest` / `UpdateAgentRequest` ↔ `AgentDefinition` 双向映射，处理 ability_tags 等 JSON 字段
- [ ] **Green-4** 创建 `grpc/GrpcExceptionAdvice.java`：`@GrpcAdvice` + `@GrpcExceptionHandler` 将各业务异常翻译为对应 Status

**Refactor：**

- [ ] **Refactor-1** 抽 `AgentMapper` 为独立类（非 service 内部静态类），便于单测
- [ ] **Refactor-2** `AgentRepoService` 中 `updateAgent` 的状态机校验（DRAFT→PUBLISHED→DEPRECATED→ARCHIVED 允许；PUBLISHED→DRAFT 禁止）抽到 `AgentStatusGuard` 类

**Commit：** `feat(agent-repo): T4 AgentRepo gRPC service (4 RPC) + exception advice`

---

## Task T5：Agent 绑定工具 / 知识库

**Red：** 测试工具与知识库的绑定 / 解绑逻辑。

- [ ] **Red-1** 写 `AgentBindingServiceTest`：
  - `bindTools(agentId, ["tool_a","tool_b"])`：断言 agent_definition.bound_tools 字段更新为 JSON 数组
  - `unbindTool(agentId, "tool_a")`：断言 bound_tools 中移除该 toolId，剩余 ["tool_b"]
  - `bindKnowledge(agentId, ["kb_1"])`：断言 bound_knowledge_ids 更新
  - `bindTools` 传入不存在的 agentId → 抛 `AgentNotFoundException`
  - 同一 toolId 重复绑定 → 幂等，列表去重
- [ ] **Red-2** 写并发绑定测试（两个线程同时 bindTools 同一 agentId）：使用 `@Lock(LockModeType.PESSIMISTIC_WRITE`）保证一致性

**Green：**

- [ ] **Green-1** 创建 `service/AgentBindingService.java`：
  ```java
  @Service
  @RequiredArgsConstructor
  public class AgentBindingService {
      private final AgentDefinitionRepository repo;

      @Transactional
      public AgentDefinition bindTools(String agentId, List<String> toolIds) {
          AgentDefinition agent = repo.findById(agentId)
              .orElseThrow(() -> new AgentNotFoundException(agentId));
          List<String> current = JsonUtils.fromJson(agent.getBoundTools());
          Set<String> merged = new LinkedHashSet<>(current);
          merged.addAll(toolIds);
          agent.setBoundTools(JsonUtils.toJson(new ArrayList<>(merged)));
          return repo.save(agent);
      }

      public AgentDefinition unbindTool(String agentId, String toolId);
      public AgentDefinition bindKnowledge(String agentId, List<String> kbIds);
      public AgentDefinition unbindKnowledge(String agentId, String kbId);
  }
  ```
- [ ] **Green-2** 在 `AgentDefinitionRepository` 加 `@Lock` / `@QueryHints` 乐观锁或悲观锁支持
- [ ] **Green-3** 在 gRPC 层追加 `BindToolsRequest` / `BindKnowledgeRequest` 处理（如 proto 已含则直接用；否则在 `AgentRepoGrpcService` 追加两个内嵌 RPC handler 调用 binding service）

**Refactor：**

- [ ] **Refactor-1** 抽 `BoundAssets` value object（tools + knowledge 合一），减少 entity 中两个 JSON 字段分别操作的重复代码
- [ ] **Refactor-2** 在 `AgentRepoService.updateAgent` 内部检测 bound_tools / bound_knowledge_ids 是否被外部直接修改，若有变更则走 `AgentBindingService` 校验路径，避免绕过校验

**Commit：** `feat(agent-repo): T5 agent binding (bound_tools / bound_knowledge_ids)`

---

## Task T6：agent-repo 集成测试

**Red：** 端到端验证 T1~T5 整体协作。

- [ ] **Red-1** 写 `AgentRepoIntegrationTest`（`@SpringBootTest` + `@Testcontainers` MySQL 8 + InProcess gRPC channel）：
  - 场景 1：CreateAgent → GetAgent → UpdateAgent → 校验 version 自增 + agent_version 表多一条快照
  - 场景 2：ListAgents 分页（page=0 / size=10）+ 按 status=DRAFT 过滤
  - 场景 3：bindTools → unbindTool → 断言 JSON 字段正确流转
  - 场景 4：rollback 到旧版本 → 校验 agent_definition 被覆盖回旧值
  - 场景 5：超出 maxHistory=3 的快照淘汰验证
- [ ] **Red-2** 测试 Flyway 迁移在真实 MySQL 上执行成功（V1__init_agent_repo.sql）

**Green：**

- [ ] **Green-1** 配置 Testcontainers MySQL 8.0 + gRPC InProcess channel
- [ ] **Green-2** 用 Awaitility 处理异步 snapshot 写入断言
- [ ] **Green-3** 全部场景通过；JaCoCo 覆盖率检查（line ≥ 80% / branch ≥ 70%）

**Refactor：**

- [ ] **Refactor-1** 抽 `IntegrationTestSupport` 基类，封装 Testcontainers / gRPC stub 初始化代码，便于 agent-knowledge 模块复用
- [ ] **Refactor-2** 将场景 1-5 的测试数据抽到 `AgentRepoTestFixture` 静态工厂方法

**Commit：** `test(agent-repo): T6 integration tests (Testcontainers MySQL)`

---

## Task T7：agent-knowledge 项目骨架

**Red：** 建立可启动工程壳，类比 T1。

- [ ] **Red-1** 在父 pom `<modules>` 追加 `<module>agent-knowledge</module>`
- [ ] **Red-2** 创建 `agent-knowledge/pom.xml`：
  - `spring-boot-starter-web` / `spring-boot-starter-data-jpa`
  - `mysql-connector-j`
  - `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE`
  - `com.agentplatform:agent-proto` / `com.agentplatform:agent-common`
  - `io.milvus:milvus-sdk-java:2.3.4`
  - `redis.clients:jedis:5.1.0`（缓存 kb 元数据）
  - `org.flywaydb:flyway-mysql`
  - `org.projectlombok:lombok` / `com.fasterxml.jackson.core:jackson-databind`
  - test scope：`spring-boot-starter-test` / `h2` / `org.testcontainers:mysql` / `org.testcontainers:milvus`
- [ ] **Red-3** 创建 `application.yml`：
  - server.port=8098 / spring.application.name=agent-knowledge
  - spring.datasource.url=jdbc:mysql://mysql:3306/agent_knowledge?...
  - spring.flyway.enabled=true / locations=classpath:db/migration
  - grpc.server.port=9100
  - `knowledge.chunk.maxTokens=512` / `knowledge.chunk.overlap=64`
  - `knowledge.search.defaultTopK=5` / `knowledge.search.mmrLambda=0.5`
  - `knowledge.embedding.model=bge-large-zh-v1.5` / `knowledge.embedding.dimension=1024`
  - `knowledge.milvus.host=milvus` / `knowledge.milvus.port=19530`
  - `knowledge.redis.host=redis` / `knowledge.redis.port=6379`
- [ ] **Red-4** 创建 `db/migration/V1__init_agent_knowledge.sql`，按 doc 01 §7 建立 `knowledge_base` 与 `knowledge_chunk` 表（idx_kb_status / idx_kb_id_doc_id 索引）
- [ ] **Red-5** 创建 `AgentKnowledgeApplication.java` + `config/KnowledgeProperties.java` + `config/MilvusConfig.java`
- [ ] **Red-6** 冒烟测试 `AgentKnowledgeApplicationTests.contextLoads()` 当前 FAIL

**Green：**

- [ ] **Green-1** 让 `contextLoads()` 通过（H2 兼容模式 + Flyway 临时禁用）
- [ ] **Green-2** 启动验证 gRPC server 9100 + HTTP 8098 监听

**Refactor：**

- [ ] **Refactor-1** `KnowledgeProperties` 拆分为 `ChunkProperties` / `SearchProperties` / `EmbeddingProperties` / `MilvusProperties` 四个嵌套静态类，对齐 doc 07 §8
- [ ] **Refactor-2** `MilvusConfig` 提供 `MilvusServiceClient` Bean，连接参数走 properties，超时配置（connectTimeoutMs=3000 / rpcDeadlineMs=10000）

**Commit：** `feat(agent-knowledge): T7 scaffold — pom + application.yml + Flyway V1 + 启动类`

---

## Task T8：knowledge_base + knowledge_chunk JPA Entity

**Red：** 先写 entity 测试。

- [ ] **Red-1** 写 `KnowledgeBaseTest`：
  - 保存一个 KnowledgeBase，断言 status 默认 = CREATING
  - 断言 doc_count / chunk_count 默认 0，dimension 默认 1024
- [ ] **Red-2** 写 `KnowledgeChunkTest`：
  - 保存一个 KnowledgeChunk，断言 token_count 正确入库
  - 断言 status 默认 = PENDING（待向量化）
- [ ] **Red-3** 写 repository 测试：
  - `KnowledgeBaseRepository.findByStatus(status, pageable)`
  - `KnowledgeChunkRepository.findByKbIdAndDocId(kbId, docId)`
  - `KnowledgeChunkRepository.countByKbId(kbId)`

**Green：**

- [ ] **Green-1** 创建 `model/KnowledgeStatus.java`（CREATING / READY / UPDATING / ERROR / DELETED）
- [ ] **Green-2** 创建 `model/IngestStatus.java`（PENDING / VECTORIZED / FAILED）
- [ ] **Green-3** 创建 `model/KnowledgeBase.java`：
  ```java
  @Entity
  @Table(name = "knowledge_base",
         indexes = @Index(name = "idx_kb_status", columnList = "status,name"))
  @Data @NoArgsConstructor @AllArgsConstructor @Builder
  public class KnowledgeBase {
      @Id @Column(name = "kb_id", length = 64)
      private String kbId;
      @Column(nullable = false, length = 128)
      private String name;
      @Column(length = 512)
      private String description;
      @Column(name = "doc_count", nullable = false)
      private Integer docCount;          // default 0
      @Column(name = "chunk_count", nullable = false)
      private Integer chunkCount;        // default 0
      @Column(name = "embedding_model", length = 64)
      private String embeddingModel;     // bge-large-zh-v1.5
      @Column(nullable = false)
      private Integer dimension;         // 1024
      @Enumerated(EnumType.STRING) @Column(length = 16, nullable = false)
      private KnowledgeStatus status;
  }
  ```
- [ ] **Green-4** 创建 `model/KnowledgeChunk.java`：
  ```java
  @Entity
  @Table(name = "knowledge_chunk",
         indexes = @Index(name = "idx_kb_id_doc_id", columnList = "kb_id,doc_id"))
  @Data @NoArgsConstructor @AllArgsConstructor @Builder
  public class KnowledgeChunk {
      @Id @Column(name = "chunk_id", length = 64)
      private String chunkId;           // UUID
      @Column(name = "doc_id", nullable = false, length = 64)
      private String docId;
      @Column(name = "kb_id", nullable = false, length = 64)
      private String kbId;
      @Column(columnDefinition = "TEXT", nullable = false)
      private String content;
      @Column(name = "token_count")
      private Integer tokenCount;
      @Column(name = "embedding_id", length = 128)
      private String embeddingId;       // Milvus primary key
      @Enumerated(EnumType.STRING) @Column(length = 16, nullable = false)
      private IngestStatus status;
  }
  ```
- [ ] **Green-5** 创建 `KnowledgeBaseRepository` / `KnowledgeChunkRepository`

**Refactor：**

- [ ] **Refactor-1** 抽 `BaseEntity` 复用（与 agent-repo 共享 `created_at` / `updated_at` 字段，可提到 agent-common 模块）
- [ ] **Refactor-2** 在 KnowledgeChunk 加 `prePersist` 钩子设置默认 status=PENDING

**Commit：** `feat(agent-knowledge): T8 knowledge_base + knowledge_chunk entities`

---

## Task T9：文档导入与分块

**Red：** 测试 DocumentIngestor 完整流程（不含向量化的下游部分）。

- [ ] **Red-1** 写 `TokenChunkStrategyTest`：
  - 给定 1200 token 文本，maxTokens=512 / overlap=64，断言分出 3 个 chunk，每个 chunk token 数 ≤512
  - 给定空字符串，断言返回空 List
  - 给定 100 token 短文本（< maxTokens），断言返回 1 个 chunk
  - 给定段落分隔文本，断言按段落优先切分（段落 ≤ maxTokens 时保留完整段落）
- [ ] **Red-2** 写 `DocumentIngestorTest`：
  - `ingestDocument(kbId, docId, content)` → 创建 KnowledgeChunk 记录 + 更新 knowledge_base.doc_count / chunk_count
  - 中途失败时已创建的 chunk 保留（事务内回滚或标记 FAILED，根据策略二选一）
  - 同一 docId 重复 ingest → 先删除旧 chunk 再重新分块（idempotent）
- [ ] **Red-3** 写 `TokenCounterTest`：
  - 中英文混合文本，断言 token 数估算接近（heuristic：中文字符按 1.5 token / 英文按空格分词近似）

**Green：**

- [ ] **Green-1** 创建 `service/ChunkStrategy.java` 接口：`List<String> split(String content)`
- [ ] **Green-2** 创建 `service/TokenChunkStrategy.java`：
  ```java
  public class TokenChunkStrategy implements ChunkStrategy {
      private final int maxTokens;
      private final int overlap;
      private final TokenCounter tokenCounter;

      @Override
      public List<String> split(String content) {
          if (content == null || content.isBlank()) return List.of();
          List<String> paragraphs = splitByParagraph(content);
          List<String> chunks = new ArrayList<>();
          StringBuilder current = new StringBuilder();
          int currentTokens = 0;
          for (String para : paragraphs) {
              int paraTokens = tokenCounter.count(para);
              if (paraTokens > maxTokens) {
                  // 段落本身超长 → 按硬切分
                  flushChunk(chunks, current, currentTokens);
                  chunks.addAll(hardSplit(para));
                  current.setLength(0); currentTokens = 0;
                  continue;
              }
              if (currentTokens + paraTokens > maxTokens) {
                  flushChunk(chunks, current, currentTokens);
                  current = new StringBuilder(appendWithOverlap(current, para, overlap));
                  currentTokens = paraTokens;
              } else {
                  current.append(para); currentTokens += paraTokens;
              }
          }
          flushChunk(chunks, current, currentTokens);
          return chunks;
      }
  }
  ```
- [ ] **Green-3** 创建 `util/TokenCounter.java`：中英文 heuristic 实现（中文按字符 1.5x / 英文按 whitespace 分词数）
- [ ] **Green-4** 创建 `service/DocumentIngestor.java`：
  ```java
  @Service
  @RequiredArgsConstructor
  @Slf4j
  public class DocumentIngestor {
      private final ChunkStrategy chunkStrategy;
      private final KnowledgeChunkRepository chunkRepo;
      private final KnowledgeBaseRepository kbRepo;

      @Transactional
      public List<KnowledgeChunk> ingestDocument(String kbId, String docId, String content) {
          // 1. 删除 docId 旧 chunk（idempotent）
          chunkRepo.deleteByKbIdAndDocId(kbId, docId);
          // 2. 分块
          List<String> texts = chunkStrategy.split(content);
          // 3. 创建 chunk 记录（status=PENDING）
          List<KnowledgeChunk> chunks = texts.stream().map(t ->
              KnowledgeChunk.builder()
                  .chunkId(UUID.randomUUID().toString())
                  .kbId(kbId).docId(docId)
                  .content(t)
                  .tokenCount(tokenCounter.count(t))
                  .status(IngestStatus.PENDING)
                  .build()
          ).collect(Collectors.toList());
          chunkRepo.saveAll(chunks);
          // 4. 更新 kb 统计
          kbRepo.incrementDocCount(kbId);
          kbRepo.addChunkCount(kbId, chunks.size());
          return chunks;
      }
  }
  ```
- [ ] **Green-5** 在 `KnowledgeBaseRepository` 加 `@Modifying` 方法 `deleteByKbIdAndDocId` / `incrementDocCount` / `addChunkCount`

**Refactor：**

- [ ] **Refactor-1** 抽 `ChunkStrategy` 接口，便于后续切换 `ParagraphChunkStrategy` / `SlidingWindowChunkStrategy`
- [ ] **Refactor-2** `TokenCounter` 抽接口，便于后续切换为 tiktoken 精确实现
- [ ] **Refactor-3** 长文档分块改为流式处理，避免一次性把超大文本加载进内存（用 `CharSequence` 切片）

**Commit：** `feat(agent-knowledge): T9 document ingestor + token chunk strategy`

---

## Task T10：向量化与 Milvus 写入

**Red：** 测试 EmbeddingService + MilvusVectorStore 协作。

- [ ] **Red-1** 写 `DefaultEmbeddingServiceTest`：
  - 给定一段文本，调用 `embed(text)`，返回 `float[1024]`，断言维度正确
  - 批量 `embed(List<String>)` 返回 List<float[]>，长度匹配
  - Mock 外部 embedding API（HTTP 调用），断言失败时抛 `EmbeddingFailedException`
- [ ] **Red-2** 写 `MilvusVectorStoreTest`（Testcontainers Milvus 2.3）：
  - `ensureCollection(kbId, dimension)`：collection 不存在时创建 `kb_{kbId}`，HNSW 索引
  - `upsert(kbId, chunkId, vector, content)`：写入向量，断言可按 id 查回
  - `search(kbId, queryVector, topK)`：插入 N=10 条向量，查询返回 topK=5，断言相似度排序正确
  - `deleteByDocId(kbId, docId)`：删除某文档全部向量，断言 collection 中无残留
- [ ] **Red-3** 写 `MmrRerankerTest`：
  - 给定 5 条候选（带相似度分数与向量），mmrLambda=0.5，topK=3
  - 断言输出按 MMR 公式（max(sim - λ·max_sim_to_selected)）排序，过滤冗余相似结果
- [ ] **Red-4** 写端到端 `IngestToVectorStoreTest`：
  - DocumentIngestor.ingestDocument → EmbeddingService.embed → MilvusVectorStore.upsert → 断言 chunk status=VECTORIZED，embedding_id 非空

**Green：**

- [ ] **Green-1** 创建 `service/EmbeddingService.java` 接口：`float[] embed(String text)` / `List<float[]> embedBatch(List<String> texts)`
- [ ] **Green-2** 创建 `service/DefaultEmbeddingService.java`：
  - 通过 `RestTemplate` 或 `WebClient` 调用外部 embedding API（配置 `knowledge.embedding.endpoint`）
  - 默认占位实现：当 endpoint 未配置时返回随机向量（仅用于本地测试，生产强制要求配置）
  - 失败重试 3 次（指数退避），最终失败抛 `EmbeddingFailedException`
- [ ] **Green-3** 创建 `service/VectorStoreService.java` 接口：
  ```java
  public interface VectorStoreService {
      void ensureCollection(String kbId, int dimension);
      void upsert(String kbId, String chunkId, float[] vector, Map<String,String> metadata);
      List<SearchResult> search(String kbId, float[] queryVector, int topK);
      void deleteByDocId(String kbId, String docId);
      void dropCollection(String kbId);
  }
  ```
- [ ] **Green-4** 创建 `service/MilvusVectorStore.java`：
  - 注入 `MilvusServiceClient`
  - `ensureCollection`：检查 `hasCollection`，不存在则 `createCollection`（字段：chunk_id VARCHAR 主键 / embedding FLOAT_VECTOR dim=dimension / content VARCHAR / doc_id VARCHAR），HNSW 索引（M=16 / efConstruction=200），加载到内存
  - `upsert`：构造 `InsertParam`，调用 `client.upsert`
  - `search`：构造 `SearchParam`，topK + HNSW `ef=64`，返回 `SearchResult(chunkId, score, metadata)`
  - `deleteByDocId`：`delete` with expr `doc_id == "..."`
- [ ] **Green-5** 创建 `service/SearchResult.java` POJO（chunkId / score / content / docId）
- [ ] **Green-6** 创建 `util/MmrReranker.java`：
  ```java
  public class MmrReranker {
      private final float lambda;

      public List<SearchResult> rerank(List<SearchResult> candidates,
                                       int topK,
                                       Function<String, float[]> vectorFetcher) {
          // 标准 MMR：score = λ * relevance - (1-λ) * max_sim_to_selected
          // 迭代挑选 score 最高的，更新剩余 candidate 的 max_sim_to_selected
      }
  }
  ```
- [ ] **Green-7** 在 `DocumentIngestor` 末尾接入 `EmbeddingService` + `MilvusVectorStore`：
  - 分块后逐批 embedding（批大小 32）
  - 调用 `vectorStore.upsert` 写入 Milvus
  - 更新 chunk.embedding_id / status=VECTORIZED
  - 任一失败则该 chunk status=FAILED，不影响其他 chunk（部分成功策略）

**Refactor：**

- [ ] **Refactor-1** EmbeddingService 的批量调用抽 `EmbeddingBatchExecutor`，封装批大小 / 重试 / 限流，独立可测
- [ ] **Refactor-2** `MilvusVectorStore` 抽 `MilvusSchemaBuilder`，便于不同 kb 共用 schema 构造逻辑
- [ ] **Refactor-3** MMR 算法抽到独立包 `com.agent.knowledge.rerank`，预留 `Reranker` 接口给后续 cross-encoder 重排实现

**Commit：** `feat(agent-knowledge): T10 embedding + milvus vector store + MMR reranker`

---

## Task T11：KnowledgeBase gRPC 服务（4 RPC）

**Red：** 写 gRPC 契约测试。

- [ ] **Red-1** 写 `KnowledgeBaseGrpcServiceTest`（InProcess gRPC）：
  - `IngestDocument`：传入 kb_id / doc_id / content → 返回 chunk_id 列表；kb 不存在 → `NOT_FOUND`；content 为空 → `INVALID_ARGUMENT`
  - `SearchChunks`：传入 kb_id / query / topK → 返回 List<SearchChunk>，按 score 降序
  - `ListBases`：分页返回 KnowledgeBase 列表，按 status 过滤
  - `DeleteBase`：传入 kb_id → 删除 knowledge_base 行 + 关联 knowledge_chunk + Milvus collection；不存在 → `NOT_FOUND`；kb 被某 Agent 引用 → `FAILED_PRECONDITION`（KB_IN_USE）
- [ ] **Red-2** 写 `GrpcExceptionAdviceTest`：业务异常 → gRPC Status 翻译

**Green：**

- [ ] **Green-1** 创建 `grpc/KnowledgeBaseGrpcService.java`：
  ```java
  @GrpcService
  @Slf4j
  public class KnowledgeBaseGrpcService extends KnowledgeBaseGrpc.KnowledgeBaseImplBase {
      private final KnowledgeBaseService service;
      private final KnowledgeMapper mapper;

      @Override
      public void ingestDocument(IngestDocumentRequest req,
                                 StreamObserver<IngestResponse> resp) {
          List<String> chunkIds = service.ingest(req.getKbId(), req.getDocId(), req.getContent());
          resp.onNext(IngestResponse.newBuilder()
              .addAllChunkIds(chunkIds)
              .setSuccess(true)
              .build());
          resp.onCompleted();
      }

      @Override
      public void searchChunks(SearchChunksRequest req,
                               StreamObserver<SearchChunksResponse> resp) {
          List<SearchResult> results = service.search(
              req.getKbId(), req.getQuery(), req.getTopK());
          resp.onNext(mapper.toSearchResponse(results));
          resp.onCompleted();
      }

      @Override public void listBases(ListBasesRequest req, StreamObserver<ListBasesResponse> resp);
      @Override public void deleteBase(DeleteBaseRequest req, StreamObserver<DeleteBaseResponse> resp);
  }
  ```
- [ ] **Green-2** 创建 `service/KnowledgeBaseService.java`：
  - `createBase(name, description, embeddingModel, dimension)`：创建 knowledge_base 行，status=CREATING → 调用 `MilvusVectorStore.ensureCollection` → status=READY
  - `ingest(kbId, docId, content)`：校验 kb.status=READY → 调用 `DocumentIngestor.ingestDocument` → 触发向量化 → 返回 chunkId 列表
  - `search(kbId, query, topK)`：embedding(query) → `MilvusVectorStore.search` → MMR rerank → 返回 List<SearchResult>
  - `listBases(filter, pageable)`：分页查询
  - `deleteBase(kbId)`：校验未被 Agent 引用（通过 agent-repo 的 ListAgents by bound_knowledge_ids，或本地缓存反向索引）→ 删除 Milvus collection → 删除 knowledge_chunk → 删除 knowledge_base 行
- [ ] **Green-3** 创建 `grpc/KnowledgeMapper.java`：proto ↔ Entity / SearchResult 映射
- [ ] **Green-4** 创建 `grpc/GrpcExceptionAdvice.java`：异常翻译（KB_NOT_FOUND / KB_IN_USE / DOC_INGEST_FAILED / EMBEDDING_FAILED / VECTOR_STORE_ERROR）

**Refactor：**

- [ ] **Refactor-1** `KnowledgeBaseService.deleteBase` 中"KB 引用检查"抽 `KbReferenceChecker` 接口，默认实现调用 agent-repo gRPC `ListAgents`（带 fallback 本地缓存），便于解耦
- [ ] **Refactor-2** `search` 中 embedding + vector search + MMR 三步抽 `KnowledgeSearchPipeline`，每步可独立替换
- [ ] **Refactor-3** 在 gRPC 服务端加拦截器记录 `search` latency / topK 分布到 Prometheus 指标

**Commit：** `feat(agent-knowledge): T11 KnowledgeBase gRPC service (4 RPC) + exception advice`

---

## Task T12：agent-knowledge 集成测试

**Red：** 端到端验证 T7~T11 整体协作（Testcontainers Milvus + MySQL + Redis）。

- [ ] **Red-1** 写 `AgentKnowledgeIntegrationTest`（`@SpringBootTest` + `@Testcontainers` Milvus 2.3 + MySQL 8 + Redis 7 + InProcess gRPC）：
  - 场景 1：创建 kb → IngestDocument（2000 token 中文文本）→ 断言分块数符合 512/64 配置 → SearchChunks 返回 topK=5 结果，score 降序
  - 场景 2：同一 docId 重复 ingest → chunk_count 不重复累加（idempotent）
  - 场景 3：ListBases 分页（page=0 / size=10）+ 按 status=READY 过滤
  - 场景 4：DeleteBase → 断言 knowledge_base 行删除 + knowledge_chunk 行删除 + Milvus collection drop
  - 场景 5：DeleteBase 时模拟 kb 被 Agent 引用（设置本地缓存反向索引）→ 返回 `KB_IN_USE` FAILED_PRECONDITION
  - 场景 6：IngestDocument 时 embedding API 失败 → chunk status=FAILED，kb.status=ERROR
- [ ] **Red-2** 性能基线：单文档 5000 token ingest → end-to-end < 5s（Milvus Testcontainers 本地门槛）

**Green：**

- [ ] **Green-1** 配置 Testcontainers Milvus 2.3（standalone 模式）+ MySQL 8 + Redis 7
- [ ] **Green-2** 复用 `IntegrationTestSupport`（T6 重构产物）
- [ ] **Green-3** Mock 外部 embedding API（用 `MockWebServer`），返回固定 1024 维向量
- [ ] **Green-4** 用 Awaitility 等待异步向量化完成（chunk status=VECTORIZED）
- [ ] **Green-5** 全部场景通过；JaCoCo 覆盖率检查（line ≥ 80% / branch ≥ 70%）

**Refactor：**

- [ ] **Refactor-1** 把场景 1-6 的 fixture 抽到 `KnowledgeTestFixture`
- [ ] **Refactor-2** 把 EmbeddingService 的 MockWebServer 配置抽 `EmbeddingMockSupport`，便于其他模块复用
- [ ] **Refactor-3** 在 README 或 docs 中补充"本地启动 Milvus 测试"指引（Testcontainers 自动拉镜像）

**Commit：** `test(agent-knowledge): T12 integration tests (Testcontainers Milvus + MySQL)`

---

## 测试矩阵

### UT-REPO（agent-repo 单元测试，对齐 `docs/tests/unit-test-cases.md` §11）

| 用例 ID | 模块 | 场景 | 断言 | 关联 Task |
|---|---|---|---|---|
| UT-REPO-001 | AgentDefinitionRepository | 创建并按 agent_id 查询 | 入库后 findByAgentId 返回完整字段；status 默认 DRAFT；version 默认 1 | T2 |
| UT-REPO-002 | AgentDefinitionRepository | name 唯一性 | 重名第二次保存抛 `DataIntegrityViolationException` | T2 |
| UT-REPO-003 | AgentVersionService | snapshot 生成 | snapshot 后 agent_version 表新增一条；snapshot JSON 包含全部字段；version 与 agent 当前一致 | T3 |
| UT-REPO-004 | AgentVersionService | 历史版本淘汰 | maxHistory=3 时第 4 次 snapshot 后最旧记录被删除 | T3 |
| UT-REPO-005 | AgentRepoService.createAgent | 正常创建 | 返回 agent_id；初始 version=1；初始 status=DRAFT；agent_version 表同时新增一条 initial snapshot | T4 |
| UT-REPO-006 | AgentRepoService.updateAgent | 状态校验 | status=PUBLISHED 时直接 update 抛 `AgentStatusConflictException`；DRAFT 状态允许 update 且 version 自增 | T4 |
| UT-REPO-007 | AgentRepoGrpcService | CreateAgent RPC | 完整字段请求 → AgentResponse 包含所有字段；重复 name → Status `ALREADY_EXISTS` | T4 |
| UT-REPO-008 | AgentRepoGrpcService | GetAgent RPC | 不存在 agent_id → Status `NOT_FOUND` | T4 |
| UT-REPO-009 | AgentRepoGrpcService | ListAgents RPC | 10 条数据 + page=0/size=5 → 返回 5 条 + total=10 | T4 |
| UT-REPO-010 | AgentBindingService.bindTools | 绑定工具 | 绑定 ["a","b"] 后 bound_tools JSON 含两项；重复绑定 ["a","c"] 后为 ["a","b","c"]（去重） | T5 |
| UT-REPO-011 | AgentBindingService.unbindTool | 解绑工具 | 解绑 "a" 后 bound_tools 剩 ["b","c"] | T5 |
| UT-REPO-012 | AgentBindingService.bindKnowledge | 绑定知识库 | 绑定 ["kb_1"] 后 bound_knowledge_ids JSON 含一项 | T5 |
| UT-REPO-013 | AgentRepoService.rollback | 版本回滚 | rollback 到 version=1 后 agent_definition 全字段被旧 snapshot 覆盖，version 字段保持当前值（不回退 version 计数器） | T3 |
| UT-REPO-014 | GrpcExceptionAdvice | 异常翻译 | `AgentNotFoundException` → Status.NOT_FOUND；`AgentAlreadyExistsException` → Status.ALREADY_EXISTS；`AgentStatusConflictException` → Status.FAILED_PRECONDITION | T4 |
| UT-REPO-015 | AgentRepoIntegrationTest | 端到端 | Create → Update → Get → List 全链路通过 Testcontainers MySQL | T6 |

### UT-KB（agent-knowledge 单元测试，对齐 `docs/tests/unit-test-cases.md` §12）

| 用例 ID | 模块 | 场景 | 断言 | 关联 Task |
|---|---|---|---|---|
| UT-KB-001 | KnowledgeBaseRepository | 创建并查询 | 入库后 status 默认 CREATING；dimension 默认 1024；doc_count/chunk_count 默认 0 | T8 |
| UT-KB-002 | KnowledgeChunkRepository | 按 kbId + docId 查询 | 入库 N 条同一 docId 的 chunk → `findByKbIdAndDocId` 返回 N 条 | T8 |
| UT-KB-003 | TokenChunkStrategy | 标准分块 | 1200 token 文本 / maxTokens=512 / overlap=64 → 3 个 chunk，每个 ≤ 512 token | T9 |
| UT-KB-004 | TokenChunkStrategy | 段落优先 | 含 3 个段落每段 200 token → 返回 3 个 chunk，每段完整不切分 | T9 |
| UT-KB-005 | TokenChunkStrategy | 空文本 | 空字符串 / null → 返回空 List | T9 |
| UT-KB-006 | TokenCounter | 中英文混合 | "Hello 世界 abc" → token 数介于 3~5 之间（heuristic） | T9 |
| UT-KB-007 | DocumentIngestor | 正常导入 | 1200 token 文本 → 创建 3 条 chunk + kb.doc_count +1 / chunk_count +3 | T9 |
| UT-KB-008 | DocumentIngestor | 幂等导入 | 同一 docId 第二次 ingest → 旧 chunk 删除，新 chunk 创建，chunk_count 不重复累加 | T9 |
| UT-KB-009 | DefaultEmbeddingService | 单条向量化 | `embed("text")` 返回 float[1024]；维度匹配 | T10 |
| UT-KB-010 | DefaultEmbeddingService | 批量向量化 | `embedBatch(3 条)` 返回 List<float[]> 长度 3 | T10 |
| UT-KB-011 | DefaultEmbeddingService | 失败重试 | Mock API 3 次失败 → 抛 `EmbeddingFailedException` | T10 |
| UT-KB-012 | MilvusVectorStore | ensureCollection | collection 不存在时创建；已存在时跳过 | T10 |
| UT-KB-013 | MilvusVectorStore | upsert + search | 写入 10 条向量 → search topK=5 返回 5 条按 score 降序 | T10 |
| UT-KB-014 | MilvusVectorStore | deleteByDocId | 删除某 docId 全部向量 → 再 search 该 docId 结果为空 | T10 |
| UT-KB-015 | MmrReranker | 多样性重排 | 5 条候选 / lambda=0.5 / topK=3 → 输出按 MMR 公式排序，过滤冗余 | T10 |
| UT-KB-016 | KnowledgeBaseService.ingest | 完整流程 | ingest → chunk 创建 → embedding 完成 → Milvus 写入 → chunk status=VECTORIZED | T11 |
| UT-KB-017 | KnowledgeBaseService.search | 完整检索 | query embedding → Milvus search → MMR → 返回 topK | T11 |
| UT-KB-018 | KnowledgeBaseService.deleteBase | 正常删除 | kb 行 + chunk 行 + Milvus collection 全部删除 | T11 |
| UT-KB-019 | KnowledgeBaseService.deleteBase | 引用检查 | kb 被引用 → 抛 `KbInUseException` | T11 |
| UT-KB-020 | KnowledgeBaseGrpcService | IngestDocument RPC | 完整请求 → IngestResponse 含 chunkIds；kb 不存在 → `NOT_FOUND` | T11 |
| UT-KB-021 | KnowledgeBaseGrpcService | SearchChunks RPC | 返回 SearchChunksResponse，按 score 降序 | T11 |
| UT-KB-022 | KnowledgeBaseGrpcService | ListBases RPC | 分页返回 + status 过滤 | T11 |
| UT-KB-023 | KnowledgeBaseGrpcService | DeleteBase RPC | 不存在 kb → `NOT_FOUND`；被引用 → `FAILED_PRECONDITION` | T11 |
| UT-KB-024 | GrpcExceptionAdvice | 异常翻译 | `KbNotFoundException` → `NOT_FOUND`；`KbInUseException` → `FAILED_PRECONDITION`；`EmbeddingFailedException` → `INTERNAL`；`VectorStoreException` → `INTERNAL` | T11 |
| UT-KB-025 | AgentKnowledgeIntegrationTest | 端到端 | Create → Ingest → Search → Delete 全链路通过 Testcontainers Milvus + MySQL | T12 |

---

## 验收标准

### 代码覆盖率

- [ ] **JaCoCo line coverage ≥ 80%**（两模块分别验证：`mvn -pl agent-repo,agent-knowledge jacoco:report`）
- [ ] **JaCoCo branch coverage ≥ 70%**
- [ ] 关键路径（gRPC 服务方法 / DocumentIngestor / MilvusVectorStore / AgentVersionService）line coverage ≥ 90%

### Task 完成度

- [ ] **T1~T12 共 12 个 Task 全部完成**（每个 Task 的 Red/Green/Refactor/Commit 全部勾选）
- [ ] 每个 Task 至少 1 个 commit，commit message 遵循 `feat(scope): T{n} ...` / `test(scope): T{n} ...` 格式
- [ ] 父 pom `<modules>` 已追加 `agent-repo` + `agent-knowledge`

### gRPC 契约

- [ ] `agent-proto` 中 `repo.proto` 定义 4 个 RPC（CreateAgent / GetAgent / UpdateAgent / ListAgents）
- [ ] `agent-proto` 中 `knowledge.proto` 定义 4 个 RPC（IngestDocument / SearchChunks / ListBases / DeleteBase）
- [ ] 两模块生成的 stub 能被 gRPC client 正确调用（InProcess 测试通过）

### 数据库

- [ ] `V1__init_agent_repo.sql` 在 MySQL 8 上执行成功，包含 agent_definition（16 字段）+ agent_version（5 字段）+ 索引
- [ ] `V1__init_agent_knowledge.sql` 在 MySQL 8 上执行成功，包含 knowledge_base（8 字段）+ knowledge_chunk（7 字段）+ 索引
- [ ] JSON 字段（ability_tags / scene_tags / business_config / bound_tools / bound_knowledge_ids / snapshot）可正确读写

### 集成测试

- [ ] `AgentRepoIntegrationTest` 5 个场景全部通过
- [ ] `AgentKnowledgeIntegrationTest` 6 个场景全部通过
- [ ] Testcontainers 自动拉取 mysql:8.0 / milvusdb/milvus:v2.3.4 / redis:7 镜像（CI 环境需预拉或允许外网）

### 文档对齐

- [ ] 端口 8096 / 8098 / 9098 / 9100 与 doc 00-overview §3.1 一致
- [ ] 数据库表结构与 doc 01-database §6 / §7 一致
- [ ] 错误码域与 doc 06 §6 / doc 07 §6 一致

---

## Self-review checklist

### 设计契约一致性

- [ ] 所有 JSON 字段（ability_tags / scene_tags / business_config / bound_tools / bound_knowledge_ids / snapshot）通过 `JsonUtils` 统一序列化，不直接拼字符串
- [ ] `AgentStatus` 状态机：DRAFT → PUBLISHED → DEPRECATED → ARCHIVED 单向流转；PUBLISHED 后修改必须先 rollback 到 DRAFT 或新建版本
- [ ] `KnowledgeStatus` 状态机：CREATING → READY → UPDATING → READY；任何状态 → ERROR；READY/ERROR → DELETED
- [ ] `AgentRepoService.updateAgent` 每次更新自动生成 snapshot，不依赖调用方显式触发
- [ ] `DocumentIngestor.ingestDocument` 幂等：同 docId 重复 ingest 不产生重复 chunk

### 性能与资源

- [ ] EmbeddingService 批量调用，批大小默认 32（可配 `knowledge.embedding.batchSize`）
- [ ] Milvus search `ef=64`（HNSW 参数），topK ≤ 50 防止过大结果集
- [ ] 长文档分块流式处理，避免一次性加载全文到内存
- [ ] `KnowledgeBase` 元数据通过 Jedis 缓存（TTL=300s），减少 MySQL 查询

### 可观测性

- [ ] gRPC 服务方法加 `@GrpcInterceptor` 记录 trace / latency / 错误率到 Prometheus
- [ ] DocumentIngestor 关键步骤（分块 / embedding / upsert）打 INFO 日志，包含 kb_id / doc_id / chunk_count
- [ ] Milvus 操作失败打 ERROR 日志，包含 collection 名 / 错误详情

### 错误处理

- [ ] 所有业务异常继承 `agent-common.BusinessException`，携带 errorCode + message + context
- [ ] gRPC `GrpcExceptionAdvice` 覆盖全部业务异常类型，翻译为对应 Status
- [ ] EmbeddingService / MilvusVectorStore 失败时记录详细错误上下文（kb_id / doc_id / chunk_id），便于排查

### 安全性

- [ ] Milvus 连接参数通过 properties / Vault 注入，不硬编码
- [ ] gRPC 服务端开启 TLS（生产 profile，dev 可关）
- [ ] KB 删除前必须通过引用检查，防止 Agent 引用悬空
- [ ] Agent 定义中的 system_prompt / core_constraints 在日志输出时脱敏（含敏感指令时打 `***`）

### 可维护性

- [ ] `ChunkStrategy` / `EmbeddingService` / `VectorStoreService` / `Reranker` 四个接口都有默认实现 + 可替换扩展点
- [ ] 配置参数集中在 `KnowledgeProperties` / `AgentRepoProperties`，避免散落
- [ ] `IntegrationTestSupport` / `KnowledgeTestFixture` / `AgentRepoTestFixture` 跨模块可复用
- [ ] README / docs 中补充"如何切换 embedding 模型" / "如何新增分块策略" / "如何对接其他向量库"扩展指南

### 与其他模块协作

- [ ] `agent-repo` 通过 `bound_knowledge_ids` 引用 kb_id，但不直接调用 agent-knowledge gRPC（仅作为元数据存储）
- [ ] `agent-knowledge` 的 `KbReferenceChecker` 通过 agent-repo gRPC `ListAgents` 反查引用关系（带本地缓存 fallback）
- [ ] `agent-runtime` 通过两模块 gRPC 接口加载 Agent 定义与检索知识（运行时调用方，本计划不实现）
- [ ] 端口 / 错误码 / proto 包名与 doc 00-overview / doc 01-database 全表对齐，无 drift

---

## 实施顺序建议

| 阶段 | Tasks | 预估工时 | 里程碑 |
|---|---|---|---|
| 阶段 1 | T1 + T2 + T3 | 1.0 D | agent-repo 数据层完成，可 CRUD + 版本快照 |
| 阶段 2 | T4 + T5 | 1.0 D | agent-repo gRPC + 绑定能力完成 |
| 阶段 3 | T6 | 0.5 D | agent-repo 集成测试通过，覆盖率达标 |
| 阶段 4 | T7 + T8 | 0.5 D | agent-knowledge 数据层完成 |
| 阶段 5 | T9 + T10 | 1.5 D | 分块 + 向量化 + Milvus 集成完成（核心难点） |
| 阶段 6 | T11 + T12 | 1.0 D | agent-knowledge gRPC + 集成测试通过，覆盖率达标 |
| **合计** | **T1~T12** | **5.5 D** | 两模块全部交付 |

---

## 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| Milvus Testcontainers 在 Windows 本地启动慢 / 镜像大 | T10 / T12 阻塞 | 提前 `docker pull milvusdb/milvus:v2.3.4`；提供 `@EnabledIfEnvironmentVariable` 开关，CI 才跑 Milvus 集成测试 |
| Embedding API 不稳定 / 需要外部服务 | T10 阻塞 | 提供 `DefaultEmbeddingService` 占位实现（随机向量），仅用于本地联调；生产前必须替换为真实实现 |
| agent-proto 中 repo.proto / knowledge.proto 未定义 | T4 / T11 阻塞 | 在本计划开始前先在 `agent-proto` 模块补两份 proto 文件（与本计划对齐） |
| JSON 字段在 MySQL 5.7 与 8.0 行为差异 | T2 / T5 数据完整性 | 锁定 MySQL 8.0+，application.yml 中明确 `useUnicode=true&characterEncoding=utf8` |
| MMR 重排性能在大候选集下退化 | T10 / T11 | 限制 MMR 输入候选数 ≤ 50（Milvus search topK 上限）；超出则先做简单相似度截断 |
| 跨模块 KB 引用检查需要调 agent-repo gRPC | T11 复杂度 | 默认走本地缓存反向索引（启动时全量加载 + 增量更新），gRPC 调用作为 fallback |

---

## 附录：proto 契约草案

### repo.proto（节选）

```protobuf
syntax = "proto3";
package agentplatform.repo.v1;

import "common.proto";

service AgentRepo {
  rpc CreateAgent(CreateAgentRequest) returns (AgentResponse);
  rpc GetAgent(GetAgentRequest) returns (AgentResponse);
  rpc UpdateAgent(UpdateAgentRequest) returns (AgentResponse);
  rpc ListAgents(ListAgentsRequest) returns (ListAgentsResponse);
}

message CreateAgentRequest {
  string agent_id = 1;
  string name = 2;
  string description = 3;
  repeated string ability_tags = 4;
  repeated string scene_tags = 5;
  string system_prompt = 6;
  string core_constraints = 7;
  string business_config = 8;        // JSON
  string model_tier = 9;             // LITE/STANDARD/ADVANCED
  int32 max_steps = 10;
  int32 max_token = 11;
  repeated string bound_tools = 12;
  repeated string bound_knowledge_ids = 13;
  string reflection_mode = 14;       // NONE/PERIODIC/ON_FAILURE
}

message AgentResponse {
  string agent_id = 1;
  string name = 2;
  // ... 全部字段
  string status = 15;
  int32 version = 16;
}

message GetAgentRequest { string agent_id = 1; }
message UpdateAgentRequest { /* 同 CreateAgentRequest + agent_id + change_log */ }
message ListAgentsRequest {
  string status = 1;
  string name_contains = 2;
  int32 page = 3;
  int32 size = 4;
}
message ListAgentsResponse {
  repeated AgentResponse items = 1;
  int32 total = 2;
  int32 page = 3;
  int32 size = 4;
}
```

### knowledge.proto（节选）

```protobuf
syntax = "proto3";
package agentplatform.knowledge.v1;

import "common.proto";

service KnowledgeBase {
  rpc IngestDocument(IngestDocumentRequest) returns (IngestResponse);
  rpc SearchChunks(SearchChunksRequest) returns (SearchChunksResponse);
  rpc ListBases(ListBasesRequest) returns (ListBasesResponse);
  rpc DeleteBase(DeleteBaseRequest) returns (DeleteBaseResponse);
}

message IngestDocumentRequest {
  string kb_id = 1;
  string doc_id = 2;
  string content = 3;
}
message IngestResponse {
  bool success = 1;
  repeated string chunk_ids = 2;
  string message = 3;
}

message SearchChunksRequest {
  string kb_id = 1;
  string query = 2;
  int32 top_k = 3;
  bool use_mmr = 4;
  float mmr_lambda = 5;
}
message SearchChunk {
  string chunk_id = 1;
  string content = 2;
  float score = 3;
  string doc_id = 4;
}
message SearchChunksResponse {
  repeated SearchChunk chunks = 1;
}

message ListBasesRequest {
  string status = 1;
  int32 page = 2;
  int32 size = 3;
}
message KnowledgeBaseInfo {
  string kb_id = 1;
  string name = 2;
  string description = 3;
  int32 doc_count = 4;
  int32 chunk_count = 5;
  string embedding_model = 6;
  int32 dimension = 7;
  string status = 8;
}
message ListBasesResponse {
  repeated KnowledgeBaseInfo items = 1;
  int32 total = 2;
}

message DeleteBaseRequest { string kb_id = 1; }
message DeleteBaseResponse {
  bool success = 1;
  string message = 2;
}
```

---

**END OF PLAN 08**
