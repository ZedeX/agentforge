# PRD: 跨服务写补偿框架 + 异常吞没治理（S-04 / S-12）

> 来源：`docs/audits/red-blue-team-report-2026-07-07.md` §5 稳定性 S-04、健壮性 S-12
> 方法论：[prd](../../../project_memory.md) · [prd-to-plan](../../../project_memory.md) · TDD 红绿循环
> 文档版本：v1.0 | 创建日期：2026-07-07
> 状态：待评审

---

## 1. Executive Summary

### Problem Statement

红蓝对抗审计发现：平台跨服务写操作（`ToolGatewayImpl` 审计落库、`ReActLoopImpl.syncStepState` 裸 RPC）无任何补偿事务机制（无 saga/TCC/本地消息表），任务库 + 记忆库 + 工具审计库三库可能不一致；同时异常被 `catch + log` 吞没的模式普遍存在（`ToolCallAuditorImpl.audit` 内部吞 JPA 异常、`ReActLoopImpl.checkpoint` 吞异常、3 个 `GrpcExceptionAdvice` 通用捕获且其中 2 个无日志），导致故障不可追溯、补偿无法触发。

### Proposed Solution

构建平台级 **本地消息表（Outbox）补偿框架**：在 `agent-common` 提供可复用的 `OutboxMessage` 基础设施（实体 + 仓库 + RocketMQ 可靠投递 Relay + 幂等消费），各跨服务写场景通过本地事务原子写业务数据 + outbox 消息，由 Relay 异步发布到 RocketMQ 实现最终一致；同时对 S-12 全量排查所有异常吞没点，统一 `GrpcExceptionAdvice` 日志规范（强制记录 traceId + 完整堆栈），并将"吞异常"改为"记日志 + 走 outbox 补偿或 rethrow"。

### Success Criteria

1. **S-04 一致性**：在注入审计 DB 不可用故障的混沌测试中，工具执行成功后审计记录最终一致率 = 100%（outbox 重放成功），零审计丢失。
2. **S-04 覆盖面**：审计报告点名的 2 处跨服务写（`ToolGatewayImpl` 审计、`ReActLoopImpl.syncStepState`）100% 接入 outbox；框架可被其他场景复用（至少 1 个扩展场景验证）。
3. **S-12 可追溯率**：全平台 `catch (Exception/Throwable)` 吞没点排查覆盖率 = 100%；`GrpcExceptionAdvice` 3 个实现 100% 增加结构化日志（含 traceId + 堆栈）。
4. **S-12 死代码消除**：`ToolCallAuditorImpl.audit()` 内部 catch 吞异常移除，使 `ToolGatewayImpl` 的 R-11 修复真正生效。
5. **回归**：全部新增/修改模块 JaCoCo line ≥ 80% / branch ≥ 70%；现有测试零回归（`mvn test` 全绿）。

---

## 2. User Experience & Functionality

### User Personas

- **平台开发工程师**：需要可复用的 outbox 基础设施，避免每个跨服务写场景重复造轮子。
- **SRE / 运维工程师**：需要在故障时能从日志和 outbox 表追溯跨服务写的最终状态，定位三库不一致。
- **安全审计员**：需要确认审计记录零丢失（即使审计 DB 短时故障），满足合规要求。

### User Stories

1. 作为平台开发工程师，我想要在 `agent-common` 中有现成的 `OutboxMessage` 实体和 `OutboxRelay`，这样我在任何服务里只需写本地事务就能保证跨服务写最终一致。
2. 作为平台开发工程师，我想要 `ToolGatewayImpl` 审计落库改为 outbox 模式，这样审计 DB 短时故障不会导致工具执行成功但审计丢失。
3. 作为平台开发工程师，我想要 `ReActLoopImpl.syncStepState` 的裸 RPC 改为 outbox + 重试，这样 StepState 服务故障不会中断 ReAct 主循环。
4. 作为 SRE，我想要 outbox 表有状态字段（PENDING/SENT/FAILED/DEAD）和重试计数，这样我能监控积压和死信，手动重放死信消息。
5. 作为 SRE，我想要 outbox Relay 有幂等消费保证（复用现有 `event_consume_log` 模式），这样消息重投不会产生重复副作用。
6. 作为安全审计员，我想要工具审计记录在 DB 故障后能 100% 补写成功，这样合规审计无缺口。
7. 作为平台开发工程师，我想要 `ToolCallAuditorImpl.audit()` 不再内部吞 JPA 异常，这样 R-11 修复能真正生效。
8. 作为平台开发工程师，我想要 `ReActLoopImpl.checkpoint` 失败时走 outbox 补偿而非静默吞掉，这样断点续跑数据不丢失。
9. 作为平台开发工程师，我想要所有 `GrpcExceptionAdvice` 实现统一记录 traceId + 完整堆栈，这样 gRPC 错误可在链路追踪中定位。
10. 作为平台开发工程师，我想要 `agent-task-orchestrator` 的 `GrpcExceptionAdvice` 不再 catch `Throwable`（含 `Error`），这样 OOM 等不应恢复的错误不会被吞。
11. 作为平台开发工程师，我想要一份异常处理 ADR，这样后续新模块遵循统一规范，不再出现吞异常模式。
12. 作为 SRE，我想要 outbox 死信有告警（Prometheus rule），这样积压超阈值能及时发现。

### Acceptance Criteria

**Story 1（Outbox 框架）**
- [ ] `agent-common` 新增 `OutboxMessage` 实体（含 `id / aggregateId / topic / payload(JSON) / status / retryCount / nextRetryAt / createdAt / sentAt` 字段）
- [ ] `agent-common` 新增 `OutboxRepository`（`findPending(int limit)` / `markSent(id)` / `markFailed(id, nextRetryAt)` / `markDead(id)`）
- [ ] `agent-common` 新增 `OutboxRelay`（`@Scheduled` 轮询 PENDING → 发布 RocketMQ → 更新状态），失败指数退避，超过 N 次进死信
- [ ] 框架含 1 个端到端集成测试：注入 DB 故障 → 恢复 → outbox 重放成功 → 消费侧幂等无重复

**Story 2（ToolGatewayImpl 审计 outbox）**
- [ ] `ToolGatewayImpl` Step 10 审计改为写 outbox 表（与工具结果在同一本地事务），不再直接同步写审计 DB
- [ ] 审计 DB 注入故障的测试：工具执行成功 → outbox PENDING → DB 恢复 → Relay 投递 → 审计记录最终写入，100% 一致
- [ ] `ToolCallAuditorImpl.audit()` 内部 catch 吞异常移除，异常向上传播

**Story 3（ReActLoopImpl syncStepState outbox）**
- [ ] `ReActLoopImpl.syncStepState` 裸 RPC 包裹 try/catch + Resilience4j 重试 + outbox 兜底
- [ ] `checkpoint` 失败改为写 outbox（不再 log 吞掉）
- [ ] StepState 服务故障测试：ReAct 主循环不中断，checkpoint 最终一致

**Story 4-6（运维 + 幂等 + 合规）**
- [ ] outbox 表状态字段支持监控查询
- [ ] 消费侧复用 `event_consume_log` 幂等，重复投递无副作用（测试验证）
- [ ] 混沌测试：审计 DB down 30s → 恢复 → outbox 重放 → 审计记录 0 丢失

**Story 7-11（S-12 异常吞没治理）**
- [ ] `ToolCallAuditorImpl.audit()` 内部 catch 移除，异常传播到 `ToolGatewayImpl` 的 R-11 处理
- [ ] `ReActLoopImpl.checkpoint` 的 catch 改为 outbox 补偿
- [ ] `agent-runtime/GrpcExceptionAdvice` 增加 `log.error` 记录 traceId + 堆栈
- [ ] `agent-task-orchestrator/GrpcExceptionAdvice` 增加 `log.error` + catch 范围收窄为 `Exception`（不再 catch `Error`）
- [ ] `agent-tool-engine/GrpcExceptionAdvice` 保持已有日志（已合规）
- [ ] 新增 ADR-006：异常处理规范（吞异常 → rethrow 或 outbox，禁止静默 log）

**Story 12（监控）**
- [ ] Prometheus rule：outbox DEAD 数量 > 0 告警；PENDING 积压 > 阈值告警

### Non-Goals

- **不引入 Seata / Saga 框架**：Outbox 最终一致已满足需求，强一致分布式事务超出范围。
- **不改造现有 Resilience4j 熔断/重试配置**：S-02 已修复，本 PRD 仅在 outbox 内部用重试，不改动全局 Resilience4j。
- **不做 XA/JTA 两阶段提交**：性能代价过高，与 outbox 理念冲突。
- **不做前端控制台**：outbox 监控仅通过 Prometheus + Grafana，不做独立 UI。
- **不改造幻觉治理 / 漂移监控的 stub 服务**：这些是功能完备性差距，不在 S-04/S-12 范围。
- **不改造多租户行级隔离（R-13）**：属安全范畴，不在本 PRD。

---

## 3. AI System Requirements

> 本 PRD 不涉及 AI 模型能力，但涉及 Agent 运行时的状态一致性，属 AI 系统可靠性范畴。

### Tool Requirements

- **RocketMQ 5.3**：作为 outbox 消息的可靠投递通道（已在技术栈中）。
- **MySQL 8.0.36**：outbox 表存储（各服务自有 DB 内）。
- **Redis 7.2**：StepState 实际存储（当前 StepStateSyncerImpl 是内存 stub，本 PRD 不强制实现 Redis 版，但 outbox 兜底需兼容内存版）。
- **Resilience4j**：outbox Relay 内部重试（指数退避）。

### Evaluation Strategy

- **一致性测试**：注入审计 DB 故障（Testcontainers 停容器 30s）→ 恢复 → 断言 outbox 重放后审计记录数 == 工具执行成功数，零丢失。
- **幂等测试**：同一 outbox 消息投递 3 次 → 消费侧 `event_consume_log` 命中 2 次 → 副作用只发生 1 次。
- **吞没点覆盖率**：脚本扫描全平台 `catch\s*\(.*(Exception|Throwable)` 并人工分类，输出吞没点清单，100% 有处置结论（rethrow / outbox / 有意忽略并注释）。
- **回归**：`mvn test` 全模块全绿，现有测试零失败。

---

## 4. Technical Specifications

### Architecture Overview

```
                         ┌─────────────────────────────────────────┐
                         │           agent-common (新增)            │
                         │  OutboxMessage @Entity                   │
                         │  OutboxRepository                        │
                         │  OutboxRelay (@Scheduled poller)         │
                         │  OutboxPublisher (RocketMQ producer)    │
                         └───────────┬─────────────────────────────┘
                                     │ 依赖
                  ┌──────────────────┼──────────────────┐
                  ▼                  ▼                  ▼
      ┌───────────────┐   ┌───────────────┐   ┌───────────────┐
      │ agent-tool-   │   │ agent-runtime │   │ 其他服务      │
      │ engine        │   │               │   │ (扩展场景)    │
      │               │   │               │   │               │
      │ ToolGateway   │   │ ReActLoopImpl │   │  复用 outbox  │
      │ .audit→outbox │   │ .syncStepState│   │               │
      │               │   │  →outbox兜底 │   │               │
      │ ToolCallAudit │   │               │   │               │
      │ orImpl(去吞)  │   │ StepStateSync │   │               │
      └───────┬───────┘   └───────┬───────┘   └───────────────┘
              │                   │
              │ 本地事务写 outbox   │ 本地事务写 outbox
              ▼                   ▼
      ┌──────────────────────────────────────┐
      │  OutboxRelay (@Scheduled 每 5s)       │
      │  findPending(100) → 发 RocketMQ       │
      │  → markSent / markFailed(退避) / dead  │
      └──────────────────┬───────────────────┘
                         ▼
      ┌──────────────────────────────────────┐
      │  RocketMQ Topic                       │
      │  tool.audit / runtime.stepstate / ... │
      └──────────────────┬───────────────────┘
                         ▼
      ┌──────────────────────────────────────┐
      │  消费侧 (复用 event_consume_log 幂等)  │
      │  审计 DB / StepState Redis / ...      │
      └──────────────────────────────────────┘
```

**数据流**：
1. 业务服务在本地 `@Transactional` 内写业务表 + outbox 表（原子）
2. `OutboxRelay` 定时轮询 PENDING 消息，发布到 RocketMQ
3. 消费侧用 `event_consume_log` 幂等表去重，写目标库
4. 发布成功 → markSent；失败 → markFailed + 指数退避；超阈值 → markDead（人工重放）

### Integration Points

- **agent-common → 各服务**：`OutboxMessage` 作为共享 JPA 实体，各服务在自有 DB 建 `outbox_message` 表。
- **agent-tool-engine**：`ToolGatewayImpl` Step 10 改为 `outboxRepository.save(msg)`（同事务）；`ToolCallAuditorImpl.audit()` 移除内部 catch。
- **agent-runtime**：`ReActLoopImpl.syncStepState` 包裹 try/catch + 重试 + outbox 兜底；`checkpoint` 改为 outbox。
- **RocketMQ**：新增 topic `tool.audit`、`runtime.stepstate`（复用现有 RocketMQ 配置）。
- **Prometheus**：新增 outbox 指标（pending_count / dead_count / relay_latency）+ 告警 rule。
- **agent-task-orchestrator / agent-runtime GrpcExceptionAdvice**：增加 `log.error` + traceId；orchestrator 收窄 catch 范围。

### Security & Privacy

- outbox payload 含工具调用参数/结果，可能含 PII → payload 加密存储（复用现有敏感字段加密策略）或字段级脱敏。
- outbox 表访问权限限定服务自身 DB 用户，不跨库直查。
- `GrpcExceptionAdvice` 日志不得打印完整 request payload（可能含 PII），仅记录 traceId + 异常类型 + message。

### Durable Architectural Decisions

- **Outbox 表名**：`outbox_message`（各服务自有 DB）
- **Outbox 状态机**：`PENDING → SENT`（成功）/ `PENDING → FAILED → ... → DEAD`（超 N 次）
- **RocketMQ topic 命名**：`{service}.{event}`，如 `tool.audit`、`runtime.stepstate`
- **幂等键**：复用 `event_consume_log.event_id = outbox_message.id`
- **ADR-006 异常处理规范**：禁止 `catch + log` 静默吞异常；必须 rethrow 或走 outbox 或显式注释"有意忽略"

---

## 5. Risks & Roadmap

### Phased Rollout

- **MVP（Phase 1-2）**：Outbox 框架 + ToolGatewayImpl 审计接入 → 解决最关键的审计丢失风险
- **v1.1（Phase 3-4）**：ReActLoopImpl syncStepState 接入 + S-12 全量吞没点治理 → 闭合两处审计发现
- **v2.0（Phase 5）**：扩展场景验证 + 监控告警 + ADR → 平台级标准化

### Technical Risks

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| outbox Relay 轮询对 DB 有压力 | MED | MED | 批量拉取（limit 100）+ 索引 `(status, next_retry_at)` + 5s 间隔 |
| outbox 表膨胀（DEAD 消息堆积） | LOW | MED | 保留 7 天后归档 + 告警 + 人工重放接口 |
| RocketMQ 投递延迟导致审计"最终一致"窗口过长 | MED | LOW | 监控 relay_latency P95 < 10s；告警超阈值 |
| 移除 ToolCallAuditorImpl 内部 catch 后 JPA 异常上抛改变行为 | MED | MED | TDD 红绿测试覆盖所有审计失败路径，确认 R-11 分支真正触发 |
| GrpcExceptionAdvice 收窄 catch 范围后 OOM 不再被捕获导致 pod 崩溃 | LOW | MED | K8s liveness probe 自动重启；OOM 本就不应被吞 |
| outbox 与现有 REQUIRES_NEW 审计事务语义冲突 | MED | MED | 设计评审：outbox 写入与工具结果同事务（非 REQUIRES_NEW），审计消费侧独立写 |

---

## 附录 A：S-12 吞没点排查清单（初稿，需全量核实）

> 以下为代码研究已发现的吞没点，全量排查阶段需补充。

| # | 文件 | 位置 | 当前行为 | 处置 |
|---|---|---|---|---|
| 1 | `ToolCallAuditorImpl.audit()` | lines 113-122 | catch Exception → log，吞掉 | 移除 catch，异常上抛（R-11 生效） |
| 2 | `ReActLoopImpl.syncStepState` checkpoint | lines 328-331 | catch Exception → log，吞掉 | 改为 outbox 补偿 |
| 3 | `agent-runtime/GrpcExceptionAdvice` | lines 43-60 | catch Throwable → 通用 INTERNAL，无日志 | 增加 log.error(traceId + 堆栈) |
| 4 | `agent-task-orchestrator/GrpcExceptionAdvice` | lines 136/159/194/257 | catch Throwable（含 Error）→ 通用 INTERNAL，无日志 | 增加 log.error + 收窄为 Exception |
| 5 | `agent-tool-engine/GrpcExceptionAdvice` | lines 59-62 | 已有 log.warn | 保持（已合规） |
| ... | 全量排查阶段补充 | ... | ... | ... |

---

## 附录 B：与现有架构的契合点

- **复用 RocketMQ**：outbox 投递复用现有 `rocketmq-common.yml` 配置和 producer。
- **复用 event_consume_log 幂等**：消费侧复用 `SubtaskDoneHandler` 的幂等模式（S-03 修复成果）。
- **复用 Resilience4j**：outbox Relay 内部重试可选用 Resilience4j Retry。
- **不违反 ADR-002**：Agent 运行时无状态，outbox 写入是本地 DB 事务，状态外置。
- **对齐 ADR-005**：工具调用统一走 tool-engine.ToolGateway，审计 outbox 在 tool-engine 内闭环。

---

# 实施计划：跨服务写补偿框架 + 异常吞没治理

> Source PRD: 本文档 §1~§5 + 附录 A/B
> 方法论：[prd-to-plan](../../../project_memory.md) · 垂直切片（tracer bullet）· TDD 红绿循环

## Architectural decisions

适用于所有 Phase 的持久决策：

- **Outbox 表**：`outbox_message`（各服务自有 DB），字段 `id / aggregate_id / topic / payload / status / retry_count / next_retry_at / created_at / sent_at`
- **Outbox 状态机**：`PENDING → SENT`（成功）/ `PENDING → FAILED → ... → DEAD`（retry_count 超阈值 N=5）
- **RocketMQ topic 命名**：`{service}.{event}`，如 `tool.audit`、`runtime.stepstate`
- **幂等键**：消费侧 `event_consume_log.event_id = outbox_message.id`（复用 S-03 模式）
- **ADR-006**：异常处理规范——禁止 `catch + log` 静默吞异常；必须 rethrow 或走 outbox 或显式注释"有意忽略"
- **Outbox 模块归属**：基础设施代码放 `agent-common`，各服务建本地 `outbox_message` 表

---

## Phase 1: Outbox 基础设施（实体 + 仓库 + DDL）

**User stories**: 1

### What to build

在 `agent-common` 提供 Outbox 框架的数据层：`OutboxMessage` JPA 实体（含状态机字段）、`OutboxRepository`（按状态+重试时间查询的接口方法）、以及各服务 DB 的 `outbox_message` DDL 脚本。此 Phase 只交付数据层，但必须可独立测试——repository 的状态查询/转移逻辑用 H2 内存库单元测试验证。

### Acceptance criteria

- [ ] `OutboxMessage` 实体含 8 个核心字段，状态枚举 `PENDING/SENT/FAILED/DEAD`
- [ ] `OutboxRepository` 提供 `findPending(limit)` / `findDead()` / `countByStatus(status)` 查询
- [ ] DDL 脚本含索引 `(status, next_retry_at)` 支撑 Relay 轮询性能
- [ ] 单元测试覆盖：插入 PENDING → 查询命中 → 更新状态 → 查询不再命中
- [ ] TDD 红绿：先写失败测试（Red），再写实现（Green），独立 commit

---

## Phase 2: Outbox Relay + RocketMQ 投递 + 端到端故障验证

**User stories**: 1, 5, 6

### What to build

补全 Outbox 框架的行为层：`OutboxRelay`（`@Scheduled` 每 5s 轮询 PENDING → 发布 RocketMQ → markSent/markFailed）、`OutboxPublisher`（RocketMQ producer）、消费侧幂等消费器（复用 `event_consume_log` 模式）。此 Phase 交付完整垂直切片：schema → relay → MQ → consumer → 故障注入测试。用 Testcontainers 停审计 DB 30s 验证 outbox 重放后零丢失。

### Acceptance criteria

- [ ] `OutboxRelay` 批量拉取 PENDING（limit 100）→ 发 MQ → 更新状态
- [ ] 失败时指数退避（`next_retry_at = now + 2^retry * delay`），超 5 次 → markDead
- [ ] 消费侧幂等：同一消息投递 3 次，副作用仅发生 1 次（`event_consume_log` 去重）
- [ ] 端到端测试：注入 DB 故障（停容器 30s）→ 恢复 → outbox 重放 → 审计记录 0 丢失
- [ ] TDD 红绿循环，Relay 调度逻辑与故障路径独立测试

---

## Phase 3: ToolGatewayImpl 审计接入 Outbox + ToolCallAuditorImpl 去吞

**User stories**: 2, 7

### What to build

将 `agent-tool-engine` 的工具审计从"同步写审计 DB"改为"本地事务写 outbox"，使审计 DB 短时故障不再丢审计记录。同时移除 `ToolCallAuditorImpl.audit()` 内部 catch 吞 JPA 异常的代码，使 Phase 2 的 R-11 修复分支真正触发。此 Phase 是 S-04 的第一个真实业务接入，验证框架可复用性。

### Acceptance criteria

- [ ] `ToolGatewayImpl` Step 10 审计改为 `outboxRepository.save(msg)`（与工具结果同事务）
- [ ] `ToolCallAuditorImpl.audit()` 内部 `catch (Exception e) { log.error(...) }` 移除，异常上抛
- [ ] 混沌测试：工具执行成功 + 审计 DB down → outbox PENDING → DB 恢复 → 审计最终一致
- [ ] 测试验证 R-11 分支：审计失败时 `ToolGatewayImpl` 抛 `ToolEngineException("AUDIT_FAILURE")` 真正触发（而非被内部 catch 吞掉）
- [ ] 现有 `ToolGatewayImpl` 测试零回归

---

## Phase 4: ReActLoopImpl syncStepState 接入 Outbox + checkpoint 补偿

**User stories**: 3, 8

### What to build

将 `agent-runtime` 的 `ReActLoopImpl.syncStepState` 裸 RPC 改为 try/catch + Resilience4j 重试 + outbox 兜底，使 StepState 服务故障不中断 ReAct 主循环。`checkpoint` 失败从"log 吞掉"改为写 outbox 补偿，断点续跑数据不丢失。此 Phase 是 S-04 的第二个接入场景，与 S-12 在 `ReActLoopImpl.checkpoint` 处重叠合并处理。

### Acceptance criteria

- [ ] `syncStepState` 裸 RPC 包裹 try/catch + 重试（3 次指数退避），失败转 outbox 兜底
- [ ] `checkpoint` catch 块从 `log.error` 吞掉改为 `outboxRepository.save` 补偿
- [ ] 测试：StepState 服务故障 → ReAct 主循环不中断 → 故障恢复 → checkpoint 最终一致
- [ ] 测试：checkpoint 序列化失败 → outbox 写入 → 恢复后可续跑
- [ ] 现有 `ReActLoopImpl` / `RuntimeApplicationTest` 零回归

---

## Phase 5: S-12 全量吞没点治理 + GrpcExceptionAdvice 统一 + ADR-006

**User stories**: 9, 10, 11

### What to build

全量排查全平台 `catch (Exception/Throwable)` 点，分类处置（rethrow / outbox / 有意忽略并注释）。统一 3 个 `GrpcExceptionAdvice` 实现：agent-runtime 和 agent-task-orchestrator 增加 `log.error`（traceId + 堆栈），agent-task-orchestrator catch 范围从 `Throwable` 收窄为 `Exception`（不再吞 `Error`）。产出 ADR-006 异常处理规范。

### Acceptance criteria

- [ ] 脚本扫描全平台 `catch\s*\(.*(Exception|Throwable)`，输出吞没点清单（预期 > 20 处）
- [ ] 每处吞没点有处置结论：rethrow / outbox / 显式注释"有意忽略 + 原因"
- [ ] `agent-runtime/GrpcExceptionAdvice` 增加 `log.error`（含 traceId + 完整堆栈）
- [ ] `agent-task-orchestrator/GrpcExceptionAdvice` 增加 `log.error` + catch 收窄为 `Exception`
- [ ] `agent-tool-engine/GrpcExceptionAdvice` 保持现状（已合规）
- [ ] ADR-006 异常处理规范文档产出，含正例/反例
- [ ] 修改的模块现有测试零回归

---

## Phase 6: 监控告警 + 扩展场景验证 + 文档同步

**User stories**: 4, 12

### What to build

为 outbox 框架补全可观测性：Prometheus 指标（pending_count / dead_count / relay_latency）+ 告警 rule（DEAD > 0 / PENDING 积压超阈值）。选 1 个扩展场景（如 agent-memory 写长期记忆的跨服务写）接入 outbox 验证框架可复用性。同步 `project_memory.md` 和 `00-coding-plans-overview.md`。

### Acceptance criteria

- [ ] `OutboxRelay` 暴露 Micrometer 指标：`outbox_pending_count` / `outbox_dead_count` / `outbox_relay_latency_seconds`
- [ ] Prometheus alert rule：`outbox_dead_count > 0` 告警；`outbox_pending_count > 1000` 积压告警
- [ ] 扩展场景（agent-memory 或其他）接入 outbox，端到端测试通过
- [ ] `project_memory.md` 更新 S-04/S-12 规划文档章节
- [ ] `00-coding-plans-overview.md` 新增 Plan 10 条目
- [ ] 全量 `mvn test` 全绿，JaCoCo line ≥ 80% / branch ≥ 70%
