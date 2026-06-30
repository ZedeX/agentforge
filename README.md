# AgentForge · Agent 智能体平台

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.java.net/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-green.svg)](https://spring.io/projects/spring-boot)
[![Status](https://img.shields.io/badge/Status-Prototype-yellow.svg)](#status)

> **多智能体编排与治理平台**：基于 DAG 任务编排、三级记忆、工具调用治理与幻觉六层防护，构建可观测、可治理、可演进的 Agent 运行时。

---

## 项目简介

AgentForge 是一个企业级多智能体平台，目标是把"声明式定义 Agent + DAG 任务编排 + 多级记忆 + 工具治理 + 质量评估"融为一体，使业务方通过低代码配置即可定义、调试、上线 Agent，运行时由平台自动完成复杂度识别、DAG 规划、子任务并行调度、ReAct 循环、Reflexion 反思、Token 水位压缩、三级质量校验、漂移治理等流程。

平台核心能力：

- **DAG 任务编排**：自动复杂度识别 → 模板/智能规划 → 5 维度 DAG 自检 → 并行批次调度 → 动态重规划（增量/全量）
- **三级记忆系统**：短期（Redis）/ 长期（Milvus + MySQL）/ 蒸馏记忆；多路召回（向量/关键词/时间/标签）+ 融合重排
- **工具调用治理**：R1/R2/R3 三级风险分级 + RBAC/ABAC 权限 + 配额熔断 + Docker 沙箱执行
- **ReAct + Reflexion 运行时**：Think → Act → Observe → Reflect；自检三问；Token 四级水位压缩
- **幻觉六层治理**：L1 模型选型 → L2 推理自校验 → L3 知识工具锚定 → L4 三级输出校验 → L5 Agent 专项治理 → L6 长效闭环
- **漂移四层管控**：指标采集 → 基准比对 → 4 类漂移分类 → 自动止损/根因定位/灰度回滚
- **可观测性**：SkyWalking 链路 + Prometheus 指标 + Loki 日志 + ClickHouse 指标沉淀

## 技术栈

| 维度 | 选型 |
|---|---|
| 语言 / JVM | Java 17（LTS） |
| 框架 | Spring Boot 3.2.5 / Spring Cloud 2023.0.1 / Spring Cloud Alibaba 2023.0.1.0 |
| RPC | gRPC 1.62.2 + Protobuf 3.25.1 |
| AI | Spring AI 0.8.1（model-gateway 适配 OpenAI/Anthropic/Gemini/通义/文心/DeepSeek） |
| 关系库 | MySQL 8.0.36 + MyBatis-Plus 3.5.5 |
| 向量库 | Milvus 2.4（HNSW M=16 efC=256 COSINE） |
| 图库 | Neo4j 5.18（代码知识图谱） |
| 缓存 | Redis 7.2 + Redisson 3.27.2 |
| 指标 | ClickHouse（MergeTree by toYYYYMMDD） |
| 搜索 | Elasticsearch 8.13.4 |
| 消息 | RocketMQ 5.x + rocketmq-spring 2.3.0 |
| 注册配置 | Nacos 2.3 |
| 部署 | Docker / K8s + HPA |

## 微服务清单

| 端口 | 模块 | 职责 |
|---|---|---|
| 8080 | agent-gateway | 多端接入（REST/SSE/IM Webhook）+ 鉴权 + 限流 + 风控前置 |
| 8082 | agent-session | 会话管理 + 多轮消息历史 + 上下文窗口 |
| 8084 | agent-task-orchestrator | 任务状态机 + DAG 引擎 + 并行调度 + 重规划 |
| 8086 | agent-planning | 复杂度识别 + 模板/智能规划 + 5 维度自检 |
| 8088 | agent-memory | 三级记忆 + 多路召回 + 蒸馏调度 |
| 8090 | agent-tool-engine | 工具注册 + 语义召回 + R1/R2/R3 分级执行 |
| 8092 | agent-runtime | ReAct + Reflexion + Token 压缩 + 断点续跑 |
| 8094 | agent-model-gateway | 多模型适配 + 路由 + 计量 + 降级 |
| 8096 | agent-repo | Agent 仓库 + 版本管理 + 能力评分 |
| 8098 | agent-knowledge | 知识库 + 文档解析 + 切片 + 版本 |
| 8100 | agent-quality | L4 三级校验 + Badcase 归集 + 漂移监测 |
| - | risk-control | 风控拦截器 + 内容安全 + RBAC/ABAC |
| - | observability | 指标采集 + 链路追踪 + 日志聚合 |
| - | agent-proto | Protobuf 契约层（8 .proto 文件，jar 依赖） |
| - | agent-common | 公共工具层（DTO / 异常 / 工具类，jar 依赖） |

## 仓库结构

```
agentforge/
├── pom.xml                          # Parent POM（11 模块激活 / 15 总计）
├── agent-proto/                     # Protobuf 契约层（8 .proto + 生成 stub）
├── agent-common/                    # 公共工具层（DTO/异常/工具类）
├── agent-gateway/                   # 接入网关（8080）
├── agent-session/                   # 会话服务（8082）
├── agent-task-orchestrator/         # 任务编排（8084）
├── agent-planning/                  # 规划服务（8086）
├── agent-memory/                    # 记忆服务（8088）
├── agent-tool-engine/               # 工具引擎（8090）
├── agent-runtime/                   # Agent 运行时（8092）
├── agent-model-gateway/             # 模型网关（8094）
├── agent-repo/                      # Agent 仓库（8096）
├── agent-knowledge/                  # 知识服务（8098）
├── agent-quality/                   # 质量服务（8100）
├── infra/
│   ├── sql/                         # DDL 初始化脚本（16 文件，9 MySQL 库 32 表）
│   ├── k8s/                         # K8s 部署配置
│   ├── docker/                      # Dockerfile + docker-compose
│   └── nacos/                       # Nacos 配置
├── docs/                            # 设计文档（19 份，见 docs/README.md）
│   ├── 00-overview/                 # 技术栈与架构
│   ├── 01-database/                 # 数据库设计
│   ├── 02-api/                      # API 规范
│   ├── 03-task-engine/              # 任务引擎详设
│   ├── 04-memory/                   # 记忆系统详设
│   ├── 05-tool-engine/              # 工具引擎详设
│   ├── 06-agent-runtime/            # Agent 运行时详设
│   ├── 07-code-retrieval/           # 代码检索详设
│   ├── 08-flow/                     # 状态机与时序图
│   ├── 09-governance-and-deployment/# 治理与部署
│   ├── 10-supplement/               # 补遗文档
│   ├── 11-detail-flow/              # 决策逻辑层流程图（12 张）
│   ├── 12-frontend/                 # 前端控制台详设
│   ├── plans/                       # 编码计划（writing-plans）
│   └── README.md                    # 文档总索引
├── PRD.md                           # 产品需求文档
├── detail-MRD.md                   # 详细市场需求文档
├── project_memory.md               # 项目开发记录
├── LICENSE                         # Apache 2.0
└── .gitignore
```

## 快速开始

### 环境要求

- JDK 17+（推荐 Eclipse Temurin 17）
- Maven 3.9+
- protoc 3.25+（或由 `protobuf-maven-plugin` 自动下载）
- Docker（用于本地起 MySQL/Redis/Milvus 等依赖，可选，仅运行时需要）

### 构建

```bash
# 编译基础层（agent-proto + agent-common）
mvn clean install -pl agent-proto,agent-common -am -DskipTests

# 编译并运行所有单元测试
mvn clean test

# 打包
mvn clean package -DskipTests
```

### DDL 初始化

```powershell
# 参数化执行（详见 infra/sql/init-all.ps1）
cd infra/sql
./init-all.ps1 -DbType mysql -TenantId default
```

## 设计文档

完整设计文档共 **19 份** + **16 个 DDL 脚本**，详见 [docs/README.md](./docs/README.md)。

阅读路径建议：

- **架构师**：[技术栈与架构](./docs/00-overview/tech-stack-and-architecture.md) → [数据库设计](./docs/01-database/database-schema-design.md) → [API 规范](./docs/02-api/api-specification.md)
- **后端开发**：技术栈 → 对应模块详设 → [决策流程图](./docs/11-detail-flow/) → 编码计划
- **算法工程师**：[记忆系统](./docs/04-memory/memory-system-design.md) → [工具引擎](./docs/05-tool-engine/tool-and-invocation-system.md) → [代码检索](./docs/07-code-retrieval/code-retrieval-system.md)
- **QA**：[状态机与时序图](./docs/08-flow/state-machines-and-sequences.md) → [决策流程图](./docs/11-detail-flow/)

## Status

当前阶段：**🎉 TDD 审计 A- 等级达成（v7.6，90.2 分）— 骨架模块业务逻辑补全 + 编码计划生成推进中**。

- ✅ 设计文档（19 份，覆盖 PRD 全部交付物）
- ✅ DDL 脚本（16 文件，2158 行，9 MySQL 库 32 表 + Milvus + Neo4j + Redis）
- ✅ 编码计划（agent-proto+common + agent-gateway+session + task-orchestrator，3 份已实现；Plan 03/05~10 生成中）
- ✅ Mermaid 语法校验（12/12 通过）
- ✅ 核心模块编码（5 模块完整实现：agent-proto / agent-common / agent-gateway / agent-session / agent-task-orchestrator，464+ 测试用例）
- ✅ 决策节点骨架（6 模块 POJO+interface+测试：agent-tool-engine / hallucination-governance / drift-monitor / agent-memory / agent-runtime / agent-quality，F1~F12 全 12 节点组覆盖）
- 🔄 骨架模块业务逻辑补全（6 模块并行推进中）
- ✅ **TDD 审计 A- 达成**（v7.6，90.2 分，CI-01 正式解除，D5 CI 维度满分 10.0，最近 10 次 CI 全绿）
- ⏸ 其余 4 个微服务编码（agent-planning / agent-model-gateway / agent-repo / agent-knowledge，待后续）
- ⏸ Docker / K8s 部署配置

详见 [project_memory.md](./project_memory.md) 与 [docs/tests/tdd-audit-report-v7.md](./docs/tests/tdd-audit-report-v7.md)。

## License

[Apache License 2.0](./LICENSE) © 2026 ZedeX
