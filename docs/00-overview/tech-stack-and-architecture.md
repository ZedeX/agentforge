# 技术栈选型与整体架构设计

> 文档版本：v1.0  |  更新日期：2026-06-26  |  对应 PRD：第一节·整体架构拆分与模块职责 / 第七节·工程文件输出要求 1

## 1. 设计目标

基于 PRD「四层业务平面 + 两大横向体系」的分层架构，落地一套**管控与执行分离、全链路可观测、能力资产化、分级容错、安全优先**的 Agent 智能体平台。本文档锁定全栈技术选型、模块物理划分、目录结构、依赖关系，作为后续所有模块详设、数据库设计、API 规范、编码实现的基础约定。

### 1.1 关键设计原则（继承自 PRD）

| 原则 | 落地约束 |
|---|---|
| 管控与执行分离 | 调度/编排/校验在平台层；Agent 运行时仅做单任务执行，无状态 |
| 全链路可观测 | TraceID 全链路透传，所有状态变更落 PostgreSQL + ClickHouse |
| 能力资产化 | Agent/工具/知识/模板统一资源化，具备版本与生命周期 |
| 分级容错 | 异常四级分类，重试/降级/回滚/人工兜底分层兜底 |
| 安全优先 | RBAC+ABAC 双模型，R3 高危工具沙箱执行 + 双人复核 |

### 1.2 非功能性指标基线

| 维度 | 指标 | 目标值 |
|---|---|---|
| 可用性 | 核心服务 SLA | ≥ 99.9% |
| 延迟 | 意图识别 P95 | ≤ 200ms |
| 延迟 | 单步工具调用 P95 | ≤ 800ms |
| 并发 | 峰值 Agent 实例 | ≥ 5000 |
| 成本 | 单任务平均 Token | ≤ 30K（中等任务基线） |
| 可扩展 | 水平扩容 | 无状态服务支持 K8s HPA 弹性 |

---

## 2. 全栈技术选型

### 2.1 技术栈总览

| 层级 | 技术 | 版本 | 选型理由 |
|---|---|---|---|
| **语言/运行时** | Java 17 (LTS) | OpenJDK 17.0.10+ | 虚拟线程（Project Loom）提升 IO 密集场景并发；生态成熟 |
| **框架** | Spring Boot 3.2 + Spring Cloud 2023.0.x | 3.2.4+ | Jakarta EE 9+，原生 GraalVM 支持；Spring AI 0.1 集成大模型 |
| **微服务治理** | Spring Cloud Alibaba | 2023.0.1.0 | Nacos 注册配置中心、Sentinel 流控熔断、Seata 分布式事务 |
| **API 网关** | Spring Cloud Gateway | 4.1.x | 响应式网关，支持限流/熔断/鉴权过滤器链 |
| **RPC** | gRPC + Protobuf | 1.60.x | 引擎间高性能内部通信；REST 用于对外 API |
| **大模型网关** | 自研 + Spring AI | 0.1.x | 统一 OpenAI 协议适配层，屏蔽国内外模型差异 |
| **关系库** | MySQL | 8.0.36 | InnoDB，utf8mb4，JSON 字段存储半结构化任务参数 |
| **向量库** | Milvus | 2.4.x | HNSW + IVF_FLAT 索引，支持记忆/知识双 Collection 隔离 |
| **缓存** | Redis Cluster | 7.2 | 短期记忆、分布式锁、热点数据、限流计数 |
| **图库** | Neo4j Community | 5.18 | 代码知识图谱、调用链追溯、依赖关系 |
| **对象存储** | MinIO | RELEASE.2024-03 | S3 兼容，存储附件/归档/快照 |
| **消息队列** | Apache RocketMQ | 5.3.x | 事务消息支持子任务事件，5.x Pop 模式提升消费吞吐 |
| **全文搜索** | Elasticsearch | 8.13.x | 代码标识符分词，知识库全文检索 |
| **任务调度** | XXL-Job | 2.4.1 | 记忆蒸馏、Badcase 回流等定时任务 |
| **可观测-Trace** | SkyWalking | 9.7.x | Java Agent 字节码增强，自动埋点 |
| **可观测-指标** | Prometheus + Grafana | 2.51 / 10.4 | 指标采集与可视化 |
| **可观测-日志** | ELK / Loki | 8.13 / 3.0 | 结构化 JSON 日志聚合 |
| **容器编排** | Kubernetes | 1.29 | Serverless Agent 实例按需拉起销毁 |
| **AST 解析** | Tree-sitter | 0.22 (Java 绑定) | 多语言代码统一解析 |
| **工作流引擎** | 自研 DAG 引擎 | - | PRD 要求动态重规划，Camunda 等偏静态，故自研 |
| **构建/CI** | Maven + Docker + ArgoCD | 3.9.x | 多模块统一构建 |

### 2.2 模块-技术对应矩阵

| PRD 模块 | 实现技术 | 关键依赖 |
|---|---|---|
| 基础资源层·算力池 | K8s + KubeSphere | 自研 Operator 调度 Agent Pod |
| 基础资源层·模型网关 | Spring Cloud Gateway + 自研 Router | Spring AI、Redis（缓存） |
| 基础资源层·存储中间件 | MySQL + Milvus + Redis + MinIO | ShardingSphere 分库分表 |
| 核心引擎·任务编排调度 | 自研 DAG + RocketMQ 事件驱动 | Seata（分布式事务） |
| 核心引擎·智能规划 | Spring Boot + Spring AI | 强模型（GPT-4 / Qwen-Max） |
| 核心引擎·记忆管理 | Milvus + MySQL + Redis | 自研向量化管道 |
| 核心引擎·统一工具引擎 | Spring Boot + gRPC | 沙箱 Docker 执行器 |
| 核心引擎·Agent 运行时 | Spring Boot（轻量化） | 无状态，K8s 拉起 |
| 能力服务层 | Spring Boot + MyBatis-Plus | 仓库/知识/模板/质量评估 |
| 接入交互层 | Spring Cloud Gateway + WebSocket | SSE 流式输出 |

---

## 3. 物理架构与微服务拆分

### 3.1 微服务拆分清单（11 个核心服务 + 2 个横向服务）

采用**按业务能力拆分**而非按技术分层，避免分布式事务。

| 序号 | 服务名 | 服务标识 | 职责 | 端口 | 对外暴露 | 数据存储 |
|---|---|---|---|---|---|---|
| 1 | 接入网关服务 | `agent-gateway` | 多协议接入、鉴权、限流、请求标准化 | 8080 | 是 | Redis |
| 2 | 会话管理服务 | `session-service` | 会话生命周期、多轮对话状态、流式输出 | 8082 | 否 | MySQL/Redis |
| 3 | 任务编排服务 | `task-orchestrator` | DAG 解析、子任务分发、状态机、重规划 | 8084 | 否 | MySQL/RocketMQ |
| 4 | 智能规划服务 | `planning-service` | 复杂度识别、子任务拆解、DAG 生成 | 8086 | 否 | MySQL |
| 5 | 记忆管理服务 | `memory-service` | 三级记忆、向量化、多路召回、膨胀治理 | 8088 | 否 | Milvus/MySQL/Redis |
| 6 | 工具引擎服务 | `tool-engine` | 工具注册、智能召回、调用网关、沙箱代理 | 8090 | 否 | MySQL/MinIO |
| 7 | Agent 运行时 | `agent-runtime` | ReAct/Reflexion 循环、Token 管控 | 8092 | 否 | 无状态（Redis 会话态） |
| 8 | 模型网关服务 | `model-gateway` | 模型路由、计量、Prompt 缓存、密钥配额 | 8094 | 否 | Redis/MySQL |
| 9 | Agent 仓库服务 | `agent-repo` | Agent 生命周期、能力标签、动态评分 | 8096 | 否 | MySQL/MinIO |
| 10 | 知识库服务 | `knowledge-service` | 文档解析切片、版本权限、质量校验 | 8098 | 否 | MySQL/Milvus/MinIO |
| 11 | 质量评估服务 | `quality-service` | 自动校验、效果评测、Badcase 归集 | 8100 | 否 | MySQL/ClickHouse |
| — | 风控合规服务 | `risk-control`（横向） | 内容安全、越权拦截、合规审计 | 8102 | 否 | MySQL/ClickHouse |
| — | 可观测服务 | `observability`（横向） | Trace 透传、指标采集、日志归集 | 8104 | 否 | SkyWalking/Loki |

### 3.2 部署拓扑图

```
                    ┌─────────────────────────────────────────┐
                    │       外部流量（Web/SDK/OpenAPI）         │
                    └───────────────────┬─────────────────────┘
                                        │ HTTPS
                              ┌─────────▼─────────┐
                              │   agent-gateway   │  (SLB + 3 副本)
                              └─────────┬─────────┘
                                        │
        ┌───────────────────────────────┼───────────────────────────────┐
        │                               │                               │
   ┌────▼────┐                  ┌──────▼──────┐                ┌───────▼───────┐
   │ session │                  │    task-    │                │   risk-       │
   │ service │                  │ orchestrator│                │  control      │
   └────┬────┘                  └──────┬──────┘                └───────┬───────┘
        │                              │                               │
        │      ┌───────────────────────┼───────────────────────┐        │
        │      │                       │                       │        │
   ┌────▼──────▼──┐            ┌───────▼──────┐         ┌──────▼───────▼─┐
   │  planning-   │            │  memory-     │         │  tool-engine   │
   │  service     │            │  service     │         │                │
   └──────────────┘            └──────────────┘         └────────────────┘
        │                              │                       │
        │      ┌───────────────────────┼───────────────────────┤
        │      │                       │                       │
   ┌────▼──────▼──┐            ┌───────▼──────┐         ┌──────▼───────┐
   │  agent-      │            │  model-      │         │  agent-      │
   │  runtime     │◄──────────►│  gateway     │         │  repo /      │
   │  (N 副本)    │            │              │         │  knowledge / │
   └──────────────┘            └──────────────┘         │  quality     │
                                                        └──────────────┘

  横向体系：risk-control（拦截器模式注入各服务） / observability（SkyWalking Agent 全量旁路）
```

### 3.3 服务间通信矩阵

| 调用方 → 被调方 | 协议 | 同步/异步 | 用途 |
|---|---|---|---|
| gateway → session | REST | 同步 | 建立会话 |
| gateway → task-orchestrator | REST | 同步 | 提交任务 |
| task-orchestrator → planning | gRPC | 同步 | 请求规划 |
| task-orchestrator → agent-runtime | RocketMQ | 异步 | 分发子任务 |
| task-orchestrator → memory | gRPC | 同步 | 加载/沉淀记忆 |
| agent-runtime → tool-engine | gRPC | 同步 | 工具调用 |
| agent-runtime → model-gateway | gRPC | 同步 | 模型推理 |
| agent-runtime → memory | gRPC | 同步 | 增量记忆写入 |
| tool-engine → risk-control | gRPC | 同步 | 前置权限校验 |
| 所有服务 → observability | SDK | 旁路 | Trace/指标上报 |
| 所有写操作服务 → risk-control | 拦截器 | 同步 | 越权拦截 |

---

## 4. 项目目录结构

采用 Maven 多模块单仓库（Monorepo）管理，便于跨服务复用公共模块。

```
agent-platform/
├── pom.xml                                    # 父 POM，统一依赖版本管理
├── README.md
├── docs/                                       # 设计文档（本目录）
│   ├── 00-overview/
│   ├── 01-database/
│   ├── 02-api/
│   ├── 03-task-engine/
│   ├── 04-memory/
│   ├── 05-tool-engine/
│   ├── 06-agent-runtime/
│   ├── 07-code-retrieval/
│   ├── 08-flow/
│   └── 09-governance-and-deployment/
├── agent-common/                              # 公共模块（工具类/常量/异常）
│   ├── pom.xml
│   └── src/main/java/com/agentplatform/common/
│       ├── enums/                             # 枚举（TaskStatus、RiskLevel 等）
│       ├── exception/                         # 统一异常体系
│       ├── model/                             # 公共 DTO/VO
│       ├── util/                              # 工具类（JsonUtils、TokenCounter）
│       └── constant/                          # 常量定义
├── agent-proto/                               # gRPC Protobuf 定义
│   ├── pom.xml
│   └── src/main/proto/
│       ├── task.proto
│       ├── memory.proto
│       ├── tool.proto
│       └── model.proto
├── agent-gateway/                             # 接入网关
│   ├── pom.xml
│   └── src/main/java/com/agentplatform/gateway/
│       ├── filter/                            # 鉴权/限流/标准化过滤器
│       ├── handler/                           # 路由处理器
│       └── config/
├── agent-session/                             # 会话管理
├── agent-task-orchestrator/                   # 任务编排调度
│   └── src/main/java/com/agentplatform/task/
│       ├── dag/                               # DAG 解析与执行
│       ├── statemachine/                      # 任务状态机
│       ├── dispatcher/                        # 子任务分发
│       └── replanner/                         # 动态重规划
├── agent-planning/                            # 智能规划
├── agent-memory/                              # 记忆管理
│   └── src/main/java/com/agentplatform/memory/
│       ├── shortterm/                         # 短期记忆（Redis）
│       ├── longterm/                          # 长期记忆（Milvus+MySQL）
│       ├── recall/                            # 多路召回与重排
│       ├── distill/                           # 记忆蒸馏
│       └── compress/                          # Token 水位压缩
├── agent-tool-engine/                         # 工具引擎
├── agent-runtime/                             # Agent 运行时
│   └── src/main/java/com/agentplatform/runtime/
│       ├── react/                             # ReAct 循环
│       ├── reflexion/                         # Reflexion 反思
│       ├── context/                           # 上下文构建与压缩
│       └── state/                             # 执行状态同步
├── agent-model-gateway/                       # 模型网关
├── agent-repo/                                # Agent 仓库
├── agent-knowledge/                           # 知识库
├── agent-quality/                             # 质量评估
├── agent-risk-control/                        # 风控合规
├── agent-observability/                       # 可观测
├── infra/                                     # 基础设施配置
│   ├── k8s/                                   # K8s 部署清单
│   ├── docker/                                # Dockerfile
│   ├── sql/                                   # DDL 脚本
│   └── config/                                # Nacos 配置导出
└── scripts/                                   # 运维脚本
    ├── deploy.ps1
    ├── init-db.ps1
    └── build-all.ps1
```

### 4.1 命名规范

| 类型 | 规范 | 示例 |
|---|---|---|
| Maven 模块 | `agent-{module}` | `agent-memory` |
| 包名 | `com.agentplatform.{module}` | `com.agentplatform.memory` |
| 配置文件 | `application-{profile}.yml` | `application-prod.yml` |
| Docker 镜像 | `agentplatform/{module}:{version}` | `agentplatform/memory:1.0.0` |
| K8s 命名空间 | `agent-platform-{env}` | `agent-platform-prod` |
| 数据库表 | `{domain}_{entity}` 蛇形命名 | `task_instance`、`memory_long_term` |

---

## 5. 关键技术决策记录（ADR 摘要）

### ADR-001：采用自研 DAG 引擎而非 Camunda/Activiti

**背景**：PRD 要求支持动态重规划（增量重规划 + 全量重规划）、运行中 DAG 修改。
**决策**：自研轻量 DAG 引擎，节点为子任务，边为依赖关系，存 MySQL + 内存双写。
**理由**：Camunda BPMN 偏静态流程，运行中修改流程实例支持弱；自研可精确控制增量重规划逻辑。
**代价**：需自行实现 DAG 校验（环检测、可达性）、并行批次划分、失败回滚。

### ADR-002：Agent 运行时无状态化，状态外置到 Redis + MySQL

**背景**：PRD 要求 Agent 实例按需拉起销毁（Serverless 模式）。
**决策**：`agent-runtime` 服务本身无状态，所有执行上下文（短期记忆、当前步、Token 水位）存 Redis，持久状态存 MySQL。
**理由**：K8s 可任意扩缩容；实例崩溃后可从 Redis 恢复断点续跑。
**代价**：每步需读写 Redis，单步延迟增加 ~5ms；通过 Pipeline 批量操作缓解。

### ADR-003：模型网关统一 OpenAI 协议，适配国内外模型

**背景**：需接入 GPT-4、Claude、Qwen、DeepSeek、私有化 Llama 等。
**决策**：模型网关对外暴露 OpenAI Chat Completions 兼容协议，内部通过 Adapter 模式适配各厂商 SDK。
**理由**：上层业务代码无需感知模型差异；新增模型仅需实现 Adapter。
**代价**：OpenAI 协议无法覆盖部分模型特有能力（如 Claude 的 prompt caching），通过扩展字段兼容。

### ADR-004：记忆向量库采用 Milvus 而非 Pinecone/Weaviate

**背景**：需私有化部署、支持多租户隔离、HNSW 索引。
**决策**：采用 Milvus 2.4，按记忆类型（情景/语义/流程）分 Collection，按业务域 Partition。
**理由**：开源可私有化；Partition 支持业务域隔离；HNSW 召回精度高。
**代价**：运维复杂度高于 SaaS 方案，需自管集群。

### ADR-005：工具调用统一走 gRPC 网关，而非直连

**背景**：PRD 要求工具调用全链路留痕、前置校验、限流熔断。
**决策**：所有工具调用必须经过 `tool-engine` 的 ToolGateway，禁止 Agent 直连工具。
**理由**：统一管控点，便于审计、限流、成本熔断、沙箱隔离。
**代价**：多一跳网络，单次调用延迟增加 ~10ms。

---

## 6. 横向体系落地方式

### 6.1 安全风控与合规体系（risk-control 服务）

采用**拦截器 + 侧车**双模式：

- **拦截器模式**（强一致场景）：通过 gRPC Interceptor 注入到各服务调用链，前置校验权限、参数白名单、越权拦截。任何写操作必经 `risk-control.preCheck()`。
- **旁路审计模式**（最终一致场景）：工具调用、模型调用、Agent 输出全量异步上报至 `risk-control`，做后置合规审计与 Badcase 归集。

### 6.2 全链路可观测体系（observability 服务）

- **Trace**：SkyWalking Java Agent 自动埋点，自定义 TraceID 贯穿 gateway → orchestrator → runtime → tool/memory/model。
- **指标**：各服务暴露 Prometheus `/actuator/prometheus` 端点，Grafana 预置 Agent 平台 Dashboard。
- **日志**：Logback 输出结构化 JSON，TraceID 注入 MDC，Loki 采集聚合。
- **业务埋点**：任务完成率、幻觉率、工具成功率等业务指标由 `quality-service` 定时聚合写入 ClickHouse。

---

## 7. 与 PRD 第七章交付物映射

| PRD 第七章交付物 | 对应文档 | 状态 |
|---|---|---|
| 1. 项目整体目录结构与技术栈选型说明 | 本文档（第 2、4 节） | ✅ 本轮 |
| 2. 各核心模块的核心代码实现 | 各模块详设文档（架构/接口/流程）+ 后续 coding plan | ⏸ 本轮出设计，代码留待后续 plan |
| 3. 数据库表结构设计 | [01-database/database-schema-design.md](../01-database/database-schema-design.md) | ✅ 本轮 |
| 4. 核心接口定义与 API 规范 | [02-api/api-specification.md](../02-api/api-specification.md) | ✅ 本轮 |
| 5. 配置文件与部署说明 | [09-governance-and-deployment/governance-and-middleware.md](../09-governance-and-deployment/governance-and-middleware.md) | ✅ 本轮 |
| 6. 关键流程的状态机与时序设计 | [08-flow/state-machines-and-sequences.md](../08-flow/state-machines-and-sequences.md) | ✅ 本轮 |
| 7. 基础中间件集成方案 | [09-governance-and-deployment/governance-and-middleware.md](../09-governance-and-deployment/governance-and-middleware.md) | ✅ 本轮 |

---

## 8. 后续计划

本文档为「地基」，后续文档依赖本文约定的：

- 微服务清单与端口（第 3.1 节）
- 数据存储分配（第 3.1 节"数据存储"列）
- 服务间通信协议（第 3.3 节）
- 命名规范（第 4.1 节）
- 技术栈版本（第 2.1 节）

下一份文档：[01-database/database-schema-design.md](../01-database/database-schema-design.md) 将基于本文件的服务边界，定义每个服务的数据库 schema。
