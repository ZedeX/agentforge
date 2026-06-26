# 多维度检索与代码解析系统 详细设计

> 文档版本：v1.0  |  更新日期：2026-06-26  |  对应 PRD：第二节(四)多维度检索与代码解析系统

## 0. 文档定位与依赖

### 0.1 文档目的

本文档为 Agent 智能体平台「多维度检索与代码解析系统」的工程级详细设计，落地 PRD 第二节(四) 全部要求：代码预处理结构化加工（AST 解析 + 知识图谱 + 多粒度索引）、多维度多路召回机制（向量/全文/结构化 + 融合重排）、代码上下文理解完整链路（召回 → 调用链扩展 → Token 裁剪 → 注入）。本文档只定义类签名、接口契约、Cypher 示例、算法伪代码与配置契约，不包含完整实现。

### 0.2 依赖文档

| 文档 | 关键约束 |
|---|---|
| [00-overview/tech-stack-and-architecture.md](../00-overview/tech-stack-and-architecture.md) | Tree-sitter 0.22、Neo4j 5.18、Milvus 2.4、Elasticsearch 8.13、Java 17、gRPC + Protobuf、包名规范 `com.agentplatform.{module}` |
| [01-database/database-schema-design.md](../01-database/database-schema-design.md) | 第 10 节 Milvus `code_snippet` Collection：维度 768、HNSW + IVF、Partition Key=`language`；第 11 节 Neo4j 节点 Project/Module/File/Class/Function/Interface/Dependency 与关系 CONTAINS/CALLS/IMPLEMENTS/EXTENDS/DEPENDS_ON/IMPORTS |
| [02-api/api-specification.md](../02-api/api-specification.md) | gRPC 风格、`TraceContext` 消息结构、统一响应格式、错误码命名规范 |
| [PRD.md](../../PRD.md) | 第二节(四) 代码预处理结构化加工、多维度多路召回、代码上下文理解完整链路 |

### 0.3 设计原则

1. **三索引并行**：向量索引（Milvus `code_snippet`）、全文索引（Elasticsearch）、结构化索引（Neo4j 图）同步构建，互为补充而非互替
2. **召回-重排分离**：召回阶段追求高 Recall（多路并行），重排阶段追求高 Precision（加权融合）
3. **增量优先**：代码变更触发增量 AST 解析与索引更新，避免全量重建
4. **Token 感知裁剪**：上下文注入前必须按 Token 预算分级裁剪，避免溢出
5. **与平台既有规范对齐**：TraceContext 透传、gRPC 内部通信、统一错误码、统一鉴权拦截

### 0.4 系统在平台中的位置

本系统作为「能力服务层」的代码理解能力，对外暴露 gRPC 接口供 `agent-runtime` 在代码类任务中调用；索引构建侧由独立的索引构建 Worker 异步消费代码仓库变更事件。本系统不直接对接终端用户，所有对外接口仅限平台内部服务调用。

```
┌─────────────────────────────────────────────────────────────────┐
│                    Agent Runtime (agent-runtime)                 │
│                       ReAct 推理循环                              │
└──────────────────────────────┬────────────────────────────────────┘
                               │ gRPC: CodeRetrievalService
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│              代码检索服务 (本系统, 端口 8112)                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ MultiRetriever → [VectorRetriever, KeywordRetriever,     │   │
│  │                   GraphRetriever]                         │   │
│  │ FusionReranker → ContextExpander → 结构化输出            │   │
│  └──────────────────────────────────────────────────────────┘   │
└───┬───────────────┬───────────────┬───────────────┬─────────────┘
    │               │               │               │
    ▼               ▼               ▼               ▼
┌────────┐    ┌──────────┐    ┌──────────┐    ┌──────────────┐
│ Milvus │    │  Elastic │    │  Neo4j   │    │  索引构建     │
│code_   │    │  search  │    │  5.18    │    │  Worker      │
│snippet │    │  8.13    │    │          │    │ (异步消费变更) │
└────────┘    └──────────┘    └──────────┘    └──────┬───────┘
                                                     │
                                                     ▼
                                              ┌──────────────┐
                                              │ 代码仓库变更  │
                                              │ 事件源        │
                                              │ (Git Webhook │
                                              │  / 定时扫描)  │
                                              └──────────────┘
```

### 0.5 名词约定

| 术语 | 含义 |
|---|---|
| 代码单元（Code Unit） | 经 AST 解析得到的函数、类、接口、方法等结构化代码块 |
| 代码片段（Code Snippet） | 写入 Milvus `code_snippet` Collection 的向量化单元，粒度=代码单元 |
| 召回候选（Candidate） | 任一召回通道返回的代码单元 + 通道内得分 |
| 调用链扩展（Context Expansion） | 以召回命中的核心节点为起点，沿 CALLS/IMPLEMENTS 关系向上下游扩展关联代码 |
| Token 预算（Token Budget） | 单次检索注入 Agent 上下文的最大 Token 配额 |

---

## 1. 代码预处理结构化加工

### 1.1 总体加工流水线

代码从仓库变更到完成索引，经过五阶段流水线，全部异步执行，由索引构建 Worker 驱动：

```
代码仓库变更事件
   │
   ▼
[阶段1] 文件获取与变更检测（Git diff + SHA 比对）
   │
   ▼
[阶段2] AST 语法解析（Tree-sitter 多语言统一解析）
   │      → 输出代码单元列表（函数/类/接口/方法 + 元数据）
   ▼
[阶段3] 代码知识图谱构建（AstToGraphBuilder → Neo4j）
   │      → 节点：Project/Module/File/Class/Function/Interface/Dependency
   │      → 关系：CONTAINS/CALLS/IMPLEMENTS/EXTENDS/DEPENDS_ON/IMPORTS
   ▼
[阶段4] 多粒度索引并行构建
   │      → 向量索引（CodeIndexer → Milvus code_snippet, dim=768）
   │      → 全文索引（CodeIndexer → Elasticsearch, 标识符分词优化）
   │      → 结构化索引（阶段3 已落 Neo4j）
   ▼
[阶段5] 一致性校验（计数对账 + 抽样校验）
```

### 1.2 AST 语法解析（Tree-sitter）

#### 1.2.1 多语言统一解析

采用 Tree-sitter 0.22 Java 绑定（`io.github.tree-sitter:javanio`），按语言加载对应 grammar：

| 支持语言 | Tree-sitter Grammar | 代码单元识别规则 |
|---|---|---|
| Java | `tree-sitter-java` | `method_declaration`、`class_declaration`、`interface_declaration`、`constructor_declaration` |
| Python | `tree-sitter-python` | `function_definition`、`class_definition` |
| Go | `tree-sitter-go` | `function_declaration`、`method_declaration`、`type_declaration`（struct/interface） |
| JavaScript | `tree-sitter-javascript` | `function_declaration`、`class_declaration`、`method_definition`、`arrow_function`（仅顶层） |
| TypeScript | `tree-sitter-typescript` | 同 JavaScript + `interface_declaration`、`type_alias_declaration` |

#### 1.2.2 元数据提取规则

对每个识别出的代码单元，提取以下元数据写入 `CodeUnit` 结构：

```java
public record CodeUnit(
    String unitId,              // 业务唯一 ID：{projectId}:{fileSha}:{nodeType}:{startLine}
    String projectId,           // 项目 ID
    String modulePath,          // 模块相对路径
    String filePath,            // 文件相对路径
    String language,            // java/python/go/javascript/typescript
    NodeType nodeType,          // FUNCTION / CLASS / INTERFACE / METHOD
    String name,                // 单元名（函数名/类名/接口名）
    String signature,           // 完整签名（含参数与返回值）
    List<ParamMeta> params,     // 参数列表
    String returnType,          // 返回值类型（无则为 null）
    Set<String> modifiers,      // 修饰符：public/private/static/final/abstract/async...
    int startLine,              // 起始行（1-based）
    int endLine,                // 结束行
    int startColumn,
    int endColumn,
    String rawSource,           // 原始代码文本（用于向量化）
    String docComment,          // 紧邻上方文档注释（JavaDoc/Docstring/JSDoc）
    Set<String> calledSymbols,  // 函数体内调用的符号名（用于 CALLS 关系构建）
    Set<String> importedSymbols // 文件级 import 符号（用于 IMPORTS 关系构建）
) {}

public record ParamMeta(
    String name,
    String type,
    boolean isVarArgs,
    String defaultValue
) {}

public enum NodeType { FUNCTION, METHOD, CLASS, INTERFACE }
```

#### 1.2.3 增量解析机制

**变更检测**：以 Git commit 为粒度，对每个变更文件计算 SHA-256，与 `code_unit_meta` 表（见 1.6）中已记录 SHA 比对，仅对变更文件重新解析。

**增量解析算法**：

```
function incrementalParse(projectId, changedFiles):
    for file in changedFiles:
        oldSha = lookupFileSha(projectId, file.path)
        newSha = computeSha(file.content)
        if oldSha == newSha:
            continue
        # 1. 解析新文件 AST
        newUnits = treeSitterParser.parse(file.content, file.language)
        # 2. 查询旧单元
        oldUnits = loadCodeUnits(projectId, file.path)
        # 3. 计算差集
        added   = newUnits - oldUnits   # 按 unitId 比对
        removed = oldUnits - newUnits
        updated = newUnits ∩ oldUnits 中 rawSource 变化的
        # 4. 上报变更批次
        emitCodeUnitChangeBatch(projectId, file, added, removed, updated)
        # 5. 更新 SHA
        updateFileSha(projectId, file.path, newSha)
```

**Tree-sitter 增量解析优化**：对已解析过的文件，若仅局部变更，调用 `Tree.parse(input, oldTree)` 复用旧 AST 节点，仅重解析变更区间，单文件解析耗时降低 60%~80%。

#### 1.2.4 解析容错

Tree-sitter 为容错解析器，对语法错误文件仍能返回部分 AST。解析失败时记录 `parse_error` 事件，标记该文件为 `degraded`，跳过其代码单元入库但继续处理其他文件，避免单文件故障阻塞整批。

### 1.3 代码知识图谱构建（Neo4j）

#### 1.3.1 节点与属性定义

对齐 [01-database 文档第 11 节](../01-database/database-schema-design.md#11-图库设计neo4j)，所有节点属性如下：

| 节点标签 | 属性 | 类型 | 说明 |
|---|---|---|---|
| `Project` | id, name, language, version, repoUrl, defaultBranch | String | 代码项目 |
| `Module` | id, name, path, projectId | String | Maven 模块/Go package/npm 包 |
| `File` | id, path, language, sha, projectId, modulePath, lineCount | String/Int | 文件 |
| `Class` | id, name, modifiers, startLine, endLine, fileId, docComment | String/Int | 类 |
| `Function` | id, name, signature, returnType, modifiers, startLine, endLine, fileId, docComment, isMethod | String/Int/Bool | 函数/方法 |
| `Interface` | id, name, modifiers, startLine, endLine, fileId, docComment | String/Int | 接口 |
| `Dependency` | id, name, version, scope, ecosystem | String | 第三方依赖（Maven/Go mod/npm） |

**节点 ID 生成规则**：`{projectId}:{label}:{name}:{fileId}:{startLine}`，确保全局唯一且可由源数据重建。

#### 1.3.2 关系与属性定义

| 关系 | from → to | 属性 | 构建来源 |
|---|---|---|---|
| `CONTAINS` | Project → Module / Module → File / File → Class/Function/Interface | - | 文件路径归属 |
| `CALLS` | Function → Function | callSite(行号), line | AST 函数体内调用的符号名匹配 |
| `IMPLEMENTS` | Class → Interface | - | Java `implements`、TS `implements` |
| `EXTENDS` | Class → Class | - | Java `extends`、TS `extends` |
| `DEPENDS_ON` | Module → Dependency | scope=compile/runtime/test | pom.xml/go.mod/package.json |
| `IMPORTS` | File → File | line, symbol | import/require/from 语句 |

**CALLS 关系构建策略**：AST 提取函数体内的调用表达式符号名，先在同文件内匹配方法定义，再跨文件按符号名+签名模糊匹配，未匹配的记入 `unresolved_calls` 表待人工/二次解析修复。跨文件匹配率目标 ≥ 85%。

#### 1.3.3 索引与约束

```cypher
CREATE CONSTRAINT project_id_unique IF NOT EXISTS FOR (n:Project) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT function_id_unique IF NOT EXISTS FOR (n:Function) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT class_id_unique IF NOT EXISTS FOR (n:Class) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT interface_id_unique IF NOT EXISTS FOR (n:Interface) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT file_id_unique IF NOT EXISTS FOR (n:File) REQUIRE n.id IS UNIQUE;

CREATE INDEX project_name IF NOT EXISTS FOR (n:Project) ON (n.name);
CREATE INDEX function_name IF NOT EXISTS FOR (n:Function) ON (n.name);
CREATE INDEX class_name IF NOT EXISTS FOR (n:Class) ON (n.name);
CREATE INDEX file_path IF NOT EXISTS FOR (n:File) ON (n.path);
CREATE INDEX function_file IF NOT EXISTS FOR (n:Function) ON (n.fileId, n.name);
```

#### 1.3.4 批量写入策略

单次提交按文件粒度批量写入，使用 Cypher `UNWIND` 批量创建节点与关系，每批不超过 500 个节点，避免事务过大。示例（节点批量 upsert）：

```cypher
UNWIND $units AS u
MERGE (f:File {id: u.fileId})
  ON CREATE SET f.path = u.filePath, f.language = u.language, f.sha = u.sha, f.projectId = u.projectId
  ON MATCH  SET f.sha = u.sha, f.lineCount = u.lineCount
WITH u, f
WHERE u.nodeType = 'FUNCTION'
MERGE (fn:Function {id: u.unitId})
  ON CREATE SET fn.name = u.name, fn.signature = u.signature, fn.returnType = u.returnType,
               fn.modifiers = u.modifiers, fn.startLine = u.startLine, fn.endLine = u.endLine,
               fn.fileId = u.fileId, fn.docComment = u.docComment, fn.isMethod = u.isMethod
MERGE (f)-[:CONTAINS]->(fn);
```

### 1.4 多粒度索引并行构建

三索引以代码单元为公共输入，并行写入，互不阻塞。

#### 1.4.1 向量索引（Milvus code_snippet）

**Collection Schema**（对齐 [01-database 文档 10.1 节](../01-database/database-schema-design.md#101-collection-规划)）：

```json
{
  "collection_name": "code_snippet",
  "fields": [
    {"name": "vector_id",   "type": "VarChar", "max_length": 64, "is_primary": true},
    {"name": "embedding",   "type": "FloatVector", "dim": 768},
    {"name": "project_id",  "type": "VarChar", "max_length": 64},
    {"name": "language",    "type": "VarChar", "max_length": 32},
    {"name": "file_path",   "type": "VarChar", "max_length": 512},
    {"name": "unit_id",     "type": "VarChar", "max_length": 128},
    {"name": "node_type",   "type": "VarChar", "max_length": 16},
    {"name": "name",        "type": "VarChar", "max_length": 128},
    {"name": "is_method",   "type": "Bool"},
    {"name": "start_line",  "type": "Int32"},
    {"name": "end_line",    "type": "Int32"},
    {"name": "popularity",  "type": "Float"},
    {"name": "last_recall", "type": "Int64"}
  ],
  "index": {
    "field": "embedding",
    "type": "HNSW",
    "params": {"M": 16, "efConstruction": 256}
  },
  "partition_key_field": "language"
}
```

**向量化输入文本构造**（提升 Embedding 语义质量）：

```
{language}\n{node_type}: {name}\n{signature}\n{docComment}\n```\n{rawSource}\n```
```

文档注释与签名前置，使 Embedding 同时捕获语义与签名特征。

**Embedding 模型选择**（详见 2.1.2）。

#### 1.4.2 全文索引（Elasticsearch）

**Index Mapping**（标识符分词优化）：

```json
{
  "settings": {
    "analysis": {
      "analyzer": {
        "code_identifier_analyzer": {
          "tokenizer": "code_identifier_tokenizer",
          "filter": ["lowercase"]
        }
      },
      "tokenizer": {
        "code_identifier_tokenizer": {
          "type": "pattern",
          "pattern": "([A-Z]+[a-z]*|[a-z]+|\\d+|_+)",
          "group": 0
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "unit_id":     {"type": "keyword"},
      "project_id":  {"type": "keyword"},
      "language":    {"type": "keyword"},
      "file_path":   {"type": "keyword"},
      "name": {
        "type": "text",
        "analyzer": "code_identifier_analyzer",
        "fields": {
          "keyword": {"type": "keyword"},
          "boosted": {"type": "text", "analyzer": "code_identifier_analyzer", "boost": 3.0}
        }
      },
      "signature":   {"type": "text", "analyzer": "code_identifier_analyzer"},
      "doc_comment": {"type": "text", "analyzer": "standard"},
      "raw_source":  {"type": "text", "analyzer": "code_identifier_analyzer"},
      "node_type":   {"type": "keyword"},
      "start_line":  {"type": "integer"},
      "popularity":  {"type": "float"},
      "embedding_vec_id": {"type": "keyword"}
    }
  }
}
```

**标识符分词优化说明**：

| 输入 | 分词结果 | 适用场景 |
|---|---|---|
| `getUserOrderList` | `get`, `user`, `order`, `list` | 驼峰拆分 |
| `query_user_by_id` | `query`, `user`, `by`, `id` | 下划线拆分 |
| `parseAST` | `parse`, `ast` | 混合驼峰 |
| `HTTPClient` | `http`, `client` | 连续大写 |

`name.boosted` 子字段加权 3.0，使符号名精确匹配权重高于函数体内容匹配。

#### 1.4.3 结构化索引（Neo4j）

即 1.3 节构建的代码知识图谱本身，无需额外索引。结构化检索通过 Cypher 直接查询。

### 1.5 索引构建并发与一致性

**并行度**：单项目索引构建按文件粒度切分，Worker 池并发度 = `min(可用 CPU 核数, 8)`。三索引写入并行进行，但同一代码单元需先完成 AST 解析（公共前置）。

**幂等保障**：所有写入以 `unitId` / `vector_id` / Neo4j 节点 ID 为主键 upsert，重复消费不会产生脏数据。

**失败隔离**：单文件解析失败不影响其他文件；单索引写入失败（如 Milvus 临时不可用）写入重试队列，不阻塞其他索引。

### 1.6 元数据存储

除 Milvus / ES / Neo4j 外，关系库 `agent_knowledge.code_unit_meta` 存储代码单元关系型元数据，用于一致性校验、增量解析与召回结果回填：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | 主键 |
| `unit_id` | VARCHAR(128) | 代码单元 ID（唯一） |
| `project_id` | VARCHAR(64) | 项目 ID |
| `file_path` | VARCHAR(512) | 文件路径 |
| `file_sha` | VARCHAR(64) | 文件 SHA-256 |
| `language` | VARCHAR(32) | 语言 |
| `node_type` | VARCHAR(16) | FUNCTION/METHOD/CLASS/INTERFACE |
| `name` | VARCHAR(128) | 单元名 |
| `start_line` | INT | 起始行 |
| `end_line` | INT | 结束行 |
| `vector_id` | VARCHAR(64) | Milvus 主键 |
| `es_doc_id` | VARCHAR(64) | ES 文档 ID |
| `neo4j_node_id` | VARCHAR(128) | Neo4j 节点 ID |
| `popularity` | DECIMAL(4,3) | 热度（按召回次数累计） |
| `recall_count` | INT UNSIGNED | 被召回次数 |
| `last_recall_at` | DATETIME(3) | 最近召回时间 |
| 通用字段 | - | 见 [01-database 0.1](../01-database/database-schema-design.md#01-通用字段规范) |

---

## 2. 多维度多路召回机制

### 2.1 召回总览

| 召回通道 | 目标场景 | 适配查询 | 后端 | Top-K |
|---|---|---|---|---|
| 向量检索 VectorRetriever | 模糊功能查找、语义相似 | "查找处理订单退款的代码" | Milvus `code_snippet` | 30 |
| 关键词检索 KeywordRetriever | 精确符号定位、标识符匹配 | "getUserOrderList 函数" | Elasticsearch | 30 |
| 结构化检索 GraphRetriever | 关系类查询、调用链追溯 | "调用 UserService.login 的所有函数" | Neo4j Cypher | 50 |

三路召回并行执行，结果汇总至 `FusionReranker` 统一去重重排。

### 2.2 向量检索（VectorRetriever）

#### 2.2.1 召回流程

```
1. query 文本 → Embedding 模型 → 768 维向量 q
2. 构造 Milvus 过滤表达式（元数据前置过滤）
3. Milvus search(collection=code_snippet, vector=q, top_k=30, expr=filter, params={ef:128})
4. 返回 List<(vector_id, distance, metadata)>
```

#### 2.2.2 元数据前置过滤

支持按 `project_id`、`language`、`file_path` 前缀、`node_type` 过滤，使用 Milvus Partition Key（`language`）+ 标量字段 expr 组合：

```
# 仅检索 Java 项目 user-service 的函数
expr = "project_id == 'proj_user_service' && language == 'java' && node_type in ['FUNCTION', 'METHOD']"
```

**性能考虑**：Partition Key 命中可缩小扫描范围；高基数标量字段（如 `file_path`）只做等值或前缀过滤，避免全表扫描。

#### 2.2.3 Embedding 模型选择

| 模型 | 维度 | 选型理由 | 部署 |
|---|---|---|---|
| `jina-embeddings-v2-code` | 768 | 多语言代码专用，与 `code_snippet` 维度匹配，开源可私有化 | 通过 `model-gateway` 调用 |
| `voyage-code-3` | 1024 | 闭源 SOTA，需降维到 768 后入库 | 备选，私有化优先选 jina |

**统一约束**：所有代码单元向量化必须使用同一模型同一版本，模型变更需触发全量重建索引任务。模型 ID 与版本写入 `code_unit_meta.vector_model` 字段。

### 2.3 关键词/全文检索（KeywordRetriever）

#### 2.3.1 召回流程

```
1. query → 标识符分词（驼峰/下划线拆分）+ 通用分词
2. 构造 ES bool query：
   - name.boosted 字段加权 3.0
   - signature 字段加权 1.5
   - raw_source 字段加权 1.0
   - doc_comment 字段加权 0.5
3. ES search(index=code_snippet, size=30)
4. 返回 List<(es_doc_id, _score, source)>
```

#### 2.3.2 ES 查询构造示例

```json
{
  "size": 30,
  "query": {
    "bool": {
      "filter": [
        {"term": {"project_id": "proj_user_service"}},
        {"term": {"language": "java"}}
      ],
      "should": [
        {"match": {"name.boosted":   {"query": "get user order", "boost": 3.0}}},
        {"match": {"signature":      {"query": "get user order", "boost": 1.5}}},
        {"match": {"raw_source":     {"query": "get user order", "boost": 1.0}}},
        {"match": {"doc_comment":    {"query": "get user order", "boost": 0.5}}}
      ],
      "minimum_should_match": 1
    }
  }
}
```

#### 2.3.3 标识符匹配优化

- 查询端同样使用 `code_identifier_analyzer`，使 `getUserOrderList` 与查询 `get user order` 能匹配
- `name.keyword` 支持精确匹配（term 查询），用于「按完整函数名定位」场景
- 支持 `file_path` 通配符前缀查询（如 `**/service/**`），用于「某目录下查找」

### 2.4 结构化检索（GraphRetriever）

#### 2.4.1 召回流程

```
1. query → 意图识别（调用方识别是「调用链」「依赖」「继承」类查询）
2. 选择对应 Cypher 模板
3. 从 query 中抽取符号名作为参数
4. 执行 Cypher 查询
5. 返回 List<(nodeId, nodeProps, pathMeta)>
```

#### 2.4.2 Cypher 模板示例

**模板 1：调用链上游追溯（谁调用了 X 函数）**

```cypher
// 查询所有直接调用 getUserOrderList 的函数
MATCH (caller:Function)-[r:CALLS]->(callee:Function {name: $functionName})
WHERE caller.projectId = $projectId
RETURN caller.id AS callerId, caller.name AS callerName, caller.fileId AS fileId,
       caller.startLine AS startLine, r.line AS callLine
LIMIT $limit;
```

**模板 2：调用链下游追溯（X 函数调用了哪些函数）**

```cypher
MATCH (caller:Function {name: $functionName, projectId: $projectId})
       -[r:CALLS]->(callee:Function)
RETURN callee.id AS calleeId, callee.name AS calleeName, callee.fileId AS fileId,
       callee.signature AS signature, callee.startLine AS startLine
LIMIT $limit;
```

**模板 3：继承关系筛选（X 接口的所有实现类）**

```cypher
MATCH (c:Class)-[:IMPLEMENTS]->(i:Interface {name: $interfaceName, projectId: $projectId})
RETURN c.id AS classId, c.name AS className, c.fileId AS fileId, c.startLine AS startLine
LIMIT $limit;
```

**模板 4：继承链向上追溯（类 X 的所有父类）**

```cypher
MATCH (c:Class {name: $className, projectId: $projectId})-[:EXTENDS*1..5]->(parent:Class)
RETURN parent.id AS parentId, parent.name AS parentName, parent.fileId AS fileId
LIMIT $limit;
```

**模板 5：模块依赖查询（模块 X 依赖哪些第三方库）**

```cypher
MATCH (m:Module {path: $modulePath, projectId: $projectId})-[:DEPENDS_ON]->(d:Dependency)
RETURN d.name AS depName, d.version AS version, d.scope AS scope, d.ecosystem AS ecosystem;
```

**模板 6：跨文件引用追溯（文件 X 被哪些文件 import）**

```cypher
MATCH (importer:File)-[:IMPORTS]->(target:File {path: $filePath, projectId: $projectId})
RETURN importer.id AS importerId, importer.path AS importerPath
LIMIT $limit;
```

**模板 7：N 跳调用链扩展（核心节点扩展上下文用）**

```cypher
// 以函数 X 为中心，扩展 2 跳 CALLS 关系
MATCH path = (center:Function {id: $centerUnitId})
            -[:CALLS*1..2]-(neighbor:Function)
WHERE ALL (n IN nodes(path) WHERE n.projectId = $projectId)
RETURN DISTINCT neighbor.id AS neighborId, neighbor.name AS neighborName,
       neighbor.fileId AS fileId, neighbor.signature AS signature,
       length(path) AS hops
ORDER BY hops ASC
LIMIT $limit;
```

### 2.5 融合重排层（FusionReranker）

#### 2.5.1 重排流程

```
1. 三路召回结果汇总（每路 Top-K=30/30/50，合计最多 110 个候选）
2. 去重归一化：按 unitId 去重，保留各通道最大原始得分
3. 计算四维子得分（均归一化到 [0,1]）：
   - s_semantic  语义相似度（来自向量检索 cosine 相似度，未命中则按 query 与候选 name 文本相似度兜底）
   - s_symbol    符号匹配度（来自 ES _score 归一化，未命中记 0）
   - s_structure 结构关联度（来自 Neo4j 命中或与已命中节点的图谱距离，未命中记 0）
   - s_popularity 热度（recall_count 归一化，按项目内 z-score 平滑）
4. 加权求和：final_score = 0.4*s_semantic + 0.3*s_symbol + 0.2*s_structure + 0.1*s_popularity
5. 按 final_score 降序，输出 Top-N（默认 N=10，由 caller 指定）
```

#### 2.5.2 加权公式

$$
\text{final\_score}(u) = 0.4 \cdot \hat{s}_{\text{sem}}(u) + 0.3 \cdot \hat{s}_{\text{sym}}(u) + 0.2 \cdot \hat{s}_{\text{str}}(u) + 0.1 \cdot \hat{s}_{\text{pop}}(u)
$$

| 维度 | 权重 | 归一化方式 | 数据来源 |
|---|---|---|---|
| 语义相似度 `s_semantic` | 40% | Milvus 余弦相似度直接归一（cosine ∈ [-1,1] → [0,1]） | VectorRetriever |
| 符号匹配度 `s_symbol` | 30% | ES `_score / max_score_of_batch` | KeywordRetriever |
| 结构关联度 `s_structure` | 20% | 命中图谱记 1.0；未命中但与命中节点 1 跳连通记 0.5；否则 0 | GraphRetriever + 命中节点邻接查询 |
| 热度 `s_popularity` | 10% | `log(1 + recall_count) / log(1 + max_recall_count_in_project)` | code_unit_meta |

#### 2.5.3 归一化处理

每批次召回结果独立归一化，避免跨批次得分不可比：

```
function normalize(scores: List[float]) -> List[float]:
    if scores.isEmpty: return []
    minS = min(scores); maxS = max(scores)
    if maxS == minS: return scores.map(_ => 1.0)
    return scores.map(s => (s - minS) / (maxS - minS))
```

#### 2.5.4 重排输出结构

```java
public record RankedCodeUnit(
    String unitId,
    String projectId,
    String filePath,
    String language,
    NodeType nodeType,
    String name,
    String signature,
    int startLine,
    int endLine,
    String rawSource,
    String docComment,
    double finalScore,
    double sSemantic,
    double sSymbol,
    double sStructure,
    double sPopularity,
    List<String> hitChannels      // 命中的召回通道：[vector, keyword, graph]
) {}
```

---

## 3. 代码上下文理解完整链路

### 3.1 链路总览

```
[步骤1] 需求解析与召回策略选择
   │
   ▼
[步骤2] 初筛召回定位核心代码节点（MultiRetriever + FusionReranker）
   │
   ▼
[步骤3] 调用链上下层关联扩展（ContextExpander，层级可控，默认最多 2 层）
   │
   ▼
[步骤4] Token 感知的分级裁剪（按预算保留高优先级片段）
   │
   ▼
[步骤5] 结构化注入 Agent 上下文（CodeContextBlock 输出）
```

### 3.2 步骤 1：需求解析与召回策略选择

**输入**：Agent 提交的自然语言查询 + 可选元数据（projectId、language、预期 Token 预算）。

**解析逻辑**：

```
function parseQueryAndSelectStrategies(query, hint):
    # 1. 识别查询意图类型
    intent = classifyQueryIntent(query)
    # 意图枚举：SEMANTIC_SEARCH | SYMBOL_LOOKUP | CALL_CHAIN | DEPENDENCY | INHERITANCE | HYBRID

    # 2. 按意图选择召回通道
    strategies = map {
        SEMANTIC_SEARCH -> [vector, keyword],
        SYMBOL_LOOKUP  -> [keyword, graph],
        CALL_CHAIN     -> [graph],
        DEPENDENCY     -> [graph],
        INHERITANCE    -> [graph],
        HYBRID         -> [vector, keyword, graph]
    }.get(intent)

    # 3. 抽取过滤条件（项目、语言、目录）
    filters = extractFilters(query, hint)

    # 4. 返回策略
    return RecallStrategy(intent, strategies, filters, tokenBudget=hint.tokenBudget)
```

**意图分类**：通过轻量模型（model-gateway `scene=intent`，tier=light）或规则匹配实现，规则示例：
- 包含「调用」「caller」「谁调用了」→ `CALL_CHAIN`
- 包含「依赖」「引用了」→ `DEPENDENCY`
- 包含「实现类」「继承」→ `INHERITANCE`
- 包含完整标识符（驼峰/下划线）→ `SYMBOL_LOOKUP`
- 其余 → `SEMANTIC_SEARCH`

### 3.3 步骤 2：初筛召回定位核心节点

调用 `MultiRetrievalService.Recall` 接口（见第 5 节），输入策略 + 查询，输出 Top-N（默认 10）核心代码节点 `RankedCodeUnit` 列表。

**N 的取值策略**：
- 单跳查询：N=5
- 多跳扩展查询：N=10
- 用户提供明确符号：N=3

### 3.4 步骤 3：调用链上下层关联扩展

**目标**：以核心节点为起点，沿 CALLS / IMPLEMENTS 关系向上下游扩展，补充调用方与被调用方代码，使 Agent 理解代码在系统中的位置。

**扩展算法**：

```
function expandContext(coreUnits: List<RankedCodeUnit>, maxHops: int = 2) -> List<ExpandedUnit>:
    expandedSet = Set<ExpandedUnit>()
    queue = PriorityQueue<ExpandedUnit>(按 coreUnit.finalScore 降序)
    for u in coreUnits:
        queue.offer(ExpandedUnit(u, hops=0, relation=CORE))

    while queue.notEmpty() && expandedSet.size < expansionLimit:
        cur = queue.poll()
        if cur.hops >= maxHops: continue
        # 沿 CALLS 向上游扩展（调用方）
        callers = graphRetriever.findCallers(cur.unitId, limit=5)
        for caller in callers:
            if caller.unitId not in expandedSet:
                queue.offer(ExpandedUnit(caller, hops=cur.hops+1, relation=CALLER_OF))
        # 沿 CALLS 向下游扩展（被调用方）
        callees = graphRetriever.findCallees(cur.unitId, limit=5)
        for callee in callees:
            if callee.unitId not in expandedSet:
                queue.offer(ExpandedUnit(callee, hops=cur.hops+1, relation=CALLEE_OF))
        # 沿 IMPLEMENTS 扩展（接口实现）
        if cur.nodeType == INTERFACE:
            impls = graphRetriever.findImplementations(cur.unitId, limit=5)
            for impl in impls:
                queue.offer(ExpandedUnit(impl, hops=cur.hops+1, relation=IMPL_OF))
        expandedSet.add(cur)

    return expandedSet.asList()
```

**层级可控性**：
- `maxHops` 默认 2，最大 4（由 caller 显式指定，防止爆炸）
- 单跳扩展节点数上限 5，总扩展节点数上限 `expansionLimit=30`
- 扩展节点优先级：`CORE(hops=0) > hops=1 > hops=2`；同跳数下按核心节点 finalScore 降序

**扩展节点关系标注**：每个扩展节点携带 `relation` 字段（`CORE` / `CALLER_OF` / `CALLEE_OF` / `IMPL_OF`），供 Agent 理解代码间的依赖方向。

### 3.5 步骤 4：Token 感知的分级裁剪

**目标**：在 Token 预算内最大化信息密度，超出预算时按优先级裁剪。

**Token 计数**：调用 `model-gateway.CountTokens` 接口获取精确 Token 数，避免本地估算误差。

**优先级排序**（高 → 低，裁剪时从低优先级开始丢弃）：

| 优先级 | 内容 | 说明 |
|---|---|---|
| P0 | CORE 节点 rawSource + signature + docComment | 必保留 |
| P1 | CORE 节点的 CALLER_OF（hops=1）signature + name | 仅保留签名，丢弃函数体 |
| P2 | CORE 节点的 CALLEE_OF（hops=1）signature + name | 同上 |
| P3 | hops=2 的扩展节点 signature | 仅保留签名 |
| P4 | 所有扩展节点 docComment | 文档注释 |
| P5 | CORE 节点 rawSource 的尾部代码 | 函数体后段 |

**裁剪算法**：

```
function tokenAwareTrim(units: List<ExpandedUnit>, tokenBudget: int) -> List<CodeContextBlock>:
    blocks = units.map(u -> toContextBlock(u))   # 初始全量
    usedTokens = countTokens(blocks)
    if usedTokens <= tokenBudget:
        return blocks
    # 按优先级从 P5 到 P1 依次裁剪
    for priority in [P5, P4, P3, P2, P1]:
        for block in blocks where block.priority == priority:
            if usedTokens <= tokenBudget: return blocks
            block.trimToSignature()  # 仅保留签名，丢弃 rawSource 与 docComment
            usedTokens = countTokens(blocks)
    # 仍超预算：丢弃低优先级 block
    blocks = blocks.filter(b => b.priority >= P1)
    # 最后兜底：CORE 节点 rawSource 截断
    if countTokens(blocks) > tokenBudget:
        for block in blocks where b.priority == P0:
            block.rawSource = truncateToToken(block.rawSource, tokenBudgetPerBlock)
    return blocks
```

**Token 预算分配建议**：
- 单次检索总预算默认 8K Token（占 128K 上下文窗口的 6%）
- CORE 节点预留 60% 预算
- 扩展节点共享 40% 预算

### 3.6 步骤 5：结构化注入 Agent 上下文

**输出结构**：将裁剪后的代码上下文封装为 `CodeContextBlock` 列表，序列化为 JSON 注入 Agent 的 system/user 消息：

```java
public record CodeContextBlock(
    String unitId,
    String projectId,
    String filePath,
    String language,
    NodeType nodeType,
    String name,
    String signature,            // 必有
    String rawSource,            // 可能为 null（被裁剪）
    String docComment,           // 可能为 null
    String relation,             // CORE / CALLER_OF / CALLEE_OF / IMPL_OF
    int hops,                    // 0=核心节点，1/2=扩展层级
    int startLine,
    int endLine,
    String retrievalReason       // 召回原因，供 Agent 理解为何注入此片段
) {}

public record CodeContextPayload(
    String query,                         // 原始查询
    String intent,                        // 解析的意图
    List<CodeContextBlock> blocks,        // 代码上下文块
    int totalTokenUsed,                   // 实际消耗 Token
    int tokenBudget,                      // 预算
    String trimLevel,                     // none | light | medium | heavy
    Map<String, String> retrievalMeta     // 各通道召回数量等
) {}
```

**注入格式示例**（Agent 收到的 system 消息片段）：

```text
[Code Context]
Query: 查找处理订单退款的代码
Intent: SEMANTIC_SEARCH
Trim Level: light

## Core (3)
### [Java] OrderRefundService.processRefund (File: order-service/src/.../OrderRefundService.java:45-120)
Signature: public RefundResult processRefund(String orderId, String reason)
Doc: 处理订单退款主流程，校验订单状态后调用支付网关反向交易
```java
public RefundResult processRefund(String orderId, String reason) {
    Order order = orderRepo.findById(orderId);
    ...
}
```

## Caller Of Core (2)
### [Java] RefundController.handleRefundRequest (File: ..., line 30)
Signature: public ResponseEntity<RefundResult> handleRefundRequest(@RequestBody RefundRequest req)

## Callee Of Core (3)
... (signatures only)
```

---

## 4. 增量索引与一致性

### 4.1 增量索引更新流程

代码变更事件触发后，增量更新三索引：

```
代码变更事件 (projectId, changedFiles)
   │
   ▼
[1] 增量 AST 解析（见 1.2.3）
   │   输出 added / removed / updated 代码单元批次
   ▼
[2] 增量图谱更新（AstToGraphBuilder → Neo4j）
   │   - 删除 removed 单元对应的节点与关系
   │   - MERGE updated 单元节点
   │   - CREATE added 单元节点与关系
   │   - 重新解析 updated 单元的 CALLS 关系（删除旧 CALLS，重建新 CALLS）
   ▼
[3] 增量向量索引更新（CodeIndexer → Milvus）
   │   - Milvus delete(removed vector_ids)
   │   - Milvus upsert(added/updated 向量)
   ▼
[4] 增量全文索引更新（CodeIndexer → Elasticsearch）
   │   - ES delete by query (removed unit_ids)
   │   - ES bulk index (added/updated 文档)
   ▼
[5] 关系库元数据同步（code_unit_meta 表）
   │   - 删除 removed 记录
   │   - 更新/插入 added/updated 记录
   ▼
[6] 一致性校验
```

### 4.2 一致性校验

**校验时机**：每次增量更新后异步触发；每日凌晨全量对账任务。

**校验项**：

| 校验维度 | 校验方法 | 不一致处置 |
|---|---|---|
| 计数对账 | `code_unit_meta` 总数 = Milvus 实体数 = ES 文档数 = Neo4j Function/Class/Interface 节点数 | 写入 `index_inconsistency` 告警，触发对应索引重建 |
| 抽样校验 | 随机抽 10 个 unitId，对比四源元数据（name/startLine/filePath）一致性 | 不一致标记为 `degraded`，下次增量重建该单元 |
| SHA 一致性 | `code_unit_meta.file_sha` 与实际文件 SHA 比对 | 不一致触发该文件重新解析 |
| 关系完整性 | Neo4j 中 CALLS 关系的 callee 节点必须存在（非悬空） | 悬空 CALLS 删除，记录 `unresolved_calls` |

**校验结果存储**：写入 `agent_knowledge.index_check_log` 表（表结构省略，参考 [01-database 13.2 节](../01-database/database-schema-design.md#132-数据一致性)），含校验时间、校验项、不一致数、修复动作。

### 4.3 全量重建兜底

当增量更新失败率 > 5% 或不一致项 > 100 时，触发全量重建任务：清空项目对应索引数据 → 重新扫描全量代码 → 走完整流水线。全量重建任务通过 XXL-Job 调度，单项目重建预计耗时（见 7.1）。

---

## 5. 核心类设计

### 5.1 包结构

```
com.agentplatform.retrieval
├── parser              # AST 解析
│   ├── CodeParser
│   ├── TreeSitterParser
│   └── CodeUnit
├── graph               # 知识图谱构建
│   ├── AstToGraphBuilder
│   ├── GraphNode
│   └── GraphRelationship
├── index               # 索引构建
│   ├── CodeIndexer
│   ├── MilvusCodeIndexWriter
│   ├── EsCodeIndexWriter
│   └── Neo4jGraphWriter
├── retrieve            # 多路召回
│   ├── MultiRetriever
│   ├── VectorRetriever
│   ├── KeywordRetriever
│   ├── GraphRetriever
│   └── RecallStrategy
├── rerank              # 融合重排
│   ├── FusionReranker
│   └── RankedCodeUnit
├── context             # 上下文扩展与裁剪
│   ├── ContextExpander
│   ├── TokenTrimmer
│   └── CodeContextBlock
├── service             # gRPC 服务入口
│   ├── CodeRetrievalServiceImpl
│   └── CodeIndexBuildServiceImpl
└── config              # 配置
```

### 5.2 解析层类签名

```java
package com.agentplatform.retrieval.parser;

/**
 * 代码解析顶层接口，屏蔽底层 Tree-sitter 实现细节。
 */
public interface CodeParser {

    /**
     * 全量解析单文件。
     * @param projectId 项目 ID
     * @param filePath 文件相对路径
     * @param content 文件文本内容
     * @param language 语言（java/python/go/javascript/typescript）
     * @return 解析出的代码单元列表
     */
    List<CodeUnit> parse(String projectId, String filePath, String content, String language);

    /**
     * 增量解析：基于旧 AST 树重解析变更区间。
     * @param projectId 项目 ID
     * @param filePath 文件路径
     * @param newContent 新文件内容
     * @param oldTree 上一次的 Tree-sitter AST（可为 null 表示首次解析）
     * @param language 语言
     * @return 解析出的代码单元列表
     */
    List<CodeUnit> parseIncremental(String projectId, String filePath,
                                     String newContent, Object oldTree, String language);

    /**
     * 是否支持指定语言。
     */
    boolean supports(String language);
}
```

```java
package com.agentplatform.retrieval.parser;

import io.github.treesitter.javanode.TSParser;
import io.github.treesitter.javanode.TSTree;

/**
 * Tree-sitter 多语言统一解析器实现。
 * 内部维护 language -> TSParser 映射，线程安全。
 */
public class TreeSitterParser implements CodeParser {

    /** 已加载的 grammar 缓存，按 language 名索引 */
    private final Map<String, TSLanguage> languageGrammars;

    /** 上次解析的 AST 树缓存，key = (projectId, filePath)，用于增量解析 */
    private final Cache<String, TSTree> astCache;

    @Override
    public List<CodeUnit> parse(String projectId, String filePath, String content, String language);

    @Override
    public List<CodeUnit> parseIncremental(String projectId, String filePath,
                                           String newContent, Object oldTree, String language);

    @Override
    public boolean supports(String language);

    /**
     * 内部：按 language 选择 grammar，执行 AST 遍历，提取代码单元。
     */
    private List<CodeUnit> extractCodeUnits(TSNode rootNode, String projectId,
                                             String filePath, String content, String language);

    /**
     * 内部：从函数节点提取签名、参数、返回值、修饰符。
     */
    private String extractSignature(TSNode functionNode, String language);
    private List<ParamMeta> extractParams(TSNode functionNode, String language);
    private Set<String> extractModifiers(TSNode unitNode, String language);
    private Set<String> extractCalledSymbols(TSNode functionBodyNode);
    private String extractDocComment(TSNode unitNode, String content);
}
```

### 5.3 图谱构建层类签名

```java
package com.agentplatform.retrieval.graph;

import com.agentplatform.retrieval.parser.CodeUnit;

/**
 * 将 AST 解析结果构建为 Neo4j 图谱节点与关系。
 */
public class AstToGraphBuilder {

    private final Neo4jGraphWriter graphWriter;

    /**
     * 构建单个文件的图谱节点与关系。
     * @param projectId 项目 ID
     * @param fileMeta 文件元数据（路径、语言、SHA）
     * @param codeUnits 该文件的代码单元
     * @param modulePath 模块路径
     * @return 写入的节点 ID 列表
     */
    public List<String> buildFileGraph(String projectId, FileMeta fileMeta,
                                        List<CodeUnit> codeUnits, String modulePath);

    /**
     * 构建跨文件关系（CALLS / IMPLEMENTS / EXTENDS / IMPORTS）。
     * 必须在所有文件节点写入完成后调用。
     * @param projectId 项目 ID
     * @param allUnits 项目全量代码单元（用于跨文件符号匹配）
     */
    public void buildCrossFileRelations(String projectId, List<CodeUnit> allUnits);

    /**
     * 删除文件对应的图谱节点与关系（增量更新时调用）。
     */
    public void removeFileGraph(String projectId, String filePath);

    /**
     * 内部：跨文件 CALLS 关系匹配。
     * @param caller 调用方函数
     * @param calleeName 被调用符号名
     * @param candidateUnits 全项目候选代码单元
     * @return 匹配到的被调用方 unitId，未匹配返回 null
     */
    private String matchCallee(CodeUnit caller, String calleeName, List<CodeUnit> candidateUnits);
}
```

### 5.4 索引构建层类签名

```java
package com.agentplatform.retrieval.index;

import com.agentplatform.retrieval.parser.CodeUnit;
import java.util.List;

/**
 * 代码索引构建器，并行写入向量、全文、图谱三索引。
 */
public class CodeIndexer {

    private final MilvusCodeIndexWriter vectorWriter;
    private final EsCodeIndexWriter esWriter;
    private final Neo4jGraphWriter graphWriter;
    private final EmbeddingClient embeddingClient;   // 调用 model-gateway 获取 Embedding

    /**
     * 全量索引构建。
     * @param projectId 项目 ID
     * @param allUnits 项目全量代码单元（已完成 AST 解析）
     * @return 索引构建结果统计
     */
    public IndexBuildResult buildAll(String projectId, List<CodeUnit> allUnits);

    /**
     * 增量索引更新。
     * @param projectId 项目 ID
     * @param added 新增代码单元
     * @param removed 删除的代码单元 ID 列表
     * @param updated 更新的代码单元（按 unitId 比对后内容变化者）
     */
    public IndexBuildResult buildIncremental(String projectId, List<CodeUnit> added,
                                              List<String> removed, List<CodeUnit> updated);

    /**
     * 内部：并行执行三索引写入。
     */
    private IndexBuildResult parallelIndexWrite(List<CodeUnit> units, WriteMode mode);

    /**
     * 内部：构造向量化输入文本。
     */
    private String buildEmbeddingInput(CodeUnit unit);
}
```

```java
package com.agentplatform.retrieval.index;

/**
 * Milvus code_snippet Collection 写入器。
 */
public class MilvusCodeIndexWriter {

    private static final String COLLECTION = "code_snippet";
    private final MilvusClientV2 milvusClient;

    public void upsert(List<CodeUnitWithVector> units);
    public void delete(List<String> vectorIds);
    public long countByProject(String projectId);
}
```

```java
package com.agentplatform.retrieval.index;

/**
 * Elasticsearch 全文索引写入器。
 */
public class EsCodeIndexWriter {

    private static final String INDEX = "code_snippet";
    private final ElasticsearchClient esClient;

    public void bulkIndex(List<CodeUnitEsDoc> docs);
    public void deleteByUnitIds(List<String> unitIds);
    public long countByProject(String projectId);
}
```

### 5.5 多路召回层类签名

```java
package com.agentplatform.retrieval.retrieve;

import java.util.List;

/**
 * 多路召回协调器，并行调用各召回通道，汇总候选。
 */
public class MultiRetriever {

    private final VectorRetriever vectorRetriever;
    private final KeywordRetriever keywordRetriever;
    private final GraphRetriever graphRetriever;

    /**
     * 多路并行召回。
     * @param request 召回请求（含 query、策略、过滤条件、topK）
     * @return 合并后的候选列表（未去重，未重排）
     */
    public List<Candidate> retrieveMulti(MultiRecallRequest request);

    /**
     * 内部：按策略并行调用。
     */
    private List<Candidate> parallelRetrieve(MultiRecallRequest request);
}
```

```java
package com.agentplatform.retrieval.retrieve;

/**
 * 向量检索通道。
 */
public class VectorRetriever {

    private final MilvusClientV2 milvusClient;
    private final EmbeddingClient embeddingClient;

    /**
     * @param query 自然语言查询
     * @param filters 元数据过滤条件（projectId/language/filePathPrefix/nodeType）
     * @param topK 召回数量，默认 30
     * @return 候选列表
     */
    public List<Candidate> retrieve(String query, MetadataFilter filters, int topK);

    /**
     * 内部：构造 Milvus 标量过滤表达式。
     */
    private String buildFilterExpr(MetadataFilter filters);
}
```

```java
package com.agentplatform.retrieval.retrieve;

/**
 * 关键词/全文检索通道。
 */
public class KeywordRetriever {

    private final ElasticsearchClient esClient;

    public List<Candidate> retrieve(String query, MetadataFilter filters, int topK);

    /**
     * 内部：构造 ES bool query（标识符加权）。
     */
    private BoolQuery buildEsQuery(String query, MetadataFilter filters);
}
```

```java
package com.agentplatform.retrieval.retrieve;

/**
 * 结构化检索通道，基于 Neo4j Cypher。
 */
public class GraphRetriever {

    private final Neo4jDriver neo4jDriver;

    /**
     * 结构化检索入口。
     * @param request 图谱查询请求（含意图类型、参数）
     * @return 候选列表
     */
    public List<Candidate> retrieve(GraphQueryRequest request);

    /**
     * 查询函数 X 的直接调用方。
     */
    public List<CodeUnitMeta> findCallers(String unitId, int limit);

    /**
     * 查询函数 X 直接调用的函数。
     */
    public List<CodeUnitMeta> findCallees(String unitId, int limit);

    /**
     * 查询接口 X 的所有实现类。
     */
    public List<CodeUnitMeta> findImplementations(String unitId, int limit);

    /**
     * 查询类 X 的所有父类（多跳 EXTENDS）。
     */
    public List<CodeUnitMeta> findAncestors(String unitId, int maxHops);

    /**
     * N 跳调用链扩展。
     */
    public List<ExpandedNeighbor> expandNeighbors(String centerUnitId, int maxHops, int limit);

    /**
     * 内部：按意图选择 Cypher 模板并填充参数。
     */
    private String renderCypher(GraphQueryRequest request);
}
```

### 5.6 融合重排层类签名

```java
package com.agentplatform.retrieval.rerank;

import com.agentplatform.retrieval.retrieve.Candidate;
import java.util.List;

/**
 * 融合重排器，对多路召回结果去重归一化后加权打分。
 */
public class FusionReranker {

    /** 子维度权重配置，可由 Nacos 配置中心动态调整 */
    private final RerankWeights weights;

    public FusionReranker(RerankWeights weights) {
        this.weights = weights;
    }

    /**
     * 重排入口。
     * @param candidates 多路召回候选（含命中通道标记）
     * @param topN 输出数量
     * @return 重排后的 Top-N
     */
    public List<RankedCodeUnit> rerank(List<Candidate> candidates, int topN);

    /**
     * 内部：按 unitId 去重，合并通道信息。
     */
    private List<Candidate> dedup(List<Candidate> candidates);

    /**
     * 内部：计算四维子得分。
     */
    private double computeSemantic(Candidate c);
    private double computeSymbol(Candidate c, List<Candidate> batch);
    private double computeStructure(Candidate c, List<Candidate> batch);
    private double computePopularity(Candidate c);

    /**
     * 内部：归一化。
     */
    private List<Double> normalize(List<Double> scores);
}

public record RerankWeights(
    double semantic,    // 0.4
    double symbol,      // 0.3
    double structure,   // 0.2
    double popularity   // 0.1
) {}
```

### 5.7 上下文扩展层类签名

```java
package com.agentplatform.retrieval.context;

import com.agentplatform.retrieval.rerank.RankedCodeUnit;
import java.util.List;

/**
 * 上下文扩展器：以核心节点为起点，沿图谱关系扩展关联代码。
 */
public class ContextExpander {

    private final GraphRetriever graphRetriever;

    /**
     * 扩展上下文。
     * @param coreUnits 召回重排后的核心节点
     * @param maxHops 最大扩展层级（默认 2）
     * @param expansionLimit 扩展节点数上限（默认 30）
     * @return 含核心与扩展节点的完整列表
     */
    public List<ExpandedUnit> expand(List<RankedCodeUnit> coreUnits, int maxHops, int expansionLimit);
}
```

```java
package com.agentplatform.retrieval.context;

/**
 * Token 感知裁剪器：按预算与优先级裁剪上下文块。
 */
public class TokenTrimmer {

    private final ModelGatewayClient modelGatewayClient;   // 调用 CountTokens

    /**
     * 裁剪。
     * @param units 扩展后的代码单元列表
     * @param tokenBudget Token 预算
     * @return 裁剪后的上下文块列表 + 实际消耗 Token
     */
    public TrimResult trim(List<ExpandedUnit> units, int tokenBudget);

    /**
     * 内部：调用 model-gateway.CountTokens。
     */
    private int countTokens(List<CodeContextBlock> blocks);

    /**
     * 内部：按优先级裁剪。
     */
    private List<CodeContextBlock> trimByPriority(List<CodeContextBlock> blocks, int tokenBudget);
}

public record TrimResult(
    List<CodeContextBlock> blocks,
    int totalTokenUsed,
    int tokenBudget,
    String trimLevel    // none | light | medium | heavy
) {}
```

---

## 6. gRPC 接口设计

### 6.1 Protobuf 定义

新增 `agent-proto/src/main/proto/code_retrieval.proto`，风格对齐 [02-api 文档](../02-api/api-specification.md)：

```protobuf
syntax = "proto3";
package agentplatform.retrieval.v1;

option java_package = "com.agentplatform.proto.retrieval.v1";
option java_multiple_files = true;

import "common.proto";

// 代码检索服务（Agent Runtime 调用）
service CodeRetrievalService {
  // 完整代码上下文检索：需求解析 → 召回 → 重排 → 扩展 → 裁剪 → 注入
  rpc RetrieveCodeContext(RetrieveCodeRequest) returns (RetrieveCodeResponse);

  // 仅多路召回 + 重排（不扩展、不裁剪），用于 Agent 自行决定后续处理
  rpc RecallOnly(RecallOnlyRequest) returns (RecallOnlyResponse);

  // 结构化检索（直接执行 Cypher 模板查询）
  rpc GraphQuery(GraphQueryRequest) returns (GraphQueryResponse);

  // 查询代码单元详情（按 unitId）
  rpc GetCodeUnit(GetCodeUnitRequest) returns (CodeUnitDetail);
}

// 代码索引构建服务（索引构建 Worker 调用）
service CodeIndexBuildService {
  // 全量索引构建
  rpc BuildIndex(BuildIndexRequest) returns (BuildIndexResponse);

  // 增量索引更新
  rpc UpdateIndex(UpdateIndexRequest) returns (UpdateIndexResponse);

  // 一致性校验
  rpc CheckConsistency(CheckConsistencyRequest) returns (CheckConsistencyResponse);
}

// ===== 代码检索请求/响应 =====

message RetrieveCodeRequest {
  string query = 1;                       // 自然语言查询
  string project_id = 2;                  // 项目 ID（必填）
  string language = 3;                    // 可选过滤：语言
  string file_path_prefix = 4;            // 可选过滤：文件路径前缀
  string node_type_filter = 5;            // 可选过滤：FUNCTION/METHOD/CLASS/INTERFACE
  int32 token_budget = 6;                 // Token 预算，默认 8192
  int32 max_hops = 7;                     // 扩展层级，默认 2，最大 4
  int32 top_n = 8;                        // 召回重排后核心节点数，默认 10
  bool enable_context_expansion = 9;      // 是否启用调用链扩展，默认 true
  TraceContext trace = 99;
}

message RetrieveCodeResponse {
  string intent = 1;                      // 解析的意图
  repeated CodeContextBlock blocks = 2;    // 代码上下文块（核心+扩展）
  int32 total_token_used = 3;
  int32 token_budget = 4;
  string trim_level = 5;                  // none | light | medium | heavy
  RecallSummary recall_summary = 6;       // 召回元信息
}

message RecallSummary {
  int32 vector_recall_count = 1;
  int32 keyword_recall_count = 2;
  int32 graph_recall_count = 3;
  int32 after_dedup_count = 4;
  int32 after_rerank_count = 5;
  int32 after_expansion_count = 6;
  int32 duration_ms = 7;
}

message CodeContextBlock {
  string unit_id = 1;
  string project_id = 2;
  string file_path = 3;
  string language = 4;
  string node_type = 5;                   // FUNCTION/METHOD/CLASS/INTERFACE
  string name = 6;
  string signature = 7;
  string raw_source = 8;                  // 裁剪后可能为空
  string doc_comment = 9;
  string relation = 10;                   // CORE / CALLER_OF / CALLEE_OF / IMPL_OF
  int32 hops = 11;                        // 0=核心，>0=扩展层级
  int32 start_line = 12;
  int32 end_line = 13;
  string retrieval_reason = 14;
}

// ===== 仅召回请求/响应 =====

message RecallOnlyRequest {
  string query = 1;
  string project_id = 2;
  string language = 3;
  string file_path_prefix = 4;
  string node_type_filter = 5;
  int32 top_k = 6;                        // 每路召回数量，默认 30
  int32 top_n = 7;                        // 重排后输出数量，默认 10
  TraceContext trace = 99;
}

message RecallOnlyResponse {
  repeated RankedCodeUnit units = 1;
  RecallSummary recall_summary = 2;
}

message RankedCodeUnit {
  string unit_id = 1;
  string project_id = 2;
  string file_path = 3;
  string language = 4;
  string node_type = 5;
  string name = 6;
  string signature = 7;
  int32 start_line = 8;
  int32 end_line = 9;
  string raw_source = 10;
  string doc_comment = 11;
  double final_score = 12;
  double s_semantic = 13;
  double s_symbol = 14;
  double s_structure = 15;
  double s_popularity = 16;
  repeated string hit_channels = 17;
}

// ===== 结构化检索请求/响应 =====

message GraphQueryRequest {
  string query_intent = 1;                // CALL_CHAIN_UP | CALL_CHAIN_DOWN | IMPLEMENTATIONS | ANCESTORS | DEPENDENCIES | IMPORTS
  string project_id = 2;
  string symbol_name = 3;                 // 函数名/类名/接口名/模块路径
  int32 max_hops = 4;                     // 用于 CALL_CHAIN 多跳
  int32 limit = 5;                        // 默认 50
  TraceContext trace = 99;
}

message GraphQueryResponse {
  repeated GraphNode nodes = 1;
  repeated GraphEdge edges = 2;
}

message GraphNode {
  string node_id = 1;
  string label = 2;                       // Function/Class/Interface/File/Module/Project/Dependency
  map<string, string> properties = 3;
}

message GraphEdge {
  string from_id = 1;
  string to_id = 2;
  string type = 3;                        // CONTAINS/CALLS/IMPLEMENTS/EXTENDS/DEPENDS_ON/IMPORTS
  map<string, string> properties = 4;
}

// ===== 代码单元详情 =====

message GetCodeUnitRequest {
  string unit_id = 1;
  TraceContext trace = 99;
}

message CodeUnitDetail {
  string unit_id = 1;
  string project_id = 2;
  string file_path = 3;
  string language = 4;
  string node_type = 5;
  string name = 6;
  string signature = 7;
  string raw_source = 8;
  string doc_comment = 9;
  int32 start_line = 10;
  int32 end_line = 11;
  double popularity = 12;
  int32 recall_count = 13;
  int64 last_recall_at = 14;
}

// ===== 索引构建请求/响应 =====

message BuildIndexRequest {
  string project_id = 1;
  string repo_url = 2;                    // Git 仓库地址
  string branch = 3;                      // 分支
  string local_clone_path = 4;            // 已 clone 的本地路径（可选）
  TraceContext trace = 99;
}

message BuildIndexResponse {
  string project_id = 1;
  int32 file_total = 2;
  int32 unit_total = 3;
  int32 vector_indexed = 4;
  int32 es_indexed = 5;
  int32 graph_nodes = 6;
  int32 graph_relations = 7;
  int32 duration_ms = 8;
  string status = 9;                       // success | partial | failed
  repeated string warnings = 10;
}

message UpdateIndexRequest {
  string project_id = 1;
  repeated ChangedFile changed_files = 2;
  TraceContext trace = 99;
}

message ChangedFile {
  string file_path = 1;
  string new_content = 2;                  // 新文件内容（删除则为空）
  string change_type = 3;                  // ADD | MODIFY | DELETE
}

message UpdateIndexResponse {
  string project_id = 1;
  int32 added = 2;
  int32 removed = 3;
  int32 updated = 4;
  int32 duration_ms = 5;
  string status = 6;
  repeated string warnings = 7;
}

message CheckConsistencyRequest {
  string project_id = 1;
  bool full_scan = 2;                      // true=全量对账，false=增量对账
  TraceContext trace = 99;
}

message CheckConsistencyResponse {
  string project_id = 1;
  bool consistent = 2;
  int32 milvus_count = 3;
  int32 es_count = 4;
  int32 neo4j_count = 5;
  int32 meta_count = 6;
  repeated ConsistencyIssue issues = 7;
}

message ConsistencyIssue {
  string unit_id = 1;
  string issue_type = 2;                   // MISSING_IN_MILVUS / MISSING_IN_ES / MISSING_IN_NEO4J / SHA_MISMATCH
  string detail = 3;
}
```

### 6.2 错误码规范

延续 [02-api 文档 0.5 节](../02-api/api-specification.md#05-错误码规范) 错误码命名：

| 错误码 | 触发场景 | 处置建议 |
|---|---|---|
| `CODE_PROJECT_NOT_FOUND` | projectId 不存在 | 检查项目是否已注册 |
| `CODE_INDEX_NOT_READY` | 项目尚未完成首次索引构建 | 提示等待索引构建完成 |
| `CODE_LANGUAGE_UNSUPPORTED` | 语言不在支持列表 | 检查 language 参数 |
| `CODE_EMBEDDING_FAILED` | Embedding 模型调用失败 | 降级到关键词检索 |
| `CODE_RETRIEVE_TIMEOUT` | 召回超时（> 3s） | 降级单通道或返回空 |
| `CODE_INDEX_BUILD_FAILED` | 索引构建失败 | 重试或人工排查 |
| `CODE_GRAPH_QUERY_INVALID` | Cypher 参数非法 | 校验 query_intent 与 symbol_name |
| `CODE_TOKEN_BUDGET_INVALID` | token_budget ≤ 0 或 > 65536 | 使用默认 8192 |

### 6.3 限流与熔断

延续 [02-api 文档 12 节](../02-api/api-specification.md#12-限流与熔断规范)：

| 资源 | 维度 | 阈值 | 行为 |
|---|---|---|---|
| `CodeRetrievalService.RetrieveCodeContext` | tenantId + projectId | 20 QPS | 排队 1s 后拒绝 |
| `CodeIndexBuildService.BuildIndex` | projectId | 1 并发 | 排队，超 5 分钟拒绝 |
| `CodeIndexBuildService.UpdateIndex` | projectId | 5 QPS | 直接拒绝 |

---

## 7. 性能与成本

### 7.1 索引构建耗时基准

基于 PRD 第六节成本测算规则与典型代码仓库规模估算：

| 项目规模 | 文件数 | 代码单元数 | AST 解析 | 图谱构建 | 向量索引 | 全文索引 | 总耗时 |
|---|---|---|---|---|---|---|---|
| 小型 | 500 | ~3K | 30s | 20s | 60s | 15s | ~2min |
| 中型 | 5K | ~30K | 5min | 3min | 8min | 2min | ~18min |
| 大型 | 20K | ~120K | 20min | 12min | 30min | 8min | ~70min |

**Embedding 成本**（按 jina-embeddings-v2-code 私有化部署估算）：单代码单元平均输入 300 Token，1 万单元 Embedding 总耗时约 3 分钟（GPU 加速）或 15 分钟（CPU）。

### 7.2 召回延迟基线

| 阶段 | P50 | P95 | P99 | 备注 |
|---|---|---|---|---|
| Embedding 生成 | 80ms | 150ms | 300ms | 单次 query 向量化 |
| 向量检索 | 30ms | 80ms | 150ms | Milvus HNSW ef=128 |
| 关键词检索 | 20ms | 50ms | 100ms | ES 单分片 |
| 结构化检索 | 40ms | 120ms | 250ms | Neo4j Cypher 2 跳 |
| 融合重排 | 5ms | 15ms | 30ms | 100 候选内 |
| 上下文扩展 | 50ms | 150ms | 300ms | Neo4j 2 跳查询 |
| Token 裁剪 | 30ms | 80ms | 150ms | 含 CountTokens 调用 |
| **端到端** | **255ms** | **645ms** | **1280ms** | 并行召回后汇总 |

**SLA 目标**：`RetrieveCodeContext` P95 ≤ 800ms，超时降级返回仅核心节点（跳过扩展与裁剪）。

### 7.3 Token 裁剪策略

| 任务类型 | 单次检索预算 | CORE 节点数 | 扩展层级 | 典型消耗 |
|---|---|---|---|---|
| 简单符号定位 | 2K | 3 | 0 | ~1.5K |
| 中等功能理解 | 8K | 10 | 2 | ~6K |
| 复杂调用链分析 | 16K | 10 | 4 | ~12K |

**与平台 Token 水位对齐**：参考 [PRD 第二节(一)3](../../PRD.md) 四级水位线，代码检索注入的 Token 占总上下文的比例不超过 10%，避免挤压推理空间。当 Agent 上下文已达临界水位（≥85%）时，强制将代码检索预算压缩至 4K 以下。

### 7.4 成本管控

| 成本维度 | 来源 | 降本手段 |
|---|---|---|
| Embedding 调用 | 索引构建 + 查询向量化 | 私有化部署模型；query Embedding 结果 5 分钟缓存（Redis） |
| Milvus 存储 | code_snippet 向量 + 标量 | 冷项目索引归档；HNSW M=16 平衡精度与内存 |
| Neo4j 存储 | 图谱节点与关系 | 大项目按模块分图（多 database） |
| ES 存储 | 全文索引 | `raw_source` 字段启用 `exclude` 不入 `_source`；按项目分 index |
| Token 注入 | 上下文占用 Agent 窗口 | Token 预算硬约束；分级裁剪优先保签名 |

### 7.5 监控指标

接入平台 Prometheus + Grafana，关键指标：

| 指标名 | 类型 | 说明 |
|---|---|---|
| `code_retrieval_request_total` | Counter | 检索请求总数 |
| `code_retrieval_latency_ms` | Histogram | 端到端召回延迟 |
| `code_retrieval_recall_count` | Histogram | 单次召回候选数 |
| `code_retrieval_token_used` | Histogram | 单次注入 Token 数 |
| `code_retrieval_trim_level` | Counter | 裁剪级别分布 |
| `code_index_build_duration_ms` | Histogram | 索引构建耗时 |
| `code_index_consistency_issue` | Gauge | 不一致项数 |
| `code_embedding_call_latency_ms` | Histogram | Embedding 调用延迟 |

---

## 8. 与平台其他模块的交互

### 8.1 与 Agent Runtime 的交互

`agent-runtime` 在 ReAct 推理循环中遇到代码类查询时，通过 gRPC 调用本系统的 `CodeRetrievalService.RetrieveCodeContext`，将返回的 `CodeContextBlock` 列表注入当前推理上下文。

调用时序：

```
Agent Runtime                       Code Retrieval Service
    │                                       │
    │── RetrieveCodeContext(query, ...) ───▶│
    │                                       │── parseQueryAndSelectStrategies
    │                                       │── MultiRetriever.retrieveMulti (并行)
    │                                       │── FusionReranker.rerank
    │                                       │── ContextExpander.expand
    │                                       │── TokenTrimmer.trim
    │◀──────── RetrieveCodeResponse ────────│
    │                                       │
    │  注入 system message，继续 ReAct
```

### 8.2 与 Model Gateway 的交互

- **Embedding 调用**：`CodeIndexer` 与 `VectorRetriever` 通过 `model-gateway` 获取代码 Embedding，scene=`embedding`，模型固定为 `jina-embeddings-v2-code`
- **Token 计数**：`TokenTrimmer` 调用 `model-gateway.CountTokens` 获取精确 Token 数
- **意图识别**（可选）：`RetrieveCodeRequest` 解析阶段调用 `model-gateway`，scene=`intent`，tier=`light`

### 8.3 与 Knowledge Service 的交互

本系统与 `knowledge-service` 共用 `agent_knowledge` 逻辑库：
- `code_unit_meta` 表位于 `agent_knowledge` 库
- 索引构建日志、一致性校验日志与知识库解析日志共用 `knowledge_*` 表命名前缀区分

### 8.4 与 Observability 的交互

- 所有 gRPC 方法通过 SkyWalking Java Agent 自动埋点
- `TraceContext` 在 gRPC metadata 中透传
- 业务指标暴露于 `/actuator/prometheus` 端点

### 8.5 与 Risk Control 的交互

代码检索为只读操作，无 R3 高危场景，但仍需：
- 鉴权拦截器校验调用方服务身份（仅允许 `agent-runtime` 与索引构建 Worker 调用）
- 项目级 ACL：调用方需持有目标 `projectId` 的读权限
- 全量调用审计落 `audit_log` 表

---

## 9. 部署与配置

### 9.1 服务部署

| 项 | 值 |
|---|---|
| 服务名 | `agent-code-retrieval` |
| 端口 | 8112（gRPC） + 8113（管理 REST） |
| 副本数 | 3（无状态，K8s HPA） |
| 资源限制 | 2C4G × 3 副本（检索） + 4C8G × 2 副本（索引构建 Worker） |
| 镜像 | `agentplatform/code-retrieval:1.0.0` |

### 9.2 关键配置项（Nacos）

```yaml
code-retrieval:
  embedding:
    model: jina-embeddings-v2-code
    dim: 768
    cache-ttl-seconds: 300
  milvus:
    collection: code_snippet
    search-ef: 128
    top-k: 30
  elasticsearch:
    index: code_snippet
    top-k: 30
  neo4j:
    max-hops: 4
    limit: 50
  retrieval:
    default-token-budget: 8192
    default-max-hops: 2
    default-top-n: 10
    expansion-limit: 30
    timeout-ms: 3000
  rerank:
    weights:
      semantic: 0.4
      symbol: 0.3
      structure: 0.2
      popularity: 0.1
  index-build:
    worker-concurrency: 8
    batch-size: 500
    retry-max: 3
```

### 9.3 依赖中间件清单

| 中间件 | 版本 | 用途 | 配置对齐 |
|---|---|---|---|
| Milvus | 2.4.x | code_snippet 向量索引 | [01-database 10.1](../01-database/database-schema-design.md#101-collection-规划) |
| Neo4j | 5.18 | 代码知识图谱 | [01-database 11](../01-database/database-schema-design.md#11-图库设计neo4j) |
| Elasticsearch | 8.13 | 代码全文索引 | 标识符分词器见 1.4.2 |
| MySQL | 8.0.36 | code_unit_meta 元数据 | `agent_knowledge` 库 |
| Redis | 7.2 | Embedding 缓存、限流计数 | 短期缓存 |

---

## 10. 后续演进与未覆盖项

| 项 | 说明 | 计划 |
|---|---|---|
| 跨语言符号解析（如 Java 调用 Python） | 当前 CALLS 关系仅支持同语言匹配 | v1.1 通过类型签名标准化打通 |
| 代码变更实时索引 | 当前为 commit 粒度批量索引 | v1.1 接入 Git Webhook 实时触发 |
| 代码语义聚类 | 同类功能的代码聚类 | v1.2 接入层次聚类 |
| 测试代码索引 | 当前默认排除 test 目录 | v1.1 支持 test 索引开关 |
| 行级变更影响面分析 | 当前仅函数级 | v1.2 细化到行 |
| 多项目跨仓库检索 | 当前单项目隔离 | v1.2 支持跨项目 CALLS 关系构建 |

---

## 11. 文档交叉引用

| 引用点 | 文档 |
|---|---|
| 技术栈版本 | [00-overview/tech-stack-and-architecture.md](../00-overview/tech-stack-and-architecture.md) 第 2.1 节 |
| Milvus code_snippet Collection | [01-database/database-schema-design.md](../01-database/database-schema-design.md) 第 10.1 节 |
| Neo4j 节点与关系 | [01-database/database-schema-design.md](../01-database/database-schema-design.md) 第 11 节 |
| 通用字段规范 | [01-database/database-schema-design.md](../01-database/database-schema-design.md) 第 0.1 节 |
| gRPC 接口规范 | [02-api/api-specification.md](../02-api/api-specification.md) 第 0.1、3.2 节 |
| TraceContext 定义 | [02-api/api-specification.md](../02-api/api-specification.md) 第 3.2 节 |
| 错误码规范 | [02-api/api-specification.md](../02-api/api-specification.md) 第 0.5 节 |
| 限流熔断规范 | [02-api/api-specification.md](../02-api/api-specification.md) 第 12 节 |
| PRD 多维度检索要求 | [PRD.md](../../PRD.md) 第二节(四) |
| Token 水位与压缩 | [PRD.md](../../PRD.md) 第二节(一)3 |
| Agent Runtime 上下文注入 | [06-agent-runtime/agent-runtime-engine.md](../06-agent-runtime/agent-runtime-engine.md)（待补充） |
