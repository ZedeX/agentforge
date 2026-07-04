# Agent 智能体平台 设计文档索引

> 生成日期：2026-06-26  |  基于需求文档：[PRD.md](../PRD.md)  |  技术栈：Java 17 / Spring Cloud Alibaba / Milvus / MySQL / RocketMQ

本目录包含 Agent 智能体平台系统的工程级设计文档，共 38 份（11 份主设计 + 1 份补遗 + 3 份详细逻辑流程图 + 9 份编码计划 + 1 份前端控制台详设 + 13 份测试文档）+ `infra/sql/` 16 个 DDL 初始化脚本，覆盖 PRD 第七章交付物 1/3/4/5/6/7，并对照 `detail-MRD.md` 完成遗漏补遗、决策逻辑层流程图详设、编码计划、前端控制台详设、基础设施 DDL 脚本，以及测试策略 / 用例 / Fixture / TDD 红绿循环记录（v1.1 + v1.2 增补）/ 独立审核框架与 v1~v7 共 7 轮审核报告。

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
| 15 | [plans/00-coding-plans-overview.md](./plans/00-coding-plans-overview.md) | - | 323 | 9 个 Plan 总览 + 依赖图 + 执行顺序 + 关键约定（v2.0 已对齐实际文件） | ✅ v2.0 |
| 16 | [plans/01-agent-proto-and-common-plan.md](./plans/01-agent-proto-and-common-plan.md) | 8 | 2785 | agent-proto（8 .proto）+ agent-common（11 Java 类），47 测试用例 | ✅ 已完成 |
| 17 | [plans/02-agent-gateway-session-plan.md](./plans/02-agent-gateway-session-plan.md) | 10 | 4339 | agent-gateway(8080) + agent-session(8082)，43 Java 类 | ✅ 已完成 |
| 18 | [plans/03-agent-memory-plan.md](./plans/03-agent-memory-plan.md) | 10 | - | agent-memory(8088/9088)：MemoryService gRPC + 8 项核心能力 | 🔄 9/10（Wave 30~37，T10 已完成） |
| 19 | [plans/04-task-orchestrator-planning-plan.md](./plans/04-task-orchestrator-planning-plan.md) | 13 | - | agent-task-orchestrator(8084) + agent-planning(8086) | 🔄 9/13（T5/T7/T11/T13 待做） |
| 20 | [plans/05-agent-tool-engine-plan.md](./plans/05-agent-tool-engine-plan.md) | 12 | - | agent-tool-engine(8090/9090)：ToolEngine gRPC + 9 项核心能力 | ⏳ 待开发 |
| 21 | [plans/06-agent-runtime-plan.md](./plans/06-agent-runtime-plan.md) | 10 | - | agent-runtime(8092/9092)：AgentRuntime gRPC + ReAct + Token 水位 | ⏳ 待开发 |
| 22 | [plans/07-agent-model-gateway-plan.md](./plans/07-agent-model-gateway-plan.md) | 14 | - | agent-model-gateway(8094/9094)：4 RPC + 多供应商适配器 | 🔄 13/14（T14 集成测试待做） |
| 23 | [plans/08-agent-repo-knowledge-plan.md](./plans/08-agent-repo-knowledge-plan.md) | 12 | - | agent-repo(8096) + agent-knowledge(8098) | 🔄 7/12（T10/T12 待做） |
| 24 | [plans/09-infra-deployment-plan.md](./plans/09-infra-deployment-plan.md) | - | - | infra/k8s + docker + nacos + 可观测组件 | ⏳ 待开发 |

### 七、前端控制台详设

| # | 文档 | 内容 | 子组件数 | API 映射 |
|---|---|---|---|---|
| 18 | [12-frontend/frontend-console-design.md](./12-frontend/frontend-console-design.md) | 运营后台 / Agent 配置工作台（低代码）/ 调试沙箱 / 终端对话界面 / 任务监控大屏，含 React 18 + Ant Design 5 + Monaco + ReactFlow + ECharts 技术栈 | 47 | 72 条 |

### 八、测试文档（TDD 红绿循环 + 独立审核）

> 脚本目录：`docs/tests/`。覆盖测试策略 / 测试计划 / 三层用例清单 / Fixture 与 Testcontainers / 红绿循环记录 / 审核框架与报告。

| # | 文档 | 版本 | 内容 | 用例数 |
|---|---|---|---|---|
| 19 | [tests/test-strategy.md](./tests/test-strategy.md) | v1.0 | 测试策略 + 金字塔（70/20/10）+ 工具栈 + 覆盖率目标 + CI 集成 + 报告规范 | — |
| 20 | [tests/test-plan.md](./tests/test-plan.md) | v1.0 | TDD 测试计划 + 13 微服务测试矩阵 + F1~F12 决策节点用例 + 3 E2E 场景 + Testcontainers + CI/CD 验收 | — |
| 21 | [tests/unit-test-cases.md](./tests/unit-test-cases.md) | v1.1 | 单元测试用例清单（18 章节，含 F1~F12 决策节点双分支补全 + 4 缺失模块补全） | 213 |
| 22 | [tests/functional-test-cases.md](./tests/functional-test-cases.md) | v1.1 | 功能测试用例清单（含状态机 10 非法流转 + 26+ 错误码触发路径） | 123 |
| 23 | [tests/user-flow-test-cases.md](./tests/user-flow-test-cases.md) | v1.1 | 端到端用户旅程（含 F10 六层幻觉治理 / F12 长期记忆 / 知识库 RAG 召回） | 13 |
| 24 | [tests/test-data-and-fixtures.md](./tests/test-data-and-fixtures.md) | v1.1 | 测试数据策略 + Fixture 工厂 + Testcontainers 容器矩阵 + 边界值常量 + 性能测试数据 | — |
| 25 | [tests/tdd-red-green-records.md](./tests/tdd-red-green-records.md) | v1.1 | 已实现 5 模块 + 6 骨架模块的 TDD 红绿循环记录（v1.0 详记 4 模块 73 方法 + v1.1 增补 task-orchestrator 221 + 骨架 60 + 端到端 23 = 490+ 方法） | 490+ |
| 25b | [tests/tdd-red-green-records-v1.2.md](./tests/tdd-red-green-records-v1.2.md) | v1.2 | v8 持久化深化期（Wave 18~37）TDD 红绿循环增补：20 个 Wave 摘要 + 经验教训 25~65 + 测试方法累计至 916+ | 916+ |
| 26 | [tests/tdd-audit-framework.md](./tests/tdd-audit-framework.md) | v1.0 | TDD 独立审核流程规范：6 维度 42 检查项 + RACI 矩阵 + 评分模型 + 一票否决项 + 8 阶段工作流 | — |
| 27 | [tests/tdd-audit-report-v1.md](./tests/tdd-audit-report-v1.md) | v1.0 | 首轮审核报告：发现 23 项问题（4 Critical / 11 Major / 5 Minor / 3 Info），总分 39.3，等级 D 不通过 | — |
| 28 | [tests/tdd-audit-report-v2.md](./tests/tdd-audit-report-v2.md) | v2.0 | 第 2 轮复核报告（P0+P1 整改后）：总分 39.3 → 65.0（C-），8 项已完成 / 1 项部分整改 / 9 项待 P2/P3；含 FN-021/022 新发现 | — |
| 29 | [tests/tdd-audit-report-v3.md](./tests/tdd-audit-report-v3.md) | v3.0 | 第 3 轮复核报告（P2 整改后）：总分 65.0 → 74.0（C+ 不通过，接近 80），P2 全 5 项完成；FIX-04 一票否决项移除；CI 已实跑 1 次 success；含 P3 整改建议 8 项 | — |
| 30 | [tests/tdd-audit-report-v4.md](./tests/tdd-audit-report-v4.md) | v4.0 | 第 4 轮复核报告（P3 部分整改后）：总分 74.0 → 80.5（B- 通过，首次过线），P3 完成 2/8（P3-1 agent-task-orchestrator + P3-6 agent-common branch 27%→92.5%）；**SEQ-02 一票否决正式解除**；含 P5 整改建议 8 项 | — |
| 31 | [tests/tdd-audit-report-v5.md](./tests/tdd-audit-report-v5.md) | v5.0 | 第 5 轮复核报告（P5 部分整改后）：总分 80.5 → 81.5（B- 通过，一票否决归零），P5 完成 1/8 + 验证 1 项非问题（P5-1 agent-gateway SessionStreamController SSE 测试 line 79.9%→85.7% / branch 66%→77.4% + P5-3 jacoco-check 继承验证为非问题）；**COV-01 一票否决正式解除**；项目已无任何部分通过状态的硬性一票否决项；含 P6 整改建议 7 项 | — |
| 32 | [tests/tdd-audit-report-v6.md](./tests/tdd-audit-report-v6.md) | v6.0 | 第 6 轮复核报告（P6 主要整改后）：总分 81.5 → 86.0（B 通过），agent-task-orchestrator T5~T13 全实现 + 错误码触发路径覆盖 29 错误码 × 3 维度 + 测试命名/AssertJ/@DisplayName 三项整改（P6-3/4/5）+ P6-6 T6~T12 复杂度/规划/校验实现；含 P7 整改建议 7 项 | — |
| 33 | [tests/tdd-audit-report-v7.md](./tests/tdd-audit-report-v7.md) | v7.5 | 第 7 轮复核报告（v6 + JaCoCo 实测校验 + P7-3/4/5/6/7 整改）：总分 86.0 → 89.2（B+ 通过），JaCoCo 实测业务代码 line 95.77% / branch 92.50% + P7-5 错误码端到端 23 用例 + P7-6 FIX 维度 D4 11.0→13.2 + P7-3 F8/F10/F11/F12 骨架 34 用例 + P7-4 F6/F7/F9 骨架 26 用例，**COV-03 一票否决项正式解除（12/12 节点组全覆盖）**；距 A-（90+）差 0.8 分，唯一路径 P7-1 CI 累计 10 次全绿 | — |

> **测试统计**：14 份文档（含 v6/v7 审计报告 + v1.2 增补），覆盖 213 单元 + 123 功能 + 13 E2E = 349 用例规划，已实现 **916+ 测试方法全绿**（v1.1：5 模块 407 + 骨架 60 + 端到端 23 = 490+；v1.2：v8 持久化深化期 Wave 18~37 新增 426 方法），文档层面达成 100% F1~F12 决策节点覆盖（99 节点 ×2 分支 = 198 用例）+ 100% 错误码覆盖（29 错误码 ×3 维度）+ 100% 状态机非法流转覆盖（10 状态）。
>
> **第 7 轮审核结论（v7.5）**：v6 → v7 通过 JaCoCo 实测校验 + P7-3/4/5/6/7 整改，总分 86.0 → **89.2（B+ 通过）**。**COV-03 一票否决项正式解除**（P7-3 F8/F10/F11/F12 + P7-4 F6/F7/F9 骨架补齐，12/12 节点组全覆盖）；D4 FIX 维度 11.0 → 13.2（P7-6 整改）；D5 CI 维度 8.0 → 9.0（CI 实跑全绿，3 次失败 streak 终止）。**A- 等级已正式达成**（Wave 20 CI streak=10，详见 [tdd-red-green-records-v1.2.md](./tests/tdd-red-green-records-v1.2.md)）。当前 CI streak=39（截至 Wave 37）。详见 [tdd-audit-report-v7.md](./tests/tdd-audit-report-v7.md)。

### TDD 审核整改进度索引

| 轮次 | 总分 | 等级 | 一票否决项 | 状态 | 报告 |
|---|---|---|---|---|---|
| v1（首轮） | 39.3 | D 不通过 | 4 项（SEQ-02 / COV-01 / COV-03-04-05 代码层 / CI-01） | ✅ 完成 | [tdd-audit-report-v1.md](./tests/tdd-audit-report-v1.md) |
| v2（P0+P1 整改复核） | 65.0 | C- 不通过 | 3 项（SEQ-02 / COV-01 覆盖率不达标 / CI-01 未实跑） | ✅ 完成 | [tdd-audit-report-v2.md](./tests/tdd-audit-report-v2.md) |
| v3（P2 整改复核，目标 75+） | 74.0 | C+ 不通过 | 2 项（SEQ-02 / COV-01 部分） | ✅ 完成 | [tdd-audit-report-v3.md](./tests/tdd-audit-report-v3.md) |
| v4（P3 部分整改复核，目标 80 通过） | 80.5 | B- 通过 | 1 项（COV-01 部分） | ✅ 完成 | [tdd-audit-report-v4.md](./tests/tdd-audit-report-v4.md) |
| v5（P5 部分整改复核，一票否决归零） | 81.5 | B- 通过 | 0 项（COV-01 正式解除） | ✅ 完成 | [tdd-audit-report-v5.md](./tests/tdd-audit-report-v5.md) |
| v6（P6 主要整改复核） | 86.0 | B 通过 | 0 项（保持） | ✅ 完成 | [tdd-audit-report-v6.md](./tests/tdd-audit-report-v6.md) |
| v7（JaCoCo 实测 + P7 整改，目标 A-） | **89.2** | **B+ 通过** | 0 项硬性 / 2 项软性（COV-03 已解除 / CI-01 进行中） | 🔄 进行中 | [tdd-audit-report-v7.md](./tests/tdd-audit-report-v7.md) |

### 九、基础设施脚本（DDL 初始化）

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
- **QA/测试**：tests/test-strategy.md → tests/test-plan.md → tests/unit-test-cases.md / functional-test-cases.md / user-flow-test-cases.md → tests/tdd-red-green-records.md（已实现 4 模块红绿循环）→ tests/tdd-audit-framework.md（审核规范）→ tests/tdd-audit-report-v1.md（首轮审核结论）→ 9（状态机/时序图）→ 4（任务状态机）→ 7（熔断/人工介入）→ 12-14（决策流程图作为测试用例输入）
- **产品/业务方**：11（补遗：业务示例 + 低代码配置台范围）→ 10（成本与模型选型）

## AI Agent 阅读指引

> 本节专为 AI Agent（Claude / GPT / Cursor / Copilot 等编码助手）设计，帮助 Agent 在少量 Token 内快速定位到与本任务相关的文档与代码位置。**所有路径均相对于本仓库根目录**（除非显式标注 `docs/` 前缀）。

### 入口决策树（按任务类型选择起点）

```
你的任务是什么？
│
├─ 「理解整个系统」
│   └─ 起点：docs/00-overview/tech-stack-and-architecture.md
│       → 然后看 PRD.md（需求原点）→ docs/08-flow/state-machines-and-sequences.md（行为契约）
│
├─ 「写/改某个微服务代码」
│   ├─ 先查 docs/00-overview/tech-stack-and-architecture.md §2 微服务清单 + 通信矩阵
│   ├─ 再查对应模块详设：
│   │   ├─ agent-task-orchestrator / agent-planning → docs/03-task-engine/task-orchestration-and-planning.md
│   │   ├─ agent-memory → docs/04-memory/memory-system-design.md
│   │   ├─ agent-tool-engine → docs/05-tool-engine/tool-and-invocation-system.md
│   │   ├─ agent-runtime → docs/06-agent-runtime/agent-runtime-engine.md
│   │   ├─ agent-repo+code-retrieval → docs/07-code-retrieval/code-retrieval-system.md
│   │   ├─ agent-gateway / agent-session → docs/02-api/api-specification.md + docs/plans/02-agent-gateway-session-plan.md
│   │   └─ agent-model-gateway → docs/09-governance-and-deployment/governance-and-middleware.md §3
│   ├─ 查接口契约：docs/02-api/api-specification.md
│   ├─ 查表结构：docs/01-database/database-schema-design.md
│   └─ 查决策分支：docs/11-detail-flow/01|02|03-*.md（F1~F12 决策节点）
│
├─ 「写/改测试」
│   ├─ 先读：docs/tests/test-strategy.md（金字塔 70/20/10 + 工具栈）
│   ├─ 查用例：docs/tests/unit-test-cases.md（单元）/ functional-test-cases.md（功能）/ user-flow-test-cases.md（E2E）
│   ├─ 查 Fixture 与边界值：docs/tests/test-data-and-fixtures.md（§3.10 边界值常量、§5 Mock、§6 Assert）
│   ├─ 查 Testcontainers：docs/tests/test-data-and-fixtures.md §4
│   ├─ 参考 TDD 红绿循环：docs/tests/tdd-red-green-records.md（已实现 4 模块 73 方法）
│   └─ 提交前自查：docs/tests/tdd-audit-framework.md §3 检查清单（42 项）
│
├─ 「排查 Bug / 行为异常」
│   ├─ 先查状态机：docs/08-flow/state-machines-and-sequences.md §1 任务状态机（10 状态）
│   ├─ 查决策分支：docs/11-detail-flow/0X-*.md（F1~F12，103 决策节点，含错误码）
│   ├─ 查错误码定义：docs/02-api/api-specification.md §错误码 + docs/tests/functional-test-cases.md §18（28 触发路径）
│   └─ 查治理流程：docs/09-governance-and-deployment/governance-and-middleware.md（幻觉六层 + 漂移四层）
│
├─ 「数据库变更」
│   ├─ 设计：docs/01-database/database-schema-design.md
│   ├─ DDL：infra/sql/mysql/0X-*.sql（9 个逻辑库）
│   ├─ 种子数据：infra/sql/mysql/11-seed-data.sql
│   ├─ Milvus：infra/sql/milvus/01-init-collections.py（6 Collection）
│   ├─ Neo4j：infra/sql/neo4j/0X-*.cypher（7 节点 6 关系）
│   ├─ Redis：infra/sql/redis/01-init-data.redis
│   └─ 编排：infra/sql/init-all.ps1（参数化一键执行）
│
├─ 「代码审查 / 安全审查」
│   ├─ 审查规范：docs/tests/tdd-audit-framework.md（6 维度 42 检查项 + 评分模型）
│   ├─ 历史报告：docs/tests/tdd-audit-report-v1.md（首轮 23 项发现）→ v2.md（65.0）→ v3.md（74.0）→ v4.md（80.5）→ v5.md（81.5）→ v6.md（86.0）→ v7.md（89.2 B+，COV-03 解除，距 A- 差 0.8）
│   ├─ 整改进度索引：见下方「TDD 审核整改进度索引」表
│   └─ 风险分级：docs/05-tool-engine/tool-and-invocation-system.md §3（R1/R2/R3 工具风险）
│
└─ 「前端 / 控制台」
    └─ docs/12-frontend/frontend-console-design.md（47 组件 + 72 API 映射）
```

### 关键路径速查表

| 你想找... | 直接看这里 | 关键章节 |
|---|---|---|
| 整个系统的 7 步执行链路 | [docs/08-flow/state-machines-and-sequences.md](./08-flow/state-machines-and-sequences.md) | §2 7 步全链路时序图 |
| 10 个任务状态与合法流转 | 同上 | §1 任务状态机 |
| F1~F12 决策节点（103 个） | [docs/11-detail-flow/01-access-and-planning-flow.md](./11-detail-flow/01-access-and-planning-flow.md)（F1~F4）+ [02-runtime-and-replan-flow.md](./11-detail-flow/02-runtime-and-replan-flow.md)（F5~F8）+ [03-quality-and-memory-flow.md](./11-detail-flow/03-quality-and-memory-flow.md)（F9~F12） | 每张图含 ≥5 判断节点 + 错误码 |
| 26+ 错误码与 HTTP 状态码映射 | [docs/02-api/api-specification.md](./02-api/api-specification.md) | §错误码 |
| 错误码触发测试用例 | [docs/tests/functional-test-cases.md](./tests/functional-test-cases.md) | §18（28 用例） |
| F1~F12 决策节点测试用例 | [docs/tests/unit-test-cases.md](./tests/unit-test-cases.md) | §18（40 用例，198 节点分支） |
| 边界值常量（F2/F7/F9/F12） | [docs/tests/test-data-and-fixtures.md](./tests/test-data-and-fixtures.md) | §3.10 Boundary Value Fixture |
| Testcontainers 容器矩阵 | 同上 | §4（MySQL/Redis/Milvus/Neo4j/ClickHouse/RocketMQ/ES/DinD） |
| 已实现 4 模块的 TDD 红绿循环 | [docs/tests/tdd-red-green-records.md](./tests/tdd-red-green-records.md) | §1~§4（17 测试文件 73 方法） |
| 审核检查清单（提交前自查） | [docs/tests/tdd-audit-framework.md](./tests/tdd-audit-framework.md) | §3（6 维度 42 项） |
| 历史审核发现 | [docs/tests/tdd-audit-report-v1.md](./tests/tdd-audit-report-v1.md) → [v2.md](./tests/tdd-audit-report-v2.md) → [v3.md](./tests/tdd-audit-report-v3.md) | §3 发现清单（23 项）→ §4 整改清单（8 项完成 / 1 部分 / 9 待 P2/P3）→ P2 全 5 项完成 / P3 待启动 8 项 |
| 整改进度索引 | 本文档 §"TDD 审核整改进度索引" 表 | v1 39.3 → v2 65.0 → v3 74.0 → v4 目标 80 通过 |
| 项目记忆 / 历史决策 | [project_memory.md](../project_memory.md) | 按日期倒序的会话记录 |
| 全局用户偏好 | [user_profile.md](../../../c:/Users/Administrator/.trae-cn/memory/user_profile.md) | 跨项目偏好 |

### Agent 行为约定

1. **改代码前**：先读对应模块的详设文档 + 当前测试文件，避免破坏既有契约
2. **提交前**：跑 `mvn clean verify`（含 JaCoCo 覆盖率检查，行 ≥80%、分支 ≥70%），全绿才能提交
3. **新增测试**：遵循 `should_{期望}_When_{条件}` 命名（详见 [tdd-audit-framework.md](./tests/tdd-audit-framework.md) §3.4 QUAL-01）
4. **新增功能**：必须有对应的 F1~F12 决策节点用例（见 [unit-test-cases.md](./tests/unit-test-cases.md) §18）
5. **遇到 Bug**：先查 [state-machines-and-sequences.md](./08-flow/state-machines-and-sequences.md) 状态机 + [11-detail-flow](./11-detail-flow/) 决策分支，确认是否状态流转异常或分支条件错误
6. **审核时**：按 [tdd-audit-framework.md](./tests/tdd-audit-framework.md) §6 工作流执行，每条发现必须引用文件路径:行号 + commit hash
7. **更新文档**：修改本索引或任何文档时，同步更新 [project_memory.md](../project_memory.md) 记录本轮工作

### 快速命令

```bash
# 编译（跳过测试）
mvn -B -ntp clean compile -DskipTests

# 跑单元测试
mvn -B -ntp test

# 跑单元 + 集成测试 + JaCoCo 覆盖率检查
mvn -B -ntp verify

# 仅生成 JaCoCo 报告（不强制覆盖率门槛）
mvn -B -ntp test jacoco:report

# 查看 JaCoCo 报告（浏览器打开）
# Windows: start target/site/jacoco/index.html
# Linux/Mac: open target/site/jacoco/index.html

# 跳过测试的快速构建（仅应急用，CI 会拒绝）
mvn -B -ntp clean package -Pquick
```

### 文档版本约定

- 所有文档头部包含 `> 文档版本：vX.Y | 更新日期：YYYY-MM-DD | 文档定位：...` 元信息
- 主版本号 X：重大重构（如新增维度/章节）
- 次版本号 Y：补充内容（如新增用例）
- 每次更新在文档末尾「修订记录」表追加一行

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

基于 writing-plans 技能，按子系统拆分为独立编码计划。截至 v7.5：

1. ✅ `agent-proto/` + `agent-common/` — [Plan 01](./plans/01-agent-proto-and-common-plan.md)（已实现，47 测试用例）
2. ✅ `agent-gateway/` + `agent-session/` — [Plan 02](./plans/02-agent-gateway-session-plan.md)（已实现，43 Java 类）
3. ✅ `agent-task-orchestrator/` — [Plan 04](./plans/04-task-orchestrator-planning-plan.md)（已实现，T5~T13 全 13 task，含 gRPC + RocketMQ + 集成测试）
4. 🔄 `agent-memory/` 编码 plan（9/10 task 已实现：T1-T5 + T7-T10，剩余 T6 Milvus，详见 [Plan 03](./plans/03-agent-memory-plan.md)）
5. ⏸ `agent-tool-engine/` 编码 plan（骨架已建，业务实现待后续）
6. ⏸ `agent-runtime/` 编码 plan（骨架已建，业务实现待后续）
7. ⏸ `agent-model-gateway/` 编码 plan（骨架未建）
8. ⏸ `agent-repo/` + `agent-knowledge/` + `agent-quality/` 编码 plan（quality 骨架已建，repo/knowledge 待后续）
9. ⏸ 基础设施（K8s/Docker/Nacos）配置 plan

每个编码计划保存至 `docs/plans/` 目录，采用 TDD 红绿循环逐步实现。已实现模块的测试覆盖详见 [tests/tdd-audit-report-v7.md](./tests/tdd-audit-report-v7.md)。
