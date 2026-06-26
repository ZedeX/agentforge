# Agent 智能体平台 设计文档索引

> 生成日期：2026-06-26  |  基于需求文档：[PRD.md](../PRD.md)  |  技术栈：Java 17 / Spring Cloud Alibaba / Milvus / MySQL / RocketMQ

本目录包含 Agent 智能体平台系统的工程级设计文档，共 19 份（11 份主设计 + 1 份补遗 + 3 份详细逻辑流程图 + 3 份编码计划 + 1 份前端控制台详设）+ `infra/sql/` 16 个 DDL 初始化脚本，覆盖 PRD 第七章交付物 1/3/4/5/6/7，并对照 `detail-MRD.md` 完成遗漏补遗、决策逻辑层流程图详设、编码计划、前端控制台详设与基础设施 DDL 脚本。

## 文档导航

### 一、基础文档（必读地基）

| # | 文档 | 内容 | 对应 PRD 交付物 |
|---|---|---|---|
| 1 | [00-overview/tech-stack-and-architecture.md](./00-overview/tech-stack-and-architecture.md) | 全栈技术选型、11+2 微服务拆分、Monorepo 目录结构、5 条 ADR 决策、横向体系落地 | 交付物 1 |
| 2 | [01-database/database-schema-design.md](./01-database/database-schema-design.md) | MySQL 9 个域表结构、Milvus 6 个 Collection、Neo4j 图谱、ClickHouse 指标表、分库分表 | 交付物 3 |
| 3 | [02-api/api-specification.md](./02-api/api-specification.md) | REST 对外 API、gRPC 内部接口、SSE 流式、RocketMQ 事件、错误码、限流熔断 | 交付物 4 |

### 二、核心引擎详设

| # | 文档 | 内容 | 对应 PRD 章节 |
|---|---|---|---|
| 4 | [03-task-engine/task-orchestration-and-planning.md](./03-task-engine/task-orchestration-and-planning.md) | 任务状态机、DAG 引擎、复杂度识别、两种规划模式、动态重规划、并行调度 | 第一节(三) / 第二节(三) |
| 5 | [04-memory/memory-system-design.md](./04-memory/memory-system-design.md) | 三级记忆架构、多路召回重排、膨胀治理、四级水位压缩、蒸馏调度 | 第二节(一) |
| 6 | [05-tool-engine/tool-and-invocation-system.md](./05-tool-engine/tool-and-invocation-system.md) | 工具标准化、调用全链路 9 步、R1/R2/R3 权限边界、沙箱执行、成本熔断 | 第二节(二) |
| 7 | [06-agent-runtime/agent-runtime-engine.md](./06-agent-runtime/agent-runtime-engine.md) | 无状态设计、ReAct 循环、Reflexion 反思、Token 水位管控、断点续跑、人工介入 | 第二节(五) |
| 8 | [07-code-retrieval/code-retrieval-system.md](./07-code-retrieval/code-retrieval-system.md) | AST 解析、Neo4j 知识图谱、三路召回、融合重排、Token 感知裁剪 | 第二节(四) |

### 三、流程与治理

| # | 文档 | 内容 | 对应 PRD 章节 |
|---|---|---|---|
| 9 | [08-flow/state-machines-and-sequences.md](./08-flow/state-machines-and-sequences.md) | 12 张 Mermaid 图：任务状态机、7 步全链路时序、ReAct、重规划、人工介入、Token 压缩、异常处理 | 第一节(三) / 第三节 |
| 10 | [09-governance-and-deployment/governance-and-middleware.md](./09-governance-and-deployment/governance-and-middleware.md) | 幻觉六层治理、漂移四层管控、成本与模型选型、11 类中间件集成、Nacos 配置、Dockerfile、K8s 部署 | 第四/五/六节 + 交付物 5,7 |

### 四、补遗文档（对照 detail-MRD.md 补充）

| # | 文档 | 内容 | 补遗类型 |
|---|---|---|---|
| 11 | [10-supplement/detail-mrd-gap-fill.md](./10-supplement/detail-mrd-gap-fill.md) | Agent「1主控+4模块」内聚架构视图、主流模型详细计费参考表（已回填 doc 09 §3.2.d）、代码场景 Token 消耗测算表（已回填 §3.2.e）、BPE 分词/Prompt 缓存/长上下文加价规则（已回填 §3.2.f）、3 个完整业务示例、低代码配置台范围 | 补遗 6 类遗漏（§2/§3/§4 已回填） |

### 五、详细逻辑流程图（决策逻辑层级）

| # | 文档 | 内容 | 图数量 |
|---|---|---|---|
| 12 | [11-detail-flow/01-access-and-planning-flow.md](./11-detail-flow/01-access-and-planning-flow.md) | F1 接入网关请求处理 / F2 意图识别与复杂度判定决策树 / F3 任务规划与 DAG 生成 / F4 子任务分发与并行调度 | 4 张 |
| 13 | [11-detail-flow/02-runtime-and-replan-flow.md](./11-detail-flow/02-runtime-and-replan-flow.md) | F5 动态重规划决策 / F6 ReAct 循环详细决策 / F7 Token 水位压缩决策 / F8 工具选择与调用决策 | 4 张 |
| 14 | [11-detail-flow/03-quality-and-memory-flow.md](./11-detail-flow/03-quality-and-memory-flow.md) | F9 三级质量校验决策 / F10 幻觉治理六层联动 / F11 漂移监测与纠偏决策 / F12 长期记忆写入与召回决策 | 4 张 |

> **层次说明**：doc 08 是「行为契约级」时序图/状态机（回答"什么状态、什么时序"）；doc 11 是「决策逻辑级」流程图（回答"什么条件、走哪个分支、为什么"），每张图含 ≥5 个判断节点、≥3 个分支、具体条件表达式、类/方法签名、错误码。共 12 张图，103 个决策节点。
>
> **Mermaid 语法校验**：12 张图已用 jsdom + mermaid@10.9.1 完成语法校验，全部通过。校验工具位于 [`11-detail-flow/_mermaid-validate/`](./11-detail-flow/_mermaid-validate/)：[`extract-and-build-html.mjs`](./11-detail-flow/_mermaid-validate/extract-and-build-html.mjs)（提取 12 个 mermaid 代码块并生成 HTML 校验页）、[`validate-jsdom.mjs`](./11-detail-flow/_mermaid-validate/validate-jsdom.mjs)（Node.js 服务端校验脚本，调用 `mermaid.parse()`）、[`validate-report.md`](./11-detail-flow/_mermaid-validate/validate-report.md)（校验报告，12/12 OK）。

### 六、编码计划（writing-plans 方法论）

| # | 文档 | Task 数 | 行数 | 覆盖模块 | 状态 |
|---|---|---|---|---|---|
| 15 | [plans/00-coding-plans-overview.md](./plans/00-coding-plans-overview.md) | - | 180 | 10 个子系统总览 + 依赖图 + 执行顺序 + 关键约定 | ✅ |
| 16 | [plans/01-agent-proto-and-common-plan.md](./plans/01-agent-proto-and-common-plan.md) | 8 | 2785 | agent-proto（8 .proto）+ agent-common（11 Java 类），47 测试用例 | ✅ |
| 17 | [plans/02-agent-gateway-session-plan.md](./plans/02-agent-gateway-session-plan.md) | 10 | 4339 | agent-gateway(8080) + agent-session(8082)，43 Java 类 | ✅ |
| - | plans/03~10（待生成） | 8 plans | - | task-orchestrator/memory/tool-engine/runtime/model-gateway/repo+knowledge+quality/infra | ⏸ 任务大纲见总览 |

### 七、前端控制台详设

| # | 文档 | 内容 | 子组件数 | API 映射 |
|---|---|---|---|---|
| 18 | [12-frontend/frontend-console-design.md](./12-frontend/frontend-console-design.md) | 运营后台 / Agent 配置工作台（低代码）/ 调试沙箱 / 终端对话界面 / 任务监控大屏，含 React 18 + Ant Design 5 + Monaco + ReactFlow + ECharts 技术栈 | 47 | 72 条 |

### 八、基础设施脚本（DDL 初始化）

> 脚本目录：`infra/sql/`（项目根目录下，与 docs/ 平级）。编排入口 [`init-all.ps1`](../infra/sql/init-all.ps1)（359 行参数化 PowerShell，纯英文）。

| # | 脚本 | 行数 | 内容 | 对应 doc 01 章节 |
|---|---|---|---|---|
| - | [mysql/01-agent-session.sql](../infra/sql/mysql/01-agent-session.sql) | 64 | session / session_message | §1 |
| - | [mysql/02-agent-task.sql](../infra/sql/mysql/02-agent-task.sql) | 216 | task_instance(23 字段) / subtask_instance / dag_definition / dag_node / dag_edge / replan_log / human_input | §2 |
| - | [mysql/03-agent-memory.sql](../infra/sql/mysql/03-agent-memory.sql) | 95 | memory_long_term / memory_distill_log / memory_recall_log | §3 |
| - | [mysql/04-agent-tool.sql](../infra/sql/mysql/04-agent-tool.sql) | 133 | tool_registry / tool_call_log / tool_quota / tool_approval | §4 |
| - | [mysql/05-agent-model.sql](../infra/sql/mysql/05-agent-model.sql) | 82 | model_provider / model_route_rule / model_usage_log | §5 |
| - | [mysql/06-agent-repo.sql](../infra/sql/mysql/06-agent-repo.sql) | 87 | agent_definition / agent_version / agent_metrics_daily_snapshot | §6 |
| - | [mysql/07-agent-knowledge.sql](../infra/sql/mysql/07-agent-knowledge.sql) | 90 | knowledge_base / knowledge_document / knowledge_chunk | §7 |
| - | [mysql/08-agent-quality.sql](../infra/sql/mysql/08-agent-quality.sql) | 115 | eval_task / eval_baseline / badcase / audit_log | §8 |
| - | [mysql/09-agent-risk.sql](../infra/sql/mysql/09-agent-risk.sql) | 81 | permission_policy / role / role_permission | §9 |
| - | [mysql/10-clickhouse-metrics.sql](../infra/sql/mysql/10-clickhouse-metrics.sql) | 56 | agent_metrics_daily + drift_metrics_daily（ClickHouse 引擎） | §8.4 / §2.4 |
| - | [mysql/11-seed-data.sql](../infra/sql/mysql/11-seed-data.sql) | 323 | 5 供应商 14 模型 / 6 路由规则 / 3 任务模板 / 4 角色 38 权限 / 5 工具 / 3 Agent / 3 基准集 | §5.1 / §6.1 / §4.1 / §9 |
| - | [milvus/01-init-collections.py](../infra/sql/milvus/01-init-collections.py) | 211 | 6 Collection（mem_episodic/mem_semantic/mem_procedural/code_snippet/code_graph/agent_skill）HNSW M=16 efC=256 COSINE + Partition | §10.1 |
| - | [neo4j/01-init-constraints.cypher](../infra/sql/neo4j/01-init-constraints.cypher) | 65 | 7 节点类型唯一约束 + 6 索引 | §11.1 |
| - | [neo4j/02-init-relationships.cypher](../infra/sql/neo4j/02-init-relationships.cypher) | 89 | 6 关系类型 + 示例图谱（CONTAINS/CALLS/DEFINES/DEPENDS_ON/INHERITS/IMPORTS） | §11.2 |
| - | [redis/01-init-data.redis](../infra/sql/redis/01-init-data.redis) | 92 | 热点配置 / 限流桶 / TTL / 灰度配置 | §12 |
| - | [init-all.ps1](../infra/sql/init-all.ps1) | 359 | 参数化编排脚本（-DbType -TenantId -DryRun -LogFile），纯英文，分阶段执行 | - |

> **脚本统计**：16 个文件，总 2158 行，覆盖 9 个 MySQL 逻辑库 32 张表 + 1 个 ClickHouse 指标库 2 张表 + 6 个 Milvus Collection + 7 个 Neo4j 节点类型 + 6 关系类型 + Redis 热点数据。

## 文档依赖关系

```
┌─────────────────────────────────────────────────────────┐
│  1. tech-stack-and-architecture.md  (地基：技术栈/服务/目录) │
└─────────────────────┬───────────────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
  2. database    3. api        10. governance
  (数据模型)     (接口契约)     (治理/中间件/部署)
        │             │
        └──────┬──────┘
               │
   ┌───────────┼───────────┬─────────────┐
   ▼           ▼           ▼             ▼
 4. task    5. memory   6. tool       8. code-retrieval
   engine     system     engine
   │           │           │
   └─────┬─────┘     ┌─────┘
         ▼           ▼
      7. agent-runtime (依赖 task/memory/tool)
         │
         ▼
      9. state-machines (依赖 task/runtime/tool 全部状态)

      11. supplement (对照 detail-MRD.md 补遗，跨文档引用)

      12-14. detail-flow (决策逻辑层，深化 doc 08 的 12 张时序图到判断节点级)
```

## 阅读路径建议

- **架构师/技术负责人**：1 → 2 → 3 → 9（先看地基与流程图，再看数据与接口）
- **后端开发**：1 → 3 → 对应模块详设（4/5/6/7/8）→ 12-14（决策流程图，落地分支条件）→ 2（查表结构）
- **DBA/运维**：2 → 10（中间件集成 + 部署配置）
- **算法/模型工程师**：5（记忆）→ 6（工具召回）→ 8（代码检索）→ 10（治理体系）→ 13（F7 Token 压缩决策 + F8 工具选择决策）
- **QA/测试**：9（状态机/时序图）→ 4（任务状态机）→ 7（熔断/人工介入）→ 12-14（决策流程图作为测试用例输入）
- **产品/业务方**：11（补遗：业务示例 + 低代码配置台范围）→ 10（成本与模型选型）

## PRD 第七章交付物覆盖清单

| PRD 交付物 | 对应文档 | 状态 |
|---|---|---|
| 1. 项目整体目录结构与技术栈选型说明 | doc 1 | ✅ |
| 2. 各核心模块的核心代码实现 | 各模块详设（架构/接口/类签名）+ 后续 coding plan | ⏸ 设计完成，代码留待后续 |
| 3. 数据库表结构设计 | doc 2 | ✅ |
| 4. 核心接口定义与 API 规范 | doc 3 | ✅ |
| 5. 配置文件与部署说明 | doc 10 | ✅ |
| 6. 关键流程的状态机与时序设计 | doc 9 | ✅ |
| 7. 基础中间件集成方案 | doc 10 | ✅ |

## 后续计划（coding plan）

本轮交付为设计文档。下一步将基于 writing-plans 技能，按子系统拆分为独立编码计划：

1. `infra/sql/` DDL 脚本编写 plan
2. `agent-proto/` Protobuf 定义 plan
3. `agent-common/` 公共模块 plan
4. `agent-task-orchestrator` + `agent-planning` 编码 plan
5. `agent-memory` 编码 plan
6. `agent-tool-engine` 编码 plan
7. `agent-runtime` 编码 plan
8. `agent-model-gateway` 编码 plan
9. `agent-gateway` + `agent-session` 编码 plan
10. 基础设施（K8s/Docker/Nacos）配置 plan

每个编码计划将保存至 `docs/plans/` 目录，采用 TDD 红绿循环逐步实现。
