# AgentForge 智能体平台 TDD 测试计划

> 文档版本：v1.0 | 更新日期：2026-06-27 | 文档定位：**测试策略 + 测试矩阵 + 决策路径用例 + 端到端场景**
>
> 适用范围：AgentForge 企业级多智能体平台后端全量核心服务（11 个核心微服务 + 2 个横向服务 = 13 个服务）的测试驱动开发（TDD）计划。
>
> 依赖文档：
> - [PRD.md](../../PRD.md) — 整体需求与设计原则
> - [detail-MRD.md](../../detail-MRD.md) — 详细需求与执行链路
> - [00-overview/tech-stack-and-architecture.md](../00-overview/tech-stack-and-architecture.md) — 微服务清单、ADR、通信矩阵
> - [01-database/database-schema-design.md](../01-database/database-schema-design.md) — 9 MySQL 库 32 表 + Milvus + Neo4j + ClickHouse
> - [02-api/api-specification.md](../02-api/api-specification.md) — REST/gRPC/SSE/RocketMQ 接口契约与错误码
> - [03-task-engine/task-orchestration-and-planning.md](../03-task-engine/task-orchestration-and-planning.md) — DAG 引擎 + 复杂度识别 + 重规划
> - [04-memory/memory-system-design.md](../04-memory/memory-system-design.md) — 三级记忆 + 多路召回 + 蒸馏
> - [05-tool-engine/tool-and-invocation-system.md](../05-tool-engine/tool-and-invocation-system.md) — R1/R2/R3 + 沙箱 + 配额
> - [06-agent-runtime/agent-runtime-engine.md](../06-agent-runtime/agent-runtime-engine.md) — ReAct + Reflexion + Token 水位
> - [08-flow/state-machines-and-sequences.md](../08-flow/state-machines-and-sequences.md) — 10 状态 + 12 时序图
> - [09-governance-and-deployment/governance-and-middleware.md](../09-governance-and-deployment/governance-and-middleware.md) — 幻觉六层 + 漂移四层 + 11 中间件
> - [11-detail-flow/01-access-and-planning-flow.md](../11-detail-flow/01-access-and-planning-flow.md) — F1~F4 决策流程图（30 决策节点）
> - [11-detail-flow/02-runtime-and-replan-flow.md](../11-detail-flow/02-runtime-and-replan-flow.md) — F5~F8 决策流程图（33 决策节点）
> - [11-detail-flow/03-quality-and-memory-flow.md](../11-detail-flow/03-quality-and-memory-flow.md) — F9~F12 决策流程图（36 决策节点）
> - [10-supplement/detail-mrd-gap-fill.md](../10-supplement/detail-mrd-gap-fill.md) — 3 个端到端业务示例

---

## 目录

- [0. 文档导览](#0-文档导览)
- [1. 测试策略（基于 TDD 方法论）](#1-测试策略基于-tdd-方法论)
  - [1.1 测试金字塔与覆盖比例](#11-测试金字塔与覆盖比例)
  - [1.2 TDD 红绿循环工作流](#12-tdd-红绿循环工作流)
  - [1.3 测试工具栈锁定](#13-测试工具栈锁定)
  - [1.4 覆盖率目标与度量](#14-覆盖率目标与度量)
  - [1.5 测试环境分层](#15-测试环境分层)
- [2. 测试分类与范围](#2-测试分类与范围)
  - [2.1 单元测试（Unit Test，70%）](#21-单元测试unit-test70)
  - [2.2 功能测试（Functional / Contract Test，含 gRPC 契约）](#22-功能测试functional--contract-test含-grpc-契约)
  - [2.3 集成测试（Integration Test，20%）](#23-集成测试integration-test20)
  - [2.4 用户流程测试（End-to-End，10%）](#24-用户流程测试end-to-end10)
  - [2.5 回归测试（Regression，基于 Badcase 表）](#25-回归测试regression基于-badcase-表)
  - [2.6 性能测试（Performance Baseline）](#26-性能测试performance-baseline)
- [3. 各微服务测试矩阵（13 个服务，按 P0-P3 优先级）](#3-各微服务测试矩阵13-个服务按-p0-p3-优先级)
  - [3.1 优先级定义](#31-优先级定义)
  - [3.2 P0 核心服务测试矩阵](#32-p0-核心服务测试矩阵)
  - [3.3 P1 重要服务测试矩阵](#33-p1-重要服务测试矩阵)
  - [3.4 P2 支撑服务测试矩阵](#34-p2-支撑服务测试矩阵)
  - [3.5 P3 基础服务测试矩阵](#35-p3-基础服务测试矩阵)
- [4. 关键测试场景（基于 F1-F12 决策流程图）](#4-关键测试场景基于-f1-f12-决策流程图)
  - [4.1 F1 接入网关请求处理流程（6 决策节点 → 12 测试用例）](#41-f1-接入网关请求处理流程6-决策节点--12-测试用例)
  - [4.2 F2 意图识别与复杂度判定（7 决策节点 → 14 测试用例）](#42-f2-意图识别与复杂度判定7-决策节点--14-测试用例)
  - [4.3 F3 任务规划与 DAG 生成（9 决策节点 → 18 测试用例）](#43-f3-任务规划与-dag-生成9-决策节点--18-测试用例)
  - [4.4 F4 子任务分发与并行调度（8 决策节点 → 16 测试用例）](#44-f4-子任务分发与并行调度8-决策节点--16-测试用例)
  - [4.5 F5 动态重规划决策（7 决策节点 → 14 测试用例）](#45-f5-动态重规划决策7-决策节点--14-测试用例)
  - [4.6 F6 ReAct 循环详细决策（8 决策节点 → 16 测试用例）](#46-f6-react-循环详细决策8-决策节点--16-测试用例)
  - [4.7 F7 Token 水位压缩决策（5 决策节点 → 10 测试用例）](#47-f7-token-水位压缩决策5-决策节点--10-测试用例)
  - [4.8 F8 工具选择与调用决策（13 决策节点 → 26 测试用例）](#48-f8-工具选择与调用决策13-决策节点--26-测试用例)
  - [4.9 F9 三级质量校验决策（6 决策节点 → 12 测试用例）](#49-f9-三级质量校验决策6-决策节点--12-测试用例)
  - [4.10 F10 幻觉治理六层联动（12 决策节点 → 24 测试用例）](#410-f10-幻觉治理六层联动12-决策节点--24-测试用例)
  - [4.11 F11 漂移监测与纠偏决策（9 决策节点 → 18 测试用例）](#411-f11-漂移监测与纠偏决策9-决策节点--18-测试用例)
  - [4.12 F12 长期记忆写入与召回（9 决策节点 → 18 测试用例）](#412-f12-长期记忆写入与召回9-决策节点--18-测试用例)
- [5. 端到端业务场景测试（3 个示例）](#5-端到端业务场景测试3-个示例)
  - [5.1 场景一：行业调研 Agent（L3 多 Agent DAG + RAG + 事实校验）](#51-场景一行业调研-agentl3-多-agent-dag--rag--事实校验)
  - [5.2 场景二：代码生成 Agent（AST 解析 + 多轮 ReAct + Reflexion）](#52-场景二代码生成-agentast-解析--多轮-react--reflexion)
  - [5.3 场景三：数据分析 Agent（SQL 工具 + 结果校验 + 重规划）](#53-场景三数据分析-agentsql-工具--结果校验--重规划)
- [6. 测试数据管理](#6-测试数据管理)
  - [6.1 Testcontainers 容器矩阵](#61-testcontainers-容器矩阵)
  - [6.2 DDL 初始化与种子数据](#62-ddl-初始化与种子数据)
  - [6.3 测试 Fixture 工厂](#63-测试-fixture-工厂)
  - [6.4 数据隔离与清理策略](#64-数据隔离与清理策略)
- [7. CI/CD 集成](#7-cicd-集成)
  - [7.1 GitHub Actions 工作流](#71-github-actions-工作流)
  - [7.2 测试报告与覆盖率](#72-测试报告与覆盖率)
  - [7.3 失败重试与不稳定测试治理](#73-失败重试与不稳定测试治理)
  - [7.4 质量门禁](#74-质量门禁)
- [8. 验收标准](#8-验收标准)
  - [8.1 单元测试验收](#81-单元测试验收)
  - [8.2 集成测试验收](#82-集成测试验收)
  - [8.3 端到端测试验收](#83-端到端测试验收)
  - [8.4 性能基线验收](#84-性能基线验收)
  - [8.5 治理与合规验收](#85-治理与合规验收)
- [9. 附录](#9-附录)
  - [9.1 命名规范](#91-命名规范)
  - [9.2 错误码与决策节点交叉索引](#92-错误码与决策节点交叉索引)
  - [9.3 测试用例统计汇总](#93-测试用例统计汇总)
  - [9.4 变更记录](#94-变更记录)

---

## 0. 文档导览

### 0.1 文档定位

本测试计划是 AgentForge 项目编码阶段的前置交付物，遵循 TDD（Test-Driven Development）方法论，对 13 个微服务、12 张决策流程图（F1~F12 共 103 个决策节点）、3 个端到端业务场景进行**测试维度全覆盖**。

文档回答以下四个核心问题：

1. **测什么** — 基于设计文档与 F1~F12 决策节点，列出每个微服务的测试类与测试方法命名（按 `Should_Shape_When_Condition` 格式）
2. **怎么测** — TDD 红绿循环 + Testcontainers + Mock 策略，明确哪些依赖 mock、哪些用真实组件
3. **测到什么程度** — 单元 70% / 集成 20% / E2E 10%；核心模块覆盖率 ≥ 80%，整体 ≥ 70%
4. **何时通过** — 8 项验收标准（覆盖率、契约一致性、性能基线、漂移阈值等）

### 0.2 与已有文档的关系

| 本测试计划章节 | 关联设计文档 | 关系说明 |
|---|---|---|
| §1 测试策略 | [00-overview §2 技术栈](../00-overview/tech-stack-and-architecture.md) | 工具栈与设计文档对齐 |
| §2 测试分类 | [02-api §0 通用规范](../02-api/api-specification.md) | 错误码与契约对齐 |
| §3 微服务矩阵 | [00-overview §3.1 微服务清单](../00-overview/tech-stack-and-architecture.md) | 13 服务一一对应 |
| §4 F1~F12 用例 | [11-detail-flow F1~F12](../11-detail-flow/01-access-and-planning-flow.md) | 103 决策节点全覆盖 |
| §5 端到端场景 | [10-supplement §5 业务示例](../10-supplement/detail-mrd-gap-fill.md) | 3 个示例场景 |
| §6 测试数据 | [01-database + infra/sql](../../infra/sql) | DDL + 种子数据复用 |
| §7 CI/CD | [09-governance §13 部署](../09-governance-and-deployment/governance-and-middleware.md) | 与现有 CI 集成 |
| §8 验收标准 | [00-overview §1.2 非功能指标](../00-overview/tech-stack-and-architecture.md) | SLA 与基线对齐 |

### 0.3 术语约定

| 术语 | 含义 |
|---|---|
| TDD | Test-Driven Development，测试驱动开发 |
| SUT | System Under Test，被测系统 |
| SUT 类 | 被测类的简写，如 `ReActLoop` 即 SUT |
| Mock | 用 Mockito 模拟依赖组件 |
| Stub | 桩件，返回固定响应 |
| Fake | 假实现，如 `InMemoryMemoryStore` |
| Testcontainers | 基于 Docker 的测试基础设施容器 |
| Fixture | 测试夹具，提供可复用测试数据构造器 |
| 契约测试 | Contract Test，验证服务间接口契约 |
| F1~F12 | 12 张决策流程图，定义见 [11-detail-flow](../11-detail-flow/01-access-and-planning-flow.md) |
| D1, D2, ... | 决策节点 ID，对应流程图中的菱形判断节点 |
| P0~P3 | 测试优先级，P0 最高（阻塞性），P3 最低（非关键） |

---

## 1. 测试策略（基于 TDD 方法论）

### 1.1 测试金字塔与覆盖比例

AgentForge 采用经典测试金字塔模型，按比例分配测试投入：

```
                    ▲
                   ╱ ╲          端到端测试（E2E）10%
                  ╱   ╲         ─ 3 个业务场景，验证全链路
                 ╱     ╲
                ╱       ╲      集成测试（Integration）20%
               ╱         ╲     ─ 跨模块集成（如 gateway→session→orchestrator）
              ╱           ╲    ─ Testcontainers 启真实中间件
             ╱             ╲
            ╱               ╲   单元测试（Unit）70%
           ╱_________________╲  ─ 每个类的公共方法行为测试
                                  ─ Mock 依赖，纯逻辑验证
```

**比例说明**：

| 测试层 | 占比 | 执行时间 | 数量预估 | 主要工具 |
|---|---|---|---|---|
| 单元测试 | 70% | < 30s | ~1500 个 | JUnit 5 + Mockito 5 + AssertJ 3 |
| 集成测试 | 20% | 2~5min | ~300 个 | Spring Boot Test + Testcontainers |
| 端到端测试 | 10% | 5~15min | ~30 个 | Spring Boot Test + Testcontainers 全栈 |
| **总计** | 100% | < 20min | ~1830 个 | — |

### 1.2 TDD 红绿循环工作流

每个新功能/行为必须按红绿循环开发，禁止先写实现再补测试：

```
┌─────────────────────────────────────────────────────────────┐
│  ① Red：写一个失败的测试                                       │
│  - 在 {module}/src/test/java/ 下新建 {ClassUnderTest}Test    │
│  - 测试方法命名：Should_{期望行为}_When_{前置条件}              │
│  - 运行测试，确认失败（编译失败或断言失败）                    │
│  - 失败原因必须明确：缺方法 / 错误返回值 / 异常未抛出          │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  ② Green：写最小实现让测试通过                                │
│  - 仅写让该测试通过的最少代码，不过度设计                      │
│  - 允许 hard-code 返回值（后续重构修正）                       │
│  - 运行测试，确认全绿                                         │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  ③ Refactor：重构优化                                         │
│  - 消除重复代码、提取公共方法、改善命名                        │
│  - 测试必须保持全绿                                           │
│  - 提交一次 commit，message 遵循 conventional commits         │
└─────────────────────────────────────────────────────────────┘
```

**TDD 三定律**（Uncle Bob）：
1. 除非有失败的测试，否则不允许写产品代码
2. 测试失败时，仅写刚好让测试通过的产品代码
3. 仅写刚好让当前测试失败的测试代码（一次一个行为）

**红绿循环节奏**：
- 单个循环 ≤ 5 分钟，超过则说明测试范围过大
- 每个行为至少覆盖：正常路径（happy path）+ 1 个边界 + 1 个异常
- 关键决策路径（F1~F12）必须覆盖 true/false 双分支

### 1.3 测试工具栈锁定

| 工具 | 版本 | 用途 | 引入位置 |
|---|---|---|---|
| JUnit 5 | 5.10.2 | 测试框架，提供 `@Test`/`@ParameterizedTest`/`@DisplayName` | `pom.xml` 父 POM |
| Mockito 5 | 5.11.0 | Mock 依赖，`@Mock`/`@InjectMocks`/`when().thenReturn()` | 各服务 `pom.xml` |
| AssertJ 3 | 3.25.3 | 流式断言，`assertThat(actual).isEqualTo(expected)` | 各服务 `pom.xml` |
| Spring Boot Test | 3.2.4 | `@SpringBootTest`/`@WebMvcTest`/`@DataJpaTest` 切片测试 | 各服务 `pom.xml` |
| Testcontainers | 1.19.7 | Docker 化测试基础设施（MySQL/Milvus/Redis/Neo4j） | `agent-test-infra` 模块 |
| grpc-spring-boot-starter | 3.1.0 | gRPC 服务端/客户端测试 | `agent-test-infra` |
| Spring Cloud Contract | 4.1.0 | 消费者驱动契约测试（CDC） | `agent-test-infra` |
| Testcontainers RocketMQ | 1.19.7 | RocketMQ Topic 测试 | `agent-test-infra` |
| ArchUnit | 1.3.0 | 架构规则测试（包依赖、分层约束） | `agent-test-infra` |
| JaCoCo | 0.8.11 | 覆盖率统计 | 父 POM |
| WireMock | 3.5.1 | HTTP 桩件（模拟第三方 API） | `agent-test-infra` |
| Awaitility | 4.2.1 | 异步测试断言（轮询等待） | `agent-test-infra` |

**新增模块** `agent-test-infra`（Maven 多模块）：

```
agent-test-infra/
├── pom.xml
└── src/main/java/com/agentplatform/testinfra/
    ├── container/         # Testcontainers 工厂
    │   ├── MySQLContainerFactory.java
    │   ├── MilvusContainerFactory.java
    │   ├── RedisContainerFactory.java
    │   ├── Neo4jContainerFactory.java
    │   ├── ClickHouseContainerFactory.java
    │   ├── RocketMQContainerFactory.java
    │   └── ElasticsearchContainerFactory.java
    ├── fixture/           # Fixture 工厂
    │   ├── AgentFixture.java
    │   ├── TaskFixture.java
    │   ├── MemoryFixture.java
    │   ├── ToolFixture.java
    │   ├── DagFixture.java
    │   └── TraceFixture.java
    ├── mock/              # Mock 工厂
    │   ├── ModelGatewayMock.java
    │   ├── ToolEngineMock.java
    │   ├── MemoryServiceMock.java
    │   └── RiskControlMock.java
    └── assertion/         # 自定义断言
        ├── DagAssert.java
        ├── TaskStatusAssert.java
        └── ErrorCodeAssert.java
```

### 1.4 覆盖率目标与度量

| 维度 | 目标 | 度量方式 | JaCoCo 配置 |
|---|---|---|---|
| 行覆盖（Line） | ≥ 80% | `INSTRUCTION_MISSED / (INSTRUCTION_MISSED + INSTRUCTION_COVERED)` | `<rule><element>LINE</element><limit>80%</limit></rule>` |
| 分支覆盖（Branch） | ≥ 75% | `BRANCH_MISSED / (BRANCH_MISSED + BRANCH_COVERED)` | `<rule><element>BRANCH</element><limit>75%</limit></rule>` |
| 方法覆盖（Method） | ≥ 85% | 方法被调用比例 | `<rule><element>METHOD</element><limit>85%</limit></rule>` |
| 类覆盖（Class） | ≥ 90% | 类被测试比例 | `<rule><element>CLASS</element><limit>90%</limit></rule>` |

**核心模块覆盖率要求**（按 P0-P3 优先级分层）：

| 优先级 | 模块 | 行覆盖目标 | 分支覆盖目标 |
|---|---|---|---|
| P0 | agent-runtime / task-orchestrator / planning-service / tool-engine | ≥ 85% | ≥ 80% |
| P0 | agent-gateway（鉴权与路由） | ≥ 85% | ≥ 80% |
| P1 | memory-service / model-gateway | ≥ 80% | ≥ 75% |
| P1 | quality-service / risk-control | ≥ 80% | ≥ 75% |
| P2 | session-service / agent-repo / knowledge-service | ≥ 75% | ≥ 70% |
| P3 | observability / agent-proto / agent-common | ≥ 70% | ≥ 65% |
| **整体加权** | — | **≥ 70%** | **≥ 65%** |

**排除项**（不计入覆盖率）：
- `*Configuration` 类（Spring 配置类）
- `*Application` 主入口类（main 方法）
- `dto/`、`vo/`、`entity/` 包（纯 POJO，由 Bean Validation 注解保证）
- `generated/` 包（protobuf 生成代码）
- `constant/` 包（常量定义）

### 1.5 测试环境分层

| 环境 | 用途 | 数据库 | 消息队列 | 启动方式 |
|---|---|---|---|---|
| **ut**（unit test） | 纯单元测试 | 无（Mock 全部依赖） | 无 | `mvn test` |
| **it**（integration test） | 集成测试 | Testcontainers（同 JVM） | Testcontainers | `mvn verify -Pit` |
| **e2e**（end-to-end） | 端到端测试 | Testcontainers 全栈 | Testcontainers | `mvn verify -Pe2e` |
| **perf**（performance） | 性能基准 | Testcontainers + JMH | Testcontainers | `mvn verify -Pperf` |

**Maven Profile 隔离**：

```xml
<!-- pom.xml 父 POM 节选 -->
<profiles>
  <profile>
    <id>ut</id>
    <activation><activeByDefault>true</activeByDefault></activation>
    <properties>
      <excludedGroups>e2e,it,perf</excludedGroups>
    </properties>
  </profile>
  <profile>
    <id>it</id>
    <properties>
      <excludedGroups>e2e,perf</excludedGroups>
    </properties>
  </profile>
  <profile>
    <id>e2e</id>
    <properties>
      <excludedGroups>perf</excludedGroups>
    </properties>
  </profile>
  <profile>
    <id>perf</id>
    <properties>
      <excludedGroups>e2e</excludedGroups>
    </properties>
  </profile>
</profiles>
```

**JUnit 5 Tag 标注**：

```java
@Tag("it")
@SpringBootTest
class TaskOrchestratorIntegrationTest { ... }

@Tag("e2e")
@SpringBootTest
class IndustryResearchE2ETest { ... }

@Tag("perf")
class DagSchedulePerfTest { ... }
```

---

## 2. 测试分类与范围

### 2.1 单元测试（Unit Test，70%）

**定义**：在 JVM 进程内、不依赖外部中间件、Mock 所有跨服务依赖的测试。

**范围**：

| 测试对象 | 典型测试类 | 关键行为 |
|---|---|---|
| Domain Service（纯逻辑） | `ComplexityScorerTest` | 6 维度评分计算逻辑 |
| 工具类 | `TokenCounterTest` | Token 计数准确性 |
| 状态机 | `TaskStateMachineTest` | 状态流转合法性 |
| 算法 | `CycleDetectorTest` | DAG 环检测（DFS 三色标记） |
| 策略类 | `ReplanModeSelectorTest` | 增量/全量重规划模式选择 |
| DTO/VO 校验 | `TaskSchemaValidatorTest` | Bean Validation 触发 |
| 异常体系 | `ErrorCodeRegistryTest` | 错误码与 HTTP 状态映射 |
| 枚举 | `RiskLevelTest` | R1/R2/R3 转换 |

**Mock 策略**：
- 跨服务依赖（gRPC Client、RocketMQ Producer）：Mockito `@Mock`
- 数据库访问（Mapper/Repository）：Mockito `@Mock` 返回构造的 Fixture
- Redis 客户端：Mockito `@Mock` 或 Fake 实现
- 时间源：`Clock` 注入，测试中固定时间

**示例**（命名规范）：

```java
@DisplayName("复杂度评分器 ComplexityScorer")
class ComplexityScorerTest {

  @Test
  @DisplayName("Should_ReturnL3_When_RiskDimensionIs3AndExecutionIs3")
  void should_return_L3_when_risk_and_execution_both_3() {
    // Given
    var dimensions = Dimensions.builder()
        .goal(2).execution(3).domain(2).knowledge(2).risk(3).context(2).build();
    // When
    var level = scorer.score(dimensions);
    // Then
    assertThat(level).isEqualTo(ComplexityLevel.L3);
  }
}
```

### 2.2 功能测试（Functional / Contract Test，含 gRPC 契约）

**定义**：验证单个 gRPC 服务接口契约是否符合 [02-api 文档](../02-api/api-specification.md) 定义，包括请求/响应消息结构、错误码、超时、流式输出。

**范围**（7 个 gRPC 服务，21 个 RPC 方法）：

| gRPC 服务 | RPC 方法 | 测试类 | 关键契约 |
|---|---|---|---|
| `ToolGateway` | `Invoke` | `ToolGatewayInvokeContractTest` | R1/R2/R3 风险分级、5 步前置校验、错误码 |
| `ToolGateway` | `Recall` | `ToolGatewayRecallContractTest` | Top-K 召回、向量+标签过滤 |
| `ToolGateway` | `ReportResult` | `ToolGatewayReportResultContractTest` | 结果回写、缓存命中 |
| `MemoryService` | `LoadShortTerm` | `MemoryLoadShortTermContractTest` | Token 水位计算、压缩级别 |
| `MemoryService` | `Recall` | `MemoryRecallContractTest` | 4 路召回、Top-N 动态截断 |
| `MemoryService` | `WriteLongTerm` | `MemoryWriteLongTermContractTest` | 重要性评分、去重合并 |
| `MemoryService` | `TriggerDistill` | `MemoryTriggerDistillContractTest` | 蒸馏触发、压缩比校验 |
| `ModelGateway` | `Chat` | `ModelGatewayChatContractTest` | 路由匹配、Prompt 缓存、计量 |
| `ModelGateway` | `CountTokens` | `ModelGatewayCountTokensContractTest` | Token 计数准确性 |
| `TaskOrchestrator` | `SubmitTask` | `TaskSubmitContractTest` | 任务状态机入口、Schema 校验 |
| `TaskOrchestrator` | `GetTask` | `TaskGetContractTest` | 进度聚合、步骤列表 |
| `TaskOrchestrator` | `ReportSubtaskResult` | `TaskReportSubtaskContractTest` | 子任务回调、状态推进 |
| `TaskOrchestrator` | `CancelTask` | `TaskCancelContractTest` | 取消级联、死信处理 |
| `TaskOrchestrator` | `RequestReplan` | `TaskRequestReplanContractTest` | 重规划触发、模式选择 |
| `PlanningService` | `AssessComplexity` | `PlanningAssessComplexityContractTest` | 6 维度评分、判级 |
| `PlanningService` | `Plan` | `PlanningPlanContractTest` | 模板/AI 双分支、DAG JSON |
| `PlanningService` | `ValidatePlan` | `PlanningValidatePlanContractTest` | 5 维度校验、错误码 |
| `AgentRuntime` | `ReportStep` | `AgentRuntimeReportStepContractTest` | 步骤上报、状态外置 |
| `AgentRuntime` | `RequestToolCall` | `AgentRuntimeRequestToolCallContractTest` | 工具调用请求转发 |
| `AgentRuntime` | `ReportSubtaskDone` | `AgentRuntimeReportSubtaskDoneContractTest` | 子任务完成上报 |
| `AgentRuntime` | `RequestHumanIntervention` | `AgentRuntimeRequestHumanContractTest` | 人工介入请求 |
| `KnowledgeService` | `Retrieve` | `KnowledgeRetrieveContractTest` | 知识检索、权限校验 |
| `KnowledgeService` | `IngestDocument` | `KnowledgeIngestDocumentContractTest` | 文档解析、切片、向量化 |

**测试方式**：
- 使用 `grpc-spring-boot-starter` 的 `@GrpcTest` 注解启动嵌入式 gRPC 服务
- Mock 业务逻辑层，仅验证协议契约
- 使用 protobuf `JsonFormat` 比对请求/响应

**REST 契约测试**（gateway 转发的 REST API）：
- 使用 `@WebMvcTest` 切片测试 Controller
- Mock Service 层，验证 HTTP 状态码、JSON 响应结构、错误码映射
- 引用 [02-api §0.5 错误码规范](../02-api/api-specification.md)

### 2.3 集成测试（Integration Test，20%）

**定义**：跨模块、跨服务的集成测试，使用 Testcontainers 启动真实中间件，验证服务间协作契约。

**范围**（关键集成链路）：

| 集成链路 | 测试类 | 验证目标 | 容器依赖 |
|---|---|---|---|
| gateway → session → orchestrator | `AccessToIntegrationTest` | 鉴权 → 会话 → 任务提交全链路 | MySQL + Redis |
| orchestrator → planning → dag | `TaskPlanDagIntegrationTest` | 复杂度评估 → 规划 → DAG 落库 | MySQL |
| orchestrator → runtime → model | `TaskExecuteIntegrationTest` | 任务分发 → ReAct 循环 → 模型调用 | MySQL + Redis |
| runtime → tool-engine → sandbox | `ToolSandboxIntegrationTest` | 工具调用 → 沙箱执行 → 结果清洗 | MySQL + Docker |
| runtime → memory → Milvus | `MemoryRecallIntegrationTest` | 记忆召回 → 向量检索 → 重排 | Milvus + MySQL |
| quality → memory.write → RocketMQ | `MemoryWriteIntegrationTest` | 子任务完成 → 异步记忆写入 | MySQL + RocketMQ |
| risk-control → audit_log → MySQL | `RiskAuditIntegrationTest` | 越权拦截 → 审计落盘 | MySQL |
| model-gateway → provider | `ModelRouteIntegrationTest` | 模型路由 → 适配器 → 计量 | Redis（WireMock 桩上游） |
| drift monitor → ClickHouse | `DriftMetricsIntegrationTest` | 指标采集 → ClickHouse 写入 | ClickHouse |
| code-retrieval → Neo4j | `CodeGraphIntegrationTest` | 代码图谱 → 调用链扩展 | Neo4j + ES |

**集成测试典型结构**：

```java
@Tag("it")
@SpringBootTest
@Testcontainers
class TaskPlanDagIntegrationTest {

  @Container
  static GenericContainer<?> mysql = MySQLContainerFactory.build();

  @Container
  static GenericContainer<?> redis = RedisContainerFactory.build();

  @Autowired PlanningServiceGrpc.PlanningServiceBlockingStub planningStub;
  @Autowired TaskOrchestratorGrpc.TaskOrchestratorBlockingStub taskStub;
  @Autowired TaskDagMapper dagMapper;

  @Test
  @DisplayName("Should_PersistDagWithCycleDetectedError_When_AiPlannerGeneratesCycle")
  void should_persist_dag_with_cycle_error_when_ai_planner_generates_cycle() {
    // Given
    var planRequest = TaskFixture.buildPlanRequest("tpl_with_cycle");
    // When
    var response = planningStub.plan(planRequest);
    // Then
    assertThat(response.getDagJson()).isNullOrEmpty();
    assertThat(response.getWarningsList()).contains("DAG_CYCLE_DETECTED");
    var dag = dagMapper.selectLatest("tk_xxx");
    assertThat(dag.getStatus()).isEqualTo("FAILED");
  }
}
```

### 2.4 用户流程测试（End-to-End，10%）

**定义**：从用户输入到最终输出的完整业务流程测试，覆盖 PRD 第一章第三节定义的 7 步全链路。

**范围**：见 [§5 端到端业务场景测试](#5-端到端业务场景测试3-个示例) 的 3 个场景：
1. 行业调研 Agent（L3 DAG + RAG + 事实校验）
2. 代码生成 Agent（AST 解析 + 多轮 ReAct）
3. 数据分析 Agent（SQL 工具 + 结果校验 + 重规划）

**E2E 测试特性**：
- 启动全栈 Testcontainers（MySQL + Redis + Milvus + RocketMQ + Neo4j + ClickHouse + ES）
- 模拟真实用户请求（HTTP/SSE）
- 验证中间状态（数据库、Redis、消息队列）
- 验证最终输出（响应内容、记忆沉淀、审计日志）

### 2.5 回归测试（Regression，基于 Badcase 表）

**定义**：基于 `badcase` 表的回归测试，防止已修复的缺陷再次出现。

**数据来源**：
- `agent_quality.badcase` 表（[01-database §8.3](../01-database/database-schema-design.md)）
- 自动归集触发源：L4 校验失败、用户反馈、漂移告警、工具调用失败、重规划触发

**回归测试用例生成规则**：

| Badcase `category` | 对应测试包 | 测试方法命名前缀 | 优先级 |
|---|---|---|---|
| `hallucination` | `HallucinationRegressionTest` | `Should_NotHallucinate_When_{场景}_` | P0 |
| `tool_error` | `ToolErrorRegressionTest` | `Should_NotFail_When_{错误码}_` | P0 |
| `plan_error` | `PlanErrorRegressionTest` | `Should_NotMisplan_When_{触发}_` | P1 |
| `drift` | `DriftRegressionTest` | `Should_NotDrift_When_{场景}_` | P1 |
| `format` | `FormatRegressionTest` | `Should_ValidateFormat_When_{任务类型}_` | P2 |

**Badcase 同步机制**：
- `quality-service` 提供 `GET /api/v1/badcases?status=analyzed` 接口
- CI 每日定时任务拉取新增已分析 Badcase，自动生成回归测试骨架
- 测试失败 → 触发告警 + 阻塞发布

### 2.6 性能测试（Performance Baseline）

**定义**：建立关键操作的延迟基线，监控性能退化。

**范围**（6 项核心性能基线，对齐 [00-overview §1.2](../00-overview/tech-stack-and-architecture.md)）：

| 性能指标 | 基线 | 测试类 | 工具 |
|---|---|---|---|
| DAG 调度延迟（单批次） | < 100ms | `DagSchedulePerfTest` | JMH |
| 向量召回延迟（Top-10） | < 50ms | `VectorRecallPerfTest` | JMH |
| Token 压缩延迟（轻/中/重） | < 200ms / 500ms / 1s | `TokenCompressPerfTest` | JMH |
| 意图识别 P95 | ≤ 200ms | `IntentRecognizePerfTest` | JMH |
| 单步工具调用 P95 | ≤ 800ms | `ToolInvokePerfTest` | JMH |
| Agent 实例并发承载 | ≥ 200 / 实例 | `AgentRuntimeConcurrencyTest` | Gatling |

**性能测试失败阈值**：
- 基线延迟超过 1.5 倍 → P3 告警，不阻塞
- 基线延迟超过 2 倍 → P1 告警，阻塞合并
- 并发承载低于 80% → 阻塞合并

---

## 3. 各微服务测试矩阵（13 个服务，按 P0-P3 优先级）

### 3.1 优先级定义

| 优先级 | 含义 | 测试覆盖要求 | 阻塞合并 |
|---|---|---|---|
| P0 | 阻塞性核心服务，失败即生产事故 | 行 ≥ 85%，分支 ≥ 80%，E2E 必跑 | 是 |
| P1 | 重要业务服务，影响核心功能 | 行 ≥ 80%，分支 ≥ 75% | 是 |
| P2 | 支撑性服务，影响范围有限 | 行 ≥ 75%，分支 ≥ 70% | 否（仅告警） |
| P3 | 基础/可观测服务，非业务关键路径 | 行 ≥ 70%，分支 ≥ 65% | 否 |

**13 个服务优先级映射**（依据 [00-overview §3.1](../00-overview/tech-stack-and-architecture.md)）：

| 服务名 | 端口 | 优先级 | 理由 |
|---|---|---|---|
| agent-proto | - | P3 | gRPC 协议定义，编译期检查为主 |
| agent-common | - | P3 | 公共工具类，覆盖率影响所有模块 |
| agent-gateway | 8080 | P0 | 唯一对外入口，鉴权/限流必过 |
| session-service | 8082 | P2 | 会话管理，失败可降级为无会话 |
| task-orchestrator | 8084 | P0 | 调度中枢，DAG/状态机核心 |
| planning-service | 8086 | P0 | 规划引擎，决定任务执行路径 |
| memory-service | 8088 | P1 | 记忆核心，影响 Agent 智能性 |
| tool-engine | 8090 | P0 | 工具网关，安全/成本/审计关键 |
| agent-runtime | 8092 | P0 | ReAct/Reflexion 执行体 |
| model-gateway | 8094 | P1 | 模型路由，影响效果与成本 |
| agent-repo | 8096 | P2 | Agent 仓库，CRUD 为主 |
| knowledge-service | 8098 | P2 | 知识库，非实时关键路径 |
| quality-service | 8100 | P1 | 质量校验，影响发布门禁 |
| risk-control | 8102 | P0 | 安全合规，越权必拦截 |
| observability | 8104 | P3 | 旁路可观测，失败不影响业务 |

### 3.2 P0 核心服务测试矩阵

#### 3.2.1 agent-proto

| 测试类 | 关键行为 | 决策节点 | 数据来源 | Mock |
|---|---|---|---|---|
| `ProtoCompileTest` | `.proto` 文件编译生成 Java stub 可序列化 | - | - | 无 |
| `ProtoBackwardCompatTest` | 新增字段不破坏旧版本反序列化 | - | git 历史 | 无 |
| `ProtoFieldNumberTest` | 字段编号 1-99 不重复，保留 reserved | - | - | 无 |
| `TraceContextPropagationTest` | `TraceContext` 在所有 RPC 中作为 field 99 传递 | - | - | 无 |
| `ErrorCodeEnumMappingTest` | `ErrorCode` 枚举与 [02-api §0.5](../02-api/api-specification.md) 错误码一一对应 | - | - | 无 |

**测试方法命名示例**：
- `Should_GenerateSerializableStub_When_ProtoCompiled`
- `Should_PreserveFieldNumber99_ForTraceContext`
- `Should_NotBreakOlderClient_When_NewOptionalFieldAdded`

#### 3.2.2 agent-gateway

| 测试类 | 关键行为 | 决策节点 | 数据来源 | Mock |
|---|---|---|---|---|
| `ProtocolAdapterTest` | REST/Web-SSE/IM/Enterprise 四协议适配为 Task 对象 | F1.D1 | - | 无 |
| `AuthFilterTest` | JWT 失效返回 401 UNAUTHENTICATED；API-Key 映射为系统用户 | F1.D2 | Testcontainers MySQL | mock SessionService |
| `RateLimiterFilterTest` | 令牌桶余额 < 0 返回 429 RATE_LIMITED；带 `Retry-After` 头 | F1.D3 | Testcontainers Redis | 无 |
| `RiskPreCheckFilterTest` | 内容安全/Prompt 注入拦截返回 403 CONTENT_BLOCKED | F1.D4 | Testcontainers MySQL | mock RiskControl |
| `SessionLoaderTest` | 会话不存在时新建；存在时复用并加载消息历史 | F1.D5 | Testcontainers MySQL | 无 |
| `IntentRouterTest` | 闲聊/单步/复杂三分支路由到对应下游服务 | F1.D6 | - | mock Orchestrator |
| `RequestNormalizerTest` | 多端输入标准化为 Task Schema | - | - | 无 |
| `SseStreamHandlerTest` | SSE 流式输出，token/tool_call/done 事件序列正确 | - | - | mock Orchestrator |
| `ErrorResponseBuilderTest` | 错误响应包含 traceId/timestamp/code/message/details | - | - | 无 |
| `GatewayIntegrationTest` | 全链路：接入 → 鉴权 → 限流 → 风控 → 路由 | F1.D1~D6 | Testcontainers MySQL+Redis | 真实 session/orchestrator |

**测试方法命名示例**：
- `Should_Return401Unauthenticated_When_JwtSignatureInvalid`（F1.D2 false 分支）
- `Should_Return429RateLimited_When_TokenBucketExhausted`（F1.D3 true 分支）
- `Should_Return403ContentBlocked_When_PromptInjectionDetected`（F1.D4 true 分支）
- `Should_CreateNewSession_When_SessionIdNotExists`（F1.D5 true 分支）
- `Should_LoadHistoryMessages_When_SessionIdExists`（F1.D5 false 分支）
- `Should_RouteToOrchestrator_When_TaskTypeIsComplex`（F1.D6 complex 分支）

#### 3.2.3 task-orchestrator

| 测试类 | 关键行为 | 决策节点 | 数据来源 | Mock |
|---|---|---|---|---|
| `TaskStateMachineTest` | 10 状态合法流转；非法流转抛 `TASK_STATUS_CONFLICT` | doc 08 §1 | - | 无 |
| `DagTopoSorterTest` | 拓扑排序正确；环检测返回 `DAG_CYCLE_DETECTED` | F4.D1~D3 | - | 无 |
| `BatchPartitionerTest` | 同层无依赖节点归为同批次；批次顺序正确 | F4.D1~D3 | - | 无 |
| `AgentMatcherTest` | 4 维度加权评分；< 0.6 抛 `AGENT_NOT_FOUND` | F4.D4 | Testcontainers MySQL | 无 |
| `ParameterInjectorTest` | 上游 outputs 按依赖边注入下游 inputs | - | - | 无 |
| `SubtaskDispatcherTest` | RocketMQ 事务消息发送；幂等键控制重复消费 | F4 | Testcontainers RocketMQ | mock AgentRuntime |
| `BatchTimeoutWatcherTest` | 超时 30s 标记 `TIMEOUT`；触发重试或重规划 | F4.D7 | Testcontainers Redis | 无 |
| `FailureClassifierTest` | 单子任务失败 / 失败过半 / 全失败分级处置 | F4.D5/D6/D8 | - | 无 |
| `ReplanTriggerTest` | 增量重规划上限 2 次；全量上限 1 次；超限转 `WAITING_HUMAN` | F5.D4/D5 | Testcontainers MySQL | mock PlanningService |
| `ResultAggregatorTest` | 全部子任务完成后聚合；处理异常分支 | F4.End | - | 无 |
| `CostCircuitBreakerTest` | `cost_used >= cost_limit` 触发 `COST_BUDGET_EXCEEDED` | F4.D9 | - | 无 |
| `TaskOrchestratorContractTest` | 5 个 RPC 方法的契约校验 | - | - | 无 |

**测试方法命名示例**：
- `Should_TransitToRunning_When_L1TaskSubmitted`（状态机）
- `Should_TransitToWaitingHuman_When_R3NodeRequiresReview`（状态机 RUNNING→WAITING_HUMAN）
- `Should_TransitToReplanning_When_SubtaskRetryExhausted`（状态机 SUBTASK_RUNNING→REPLANNING）
- `Should_PartitionIntoTwoBatches_When_DagHasParallelNodes`（F4.D1~D3）
- `Should_SelectHighestScoreAgent_When_MultipleCandidatesMatch`（F4.D4 true 分支）
- `Should_TransitToWaitingHuman_When_NoAgentScoreAbove06`（F4.D4 false 分支）
- `Should_TriggerIncrementalReplan_When_SingleSubtaskFailsAndOthersValid`（F5.D5 true 分支）
- `Should_TransitToWaitingHuman_When_ReplanCountExceedsMax`（F5