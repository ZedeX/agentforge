# detail-MRD 对照补遗

> 文档版本：v1.0  |  更新日期：2026-06-26  |  对应来源：[detail-MRD.md](../../detail-MRD.md)
> 文档目的：补充主设计文档集（docs/00~09）对照 detail-MRD 后识别的遗漏内容，避免分散修改多份已稳定文档。

## 0. 遗漏清单与补遗映射

| # | 遗漏项 | 来源（detail-MRD 行号） | 本文档章节 | 是否需回填主文档 |
|---|---|---|---|---|
| 1 | Agent 内聚架构「1 主控 + 4 核心模块」视图 | 105-112 | §1 | 否（概念视角，独立呈现） |
| 2 | 主流模型详细计费参考表（海外 $/Mtoken + 国内 元/千Token） | 307-341 | §2 | 建议回填 [09-governance §3.2](../09-governance-and-deployment/governance-and-middleware.md) |
| 3 | 代码场景 Token 消耗量测算表 | 281-305 | §3 | 建议回填 [09-governance §3.2](../09-governance-and-deployment/governance-and-middleware.md) |
| 4 | BPE 分词规则 / Prompt 缓存折扣 / 超长上下文加价细节 | 322-335 | §4 | 建议回填 [09-governance §3.2](../09-governance-and-deployment/governance-and-middleware.md) |
| 5 | 完整业务示例（融资调研 6 子任务 DAG + 异常处理 walkthrough + 漂移处理示例） | 86-94, 666-698, 866-874, 1363-1370 | §5 | 否（示例独立成节） |
| 6 | 低代码配置台（前端控制台范围说明） | 462, 491 | §6 | 否（前端范畴，标注后续范围） |

---

## 1. Agent 内聚架构：1 主控 + 4 核心模块

### 1.1 概念视角与平台视角的差异

主设计文档（[00-overview](../00-overview/tech-stack-and-architecture.md)）从**平台微服务视角**拆分了 11+2 个服务，强调服务边界与通信协议。detail-MRD（105-112 行）补充了**单 Agent 内部视角**的「1 主控 + 4 模块」内聚架构，强调一个 Agent 实例内部各能力的紧耦合关系，两者是不同抽象层级的互补视图。

```
┌──────────────────────────────────────────────────────────┐
│                    Agent 实例（运行时）                     │
│                                                          │
│   ┌──────────────────────────────────────────────────┐   │
│   │  主控：推理调度引擎（ReAct/Reflexion 全循环驱动）   │   │
│   │  对应服务：agent-runtime                            │   │
│   │  对应类：ReActLoop / ReflexionEngine               │   │
│   └──────────────────────────────────────────────────┘   │
│            │          │           │            │           │
│   ┌────────▼──┐ ┌────▼─────┐ ┌───▼──────┐ ┌───▼────────┐ │
│   │ 模块1     │ │ 模块2    │ │ 模块3    │ │ 模块4       │ │
│   │ 记忆系统   │ │ 工具能力 │ │ 上下文   │ │ 安全校验    │ │
│   │           │ │ 系统     │ │ 管理器   │ │ 模块        │ │
│   │ 短/长/瞬时 │ │ 标准化   │ │ Token水 │ │ 内容合规    │ │
│   │ 三级记忆   │ │ 工具集+  │ │ 位管控+  │ │ 权限管控    │ │
│   │ 召回压缩   │ │ 网关管控 │ │ 分级压缩 │ │ 幻觉兜底    │ │
│   │           │ │ 智能选择 │ │ 执行     │ │             │ │
│   └─────┬─────┘ └────┬─────┘ └────┬─────┘ └─────┬───────┘ │
│         │            │            │             │         │
│   对应服务：      对应服务：    对应服务：     对应服务：       │
│   memory-service  tool-engine  agent-runtime  risk-control │
│   (gRPC)           (gRPC)       (内置)         (拦截器)    │
└──────────────────────────────────────────────────────────┘
```

### 1.2 模块归属映射

| 内聚模块 | 职责（detail-MRD 定义） | 平台服务归属 | 设计文档 |
|---|---|---|---|
| 主控·推理调度引擎 | 驱动 ReAct/Reflexion 全循环 | `agent-runtime` | [06-agent-runtime §2-3](../06-agent-runtime/agent-runtime-engine.md) |
| 模块1·记忆系统 | 短/长/瞬时三级记忆 + 召回压缩 | `memory-service` | [04-memory](../04-memory/memory-system-design.md) |
| 模块2·工具能力系统 | 标准化工具集 + 网关管控 + 智能选择 | `tool-engine` | [05-tool-engine](../05-tool-engine/tool-and-invocation-system.md) |
| 模块3·上下文管理器 | Token 水位管控 + 分级压缩执行 | `agent-runtime`（内置，非独立服务） | [06-agent-runtime §4](../06-agent-runtime/agent-runtime-engine.md) + [04-memory §6](../04-memory/memory-system-design.md) |
| 模块4·安全校验模块 | 内容合规 + 权限管控 + 幻觉兜底 | `risk-control`（横向拦截器） | [09-governance §1](../09-governance-and-deployment/governance-and-middleware.md) |

### 1.3 关键约束

- **模块3 上下文管理器**非独立微服务，而是 `agent-runtime` 内的内聚组件，避免每步压缩都跨网络调用；通过 gRPC 调用 `memory-service` 获取水位并触发分级压缩
- **模块4 安全校验模块**采用拦截器模式注入各服务调用链（见 [00-overview §6.1](../00-overview/tech-stack-and-architecture.md)），非独立调用
- 编码实现时，4 个模块的接口契约需在 `agent-proto` 中明确定义，确保内聚但可独立测试

---

## 2. 主流模型详细计费参考表

补充 [09-governance §3.2](../09-governance-and-deployment/governance-and-middleware.md) 缺失的具体模型单价，用于成本测算与 `model_route_rule` 配置参考。

### 2.1 海外主流模型公开计费（美元/百万 Token）

| 模型 | 输入单价 | 输出单价 | 最大上下文 | 档位 | 核心备注 |
|---|---|---|---|---|---|
| GPT-4o | $5.00 | $15.00 | 128K | strong | 综合能力均衡，工具调用成熟 |
| GPT-4o Mini | $0.15 | $0.60 | 128K | light | 性价比极高，轻量任务首选 |
| Claude 3.5 Sonnet | $3.00 | $15.00 | 200K | strong | 长上下文、代码能力突出 |
| Claude 3 Opus | $15.00 | $75.00 | 200K | strong+ | 顶级推理能力，高成本 |
| Claude 3 Haiku | $0.25 | $1.25 | 200K | light | 低延迟低成本，轻量任务适配 |
| Gemini 1.5 Pro | $1.25 | $5.00 | 1M | middle | 超长上下文性价比高 |
| Gemini 1.5 Flash | $0.075 | $0.30 | 1M | light | 超低成本，长文档处理首选 |

> 数据来源：detail-MRD 311-319 行，2026 年公开 API 官方价。实际采购价以合同为准，配置于 `model_provider` 表。

### 2.2 国内主流模型计费（元/千 Token）

国内模型同档位价格约为海外的 1/3~1/2：

| 档位 | 代表模型 | 输入单价（元/千Token） | 输出单价（元/千Token） |
|---|---|---|---|
| 强模型 | 通义千问 Max / 文心一言 4.0 / DeepSeek V3 | 0.02 ~ 0.05 | 0.06 ~ 0.15 |
| 中等模型 | 通义千问 Plus / 文心 ERNIE Speed / DeepSeek Chat | 0.005 ~ 0.015 | 0.01 ~ 0.04 |
| 轻量模型 | 通义千问 Turbo / 文心 ERNIE Lite / DeepSeek Lite | 0.001 ~ 0.003 | 0.002 ~ 0.006 |

### 2.3 计费规则要点（影响成本测算）

| 规则 | 说明 | 对成本影响 |
|---|---|---|
| 输入/输出分开计费 | 输出单价通常为输入的 3~5 倍 | 控制输出长度降本效果显著 |
| Prompt 缓存折扣 | 固定 Prompt 命中缓存，输入 Token 仅按原价 10%~50% 计费 | Agent 固定开销占比高，可降本 30%+ |
| 超长上下文加价 | 超过指定长度（如 128K）后，超出部分单价上浮 50%~100% | 长上下文任务需额外核算 |
| 函数调用不额外收费 | 仅按输入输出 Token 计费 | 工具调用无额外成本 |
| 流式与普通一致 | 流式输出计费规则相同 | 无差异 |
| 单次向上取整 | 高频小请求有小幅溢价 | 批量调用更经济 |

---

## 3. 代码场景 Token 消耗量测算表

代码是 Agent 场景中 Token 消耗最高的场景，detail-MRD 281-305 行提供了量化基准，补充 [09-governance §3.2](../09-governance-and-deployment/governance-and-middleware.md) 的 L1/L2/L3 通用基准。

### 3.1 单任务全链路 Token 构成

```
单任务总 Token = 固定开销 + 动态开销 + 累加开销

固定开销（30%~60%）：系统Prompt + 召回的历史代码片段 + 依赖定义（每次必带）
动态开销（40%~70%）：用户需求 + 当前代码上下文 + 报错栈 + 工具执行结果 + 模型输出代码
累加开销：ReAct 多轮调试 + 多次工具调用 + 反思修正（复杂任务可使总消耗翻倍）
```

### 3.2 不同复杂度任务量化参考

| 任务等级 | 场景示例 | 输入 Token | 输出 Token | 单轮总消耗 | 多轮调试总消耗 |
|---|---|---|---|---|---|
| 简单 | 单函数编写、单行 Bug 修复 | 3K ~ 5K | 1K ~ 2K | 4K ~ 7K | 8K ~ 15K（2 轮） |
| 中等 | 单模块开发、代码重构、单文件调试 | 10K ~ 20K | 4K ~ 8K | 14K ~ 28K | 40K ~ 80K（3 轮） |
| 复杂 | 多文件依赖调试、架构改造、全项目排错 | 30K ~ 90K | 10K ~ 30K | 40K ~ 120K | 120K ~ 300K（3-5 轮） |

### 3.3 影响消耗的关键因素

| 因素 | 影响程度 | 管控手段 |
|---|---|---|
| 上下文携带量（是否传入全量依赖文件、接口定义、第三方库文档） | 最大变量 | [07-code-retrieval §3.5](../07-code-retrieval/code-retrieval-system.md) Token 感知分级裁剪 |
| 调试轮数（Bug 修复通常 2~5 轮，Token 近似线性增长） | 高 | `agent_definition.max_steps` 熔断 + 增量重规划优先 |
| 工具返回结果（执行日志、检索片段全额注入） | 中 | [05-tool-engine §8](../05-tool-engine/tool-and-invocation-system.md) 结果标准化清洗 + Token 上限 |
| 提示词冗余（编码规范、工具描述） | 中 | `system_prompt` 与 `core_constraints` 分离 + Schema 最小化 |

### 3.4 补充参考换算

- 1KB 纯 Python 代码 ≈ 300~500 Token
- 中文需求描述的 Token 密度是英文的 1.5~2 倍（对应成本更高）
- 128K 上下文窗口下，代码任务建议预留 ≤30% 给历史代码召回（即 ≤38K Token）

---

## 4. BPE 分词规则与计费细节

补充 [09-governance §3.2](../09-governance-and-deployment/governance-and-middleware.md) 缺失的底层计费规则，影响 `model-gateway.CountTokens` 的估算精度与成本测算。

### 4.1 BPE 分词计数规则

主流大模型基于 BPE（Byte Pair Encoding）分词：

| 文本类型 | 换算关系 | 示例 |
|---|---|---|
| 英文 | 约 4 个字符 ≈ 1 Token | "hello world" ≈ 3 Token |
| 中文 | 约 1.3~2 个汉字 ≈ 1 Token | "你好世界" ≈ 2~3 Token |
| 代码 | 标识符按驼峰/下划线拆分 | `getUserOrder` ≈ 3~4 Token |

**关键结论**：相同字数下中文 Token 量是英文的 1.5~2 倍，对应成本更高。`model-gateway.CountTokens` 对中文按 1.7 倍系数估算（见 [09-governance §3.2c](../09-governance-and-deployment/governance-and-middleware.md)），精确计数依赖各供应商 tokenizer。

### 4.2 Prompt 缓存折扣机制

| 供应商 | 缓存命中计费 | 适用场景 | 降本幅度 |
|---|---|---|---|
| Anthropic | 命中部分按原价 10%（即 1 折） | 固定系统 Prompt、工具描述 | 输入成本降 90% |
| OpenAI | 命中部分按原价 50%（即 5 折） | 固定 Prompt 前缀 | 输入成本降 50% |

**Agent 场景适用性**：系统 Prompt、工具描述、固定规则占固定开销 30%~60%，开启 `ChatRequest.enable_prompt_cache` 后，固定部分按 10%~50% 计费，整体可降本 30%+。

配置位置：`model_route_rule` 表 `preferred_model` 对应的供应商需支持缓存；`tool_registry.prompt_cache_key` 标记可缓存工具。

### 4.3 超长上下文加价

| 供应商 | 阈值 | 超出部分加价 | 影响场景 |
|---|---|---|---|
| OpenAI | 128K | +50%~100% | 长文档分析、大代码库检索 |
| Anthropic | 200K | 部分模型阶梯加价 | 超长上下文任务 |

**管控建议**：
- `agent_definition.max_token` 设置硬上限，触发 [04-memory §6](../04-memory/memory-system-design.md) 四级水位压缩
- 代码检索单次 Token 预算默认 8K（见 [07-code-retrieval §7.3](../07-code-retrieval/code-retrieval-system.md)），避免触发加价区间
- 长文档任务优先选择 Gemini 1.5 Pro（1M 上下文，加价区间更宽）

### 4.4 其他计费细节

- 函数调用/工具调用**不额外收费**，仅按输入输出 Token 计费
- 流式输出与普通调用计费规则一致
- 单次调用 Token 向上取整计费，高频小请求有小幅溢价
- 批量 API、微调、专属部署有单独定价体系（本平台不涉及）

---

## 5. 完整业务示例

补充主文档集缺少的端到端具体业务示例，便于理解各模块协作。对应 detail-MRD 86-94、666-698、866-874、1363-1370 行。

### 5.1 示例一：行业调研任务全链路（6 子任务 DAG）

**用户需求**：「调研 2026 年上半年国内大模型厂商的融资情况，对比头部 5 家厂商的技术路线和商业化进展，输出 15 页 PPT 大纲，所有数据标注官方来源」

#### 5.1.1 复杂度识别

- **规则初筛**：命中「调研、对比、报告」关键词，涉及融资、技术、商业化多个领域 → 初步判定 L3
- **模型精判**：确认跨 3 个领域、需 5 步以上执行、多次搜索工具调用 → 最终判定 **L3 复杂任务**，进入规划流程

#### 5.1.2 规划生成 DAG

```
[子任务1: 融资数据采集与厂商筛选] ──┬──> [子任务2: 头部厂商技术路线调研] ──┐
  (入口节点, 无依赖)              │   (依赖: 子任务1 厂商名单)          │
                                  │                                     ├──> [子任务4: 多维度综合对比分析]
                                  └──> [子任务3: 头部厂商商业化进展] ──┘    (依赖: 子任务2,3)
                                       (依赖: 子任务1, 与子任务2并行)            │
                                                                                 ▼
                                                                        [子任务5: PPT大纲结构化生成]
                                                                              (依赖: 子任务4)
                                                                                 │
                                                                                 ▼
                                                                        [子任务6: 数据来源标注与事实校验]
                                                                              (依赖: 子任务5, 出口节点)
```

**并行批次**：`[["st1"], ["st2","st3"], ["st4"], ["st5"], ["st6"]]`（st2 与 st3 无相互依赖，同批次并行执行）

#### 5.1.3 子任务元数据示例（子任务1）

```json
{
  "nodeId": "n1",
  "subtaskId": "st_001",
  "title": "融资数据采集与厂商筛选",
  "goal": "检索2026上半年大模型融资事件，按金额排序选出头部5家厂商",
  "agentId": "ag_research_001",
  "abilityTags": ["search", "finance", "data_collection"],
  "inputs": {"period": "2026-H1", "topN": 5},
  "outputs": {
    "companies": "厂商名单数组",
    "fundingEvents": "融资事件明细",
    "sources": "来源链接数组"
  },
  "config": {"maxRetries": 2, "timeoutMs": 60000, "modelTier": "middle"},
  "dependsOn": [],
  "status": "pending"
}
```

#### 5.1.4 全链路执行步骤

| 步骤 | 阶段（PRD 第一节(三)） | 模块协作 |
|---|---|---|
| 1 | 接入与上下文初始化 | gateway 鉴权 → session 加载历史 → 标准化为 task_instance |
| 2 | 意图理解与任务形式化 | task-orchestrator → planning.AssessComplexity → L3 → 生成 Task Schema |
| 3 | 任务规划与编排 | planning.Plan → 生成 6 节点 DAG（见上）→ agent-repo 匹配 research Agent |
| 4 | Agent 运行与工具调用 | task-orchestrator → RocketMQ 分发 → agent-runtime ReAct 循环 → tool-engine 检索工具 + model-gateway 推理 |
| 5 | 结果聚合与质量校验 | st6 执行事实校验（quality-service 三级校验：来源存在性 + 数据一致性 + 目标完成度） |
| 6 | 响应输出与会话收尾 | task-orchestrator 聚合 → session 输出 Markdown 大纲 → gateway 返回用户 |
| 7 | 记忆沉淀与能力迭代 | memory.WriteLongTerm 沉淀调研框架 → quality-service 评估 → 沉淀为「行业调研」task_template |

---

### 5.2 示例二：异常处理完整 walkthrough

**场景**：上述示例中「子任务1 融资数据采集」执行失败。

| 步骤 | 异常处理防线 | 执行动作 | 对应设计文档 |
|---|---|---|---|
| 1 | 接口级重试 | 检索工具返回 503 超时 → 触发指数退避重试（3 次：1s/2s/4s） | [05-tool-engine §3](../05-tool-engine/tool-and-invocation-system.md) |
| 2 | 资源级降级 | 重试全部失败，工具触发熔断（5 分钟）→ 切换备用检索工具执行 | [05-tool-engine §6](../05-tool-engine/tool-and-invocation-system.md) |
| 3 | 单步执行重试 | 备用工具返回结果，校验发现数据不全、质量评分 65 分 → 触发单步重试，附带修正提示 | [06-agent-runtime §3](../06-agent-runtime/agent-runtime-engine.md) Reflexion |
| 4 | 子任务级回滚 | 重试后质量仍不达标，累计 2 次子任务重试失败 → 触发子任务回滚，清理中间数据 | [03-task-engine §10](../03-task-engine/task-orchestration-and-planning.md) |
| 5 | 增量重规划 | 无可用降级方案 → 触发增量重规划，将「全网检索」拆分为「多站点分别检索 + 数据聚合」 | [03-task-engine §5](../03-task-engine/task-orchestration-and-planning.md) |
| 6 | 恢复执行 | 新子任务执行成功，校验通过 → 继续推进后续任务，全链路标记异常处理过程 | task-orchestrator 状态机 |
| 7 | 人工介入兜底 | 若重规划后仍失败，且达到重规划上限 → 转入人工审核队列 | [06-agent-runtime §7](../06-agent-runtime/agent-runtime-engine.md) |

**关键状态流转**：`SUBTASK_RUNNING` → `FAILED`（重试中）→ `REPLANNING` → `SUBTASK_RUNNING`（新 DAG 版本）→ `SUCCESS`

---

### 5.3 示例三：静默漂移处理

**现象**：代码 Agent 的工具调用率连续 3 天下降，代码生成准确率同步下滑，无明显报错。

| 步骤 | 漂移治理层 | 执行动作 | 对应设计文档 |
|---|---|---|---|
| 1 | 监测发现 | 行为指标监控触发告警：工具调用率从 72% 降至 48%，连续 3 天同方向下滑 | [09-governance §2.2](../09-governance-and-deployment/governance-and-middleware.md) |
| 2 | 自动止损 | 系统级重度漂移 → 自动回滚至上一稳定 Prompt 版本（`agent_version.is_stable=1`），工具调用率快速恢复 | [09-governance §2.3](../09-governance-and-deployment/governance-and-middleware.md) |
| 3 | 根因定位 | 关联变更记录，发现底层大模型静默更新小版本，对工具调用指令遵循度下降 | [09-governance §2.4](../09-governance-and-deployment/governance-and-middleware.md) |
| 4 | 优化修复 | 调整工具调用 Prompt，补充示例，强化指令约束 | agent_definition 新版本 |
| 5 | 灰度验证 | 小流量灰度测试，工具调用率与准确率恢复至基线水平后全量发布 | [09-governance §2.4](../09-governance-and-deployment/governance-and-middleware.md) |
| 6 | 闭环沉淀 | 将该场景加入 `eval_baseline` 黄金基准集（behavior 类型），后续版本更新自动校验 | [01-database §8.2](../01-database/database-schema-design.md) |

---

## 6. 低代码配置台（前端控制台范围说明）

detail-MRD 462、491 行提及的「低代码配置台」「前端交互控制台」属于前端范畴，本轮设计文档聚焦后端全量核心服务（见 [00-overview §1](../00-overview/tech-stack-and-architecture.md)），此处补充前端范围界定，供后续前端设计参考。

### 6.1 前端控制台模块规划

| 模块 | 面向角色 | 核心功能 | 后端依赖服务 |
|---|---|---|---|
| 运营后台 | 平台运营 | 租户管理、Agent 上下线审批、配额配置、审计查询、运营报表 | agent-repo / risk-control / quality-service |
| Agent 配置工作台 | Agent 开发 | 低代码配置 Agent 角色设定、工具权限、记忆范围、输出规则、模型档位；Prompt 编辑与预览 | agent-repo / tool-engine / model-gateway |
| 调试沙箱 | Agent 开发 | 单任务模拟执行、DAG 可视化、断点调试、步骤回放、Token 水位实时展示 | task-orchestrator / agent-runtime / observability |
| 终端对话界面 | 终端用户 | 多轮对话、流式输出、文件上传、任务进度展示、结果反馈 | session / gateway |
| 任务监控大屏 | 运营/开发 | 实时任务状态、链路追踪、异常告警、成本看板 | observability / quality-service |

### 6.2 低代码配置台核心能力（detail-MRD 462 行）

```
Agent 配置工作台（低代码）
├── 角色设定：表单化配置 system_prompt + core_constraints（分离编辑，核心约束只读保护）
├── 工具权限：可视化勾选 bound_tools（按 R1/R2/R3 分组，R3 需审批流程）
├── 记忆范围：配置 domain 分域、importance_score 阈值、tier 生命周期策略
├── 输出规则：配置 output_format、max_token、temperature 等参数
├── 模型档位：选择 model_tier（light/middle/strong）+ 绑定 model_route_rule
├── 反思模式：配置 reflection_mode（none/single/multi）+ 最大反思次数
├── 预览与调试：实时预览组装后的完整 Prompt + 沙箱试跑
└── 版本管理：保存为 agent_version 草稿 → 发布 → 灰度 → 全量
```

### 6.3 前后端接口约定

前端控制台通过 [02-api 文档](../02-api/api-specification.md) 定义的 REST API 与后端交互，无需额外接口。低代码配置台写入 `agent_definition` 表，发布时生成 `agent_version` 快照。

---

## 7. 回填状态跟踪

以下内容已回填至主文档，保持单一事实来源：

| 补遗章节 | 回填目标 | 回填方式 | 状态 |
|---|---|---|---|
| §2 模型计费表 | [09-governance §3.2.d](../09-governance-and-deployment/governance-and-middleware.md) | 已回填为 §3.2.d「主流模型计费参考表」（含 d-1 海外 / d-2 国内 / 计费规则要点） | ✅ 已回填 2026-06-26 |
| §3 代码 Token 测算 | [09-governance §3.2.e](../09-governance-and-deployment/governance-and-middleware.md) | 已回填为 §3.2.e「代码场景 Token 消耗测算」（含构成公式 + 三级量化表 + 换算参考） | ✅ 已回填 2026-06-26 |
| §4 BPE 与缓存折扣 | [09-governance §3.2.f](../09-governance-and-deployment/governance-and-middleware.md) | 已回填为 §3.2.f「BPE 分词规则与计费细节」（含 f-1 分词 / f-2 缓存折扣 / f-3 长上下文加价） | ✅ 已回填 2026-06-26 |

> **回填后状态说明**：§2/§3/§4 的内容已完整回填到 doc 09-governance §3.2.d/e/f，主文档为单一事实来源。本补遗文档对应章节保留作为历史参考与审计轨迹，编码实现请以 [09-governance §3.2](../09-governance-and-deployment/governance-and-middleware.md) 为准。
>
> §1（Agent 1+4 内聚架构）、§5（业务示例）、§6（低代码配置台范围）不回填主文档，保持独立呈现。
