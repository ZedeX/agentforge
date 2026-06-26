# 记忆管理系统工程详细设计

> 文档版本：v1.0  |  更新日期：2026-06-26  |  对应 PRD：第二节(一)记忆系统设计

## 0. 文档定位与依赖

本文档为 `memory-service`（端口 8088）的工程级详细设计，落地 PRD「第二节(一)记忆系统设计」全部要求：三级记忆架构、记忆膨胀治理、上下文 Token 阈值与压缩规则。

依赖约定来自：
- [../00-overview/tech-stack-and-architecture.md](../00-overview/tech-stack-and-architecture.md)：微服务清单、端口、存储分配、包名规范 `com.agentplatform.memory.*`
- [../01-database/database-schema-design.md](../01-database/database-schema-design.md)：记忆域表（第 3 节）、Milvus Collection（第 10 节）、Redis 短期记忆 Key（第 3.4 节）
- [../02-api/api-specification.md](../02-api/api-specification.md)：MemoryService gRPC 接口（第 4 节）
- [../../PRD.md](../../PRD.md)：第二节(一)记忆系统设计

### 0.1 设计目标

| 目标 | 衡量指标 |
|---|---|
| 跨会话记忆持久化 | 长期记忆写入成功率 ≥ 99.9%，召回 P95 ≤ 80ms |
| 上下文 Token 可控 | 单任务平均 Token ≤ 30K（中等任务基线），熔断零溢出 |
| 记忆膨胀可控 | 单租户长期记忆年增长 ≤ 50 万条，冷记忆归档率 ≥ 60% |
| 召回精准 | Top-10 命中率 ≥ 0.85，召回 Token 占比 ≤ 上下文 20% |
| 全链路留痕 | 写入/召回/压缩/蒸馏操作 100% 可回溯 |

### 0.2 技术栈锁定

| 组件 | 版本 | 用途 |
|---|---|---|
| Java 17 + Spring Boot 3.2 | OpenJDK 17.0.10+ | 运行时 |
| Milvus | 2.4.x | 长期记忆向量库（HNSW 索引） |
| MySQL | 8.0.36 | 记忆元数据（`agent_memory` 库） |
| Redis Cluster | 7.2 | 短期记忆、瞬时草稿、Token 水位 |
| XXL-Job | 2.4.1 | 蒸馏/归档定时调度 |
| Elasticsearch | 8.13.x | 关键词召回（记忆全文索引） |
| RocketMQ | 5.3.x | `memory.write` 异步写入事件 |
| gRPC + Protobuf | 1.60.x | 与 Agent Runtime 通信 |

### 0.3 模块包结构

```
agent-memory/
└── src/main/java/com/agentplatform/memory/
    ├── shortterm/        # 短期记忆（Redis）
    ├── longterm/         # 长期记忆（Milvus + MySQL）
    ├── recall/           # 多路召回与重排
    ├── distill/          # 记忆蒸馏
    ├── compress/         # Token 水位压缩
    ├── vectorize/        # 向量化管道
    ├── pipeline/         # 写入管道
    ├── scheduler/        # XXL-Job 蒸馏调度
    ├── grpc/             # gRPC 服务实现
    └── config/           # 配置与常量
```

---

## 1. 三级记忆架构总览

```
┌─────────────────────────────────────────────────────────────────┐
│                    Agent Runtime（推理循环）                     │
└───────────────┬───────────────────────────────┬──────────────────┘
                │ LoadShortTerm / Recall         │ WriteLongTerm
                ▼                                ▼
┌──────────────────────────────┐   ┌──────────────────────────────┐
│  短期记忆（工作记忆）         │   │  长期记忆（跨会话持久）       │
│  Redis sm:{sessionId}:*      │   │  Milvus + MySQL              │
│  ┌────────────────────────┐  │   │  ┌────────────────────────┐  │
│  │ system_prompt          │  │   │  │ mem_episodic 情景       │  │
│  │ core_constraints       │  │   │  │ mem_semantic  语义      │  │
│  │ recent_msgs（N 轮）    │  │   │  │ mem_procedural 流程     │  │
│  │ tool_history           │  │   │  └────────────────────────┘  │
│  │ recalled_memories      │◀┼───┼── 多路召回 + 重排 + Top-N     │
│  └────────────────────────┘  │   │                              │
│  TTL: 2h                     │   │  tier: 1=热 2=温 3=冷         │
└──────────────┬───────────────┘   └──────────────┬───────────────┘
               │ 每轮推理结束                      │ 定期蒸馏
               ▼                                  ▼
┌──────────────────────────────┐   ┌──────────────────────────────┐
│  瞬时记忆（草稿）             │   │  蒸馏日志 memory_distill_log │
│  Redis sm:{sessionId}:draft  │   │  全局摘要 → 主题摘要 → 细节   │
│  TTL: 30min，轮次结束丢弃     │   └──────────────────────────────┘
└──────────────────────────────┘
```

### 1.1 短期记忆（工作记忆）

**存储介质**：Redis Cluster（Key 见 [../01-database/database-schema-design.md#34-redis-短期记忆-key-设计](../01-database/database-schema-design.md)）

**Key 设计**：

| Key 模式 | Redis 类型 | TTL | 字段/内容 |
|---|---|---|---|
| `sm:{sessionId}:ctx` | Hash | 2h | `system_prompt`、`core_constraints`、`task_goal`、`agent_id`、`token_budget`、`compress_level` |
| `sm:{sessionId}:recent_msgs` | List | 2h | 最近 N 轮消息（JSON 序列化），LPUSH 入队，LTRIM 截断 |
| `sm:{sessionId}:tool_history` | List | 2h | 本轮工具调用记录（callId/toolId/input摘要/output摘要） |
| `sm:{sessionId}:recalled` | String(JSON) | 2h | 本轮注入的长期记忆片段（避免重复召回） |
| `sm:{sessionId}:steps` | List | 2h | ReAct 推理步骤栈（think/act/observe/reflect） |
| `sm:{sessionId}:token_water` | String | 2h | 当前 Token 水位百分比（如 `72.5`） |
| `sm:{sessionId}:draft` | String | 30min | 瞬时记忆草稿（单轮推理中间态） |

**`sm:{sessionId}:ctx` Hash 详细字段**：

```json
{
  "session_id": "ss_a1b2c3d4",
  "agent_id": "ag_1001",
  "tenant_id": 1001,
  "user_id": "u_123",
  "domain": "order",
  "system_prompt": "你是订单查询助手...",
  "core_constraints": "1. 必须通过工具查询；2. 禁止编造订单号",
  "task_goal": "查询最近 7 天订单",
  "token_budget": 128000,
  "token_used": 91200,
  "token_water_percent": 71.25,
  "compress_level": "light",
  "max_recent_turns": 8,
  "last_compress_at": "2026-06-26T10:05:00.000Z"
}
```

**短期记忆组成（注入大模型上下文窗口的内容）**：

| 组成 | 来源 | Token 占比上限 | 说明 |
|---|---|---|---|
| 系统提示词 | `agent_definition.system_prompt` | 5% | Agent 角色定义，压缩时优先保留 |
| 核心约束 | `agent_definition.core_constraints` | 3% | 不可压缩区，漂移锚定基准 |
| 任务目标 | `task_instance.goal` | 2% | 当前任务目标描述 |
| 最近 N 轮对话 | `sm:{sessionId}:recent_msgs` | 30%~50% | 动态 N，按 Token 预算反推 |
| 工具调用历史 | `sm:{sessionId}:tool_history` | 10%~20% | 本轮工具 input/output 摘要 |
| 召回的长期记忆 | `sm:{sessionId}:recalled` | ≤ 20% | 多路召回 Top-N 注入 |
| 预留推理空间 | - | ≥ 15% | 模型输出 Token 预留 |

**Token 窗口限制规则**：
- 单次注入短期记忆的 Token 总量 ≤ `agent_definition.max_token` × 0.85（预留 15% 输出空间）
- `recent_msgs` 的轮数 N 动态计算：`N = min(max_recent_turns, (token_budget × 0.5 - other_used) / avg_turn_tokens)`
- 当 `token_water_percent ≥ 70%` 时触发压缩，详见第 4 节

### 1.2 长期记忆

**三类长期记忆**（对应 Milvus 三个 Collection，见 [../01-database/database-schema-design.md#101-collection-规划](../01-database/database-schema-design.md)）：

| 类型 | Milvus Collection | 触发写入时机 | 内容示例 | 关联表字段 |
|---|---|---|---|---|
| 情景记忆 episodic | `mem_episodic` | 用户交互完成、会话归档 | "用户 u_123 偏好周日晚上下单"、"上次咨询了退款流程" | `user_id` 非空 |
| 语义记忆 semantic | `mem_semantic` | 任务归档、知识沉淀 | "订单状态包含：待支付/已发货/已完成/已取消"、"退货政策 7 天无理由" | `domain` 标识业务域 |
| 流程记忆 procedural | `mem_procedural` | 最佳实践沉淀、失败解决方案 | "处理高优先级客诉的标准流程：1.确认订单 2.查询历史 3.给方案" | 高频流程可转 `task_template` |

**Milvus Collection Schema 统一约定**（以 `mem_episodic` 为例，详见 [../01-database/database-schema-design.md#102-collection-schema-示例mem_episodic](../01-database/database-schema-design.md)）：

```json
{
  "fields": [
    {"name": "vector_id", "type": "VarChar", "max_length": 64, "is_primary": true},
    {"name": "embedding", "type": "FloatVector", "dim": 1024},
    {"name": "tenant_id", "type": "Int64"},
    {"name": "domain", "type": "VarChar", "max_length": 64},
    {"name": "user_id", "type": "VarChar", "max_length": 64},
    {"name": "agent_id", "type": "Int64"},
    {"name": "memory_id", "type": "VarChar", "max_length": 32},
    {"name": "importance_score", "type": "Float"},
    {"name": "tier", "type": "Int8"},
    {"name": "created_at", "type": "Int64"},
    {"name": "valid", "type": "Bool"}
  ],
  "index": {"field": "embedding", "type": "HNSW", "params": {"M": 16, "efConstruction": 256}}
}
```

- **Partition Key**：`domain`，实现业务域物理隔离（订单域/客服域/代码域不串扰）
- **多租户隔离**：召回时 `tenant_id` 作为过滤表达式 `tenant_id == 1001`
- **失效控制**：`valid=false` 的记忆在召回时过滤，逻辑删除而非物理删除

**写入机制（5 步管道）**：

```
任务完成事件 ──▶ ① 信息提取 ──▶ ② 标签分类 ──▶ ③ 重要性评分
                                                      │
                                                      ▼
                                            ④ 向量化 ──▶ ⑤ 去重校验 ──▶ 入库
```

详见第 3.1 节。

**召回机制（多路召回 + 重排）**：

```
查询 query
   │
   ├─▶ 语义向量召回（Milvus，top_k=50）
   ├─▶ 关键词召回（ES，top_k=30）
   ├─▶ 时间权重召回（MySQL，最近 N 天高重要性）
   └─▶ 标签匹配召回（MySQL tags JSON）
                              │
                              ▼
                      候选合并去重
                              │
                              ▼
                      融合重排（加权打分）
                              │
                              ▼
                      Top-N 动态截断（按 Token 配额）
                              │
                              ▼
                      注入短期记忆 sm:{sessionId}:recalled
```

详见第 3.2、第 5 节。

### 1.3 瞬时记忆

**存储介质**：Redis Key `sm:{sessionId}:draft`，TTL 30min。

**内容**：单轮 ReAct 推理中的中间思考草稿、CoT 思维链中间结果、临时计算的中间数据结构。

**生命周期**：
- 单步推理开始时初始化
- 推理过程中可追加（think → act → observe 各阶段草稿）
- 轮次结束（step 完成或消息返回）时**显式丢弃**（DEL Key）
- 不进入 `recent_msgs`，不进入长期记忆
- 异常崩溃时由 TTL 自动回收，不污染下一次推理

**与短期记忆的边界**：
- 瞬时记忆仅当前步可见；短期记忆跨步可见
- 瞬时记忆不参与 Token 水位计算（不计入大模型上下文）
- 仅当推理结果产生新事实时，经写入管道沉淀为长期记忆

---

## 2. 长期记忆写入管道

### 2.1 触发时机

| 触发源 | 事件 | 写入类型 |
|---|---|---|
| Agent Runtime | 任务完成 `task.subtask.done` | 情景/语义 |
| Agent Runtime | 会话结束 `session.closed` | 情景（用户偏好） |
| 用户标注 | 显式 `POST /api/v1/memories` | 三类均可 |
| 系统沉淀 | 蒸馏任务生成新记忆 | 主题摘要 |
| 质量评估 | Badcase 修复方案沉淀 | 流程 |

异步通道：RocketMQ Topic `memory.write`（见 [../02-api/api-specification.md#11-异步事件规范rocketmq-topics](../02-api/api-specification.md)），消费者幂等去重。

### 2.2 五步管道详解

#### ① 信息提取

从原始事件（任务结果、对话历史）中抽取可记忆的"事实单元"。

**输入**：`WriteLongTermRequest`（gRPC，见 [../02-api/api-specification.md#4-记忆管理-apigrpc内部](../02-api/api-specification.md)）

**伪代码**：

```text
function extract(rawEvent):
    # 调用轻量模型抽取事实单元
    facts = model.extract(
        prompt = EXTRACT_TEMPLATE,
        input = rawEvent.content + rawEvent.context
    )
    # 每个 fact 包含：content / suggested_type / suggested_tags
    return facts
```

**输出**：`List<FactCandidate>`，每条 `FactCandidate` 含：
- `content`：精炼后的事实陈述（≤ 200 字）
- `suggested_type`：episodic/semantic/procedural
- `suggested_tags`：标签数组

#### ② 标签分类

将 `suggested_tags` 标准化到受控词表，并补全 `domain`。

| 标签维度 | 取值示例 | 来源 |
|---|---|---|
| `domain` | order/cs/code/finance | 任务/Agent 配置 |
| `scene` | query/refund/complaint | 模型抽取 |
| `entity` | user/order/product | 实体识别 |
| `time` | short_term/long_term | 按内容时效性 |

#### ③ 重要性评分

见第 4.4 节算法，输出 `importance_score ∈ [0, 1]`，落库到 `memory_long_term.importance_score`。

**评分阈值**：
- `score ≥ 0.7` → tier=1（热）
- `0.4 ≤ score < 0.7` → tier=2（温）
- `score < 0.4` → tier=3（冷），且 `ttl_at = now + 90d`

#### ④ 向量化

见第 4 节向量化管道，输出 1024 维 FloatVector，写入 Milvus 取得 `vector_id`。

#### ⑤ 去重校验

写入前在 Milvus 同 Collection 同 `domain` Partition 内做相似度检索：

```text
function dedupCheck(newVector, domain, memory_type, tenant_id):
    # 同域同类型检索 top 1
    hit = milvus.search(
        collection = "mem_" + memory_type,
        partition = domain,
        vector = newVector,
        top_k = 1,
        filter = "tenant_id == {tenant_id} and valid == true",
        ef = 64
    )
    if hit and hit[0].score >= SIM_THRESHOLD:
        # 高相似，执行更新而非新增
        existing = loadMetadata(hit[0].memory_id)
        existing.content = merge(existing.content, newContent)
        existing.importance_score = max(existing.importance_score, newScore)
        existing.recall_count = existing.recall_count  # 保留
        updateMetadata(existing)
        # 更新 Milvus 向量（同 vector_id 重写）
        milvus.upsert(vector_id = existing.vector_id, vector = newVector)
        return WriteAck(memory_id = existing.memory_id, deduplicated = true,
                        merged_memory_id = existing.memory_id)
    else:
        # 新增
        memory_id = snowflake()
        milvus.insert(...)
        mysql.insert(memory_long_term ...)
        return WriteAck(memory_id = memory_id, deduplicated = false)
```

**去重阈值**（按记忆类型分级）：

| 记忆类型 | SIM_THRESHOLD | 说明 |
|---|---|---|
| semantic | 0.92 | 语义高度同质才合并 |
| episodic | 0.85 | 同用户同主题合并 |
| procedural | 0.88 | 流程相近即合并 |

---

## 3. 多路召回与重排序

### 3.1 四路召回策略

| 召回路 | 数据源 | 召回算法 | top_k | 适用场景 |
|---|---|---|---|---|
| 语义向量 | Milvus `mem_*` | HNSW 余弦相似 | 50 | 模糊语义匹配 |
| 关键词 | Elasticsearch | BM25 + 标识符分词 | 30 | 精确实体命中 |
| 时间权重 | MySQL `memory_long_term` | `importance_score × time_decay` 排序 | 20 | 偏好最近行为 |
| 标签匹配 | MySQL `tags` JSON | JSON_CONTAINS 标签交集计数 | 20 | 同主题扩展 |

**召回参数基线**（见 [../01-database/database-schema-design.md#103-召回参数基线](../01-database/database-schema-design.md)）：

| 召回类型 | top_k | ef（搜索） | 备注 |
|---|---|---|---|
| 短期高频 | 20 | 64 | 召回后重排取 5 |
| 长期深度 | 50 | 128 | 召回后重排取 10 |
| 工具召回 | 30 | 64 | 按场景标签前置过滤 |

### 3.2 融合重排算法

**输入**：四路召回的候选集 `C = C_vec ∪ C_kw ∪ C_time ∪ C_tag`（去重后）。

**重排公式**（加权融合）：

```
final_score(m) = w_semantic × sim_semantic(m)
               + w_time     × time_decay(m)
               + w_import   × importance_score(m)
               + w_tag      × tag_match(m)
```

**权重配置**（按记忆类型动态调整）：

| 记忆类型 | w_semantic | w_time | w_import | w_tag | 召回侧重 |
|---|---|---|---|---|---|
| episodic | 0.40 | 0.30 | 0.20 | 0.10 | 时间衰减权重高（用户偏好近期） |
| semantic | 0.55 | 0.10 | 0.25 | 0.10 | 语义相似主导 |
| procedural | 0.30 | 0.10 | 0.30 | 0.30 | 标签匹配主导（流程按场景） |

**子项定义**：

- `sim_semantic(m) ∈ [0,1]`：Milvus 余弦相似度（已归一化）
- `time_decay(m) = exp(-Δt / half_life)`，`Δt` 为距今天数，`half_life`：episodic=30d，semantic=180d，procedural=365d
- `importance_score(m) ∈ [0,1]`：直接取 `memory_long_term.importance_score`
- `tag_match(m) = |query_tags ∩ m.tags| / |query_tags|`：标签交集占比

**伪代码**：

```text
function recall(query, agent_id, session_id, strategies, top_k, token_budget):
    candidates = []
    if "vector" in strategies:
        candidates += milvus.search(collection, partition=domain, query_vector, top_k=50, ef=128)
    if "keyword" in strategies:
        candidates += es.search(index="memory_" + domain, query, top_k=30)
    if "time" in strategies:
        candidates += mysql.query("SELECT * FROM memory_long_term WHERE ... ORDER BY importance_score * EXP(-DATEDIFF(NOW(),created_at)/half_life) DESC LIMIT 20")
    if "tag" in strategies:
        candidates += mysql.query("SELECT * FROM memory_long_term WHERE JSON_OVERLAPS(tags, ?) ORDER BY ... LIMIT 20")

    # 去重
    candidates = dedupById(candidates)

    # 重排
    for m in candidates:
        m.relevance_score = w_semantic * m.sim_semantic
                          + w_time * timeDecay(m)
                          + w_import * m.importance_score
                          + w_tag * tagMatch(m)

    candidates.sort(by = relevance_score, desc = true)

    # 相关性阈值过滤
    candidates = candidates.filter(m -> m.relevance_score >= RELEVANCE_THRESHOLD)

    # Token 配额动态截断
    final = takeWithinTokenBudget(candidates, token_budget)
    return final
```

### 3.3 Top-N 动态调整

**Token 配额计算**：

```
recall_token_budget = min(
    token_budget × 0.20,                        # 不超过上下文 20%
    agent_definition.max_token - token_used - 15000  # 预留 15K 输出
)
```

**贪心截断**：按 `relevance_score` 降序累加 Token，直到达到 `recall_token_budget`，截断后剩余的记忆不注入。

**记录召回日志**：写入 `memory_recall_log`（见 [../01-database/database-schema-design.md#33-memory_recall_log-召回日志表](../01-database/database-schema-design.md)），含 `recall_strategies`、`candidate_count`、`final_count`、`returned_memory_ids`、`duration_ms`。

---

## 4. 向量化管道

### 4.1 文本切片策略

| 记忆类型 | 切片策略 | 单片 Token 上限 |
|---|---|---|
| episodic | 单事实切片（一个事件一段） | 256 |
| semantic | 按语义段落切（保持事实完整） | 512 |
| procedural | 按"步骤组"切（一个完整流程一段） | 1024 |

**切片规则**：
- 单条 `memory_long_term.content` 不再二次切片（写入管道已确保 ≤ 200 字）
- 文档型记忆（知识沉淀）按 `knowledge_chunk` 标准切片策略，见 [../01-database/database-schema-design.md#73-knowledge_chunk-知识切片表](../01-database/database-schema-design.md)

### 4.2 Embedding 模型选择

| 模型 | 维度 | 适用场景 | 部署方式 |
|---|---|---|---|
| `bge-large-zh-v1.5` | 1024 | 中文记忆（默认） | 本地 GPU 推理 |
| `text-embedding-3-large` | 1024（截断） | 英文/混合 | OpenAI 兼容网关 |
| `bge-m3` | 1024 | 多语言混合 | 本地 GPU 推理 |

**模型路由**：通过 `model-gateway` 统一调用，按 `domain` 与 `memory_type` 路由（见 [../02-api/api-specification.md#5-模型网关-apigrpc内部](../02-api/api-specification.md)）。

**版本兼容**：`memory_long_term` 表新增字段 `embedding_model` 记录所用模型；模型升级时新记忆走新 Collection，旧记忆灰度迁移。

### 4.3 Milvus 写入流程

```text
function writeToMilvus(content, memory_id, domain, memory_type, tenant_id, user_id, agent_id, importance_score, tier):
    vector = modelGateway.embed(content, model = "bge-large-zh-v1.5")
    vector_id = snowflake()
    milvus.insert(
        collection = "mem_" + memory_type,
        partition = domain,    # 按 domain 物理 Partition
        record = {
            vector_id, embedding = vector,
            tenant_id, domain, user_id, agent_id,
            memory_id, importance_score, tier,
            created_at = now(),
            valid = true
        }
    )
    return vector_id
```

### 4.4 重要性评分算法

**输入维度**：

| 维度 | 权重 | 取值依据 |
|---|---|---|
| 召回价值 `recall_value` | 0.30 | 历史相似 query 召回命中率（新记忆取 0.5） |
| 时效性 `temporal_value` | 0.20 | episodic=0.8 / semantic=0.5 / procedural=0.9 |
| 业务关键度 `business_criticality` | 0.30 | `domain` 配置 + 是否涉及写操作 |
| 用户反馈 `user_feedback` | 0.10 | 显式标注 positive=1.0 / negative=0.0 / 无=0.5 |
| 来源可信度 `source_trust` | 0.10 | system=0.9 / user=0.7 / task=0.6 |

**公式**：

```
importance_score = 0.30 × recall_value
                 + 0.20 × temporal_value
                 + 0.30 × business_criticality
                 + 0.10 × user_feedback
                 + 0.10 × source_trust
```

**结果区间**：`[0, 1]`，写入 `memory_long_term.importance_score DECIMAL(4,3)`。

**评分随时间衰减**：召回时实时计算 `effective_score = importance_score × time_decay(Δt)`，不修改原值。

---

## 5. 记忆膨胀治理

### 5.1 分级存储与生命周期淘汰

**三级 tier**（对应 `memory_long_term.tier` 字段，见 [../01-database/database-schema-design.md#31-memory_long_term-长期记忆元数据表](../01-database/database-schema-design.md)）：

| tier | 名称 | 触发条件 | 存储策略 | TTL | 召回优先级 |
|---|---|---|---|---|---|
| 1 | 热 | `importance_score ≥ 0.7` | Milvus 全量索引 + Redis 缓存热点 | 永久 | 优先召回 |
| 2 | 温 | `0.4 ≤ score < 0.7` | Milvus 索引 + MySQL 元数据 | 永久 | 正常召回 |
| 3 | 冷 | `score < 0.4` 或 90 天未召回 | Milvus 索引 valid=false + MySQL 标记 + 归档 MinIO | `ttl_at = now + 365d` | 仅深度召回 |

**生命周期状态机**：

```
    写入                 90 天未召回                ttl_at 到期
    ──▶ HOT ──────────▶ WARM ──────────▶ COLD ──────────▶ ARCHIVED
         ▲    召回增热     ▲    召回增热                      │
         └─────────────────┴──────────────────────────────────┘
                         召回回流
```

**淘汰规则**：
- 冷记忆 `ttl_at` 到期 → 异步归档到 MinIO（`memory-backup/{tenant}/{memory_id}.json`）+ Milvus `valid=false` + MySQL `valid=0`
- 归档记忆不参与常规召回，仅在 `recall_strategies` 显式包含 `archived` 时检索
- 蒸馏后被合并的源记忆 `valid=0`，但保留 `parent_memory_id` 关联

### 5.2 记忆蒸馏与压缩

**三级蒸馏结构**（对应 `memory_distill_log.summary_level` 字段，见 [../01-database/database-schema-design.md#32-memory_distill_log-记忆蒸馏日志表](../01-database/database-schema-design.md)）：

| summary_level | 名称 | 输入 | 输出 | 触发条件 |
|---|---|---|---|---|
| 3 | 细节记忆 | 原始碎片记忆 | 单条事实单元 | 写入管道已生成 |
| 2 | 主题摘要 | 同 domain + 同 tags 的细节记忆 ≥ 20 条 | 1 条主题摘要（≤ 500 字） | 周期触发（XXL-Job 每日） |
| 1 | 全局摘要 | 同 domain 的所有主题摘要 | 1 条全局摘要（≤ 1000 字） | 周期触发（XXL-Job 每周） |

**蒸馏流程伪代码**：

```text
function distill(domain, summary_level):
    if summary_level == 2:
        # 按标签分组，每组生成主题摘要
        groups = mysql.query("SELECT tags, COUNT(*) as cnt FROM memory_long_term "
                             "WHERE domain=? AND valid=1 AND distilled=0 "
                             "GROUP BY tags HAVING cnt >= 20", domain)
        for group in groups:
            source_memories = mysql.query("SELECT * FROM memory_long_term WHERE domain=? AND tags=? AND valid=1 AND distilled=0", ...)
            # 调用强模型生成摘要
            summary = model.distill(prompt=DISTILL_TEMPLATE, inputs=source_memories)
            before_token = sum(tokenCount(m.content) for m in source_memories)
            after_token = tokenCount(summary)
            # 写入新记忆
            new_memory = writeLongTerm(domain, "semantic", summary, tags=group.tags, importance=0.8)
            # 标记源记忆为已蒸馏
            for m in source_memories:
                mysql.update("UPDATE memory_long_term SET distilled=1, parent_memory_id=? WHERE memory_id=?", new_memory.id, m.memory_id)
            # 记录蒸馏日志
            mysql.insert("memory_distill_log", distill_task_id, domain, source_memory_ids, target_memory_id=new_memory.id, summary_level=2, before_token, after_token, status="success")
```

**高频流程类记忆转化**：当 `mem_procedural` 中同 tags 的记忆被召回次数 ≥ 50 且成功率 ≥ 0.8，触发转为 `task_template`（标准任务模板），见 [../01-database/database-schema-design.md#28-task_template-任务模板表](../01-database/database-schema-design.md)。

### 5.3 语义去重与关联治理

**写入时去重**：见第 2.2 节 ⑤ 步骤，基于 Milvus 相似度检索，超阈值执行更新。

**记忆关联图谱**：
- 利用 `memory_long_term.parent_memory_id` 维护蒸馏父子关系
- 利用 `tags` JSON 字段建立主题关联（同 tags 即同主题）
- 跨记忆实体关联通过 Elasticsearch 实体索引建立（实体名 → 关联 memory_id 列表）
- 后续可扩展至 Neo4j 构建记忆图谱（本期不实现，预留扩展点）

**分域隔离**：
- Milvus Partition Key = `domain`，物理隔离不同业务域
- 召回时强制 `domain` 过滤，避免跨域污染
- 多租户隔离：召回过滤表达式 `tenant_id == {current_tenant}`

### 5.4 召回侧精准限流

| 控制项 | 规则 | 配置位置 |
|---|---|---|
| 单次召回数量上限 | `top_k ≤ 50`，重排后 `final_count ≤ 10` | 代码常量 |
| 相关性阈值 | `relevance_score ≥ 0.3` 才注入 | 代码常量 |
| Token 配额 | 召回 Token ≤ 上下文剩余 × 0.20 | 动态计算 |
| 跨域隔离 | 仅召回 `domain` 匹配的记忆 | 召回过滤 |
| 召回去重 | 同 query 5 分钟内复用结果（Redis 缓存） | `recall_cache:{query_hash}` |

---

## 6. 上下文 Token 阈值与压缩规则

### 6.1 四级水位线

**基准**：`agent_definition.max_token`（如 128K）。当前水位 `token_water_percent = token_used / max_token × 100`，存储于 Redis Key `sm:{sessionId}:token_water`。

| 水位区间 | 名称 | 触发动作 | 上报频率 |
|---|---|---|---|
| `≤ 70%` | 安全 | 正常运行，无压缩 | 每步上报 |
| `70% ~ 85%` | 预警 | 触发轻度压缩（`compress_level=light`） | 每步上报 |
| `85% ~ 95%` | 临界 | 触发中度压缩（`compress_level=medium`） | 每步上报 + 告警 |
| `≥ 95%` | 熔断 | 触发重度压缩兜底（`compress_level=heavy`） | 立即告警 + 强制压缩 |

### 6.2 分级压缩规则

| 级别 | 压缩对象 | 具体动作 | 留痕 |
|---|---|---|---|
| **轻度 light** | 工具返回 | 裁剪冗余元数据字段（如 `debug_info`、`trace_id`、`duration_ms`） | 写入 `tool_call_log.output` 的 `compressed` 标记 |
| 轻度 | 思考过程 | 折叠 ReAct 的 think 阶段，仅保留决策结论 | `step_log.input_snapshot` 增加 `compressed=light` |
| 轻度 | 闲聊内容 | 折叠非任务相关对话（保留首尾各 1 条） | `recent_msgs` 增加 `compressed=light` |
| **中度 medium** | 早期对话 | 按主题摘要化（每 N 轮合并为 1 条摘要） | `session.context_summary` 字段更新 |
| 中度 | 召回记忆 | 裁剪召回数量（Top-N 减半） | `memory_recall_log.final_count` 减少 |
| 中度 | 系统提示词 | 精简 `business_config` 区，保留 `core_constraints` | `sm:{sessionId}:ctx.system_prompt` 更新 |
| 中度 | 已完成子任务 | 归档详情，仅保留结论摘要 | `task_step_log` 标记 `archived=true` |
| **重度 heavy** | 对话历史 | 滑动窗口仅保留最近 K 轮（K=3） | 早期消息归档至 `session.context_summary` |
| 重度 | 工具历史 | 仅保留工具调用结论，丢弃 input/output 细节 | `tool_call_log` 标记 `heavy_compressed=true` |
| 重度 | 非核心信息 | 清空召回的非核心补充记忆（保留 top 1） | `memory_recall_log` 记录裁剪 |

### 6.3 执行原则

| 原则 | 落地要求 |
|---|---|
| 全量留痕 | 每次压缩记录 `compress_level`、`before_token`、`after_token`、`compressed_fields`，写入 `sm:{sessionId}:compress_log`（List） |
| 核心约束不可压缩 | `core_constraints` 字段在任何压缩级别下都完整保留（漂移锚定要求，见 [../../PRD.md#五静默漂移管控体系](../../PRD.md)） |
| 按任务类型分配配额 | L1 简单任务 `max_token=32K`；L2 中等 `64K`；L3 复杂 `128K`（见 [../../PRD.md#三任务规划与编排引擎](../../PRD.md)） |
| 优先保障核心推理 | 压缩顺序：闲聊 → 工具冗余 → 早期对话 → 召回记忆 → 系统提示词（最后压缩） |
| 可回溯 | 压缩前快照保存至 `session.context_summary` 的 `snapshot` 字段，支持回放 |

---

## 7. 核心类设计（Java 类签名）

包名 `com.agentplatform.memory.*`，对应 [../00-overview/tech-stack-and-architecture.md#4-项目目录结构](../00-overview/tech-stack-and-architecture.md) 第 4 节 `agent-memory` 模块。

### 7.1 短期记忆层

```java
package com.agentplatform.memory.shortterm;

public interface ShortTermMemoryStore {
    ShortTermMemory load(String sessionId, int maxRecentTurns, int tokenBudget);
    void appendMessage(String sessionId, MessageBlock message);
    void appendToolHistory(String sessionId, ToolCallRecord record);
    void updateTokenWater(String sessionId, double percent);
    void setRecalledMemories(String sessionId, List<RecalledMemory> memories);
    void clearDraft(String sessionId);
    CompressionResult compress(String sessionId, CompressLevel level);
}

public class RedisShortTermMemoryStore implements ShortTermMemoryStore {
    // 基于 Redis Pipeline 批量读写
    // 依赖：RedisTemplate<String, String>、JsonUtils、TokenCounter
}

public class ShortTermMemory {
    private String systemPrompt;
    private String coreConstraints;
    private String taskGoal;
    private List<MessageBlock> recentMessages;
    private List<ToolCallRecord> toolHistory;
    private List<RecalledMemory> recalled;
    private int tokenUsed;
    private double tokenWaterPercent;
    private CompressLevel compressLevel;
}

public enum CompressLevel { NONE, LIGHT, MEDIUM, HEAVY }
```

### 7.2 长期记忆层

```java
package com.agentplatform.memory.longterm;

public interface LongTermMemoryStore {
    MemoryWriteResult write(WriteLongTermRequest request);
    MemoryWriteResult dedupAndMerge(MemoryCandidate candidate);
    void updateTier(String memoryId, int tier);
    void markInvalid(String memoryId);
    void archive(String memoryId);
    Optional<MemoryRecord> findByMemoryId(String memoryId);
}

public class MilvusLongTermMemoryStore implements LongTermMemoryStore {
    // 依赖：MilvusServiceClient、MemoryLongTermMapper（MyBatis-Plus）
}

public class MemoryRecord {
    private String memoryId;
    private Long tenantId;
    private String userId;
    private Long agentId;
    private String domain;
    private MemoryType memoryType;
    private String content;
    private String summary;
    private List<String> tags;
    private double importanceScore;
    private int tier;
    private String vectorId;
    private String collectionName;
    private boolean distilled;
    private String parentMemoryId;
    private int recallCount;
    private LocalDateTime lastRecallAt;
    private boolean valid;
}

public enum MemoryType { EPISODIC, SEMANTIC, PROCEDURAL }
```

### 7.3 召回层

```java
package com.agentplatform.memory.recall;

public interface MemoryRecaller {
    RecallResponse recall(RecallRequest request);
}

public class MultiPathMemoryRecaller implements MemoryRecaller {
    // 依赖：VectorRecaller、KeywordRecaller、TimeWeightRecaller、TagMatchRecaller、FusionReranker
    public RecallResponse recall(RecallRequest request) {
        // 1. 并行四路召回
        // 2. 候选合并去重
        // 3. 融合重排
        // 4. Token 配额截断
        // 5. 写召回日志 memory_recall_log
    }
}

public interface VectorRecaller {
    List<MemoryCandidate> recall(String query, String collection, String domain, Long tenantId, int topK);
}

public interface KeywordRecaller {
    List<MemoryCandidate> recall(String query, String domain, Long tenantId, int topK);
}

public interface TimeWeightRecaller {
    List<MemoryCandidate> recall(Long agentId, String userId, String domain, MemoryType type, int topK);
}

public interface TagMatchRecaller {
    List<MemoryCandidate> recall(List<String> tags, String domain, Long tenantId, int topK);
}

public class FusionReranker {
    public List<MemoryCandidate> rerank(List<MemoryCandidate> candidates, RerankWeights weights);
}

public class RerankWeights {
    private double wSemantic;
    private double wTime;
    private double wImport;
    private double wTag;
    public static RerankWeights of(MemoryType type);  // 按记忆类型返回权重表
}
```

### 7.4 蒸馏层

```java
package com.agentplatform.memory.distill;

public interface MemoryDistiller {
    DistillResult distill(DistillRequest request);
    DistillResult distillByDomain(String domain, SummaryLevel level);
}

public class DefaultMemoryDistiller implements MemoryDistiller {
    // 依赖：LongTermMemoryStore、ModelGatewayClient、MemoryDistillLogMapper、TokenCounter
}

public enum SummaryLevel { GLOBAL(1), TOPIC(2), DETAIL(3); }

public class DistillRequest {
    private String domain;
    private SummaryLevel summaryLevel;
    private int batchSize;
    private double similarityThreshold;
}

public class DistillResult {
    private String distillTaskId;
    private String targetMemoryId;
    private int sourceCount;
    private int beforeToken;
    private int afterToken;
    private double compressionRatio;  // after/before
    private DistillStatus status;
}
```

### 7.5 压缩层

```java
package com.agentplatform.memory.compress;

public interface TokenWaterMonitor {
    double getCurrentPercent(String sessionId);
    WaterLevel getLevel(double percent);
    void report(String sessionId, int tokenUsed, int tokenBudget);
    boolean shouldCompress(double percent);
}

public class TokenWaterMonitorImpl implements TokenWaterMonitor {
    // 依赖：RedisTemplate、MetricReporter
    // 上报 Prometheus 指标：memory_token_water_percent
}

public enum WaterLevel { SAFE, WARNING, CRITICAL, CIRCUIT_BREAKER }

public interface ContextCompressor {
    CompressionResult compress(String sessionId, CompressLevel level);
    CompressionResult compressLight(String sessionId);
    CompressionResult compressMedium(String sessionId);
    CompressionResult compressHeavy(String sessionId);
}

public class DefaultContextCompressor implements ContextCompressor {
    // 依赖：ShortTermMemoryStore、TokenCounter、CompressLogStore
    // 压缩顺序：闲聊 → 工具冗余 → 早期对话 → 召回记忆 → 系统提示词
}

public class CompressionResult {
    private CompressLevel level;
    private int beforeToken;
    private int afterToken;
    private List<String> compressedFields;
    private String snapshotRef;  // 归档快照引用
}
```

### 7.6 向量化管道

```java
package com.agentplatform.memory.vectorize;

public interface VectorizationPipeline {
    String vectorizeAndStore(VectorizeRequest request);
}

public class DefaultVectorizationPipeline implements VectorizationPipeline {
    // 依赖：ModelGatewayClient、MilvusServiceClient、EmbeddingRouter
}

public class EmbeddingRouter {
    public String route(String domain, MemoryType type);  // 返回模型名
}

public class VectorizeRequest {
    private String content;
    private String memoryId;
    private String domain;
    private MemoryType memoryType;
    private Long tenantId;
    private String userId;
    private Long agentId;
    private double importanceScore;
    private int tier;
}
```

### 7.7 写入管道

```java
package com.agentplatform.memory.pipeline;

public interface MemoryWritePipeline {
    WriteAck process(WriteLongTermRequest request);
}

public class DefaultMemoryWritePipeline implements MemoryWritePipeline {
    // 依赖：InformationExtractor、TagClassifier、ImportanceScorer、VectorizationPipeline、DedupChecker
    public WriteAck process(WriteLongTermRequest request) {
        // ① extract  ② classify  ③ score  ④ vectorize  ⑤ dedupCheck
    }
}

public interface InformationExtractor {
    List<FactCandidate> extract(Object rawEvent);
}
public interface TagClassifier {
    TagResult classify(FactCandidate fact);
}
public interface ImportanceScorer {
    double score(FactCandidate fact, String domain, String sourceType);
}
public interface DedupChecker {
    DedupResult check(float[] vector, String domain, MemoryType type, Long tenantId);
}
```

### 7.8 gRPC 服务实现

```java
package com.agentplatform.memory.grpc;

@GrpcService
public class MemoryGrpcService extends MemoryServiceImplBase {
    // 依赖：ShortTermMemoryStore、MemoryRecaller、MemoryWritePipeline、MemoryDistiller

    @Override
    public void loadShortTerm(LoadShortTermRequest req, StreamObserver<ShortTermMemory> resp);
    @Override
    public void recall(RecallRequest req, StreamObserver<RecallResponse> resp);
    @Override
    public void writeLongTerm(WriteLongTermRequest req, StreamObserver<WriteAck> resp);
    @Override
    public void triggerDistill(DistillRequest req, StreamObserver<DistillAck> resp);
    @Override
    public void getMemoryStatus(MemoryStatusRequest req, StreamObserver<MemoryStatus> resp);
}
```

---

## 8. 与 Agent Runtime 的交互

### 8.1 LoadShortTerm 调用时机

```
Agent Runtime 单步推理循环
   │
   ├─ 1. 接收 subtask.execute 消息
   ├─ 2. 调用 MemoryService.LoadShortTerm  ◀── 加载上下文
   │      请求：sessionId, agentId, maxRecentTurns, tokenBudget
   │      响应：ShortTermMemory（含 system_prompt/core_constraints/recent_msgs/recalled/token_water_percent/compress_level）
   ├─ 3. ReAct think：组装 prompt（含 recalled 记忆）→ model-gateway
   ├─ 4. ReAct act：tool-engine 调用工具
   ├─ 5. ReAct observe：观察结果
   ├─ 6. 调用 MemoryService.Recall  ◀── 主动召回（按需，query=当前思考）
   ├─ 7. 更新短期记忆：appendMessage / appendToolHistory
   ├─ 8. 上报 Token 水位 → TokenWaterMonitor
   │      若 water_percent ≥ 70% → 调用 ContextCompressor.compress
   ├─ 9. 反思（若 Reflexion 模式）
   └─ 10. 步骤完成上报 → task-orchestrator
```

### 8.2 Token 水位上报与压缩触发流程

**上报**：Agent Runtime 每步结束通过 gRPC 上报 `token_used`，`MemoryService` 更新 Redis `sm:{sessionId}:token_water`。

**压缩触发**：

```text
function onTokenReport(sessionId, tokenUsed, tokenBudget):
    percent = tokenUsed / tokenBudget * 100
    redis.set("sm:" + sessionId + ":token_water", percent)
    monitor.report(sessionId, tokenUsed, tokenBudget)  # Prometheus

    if percent >= 95:
        # 熔断，强制重度压缩
        result = compressor.compress(sessionId, HEAVY)
        alert.fire("MEMORY_CIRCUIT_BREAKER", sessionId, percent)
    elif percent >= 85:
        result = compressor.compress(sessionId, MEDIUM)
        alert.fire("MEMORY_CRITICAL", sessionId, percent)
    elif percent >= 70:
        result = compressor.compress(sessionId, LIGHT)

    # 更新 ctx 中的 compress_level
    redis.hset("sm:" + sessionId + ":ctx", "compress_level", result.level)
```

### 8.3 记忆写入流程

任务完成（`task.subtask.done` 事件）触发：

```
Agent Runtime ──▶ MemoryService.WriteLongTerm(gRPC) ──▶ 异步队列 memory.write
                                                          │
                                                          ▼
                                                   MemoryWritePipeline.process
                                                          │
                                                  ┌───────┴───────┐
                                                  ▼               ▼
                                          LongTermMemoryStore   memory_distill_log（触发蒸馏）
```

---

## 9. 蒸馏任务调度

### 9.1 XXL-Job 任务配置

| Job Handler | Cron | 参数 | 说明 |
|---|---|---|---|
| `memoryDistillTopicJob` | `0 0 2 * * ?`（每日 2:00） | `domain`、`batchSize=100` | 主题摘要蒸馏（level=2） |
| `memoryDistillGlobalJob` | `0 0 3 ? * MON`（每周一 3:00） | `domain` | 全局摘要蒸馏（level=1） |
| `memoryArchiveJob` | `0 30 3 * * ?`（每日 3:30） | `ttl_at <= now` | 冷记忆归档 |
| `memoryTierDowngradeJob` | `0 0 4 * * ?`（每日 4:00） | `last_recall_at` 90 天前 | tier 降级（热→温→冷） |
| `memoryTemplateConvertJob` | `0 0 5 * * ?`（每日 5:00） | `recall_count >= 50` | 流程记忆转任务模板 |

### 9.2 按 domain 分组并行

```text
function memoryDistillTopicJob():
    domains = mysql.query("SELECT DISTINCT domain FROM memory_long_term WHERE valid=1 AND distilled=0")
    # 按 domain 并行触发（XXL-Job 分片广播）
    for domain in domains:
        distiller.distillByDomain(domain, SummaryLevel.TOPIC)
```

**XXL-Job 分片广播**：`ShardingUtil.getShardingItem()` 按 `domain.hashCode() % total` 分片，多节点并行执行不同 domain 的蒸馏。

### 9.3 蒸馏前后 Token 对比

每条蒸馏任务记录到 `memory_distill_log`：

| 字段 | 说明 |
|---|---|
| `before_token` | 被蒸馏源记忆的 Token 总和 |
| `after_token` | 生成摘要的 Token 数 |
| `status` | running/success/failed |

**压缩比监控**：`compression_ratio = after_token / before_token`，期望 ≤ 0.1（即压缩到 10% 以下）。

**失败回滚**：蒸馏任务失败时，源记忆的 `distilled` 字段不更新（保持 0），下次任务重试。

---

## 10. 关键流程时序

### 10.1 完整召回注入时序

```
AgentRuntime        MemoryService        Milvus       MySQL       Elasticsearch    Redis
    │                   │                   │            │              │            │
    │ LoadShortTerm ──▶│                   │            │              │            │
    │                   │── 读取 ctx ──────────────────────────────────────────────▶│
    │                   │── 读取 recent_msgs ─────────────────────────────────────▶│
    │                   │── 读取 tool_history ────────────────────────────────────▶│
    │                   │                   │            │              │            │
    │                   │ Recall(query) ◀──│            │              │            │
    │                   │── 向量召回 ──────▶│            │              │            │
    │                   │── 关键词召回 ────────────────────────────▶│              │
    │                   │── 时间/标签召回 ─────────────▶│              │            │
    │                   │◀── 候选合并 ──────│            │              │            │
    │                   │── 融合重排（本地计算）        │              │            │
    │                   │── Top-N 截断（Token 配额）    │              │            │
    │                   │── 写 recall_log ────────────▶│              │            │
    │                   │── 缓存 recalled ───────────────────────────────────────▶│
    │◀── ShortTermMemory│                   │            │              │            │
    │                   │                   │            │              │            │
```

### 10.2 压缩触发时序

```
AgentRuntime        MemoryService        TokenWaterMonitor    ContextCompressor    Redis
    │                   │                      │                     │              │
    │ ReportStep(token)─▶│                      │                     │              │
    │                   │── report(percent) ──▶│                     │              │
    │                   │                      │── getLevel ─────────│              │
    │                   │                      │   percent=72% → WARNING           │
    │                   │── compress(LIGHT) ◀────────────────────────│              │
    │                   │                      │   compress ─────────▶│            │
    │                   │                      │   裁剪工具冗余/闲聊 ─▶│            │
    │                   │                      │   写 compress_log ──────────────▶│
    │                   │                      │   更新 ctx.compress_level ─────▶│
    │◀── ack ──────────│                      │                     │              │
```

---

## 11. 配置项汇总

| 配置 Key | 默认值 | 说明 |
|---|---|---|
| `memory.short-term.ttl` | `2h` | 短期记忆 Redis TTL |
| `memory.short-term.max-recent-turns` | `8` | 最近对话轮数上限 |
| `memory.recall.top-k` | `50` | 单路召回 top_k |
| `memory.recall.final-count` | `10` | 重排后注入上限 |
| `memory.recall.relevance-threshold` | `0.3` | 相关性阈值 |
| `memory.recall.token-ratio` | `0.20` | 召回 Token 占上下文比例上限 |
| `memory.recall.cache-ttl` | `5m` | 同 query 召回结果缓存 |
| `memory.dedup.threshold.semantic` | `0.92` | 语义记忆去重阈值 |
| `memory.dedup.threshold.episodic` | `0.85` | 情景记忆去重阈值 |
| `memory.dedup.threshold.procedural` | `0.88` | 流程记忆去重阈值 |
| `memory.water.safe` | `70` | 安全水位线 |
| `memory.water.warning` | `85` | 预警水位线 |
| `memory.water.critical` | `95` | 临界水位线 |
| `memory.tier.hot-threshold` | `0.7` | 热记忆重要性阈值 |
| `memory.tier.warm-threshold` | `0.4` | 温记忆重要性阈值 |
| `memory.archive.cold-ttl-days` | `365` | 冷记忆 TTL（天） |
| `memory.distill.topic-batch-size` | `100` | 主题蒸馏批量 |
| `memory.distill.topic-min-count` | `20` | 触发主题蒸馏的最少记忆数 |
| `memory.embedding.default-model` | `bge-large-zh-v1.5` | 默认 Embedding 模型 |
| `memory.half-life.episodic` | `30` | 情景记忆时间衰减半衰期（天） |
| `memory.half-life.semantic` | `180` | 语义记忆时间衰减半衰期（天） |
| `memory.half-life.procedural` | `365` | 流程记忆时间衰减半衰期（天） |

---

## 12. 与上下游文档映射

| 本文档章节 | 上游约定 |
|---|---|
| §1.1 短期记忆 Key | [../01-database/database-schema-design.md#34-redis-短期记忆-key-设计](../01-database/database-schema-design.md) |
| §1.2 长期记忆表 | [../01-database/database-schema-design.md#31-memory_long_term-长期记忆元数据表](../01-database/database-schema-design.md) |
| §1.2 Milvus Collection | [../01-database/database-schema-design.md#101-collection-规划](../01-database/database-schema-design.md) |
| §1.2 召回参数 | [../01-database/database-schema-design.md#103-召回参数基线](../01-database/database-schema-design.md) |
| §5.2 蒸馏日志 | [../01-database/database-schema-design.md#32-memory_distill_log-记忆蒸馏日志表](../01-database/database-schema-design.md) |
| §3 召回日志 | [../01-database/database-schema-design.md#33-memory_recall_log-召回日志表](../01-database/database-schema-design.md) |
| §7 核心类包名 | [../00-overview/tech-stack-and-architecture.md#4-项目目录结构](../00-overview/tech-stack-and-architecture.md) |
| §8 gRPC 交互 | [../02-api/api-specification.md#4-记忆管理-apigrpc内部](../02-api/api-specification.md) |
| §6 Token 水位 | [../../PRD.md#3-上下文-token-阈值与压缩规则](../../PRD.md) 第二节(一)·3 |
| §9 XXL-Job | [../00-overview/tech-stack-and-architecture.md#21-技术栈总览](../00-overview/tech-stack-and-architecture.md) 任务调度行 |

---

## 13. 后续计划

本文档为 `memory-service` 工程详设，下一阶段产出：
1. `agent-memory` 模块编码实现计划（按 §7 类设计拆 issue）
2. `infra/sql/03-agent-memory.sql` DDL 脚本
3. `infra/sql/10-milvus-collections.json` 中记忆相关 Collection 初始化
4. XXL-Job 任务配置文件
5. 单元测试与集成测试方案（召回准确率、压缩比、水位告警）
