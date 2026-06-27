# AgentForge 智能体平台 测试策略与总体规划

> 文档版本：v1.0 | 更新日期：2026-06-27 | 文档定位：**测试策略 + 覆盖率目标 + CI 集成 + 报告规范**
>
> 适用范围：AgentForge 企业级多智能体平台后端全量核心服务（13 个微服务）的测试总体规划。
>
> 依赖文档：
> - [PRD.md](../../PRD.md) — 整体需求与设计原则
> - [detail-MRD.md](../../detail-MRD.md) — 详细需求与执行链路
> - [00-overview/tech-stack-and-architecture.md](../00-overview/tech-stack-and-architecture.md) — 微服务清单、ADR、通信矩阵
> - [02-api/api-specification.md](../02-api/api-specification.md) — REST/gRPC/SSE/RocketMQ 接口契约与错误码
> - [08-flow/state-machines-and-sequences.md](../08-flow/state-machines-and-sequences.md) — 10 状态 + 12 时序图
> - [11-detail-flow/01-access-and-planning-flow.md](../11-detail-flow/01-access-and-planning-flow.md) — F1~F4 决策流程图
> - [11-detail-flow/02-runtime-and-replan-flow.md](../11-detail-flow/02-runtime-and-replan-flow.md) — F5~F8 决策流程图
> - [11-detail-flow/03-quality-and-memory-flow.md](../11-detail-flow/03-quality-and-memory-flow.md) — F9~F12 决策流程图
> - [test-plan.md](test-plan.md) — 已有 TDD 测试计划（本文为其策略总纲的展开）

---

## 1. 测试目标与原则

### 1.1 测试目标

AgentForge 平台的测试体系围绕以下核心目标构建：

| 目标编号 | 目标 | 衡量标准 | 关联文档 |
|---|---|---|---|
| G1 | 保障 13 个微服务公共接口的行为正确性 | gRPC 21 个 RPC 方法契约测试全绿 | doc 02-api |
| G2 | 验证 F1~F12 共 103 个决策节点的分支覆盖 | 每个决策节点 true/false 双分支必测 | doc 11-detail-flow |
| G3 | 确保任务状态机 10 状态流转合法 | 非法流转抛 `TASK_STATUS_CONFLICT` | doc 08 §1 |
| G4 | 守住安全合规底线 | R3 高危工具越权拦截率 100% | PRD §二(二)3 |
| G5 | 控制性能基线不退化 | 关键操作 P95 延迟不超基线 1.5 倍 | doc 00 §1.2 |
| G6 | 防止已修复缺陷复发 | Badcase 回归测试通过率 100% | PRD §三(三)4 |

### 1.2 测试原则

本平台遵循五项测试原则，所有测试用例的编写与评审必须符合：

#### 原则 1：行为驱动（Behavior-Driven）

测试验证的是**类的公共方法对外暴露的可观察行为**，而非内部实现细节。测试用例命名采用 `Should_{期望行为}_When_{前置条件}` 格式，关注输入输出契约，不依赖私有方法或内部状态。

- **正确做法**：`Should_ReturnL3_When_RiskDimensionIs3`（验证 `ComplexityScorer.score()` 的输出）
- **错误做法**：`Should_CallInternalMethod_When_Scored`（依赖内部实现，重构即碎）

#### 原则 2：公共接口测试（Test Public API）

只对类的 `public` 方法编写测试。`private`/`protected` 方法通过公共入口间接测试。`package-private` 方法仅在同类包测试中可访问，不跨包测试。

#### 原则 3：垂直切片 TDD（Vertical Slice TDD）

每个功能特性按"垂直切片"开发，一个切片贯穿 Controller → Service → Repository → DB 全链路。先写切片内最外层失败的测试（红），再逐层实现直到测试通过（绿），最后重构。禁止先写实现再补测试。

#### 原则 4：单一职责用例

每个测试方法只验证一个行为点。一个 `@Test` 方法只包含一个断言主题（允许有多个辅助断言，但核心断言只有一个）。测试方法体遵循 Given-When-Then 三段式结构。

#### 原则 5：可追溯性（Traceability）

每条测试用例必须标注来源设计文档（如"来源：doc 11-detail-flow F5.D3"），便于需求变更时定位受影响的测试集。决策节点的 true/false 分支必须分别有独立用例覆盖。

---

## 2. 测试金字塔

AgentForge 采用经典测试金字塔模型，按 70:20:10 的比例分配测试投入：

```
                    ▲
                   ╱ ╲          端到端测试（E2E）10%
                  ╱   ╲         ─ 10+ 用户旅程，验证全链路业务场景
                 ╱     ╲        ─ 启动全栈 Testcontainers
                ╱       ╲       ─ 执行时间 5~15min
               ╱         ╲
              ╱           ╲    功能/集成测试（Functional + Integration）20%
             ╱             ╲   ─ gRPC 契约测试 + 跨模块集成
            ╱               ╲  ─ Testcontainers 启真实中间件
           ╱                 ╲ ─ 执行时间 2~5min
          ╱                   ╲
         ╱_____________________╲ 单元测试（Unit）70%
                                  ─ 每个类的公共方法行为测试
                                  ─ Mock 依赖，纯逻辑验证
                                  ─ 执行时间 < 30s
```

### 2.1 各层比例与数量规划

| 测试层 | 占比 | 执行时间 | 数量目标 | 主要工具 | 验证内容 |
|---|---|---|---|---|---|
| 单元测试 | 70% | < 30s | ≥ 100 条 | JUnit 5 + Mockito 5 + AssertJ 3 | 类级行为、状态机、算法、边界、异常 |
| 功能测试 | 13% | 2~5min | ≥ 60 条 | Spring Boot Test + gRPC Test | gRPC 契约、REST 切片、业务功能链路 |
| 集成测试 | 7% | 2~5min | 含在功能测试内 | Testcontainers | 跨服务协作、真实中间件 |
| 端到端测试 | 10% | 5~15min | ≥ 10 旅程 | Spring Boot Test 全栈 | 完整用户旅程、全链路验证 |
| **总计** | 100% | < 20min | ≥ 170 条 | — | — |

### 2.2 金字塔反模式规避

| 反模式 | 表现 | 规避措施 |
|---|---|---|
| 冰淇淋模型 | E2E 过多，单元过少 | 单元测试占比不得低于 65% |
| 沙漏模型 | 集成层断裂 | 核心服务必须有集成测试 |
| 测试盲区 | DTO/枚举/异常无测试 | agent-proto/agent-common 必须有序列化与边界测试 |

---

## 3. 测试分层定义

### 3.1 单元测试（Unit Test）

**定义**：在 JVM 进程内、不依赖外部中间件、Mock 所有跨服务依赖的测试。

**范围**：

| 测试对象 | 典型测试类 | 关键行为 |
|---|---|---|
| Domain Service（纯逻辑） | `ComplexityScorerTest` | 6 维度评分计算逻辑 |
| 工具类 | `TokenEstimatorTest` | Token 估算准确性（中文 1.7 倍） |
| 状态机 | `TaskStateMachineTest` | 10 状态合法/非法流转 |
| 算法 | `DagCycleDetectorTest` | DAG 环检测（DFS 三色标记） |
| 策略类 | `ReplanModeSelectorTest` | 增量/全量重规划模式选择 |
| DTO/VO 校验 | `TaskSchemaValidatorTest` | Bean Validation 触发 |
| 异常体系 | `ErrorCodeRegistryTest` | 错误码与 HTTP 状态映射 |
| 枚举 | `RiskLevelTest` | R1/R2/R3 转换 |

**Mock 策略**：
- 跨服务依赖（gRPC Client、RocketMQ Producer）：Mockito `@Mock`
- 数据库访问（Mapper/Repository）：Mockito `@Mock` 返回构造的 Fixture
- Redis 客户端：Mockito `@Mock` 或 Fake 实现
- 时间源：`Clock` 注入，测试中固定时间

**详细用例清单**：见 [unit-test-cases.md](unit-test-cases.md)

### 3.2 功能测试（Functional Test）

**定义**：验证单个 gRPC 服务接口契约或单个业务功能是否符合设计文档定义，包括请求/响应消息结构、错误码、超时、流式输出。

**范围**（覆盖 7 个 gRPC 服务、21 个 RPC 方法 + REST API）：

| 类别 | 测试内容 | 来源文档 |
|---|---|---|
| gRPC 契约 | 21 个 RPC 方法的请求/响应契约 | doc 02-api §3~§9 |
| REST 切片 | gateway/session/repo 的 Controller HTTP 契约 | doc 02-api §1~§2 |
| 业务功能 | 多端接入、会话管理、DAG 规划、工具调用等 | PRD §一(三) |
| 治理功能 | L4 校验、幻觉治理、漂移监测、版本灰度 | PRD §四、§五 |

**详细用例清单**：见 [functional-test-cases.md](functional-test-cases.md)

### 3.3 集成测试（Integration Test）

**定义**：跨模块、跨服务的集成测试，使用 Testcontainers 启动真实中间件，验证服务间协作契约。

**关键集成链路**：

| 集成链路 | 验证目标 | 容器依赖 |
|---|---|---|
| gateway → session → orchestrator | 鉴权 → 会话 → 任务提交全链路 | MySQL + Redis |
| orchestrator → planning → dag | 复杂度评估 → 规划 → DAG 落库 | MySQL |
| orchestrator → runtime → model | 任务分发 → ReAct 循环 → 模型调用 | MySQL + Redis |
| runtime → tool-engine → sandbox | 工具调用 → 沙箱执行 → 结果清洗 | MySQL + Docker |
| runtime → memory → Milvus | 记忆召回 → 向量检索 → 重排 | Milvus + MySQL |

### 3.4 用户流程测试（End-to-End）

**定义**：从用户输入到最终输出的完整业务流程测试，覆盖 PRD 第一章第三节定义的 7 步全链路。

**详细用例清单**：见 [user-flow-test-cases.md](user-flow-test-cases.md)

### 3.5 性能测试（Performance Baseline）

**定义**：建立关键操作的延迟基线，监控性能退化。

| 性能指标 | 基线 | 来源 |
|---|---|---|
| DAG 调度延迟（单批次） | < 100ms | doc 00 §1.2 |
| 向量召回延迟（Top-10） | < 50ms | doc 00 §1.2 |
| Token 压缩延迟（轻/中/重） | < 200ms / 500ms / 1s | PRD §二(一)3 |
| 意图识别 P95 | ≤ 200ms | doc 00 §1.2 |
| 单步工具调用 P95 | ≤ 800ms | doc 00 §1.2 |
| Agent 实例并发承载 | ≥ 200/实例 | doc 00 §1.2 |

**性能测试失败阈值**：
- 基线延迟超过 1.5 倍 → P3 告警，不阻塞
- 基线延迟超过 2 倍 → P1 告警，阻塞合并
- 并发承载低于 80% → 阻塞合并

### 3.6 安全测试（Security Test）

**定义**：验证平台安全风控与合规体系，覆盖 PRD §二(二) 与 doc 09 §1 的安全要求。

| 安全测试项 | 验证内容 | 来源 |
|---|---|---|
| 鉴权绕过 | JWT 伪造/过期/API-Key 无效必拒 | F1.D2 |
| 越权拦截 | RBAC+ABAC 写操作拦截 | PRD §二(二)3 |
| R3 高危工具 | 沙箱执行 + 双人复核 + 限时授权 | F8 + doc 08 §2 |
| 内容安全 | Prompt 注入/敏感词拦截 | F1.D4 |
| 数据隔离 | 多租户数据不串 | PRD §二(二)3 |
| 审计留痕 | 全量调用审计落盘 | PRD §二(二)3 |

---

## 4. 测试工具选型

### 4.1 工具栈锁定

| 工具 | 版本 | 用途 | 引入位置 |
|---|---|---|---|
| JUnit 5 | 5.10.2 | 测试框架，`@Test`/`@ParameterizedTest`/`@DisplayName` | 父 POM |
| Mockito 5 | 5.11.0 | Mock 依赖，`@Mock`/`@InjectMocks`/`when().thenReturn()` | 各服务 pom.xml |
| AssertJ 3 | 3.25.3 | 流式断言，`assertThat(actual).isEqualTo(expected)` | 各服务 pom.xml |
| Spring Boot Test | 3.2.4 | `@SpringBootTest`/`@WebMvcTest`/`@DataJpaTest` 切片测试 | 各服务 pom.xml |
| Testcontainers | 1.19.7 | Docker 化测试基础设施（MySQL/Milvus/Redis/Neo4j） | agent-test-infra 模块 |
| grpc-spring-boot-starter | 3.1.0 | gRPC 服务端/客户端测试 | agent-test-infra |
| Spring Cloud Contract | 4.1.0 | 消费者驱动契约测试（CDC） | agent-test-infra |
| ArchUnit | 1.3.0 | 架构规则测试（包依赖、分层约束） | agent-test-infra |
| JaCoCo | 0.8.11 | 覆盖率统计 | 父 POM |
| WireMock | 3.5.1 | HTTP 桩件（模拟第三方 API） | agent-test-infra |
| Awaitility | 4.2.1 | 异步测试断言（轮询等待） | agent-test-infra |

### 4.2 测试基础设施模块

新增 `agent-test-infra` 模块（Maven 多模块），集中管理跨服务复用的测试基础设施：

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

### 4.3 测试框架使用约定

| 场景 | 使用方式 | 示例 |
|---|---|---|
| 纯逻辑单元测试 | JUnit 5 + Mockito + AssertJ | `ComplexityScorerTest` |
| 参数化测试 | `@ParameterizedTest` + `@CsvSource`/`@EnumSource` | 状态机流转矩阵 |
| Spring 切片测试 | `@WebMvcTest`/`@DataJpaTest`/`@JsonTest` | Controller/Repository/序列化 |
| gRPC 契约测试 | `@GrpcTest` + 嵌入式 gRPC 服务 | `ToolGatewayInvokeContractTest` |
| 集成测试 | `@SpringBootTest` + `@Testcontainers` | `TaskPlanDagIntegrationTest` |
| 异步断言 | `Awaitility.await().until()` | RocketMQ 消费验证 |
| 架构规则 | `ArchUnit` 规则 | 包依赖分层约束 |

---

## 5. 测试覆盖率目标

### 5.1 总体覆盖率目标

| 维度 | 目标 | 度量方式 |
|---|---|---|
| 行覆盖（Line） | ≥ 80% | `INSTRUCTION_COVERED / (INSTRUCTION_MISSED + INSTRUCTION_COVERED)` |
| 分支覆盖（Branch） | ≥ 70% | `BRANCH_COVERED / (BRANCH_MISSED + BRANCH_COVERED)` |
| 方法覆盖（Method） | ≥ 85% | 方法被调用比例 |
| 类覆盖（Class） | ≥ 90% | 类被测试比例 |

### 5.2 分模块覆盖率目标

按 P0~P3 优先级分层设定覆盖率门槛：

| 优先级 | 模块 | 行覆盖目标 | 分支覆盖目标 |
|---|---|---|---|
| P0 | agent-runtime / task-orchestrator / planning-service / tool-engine | ≥ 85% | ≥ 80% |
| P0 | agent-gateway（鉴权与路由） | ≥ 85% | ≥ 80% |
| P0 | risk-control（安全合规） | ≥ 85% | ≥ 80% |
| P1 | memory-service / model-gateway / quality-service | ≥ 80% | ≥ 75% |
| P2 | session-service / agent-repo / knowledge-service | ≥ 75% | ≥ 70% |
| P3 | observability / agent-proto / agent-common | ≥ 70% | ≥ 65% |
| **整体加权** | — | **≥ 80%** | **≥ 70%** |

### 5.3 覆盖率排除项

以下内容不计入覆盖率统计（JaCoCo `excludes` 配置）：

- `*Configuration` 类（Spring 配置类）
- `*Application` 主入口类（main 方法）
- `dto/`、`vo/`、`entity/` 包（纯 POJO，由 Bean Validation 注解保证）
- `generated/` 包（protobuf 生成代码）
- `constant/` 包（常量定义）

### 5.4 关键模块强化覆盖

以下模块为幻觉治理与安全关键路径，覆盖率必须达到 90% 以上：

| 模块 | 关键类 | 强化理由 | 覆盖目标 |
|---|---|---|---|
| task-orchestrator | `TaskStateMachine`、`Replanner` | 状态流转错误导致任务失控 | ≥ 90% |
| tool-engine | `ToolGateway`、`RiskClassifier` | R3 工具误放行即安全事故 | ≥ 90% |
| planning-service | `ComplexityScorer`、`PlanValidator` | 规划错误导致执行路径错误 | ≥ 90% |
| quality-service | `L4AuditValidator` | 质量门禁误判放行幻觉 | ≥ 90% |
| agent-runtime | `ReActLoop`、`TokenWatermarkMonitor` | 循环失控导致成本失控 | ≥ 90% |

---

## 6. 测试数据策略

### 6.1 数据来源策略

| 数据类型 | 策略 | 说明 |
|---|---|---|
| 单元测试数据 | 合成数据（Fixture 工厂） | 程序构造，可控可复现 |
| 集成测试数据 | Testcontainers + DDL 种子 | 每次启动全新库，执行 `infra/sql/11-seed-data.sql` |
| E2E 测试数据 | 合成 + 脱敏生产样本 | 核心场景用合成，边界场景用脱敏样本 |
| 性能测试数据 | 批量生成脚本 | 按量级生成，如 10 万条记忆向量 |

**禁止**：在测试中直接连接生产数据库或使用未脱敏的生产数据。

### 6.2 测试生命周期管理

采用 `@TestInstance(PER_CLASS)` + `@BeforeEach` 组合管理测试数据：

- `@TestInstance(Lifecycle.PER_CLASS)`：每个测试类共享一个实例，减少重复初始化开销
- `@BeforeEach`：每个测试方法前重置数据状态，保证用例隔离
- `@AfterEach`：清理测试产生的副作用（如 Redis Key、临时文件）
- `@BeforeAll`：类级别一次性初始化（如加载种子数据）
- `@AfterAll`：类级别清理（如销毁容器）

### 6.3 数据隔离策略

| 隔离维度 | 策略 | 实现 |
|---|---|---|
| 测试类间隔离 | 每个测试类独立 Testcontainers 容器 | `@Container static` |
| 测试方法间隔离 | 每个方法独立事务回滚 | `@Transactional` + `@Rollback` |
| 多租户隔离 | 每个测试用独立 `tenantId` | Fixture 注入随机 tenantId |
| 数据库表清理 | `@Sql` 注解执行清理脚本 | `classpath:cleanup.sql` |

### 6.4 Testcontainers 配置

各中间件的容器配置统一封装在 `agent-test-infra` 模块，详见 [test-data-and-fixtures.md](test-data-and-fixtures.md)。

---

## 7. CI 集成方案

### 7.1 Maven 构建阶段划分

```
mvn clean
  ├── validate          # 校验项目正确性
  ├── compile           # 编译主代码
  ├── test              # 单元测试（跳过 it/e2e/perf）
  │   └── mvn surefire  # 执行 *Test.java
  ├── package           # 打包
  ├── integration-test  # 集成测试
  │   └── mvn failsafe  # 执行 *IT.java
  └── verify           # 校验质量门禁
      └── JaCoCo report # 覆盖率报告
```

### 7.2 Maven Profile 隔离

通过 Maven Profile 隔离不同测试层级，控制执行范围：

| Profile | 执行命令 | 包含的测试 | 排除的测试 |
|---|---|---|---|
| ut（默认） | `mvn test` | `@Tag("ut")` 或无标签 | it, e2e, perf |
| it | `mvn verify -Pit` | ut + `@Tag("it")` | e2e, perf |
| e2e | `mvn verify -Pe2e` | ut + it + `@Tag("e2e")` | perf |
| perf | `mvn verify -Pperf` | `@Tag("perf")` | e2e |
| all | `mvn verify -Pall` | 全部 | 无 |

**JUnit 5 Tag 标注规范**：

| Tag | 用途 | 执行阶段 |
|---|---|---|
| `ut` | 单元测试 | PR 每次 push |
| `it` | 集成测试 | PR 合并前 |
| `e2e` | 端到端测试 | 每日定时 / 发版前 |
| `perf` | 性能基准 | 每周定时 / 发版前 |

### 7.3 质量门禁

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

### 7.4 不稳定测试治理（Flaky Test）

| 策略 | 说明 |
|---|---|
| 标记隔离 | `@Tag("flaky")` 隔离不稳定测试，不阻塞主流程 |
| 重试机制 | `@RetryableTest(maxAttempts=3)` 自动重试最多 3 次 |
| 根因分析 | 连续 3 次失败必须提 Issue 排查根因 |
| 删除门槛 | Flaky 超过 7 天未修复，移出测试集并记录 |

---

## 8. 测试报告产出格式

### 8.1 报告类型与格式

| 报告类型 | 格式 | 产出工具 | 用途 |
|---|---|---|---|
| 测试执行报告 | JUnit XML | Surefire/Failsafe | CI 解析、趋势追踪 |
| 覆盖率报告 | HTML + CSV + XML | JaCoCo | 覆盖率可视化、门禁判定 |
| 测试摘要报告 | Markdown | 自定义脚本 | 人工阅读、评审记录 |
| 性能基准报告 | HTML + JSON | JMH | 性能趋势追踪 |
| 契约验证报告 | Markdown | Spring Cloud Contract | 契约一致性记录 |

### 8.2 Markdown 摘要报告模板

每次 CI 构建产出测试摘要报告，包含以下章节：

```markdown
# 测试摘要报告

## 1. 构建信息
- 构建编号：#{BUILD_NUMBER}
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

## 4. 失败用例清单
（如有失败，列出用例 ID、失败原因、日志链接）

## 5. 质量门禁
- 单元测试通过率：✅/❌
- 覆盖率达标：✅/❌
- 契约测试：✅/❌
- 整体结论：✅ 可合并 / ❌ 阻塞合并
```

### 8.3 JUnit XML 报告结构

Surefire/Failsafe 产出标准 JUnit XML，供 CI 系统解析：

```xml
<testsuite name="com.agentplatform.task.DagCycleDetectorTest"
           tests="8" failures="0" errors="0" skipped="0"
           time="0.342" timestamp="2026-06-27T10:00:00">
  <testcase name="should_ReturnTrue_When_DagHasCycle"
            classname="com.agentplatform.task.DagCycleDetectorTest"
            time="0.045"/>
  <testcase name="should_ReturnFalse_When_DagIsAcyclic"
            classname="com.agentplatform.task.DagCycleDetectorTest"
            time="0.032"/>
</testsuite>
```

---

## 9. 测试用例命名规范

### 9.1 测试类命名

| 类型 | 命名规范 | 示例 |
|---|---|---|
| 单元测试 | `{ClassUnderTest}Test` | `ComplexityScorerTest` |
| gRPC 契约测试 | `{Service}{Method}ContractTest` | `ToolGatewayInvokeContractTest` |
| 集成测试 | `{ChainName}IntegrationTest` | `TaskPlanDagIntegrationTest` |
| 端到端测试 | `{ScenarioName}E2ETest` | `IndustryResearchE2ETest` |
| 性能测试 | `{Operation}PerfTest` | `DagSchedulePerfTest` |
| 回归测试 | `{Category}RegressionTest` | `HallucinationRegressionTest` |

### 9.2 测试方法命名

统一采用 `Should_{期望行为}_When_{前置条件}` 格式：

| 场景 | 命名示例 |
|---|---|
| 正常路径 | `Should_ReturnL3_When_AllDimensionsHigh` |
| 边界值 | `Should_ReturnL2_When_TotalScoreEquals14` |
| 异常路径 | `Should_ThrowDagCycleDetected_When_DagHasCycle` |
| 状态流转 | `Should_TransitToRunning_When_L1TaskSubmitted` |
| 错误码 | `Should_Return401Unauthenticated_When_JwtInvalid` |

### 9.3 测试用例 ID 规范

测试用例 ID 采用 `{层级}-{模块}-{序号}` 格式，便于检索追溯：

| 前缀 | 层级 | 示例 |
|---|---|---|
| UT | 单元测试 | `UT-PROTO-001`、`UT-GATE-012` |
| FT | 功能测试 | `FT-SESS-003`、`FT-TOOL-008` |
| E2E | 端到端 | `E2E-01`、`E2E-10` |
| PERF | 性能测试 | `PERF-DAG-001` |
| SEC | 安全测试 | `SEC-AUTH-001` |

---

## 10. 测试用例可追溯性矩阵

每条测试用例必须标注来源设计文档，确保需求变更时可定位受影响测试：

| 来源文档 | 对应测试集 | 决策节点数 | 测试用例要求 |
|---|---|---|---|
| doc 11-detail-flow F1 | 接入网关测试 | 6 节点 | 12 条（每节点 true/false） |
| doc 11-detail-flow F2 | 复杂度判定测试 | 7 节点 | 14 条 |
| doc 11-detail-flow F3 | 任务规划测试 | 9 节点 | 18 条 |
| doc 11-detail-flow F4 | 子任务调度测试 | 8 节点 | 16 条 |
| doc 11-detail-flow F5 | 动态重规划测试 | 7 节点 | 14 条 |
| doc 11-detail-flow F6 | ReAct 循环测试 | 8 节点 | 16 条 |
| doc 11-detail-flow F7 | Token 压缩测试 | 5 节点 | 10 条 |
| doc 11-detail-flow F8 | 工具调用测试 | 13 节点 | 26 条 |
| doc 11-detail-flow F9 | 质量校验测试 | 6 节点 | 12 条 |
| doc 11-detail-flow F10 | 幻觉治理测试 | 12 节点 | 24 条 |
| doc 11-detail-flow F11 | 漂移监测测试 | 9 节点 | 18 条 |
| doc 11-detail-flow F12 | 记忆写入召回测试 | 9 节点 | 18 条 |
| doc 08 §1 | 状态机测试 | 10 状态 | 流转矩阵全覆盖 |
| doc 08 §2 | R3 审批测试 | 4 状态 | 审批全流程 |
| PRD §四 | 幻觉治理测试 | 6 层 | 每层至少 2 条 |

---

## 11. 测试环境分层

| 环境 | 用途 | 数据库 | 消息队列 | 启动方式 |
|---|---|---|---|---|
| **ut** | 纯单元测试 | 无（Mock 全部依赖） | 无 | `mvn test` |
| **it** | 集成测试 | Testcontainers（同 JVM） | Testcontainers | `mvn verify -Pit` |
| **e2e** | 端到端测试 | Testcontainers 全栈 | Testcontainers | `mvn verify -Pe2e` |
| **perf** | 性能基准 | Testcontainers + JMH | Testcontainers | `mvn verify -Pperf` |
| **staging** | 预发布验收 | 独立 MySQL/Redis/Milvus | 独立 RocketMQ | 手动部署 |

---

## 12. 验收标准

### 12.1 单元测试验收

| 验收项 | 标准 |
|---|---|
| 通过率 | 100% |
| 行覆盖率 | ≥ 80%（整体），P0 模块 ≥ 85% |
| 分支覆盖率 | ≥ 70%（整体），P0 模块 ≥ 80% |
| 命名规范 | 100% 符合 `Should_..._When_...` |
| 可追溯性 | 100% 标注来源文档 |

### 12.2 功能测试验收

| 验收项 | 标准 |
|---|---|
| gRPC 契约测试 | 21 个 RPC 方法全覆盖 |
| REST 切片测试 | Controller 全覆盖 |
| 错误码 | doc 02 §0.5 所有错误码均有触发用例 |
| 状态机 | 10 状态合法/非法流转全覆盖 |

### 12.3 端到端测试验收

| 验收项 | 标准 |
|---|---|
| 旅程覆盖 | ≥ 10 个核心用户旅程 |
| 全链路 | 覆盖 PRD §一(三) 7 步全链路 |
| 中间状态验证 | 数据库、Redis、消息队列均有断言 |
| 记忆沉淀验证 | 任务完成后长期记忆正确写入 |

### 12.4 性能基线验收

| 验收项 | 标准 |
|---|---|
| 意图识别 P95 | ≤ 200ms |
| 单步工具调用 P95 | ≤ 800ms |
| DAG 调度延迟 | < 100ms |
| 并发承载 | ≥ 200 Agent 实例/节点 |

### 12.5 治理与合规验收

| 验收项 | 标准 |
|---|---|
| R3 工具越权拦截率 | 100% |
| 审计日志覆盖率 | 全量调用留痕 |
| 幻觉治理六层 | 每层有对应测试 |
| 漂移监测 | 四层管控有测试 |

---

## 13. 附录

### 13.1 相关测试文档索引

| 文档 | 内容 | 路径 |
|---|---|---|
| 测试策略（本文） | 测试目标、原则、金字塔、工具、覆盖率、CI | `docs/tests/test-strategy.md` |
| 单元测试用例 | 11 个模块的单元测试用例清单 | `docs/tests/unit-test-cases.md` |
| 功能测试用例 | 业务功能测试用例清单 | `docs/tests/functional-test-cases.md` |
| 用户流程测试 | 端到端用户旅程测试 | `docs/tests/user-flow-test-cases.md` |
| 测试数据与 Fixture | 测试数据策略、Fixture 示例、Testcontainers 配置 | `docs/tests/test-data-and-fixtures.md` |
| 已有 TDD 测试计划 | TDD 方法论、F1~F12 决策节点用例、3 个 E2E 场景 | `docs/tests/test-plan.md` |

### 13.2 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-06-27 | 初始版本，建立测试策略总纲 | AgentForge 测试团队 |
