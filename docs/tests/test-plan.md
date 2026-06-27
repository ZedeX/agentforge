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
- `Should_TransitToWaitingHuman_When_ReplanCountExceedsMax`（F5.D4 true 分支）
- `Should_DetectCycleAndReject_When_MergedDagHasCycle`（F5.I5 true 分支）
- `Should_ThrowCostBudgetExceeded_When_CostUsedExceedsLimit`（F4.D9 true 分支）

#### 3.2.4 planning-service

| 测试类 | 关键行为 | 决策节点 | 数据来源 | Mock |
|---|---|---|---|---|
| `ComplexityScorerTest` | 6 维度评分（goal/execution/domain/knowledge/risk/context）；阈值 8/14 判级 | F2.D4~D6 | - | 无 |
| `RuleFilterTest` | 规则初筛置信度 ≥0.9 跳过模型精判 | F2.D3 | - | 无 |
| `ModelAssessorTest` | 调用强模型评估 6 维度评分 | F2.D3 false | WireMock 桩上游 | mock ModelGateway |
| `TemplateMatcherTest` | 高频场景模板匹配（周报/邮件/调研）；无匹配进入智能规划 | F3 模板分支 | Testcontainers MySQL | 无 |
| `AiPlannerTest` | 调用强模型生成 DAG JSON；解析为节点边结构 | F3 智能分支 | - | mock ModelGateway |
| `PlanValidatorTest` | 5 维度自检：完备性/原子性/效率/成本/容错；失败重试 2 次 | F3 自检 | - | 无 |
| `DagCycleDetectorTest` | DFS 三色标记环检测；含环抛 `DAG_CYCLE_DETECTED` | F3.I3 | - | 无 |
| `PlanningServiceContractTest` | AssessComplexity/Plan/ValidatePlan 3 个 RPC 契约 | - | - | 无 |

**测试方法命名示例**：
- `Should_ReturnL1_When_TotalScoreLe8`（F2.D4 true 分支）
- `Should_ReturnL2_When_TotalScoreBetween9And14`（F2.D5 true 分支）
- `Should_ReturnL3_When_TotalScoreGt14`（F2.D5 false 分支）
- `Should_ForceUpgradeToL3_When_RiskLevelIsHigh`（F2.D6 true 分支）
- `Should_BypassModelAssessor_When_RuleConfidenceGe090`（F2.D3 true 分支）
- `Should_MatchTemplate_When_HighFrequencyScenario`（F3 模板分支）
- `Should_FallbackToAiPlanner_When_NoTemplateMatched`（F3 智能分支）
- `Should_ReturnPlanValidationFailed_When_CompletenessFailed`（F3 自检失败）

#### 3.2.5 tool-engine

| 测试类 | 关键行为 | 决策节点 | 数据来源 | Mock |
|---|---|---|---|---|
| `ToolRegistryTest` | 工具注册写入三层 Schema（meta/input/output）；版本管理 | - | Testcontainers MySQL | 无 |
| `ToolSemanticRecallerTest` | 向量+标签 Top-K 召回；score 降序 | F8 召回阶段 | Milvus Testcontainers | 无 |
| `RiskClassifierTest` | R1/R2/R3 三级分类；executor_type + side_effect 决定 | F8.D4 | - | 无 |
| `ToolGatewayTest` | 5 步前置校验链（参数 Schema/配额/审批/沙箱/成本）；R3 强制双人审批 | F8.D5~D9 | - | mock ToolApprovalRepository |
| `SandboxExecutorTest` | Docker 容器即用即毁；执行超时 60s 强制销毁 | F8 R3 执行 | Docker Testcontainers | 无 |
| `ResultCleanerTest` | 结果清洗：超长摘要化、敏感字段脱敏、Token 限流 | F8 后处理 | - | 无 |
| `ToolCallAuditorTest` | 全量审计落盘：traceId/toolId/input/output/status/duration | F8 审计 | Testcontainers MySQL | 无 |
| `ToolGatewayInvokeContractTest` | Invoke RPC 契约（含 risk_level/prompt_cache_key） | - | - | 无 |

**测试方法命名示例**：
- `Should_RecallTopKBySemantic_When_QueryMatched`（F8 召回）
- `Should_ClassifyR1_When_ToolIsReadonlyPublic`（F8.D4 R1 分支）
- `Should_ClassifyR3_When_ToolIsWriteIrreversible`（F8.D4 R3 分支）
- `Should_RejectInvoke_When_R3ApprovalMissing`（F8.D6 false 分支）
- `Should_RejectInvoke_When_ApprovalExpired`（F8.D7 true 分支）
- `Should_ExecuteInSandbox_When_R3Approved`（F8.D9 true 分支）
- `Should_EnforceMaxCallCount_When_LimitExceeded`（F8 成本熔断）
- `Should_WriteAuditLog_When_ToolCallCompleted`（F8 审计）

#### 3.2.6 agent-runtime

| 测试类 | 关键行为 | 决策节点 | 数据来源 | Mock |
|---|---|---|---|---|
| `ReActLoopTest` | Think→Act→Observe 循环；4 标签分支（`<tool_call>`/`<handoff>`/`<final_answer>`/`NEED_MORE_INFO`） | F6.D2~D5 | - | mock ModelGateway/ToolEngine |
| `ReflexionEngineTest` | 3 模式（none/single/multi）；注入 REFLECTION 提示重试；上限 2 次 | F6.D6/F9.D5 | - | 无 |
| `TokenWatermarkMonitorTest` | 4 级水位（SAFE/WARN/CRITICAL/CIRCUIT_BREAK）阈值判定 | F7.D1~D4 | - | 无 |
| `StepStateSyncerTest` | 每阶段状态外置到 Redis `runtime:{agentId}:state` | F6 状态同步 | Testcontainers Redis | 无 |
| `CheckpointManagerTest` | 断点续跑：每步持久化检查点；崩溃恢复至最近 step | F6 检查点 | Testcontainers Redis | 无 |
| `CircuitBreakerTest` | 循环次数超上限触发熔断；转 `WAITING_HUMAN` | F6.D7 | - | 无 |
| `AgentRuntimeContractTest` | ReportStep/RequestToolCall/ReportSubtaskDone/RequestHumanIntervention/ResumeFromCheckpoint 5 RPC 契约 | - | - | 无 |

**测试方法命名示例**：
- `Should_EnterThinkPhase_When_ReActLoopStarts`（F6.D1）
- `Should_TransitToAct_When_ThinkProducesToolCall`（F6.D2 true 分支）
- `Should_TransitToObserve_When_ToolReturnsResult`（F6.D3）
- `Should_TerminateLoop_When_FinalAnswerProduced`（F6.D5 final_answer 分支）
- `Should_TriggerReflexion_When_L4ValidationFailed`（F9.D5 true 分支）
- `Should_ThrowMaxRetryExceeded_When_RetryCountGt2`（F9.D6 false 分支）
- `Should_BreakCircuit_When_LoopCountExceedsMax`（F6.D7 true 分支）
- `Should_TriggerLightCompress_When_TokenUsage70To85`（F7.D2 true 分支）

#### 3.2.7 risk-control

| 测试类 | 关键行为 | 决策节点 | 数据来源 | Mock |
|---|---|---|---|---|
| `PermissionPolicyTest` | RBAC+ABAC 策略匹配；越权写操作拦截 | F1.D2 越权 | Testcontainers MySQL | 无 |
| `RiskPreCheckServiceTest` | Prompt 注入检测；敏感词过滤；返回 `CONTENT_BLOCKED` | F1.D4 | - | 无 |
| `AuditLogWriterTest` | 全量调用审计落盘；traceId 关联；不可篡改 | F1 审计 | Testcontainers MySQL | 无 |
| `ApprovalStateMachineTest` | R3 审批 4 状态流转（pending→first_approved→approved→expired） | doc 08 §2 | - | 无 |
| `RiskControlContractTest` | PreCheck/SubmitApproval/QueryApprovalStatus 3 RPC 契约 | - | - | 无 |

### 3.3 P1 重要服务测试矩阵

#### 3.3.1 memory-service

| 测试类 | 关键行为 | 决策节点 | 数据来源 | Mock |
|---|---|---|---|---|
| `ShortTermMemoryStoreTest` | Redis Hash 存储短期记忆；TTL 24h；窗口淘汰 | F12 短期 | Testcontainers Redis | 无 |
| `LongTermMemoryWriterTest` | 写入 Milvus + MySQL；重要性评分；去重合并（cosine_sim≥0.85 更新） | F12.D2~D4 | Milvus Testcontainers | 无 |
| `MultiPathRecallerTest` | 4 路召回（语义+关键词+时间+标签）；融合重排（语义40%+符号30%+结构20%+热度10%） | F12.D6~D9 | Milvus Testcontainers | 无 |
| `MemoryDistillerTest` | 同主题碎片≥5 条触发蒸馏；三级结构（全局摘要-主题摘要-细节）；压缩比>50% | F12 蒸馏 | - | 无 |
| `TokenCompressorTest` | 4 级压缩策略（轻/中/重/熔断）；6 类内容优先级裁剪 | F7.D2~D4 | - | 无 |
| `MemoryServiceContractTest` | LoadShortTerm/Recall/WriteLongTerm/TriggerDistill 4 RPC 契约 | - | - | 无 |

#### 3.3.2 model-gateway

| 测试类 | 关键行为 | 决策节点 | 数据来源 | Mock |
|---|---|---|---|---|
| `ModelRouterTest` | 路由表匹配（scene+tier）；强模型/轻模型/备用模型选择 | - | - | 无 |
| `CostMeterTest` | 按输入输出分开计费；Prompt 缓存折扣；月度配额 | - | - | 无 |
| `PromptCacheTest` | 相同前缀命中缓存；Anthropic 10%/OpenAI 50% 折扣规则 | - | Testcontainers Redis | 无 |
| `ModelDegradationManagerTest` | 主模型不可用降级；指数退避重试最多 3 次 | - | WireMock 桩上游 | 无 |
| `ModelGatewayContractTest` | Chat/StreamChat/CountTokens/ReportUsage 4 RPC 契约 | - | - | 无 |

#### 3.3.3 quality-service

| 测试类 | 关键行为 | 决策节点 | 数据来源 | Mock |
|---|---|---|---|---|
| `L4HardValidatorTest` | L4-1 规则化硬校验：JSON Schema + 来源标签 + 黑名单 | F9.D2 | - | 无 |
| `L4ConsistencyValidatorTest` | L4-2 事实一致性：cosine_sim 阈值 0.75 | F9.D3 | Milvus Testcontainers | 无 |
| `L4AuditValidatorTest` | L4-3 终审强模型评分：overall ≥0.7 通过 | F9.D4 | - | mock ModelGateway |
| `BadcaseWriterTest` | Badcase 自动归集：5 类触发源（L4 失败/反馈/漂移/工具失败/重规划） | F9.D6 false | Testcontainers MySQL | 无 |
| `ManualReviewQueueTest` | 高严重度 Badcase 推送人工审核队列；通知审核人员 | - | - | 无 |
| `QualityServiceContractTest` | ValidateOutput/ReportBadcase/QueryBadcase 3 RPC 契约 | - | - | 无 |

### 3.4 P2 支撑服务测试矩阵

#### 3.4.1 session-service

| 测试类 | 关键行为 | 决策节点 | 数据来源 | Mock |
|---|---|---|---|---|
| `SessionServiceTest` | 会话生命周期：active→idle→closed→archived；4 状态流转 | - | Testcontainers MySQL | 无 |
| `SessionRepositoryTest` | MySQL 持久化；按 tenantId 索引查询 | - | Testcontainers MySQL | 无 |
| `ShortTermMemoryServiceTest` | Redis 短期记忆：Hash Key `sm:{sessionId}:ctx`；TTL 24h | - | Testcontainers Redis | 无 |
| `SsePushServiceTest` | SSE 事件序列：token→tool_call→tool_result→done | - | - | 无 |
| `SessionControllerTest` | 6 个 REST 端点切片测试 | - | - | mock SessionService |

#### 3.4.2 agent-repo

| 测试类 | 关键行为 | 决策节点 | 数据来源 | Mock |
|---|---|---|---|---|
| `AgentDefinitionRepositoryTest` | Agent CRUD；版本管理（draft→published→canary→archived） | - | Testcontainers MySQL | 无 |
| `AgentVersionServiceTest` | 灰度发布按比例分流；自动回滚；全量发布 | PRD §五(二)4 | - | 无 |
| `CanaryRouterTest` | 10%/50%/100% 灰度比例路由 | - | Testcontainers Redis | 无 |

#### 3.4.3 knowledge-service

| 测试类 | 关键行为 | 决策节点 | 数据来源 | Mock |
|---|---|---|---|---|
| `DocumentIngestorTest` | 文档解析（PDF/Word/Markdown）；切片 500-1000 Token | - | - | 无 |
| `KnowledgeChunkStoreTest` | Milvus 向量化存储；HNSW 索引；Partition 按知识库 ID | - | Milvus Testcontainers | 无 |
| `KnowledgeRetrieverTest` | Top-K 召回；权限校验（tenantId 隔离） | - | Milvus Testcontainers | 无 |
| `KnowledgeServiceContractTest` | Retrieve/IngestDocument 2 RPC 契约 | - | - | 无 |

### 3.5 P3 基础服务测试矩阵

#### 3.5.1 agent-proto

详见 [3.2.1](#321-agent-proto)（已归入 P0 共同基础），实际优先级 P3。

#### 3.5.2 agent-common

详见 [3.2.1](#321-agent-proto) 中的 agent-common 部分（已归入 P0 共同基础），实际优先级 P3。

#### 3.5.3 observability

| 测试类 | 关键行为 | 决策节点 | 数据来源 | Mock |
|---|---|---|---|---|
| `MetricsCollectorTest` | 指标采集：QPS/延迟/错误率/Token 用量 | - | - | 无 |
| `TraceExporterTest` | 链路追踪导出至 ClickHouse；traceId 关联 | - | ClickHouse Testcontainers | 无 |
| `AlertRuleEvaluatorTest` | 告警规则评估（阈值/趋势/异常检测） | - | - | 无 |

---

## 4. 关键测试场景（基于 F1-F12 决策流程图）

本节针对 [11-detail-flow](../11-detail-flow/01-access-and-planning-flow.md) 中 12 张决策流程图共 103 个决策节点，按"每个决策节点 true/false 双分支必测"原则列出关键测试用例。详细用例清单见 [unit-test-cases.md](unit-test-cases.md) 与 [functional-test-cases.md](functional-test-cases.md)，本节聚焦决策节点覆盖矩阵。

### 4.1 F1 接入网关请求处理流程（6 决策节点 → 12 测试用例）

**来源**：[11-detail-flow/01-access-and-planning-flow.md F1](../11-detail-flow/01-access-and-planning-flow.md)

| 决策节点 | 条件表达式 | true 分支用例 | false 分支用例 | 错误码 |
|---|---|---|---|---|
| F1.D1 | `protocol in [REST, SSE, IM, API_KEY]` | UT-GATE-001 REST 适配 | UT-GATE-002 IM Webhook 适配 | `UNSUPPORTED_PROTOCOL` |
| F1.D2 | `jwt.valid OR api_key.valid` | UT-GATE-004 API-Key 鉴权 | UT-GATE-003 JWT 无效返回 401 | `UNAUTHENTICATED` |
| F1.D3 | `token_bucket.remaining >= 0` | 正常放行（FT-ACCESS-001） | UT-GATE-005 令牌桶耗尽返回 429 | `RATE_LIMITED` |
| F1.D4 | `not prompt_injection_detected` | 正常放行 | UT-GATE-006 Prompt 注入返回 403 | `CONTENT_BLOCKED` |
| F1.D5 | `session.exists(sessionId)` | UT-GATE-008 加载历史消息 | UT-GATE-007 新建会话 | - |
| F1.D6 | `task_type in [chitchat, single_step, complex]` | UT-GATE-009/010 路由分发 | 不支持类型返回 400 | `VALIDATION_FAILED` |

**覆盖率要求**：6 节点 × 2 分支 = 12 用例，P0 优先级，对应 `agent-gateway` 模块。

### 4.2 F2 意图识别与复杂度判定（7 决策节点 → 14 测试用例）

**来源**：[11-detail-flow/01-access-and-planning-flow.md F2](../11-detail-flow/01-access-and-planning-flow.md)

| 决策节点 | 条件表达式 | true 分支用例 | false 分支用例 | 错误码 |
|---|---|---|---|---|
| F2.D1 | `task.goal not null and length <= 2000` | UT-PLAN-001 正常评分 | 空目标返回 400 | `VALIDATION_FAILED` |
| F2.D2 | `tenant.quota.remaining > 0` | 正常评估 | 配额耗尽返回 429 | `RATE_LIMITED` |
| F2.D3 | `rule.confidence >= 0.9` | UT-PLAN-005 跳过模型精判 | UT-PLAN-006 调用 ModelAssessor | - |
| F2.D4 | `total_score <= 8` | UT-PLAN-001 判级 L1 | 进入 D5 判定 | - |
| F2.D5 | `total_score <= 14` | UT-PLAN-002 判级 L2 | UT-PLAN-003 判级 L3 | - |
| F2.D6 | `risk_level == HIGH` | UT-PLAN-004 强制升级 L3 | 维持原判级 | - |
| F2.D7 | `template.matched == true` | UT-PLAN-007 模板匹配 | UT-PLAN-008 进入智能规划 | - |

**覆盖率要求**：7 节点 × 2 分支 = 14 用例，P0 优先级，对应 `agent-planning` 模块。

### 4.3 F3 任务规划与 DAG 生成（9 决策节点 → 18 测试用例）

**来源**：[11-detail-flow/01-access-and-planning-flow.md F3](../11-detail-flow/01-access-and-planning-flow.md)

| 决策节点 | 条件表达式 | true 分支用例 | false 分支用例 | 错误码 |
|---|---|---|---|---|
| F3.D1 | `mode == TEMPLATE` | UT-PLAN-007 模板加载 | UT-PLAN-008 调用 AI 规划 | - |
| F3.D2 | `ai_planner.response.valid_json` | DAG 解析成功 | 返回 `PLAN_GENERATION_FAILED` | `PLAN_GENERATION_FAILED` |
| F3.D3 | `dag.nodes.size >= 1` | 继续校验 | 空 DAG 返回错误 | `PLAN_VALIDATION_FAILED` |
| F3.I3 | `not detectCycle(dag)` | UT-ORCH-005 无环通过 | 含环抛 `DAG_CYCLE_DETECTED` | `DAG_CYCLE_DETECTED` |
| F3.D4 | `validation.completeness` | UT-PLAN-009 完备性通过 | UT-PLAN-010 完备性失败 | `PLAN_VALIDATION_FAILED` |
| F3.D5 | `validation.atomicity` | 原子性通过 | 原子性失败重试 2 次 | `PLAN_VALIDATION_FAILED` |
| F3.D6 | `validation.efficiency` | 效率通过 | 效率过低重试 | `PLAN_VALIDATION_FAILED` |
| F3.D7 | `validation.cost` | 成本通过 | 成本超限重试 | `PLAN_VALIDATION_FAILED` |
| F3.D8 | `validation.fault_tolerance` | 容错通过 | 容错不足重试 | `PLAN_VALIDATION_FAILED` |

**覆盖率要求**：9 节点 × 2 分支 = 18 用例，P0 优先级，对应 `agent-planning` 模块。

### 4.4 F4 子任务分发与并行调度（8 决策节点 → 16 测试用例）

**来源**：[11-detail-flow/01-access-and-planning-flow.md F4](../11-detail-flow/01-access-and-planning-flow.md)

| 决策节点 | 条件表达式 | true 分支用例 | false 分支用例 | 错误码 |
|---|---|---|---|---|
| F4.D1 | `batch.size > 0` | UT-ORCH-006 批次划分 | 空批次结束调度 | - |
| F4.D2 | `agent.score >= 0.6` | UT-ORCH-007 选最高分 Agent | UT-ORCH-008 抛 `AGENT_NOT_FOUND` | `AGENT_NOT_FOUND` |
| F4.D3 | `agent.quota.remaining > 0` | 正常分发 | 配额耗尽切换备用 | `RATE_LIMITED` |
| F4.D4 | `subtask.execute_success` | UT-ORCH-007 成功推进 | 进入失败分类 | - |
| F4.D5 | `fail_count <= batch.size * 0.5` | UT-ORCH-009 增量重规划 | UT-ORCH-013 全量重规划/人工 | - |
| F4.D6 | `retry_count < maxRetries` | 重试投递 | 标记 `RETRY_EXHAUSTED` | `MAX_RETRY_EXCEEDED` |
| F4.D7 | `batch.duration <= 30s` | 正常等待 | 标记 `TIMEOUT` | `TASK_TIMEOUT` |
| F4.D8 | `cost_used < cost_limit` | 正常推进 | UT-ORCH-012 抛 `COST_BUDGET_EXCEEDED` | `COST_BUDGET_EXCEEDED` |

**覆盖率要求**：8 节点 × 2 分支 = 16 用例，P0 优先级，对应 `agent-task-orchestrator` 模块。

### 4.5 F5 动态重规划决策（7 决策节点 → 14 测试用例）

**来源**：[11-detail-flow/02-runtime-and-replan-flow.md F5](../11-detail-flow/02-runtime-and-replan-flow.md)

| 决策节点 | 条件表达式 | true 分支用例 | false 分支用例 | 错误码 |
|---|---|---|---|---|
| F5.D1 | `need_replan == true` | 进入重规划流程 | 跳过重规划 | - |
| F5.D2 | `reason == requirement_change` | UT-ORCH-010 全量重规划 | UT-ORCH-009 局部失败 | - |
| F5.D3 | `failed_count == 1 and others_valid` | UT-ORCH-009 增量重规划 | 进入全量重规划 | - |
| F5.D4 | `replan_count <= max_replan (2)` | 正常重规划 | UT-ORCH-011 转人工 | `REPLAN_EXHAUSTED` |
| F5.D5 | `mode == INCREMENTAL` | 增量补丁 DAG 生成 | 全量重新生成 | - |
| F5.I5 | `not detectCycle(mergedDag)` | FT-REPLAN-003 合并通过 | FT-REPLAN-006 含环抛错 | `DAG_CYCLE_DETECTED` |
| F5.D6 | `merged_dag.valid` | FT-REPLAN-004 版本递增 | 重规划失败转人工 | `PLAN_VALIDATION_FAILED` |

**覆盖率要求**：7 节点 × 2 分支 = 14 用例，P0 优先级，对应 `agent-task-orchestrator` 模块。

### 4.6 F6 ReAct 循环详细决策（8 决策节点 → 16 测试用例）

**来源**：[11-detail-flow/02-runtime-and-replan-flow.md F6](../11-detail-flow/02-runtime-and-replan-flow.md)

| 决策节点 | 条件表达式 | true 分支用例 | false 分支用例 | 错误码 |
|---|---|---|---|---|
| F6.D1 | `model.response.has_tool_call` | UT-RT-002 转 Act | UT-RT-004 直接 FINISH | - |
| F6.D2 | `tool_call.label == "<tool_call>"` | UT-RT-002 执行工具 | 进入其他标签分支 | - |
| F6.D3 | `tool.execute_success` | UT-RT-003 转 Observe | 工具失败进入重试 | `TOOL_EXECUTION_FAILED` |
| F6.D4 | `observe.requires_more_info` | NEED_MORE_INFO 分支 | 进入自检 | - |
| F6.D5 | `self_check.think_pass` | 自检通过 | 注入反思提示 | `HALLUCINATION_SUSPECTED` |
| F6.D6 | `retry_count <= 2` | UT-RT-005 Reflexion 重试 | UT-RT-006 抛 `MAX_RETRY_EXCEEDED` | `MAX_RETRY_EXCEEDED` |
| F6.D7 | `loop_count <= max_loops (10)` | 继续循环 | UT-RT-007 熔断 | `CIRCUIT_OPEN` |
| F6.D8 | `final_answer.produced` | UT-RT-004 终止循环 | 继续循环 | - |

**覆盖率要求**：8 节点 × 2 分支 = 16 用例，P0 优先级，对应 `agent-runtime` 模块。

### 4.7 F7 Token 水位压缩决策（5 决策节点 → 10 测试用例）

**来源**：[11-detail-flow/02-runtime-and-replan-flow.md F7](../11-detail-flow/02-runtime-and-replan-flow.md)

| 决策节点 | 条件表达式 | true 分支用例 | false 分支用例 | 错误码 |
|---|---|---|---|---|
| F7.D1 | `token_ratio < 0.70` | UT-MEM-001 SAFE 不压缩 | 进入 D2 | - |
| F7.D2 | `token_ratio < 0.85` | UT-MEM-002 WARN 轻度压缩 | 进入 D3 | - |
| F7.D3 | `token_ratio < 0.95` | UT-MEM-003 CRITICAL 中度压缩 | 进入 D4 | - |
| F7.D4 | `token_ratio >= 0.95` | UT-MEM-004 CIRCUIT_BREAK 重度压缩 | - | - |
| F7.D5 | `compress.success` | 压缩后继续执行 | 压缩失败转人工 | `MEMORY_COMPRESS_FAILED` |

**覆盖率要求**：5 节点 × 2 分支 = 10 用例，P0 优先级，对应 `agent-memory` + `agent-runtime` 模块。

### 4.8 F8 工具选择与调用决策（13 决策节点 → 26 测试用例）

**来源**：[11-detail-flow/02-runtime-and-replan-flow.md F8](../11-detail-flow/02-runtime-and-replan-flow.md)

| 决策节点 | 条件表达式 | true 分支用例 | false 分支用例 | 错误码 |
|---|---|---|---|---|
| F8.D1 | `tool_registry.size > 0` | UT-TOOL-002 召回 Top-K | 无可用工具 | `TOOL_NOT_FOUND` |
| F8.D2 | `recall.score >= 0.5` | UT-TOOL-002 选 Top-1 | 召回分数过低重新规划 | `TOOL_NOT_FOUND` |
| F8.D3 | `params.schema_valid` | UT-TOOL-001 通过校验 | UT-HAL-005 拦截非法参数 | `VALIDATION_FAILED` |
| F8.D4 | `risk_level in [R1, R2, R3]` | UT-TOOL-003/004 风险分级 | 未知风险等级 | `VALIDATION_FAILED` |
| F8.D5 | `quota.remaining > 0` | 配额充足 | UT-TOOL-006 配额耗尽 | `RATE_LIMITED` |
| F8.D6 | `risk_level != R3 OR approval.valid` | UT-TOOL-007 沙箱执行 | UT-TOOL-005 缺审批 | `APPROVAL_REQUIRED` |
| F8.D7 | `approval.expire_at > now` | 审批有效 | UT-TOOL-006 审批过期 | `APPROVAL_EXPIRED` |
| F8.D8 | `cost_used + estimated < cost_limit` | 成本预算充足 | UT-ORCH-012 成本超限 | `COST_BUDGET_EXCEEDED` |
| F8.D9 | `call_count < max_calls (20)` | 调用次数充足 | 调用次数熔断 | `COST_BUDGET_EXCEEDED` |
| F8.D10 | `executor.execute_success` | UT-TOOL-007 执行成功 | 进入容错分支 | `TOOL_EXECUTION_FAILED` |
| F8.D11 | `retry_count < max_retries (2)` | 重试执行 | 切换备用工具 | `TOOL_EXECUTION_FAILED` |
| F8.D12 | `result.size <= max_token` | UT-TOOL-008 直接返回 | 触发结果清洗 | - |
| F8.D13 | `audit_log.write_success` | UT-TOOL-010 审计落盘 | 审计失败告警不阻塞 | - |

**覆盖率要求**：13 节点 × 2 分支 = 26 用例，P0 优先级，对应 `agent-tool-engine` 模块。

### 4.9 F9 三级质量校验决策（6 决策节点 → 12 测试用例）

**来源**：[11-detail-flow/03-quality-and-memory-flow.md F9](../11-detail-flow/03-quality-and-memory-flow.md)

| 决策节点 | 条件表达式 | true 分支用例 | false 分支用例 | 错误码 |
|---|---|---|---|---|
| F9.D1 | `risk_level in [low, mid, high]` | UT-QA-008 闲聊仅 L4-1 | UT-QA-001 高风险全三级 | - |
| F9.D2 | `L4_1.hard_check_pass` | UT-QA-001 通过 L4-1 | UT-QA-002 缺来源标签 | `CODE_FORMAT_VIOLATION` |
| F9.D3 | `cosine_sim >= 0.75` | UT-QA-004 通过 L4-2 | UT-QA-005 事实不一致 | `FACT_INCONSISTENCY` |
| F9.D4 | `overall_score >= 0.7` | UT-QA-006 通过 L4-3 | UT-QA-007 终审驳回 | `AUDIT_REJECTED` |
| F9.D5 | `retry_count <= 2` | UT-RT-005 Reflexion 重试 | UT-RT-006 重试耗尽 | `MAX_RETRY_EXCEEDED` |
| F9.D6 | `badcase.write_success` | UT-QA-009 写入 Badcase | 写入失败告警 | - |

**覆盖率要求**：6 节点 × 2 分支 = 12 用例，P0 优先级，对应 `agent-quality` 模块。

### 4.10 F10 幻觉治理六层联动（12 决策节点 → 24 测试用例）

**来源**：[11-detail-flow/03-quality-and-memory-flow.md F10](../11-detail-flow/03-quality-and-memory-flow.md)

| 决策节点 | 条件表达式 | true 分支用例 | false 分支用例 | 错误码 |
|---|---|---|---|---|
| F10.L1.D1 | `scene == high_risk` | UT-HAL-001 路由强模型 | 使用默认模型 | - |
| F10.L1.D2 | `temperature <= 0.1` | 低温度参数 | 调整为低温度 | - |
| F10.L2.D3 | `step.has_claim` | UT-HAL-002 触发自检 | 跳过自检 | - |
| F10.L2.D4 | `recall.size > 0` | 信息充足继续 | UT-HAL-007 拒答 | `INSUFFICIENT_INFO` |
| F10.L3.D5 | `output.has_source_tag` | UT-HAL-004 通过来源校验 | UT-HAL-004 判定幻觉 | `HALLUCINATION_SUSPECTED` |
| F10.L3.D6 | `cosine_sim(output, source) >= 0.75` | UT-HAL-006 交叉验证通过 | 触发 Reflexion | `FACT_INCONSISTENCY` |
| F10.L4.D7 | `L4.overall_pass` | 进入终审 | UT-QA-007 驳回 | `AUDIT_REJECTED` |
| F10.L5.D8 | `tool.params.valid` | 工具调用通过 | UT-HAL-005 拦截 | `VALIDATION_FAILED` |
| F10.L5.D9 | `result.cleaned` | 结果清洗完成 | 触发清洗 | - |
| F10.L6.D10 | `drift.detected` | UT-DRIFT-002 触发漂移监测 | 正常归档 | - |
| F10.L6.D11 | `badcase.category_assigned` | UT-QA-009 Badcase 归集 | 自动分类失败转人工 | - |
| F10.L6.D12 | `metric.recorded` | UT-HAL-008 指标追踪 | 指标写入失败告警 | - |

**覆盖率要求**：12 节点 × 2 分支 = 24 用例，P0 优先级，对应 `agent-quality` + `hallucination-governance` 模块。

### 4.11 F11 漂移监测与纠偏决策（9 决策节点 → 18 测试用例）

**来源**：[11-detail-flow/03-quality-and-memory-flow.md F11](../11-detail-flow/03-quality-and-memory-flow.md)

| 决策节点 | 条件表达式 | true 分支用例 | false 分支用例 | 错误码 |
|---|---|---|---|---|
| F11.D1 | `first_run == true` | UT-DRIFT-001 锚定基准 | 跳过锚定 | - |
| F11.D2 | `tool_call_rate_delta > 0.2` | UT-DRIFT-002 行为漂移 | 正常运行 | - |
| F11.D3 | `success_rate_declining_count >= 5` | UT-DRIFT-003 效果漂移 | 正常运行 | - |
| F11.D4 | `drift_level in [session, task, system]` | 分级纠偏 | 不纠偏 | - |
| F11.D5 | `drift_level == session` | UT-DRIFT-004 注入约束 | 进入任务级 | - |
| F11.D6 | `drift_level == task` | 切换 Prompt 模板 | 进入系统级 | - |
| F11.D7 | `drift_level == system` | UT-DRIFT-005 自动回滚 | 不回滚 | - |
| F11.D8 | `drift_score > 0.8` | UT-DRIFT-007 暂停 Agent | 继续监测 | - |
| F11.D9 | `memory.relevance_declining` | UT-DRIFT-006 记忆漂移 | 正常运行 | - |

**覆盖率要求**：9 节点 × 2 分支 = 18 用例，P0 优先级，对应 `drift-monitor` 模块。

### 4.12 F12 长期记忆写入与召回决策（9 决策节点 → 18 测试用例）

**来源**：[11-detail-flow/03-quality-and-memory-flow.md F12](../11-detail-flow/03-quality-and-memory-flow.md)

| 决策节点 | 条件表达式 | true 分支用例 | false 分支用例 | 错误码 |
|---|---|---|---|---|
| F12.D1 | `task.status == SUCCESS` | UT-MEM-007 触发写入 | 不写入 | - |
| F12.D2 | `memory.has_tags` | UT-MEM-008 重要性评分 | 默认评分 | - |
| F12.D3 | `existing_memory.cosine_sim >= 0.85` | UT-MEM-007 更新合并 | 新增记录 | - |
| F12.D4 | `existing_memory.cosine_sim >= 0.95` | 强去重合并 | 普通更新 | - |
| F12.D5 | `recall.query not empty` | UT-MEM-005 触发召回 | 不召回 | - |
| F12.D6 | `semantic_recall.size > 0` | 语义召回返回结果 | 进入其他路 | - |
| F12.D7 | `keyword_recall.size > 0` | 关键词召回返回结果 | 进入其他路 | - |
| F12.D8 | `time_recall.size > 0` | 时间召回返回结果 | 进入其他路 | - |
| F12.D9 | `tag_recall.size > 0` | UT-MEM-006 标签召回返回结果 | 召回为空 | - |

**覆盖率要求**：9 节点 × 2 分支 = 18 用例，P0 优先级，对应 `agent-memory` 模块。

### 4.13 决策节点覆盖统计

| 流程图 | 决策节点数 | 测试用例数 | P0 用例 | P1 用例 | 已分配模块 |
|---|---|---|---|---|---|
| F1 接入网关 | 6 | 12 | 10 | 2 | agent-gateway |
| F2 意图识别 | 7 | 14 | 12 | 2 | agent-planning |
| F3 任务规划 | 9 | 18 | 16 | 2 | agent-planning |
| F4 子任务调度 | 8 | 16 | 14 | 2 | agent-task-orchestrator |
| F5 动态重规划 | 7 | 14 | 12 | 2 | agent-task-orchestrator |
| F6 ReAct 循环 | 8 | 16 | 14 | 2 | agent-runtime |
| F7 Token 压缩 | 5 | 10 | 8 | 2 | agent-memory + agent-runtime |
| F8 工具调用 | 13 | 26 | 22 | 4 | agent-tool-engine |
| F9 L4 质量校验 | 6 | 12 | 10 | 2 | agent-quality |
| F10 幻觉治理 | 12 | 24 | 20 | 4 | agent-quality + hallucination-governance |
| F11 漂移监测 | 9 | 18 | 16 | 2 | drift-monitor |
| F12 记忆写入召回 | 9 | 18 | 14 | 4 | agent-memory |
| **合计** | **99** | **198** | **168** | **30** | - |

> **说明**：原 [11-detail-flow](../11-detail-flow/01-access-and-planning-flow.md) 共 103 决策节点，本表覆盖 99 个核心决策节点（4 个为辅助/日志类节点，无 true/false 分支测试必要）。

---

## 5. 端到端业务场景测试（3 个示例）

本节详述 3 个端到端业务场景的完整测试流程，覆盖 PRD §一(三) 7 步全链路。其余 7 个 E2E 旅程详见 [user-flow-test-cases.md](user-flow-test-cases.md)。

### 5.1 场景一：行业调研 Agent（L3 多 Agent DAG + RAG + 事实校验）

**来源**：[10-supplement §5.1](../10-supplement/detail-mrd-gap-fill.md)

**测试目标**：验证 L3 复杂任务的多 Agent DAG 调度、RAG 事实约束、L4 三级校验全链路。

**前置条件**：
- 测试租户 `tn_test_003`（金融高风险）已配置
- Agent `ag_industry_research` 已发布 v1
- 知识库 `kb_finance_2024` 已向量化（≥1000 chunks）
- 黄金基准集 `baseline_industry_research` 已锚定

**测试步骤**：

| 步骤 | 角色 | 操作 | 系统响应 | 验证点 | 决策节点 |
|---|---|---|---|---|---|
| 1 | 用户 | `POST /api/v1/tasks` goal="生成 2024 年新能源行业调研报告并发邮件" | 任务 PENDING | taskId 返回，traceId 生成 | F1.D1-D6 |
| 2 | 系统 | F2 评估复杂度：6 维度总分 18（goal=3/execution=3/domain=2/knowledge=3/risk=2/context=2） | L3 复杂任务 | 状态 PENDING→PLANNING | F2.D4-D6 |
| 3 | 系统 | F3 智能规划调用强模型生成 6 节点 DAG（搜集/分析/撰写/审校/发送/归档） | DAG 落库 v1 | 5 维度自检全通过 | F3.D1-D8 |
| 4 | 系统 | F4 批次划分：批次1=[搜集,分析] 并行；批次2=[撰写] 依赖1,2；批次3=[审校,发送] 依赖2 | 3 批次串行调度 | RocketMQ 投递 2 个子任务 | F4.D1-D4 |
| 5 | 系统 | 子任务「搜集」ReAct 循环：调用知识库检索工具（R1）召回 50 chunks | 工具调用成功 | tool_call_log 审计完整 | F6.D1-D8, F8.D1-D13 |
| 6 | 系统 | 子任务「分析」ReAct 循环：基于搜集结果调用数据分析工具（R2） | 工具调用成功 | 中间结果注入上下文 | F6.D1-D8 |
| 7 | 系统 | 批次2「撰写」启动：参数注入搜集+分析结果 | 子任务执行 | 上游 outputs 正确注入 | F4 参数注入 |
| 8 | 系统 | 「撰写」产出报告草稿，触发 F9 L4 三级校验（高风险全三级） | L4 校验启动 | L4-1 来源标签通过 | F9.D1-D4 |
| 9 | 系统 | L4-2 事实一致性：cosine_sim=0.82（≥0.75） | L4-2 通过 | 事实校验通过 | F9.D3 true |
| 10 | 系统 | L4-3 终审：overall=0.85（≥0.7） | L4-3 通过 | 终审通过 | F9.D4 true |
| 11 | 系统 | 批次3「审校」+「发送」并行执行 | 子任务完成 | 审校通过，邮件发送成功 | F4.D4 |
| 12 | 系统 | 任务 SUCCESS，F12 触发长期记忆写入 | 记忆写入 Milvus | importance_score=0.85 | F12.D1-D4 |
| 13 | 系统 | F10 L6 幻觉率指标采集 | 指标写入 ClickHouse | drift_metrics_daily 更新 | F10.L6.D10-D12 |
| 14 | 用户 | `GET /api/v1/tasks/{id}` 查询结果 | SUCCESS | progress=100%, tokenUsed/costUsed 统计正确 | - |

**端到端验证点**：
- ✅ L3 复杂度判定（6 维度评分）
- ✅ AI 规划生成 6 节点 DAG
- ✅ 3 批次并行/串行调度
- ✅ 多 Agent 协作（搜集+分析+撰写+审校+发送）
- ✅ R1/R2 工具调用全链路
- ✅ L4 三级校验全通过
- ✅ 长期记忆写入与重要性评分
- ✅ 漂移指标采集

**预期执行时间**：8-12 分钟（含 Testcontainers 启动）

### 5.2 场景二：代码生成 Agent（AST 解析 + 多轮 ReAct + Reflexion）

**来源**：[10-supplement §5.2](../10-supplement/detail-mrd-gap-fill.md)

**测试目标**：验证代码生成场景的 AST 解析、Neo4j 图谱召回、多轮 ReAct 循环、Reflexion 反思重试。

**前置条件**：
- 测试租户 `tn_test_001` 已配置
- Agent `ag_code_gen` 已发布 v1
- 代码图谱 `codegraph_test_repo` 已建立（Neo4j 含 500 节点）
- Badcase 表已清空

**测试步骤**：

| 步骤 | 角色 | 操作 | 系统响应 | 验证点 | 决策节点 |
|---|---|---|---|---|---|
| 1 | 用户 | 提交代码生成任务"实现一个用户登录接口" | 任务 PENDING | taskId 返回 | F1 |
| 2 | 系统 | F2 评估：L2 中等任务（total=12） | L2 | 单 Agent + 工具循环 | F2.D5 true |
| 3 | 系统 | F6 ReAct Think：调用代码图谱召回相似实现 | Neo4j 召回 Top-5 | 召回结果含登录接口示例 | F6.D1, F8.D1-D2 |
| 4 | 系统 | F6 ReAct Act：调用代码生成工具（R2） | 工具执行成功 | 生成代码草稿 | F6.D2, F8.D4 |
| 5 | 系统 | F6 ReAct Observe：AST 解析生成代码 | AST 解析成功 | 函数签名/参数/返回值解析 | F6.D3 |
| 6 | 系统 | F9 L4-1 硬校验：JSON Schema + 来源标签 | L4-1 失败（缺来源） | 返回 `CODE_FORMAT_VIOLATION` | F9.D2 false |
| 7 | 系统 | F6.D6 Reflexion 重试 1 次 | 注入 REFLECTION 提示 | retry_count=1 | F9.D5 true |
| 8 | 系统 | 重新生成，补充来源标签 | L4-1 通过 | 来源标签 `[来源:codegraph_test_repo]` | F9.D2 true |
| 9 | 系统 | F9 L4-2 事实一致性：cosine_sim=0.78 | L4-2 通过 | 事实校验通过 | F9.D3 true |
| 10 | 系统 | F9 L4-3 终审：overall=0.72 | L4-3 通过 | 终审通过 | F9.D4 true |
| 11 | 系统 | 任务 SUCCESS，代码归档至代码图谱 | Neo4j 新增节点 | CONTAINS/DEFINES 关系建立 | F12.D1-D4 |
| 12 | 用户 | 收到生成代码 + 测试用例 | 响应完整 | tokenUsed 统计正确 | - |

**端到端验证点**：
- ✅ L2 中等任务单 Agent + 工具循环
- ✅ Neo4j 代码图谱召回
- ✅ AST 解析验证
- ✅ L4-1 失败 → Reflexion 重试 → 通过
- ✅ L4 三级校验全通过
- ✅ 生成代码归档至图谱

**预期执行时间**：5-8 分钟

### 5.3 场景三：数据分析 Agent（SQL 工具 + 结果校验 + 重规划）

**来源**：[10-supplement §5.3](../10-supplement/detail-mrd-gap-fill.md)

**测试目标**：验证数据分析场景的 SQL 工具调用（R2）、结果校验失败触发重规划、增量重规划合并 DAG。

**前置条件**：
- 测试租户 `tn_test_001` 已配置
- Agent `ag_data_analysis` 已发布 v1
- 测试数据库 `db_test_analytics` 已初始化（含 10000 条订单数据）
- SQL 工具 `tool_sql_query` 已注册（R2 风险等级）

**测试步骤**：

| 步骤 | 角色 | 操作 | 系统响应 | 验证点 | 决策节点 |
|---|---|---|---|---|---|
| 1 | 用户 | 提交任务"分析 Q3 订单数据并生成可视化报告" | 任务 PENDING | taskId 返回 | F1 |
| 2 | 系统 | F2 评估：L3 复杂任务（total=16） | L3 | 状态 PLANNING | F2.D5 false |
| 3 | 系统 | F3 智能规划生成 4 节点 DAG（查询/分析/可视化/报告） | DAG 落库 v1 | 5 维度自检通过 | F3 |
| 4 | 系统 | F4 批次1：[查询] 子任务启动 | 子任务执行 | SQL 工具调用 | F4.D1-D4 |
| 5 | 系统 | F6 ReAct：调用 SQL 工具查询 Q3 订单 | 工具执行失败（表名错误） | 抛 `TOOL_EXECUTION_FAILED` | F8.D10 false |
| 6 | 系统 | F8.D11 重试 1 次 | 重试仍失败 | retry_count=1 | F8.D11 true |
| 7 | 系统 | F8.D11 重试 2 次耗尽 | 切换备用 SQL 工具 | 备用工具调用成功 | F8.D11 false |
| 8 | 系统 | F9 L4-2 事实一致性：cosine_sim=0.65 | L4-2 失败 | 返回 `FACT_INCONSISTENCY` | F9.D3 false |
| 9 | 系统 | F9.D5 Reflexion 重试 1 次 | 注入 REFLECTION | retry_count=1 | F9.D5 true |
| 10 | 系统 | 重新生成 SQL 查询，结果正确 | L4-2 通过（sim=0.85） | 事实校验通过 | F9.D3 true |
| 11 | 系统 | 批次2：[分析] 子任务执行，但参数注入失败 | 子任务失败 | failed_count=1 | F4.D5 true |
| 12 | 系统 | F5.D3 增量重规划：保留查询结果，重新规划分析节点 | 增量补丁 DAG | dag_version=2 | F5.D3 true |
| 13 | 系统 | F5.I5 合并 DAG 环检测通过 | 合并成功 | 无环 | F5.I5 false |
| 14 | 系统 | 重新执行分析+可视化+报告 | 任务 SUCCESS | progress=100% | F4.End |
| 15 | 用户 | 收到可视化报告 | 响应完整 | 含图表数据 + SQL 查询记录 | - |

**端到端验证点**：
- ✅ SQL 工具调用（R2）
- ✅ 工具失败重试 + 备用工具切换
- ✅ L4-2 事实校验失败 → Reflexion 重试
- ✅ 子任务失败触发增量重规划
- ✅ DAG 合并 + 环检测
- ✅ 重规划后续跑成功

**预期执行时间**：10-15 分钟

---

## 6. 测试数据管理

### 6.1 Testcontainers 容器矩阵

| 容器 | 镜像 | 端口 | 用途 | 启动时机 |
|---|---|---|---|---|
| MySQL 8.0.36 | `mysql:8.0.36` | 3306 | 9 个逻辑库 32 张表 | it/e2e/perf |
| Redis 7.2 | `redis:7.2-alpine` | 6379 | 短期记忆 + 限流桶 + 灰度配置 | it/e2e/perf |
| Milvus 2.4 | `milvusdb/milvus:v2.4.0` | 19530 | 6 个 Collection 向量检索 | it/e2e/perf |
| Neo4j 5.18 | `neo4j:5.18-community` | 7687 | 代码图谱 7 节点 6 关系 | it/e2e |
| ClickHouse 24.3 | `clickhouse/clickhouse-server:24.3` | 8123 | 指标表 + 漂移指标 | it/e2e/perf |
| RocketMQ 5.2 | `apache/rocketmq:5.2.0` | 9876 | 7 个 Topic 事件驱动 | it/e2e |
| Elasticsearch 8.13 | `elasticsearch:8.13.0` | 9200 | 知识库全文检索 | it/e2e |
| Docker-in-Docker | `docker:24-dind` | - | R3 沙箱执行 | it/e2e |

**容器复用策略**：
- 同一测试类共享容器（`@Container static`）
- 跨测试类通过 `@Testcontainers` 单例模式复用（减少启动开销）
- E2E 测试启动全栈（8 容器），集成测试按需启动（2-4 容器）

### 6.2 DDL 初始化与种子数据

复用 `infra/sql/` 目录的 16 个 DDL 脚本，详见 [test-data-and-fixtures.md §2](test-data-and-fixtures.md)。

**初始化顺序**：
1. MySQL：`01-agent-session.sql` → `02-agent-task.sql` → ... → `11-seed-data.sql`
2. Milvus：`01-init-collections.py`
3. Neo4j：`01-init-constraints.cypher` → `02-init-relationships.cypher`
4. Redis：`01-init-data.redis`
5. ClickHouse：`10-clickhouse-metrics.sql`

### 6.3 测试 Fixture 工厂

详见 [test-data-and-fixtures.md §3](test-data-and-fixtures.md)，提供以下 Fixture：

| Fixture | 用途 | 关键方法 |
|---|---|---|
| `AgentFixture` | 构造测试 Agent | `buildDraftAgent()`, `buildPublishedAgent()` |
| `TaskFixture` | 构造测试任务 | `buildL1Task()`, `buildL3Task()`, `buildPlanRequest()` |
| `MemoryFixture` | 构造测试记忆 | `buildEpisodicMemory()`, `buildSemanticMemory()` |
| `ToolFixture` | 构造测试工具 | `buildR1Tool()`, `buildR3Tool()` |
| `DagFixture` | 构造测试 DAG | `buildAcyclicDag()`, `buildCyclicDag()` |
| `TraceFixture` | 构造测试 TraceContext | `buildTraceContext()` |

### 6.4 数据隔离与清理策略

| 隔离维度 | 策略 | 实现 |
|---|---|---|
| 测试类间 | 独立 Testcontainers 容器 | `@Container static` 每类一个实例 |
| 测试方法间 | 事务回滚 | `@Transactional` + `@Rollback(true)` |
| 多租户 | 独立 tenantId | Fixture 注入随机 `tn_test_{uuid}` |
| 数据库表 | `@Sql` 清理脚本 | `classpath:cleanup-{module}.sql` |
| Redis Key | Key 前缀隔离 | `test:{testId}:*`，测试后 `DEL` |
| Milvus Collection | Partition 隔离 | `partition_key={testId}` |

---

## 7. CI/CD 集成

### 7.1 GitHub Actions 工作流

```yaml
# .github/workflows/test-pipeline.yml
name: Test Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  unit-test:
    name: Unit Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'
      - name: Run Unit Tests
        run: mvn test -B -Put
      - name: Upload JaCoCo Report
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: '**/target/site/jacoco/'

  integration-test:
    name: Integration Test
    runs-on: ubuntu-latest
    needs: unit-test
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'
      - name: Run Integration Tests
        run: mvn verify -B -Pit
      - name: Upload Test Report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: integration-test-report
          path: '**/target/failsafe-reports/'

  e2e-test:
    name: E2E Test
    runs-on: ubuntu-latest
    needs: integration-test
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'
      - name: Run E2E Tests
        run: mvn verify -B -Pe2e
      - name: Upload E2E Report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: e2e-test-report
          path: '**/target/failsafe-reports/'

  coverage-check:
    name: Coverage Gate
    runs-on: ubuntu-latest
    needs: [unit-test, integration-test]
    steps:
      - uses: actions/checkout@v4
      - name: Download JaCoCo Report
        uses: actions/download-artifact@v4
        with:
          name: jacoco-report
      - name: Check Coverage Threshold
        run: |
          # 解析 JaCoCo XML，校验阈值
          python3 scripts/check-coverage.py \
            --line-threshold 80 \
            --branch-threshold 70 \
            --p0-line-threshold 85
```

### 7.2 测试报告与覆盖率

**报告类型**：

| 报告 | 格式 | 工具 | 用途 |
|---|---|---|---|
| 测试执行报告 | JUnit XML | Surefire/Failsafe | CI 解析、趋势追踪 |
| 覆盖率报告 | HTML + CSV + XML | JaCoCo | 覆盖率可视化、门禁判定 |
| 测试摘要报告 | Markdown | 自定义脚本 | 人工阅读、评审记录 |
| 性能基准报告 | HTML + JSON | JMH | 性能趋势追踪 |

**Markdown 摘要报告模板**（每次 CI 产出）：

```markdown
# 测试摘要报告 #{BUILD_NUMBER}

## 1. 构建信息
- 分支：{BRANCH}
- 提交：{COMMIT_SHA}
- 触发者：{TRIGGER_USER}
- 执行时间：{DURATION}

## 2. 测试结果汇总
| 测试层 | 总数 | 通过 | 失败 | 跳过 | 通过率 |
|---|---|---|---|---|---|
| 单元测试 | {n} | {n} | {n} | {n} | {%} |
| 集成测试 | {n} | {n} | {n} | {n} | {%} |
| 端到端测试 | {n} | {n} | {n} | {n} | {%} |

## 3. 覆盖率
| 维度 | 目标 | 实际 | 状态 |
|---|---|---|---|
| 行覆盖 | 80% | {%} | ✅/❌ |
| 分支覆盖 | 70% | {%} | ✅/❌ |
| P0 模块行覆盖 | 85% | {%} | ✅/❌ |

## 4. 质量门禁
- 单元测试通过率：✅/❌
- 覆盖率达标：✅/❌
- 契约测试：✅/❌
- 整体结论：✅ 可合并 / ❌ 阻塞合并
```

### 7.3 失败重试与不稳定测试治理

| 策略 | 说明 |
|---|---|
| 标记隔离 | `@Tag("flaky")` 隔离不稳定测试，不阻塞主流程 |
| 重试机制 | `@RetryableTest(maxAttempts=3)` 自动重试最多 3 次 |
| 根因分析 | 连续 3 次失败必须提 Issue 排查根因 |
| 删除门槛 | Flaky 超过 7 天未修复，移出测试集并记录 |

### 7.4 质量门禁

CI 流水线设置以下质量门禁，任一不通过即阻塞合并：

| 门禁 | 阈值 | 阻塞级别 | 工具 |
|---|---|---|---|
| 单元测试通过率 | 100% | 阻塞 | Surefire |
| 单元测试数量 | 不减少（除非删除对应功能） | 阻塞 | Surefire |
| 行覆盖率 | ≥ 80% | 阻塞 | JaCoCo |
| 分支覆盖率 | ≥ 70% | 阻塞 | JaCoCo |
| P0 模块覆盖率 | ≥ 85% | 阻塞 | JaCoCo |
| 契约测试 | 100% 通过 | 阻塞 | gRPC Test |
| 架构规则 | 100% 通过 | 阻塞 | ArchUnit |
| 重复代码 | < 3% | 告警 | PMD CPD |

---

## 8. 验收标准

### 8.1 单元测试验收

| 验收项 | 标准 | 验证方式 |
|---|---|---|
| 通过率 | 100% | `mvn test` 全绿 |
| 行覆盖率 | ≥ 80%（整体），P0 模块 ≥ 85% | JaCoCo 报告 |
| 分支覆盖率 | ≥ 70%（整体），P0 模块 ≥ 80% | JaCoCo 报告 |
| 命名规范 | 100% 符合 `Should_..._When_...` | ArchUnit 规则 |
| 可追溯性 | 100% 标注来源文档（F1~F12/PRD/doc） | Grep `来源：` |
| 决策节点覆盖 | F1~F12 共 99 决策节点 true/false 双分支必测 | 决策矩阵覆盖表 |

### 8.2 集成测试验收

| 验收项 | 标准 | 验证方式 |
|---|---|---|
| gRPC 契约测试 | 21 个 RPC 方法全覆盖 | 契约测试报告 |
| REST 切片测试 | Controller 全覆盖 | `@WebMvcTest` |
| 错误码 | doc 02 §0.5 所有错误码均有触发用例 | 错误码交叉索引表 |
| 状态机 | 10 状态合法/非法流转全覆盖 | 状态机流转矩阵 |
| 集成链路 | 10 条集成链路全测试 | Testcontainers 全绿 |

### 8.3 端到端测试验收

| 验收项 | 标准 | 验证方式 |
|---|---|---|
| 旅程覆盖 | ≥ 10 个核心用户旅程（已覆盖 10 个） | E2E 测试报告 |
| 全链路 | 覆盖 PRD §一(三) 7 步全链路 | 旅程覆盖矩阵 |
| 中间状态验证 | 数据库、Redis、消息队列均有断言 | 测试代码审查 |
| 记忆沉淀验证 | 任务完成后长期记忆正确写入 | Milvus 查询断言 |
| 审计完整性 | 全量调用留痕可追溯 | audit_log 表查询 |

### 8.4 性能基线验收

| 验收项 | 基线 | 验证方式 |
|---|---|---|
| 意图识别 P95 | ≤ 200ms | JMH 基准测试 |
| 单步工具调用 P95 | ≤ 800ms | JMH 基准测试 |
| DAG 调度延迟 | < 100ms | JMH 基准测试 |
| 向量召回延迟（Top-10） | < 50ms | JMH 基准测试 |
| Token 压缩延迟 | 轻<200ms/中<500ms/重<1s | JMH 基准测试 |
| 并发承载 | ≥ 200 Agent 实例/节点 | Gatling 压测 |

### 8.5 治理与合规验收

| 验收项 | 标准 | 验证方式 |
|---|---|---|
| R3 工具越权拦截率 | 100% | 安全测试用例全绿 |
| 审计日志覆盖率 | 全量调用留痕 | audit_log 表抽样校验 |
| 幻觉治理六层 | 每层至少 2 条测试 | F10 决策节点覆盖表 |
| 漂移监测四层 | 每层至少 2 条测试 | F11 决策节点覆盖表 |
| 多租户数据隔离 | 100% 不串 | 多租户隔离测试 |

### 8.6 TDD 红绿循环验收

| 验收项 | 标准 | 验证方式 |
|---|---|---|
| 红绿循环执行 | 每个 Task 必须有红→绿记录 | TDD 红绿循环记录文档 |
| 测试先行 | 实现代码前先有失败测试 | git 提交历史审查 |
| 重构阶段 | 绿后必有一次重构 commit | git 提交历史审查 |
| 测试独立性 | 每个 `@Test` 独立可运行 | `mvn test -Dtest=X#y` 验证 |

---

## 9. 附录

### 9.1 命名规范

#### 测试类命名

| 类型 | 命名规范 | 示例 |
|---|---|---|
| 单元测试 | `{ClassUnderTest}Test` | `ComplexityScorerTest` |
| gRPC 契约测试 | `{Service}{Method}ContractTest` | `ToolGatewayInvokeContractTest` |
| 集成测试 | `{ChainName}IntegrationTest` | `TaskPlanDagIntegrationTest` |
| 端到端测试 | `{ScenarioName}E2ETest` | `IndustryResearchE2ETest` |
| 性能测试 | `{Operation}PerfTest` | `DagSchedulePerfTest` |
| 回归测试 | `{Category}RegressionTest` | `HallucinationRegressionTest` |

#### 测试方法命名

统一采用 `Should_{期望行为}_When_{前置条件}` 格式：

| 场景 | 命名示例 |
|---|---|
| 正常路径 | `Should_ReturnL3_When_AllDimensionsHigh` |
| 边界值 | `Should_ReturnL2_When_TotalScoreEquals14` |
| 异常路径 | `Should_ThrowDagCycleDetected_When_DagHasCycle` |
| 状态流转 | `Should_TransitToRunning_When_L1TaskSubmitted` |
| 错误码 | `Should_Return401Unauthenticated_When_JwtInvalid` |

#### 测试用例 ID 规范

采用 `{层级}-{模块}-{序号}` 格式：

| 前缀 | 层级 | 示例 |
|---|---|---|
| UT | 单元测试 | `UT-PROTO-001`、`UT-GATE-012` |
| FT | 功能测试 | `FT-SESS-003`、`FT-TOOL-008` |
| E2E | 端到端 | `E2E-01`、`E2E-10` |
| PERF | 性能测试 | `PERF-DAG-001` |
| SEC | 安全测试 | `SEC-AUTH-001` |

### 9.2 错误码与决策节点交叉索引

| 错误码 | HTTP | 触发决策节点 | 测试用例 |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | F1.D2 false | UT-GATE-003 |
| `RATE_LIMITED` | 429 | F1.D3 true, F8.D5 false | UT-GATE-005, UT-TOOL-006 |
| `CONTENT_BLOCKED` | 403 | F1.D4 true | UT-GATE-006 |
| `VALIDATION_FAILED` | 400 | F8.D3 false, F10.L5.D8 false | UT-TOOL-001, UT-HAL-005 |
| `TASK_NOT_FOUND` | 404 | F4.D1 false | FT-SESS-005 |
| `TASK_STATUS_CONFLICT` | 409 | doc 08 §1 | UT-ORCH-004 |
| `DAG_CYCLE_DETECTED` | 409 | F3.I3 true, F5.I5 true | UT-ORCH-005, FT-REPLAN-006 |
| `AGENT_NOT_FOUND` | 404 | F4.D2 false | UT-ORCH-008 |
| `APPROVAL_REQUIRED` | 403 | F8.D6 false | UT-TOOL-005 |
| `APPROVAL_EXPIRED` | 403 | F8.D7 true | UT-TOOL-006 |
| `COST_BUDGET_EXCEEDED` | 429 | F4.D9 true, F8.D8 false | UT-ORCH-012, FT-TOOL-006 |
| `TOOL_EXECUTION_FAILED` | 500 | F8.D10 false | FT-TOOL-005 |
| `TOOL_NOT_FOUND` | 404 | F8.D1 false, F8.D2 false | UT-TOOL-002 |
| `MAX_RETRY_EXCEEDED` | 429 | F6.D6 false, F9.D6 false | UT-RT-006 |
| `CIRCUIT_OPEN` | 503 | F6.D7 true | UT-RT-007 |
| `CODE_FORMAT_VIOLATION` | 422 | F9.D2 false | UT-QA-002, UT-QA-003 |
| `FACT_INCONSISTENCY` | 422 | F9.D3 false, F10.L3.D6 false | UT-QA-005 |
| `AUDIT_REJECTED` | 422 | F9.D4 false, F10.L4.D7 false | UT-QA-007 |
| `HALLUCINATION_SUSPECTED` | 422 | F10.L3.D5 false | UT-HAL-004 |
| `REPLAN_EXHAUSTED` | 503 | F5.D4 true | UT-ORCH-011 |
| `PLAN_VALIDATION_FAILED` | 422 | F3.D4-D8 false, F5.D6 false | UT-PLAN-010 |
| `PLAN_GENERATION_FAILED` | 422 | F3.D2 false | - |
| `MODEL_GATEWAY_ERROR` | 500 | F6.D1 false | UT-MG-008 |
| `MODEL_TIMEOUT` | 504 | F8.D11 false | UT-MG-010 |
| `INSUFFICIENT_INFO` | 422 | F10.L2.D4 false | UT-HAL-007 |
| `TASK_TIMEOUT` | 504 | F4.D7 true | UT-ORCH-007 |
| `SESSION_NOT_FOUND` | 404 | F1.D5 false | FT-SESS-005 |
| `INTERNAL` | 500 | 兜底 | - |

### 9.3 测试用例统计汇总

| 测试层 | 文档 | 用例数 | P0 | P1 | P2 |
|---|---|---|---|---|---|
| 单元测试 | [unit-test-cases.md](unit-test-cases.md) | 127 | 82 | 39 | 6 |
| 功能测试 | [functional-test-cases.md](functional-test-cases.md) | 74 | 53 | 18 | 3 |
| 端到端测试 | [user-flow-test-cases.md](user-flow-test-cases.md) | 10 旅程 | - | - | - |
| F1~F12 决策节点 | 本文 §4 | 198 | 168 | 30 | - |
| **去重后总计** | - | **~280** | **~190** | **~70** | **~20** |

> **说明**：F1~F12 决策节点用例与 unit/functional 用例有部分重叠（同一决策节点可能在多处引用），去重后实际测试方法数约 280 条。

### 9.4 测试文档索引

| 文档 | 内容 | 路径 |
|---|---|---|
| 测试策略 | 测试目标、原则、金字塔、工具、覆盖率、CI | [test-strategy.md](test-strategy.md) |
| 测试计划（本文） | TDD 方法论、F1~F12 决策节点用例、3 个 E2E 场景 | [test-plan.md](test-plan.md) |
| 单元测试用例 | 13 个模块的单元测试用例清单 | [unit-test-cases.md](unit-test-cases.md) |
| 功能测试用例 | 13 个功能域的功能测试用例清单 | [functional-test-cases.md](functional-test-cases.md) |
| 用户流程测试 | 10 个端到端用户旅程 | [user-flow-test-cases.md](user-flow-test-cases.md) |
| 测试数据与 Fixture | 测试数据策略、Fixture 示例、Testcontainers 配置 | [test-data-and-fixtures.md](test-data-and-fixtures.md) |
| TDD 红绿循环记录 | 4 个已实现模块的 TDD 执行记录 | [tdd-red-green-records.md](tdd-red-green-records.md) |
| TDD 独立审核流程 | 审核角色、检查清单、评分标准、工作流 | [tdd-audit-framework.md](tdd-audit-framework.md) |
| TDD 首轮审核报告 | 首轮审核结果、问题清单、整改建议 | [tdd-audit-report-v1.md](tdd-audit-report-v1.md) |

### 9.5 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-06-27 | 初始版本，覆盖 §0-3 | AgentForge 测试团队 |
| v1.1 | 2026-06-27 | 补全 §3.2.4-§3.5（其余服务矩阵）、§4（F1~F12 决策节点用例 198 条）、§5（3 个 E2E 场景）、§6（测试数据）、§7（CI/CD）、§8（验收标准）、§9（附录） | AgentForge 测试团队 |