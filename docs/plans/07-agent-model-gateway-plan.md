# agent-model-gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `agent-model-gateway`（端口 8094 HTTP / 9094 gRPC）单模块内补齐模型网关的全部能力：ModelRouter 路由（按场景/成本/可用性）、CostMeter 计量（input/output 分开计费）、PromptCache 缓存（相同前缀命中）、ModelDegradationManager 故障自动降级（主模型→备用）、4 个 gRPC 服务（Chat / StreamChat / CountTokens / ListModels）、多供应商适配器（OpenAI / Anthropic / Gemini / 国内模型通义/文心/DeepSeek）。T1~T14 全部待做，对齐 v5 审核 §6 P6-7 整改项与 ADR-003 多供应商解耦策略。

**Architecture:** 单 Spring Boot 应用 `agent-model-gateway`，对外暴露 gRPC 服务端口 9094（HTTP 健康检查 8094），内部以 ModelRouter 为核心路由层，根据 scene（intent / audit / generic）/ cost budget / availability 选择对应 ModelProviderAdapter；请求经 PromptCache 命中检查后由适配器转发至上游供应商；响应经 CostMeter 计量后落 model_usage_log，并触发 ModelDegradationManager 健康度更新。主模型故障连续达阈值（默认 3 次）后自动切换备用 provider，恢复后回切。依赖 agent-proto（`model.proto` 定义 ChatRequest / ChatResponse / ChatChunk / CountTokensRequest/Response / ListModelsRequest/Response）与 agent-common（ErrorCode: MODEL_GATEWAY_ERROR / MODEL_TIMEOUT / RATE_LIMITED / QUOTA_EXCEEDED / COST_BUDGET_EXCEEDED；TokenEstimator 复用）。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / grpc-spring-boot-starter 3.1.0.RELEASE（net.devh，端口 9094）/ Spring AI 0.8.1（OpenAiChatModel / AnthropicChatModel 客户端）/ spring-boot-starter-webflux（WebClient 用于 Gemini / 通义 / 文心 / DeepSeek 自研适配器）/ JPA + MySQL 8（model_provider / model_route_rule / model_usage_log 三表）/ Jedis 5.x（PromptCache + 限流计数器）/ JUnit 5 / Mockito 5 / AssertJ 3.25.3 / WireMock 3.x（上游 HTTP 桩）/ Awaitility（流式断言）/ Testcontainers 1.19.7（可选 MySQL + Redis 集成路径）

---

## 设计文档对齐

| 项 | 来源 | 锁定值 |
|---|---|---|
| model-gateway 端口 | doc 00-overview §3.1 | 8094（HTTP） / 9094（gRPC） |
| 逻辑库 | doc 01-database §0.4 / §2.1 | `agent_model`（model_provider / model_route_rule / model_usage_log） |
| model_provider 表结构 | doc 01-database §5.1 | id / provider_code / provider_name / api_base_url / api_key_ref（Vault 路径） / enabled / weight / max_qps / max_concurrency / created_at / updated_at |
| model_route_rule 表结构 | doc 01-database §5.2 | id / scene（intent/audit/generic） / priority / from_provider_id / to_provider_id / fallback_provider_id / cost_ceiling_usd / enabled |
| model_usage_log 表结构 | doc 01-database §5.3 | id / trace_id / provider_code / model_name / scene / input_tokens / output_tokens / input_cost_usd / output_cost_usd / total_cost_usd / latency_ms / status / error_code / created_at |
| 4 gRPC RPC | `agent-proto/src/main/proto/model.proto` | Chat / StreamChat（server streaming）/ CountTokens / ListModels |
| proto 生成包名 | model.proto / common.proto | `agentplatform.model.v1` / `agentplatform.common.v1` |
| 路由场景枚举 | doc 02-api 模型网关 §3 | SCENE_INTENT（路由到轻量模型）/ SCENE_AUDIT（路由到强模型）/ SCENE_GENERIC（默认路由） |
| PromptCache 缓存策略 | doc 02-api 模型网关 §5 | Redis Hash，key=`promptcache:{tenantId}:{md5(prefix_256_chars)}`，TTL 24h，命中直接返回缓存 ChatResponse |
| CostMeter 计费策略 | doc 02-api 模型网关 §6 | input/output 分开计费，按 provider 单价表（model_provider.cost_per_input_1k / cost_per_output_1k）计算 USD，累加至 tenant_quota |
| 故障降级阈值 | doc 02-api 模型网关 §7 | 连续失败 3 次切换备用 provider，冷却 5min 后尝试回切 |
| Token 估算系数 | doc 02-api 模型网关 §4 + agent-common TokenEstimator | 中文 1.7 倍系数，英文按 4 char/token，复用 `TokenEstimator.estimate(String)` |
| 错误码域 | agent-common ErrorCode | MODEL_GATEWAY_ERROR(500) / MODEL_TIMEOUT(504) / RATE_LIMITED(429) / QUOTA_EXCEEDED(429) / COST_BUDGET_EXCEEDED(429) |
| gRPC 服务类签名 | doc 02-api 模型网关 §8 | `@GrpcService extends ModelGatewayGrpc.ModelGatewayImplBase` |
| 多供应商解耦策略 | ADR-003 | 统一 ModelProviderAdapter 接口（chat / streamChat / countTokens / listModels / health），各供应商独立适配器，禁止业务层耦合具体 SDK |
| 配置参数 | doc 02-api 模型网关 §9 | `model.gateway.defaultScene=generic` / `prompt.cache.ttl=24h` / `degradation.failThreshold=3` / `degradation.cooldownMinutes=5` / `quota.tenant.defaultUsd=100` |
| 测试用例 | `docs/tests/unit-test-cases.md` §10 | UT-MG-001~010 |
| v5 审核整改项 | `docs/tests/tdd-audit-report-v5.md` §6 P6-7 | 实现 agent-model-gateway T1-T14（多供应商适配 / 计量 / 缓存 / 降级）D3 +1.5 |
| Spring AI 客户端版本 | 父 pom `spring-ai.version` | 0.8.1（spring-ai-openai-spring-boot-starter / spring-ai-anthropic-spring-boot-starter） |
| WireMock 桩端口 | 测试约定 | 18094（HTTP）上游供应商桩 |

---

## 文件结构总览

### 目标新增目录与文件

```
agent-model-gateway/
├── pom.xml                                                 # T1 Maven 配置
├── src/main/resources/
│   ├── application.yml                                     # T1 端口 8094/9094 + MySQL agent_model + Redis
│   ├── application-test.yml                                # T1 测试 profile（H2 + jedis-mock + WireMock）
│   └── db/migration/V1__init_model_gateway.sql             # T2/T12 三表 DDL
└── src/
    ├── main/java/com/agent/modelgateway/
    │   ├── ModelGatewayApplication.java                    # T1 Spring Boot 启动类
    │   ├── config/
    │   │   ├── GrpcServerConfig.java                       # T1 gRPC 端口 9094 配置
    │   │   ├── ModelGatewayProperties.java                 # T1 @ConfigurationProperties("model.gateway")
    │   │   ├── JpaConfig.java                              # T1 JPA 配置
    │   │   ├── RedisConfig.java                            # T1 Redis/Jedis 配置
    │   │   ├── WebClientConfig.java                        # T1 WebClient（Gemini/通义/文心/DeepSeek）
    │   │   └── SpringAiConfig.java                         # T4 OpenAI/Anthropic 客户端 bean
    │   ├── model/
    │   │   ├── ModelProvider.java                          # T2 JPA Entity（model_provider）
    │   │   ├── ModelRouteRule.java                         # T3 JPA Entity（model_route_rule）
    │   │   ├── ModelUsageLog.java                          # T12 JPA Entity（model_usage_log）
    │   │   ├── ProviderStatus.java                         # T13 枚举 ACTIVE / DEGRADED / RECOVERING
    │   │   └── Scene.java                                  # T3 枚举 INTENT / AUDIT / GENERIC
    │   ├── repository/
    │   │   ├── ModelProviderRepository.java                # T2 JPA Repository
    │   │   ├── ModelRouteRuleRepository.java               # T3 JPA Repository（按 scene 查询）
    │   │   └── ModelUsageLogRepository.java               # T12 JPA Repository（按 traceId / tenant 聚合）
    │   ├── router/
    │   │   ├── ModelRouter.java                            # T3 路由核心（scene→rule→provider）
    │   │   ├── RouteResult.java                           # T3 路由结果（primary + fallback）
    │   │   └── RouteRuleMatcher.java                      # T3 scene + priority 匹配
    │   ├── adapter/
    │   │   ├── ModelProviderAdapter.java                  # T4 适配器接口
    │   │   ├── AdapterContext.java                         # T4 适配器调用上下文（traceId / scene / timeout）
    │   │   ├── OpenAiAdapter.java                          # T4 OpenAI 适配器（Spring AI OpenAiChatModel）
    │   │   ├── AnthropicAdapter.java                      # T5 Anthropic 适配器（Spring AI AnthropicChatModel）
    │   │   ├── GeminiAdapter.java                          # T6 Gemini 适配器（WebClient + REST v1beta）
    │   │   ├── QwenAdapter.java                            # T7 通义千问适配器（DashScope API）
    │   │   ├── ErnieAdapter.java                           # T7 文心一言适配器（百度千帆 API）
    │   │   ├── DeepSeekAdapter.java                        # T7 DeepSeek 适配器（OpenAI 兼容协议）
    │   │   ├── AdapterRegistry.java                        # T4 适配器注册中心（providerCode→Adapter）
    │   │   └── ChatResponseMapper.java                     # T4 上游响应 → proto ChatResponse 映射
    │   ├── cache/
    │   │   ├── PromptCache.java                            # T11 缓存核心（Redis Hash 前缀 md5）
    │   │   ├── PromptCacheKey.java                         # T11 key 生成（tenantId + md5(prefix_256)）
    │   │   └── PromptCacheProperties.java                  # T11 TTL / prefix 长度配置
    │   ├── cost/
    │   │   ├── CostMeter.java                              # T12 计量核心（input/output 分开计费）
    │   │   ├── CostCalculator.java                         # T12 单价表查询 + USD 计算
    │   │   ├── QuotaEnforcer.java                          # T12 租户配额校验（超限抛 QUOTA_EXCEEDED）
    │   │   └── TenantQuotaRepository.java                  # T12 租户配额 Redis 计数器
    │   ├── token/
    │   │   └── TokenCounter.java                           # T10 复用 agent-common TokenEstimator + 中文 1.7 倍系数
    │   ├── degradation/
    │   │   ├── ModelDegradationManager.java                # T13 故障降级（失败计数 + 冷却 + 回切）
    │   │   ├── ProviderHealthIndicator.java                # T13 健康度（连续失败/成功率/平均延迟）
    │   │   └── DegradationState.java                       # T13 状态机 ACTIVE→DEGRADED→RECOVERING→ACTIVE
    │   ├── retry/
    │   │   └── RetryPolicy.java                            # T4/T13 瞬时错误指数退避（max 3 次）
    │   ├── grpc/
    │   │   ├── ModelGatewayGrpcService.java                # T8/T9/T10/T11 4 RPC 服务端
    │   │   ├── GrpcExceptionAdvice.java                    # T8 BusinessException → StatusResponse 翻译
    │   │   └── ProtoMapper.java                            # T8 proto ↔ 内部模型映射
    │   └── exception/
    │       ├── ModelGatewayException.java                  # T4 业务异常基类
    │       ├── ProviderUnavailableException.java           # T13 上游不可用
    │       └── ErrorCodeMapper.java                        # T8 错误码映射
    └── test/java/com/agent/modelgateway/
        ├── router/
        │   ├── ModelRouterTest.java                        # T3 UT-MG-001 / UT-MG-002
        │   └── RouteRuleMatcherTest.java                   # T3 scene + priority 匹配
        ├── adapter/
        │   ├── OpenAiAdapterTest.java                      # T4 Spring AI Mock
        │   ├── AnthropicAdapterTest.java                   # T5
        │   ├── GeminiAdapterTest.java                      # T6 WireMock
        │   ├── QwenAdapterTest.java                       # T7 WireMock
        │   ├── ErnieAdapterTest.java                       # T7 WireMock
        │   ├── DeepSeekAdapterTest.java                    # T7 WireMock
        │   ├── AdapterRegistryTest.java                    # T4 注册中心
        │   └── RetryPolicyTest.java                        # T4/T13 UT-MG-010
        ├── cache/
        │   └── PromptCacheTest.java                        # T11 UT-MG-004
        ├── cost/
        │   ├── CostMeterTest.java                          # T12 UT-MG-003
        │   └── QuotaEnforcerTest.java                      # T12 UT-MG-006
        ├── token/
        │   └── TokenCounterTest.java                       # T10 UT-MG-007
        ├── degradation/
        │   └── ModelDegradationManagerTest.java            # T13 UT-MG-005 / UT-MG-008
        ├── grpc/
        │   ├── ModelGatewayGrpcServiceChatTest.java        # T8 UT-MG-008
        │   ├── ModelGatewayGrpcServiceStreamTest.java      # T9 UT-MG-009
        │   ├── ModelGatewayGrpcServiceCountTokensTest.java# T10
        │   └── ModelGatewayGrpcServiceListModelsTest.java # T11
        └── integration/
            └── ModelGatewayIntegrationTest.java            # T14 WireMock 桩 + 4 RPC 端到端
```

---

## Tasks

### T1 项目骨架（pom.xml + application.yml + 启动类）

**Red**
- [ ] 在 `agent-model-gateway/pom.xml` 中声明父 pom（`com.agent:agent-platform-parent`）、artifactId `agent-model-gateway`、依赖：`spring-boot-starter-web` / `spring-boot-starter-data-jpa` / `mysql-connector-j` / `grpc-spring-boot-starter` 3.1.0.RELEASE / `spring-ai-openai-spring-boot-starter` 0.8.1 / `spring-ai-anthropic-spring-boot-starter` 0.8.1 / `spring-boot-starter-webflux` / `jedis` 5.x / `agent-proto` / `agent-common` / `lombok` / 测试 scope：`spring-boot-starter-test` / `mockito-core` / `assertj-core` / `wiremock-standalone-jetty` 3.x / `awaitility` / `h2` / `jedis-mock`
- [ ] 编写 `ModelGatewayApplicationTests.java`：`contextLoads()` 期望失败（启动类还不存在）

**Green**
- [ ] 创建 `ModelGatewayApplication.java`（`@SpringBootApplication` + `@ComponentScan("com.agent")`）
- [ ] 创建 `application.yml`：`server.port: 8094` / `grpc.server.port: 9094` / `spring.datasource.url: jdbc:mysql://localhost:3306/agent_model` / `spring.jpa.hibernate.ddl-auto: validate` / `model.gateway.*` 默认配置 / `spring.ai.openai.*` 占位 / `spring.ai.anthropic.*` 占位
- [ ] 创建 `application-test.yml`：H2 模式 + jedis-mock + WireMock base-url 占位
- [ ] 创建 `ModelGatewayProperties.java`（`@ConfigurationProperties("model.gateway")`：defaultScene / failThreshold=3 / cooldownMinutes=5 / quota.tenant.defaultUsd=100）
- [ ] 创建 `GrpcServerConfig.java` / `JpaConfig.java` / `RedisConfig.java` / `WebClientConfig.java` / `SpringAiConfig.java`（最小 bean 配置）

**Refactor**
- [ ] 检查端口 8094 / 9094 在 doc 00-overview §3.1 对齐
- [ ] 抽取 `model.gateway.*` 默认值到 `ModelGatewayProperties`，禁止散落 `@Value`
- [ ] 验证 `mvn -pl agent-model-gateway compile` 通过

**Commit**
- [ ] `feat(agent-model-gateway): T1 scaffold pom + application.yml + 启动类`
- [ ] 推送分支，关联 issue（v5 审核 P6-7）

---

### T2 model_provider 表与 JPA Entity

**Red**
- [ ] 编写 `ModelProviderRepositoryTest.java`：`Should_FindByProviderCode_When_Enabled` / `Should_FindAllEnabled_When_QueryActive` 期望失败（Entity 不存在）

**Green**
- [ ] 创建 `V1__init_model_gateway.sql` 中 `model_provider` DDL（参照 doc 01-database §5.1）：`id BIGINT PK AUTO_INCREMENT` / `provider_code VARCHAR(64) UNIQUE` / `provider_name VARCHAR(128)` / `api_base_url VARCHAR(512)` / `api_key_ref VARCHAR(256)`（Vault 路径，非明文）/ `cost_per_input_1k DECIMAL(10,6)` / `cost_per_output_1k DECIMAL(10,6)` / `max_qps INT` / `max_concurrency INT` / `weight INT DEFAULT 1` / `enabled TINYINT DEFAULT 1` / `created_at` / `updated_at`
- [ ] 创建 `ModelProvider.java` `@Entity` + `@Table(name="model_provider")`，对应字段
- [ ] 创建 `ModelProviderRepository.java` `extends JpaRepository<ModelProvider, Long>`：`findByProviderCode(String)` / `findByEnabledTrue()`

**Refactor**
- [ ] 抽取 `BaseEntity`（created_at / updated_at `@PrePersist` / `@PreUpdate`，参照 Plan 04 T3）
- [ ] 验证 H2 模式下 DDL 可执行

**Commit**
- [ ] `feat(agent-model-gateway): T2 model_provider entity + repository + DDL`

---

### T3 model_route_rule 路由匹配（ModelRouter）

**Red**
- [ ] 编写 `ModelRouterTest.java`：
  - [ ] UT-MG-001: `Should_RouteToLightModel_When_SceneIsIntent`（scene=INTENT → 期望 provider=qwen-turbo 或类似轻量模型）
  - [ ] UT-MG-002: `Should_RouteToStrongModel_When_SceneIsAudit`（scene=AUDIT → 期望 provider=gpt-4 / claude-opus）
  - [ ] `Should_ReturnFallback_When_PrimaryDegraded`（主降级时返回 fallback_provider_id）

**Green**
- [ ] 创建 `model_route_rule` DDL（doc 01-database §5.2）：`id` / `scene VARCHAR(32)` / `priority INT` / `from_provider_id BIGINT NULL` / `to_provider_id BIGINT` / `fallback_provider_id BIGINT NULL` / `cost_ceiling_usd DECIMAL(10,4) NULL` / `enabled TINYINT`
- [ ] 创建 `ModelRouteRule.java` `@Entity`
- [ ] 创建 `ModelRouteRuleRepository.java`：`findBySceneAndEnabledTrueOrderByPriorityAsc(Scene)`
- [ ] 创建 `Scene.java` 枚举（INTENT / AUDIT / GENERIC）
- [ ] 创建 `RouteRuleMatcher.java`：按 scene + priority 匹配规则，跳过 disabled provider
- [ ] 创建 `RouteResult.java`（primaryProvider / fallbackProvider / routeRule）
- [ ] 创建 `ModelRouter.java`：`route(Scene, tenantId) → RouteResult`，调用 `RouteRuleMatcher` + `ModelDegradationManager.isAvailable()` 过滤降级中的 provider

**Refactor**
- [ ] 检查路由结果是否考虑 `cost_ceiling_usd`（超限回退 fallback）
- [ ] 提取 `ModelRouter` 与 `AdapterRegistry` 解耦（router 只返回 RouteResult，不直接调适配器）

**Commit**
- [ ] `feat(agent-model-gateway): T3 ModelRouter + route_rule + Scene 枚举`

---

### T4 OpenAI 协议适配器（OpenAiAdapter，ADR-003）

**Red**
- [ ] 编写 `OpenAiAdapterTest.java`：
  - [ ] `Should_ReturnChatResponse_When_OpenAiReturnsSuccess`（Mock OpenAiChatModel.call）
  - [ ] `Should_ThrowProviderUnavailable_When_OpenAiReturns5xx`（Mock 抛异常）
  - [ ] UT-MG-010: `Should_RetryWithBackoff_When_TransientError`（429/503 重试 3 次）
- [ ] 编写 `RetryPolicyTest.java`：指数退避 100ms / 400ms / 1600ms

**Green**
- [ ] 创建 `ModelProviderAdapter.java` 接口：
  ```java
  ChatResponse chat(ChatRequest request, AdapterContext ctx);
  Flux<ChatChunk> streamChat(ChatRequest request, AdapterContext ctx);
  long countTokens(CountTokensRequest request);
  List<ModelInfo> listModels();
  boolean health();
  ```
- [ ] 创建 `AdapterContext.java`（traceId / scene / timeoutMs / tenantId）
- [ ] 创建 `OpenAiAdapter.java` `implements ModelProviderAdapter`，注入 `OpenAiChatModel`（Spring AI 0.8.1），`chat()` 调用 `openAiChatModel.call(Prompt)`，`streamChat()` 调用 `openAiChatModel.stream(Prompt).toFlux()`
- [ ] 创建 `AdapterRegistry.java`：`@PostConstruct` 注册所有适配器到 `Map<String, ModelProviderAdapter>`（key=providerCode）
- [ ] 创建 `ChatResponseMapper.java`：Spring AI `AiResponse` → proto `ChatResponse`
- [ ] 创建 `RetryPolicy.java`：`executeWithRetry(Supplier, maxRetries=3, baseDelay=100ms)`，对 429 / 503 / SocketTimeout 重试
- [ ] 创建 `ModelGatewayException.java` / `ProviderUnavailableException.java`

**Refactor**
- [ ] 验证 ADR-003 多供应商解耦：`OpenAiAdapter` 不暴露 Spring AI 类型到接口外
- [ ] 提取 `ChatResponseMapper` 为通用映射器（OpenAI / Anthropic / Gemini 共用结构）

**Commit**
- [ ] `feat(agent-model-gateway): T4 OpenAiAdapter + AdapterRegistry + RetryPolicy (ADR-003)`

---

### T5 Anthropic 适配器（AnthropicAdapter）

**Red**
- [ ] 编写 `AnthropicAdapterTest.java`：
  - [ ] `Should_ReturnChatResponse_When_AnthropicReturnsSuccess`（Mock AnthropicChatModel）
  - [ ] `Should_StreamChunks_When_ServerStreaming`（Mock Flux<AiResponse>）
  - [ ] `Should_MapError_When_AnthropicReturnsOverloaded`（529 overloaded → ProviderUnavailable）

**Green**
- [ ] 创建 `AnthropicAdapter.java` `implements ModelProviderAdapter`，注入 `AnthropicChatModel`（Spring AI 0.8.1）
- [ ] `chat()` 调用 `anthropicChatModel.call(Prompt)`，包装为 `RetryPolicy.executeWithRetry`
- [ ] `streamChat()` 调用 `anthropicChatModel.stream(Prompt).toFlux()`，每个 `AiResponse` 映射为 proto `ChatChunk`
- [ ] 在 `SpringAiConfig.java` 中配置 `AnthropicChatModel` bean（`spring.ai.anthropic.api-key` 从 Vault 占位）

**Refactor**
- [ ] 验证与 OpenAiAdapter 共用 `ChatResponseMapper`
- [ ] 提取 Anthropic 错误码映射（429 → RATE_LIMITED / 529 → ProviderUnavailable / 401 → MODEL_GATEWAY_ERROR）

**Commit**
- [ ] `feat(agent-model-gateway): T5 AnthropicAdapter (Spring AI 0.8.1)`

---

### T6 Gemini 适配器（GeminiAdapter）

**Red**
- [ ] 编写 `GeminiAdapterTest.java`（WireMock 桩 `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`）：
  - [ ] `Should_ReturnChatResponse_When_GeminiReturnsSuccess`
  - [ ] `Should_MapGeminiError_When_SafetyBlocked`（safety 反馈 → MODEL_GATEWAY_ERROR）
  - [ ] `Should_Stream_When_ServerStreaming`（`streamGenerateContent` SSE）

**Green**
- [ ] 创建 `GeminiAdapter.java`，注入 `WebClient`（`WebClientConfig` 配置 baseUrl=`https://generativelanguage.googleapis.com`）
- [ ] `chat()`：POST `/v1beta/models/{model}:generateContent?key={apiKey}`，响应 JSON → `ChatResponse`
- [ ] `streamChat()`：POST `/v1beta/models/{model}:streamGenerateContent?key={apiKey}`，解析 SSE 流为 `Flux<ChatChunk>`
- [ ] `countTokens()`：POST `/v1beta/models/{model}:countTokens`
- [ ] `listModels()`：GET `/v1beta/models`
- [ ] 错误映射：400 invalid_argument / 403 permission_denied / 429 quota_exceeded / 500 server_error

**Refactor**
- [ ] 提取 Gemini 响应 JSON 解析到独立 `GeminiResponseParser`
- [ ] 验证 WireMock 桩在测试 profile 启用（`application-test.yml` 中 `gemini.api-base-url=http://localhost:18094/gemini`）

**Commit**
- [ ] `feat(agent-model-gateway): T6 GeminiAdapter (WebClient REST v1beta)`

---

### T7 国内模型适配器（通义/文心/DeepSeek）

**Red**
- [ ] 编写 `QwenAdapterTest.java` / `ErnieAdapterTest.java` / `DeepSeekAdapterTest.java`（WireMock 桩各自上游）：
  - [ ] 通义：`Should_CallDashScope_When_QwenChat`（POST `https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation`）
  - [ ] 文心：`Should_CallQianfan_When_ErnieChat`（POST `https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/{model}`，需 access_token 交换）
  - [ ] DeepSeek：`Should_CallCompatibleApi_When_DeepSeekChat`（POST `https://api.deepseek.com/v1/chat/completions`，OpenAI 兼容）
  - [ ] 各自：`Should_Stream_When_ServerStreaming`（SSE）

**Green**
- [ ] 创建 `QwenAdapter.java`：DashScope 协议，header `Authorization: Bearer {apiKey}`，body `{model, input: {messages}, parameters: {result_type}}`，响应 `output.text`
- [ ] 创建 `ErnieAdapter.java`：先 POST 百度 OAuth 换 `access_token`（缓存 30 天），再调千帆 chat 接口；响应 `result` 字段
- [ ] 创建 `DeepSeekAdapter.java`：OpenAI 兼容协议，可直接复用 `OpenAiAdapter` 逻辑（仅 baseUrl/apiKey 不同），通过 `@Qualifier("deepSeekWebClient")` 注入
- [ ] 在 `AdapterRegistry` 注册 `qwen` / `ernie` / `deepseek` 三个 providerCode

**Refactor**
- [ ] 提取 `AbstractHttpAdapter` 抽象基类（公共 WebClient 调用 + 错误处理 + RetryPolicy 包装）
- [ ] DeepSeek 复用 OpenAI 协议映射器，避免重复代码

**Commit**
- [ ] `feat(agent-model-gateway): T7 国内模型适配器（通义/文心/DeepSeek）`

---

### T8 Chat gRPC 服务（ModelGatewayGrpcService.Chat）

**Red**
- [ ] 编写 `ModelGatewayGrpcServiceChatTest.java`：
  - [ ] UT-MG-008: `Should_ReturnModelError_When_ProviderReturnsError`（Mock Adapter 抛 ProviderUnavailable → gRPC Status UNKNOWN with ErrorCode MODEL_GATEWAY_ERROR metadata）
  - [ ] `Should_ReturnChatResponse_When_RouteAndCallSuccess`（Mock ModelRouter + Adapter）
  - [ ] UT-MG-006: `Should_EnforceQuotaLimit_When_ApiKeyExceedsQuota`（Mock QuotaEnforcer 抛 QUOTA_EXCEEDED → gRPC Status RESOURCE_EXHAUSTED）

**Green**
- [ ] 创建 `ModelGatewayGrpcService.java` `@GrpcService extends ModelGatewayGrpc.ModelGatewayImplBase`
- [ ] 实现 `chat(ChatRequest, StreamObserver<ChatResponse>)`：
  1. 从 request 解析 scene / tenantId / messages
  2. `ModelRouter.route(scene, tenantId)` → RouteResult
  3. `PromptCache.tryHit(cacheKey)` 命中则直接返回
  4. `QuotaEnforcer.checkQuota(tenantId, estimatedCost)` 超限抛 QUOTA_EXCEEDED
  5. `AdapterRegistry.get(primary.providerCode).chat(request, ctx)`
  6. `CostMeter.record(usageLog)` 落 model_usage_log
  7. `PromptCache.put(cacheKey, response)`
  8. `onNext(response) / onCompleted()`
- [ ] 创建 `GrpcExceptionAdvice.java`：`@GrpcAdvice` 拦截 `ModelGatewayException`，映射为 `StatusResponse`（Code + ErrorCode + message）
- [ ] 创建 `ProtoMapper.java`：proto `Message` ↔ 内部 `ChatMessage`、`ChatRequest` ↔ `AdapterContext`

**Refactor**
- [ ] 检查 TraceId 透传：从 gRPC metadata 读取 `x-trace-id` 注入 MDC
- [ ] 抽取 `ChatFlowTemplate`（template method：route → cache → quota → call → cost → cachePut），后续 T9 StreamChat 复用部分逻辑

**Commit**
- [ ] `feat(agent-model-gateway): T8 Chat gRPC service + GrpcExceptionAdvice`

---

### T9 StreamChat server streaming

**Red**
- [ ] 编写 `ModelGatewayGrpcServiceStreamTest.java`：
  - [ ] UT-MG-009: `Should_StreamResponse_When_ServerStreamingRequested`（Mock Adapter 返回 `Flux<ChatChunk>`，用 Awaitility 断言收到的 chunk 序列）
  - [ ] `Should_CancelStream_When_ClientCancelled`（Mock 客户端取消，验证上游 Flux 被 cancel）

**Green**
- [ ] 在 `ModelGatewayGrpcService.java` 实现 `streamChat(ChatRequest, StreamObserver<ChatChunk>)`：
  1. 同 chat() 前置流程（route / quota check）
  2. **不查 PromptCache**（流式不缓存，避免缓存大对象）
  3. `Adapter.streamChat(request, ctx)` 返回 `Flux<ChatChunk>`
  4. `Flux.subscribe(chunk -> onNext(chunk), error -> onError(map), () -> onCompleted())`
  5. 累计 tokens，流结束后 `CostMeter.record(usageLog)` 一次性计量
- [ ] 处理 `StreamObserver` 取消：`Flux.doOnCancel(() -> log.info("client cancelled traceId={}", traceId))`

**Refactor**
- [ ] 检查背压：使用 `Flux.onBackpressureBuffer(256)` 防止客户端慢消费导致 OOM
- [ ] 提取流式 token 累加器 `StreamingTokenAccumulator`

**Commit**
- [ ] `feat(agent-model-gateway): T9 StreamChat server streaming`

---

### T10 CountTokens（含中文 1.7 倍系数，复用 TokenEstimator）

**Red**
- [ ] 编写 `TokenCounterTest.java`：
  - [ ] UT-MG-007: `Should_CountChineseTokenAs1Point7_When_Estimating`（输入 100 个中文字符 → 期望 token ≈ 100/1.7 ≈ 58.8，向上取整 59）
  - [ ] `Should_CountEnglishToken_When_AllAscii`（输入 "hello world" → 期望约 2-3 token）
  - [ ] `Should_CountMixedContent_When_ChineseAndEnglish`（混合文本按中文 1.7x + 英文 4char/token 分别估算后相加）

**Green**
- [ ] 创建 `TokenCounter.java`：复用 `agent-common` 的 `TokenEstimator.estimate(String)`，对中文字符按 1.7 倍系数加权：
  ```java
  public long count(String text) {
      int chineseChars = countChineseChars(text);
      int otherChars = text.length() - chineseChars;
      // 中文 1 字 ≈ 1.7 token，英文 4 char ≈ 1 token
      return Math.round(chineseChars * 1.7 + otherChars / 4.0);
  }
  ```
- [ ] 在 `ModelGatewayGrpcService.java` 实现 `countTokens(CountTokensRequest, StreamObserver<CountTokensResponse>)`：对 request.messages 累加 token，返回 `CountTokensResponse{total_tokens, by_message: [...]}`

**Refactor**
- [ ] 验证中文 1.7 系数来源（doc 02-api §4 + agent-common TokenEstimator 注释）
- [ ] 提取 `TokenCounter` 到独立 bean，供 `CostMeter` 预估和 `QuotaEnforcer` 预检共用

**Commit**
- [ ] `feat(agent-model-gateway): T10 CountTokens + 中文 1.7 倍系数 (复用 TokenEstimator)`

---

### T11 Prompt 缓存（PromptCache，Redis 相同前缀）

**Red**
- [ ] 编写 `PromptCacheTest.java`：
  - [ ] UT-MG-004: `Should_HitPromptCache_When_SamePrefixRecalled`（相同前缀 256 字符 + 相同后缀 → 命中）
  - [ ] `Should_MissCache_When_PrefixDiffers`（前缀不同 → 未命中）
  - [ ] `Should_ExpireAfterTtl_When_TwentyFourHoursPassed`（TTL 24h，模拟过期未命中）

**Green**
- [ ] 创建 `PromptCacheKey.java`：`generate(tenantId, messages)` → 取前 256 字符 md5，key=`promptcache:{tenantId}:{md5}`
- [ ] 创建 `PromptCacheProperties.java`：`ttl=24h` / `prefixLength=256` / `enabled=true`
- [ ] 创建 `PromptCache.java`：
  - `tryHit(String key) → Optional<ChatResponse>`：`jedis.hget(key, "response")` 反序列化
  - `put(String key, ChatResponse response)`：`jedis.hset(key, "response", json)` + `jedis.expire(key, ttlSeconds)`
- [ ] 在 `ModelGatewayGrpcService.chat()` 中集成（T8 流程第 3 步 / 第 7 步）

**Refactor**
- [ ] 检查缓存粒度：仅缓存非流式 chat 响应，streamChat 不缓存
- [ ] 提取序列化为 `JsonSerdeUtils`（Jackson ObjectMapper 单例）

**Commit**
- [ ] `feat(agent-model-gateway): T11 PromptCache (Redis 前缀 md5, TTL 24h)`

---

### T12 model_usage_log 计量（CostMeter，按 input/output 分开计费）

**Red**
- [ ] 编写 `CostMeterTest.java`：
  - [ ] UT-MG-003: `Should_CalculateCostByInputOutputToken_When_CallCompleted`（input=1000 token @ $0.001/1k + output=500 token @ $0.002/1k → input_cost=$1.0 / output_cost=$1.0 / total=$2.0）
  - [ ] `Should_RecordUsageLog_When_CallCompleted`（验证 model_usage_log 落库）
  - [ ] `Should_AggregateTenantUsage_When_QueryByTenant`（按 tenant 聚合日用量）
- [ ] 编写 `QuotaEnforcerTest.java`：UT-MG-006 补充 `Should_Reject_When_TenantExceedsDailyQuota`

**Green**
- [ ] 创建 `model_usage_log` DDL（doc 01-database §5.3）：`id` / `trace_id VARCHAR(64)` / `tenant_id VARCHAR(64)` / `provider_code VARCHAR(64)` / `model_name VARCHAR(128)` / `scene VARCHAR(32)` / `input_tokens INT` / `output_tokens INT` / `input_cost_usd DECIMAL(10,6)` / `output_cost_usd DECIMAL(10,6)` / `total_cost_usd DECIMAL(10,6)` / `latency_ms INT` / `status VARCHAR(16)` / `error_code VARCHAR(64)` / `created_at DATETIME`
- [ ] 创建 `ModelUsageLog.java` `@Entity`
- [ ] 创建 `ModelUsageLogRepository.java`：`sumTotalCostByTenantAndDateRange(tenantId, start, end)`
- [ ] 创建 `CostCalculator.java`：查询 `model_provider.cost_per_input_1k` / `cost_per_output_1k`，计算 `input_cost = input_tokens/1000 * rate_input`、`output_cost = output_tokens/1000 * rate_output`
- [ ] 创建 `CostMeter.java`：`record(UsageContext)` → 调用 `CostCalculator` 计算 + `ModelUsageLogRepository.save()`
- [ ] 创建 `QuotaEnforcer.java`：`checkQuota(tenantId, estimatedCost)`，从 Redis 计数器 `tenantquota:{tenantId}:{date}` 读取当日累计，加预估超 `quota.tenant.defaultUsd=100` 则抛 `QUOTA_EXCEEDED`
- [ ] 创建 `TenantQuotaRepository.java`：Redis `incrBy` + `expire`（每日 0 点重置）

**Refactor**
- [ ] 检查 input/output 分开计费是否符合 doc 02-api §6（不要合并成单值）
- [ ] 提取 `CostMeter` 与 `QuotaEnforcer` 解耦：CostMeter 只记录，QuotaEnforcer 只校验

**Commit**
- [ ] `feat(agent-model-gateway): T12 CostMeter + QuotaEnforcer + model_usage_log (input/output 分开计费)`

---

### T13 故障自动降级（ModelDegradationManager，主模型故障→备用）

**Red**
- [ ] 编写 `ModelDegradationManagerTest.java`：
  - [ ] UT-MG-005: `Should_DegradeToBackupModel_When_PrimaryUnavailable`（连续失败 3 次 → state 从 ACTIVE 转 DEGRADED，下次路由返回 fallback）
  - [ ] `Should_RecoverAfterCooldown_When_PrimaryHealthCheckPasses`（冷却 5min 后尝试 ping 主模型，成功 → RECOVERING → ACTIVE）
  - [ ] UT-MG-008 补充: `Should_RecordProviderError_When_AdapterThrows`（异常计入失败计数）
  - [ ] `Should_NotDegrade_When_FailuresBelowThreshold`（失败 2 次 < 3，仍 ACTIVE）

**Green**
- [ ] 创建 `DegradationState.java` 枚举（ACTIVE / DEGRADED / RECOVERING）
- [ ] 创建 `ProviderStatus.java` `@Entity` 或 Redis Hash：`providerCode` / `state` / `consecutiveFailures` / `lastFailureAt` / `degradedAt`
- [ ] 创建 `ProviderHealthIndicator.java`：
  - `recordSuccess(providerCode)`：清零失败计数，若 state=RECOVERING 则转 ACTIVE
  - `recordFailure(providerCode)`：失败计数++，达阈值 3 转 DEGRADED + 记 `degradedAt`
  - `isAvailable(providerCode)`：state != DEGRADED 或已过冷却期（5min）转 RECOVERING
- [ ] 创建 `ModelDegradationManager.java`：组合 `ProviderHealthIndicator` + `AdapterRegistry`，提供 `getAvailableProvider(RouteResult)`（若 primary 不可用返回 fallback）
- [ ] 在 `ModelRouter.route()` 中调用 `ModelDegradationManager.isAvailable(primary)` 过滤
- [ ] 在 `ModelGatewayGrpcService.chat()` catch `ProviderUnavailableException` 时调 `recordFailure` + 重试 fallback provider

**Refactor**
- [ ] 检查冷却逻辑：`degradedAt + cooldownMinutes(5) < now` 才允许尝试回切，且单次失败即回退 DEGRADED
- [ ] 提取 `ProviderHealthStore` 抽象（Redis 实现 + 内存实现供测试）

**Commit**
- [ ] `feat(agent-model-gateway): T13 ModelDegradationManager (主模型故障自动降级 + 冷却回切)`

---

### T14 集成测试（WireMock 桩上游 + 4 RPC 端到端）

**Red**
- [ ] 编写 `ModelGatewayIntegrationTest.java` `@SpringBootTest`：
  - [ ] `Should_ChatEndToEnd_When_OpenAiProvider`（WireMock 桩 OpenAI `/v1/chat/completions` → 调用 gRPC Chat → 验证响应 + model_usage_log 落库）
  - [ ] `Should_StreamChatEndToEnd_When_AnthropicProvider`（WireMock 桩 Anthropic SSE → 调用 StreamChat → 验证收到的 chunk 序列）
  - [ ] `Should_CountTokensEndToEnd_When_MixedContent`（中文+英文混合 → 验证 1.7x 系数）
  - [ ] `Should_ListModelsEndToEnd_When_MultiProvider`（聚合 OpenAI/Anthropic/Gemini 模型列表）
  - [ ] `Should_DegradeEndToEnd_When_PrimaryFailsThreeTimes`（WireMock 强制 500 × 3 → 验证降级到 fallback）
  - [ ] `Should_HitCacheEndToEnd_When_SameRequestTwice`（相同请求 × 2 → 第二次命中 PromptCache，WireMock 上游仅被调用 1 次）

**Green**
- [ ] 在 `application-test.yml` 中配置 WireMock base-url 指向 `http://localhost:18094`
- [ ] 在测试类 `@BeforeEach` 启动 WireMock server（`@RegisterExtension static WireMockExtension wm`），注册 OpenAI / Anthropic / Gemini / 通义 / 文心 / DeepSeek 桩响应
- [ ] 使用 `@Sql` 初始化 `model_provider` / `model_route_rule` 测试数据
- [ ] 使用 InProcess gRPC server（`InProcessServerTransportFactory`）启动 `ModelGatewayGrpcService`
- [ ] 使用 jedis-mock 替代真实 Redis（PromptCache + TenantQuota）
- [ ] 使用 H2 MySQL 模式替代真实 MySQL
- [ ] 断言：响应内容 / model_usage_log 表记录 / WireMock 收到的请求数（验证缓存命中与降级）

**Refactor**
- [ ] 检查测试覆盖：4 RPC × 多 provider × 缓存命中/未命中 × 降级路径
- [ ] 验证 JaCoCo 覆盖率：line ≥80% / branch ≥70%

**Commit**
- [ ] `test(agent-model-gateway): T14 集成测试 (WireMock 桩上游 + 4 RPC 端到端)`
- [ ] 更新 `docs/tests/tdd-audit-report-v5.md` P6-7 整改项状态为 Done

---

## 测试矩阵（UT-MG-001 ~ 010）

引用自 `docs/tests/unit-test-cases.md` §10。

| 用例 ID | 名称 | 所在 Task | 所在测试类 | 验证点 |
|---|---|---|---|---|
| UT-MG-001 | Should_RouteToLightModel_When_SceneIsIntent | T3 | `ModelRouterTest` | scene=INTENT → 路由到轻量模型（qwen-turbo / gpt-3.5） |
| UT-MG-002 | Should_RouteToStrongModel_When_SceneIsAudit | T3 | `ModelRouterTest` | scene=AUDIT → 路由到强模型（gpt-4 / claude-opus） |
| UT-MG-003 | Should_CalculateCostByInputOutputToken_When_CallCompleted | T12 | `CostMeterTest` | input/output 分开计费，total = input_cost + output_cost |
| UT-MG-004 | Should_HitPromptCache_When_SamePrefixRecalled | T11 | `PromptCacheTest` | 相同前缀 256 字符 → 命中 Redis 缓存 |
| UT-MG-005 | Should_DegradeToBackupModel_When_PrimaryUnavailable | T13 | `ModelDegradationManagerTest` | 连续失败 3 次 → 自动切到 fallback_provider_id |
| UT-MG-006 | Should_EnforceQuotaLimit_When_ApiKeyExceedsQuota | T8/T12 | `ModelGatewayGrpcServiceChatTest` / `QuotaEnforcerTest` | 超租户配额 → QUOTA_EXCEEDED (429) |
| UT-MG-007 | Should_CountChineseTokenAs1Point7_When_Estimating | T10 | `TokenCounterTest` | 中文 1 字 ≈ 1.7 token，复用 TokenEstimator |
| UT-MG-008 | Should_ReturnModelError_When_ProviderReturnsError | T8/T13 | `ModelGatewayGrpcServiceChatTest` | 上游错误 → gRPC Status UNKNOWN + ErrorCode MODEL_GATEWAY_ERROR |
| UT-MG-009 | Should_StreamResponse_When_ServerStreamingRequested | T9 | `ModelGatewayGrpcServiceStreamTest` | StreamChat 返回 `stream ChatChunk`，Awaitility 断言 |
| UT-MG-010 | Should_RetryWithBackoff_When_TransientError | T4 | `OpenAiAdapterTest` / `RetryPolicyTest` | 429/503 指数退避重试 max 3 次 |

---

## 验收标准

- [ ] 14 个 Task 全部完成（T1~T14 checkbox 全勾选）
- [ ] UT-MG-001~010 全部通过（绿）
- [ ] JaCoCo 覆盖率：line ≥80% / branch ≥70%（运行 `mvn -pl agent-model-gateway verify` 生成报告）
- [ ] `mvn -pl agent-model-gateway package` 成功，产出 `agent-model-gateway-1.0.0-SNAPSHOT.jar`
- [ ] 集成测试 `ModelGatewayIntegrationTest` 在 H2 + jedis-mock + WireMock 下全绿
- [ ] 4 个 gRPC RPC（Chat / StreamChat / CountTokens / ListModels）可被客户端调用并返回正确响应
- [ ] model_provider / model_route_rule / model_usage_log 三表 DDL 在 MySQL 8 下可执行
- [ ] ADR-003 多供应商解耦：业务层（gRPC 服务）不直接依赖任何供应商 SDK，仅通过 `ModelProviderAdapter` 接口交互
- [ ] v5 审核 `docs/tests/tdd-audit-report-v5.md` §6 P6-7 整改项标记为 Done

---

## Self-review checklist

- [ ] **端口对齐**：8094（HTTP）/ 9094（gRPC）与 doc 00-overview §3.1 一致，未误用 8084/9090 等 task-orchestrator 端口
- [ ] **proto 契约**：`agent-proto/src/main/proto/model.proto` 包含 4 RPC（Chat / StreamChat / CountTokens / ListModels），生成包名 `agentplatform.model.v1`
- [ ] **三表 DDL**：model_provider / model_route_rule / model_usage_log 字段与 doc 01-database §5 完全一致，api_key_ref 为 Vault 路径非明文
- [ ] **错误码**：MODEL_GATEWAY_ERROR / MODEL_TIMEOUT / RATE_LIMITED / QUOTA_EXCEEDED / COST_BUDGET_EXCEEDED 全部在 agent-common ErrorCode 中定义并在 `GrpcExceptionAdvice` 映射
- [ ] **中文 1.7 系数**：`TokenCounter` 复用 agent-common `TokenEstimator`，中文按 1.7 倍加权，与 doc 02-api §4 一致
- [ ] **input/output 分开计费**：`CostMeter` 不合并计算，`model_usage_log` 分两列存储 input_cost_usd / output_cost_usd
- [ ] **降级阈值**：failThreshold=3 / cooldownMinutes=5 与 doc 02-api §7 一致
- [ ] **PromptCache 策略**：前缀 256 字符 md5 / TTL 24h / 仅缓存非流式 chat / 不缓存流式，与 doc 02-api §5 一致
- [ ] **ADR-003 解耦**：`ModelProviderAdapter` 接口统一，6 个适配器（OpenAI / Anthropic / Gemini / 通义 / 文心 / DeepSeek）独立实现，业务层无 SDK 耦合
- [ ] **TraceId 透传**：gRPC metadata `x-trace-id` → MDC → model_usage_log.trace_id
- [ ] **Spring AI 版本**：0.8.1 与父 pom `spring-ai.version` 一致
- [ ] **测试隔离**：WireMock 桩端口 18094 与其他模块不冲突；H2 + jedis-mock 不污染真实环境
- [ ] **配置外部化**：所有 api-key 走 Vault 占位（`api_key_ref` 字段），不在 application.yml 明文
- [ ] **流式背压**：`streamChat` 使用 `onBackpressureBuffer(256)` 防止 OOM
- [ ] **RetryPolicy**：仅对瞬时错误（429 / 503 / SocketTimeout）重试，业务错误（4xx 非 429）不重试
- [ ] **集成测试覆盖路径**：4 RPC × 多 provider × 缓存命中/未命中 × 降级路径全枚举
- [ ] **未引入未授权依赖**：pom.xml 仅使用父 pom 已声明版本的依赖，不私自锁定版本
- [ ] **文档同步**：完成后更新 `docs/tests/tdd-audit-report-v5.md` P6-7 整改状态
