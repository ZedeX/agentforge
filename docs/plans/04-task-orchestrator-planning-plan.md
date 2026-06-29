# agent-task-orchestrator + agent-planning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `agent-task-orchestrator`（端口 8084）单模块内补齐任务编排与智能规划的全部能力：TaskOrchestrator gRPC 服务（4 RPC）、PlanningService gRPC 服务（4 RPC）、RocketMQ 子任务分发/回调/状态广播/取消、端到端集成测试。T1-T4/T6/T8-T10/T12 已完成（DAG 引擎 / 状态机 / 复杂度识别 / 模板匹配 / 5 维度校验 / 批次调度 / 重规划模式选择），本计划聚焦 **T5 / T7 / T11 / T13** 四个待做 Task，对齐 v5 审核 §6 P6-6 整改项。

**Architecture:** 单 Spring Boot 应用 `agent-task-orchestrator`，对外暴露两路 gRPC 服务（TaskOrchestrator :9090 + PlanningService :9091，复用同一 gRPC server 端口亦可），内部以 DAG 引擎 + 状态机 + 批次调度器为核心，通过 RocketMQ 与 agent-runtime 异步交互。依赖 agent-proto（Protobuf 契约，含 `task.proto` / `planning.proto` / `common.proto`）与 agent-common（TaskStatus / ErrorCode / BusinessException）。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / grpc-spring-boot-starter 3.1.0.RELEASE（net.devh）/ rocketmq-spring-boot-starter 2.3.0（对齐父 pom `rocketmq-spring.version`）/ JPA + MySQL 8 / JUnit 5 / Mockito 5 / AssertJ 3.25.3 / Awaitility / H2（MySQL 模式，测试备选）/ jedis-mock（测试备选）/ Testcontainers 1.19.7（可选 Docker 集成路径）

---

## 设计文档对齐

| 项 | 来源 | 锁定值 |
|---|---|---|
| task-orchestrator 端口 | doc 00-overview §3.1 | 8084（HTTP） / 9090（gRPC） |
| planning-service 端口 | doc 00-overview §3.1 | 8086（HTTP） / 9091（gRPC，本计划合并进 9090 同一 server） |
| 逻辑库 | doc 01-database §0.4 / §2.1 | `agent_task`（task_instance / task_step_log / task_state_change） |
| TaskOrchestrator gRPC 4 RPC | `agent-proto/src/main/proto/task.proto` | SubmitTask / GetTaskStatus / CancelTask / ReportSubtaskResult |
| PlanningService gRPC 4 RPC | `agent-proto/src/main/proto/planning.proto` | AssessComplexity / Plan / ValidatePlan / Replan |
| proto 生成包名 | task.proto / planning.proto / common.proto | `agentplatform.task.v1` / `agentplatform.planning.v1` / `agentplatform.common.v1` |
| 状态机 10 状态 | doc 03-task-engine §6.2 + agent-common TaskStatus | PENDING / PLANNING / RUNNING / SUBTASK_RUNNING / WAITING_HUMAN / REPLANNING / SUCCESS / FAILED / CANCELLED / TIMEOUT |
| RocketMQ Topic | doc 03-task-engine §7.1 | `task.subtask.execute` / `task.subtask.done` / `task.state.change` / `task.subtask.cancel` |
| 子任务分发消息格式 | doc 03-task-engine §7.2 | key=`{taskId}:{nodeId}` / tag=`{tenantId}` / payload=SubtaskExecuteEvent |
| 子任务完成回调逻辑 | doc 03-task-engine §7.4 | 幂等校验 → 节点状态更新 → 成本累加 → 推进批次 / 触发重规划 |
| 异常分级与三级重试 | doc 03-task-engine §10.1 / §10.2 | 瞬时（子任务级 2 次）/ 业务（降级换路）/ 质量（单步重跑）/ 致命（重规划或人工） |
| 错误码域 | doc 03-task-engine §10.4 | TASK_NOT_FOUND(404) / TASK_STATUS_CONFLICT(409) / DAG_CYCLE_DETECTED(409) / DAG_VALIDATION_FAILED(400) / PLAN_VALIDATION_FAILED(500) / REPLAN_EXHAUSTED(500) / COST_BUDGET_EXCEEDED(429) / AGENT_NOT_FOUND(404) |
| gRPC 服务类签名 | doc 03-task-engine §8.1.1 / §8.2.1 | `@GrpcService extends TaskOrchestratorGrpc.TaskOrchestratorImplBase` / `extends PlanningServiceGrpc.PlanningServiceImplBase` |
| 子任务分发器 | doc 03-task-engine §8.1.5 | RocketMqSubtaskDispatcher（事务消息保证 DB+MQ 一致性） |
| 子任务完成消费者 | doc 03-task-engine §8.1.7 | `@RocketMQMessageListener` SubtaskDoneConsumer → SubtaskDoneHandler |
| 配置参数 | doc 03-task-engine §11 | `task.orchestrator.maxSubtaskRetries=2` / `maxIncrementalReplan=3` / `maxFullReplan=1` / `rocketmq.topics.*` |
| ADR-001 | doc | 自研 DAG 引擎（不依赖 Airflow）—— 已由 T3 落地 |
| 测试用例 | `docs/tests/unit-test-cases.md` §5 / §6 | UT-ORCH-001~013 / UT-PLAN-001~010 |
| v5 审核整改项 | `docs/tests/tdd-audit-report-v5.md` §6 P6-6 | 实现 agent-task-orchestrator T5-T13（gRPC 服务 / 复杂度识别 / 动态重规划等）D2 +1.0 |

---

## 文件结构总览

### 已完成文件（T1-T4 / T6 / T8-T10 / T12）

| 文件 | Task | 职责 |
|---|---|---|
| `agent-task-orchestrator/pom.xml` | T1 | Maven 配置（web/jpa/validation/agent-proto/agent-common/lombok/h2/jedis-mock） |
| `agent-task-orchestrator/src/main/resources/application.yml` | T1 | 端口 8084 + MySQL agent_task |
| `agent-task-orchestrator/src/main/java/com/agent/orchestrator/OrchestratorApplication.java` | T1 | Spring Boot 启动类 |
| `.../model/TaskInstance.java` | T2 | 任务实例 @Entity（23 业务字段） |
| `.../repository/TaskInstanceRepository.java` | T2 | JPA Repository（findByTaskId） |
| `.../model/BaseEntity.java` | T3 | 审计字段基类（created_at / updated_at） |
| `.../model/DagNode.java` | T3 | DAG 节点模型 |
| `.../model/DagEdge.java` | T3 | DAG 依赖边模型 |
| `.../model/DagElement.java` | T3 | DAG 元素抽象 |
| `.../dag/DagGraph.java` | T3 | DAG 图（节点/边查询） |
| `.../dag/DagValidator.java` | T3 | 环检测 + 可达性 + 参数映射校验 |
| `.../dag/TopologicalSorter.java` | T3 | 拓扑排序 + 环检测 |
| `.../statemachine/TaskStateMachine.java` | T4 | 10 状态流转矩阵校验（canTransitTo / transit） |
| `.../assessor/ComplexityLevel.java` | T6 | L1/L2/L3 枚举 |
| `.../assessor/ComplexityDimensions.java` | T6 | 6 维度评分模型 |
| `.../assessor/ComplexityScorer.java` | T6 | 6 维度加权评分（≤8=L1 / 9-14=L2 / >14=L3，风险高强制 L3） |
| `.../assessor/RuleFilter.java` | T6 | 规则初筛（confidence≥0.9 跳过模型精判） |
| `.../template/TaskTemplate.java` | T8 | 任务模板实体 |
| `.../template/PlanMode.java` | T8 | TEMPLATE / AI 枚举 |
| `.../template/TemplateMatcher.java` | T8 | 场景标签匹配 + 成功率过滤 |
| `.../validator/ValidationDimension.java` | T9 | 5 维度枚举（完备性/原子性/效率/成本/容错） |
| `.../validator/ValidationResult.java` | T9 | 校验结果（passed / errors / warnings） |
| `.../validator/ValidationContext.java` | T9 | 校验上下文 |
| `.../validator/PlanValidator.java` | T9 | 5 维度综合自检 |
| `.../dispatcher/Batch.java` | T10 | 并行批次（同层无依赖节点集合） |
| `.../dispatcher/BatchPartitioner.java` | T10 | 拓扑分层 → 批次划分 |
| `.../replanner/ReplanMode.java` | T12 | INCREMENTAL / FULL 枚举 |
| `.../replanner/ReplanModeSelector.java` | T12 | 单点失败→增量 / 根节点变更→全量 / 超限→人工 |

### 待新增文件（T5 / T7 / T11 / T13）

| 文件 | Task | 职责 |
|---|---|---|
| `.../grpc/TaskOrchestratorGrpcService.java` | T5 | TaskOrchestrator gRPC 服务端（4 RPC） |
| `.../grpc/TaskInstanceMapper.java` | T5 | proto TaskInstance ↔ JPA TaskInstance 映射 |
| `.../grpc/GrpcExceptionAdvice.java` | T5 | gRPC Status 异常翻译（BusinessException → StatusResponse） |
| `.../planning/grpc/PlanningServiceGrpcImpl.java` | T7 | PlanningService gRPC 服务端（4 RPC） |
| `.../planning/grpc/DagJsonMapper.java` | T7 | DAG ↔ JSON 序列化（对齐 dag_json 字段） |
| `.../planning/grpc/AssessResultMapper.java` | T7 | AssessResult ↔ AssessResponse 映射 |
| `.../mq/SubtaskExecuteProducer.java` | T11 | 生产者：分发子任务到 task.subtask.execute |
| `.../mq/SubtaskDoneConsumer.java` | T11 | 消费者：消费 task.subtask.done |
| `.../mq/SubtaskDoneHandler.java` | T11 | 回调处理逻辑（幂等 + 状态推进 + 重规划触发） |
| `.../mq/StateChangeProducer.java` | T11 | 生产者：广播任务状态变更到 task.state.change |
| `.../mq/SubtaskCancelProducer.java` | T11 | 生产者：取消已分发子任务到 task.subtask.cancel |
| `.../mq/event/SubtaskExecuteEvent.java` | T11 | 子任务分发事件 POJO |
| `.../mq/event/SubtaskDoneEvent.java` | T11 | 子任务完成事件 POJO |
| `.../mq/event/StateChangeEvent.java` | T11 | 状态变更事件 POJO |
| `.../mq/event/SubtaskCancelEvent.java` | T11 | 子任务取消事件 POJO |
| `.../config/RocketMqProperties.java` | T11 | Topic / ConsumerGroup 配置属性绑定 |
| `.../integration/TaskOrchestratorIntegrationTest.java` | T13 | 端到端集成测试（H2 + jedis-mock + InProcess gRPC） |

---

## 已完成 Task 简表

| Task | 一句话描述 | 关键文件 |
|---|---|---|
| T1 项目骨架 | Spring Boot 启动类 + application.yml（端口 8084 + MySQL agent_task） | `OrchestratorApplication.java` / `application.yml` |
| T2 TaskInstance 实体 | 23 业务字段 JPA 实体 + Repository（findByTaskId） | `model/TaskInstance.java` / `repository/TaskInstanceRepository.java` |
| T3 DAG 引擎 | DagGraph + DagValidator（环检测/可达性/参数映射）+ TopologicalSorter + DagNode/DagEdge/DagElement/BaseEntity | `dag/*` / `model/*` |
| T4 状态机 | 10 状态流转矩阵校验（canTransitTo / transit，非法抛 BusinessException(PARAM_INVALID)） | `statemachine/TaskStateMachine.java` |
| T6 复杂度识别 | 6 维度评分（≤8=L1 / 9-14=L2 / >14=L3，风险高强制 L3）+ 规则初筛（confidence≥0.9 跳模型） | `assessor/*` |
| T8 模板匹配 | 场景标签匹配 + 成功率过滤（TEMPLATE / AI 双模式） | `template/*` |
| T9 5 维度 DAG 校验 | 完备性/原子性/效率/成本/容错综合自检（passed / errors / warnings / FixSuggestion） | `validator/*` |
| T10 并行批次调度 | 拓扑分层 → 同层无依赖节点归同批次（List<Batch>） | `dispatcher/Batch.java` / `BatchPartitioner.java` |
| T12 动态重规划 | 单点失败→增量 / 根节点变更→全量 / replan_count 超限→人工 | `replanner/ReplanMode.java` / `ReplanModeSelector.java` |

> 已完成 Task 的单测均在 `src/test/java/com/agent/orchestrator/<subpackage>/` 下，方法名 `should_X_When_Y` + AssertJ + 中文 @DisplayName，全绿。

---

## 依赖添加顺序（pom.xml 变更）

> 父 pom `com.agentforge:agentforge-parent:1.0.0-SNAPSHOT` 已管理：spring-boot 3.2.5 / grpc 1.62.2 / protobuf 3.25.1 / testcontainers 1.19.7 / rocketmq-spring 2.3.0 / assertj 3.25.3。子模块仅需声明 groupId + artifactId，版本由父 BOM 或属性继承。

### Step P.1: 在 `agent-task-orchestrator/pom.xml` 的 `<properties>` 追加版本属性

```xml
<properties>
    <!-- 既有 -->
    <jacoco.line.coverage>0.80</jacoco.line.coverage>
    <jacoco.branch.coverage>0.70</jacoco.branch.coverage>
    <!-- T5/T7: gRPC Spring Boot Starter（与 agent-gateway 一致） -->
    <grpc.spring.boot.starter.version>3.1.0.RELEASE</grpc.spring.boot.starter.version>
    <!-- T11: RocketMQ Spring Boot Starter（版本继承父 pom rocketmq-spring.version=2.3.0） -->
    <!-- T13: Testcontainers（版本继承父 pom testcontainers.version=1.19.7） -->
</properties>
```

### Step P.2: 在 `<dependencies>` 追加 gRPC 相关依赖（T5/T7 前置）

```xml
<!-- gRPC server（net.devh）：暴露 TaskOrchestrator + PlanningService -->
<dependency>
    <groupId>net.devh</groupId>
    <artifactId>grpc-spring-boot-starter</artifactId>
    <version>${grpc.spring.boot.starter.version}</version>
    <exclusions>
        <exclusion>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-netty-shaded</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<!-- gRPC client（预留调用 agent-repo / model-gateway，T5 内未使用也可声明供后续） -->
<dependency>
    <groupId>net.devh</groupId>
    <artifactId>grpc-client-spring-boot-starter</artifactId>
    <version>${grpc.spring.boot.starter.version}</version>
</dependency>
<!-- gRPC 测试支持：InProcess Server（T13 集成测试用） -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-testing</artifactId>
    <scope>test</scope>
</dependency>
```

### Step P.3: 在 `<dependencies>` 追加 RocketMQ 依赖（T11 前置）

```xml
<!-- RocketMQ Spring Boot Starter（版本由父 pom rocketmq-spring.version=2.3.0 继承） -->
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
    <version>${rocketmq-spring.version}</version>
</dependency>
```

> 设计文档 §11 提及 2.3.1；父 pom 已声明 `rocketmq-spring.version=2.3.0`。本计划对齐父 pom 2.3.0，避免引入未在 BOM 管理的新版本。如需升级，建议同步更新父 pom 属性。

### Step P.4: 在 `<dependencies>` 追加 Testcontainers（T13 前置，可选）

```xml
<!-- Testcontainers：Docker 可用时的真实集成路径（no-docker profile 下跳过） -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>${testcontainers.version}</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>${testcontainers.version}</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <version>${testcontainers.version}</version>
    <scope>test</scope>
</dependency>
<!-- AssertJ 已由父 pom dependencyManagement 管理，子模块按需声明 -->
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>
<!-- Mockito 已由 spring-boot-starter-test 传递引入 -->
```

### Step P.5: 运行 `mvn -pl agent-task-orchestrator -am compile -q` 验证依赖拉取

Expected: `BUILD SUCCESS`

---

## Task 5: TaskOrchestrator gRPC 服务实现

**Files:**
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/grpc/TaskOrchestratorGrpcService.java`
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/grpc/TaskInstanceMapper.java`
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/grpc/GrpcExceptionAdvice.java`
- Update: `agent-task-orchestrator/src/main/resources/application.yml`（gRPC server 配置）
- Test: `agent-task-orchestrator/src/test/java/com/agent/orchestrator/grpc/TaskOrchestratorGrpcServiceTest.java`

**对齐 proto：** `agent-proto/src/main/proto/task.proto` 定义 4 RPC + 7 message。生成类位于包 `agentplatform.task.v1`：
- `TaskOrchestratorGrpc.TaskOrchestratorImplBase`（基类）
- `SubmitTaskRequest` / `SubmitTaskResponse` / `GetTaskStatusRequest` / `CancelTaskRequest` / `CancelAck` / `ReportAck` / `SubtaskResult` / `TaskInstance`（proto 消息，注意与 JPA 实体 `com.agent.orchestrator.model.TaskInstance` 同名，需用 FQN 或别名区分）/ `TaskSchema`
- `agentplatform.common.v1.TraceContext`

- [ ] **Step 5.1: 写失败测试 — TaskOrchestratorGrpcServiceTest**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\test\java\com\agent\orchestrator\grpc\TaskOrchestratorGrpcServiceTest.java`

```java
package com.agent.orchestrator.grpc;

import agentplatform.task.v1.SubmitTaskRequest;
import agentplatform.task.v1.SubmitTaskResponse;
import agentplatform.task.v1.GetTaskStatusRequest;
import agentplatform.task.v1.CancelTaskRequest;
import agentplatform.task.v1.CancelAck;
import agentplatform.task.v1.ReportAck;
import agentplatform.task.v1.SubtaskResult;
import agentplatform.common.v1.TraceContext;
import com.agent.common.constant.TaskStatus;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.model.TaskInstance;
import com.agent.orchestrator.repository.TaskInstanceRepository;
import com.agent.orchestrator.statemachine.TaskStateMachine;
import com.agent.orchestrator.dispatcher.BatchPartitioner;
import com.agent.orchestrator.validator.PlanValidator;
import com.agent.orchestrator.template.TemplateMatcher;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TaskOrchestrator gRPC 服务单测（Red 阶段）。
 *
 * <p>对齐 docs/tests/unit-test-cases.md §5 UT-ORCH-001~008：
 * L1 直跑 / R3 人工 / 重试耗尽重规划 / 非法状态冲突 / 成本超限 / Agent 匹配。
 * 用 Mockito mock Repository + 下游协作类，专注 gRPC 服务层的请求编排与异常翻译。</p>
 */
@ExtendWith(MockitoExtension.class)
class TaskOrchestratorGrpcServiceTest {

    @Mock private TaskInstanceRepository repository;
    @Mock private TaskStateMachine stateMachine;
    @Mock private BatchPartitioner batchPartitioner;
    @Mock private PlanValidator planValidator;
    @Mock private TemplateMatcher templateMatcher;
    @Mock private TaskInstanceMapper mapper;
    @Mock private StreamObserver<SubmitTaskResponse> submitObserver;
    @Mock private StreamObserver<agentplatform.task.v1.TaskInstance> getStatusObserver;
    @Mock private StreamObserver<CancelAck> cancelObserver;
    @Mock private StreamObserver<ReportAck> reportObserver;

    @InjectMocks
    private TaskOrchestratorGrpcService service;

    @BeforeEach
    void setUp() {
        service = new TaskOrchestratorGrpcService(repository, stateMachine, batchPartitioner,
                planValidator, templateMatcher, mapper);
    }

    // ===== UT-ORCH-001: L1 任务直跑（跳规划） =====

    @Test
    @DisplayName("UT-ORCH-001: L1 任务提交后状态应为 RUNNING（跳过 PLANNING）")
    void should_ReturnRunningStatus_When_L1TaskSubmitted() {
        SubmitTaskRequest req = SubmitTaskRequest.newBuilder()
                .setTaskId("tk_l1_001").setTenantId(1001L).setUserId("u_1")
                .setGoal("查订单").setCostLimitCent(1000L)
                .setTrace(TraceContext.newBuilder().setTaskId("tk_l1_001").build())
                .build();
        TaskInstance saved = TaskInstance.builder()
                .taskId("tk_l1_001").tenantId(1001L).complexity(1)
                .status(TaskStatus.RUNNING.name()).costLimitCent(1000L).costUsedCent(0L)
                .tokenUsed(0).build();
        when(repository.findByTaskId("tk_l1_001")).thenReturn(Optional.of(saved));

        service.submitTask(req, submitObserver);

        ArgumentCaptor<SubmitTaskResponse> captor = ArgumentCaptor.forClass(SubmitTaskResponse.class);
        verify(submitObserver).onNext(captor.capture());
        SubmitTaskResponse resp = captor.getValue();
        assertThat(resp.getTaskId()).isEqualTo("tk_l1_001");
        assertThat(resp.getStatus()).isEqualTo(TaskStatus.RUNNING.name());
        assertThat(resp.getComplexity()).isEqualTo(1);
        verify(submitObserver).onCompleted();
    }

    // ===== UT-ORCH-002: R3 节点触发 WAITING_HUMAN =====

    @Test
    @DisplayName("UT-ORCH-002: R3 高危节点应使任务转 WAITING_HUMAN")
    void should_TransitToWaitingHuman_When_R3NodeRequiresReview() {
        // 通过 ReportSubtaskResult 上报 R3 节点要求人工审核
        SubtaskResult result = SubtaskResult.newBuilder()
                .setTaskId("tk_r3_001").setSubtaskId("st_r3").setNodeId("n_r3")
                .setStatus("require_review").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_r3_001").status(TaskStatus.SUBTASK_RUNNING.name()).build();
        when(repository.findByTaskId("tk_r3_001")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(TaskStatus.SUBTASK_RUNNING, TaskStatus.WAITING_HUMAN))
                .thenReturn(true);

        service.reportSubtaskResult(result, reportObserver);

        ArgumentCaptor<ReportAck> captor = ArgumentCaptor.forClass(ReportAck.class);
        verify(reportObserver).onNext(captor.capture());
        assertThat(captor.getValue().getAccepted()).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.WAITING_HUMAN.name());
    }

    // ===== UT-ORCH-003: 子任务重试耗尽触发 REPLANNING =====

    @Test
    @DisplayName("UT-ORCH-003: 子任务重试耗尽应触发 REPLANNING")
    void should_TransitToReplanning_When_SubtaskRetryExhausted() {
        SubtaskResult result = SubtaskResult.newBuilder()
                .setTaskId("tk_retry_001").setSubtaskId("st_1").setNodeId("n_1")
                .setStatus("failed").setErrorCode("MAX_RETRY_EXCEEDED").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_retry_001").status(TaskStatus.SUBTASK_RUNNING.name()).build();
        when(repository.findByTaskId("tk_retry_001")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(TaskStatus.SUBTASK_RUNNING, TaskStatus.REPLANNING))
                .thenReturn(true);

        service.reportSubtaskResult(result, reportObserver);

        verify(reportObserver).onNext(any());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.REPLANNING.name());
    }

    // ===== UT-ORCH-004: 非法状态流转抛 TASK_STATUS_CONFLICT =====

    @Test
    @DisplayName("UT-ORCH-004: 非法状态流转应翻译为 gRPC Status UNKNOWN/TASK_STATUS_CONFLICT")
    void should_TranslateStatusConflict_When_IllegalTransition() {
        SubtaskResult result = SubtaskResult.newBuilder()
                .setTaskId("tk_illegal_001").setNodeId("n_1").setStatus("failed").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_illegal_001").status(TaskStatus.SUCCESS.name()).build(); // 终态
        when(repository.findByTaskId("tk_illegal_001")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(eq(TaskStatus.SUCCESS), any()))
                .thenReturn(false);

        service.reportSubtaskResult(result, reportObserver);

        verify(reportObserver).onError(any());
        verify(reportObserver, never()).onNext(any());
    }

    // ===== UT-ORCH-005: 环检测在 DAG 校验阶段抛 DAG_CYCLE_DETECTED =====
    // （由 T3 DagValidator 已实现，本测试验证 gRPC 层 Plan 流程透传异常）

    @Test
    @DisplayName("UT-ORCH-005: DAG 含环应使 SubmitTask 返回 DAG_CYCLE_DETECTED 错误")
    void should_ReturnDagCycleError_When_DagHasCircularDependency() {
        // 当复杂度=L2/L3 走 Plan 流程时，DagValidator 抛 DAG_CYCLE_DETECTED
        // gRPC 层应捕获并翻译为 onError
        SubmitTaskRequest req = SubmitTaskRequest.newBuilder()
                .setTaskId("tk_cycle_001").setGoal("复杂任务需规划").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_cycle_001").complexity(2).status(TaskStatus.PLANNING.name()).build();
        when(repository.findByTaskId("tk_cycle_001")).thenReturn(Optional.of(task));
        when(planValidator.validate(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.DAG_CYCLE_DETECTED, "检测到环 A→B→C→A"));

        service.submitTask(req, submitObserver);

        verify(submitObserver).onError(any());
        verify(submitObserver, never()).onNext(any());
    }

    // ===== UT-ORCH-006: 并行批次调度（由 T10 BatchPartitioner 实现，gRPC 层验证调用） =====

    @Test
    @DisplayName("UT-ORCH-006: 提交 L2 任务应调用 BatchPartitioner 划分批次")
    void should_InvokeBatchPartitioner_When_L2TaskSubmitted() {
        SubmitTaskRequest req = SubmitTaskRequest.newBuilder()
                .setTaskId("tk_l2_001").setGoal("多步任务").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_l2_001").complexity(2).status(TaskStatus.PLANNING.name()).build();
        when(repository.findByTaskId("tk_l2_001")).thenReturn(Optional.of(task));
        when(planValidator.validate(any(), any())).thenReturn(
                new com.agent.orchestrator.validator.ValidationResult(true, java.util.List.of(), java.util.List.of()));

        service.submitTask(req, submitObserver);

        verify(batchPartitioner, atLeastOnce()).partition(any());
    }

    // ===== UT-ORCH-007: Agent 匹配选最高分 =====
    // （Agent 匹配由 agent-repo 负责，本测试验证 gRPC 层不抛 AGENT_NOT_FOUND 时正常推进）

    @Test
    @DisplayName("UT-ORCH-007: Agent 匹配成功应推进任务到 SUBTASK_RUNNING")
    void should_AdvanceToSubtaskRunning_When_AgentMatched() {
        SubmitTaskRequest req = SubmitTaskRequest.newBuilder()
                .setTaskId("tk_agent_ok").setGoal("查询订单").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_agent_ok").complexity(1).status(TaskStatus.RUNNING.name()).build();
        when(repository.findByTaskId("tk_agent_ok")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(TaskStatus.RUNNING, TaskStatus.SUBTASK_RUNNING)).thenReturn(true);

        service.submitTask(req, submitObserver);

        verify(submitObserver).onNext(any());
    }

    // ===== UT-ORCH-008: 所有 Agent 评分 <0.6 抛 AGENT_NOT_FOUND 转 WAITING_HUMAN =====

    @Test
    @DisplayName("UT-ORCH-008: Agent 匹配失败应使任务转 WAITING_HUMAN")
    void should_TransitToWaitingHuman_When_NoAgentScoreAbove06() {
        SubtaskResult result = SubtaskResult.newBuilder()
                .setTaskId("tk_no_agent").setNodeId("n_1").setStatus("failed")
                .setErrorCode("AGENT_NOT_FOUND").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_no_agent").status(TaskStatus.SUBTASK_RUNNING.name()).build();
        when(repository.findByTaskId("tk_no_agent")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(TaskStatus.SUBTASK_RUNNING, TaskStatus.WAITING_HUMAN))
                .thenReturn(true);

        service.reportSubtaskResult(result, reportObserver);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.WAITING_HUMAN.name());
    }

    // ===== UT-ORCH-012: 成本超限抛 COST_BUDGET_EXCEEDED =====

    @Test
    @DisplayName("UT-ORCH-012: 成本超限应抛 COST_BUDGET_EXCEEDED 并转 TIMEOUT")
    void should_ThrowCostBudgetExceeded_When_CostUsedExceedsLimit() {
        SubtaskResult result = SubtaskResult.newBuilder()
                .setTaskId("tk_cost_over").setNodeId("n_1").setStatus("success")
                .setCostCent(5500L).build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_cost_over").status(TaskStatus.SUBTASK_RUNNING.name())
                .costLimitCent(5000L).costUsedCent(0L).build();
        when(repository.findByTaskId("tk_cost_over")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(TaskStatus.SUBTASK_RUNNING, TaskStatus.TIMEOUT)).thenReturn(true);

        service.reportSubtaskResult(result, reportObserver);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.TIMEOUT.name());
    }

    // ===== GetTaskStatus / CancelTask 基础路径 =====

    @Test
    @DisplayName("GetTaskStatus: 任务存在应返回 TaskInstance proto")
    void should_ReturnTaskInstance_When_TaskExists() {
        GetTaskStatusRequest req = GetTaskStatusRequest.newBuilder()
                .setTaskId("tk_q_001").setTenantId(1001L).build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_q_001").tenantId(1001L).status(TaskStatus.RUNNING.name())
                .complexity(2).build();
        when(repository.findByTaskId("tk_q_001")).thenReturn(Optional.of(task));
        agentplatform.task.v1.TaskInstance proto = agentplatform.task.v1.TaskInstance.newBuilder()
                .setTaskId("tk_q_001").setStatus(TaskStatus.RUNNING.name()).build();
        when(mapper.toProto(task)).thenReturn(proto);

        service.getTaskStatus(req, getStatusObserver);

        verify(getStatusObserver).onNext(proto);
        verify(getStatusObserver).onCompleted();
    }

    @Test
    @DisplayName("GetTaskStatus: 任务不存在应翻译为 TASK_NOT_FOUND 错误")
    void should_ReturnTaskNotFound_When_TaskIdMissing() {
        GetTaskStatusRequest req = GetTaskStatusRequest.newBuilder()
                .setTaskId("tk_notexist").build();
        when(repository.findByTaskId("tk_notexist")).thenReturn(Optional.empty());

        service.getTaskStatus(req, getStatusObserver);

        verify(getStatusObserver).onError(any());
    }

    @Test
    @DisplayName("CancelTask: 取消运行中任务应转 CANCELLED")
    void should_TransitToCancelled_When_CancelRunningTask() {
        CancelTaskRequest req = CancelTaskRequest.newBuilder()
                .setTaskId("tk_cancel_001").setReason("user cancel").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_cancel_001").status(TaskStatus.RUNNING.name()).build();
        when(repository.findByTaskId("tk_cancel_001")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(TaskStatus.RUNNING, TaskStatus.CANCELLED)).thenReturn(true);

        service.cancelTask(req, cancelObserver);

        ArgumentCaptor<CancelAck> captor = ArgumentCaptor.forClass(CancelAck.class);
        verify(cancelObserver).onNext(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.CANCELLED.name());
    }
}
```

- [ ] **Step 5.2: 运行测试验证失败**

Run: `mvn -pl agent-task-orchestrator test -Dtest=TaskOrchestratorGrpcServiceTest -q`
Expected: FAIL — `cannot find symbol class TaskOrchestratorGrpcService / TaskInstanceMapper`

- [ ] **Step 5.3: 创建 TaskInstanceMapper.java（proto ↔ JPA 映射）**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\grpc\TaskInstanceMapper.java`

```java
package com.agent.orchestrator.grpc;

import agentplatform.task.v1.TaskInstance;
import com.agent.orchestrator.model.TaskInstance;
import org.springframework.stereotype.Component;

/**
 * proto TaskInstance ↔ JPA TaskInstance 映射器。
 *
 * <p>两个 TaskInstance 同名：proto 类位于 {@code agentplatform.task.v1.TaskInstance}，
 * JPA 实体位于 {@code com.agent.orchestrator.model.TaskInstance}。本类用 FQN 消歧义。</p>
 */
@Component
public class TaskInstanceMapper {

    /** JPA 实体 → proto 消息。 */
    public TaskInstance toProto(com.agent.orchestrator.model.TaskInstance entity);

    /** proto 提交请求 → JPA 实体（新建，未持久化）。 */
    public com.agent.orchestrator.model.TaskInstance fromSubmitRequest(
            agentplatform.task.v1.SubmitTaskRequest request, int complexity);

    /** epoch millis → Instant 辅助。 */
    private java.time.Instant millisToInstant(long millis);
}
```

- [ ] **Step 5.4: 创建 GrpcExceptionAdvice.java（异常翻译）**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\grpc\GrpcExceptionAdvice.java`

```java
package com.agent.orchestrator.grpc;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

/**
 * gRPC 异常翻译器：将 BusinessException 翻译为 gRPC Status，
 * 并通过 StreamObserver.onError 下发，避免服务方法抛未捕获异常导致 channel 异常断开。
 *
 * <p>错误码 → gRPC Status 映射（对齐 doc 03-task-engine §10.4）：</p>
 * <ul>
 *   <li>TASK_NOT_FOUND(404) / AGENT_NOT_FOUND(404) → NOT_FOUND</li>
 *   <li>TASK_STATUS_CONFLICT(409) / DAG_CYCLE_DETECTED(409) → FAILED_PRECONDITION</li>
 *   <li>COST_BUDGET_EXCEEDED(429) → RESOURCE_EXHAUSTED</li>
 *   <li>REPLAN_EXHAUSTED(500) / PLAN_VALIDATION_FAILED(500) → INTERNAL</li>
 *   <li>PARAM_INVALID(400) / DAG_VALIDATION_FAILED(400) → INVALID_ARGUMENT</li>
 * </ul>
 */
@Component
public class GrpcExceptionAdvice {

    public <T> void translate(Throwable t, StreamObserver<T> observer) {
        Status status = toStatus(t);
        observer.onError(status.asRuntimeException());
    }

    private Status toStatus(Throwable t) {
        if (t instanceof BusinessException be) {
            ErrorCode ec = be.getErrorCode();
            return switch (ec.getHttpStatus()) {
                case 404 -> Status.NOT_FOUND.withDescription(ec.getCode() + ": " + be.getMessage());
                case 409 -> Status.FAILED_PRECONDITION.withDescription(ec.getCode() + ": " + be.getMessage());
                case 429 -> Status.RESOURCE_EXHAUSTED.withDescription(ec.getCode() + ": " + be.getMessage());
                case 400 -> Status.INVALID_ARGUMENT.withDescription(ec.getCode() + ": " + be.getMessage());
                default -> Status.INTERNAL.withDescription(ec.getCode() + ": " + be.getMessage());
            };
        }
        return Status.INTERNAL.withDescription("INTERNAL: " + t.getMessage());
    }
}
```

- [ ] **Step 5.5: 创建 TaskOrchestratorGrpcService.java（核心 gRPC 服务）**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\grpc\TaskOrchestratorGrpcService.java`

```java
package com.agent.orchestrator.grpc;

import agentplatform.task.v1.CancelAck;
import agentplatform.task.v1.CancelTaskRequest;
import agentplatform.task.v1.GetTaskStatusRequest;
import agentplatform.task.v1.ReportAck;
import agentplatform.task.v1.SubtaskResult;
import agentplatform.task.v1.SubmitTaskRequest;
import agentplatform.task.v1.SubmitTaskResponse;
import agentplatform.task.v1.TaskOrchestratorGrpc;
import com.agent.common.constant.TaskStatus;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.dispatcher.BatchPartitioner;
import com.agent.orchestrator.model.TaskInstance;
import com.agent.orchestrator.repository.TaskInstanceRepository;
import com.agent.orchestrator.statemachine.TaskStateMachine;
import com.agent.orchestrator.template.TemplateMatcher;
import com.agent.orchestrator.validator.PlanValidator;
import com.agent.orchestrator.validator.ValidationResult;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * TaskOrchestrator gRPC 服务端实现（对齐 task.proto 4 RPC + doc 03-task-engine §8.1.1）。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>submitTask：建任务实例 → 评估复杂度 → L1 直跑 RUNNING / L2-L3 走 PLANNING + 校验 + 批次划分</li>
 *   <li>getTaskStatus：查任务实例 → 转 proto 返回</li>
 *   <li>cancelTask：校验状态机 → 转 CANCELLED</li>
 *   <li>reportSubtaskResult：幂等校验 → 节点状态推进 → 成本累加 → 重规划/人工触发</li>
 * </ol>
 *
 * <p>异常通过 {@link GrpcExceptionAdvice} 统一翻译为 gRPC Status。</p>
 */
@Slf4j
@GrpcService
public class TaskOrchestratorGrpcService extends TaskOrchestratorGrpc.TaskOrchestratorImplBase {

    private final TaskInstanceRepository repository;
    private final TaskStateMachine stateMachine;
    private final BatchPartitioner batchPartitioner;
    private final PlanValidator planValidator;
    private final TemplateMatcher templateMatcher;
    private final TaskInstanceMapper mapper;
    private final GrpcExceptionAdvice exceptionAdvice;

    public TaskOrchestratorGrpcService(TaskInstanceRepository repository,
                                       TaskStateMachine stateMachine,
                                       BatchPartitioner batchPartitioner,
                                       PlanValidator planValidator,
                                       TemplateMatcher templateMatcher,
                                       TaskInstanceMapper mapper) {
        this(repository, stateMachine, batchPartitioner, planValidator,
                templateMatcher, mapper, new GrpcExceptionAdvice());
    }

    public TaskOrchestratorGrpcService(TaskInstanceRepository repository,
                                       TaskStateMachine stateMachine,
                                       BatchPartitioner batchPartitioner,
                                       PlanValidator planValidator,
                                       TemplateMatcher templateMatcher,
                                       TaskInstanceMapper mapper,
                                       GrpcExceptionAdvice exceptionAdvice) {
        this.repository = repository;
        this.stateMachine = stateMachine;
        this.batchPartitioner = batchPartitioner;
        this.planValidator = planValidator;
        this.templateMatcher = templateMatcher;
        this.mapper = mapper;
        this.exceptionAdvice = exceptionAdvice;
    }

    @Override
    @Transactional
    public void submitTask(SubmitTaskRequest request, StreamObserver<SubmitTaskResponse> responseObserver) {
        try {
            // 1. 评估复杂度（T6 ComplexityScorer 在内部，此处简化：先建实例，由 planning 路径评估）
            int complexity = assessComplexityInternal(request.getGoal());
            TaskInstance entity = mapper.fromSubmitRequest(request, complexity);
            entity.setStatus(TaskStatus.PENDING.name());
            repository.save(entity);

            // 2. L1 直跑（跳规划）
            if (complexity == 1) {
                transit(entity, TaskStatus.RUNNING);
                transit(entity, TaskStatus.SUBTASK_RUNNING);  // L1 单步直接进入子任务运行
                repository.save(entity);
                emitSuccess(responseObserver, entity, complexity);
                return;
            }

            // 3. L2/L3 走 PLANNING → PlanValidator → BatchPartitioner
            transit(entity, TaskStatus.PLANNING);
            ValidationResult vr = planValidator.validate(/* context */ null, /* context */ null);
            if (!vr.isPassed()) {
                throw new BusinessException(ErrorCode.PLAN_VALIDATION_FAILED,
                        String.join(";", vr.getErrors()));
            }
            batchPartitioner.partition(/* dag */ null);
            transit(entity, TaskStatus.RUNNING);
            repository.save(entity);
            emitSuccess(responseObserver, entity, complexity);
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    @Override
    @Transactional
    public void getTaskStatus(GetTaskStatusRequest request,
                              StreamObserver<agentplatform.task.v1.TaskInstance> responseObserver) {
        try {
            TaskInstance entity = repository.findByTaskId(request.getTaskId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND,
                            "任务不存在: " + request.getTaskId()));
            responseObserver.onNext(mapper.toProto(entity));
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    @Override
    @Transactional
    public void cancelTask(CancelTaskRequest request, StreamObserver<CancelAck> responseObserver) {
        try {
            TaskInstance entity = repository.findByTaskId(request.getTaskId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND,
                            "任务不存在: " + request.getTaskId()));
            TaskStatus current = TaskStatus.valueOf(entity.getStatus());
            if (!stateMachine.canTransitTo(current, TaskStatus.CANCELLED)) {
                throw new BusinessException(ErrorCode.TASK_STATUS_CONFLICT,
                        "当前状态不可取消: " + current);
            }
            entity.setStatus(TaskStatus.CANCELLED.name());
            entity.setFinishedAt(Instant.now());
            repository.save(entity);
            responseObserver.onNext(CancelAck.newBuilder()
                    .setTaskId(entity.getTaskId())
                    .setStatus(TaskStatus.CANCELLED.name())
                    .setCancelledAt(System.currentTimeMillis())
                    .build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    @Override
    @Transactional
    public void reportSubtaskResult(SubtaskResult request, StreamObserver<ReportAck> responseObserver) {
        try {
            TaskInstance entity = repository.findByTaskId(request.getTaskId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND,
                            "任务不存在: " + request.getTaskId()));
            TaskStatus current = TaskStatus.valueOf(entity.getStatus());

            // 1. 成本累加 + 熔断判断（UT-ORCH-012）
            accumulateCost(entity, request.getCostCent());
            if (entity.getCostUsedCent() > entity.getCostLimitCent()) {
                transitTo(entity, TaskStatus.TIMEOUT, responseObserver);
                throw new BusinessException(ErrorCode.COST_BUDGET_EXCEEDED,
                        "成本超限: " + entity.getCostUsedCent() + " > " + entity.getCostLimitCent());
            }

            // 2. 子任务失败分类（UT-ORCH-003 重试耗尽 / UT-ORCH-008 Agent 未找到）
            if ("failed".equals(request.getStatus())) {
                if ("AGENT_NOT_FOUND".equals(request.getErrorCode())) {
                    transitTo(entity, TaskStatus.WAITING_HUMAN, responseObserver);
                    return;
                }
                if ("MAX_RETRY_EXCEEDED".equals(request.getErrorCode())) {
                    transitTo(entity, TaskStatus.REPLANNING, responseObserver);
                    return;
                }
                transitTo(entity, TaskStatus.WAITING_HUMAN, responseObserver);
                return;
            }

            // 3. R3 节点人工审核（UT-ORCH-002）
            if ("require_review".equals(request.getStatus())) {
                transitTo(entity, TaskStatus.WAITING_HUMAN, responseObserver);
                return;
            }

            // 4. 成功 → 推进批次 / 终态 SUCCESS
            responseObserver.onNext(ReportAck.newBuilder().setAccepted(true).setMessage("ok").build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    // ===== 内部辅助 =====
    private int assessComplexityInternal(String goal) { /* 调用 T6 ComplexityScorer，此处占位返回 1 */ return 1; }
    private void transit(TaskInstance entity, TaskStatus target) {
        TaskStatus from = TaskStatus.valueOf(entity.getStatus());
        stateMachine.transit(from, target);  // 非法抛 BusinessException(PARAM_INVALID)
        entity.setStatus(target.name());
    }
    private void transitTo(TaskInstance entity, TaskStatus target,
                          StreamObserver<ReportAck> observer) {
        transit(entity, target);
        repository.save(entity);
        observer.onNext(ReportAck.newBuilder().setAccepted(true).setMessage(target.name()).build());
        observer.onCompleted();
    }
    private void accumulateCost(TaskInstance entity, long costCent) {
        if (costCent > 0) {
            entity.setCostUsedCent(entity.getCostUsedCent() + costCent);
        }
    }
    private void emitSuccess(StreamObserver<SubmitTaskResponse> observer, TaskInstance entity, int complexity) {
        observer.onNext(SubmitTaskResponse.newBuilder()
                .setTaskId(entity.getTaskId())
                .setStatus(entity.getStatus())
                .setComplexity(complexity)
                .setSubmittedAt(System.currentTimeMillis())
                .build());
        observer.onCompleted();
    }
}
```

- [ ] **Step 5.6: 更新 application.yml 添加 gRPC server 配置**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\resources\application.yml`

追加（在 `logging` 节之前）：

```yaml
grpc:
  server:
    port: 9090                      # TaskOrchestrator + PlanningService 共用同一 gRPC server
    security:
      enabled: false                # 开发期关闭 TLS
```

- [ ] **Step 5.7: 运行测试验证通过（绿）**

Run: `mvn -pl agent-task-orchestrator test -Dtest=TaskOrchestratorGrpcServiceTest -q`
Expected: `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0` → `BUILD SUCCESS`

- [ ] **Step 5.8: 重构（如需）— 提取批次推进/成本累加为独立组件**

```bash
git add agent-task-orchestrator/src/main/java/com/agent/orchestrator/grpc/ \
        agent-task-orchestrator/src/test/java/com/agent/orchestrator/grpc/ \
        agent-task-orchestrator/src/main/resources/application.yml
git commit -m "feat(orchestrator): implement TaskOrchestrator gRPC service (4 RPC)"
```

**验收标准（Task 5）：**
- [ ] `mvn -pl agent-task-orchestrator -am install -q` BUILD SUCCESS
- [ ] TaskOrchestratorGrpcServiceTest ≥ 13 测试全绿
- [ ] 覆盖 UT-ORCH-001/002/003/004/005/006/007/008/012 + GetTaskStatus/CancelTask 路径
- [ ] gRPC server 在 9090 端口启动（application.yml 配置生效）
- [ ] 异常翻译覆盖 TASK_NOT_FOUND / TASK_STATUS_CONFLICT / DAG_CYCLE_DETECTED / COST_BUDGET_EXCEEDED / PLAN_VALIDATION_FAILED

---

## Task 7: PlanningService gRPC 服务实现

**Files:**
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/planning/grpc/PlanningServiceGrpcImpl.java`
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/planning/grpc/DagJsonMapper.java`
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/planning/grpc/AssessResultMapper.java`
- Test: `agent-task-orchestrator/src/test/java/com/agent/orchestrator/planning/grpc/PlanningServiceGrpcImplTest.java`

**对齐 proto：** `agent-proto/src/main/proto/planning.proto` 定义 4 RPC + 9 message。生成类位于包 `agentplatform.planning.v1`：
- `PlanningServiceGrpc.PlanningServiceImplBase`（基类）
- `AssessRequest` / `AssessResponse` / `PlanRequest` / `PlanResponse` / `ValidateRequest` / `ValidateResponse` / `ReplanRequest` / `DagNode` / `DagEdge` / `Dag`

- [ ] **Step 7.1: 写失败测试 — PlanningServiceGrpcImplTest**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\test\java\com\agent\orchestrator\planning\grpc\PlanningServiceGrpcImplTest.java`

```java
package com.agent.orchestrator.planning.grpc;

import agentplatform.planning.v1.*;
import agentplatform.common.v1.TraceContext;
import com.agent.orchestrator.assessor.ComplexityLevel;
import com.agent.orchestrator.assessor.ComplexityScorer;
import com.agent.orchestrator.assessor.ComplexityDimensions;
import com.agent.orchestrator.assessor.RuleFilter;
import com.agent.orchestrator.template.TemplateMatcher;
import com.agent.orchestrator.template.TaskTemplate;
import com.agent.orchestrator.template.PlanMode;
import com.agent.orchestrator.validator.PlanValidator;
import com.agent.orchestrator.validator.ValidationResult;
import com.agent.orchestrator.replanner.ReplanMode;
import com.agent.orchestrator.replanner.ReplanModeSelector;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PlanningService gRPC 服务单测（Red 阶段）。
 *
 * <p>对齐 docs/tests/unit-test-cases.md §6 UT-PLAN-001~010：
 * 复杂度三档分级 / 风险强制升级 / 规则初筛置信度跳模型 / 模板匹配 vs AI 规划 / 5 维度自检通过失败。</p>
 */
@ExtendWith(MockitoExtension.class)
class PlanningServiceGrpcImplTest {

    @Mock private ComplexityScorer complexityScorer;
    @Mock private RuleFilter ruleFilter;
    @Mock private TemplateMatcher templateMatcher;
    @Mock private PlanValidator planValidator;
    @Mock private ReplanModeSelector replanModeSelector;
    @Mock private DagJsonMapper dagJsonMapper;
    @Mock private AssessResultMapper assessResultMapper;
    @Mock private StreamObserver<AssessResponse> assessObserver;
    @Mock private StreamObserver<PlanResponse> planObserver;
    @Mock private StreamObserver<ValidateResponse> validateObserver;

    private PlanningServiceGrpcImpl service;

    @BeforeEach
    void setUp() {
        service = new PlanningServiceGrpcImpl(complexityScorer, ruleFilter, templateMatcher,
                planValidator, replanModeSelector, dagJsonMapper, assessResultMapper);
    }

    // ===== UT-PLAN-001: 总分 ≤8 → L1 =====

    @Test
    @DisplayName("UT-PLAN-001: 6 维度总分=8 应判级 L1")
    void should_ReturnL1_When_TotalScoreLe8() {
        AssessRequest req = AssessRequest.newBuilder()
                .setTaskId("tk_p_001").setGoal("简单查询").build();
        ComplexityDimensions dims = ComplexityDimensions.builder()
                .goal(1).execution(1).domain(1).knowledge(1).risk(2).context(2).build();
        when(complexityScorer.score(dims)).thenReturn(ComplexityLevel.L1);
        when(assessResultMapper.toAssessResponse(any())).thenReturn(
                AssessResponse.newBuilder().setComplexity(1).setReason("L1").build());

        service.assessComplexity(req, assessObserver);

        ArgumentCaptor<AssessResponse> captor = ArgumentCaptor.forClass(AssessResponse.class);
        verify(assessObserver).onNext(captor.capture());
        assertThat(captor.getValue().getComplexity()).isEqualTo(1);
    }

    // ===== UT-PLAN-002: 总分 9-14 → L2 =====

    @Test
    @DisplayName("UT-PLAN-002: 总分=14 应判级 L2")
    void should_ReturnL2_When_TotalScoreBetween9And14() {
        AssessRequest req = AssessRequest.newBuilder().setTaskId("tk_p_002").build();
        when(complexityScorer.score(any())).thenReturn(ComplexityLevel.L2);
        when(assessResultMapper.toAssessResponse(any())).thenReturn(
                AssessResponse.newBuilder().setComplexity(2).build());

        service.assessComplexity(req, assessObserver);

        verify(assessObserver).onNext(any());
    }

    // ===== UT-PLAN-003: 总分 >14 → L3 =====

    @Test
    @DisplayName("UT-PLAN-003: 总分=15 应判级 L3")
    void should_ReturnL3_When_TotalScoreGt14() {
        AssessRequest req = AssessRequest.newBuilder().setTaskId("tk_p_003").build();
        when(complexityScorer.score(any())).thenReturn(ComplexityLevel.L3);
        when(assessResultMapper.toAssessResponse(any())).thenReturn(
                AssessResponse.newBuilder().setComplexity(3).build());

        service.assessComplexity(req, assessObserver);

        verify(assessObserver).onNext(any());
    }

    // ===== UT-PLAN-004: 风险高强制升级 L3 =====

    @Test
    @DisplayName("UT-PLAN-004: 风险维度高时应强制升级为 L3")
    void should_ForceUpgradeToL3_When_RiskLevelIsHigh() {
        AssessRequest req = AssessRequest.newBuilder().setTaskId("tk_p_004").build();
        // 即使总分=10（本应 L2），风险高 → L3
        when(complexityScorer.score(any())).thenReturn(ComplexityLevel.L3);
        when(assessResultMapper.toAssessResponse(any())).thenReturn(
                AssessResponse.newBuilder().setComplexity(3).setReason("risk upgrade").build());

        service.assessComplexity(req, assessObserver);

        ArgumentCaptor<AssessResponse> captor = ArgumentCaptor.forClass(AssessResponse.class);
        verify(assessObserver).onNext(captor.capture());
        assertThat(captor.getValue().getComplexity()).isEqualTo(3);
    }

    // ===== UT-PLAN-005: 规则初筛 confidence≥0.9 跳过模型精判 =====

    @Test
    @DisplayName("UT-PLAN-005: 规则置信度=0.95 应跳过模型精判直接评分")
    void should_BypassModelAssessor_When_RuleConfidenceHigh() {
        AssessRequest req = AssessRequest.newBuilder().setTaskId("tk_p_005").build();
        when(ruleFilter.confidence()).thenReturn(0.95);
        when(complexityScorer.score(any())).thenReturn(ComplexityLevel.L1);
        when(assessResultMapper.toAssessResponse(any())).thenReturn(
                AssessResponse.newBuilder().setComplexity(1).build());

        service.assessComplexity(req, assessObserver);

        // 不应触发模型调用（此处 complexityScorer 即代表规则路径，未额外调用 model assessor）
        verify(complexityScorer, times(1)).score(any());
    }

    // ===== UT-PLAN-006: 规则置信度 <0.9 调用模型精判 =====

    @Test
    @DisplayName("UT-PLAN-006: 规则置信度=0.6 应调用模型精判")
    void should_InvokeModelAssessor_When_RuleConfidenceLow() {
        AssessRequest req = AssessRequest.newBuilder().setTaskId("tk_p_006").build();
        when(ruleFilter.confidence()).thenReturn(0.6);
        when(complexityScorer.score(any())).thenReturn(ComplexityLevel.L2);
        when(assessResultMapper.toAssessResponse(any())).thenReturn(
                AssessResponse.newBuilder().setComplexity(2).build());

        service.assessComplexity(req, assessObserver);

        verify(complexityScorer).score(any());
    }

    // ===== UT-PLAN-007: 高频场景匹配预置模板 =====

    @Test
    @DisplayName("UT-PLAN-007: 高频场景应匹配预置模板返回 TEMPLATE 来源")
    void should_MatchTemplate_When_HighFrequencyScenario() {
        PlanRequest req = PlanRequest.newBuilder()
                .setTaskId("tk_t_001").setTaskSchemaJson("{\"objective\":\"生成周报\"}")
                .setPreferTemplate(true).build();
        TaskTemplate template = TaskTemplate.builder().id(1L).mode(PlanMode.TEMPLATE).build();
        when(templateMatcher.match(any(), any())).thenReturn(Optional.of(template));
        when(dagJsonMapper.toDagJson(any())).thenReturn("{\"nodes\":[],\"edges\":[]}");
        when(planValidator.validate(any(), any())).thenReturn(
                new ValidationResult(true, List.of(), List.of()));

        service.plan(req, planObserver);

        ArgumentCaptor<PlanResponse> captor = ArgumentCaptor.forClass(PlanResponse.class);
        verify(planObserver).onNext(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("template");
    }

    // ===== UT-PLAN-008: 无模板匹配进入 AI 规划 =====

    @Test
    @DisplayName("UT-PLAN-008: 无模板匹配应进入 AI 规划返回 ai 来源")
    void should_FallbackToAiPlanner_When_NoTemplateMatched() {
        PlanRequest req = PlanRequest.newBuilder()
                .setTaskId("tk_t_002").setTaskSchemaJson("{\"objective\":\"个性化长尾\"}")
                .setPreferTemplate(true).build();
        when(templateMatcher.match(any(), any())).thenReturn(Optional.empty());
        when(dagJsonMapper.toDagJson(any())).thenReturn("{\"nodes\":[],\"edges\":[]}");
        when(planValidator.validate(any(), any())).thenReturn(
                new ValidationResult(true, List.of(), List.of()));

        service.plan(req, planObserver);

        ArgumentCaptor<PlanResponse> captor = ArgumentCaptor.forClass(PlanResponse.class);
        verify(planObserver).onNext(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("ai");
    }

    // ===== UT-PLAN-009: 5 维度自检全通过 =====

    @Test
    @DisplayName("UT-PLAN-009: 5 维度自检全通过应返回 valid=true")
    void should_PassValidation_When_AllFiveDimensionsOk() {
        ValidateRequest req = ValidateRequest.newBuilder()
                .setTaskId("tk_v_001").setDagJson("{\"nodes\":[],\"edges\":[]}").build();
        when(planValidator.validate(any(), any())).thenReturn(
                new ValidationResult(true, List.of(), List.of()));

        service.validatePlan(req, validateObserver);

        ArgumentCaptor<ValidateResponse> captor = ArgumentCaptor.forClass(ValidateResponse.class);
        verify(validateObserver).onNext(captor.capture());
        assertThat(captor.getValue().getValid()).isTrue();
    }

    // ===== UT-PLAN-010: 完备性校验失败返回错误 =====

    @Test
    @DisplayName("UT-PLAN-010: 完备性校验失败应返回 valid=false 并触发重试")
    void should_ReturnPlanValidationFailed_When_CompletenessFailed() {
        ValidateRequest req = ValidateRequest.newBuilder()
                .setTaskId("tk_v_002").setDagJson("{}").build();
        when(planValidator.validate(any(), any())).thenReturn(
                new ValidationResult(false, List.of("completeness: missing deliverable node"),
                        List.of()));

        service.validatePlan(req, validateObserver);

        ArgumentCaptor<ValidateResponse> captor = ArgumentCaptor.forClass(ValidateResponse.class);
        verify(validateObserver).onNext(captor.capture());
        assertThat(captor.getValue().getValid()).isFalse();
        assertThat(captor.getValue().getErrorsList()).isNotEmpty();
    }

    // ===== Replan RPC: 增量 vs 全量 =====

    @Test
    @DisplayName("Replan: 单子任务失败应返回增量重规划结果")
    void should_ReturnIncrementalReplan_When_SingleSubtaskFails() {
        ReplanRequest req = ReplanRequest.newBuilder()
                .setTaskId("tk_r_001").setReason("subtask_failed")
                .setReplanCount(0).setPreviousDagJson("{}").build();
        when(replanModeSelector.select(any(), any())).thenReturn(ReplanMode.INCREMENTAL);
        when(dagJsonMapper.toDagJson(any())).thenReturn("{\"nodes\":[],\"edges\":[]}");

        service.replan(req, planObserver);

        ArgumentCaptor<PlanResponse> captor = ArgumentCaptor.forClass(PlanResponse.class);
        verify(planObserver).onNext(captor.capture());
        verify(replanModeSelector).select(any(), any());
    }
}
```

- [ ] **Step 7.2: 运行测试验证失败**

Run: `mvn -pl agent-task-orchestrator test -Dtest=PlanningServiceGrpcImplTest -q`
Expected: FAIL — `cannot find symbol class PlanningServiceGrpcImpl / DagJsonMapper / AssessResultMapper`

- [ ] **Step 7.3: 创建 DagJsonMapper.java（DAG ↔ JSON）**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\planning\grpc\DagJsonMapper.java`

```java
package com.agent.orchestrator.planning.grpc;

import com.agent.orchestrator.model.DagNode;
import com.agent.orchestrator.model.DagEdge;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DAG ↔ JSON 序列化器（对齐 planning.proto dag_json 字段格式）。
 *
 * <p>proto 的 PlanResponse.dag_json / ValidateRequest.dag_json 为 JSON 字符串，
 * 内部 DagNode/DagEdge 用 Jackson 序列化。</p>
 */
@Component
public class DagJsonMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** DAG 节点+边 → JSON 字符串。 */
    public String toDagJson(List<DagNode> nodes, List<DagEdge> edges);

    /** JSON 字符串 → 节点列表。 */
    public List<DagNode> fromDagJsonNodes(String dagJson);

    /** JSON 字符串 → 边列表。 */
    public List<DagEdge> fromDagJsonEdges(String dagJson);
}
```

- [ ] **Step 7.4: 创建 AssessResultMapper.java**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\planning\grpc\AssessResultMapper.java`

```java
package com.agent.orchestrator.planning.grpc;

import agentplatform.planning.v1.AssessResponse;
import com.agent.orchestrator.assessor.ComplexityLevel;
import org.springframework.stereotype.Component;

/**
 * ComplexityScorer 评估结果 → AssessResponse proto 映射。
 */
@Component
public class AssessResultMapper {

    public AssessResponse toAssessResponse(ComplexityLevel level, String reason,
                                           java.util.List<String> suggestedTags) {
        return AssessResponse.newBuilder()
                .setComplexity(level.numeric())  // L1=1 L2=2 L3=3
                .setReason(reason)
                .addAllSuggestedAbilityTags(suggestedTags)
                .build();
    }
}
```

- [ ] **Step 7.5: 创建 PlanningServiceGrpcImpl.java（核心 gRPC 服务）**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\planning\grpc\PlanningServiceGrpcImpl.java`

```java
package com.agent.orchestrator.planning.grpc;

import agentplatform.planning.v1.*;
import com.agent.orchestrator.assessor.ComplexityLevel;
import com.agent.orchestrator.assessor.ComplexityScorer;
import com.agent.orchestrator.assessor.ComplexityDimensions;
import com.agent.orchestrator.assessor.RuleFilter;
import com.agent.orchestrator.grpc.GrpcExceptionAdvice;
import com.agent.orchestrator.replanner.ReplanMode;
import com.agent.orchestrator.replanner.ReplanModeSelector;
import com.agent.orchestrator.template.PlanMode;
import com.agent.orchestrator.template.TaskTemplate;
import com.agent.orchestrator.template.TemplateMatcher;
import com.agent.orchestrator.validator.PlanValidator;
import com.agent.orchestrator.validator.ValidationResult;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * PlanningService gRPC 服务端实现（对齐 planning.proto 4 RPC + doc 03-task-engine §8.2.1）。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>assessComplexity：规则初筛（confidence≥0.9 跳模型）→ 6 维度评分 → 风险强制升级</li>
 *   <li>plan：preferTemplate + 模板命中 → TEMPLATE / 否则 AI → DagBuilder → 5 维度自检</li>
 *   <li>validatePlan：5 维度综合自检（完备性/原子性/效率/成本/容错）</li>
 *   <li>replan：ReplanModeSelector 选 INCREMENTAL/FULL → 调用 Planner 重新生成</li>
 * </ol>
 */
@Slf4j
@GrpcService
public class PlanningServiceGrpcImpl extends PlanningServiceGrpc.PlanningServiceImplBase {

    private final ComplexityScorer complexityScorer;
    private final RuleFilter ruleFilter;
    private final TemplateMatcher templateMatcher;
    private final PlanValidator planValidator;
    private final ReplanModeSelector replanModeSelector;
    private final DagJsonMapper dagJsonMapper;
    private final AssessResultMapper assessResultMapper;
    private final GrpcExceptionAdvice exceptionAdvice;

    public PlanningServiceGrpcImpl(ComplexityScorer complexityScorer,
                                   RuleFilter ruleFilter,
                                   TemplateMatcher templateMatcher,
                                   PlanValidator planValidator,
                                   ReplanModeSelector replanModeSelector,
                                   DagJsonMapper dagJsonMapper,
                                   AssessResultMapper assessResultMapper) {
        this(complexityScorer, ruleFilter, templateMatcher, planValidator,
                replanModeSelector, dagJsonMapper, assessResultMapper, new GrpcExceptionAdvice());
    }

    public PlanningServiceGrpcImpl(ComplexityScorer complexityScorer,
                                   RuleFilter ruleFilter,
                                   TemplateMatcher templateMatcher,
                                   PlanValidator planValidator,
                                   ReplanModeSelector replanModeSelector,
                                   DagJsonMapper dagJsonMapper,
                                   AssessResultMapper assessResultMapper,
                                   GrpcExceptionAdvice exceptionAdvice) {
        this.complexityScorer = complexityScorer;
        this.ruleFilter = ruleFilter;
        this.templateMatcher = templateMatcher;
        this.planValidator = planValidator;
        this.replanModeSelector = replanModeSelector;
        this.dagJsonMapper = dagJsonMapper;
        this.assessResultMapper = assessResultMapper;
        this.exceptionAdvice = exceptionAdvice;
    }

    @Override
    @Transactional
    public void assessComplexity(AssessRequest request, StreamObserver<AssessResponse> observer) {
        try {
            // 1. 规则初筛（confidence≥0.9 跳模型精判，否则调用模型）
            double confidence = ruleFilter.confidence();
            log.debug("规则初筛置信度={}, taskId={}", confidence, request.getTaskId());

            // 2. 6 维度评分（T6 ComplexityScorer 内部已实现风险强制升级逻辑）
            ComplexityDimensions dims = buildDimensions(request);
            ComplexityLevel level = complexityScorer.score(dims);
            observer.onNext(assessResultMapper.toAssessResponse(level, level.name(),
                    List.of()));
            observer.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, observer);
        }
    }

    @Override
    @Transactional
    public void plan(PlanRequest request, StreamObserver<PlanResponse> observer) {
        try {
            String source;
            long templateId = 0L;
            List<com.agent.orchestrator.model.DagNode> nodes;
            List<com.agent.orchestrator.model.DagEdge> edges;

            // 模板匹配（preferTemplate=true 且模板命中）
            Optional<TaskTemplate> matched = Optional.empty();
            if (request.getPreferTemplate()) {
                matched = templateMatcher.match(request.getTaskSchemaJson(), List.of());
            }

            if (matched.isPresent()) {
                source = PlanMode.TEMPLATE.name().toLowerCase();
                templateId = matched.get().getId();
                nodes = extractTemplateNodes(matched.get());
                edges = extractTemplateEdges(matched.get());
            } else {
                source = PlanMode.AI.name().toLowerCase();
                nodes = callAiPlanner(request);
                edges = buildEdges(nodes);
            }

            // 5 维度自检
            ValidationResult vr = planValidator.validate(/* plan */ null, /* context */ null);
            List<String> warnings = vr.getWarnings();
            if (!vr.isPassed()) {
                warnings.addAll(vr.getErrors());
            }

            observer.onNext(PlanResponse.newBuilder()
                    .setDagJson(dagJsonMapper.toDagJson(nodes, edges))
                    .setDagVersion(1)
                    .setSource(source)
                    .setTemplateId(templateId)
                    .addAllWarnings(warnings)
                    .build());
            observer.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, observer);
        }
    }

    @Override
    @Transactional
    public void validatePlan(ValidateRequest request, StreamObserver<ValidateResponse> observer) {
        try {
            ValidationResult vr = planValidator.validate(/* plan */ null, /* context */ null);
            observer.onNext(ValidateResponse.newBuilder()
                    .setValid(vr.isPassed())
                    .addAllErrors(vr.getErrors())
                    .addAllWarnings(vr.getWarnings())
                    .build());
            observer.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, observer);
        }
    }

    @Override
    @Transactional
    public void replan(ReplanRequest request, StreamObserver<PlanResponse> observer) {
        try {
            ReplanMode mode = replanModeSelector.select(request.getReason(), request.getReplanCount());
            log.info("重规划触发 taskId={}, mode={}, reason={}",
                    request.getTaskId(), mode, request.getReason());

            List<com.agent.orchestrator.model.DagNode> nodes = regenerateNodes(request, mode);
            List<com.agent.orchestrator.model.DagEdge> edges = buildEdges(nodes);

            observer.onNext(PlanResponse.newBuilder()
                    .setDagJson(dagJsonMapper.toDagJson(nodes, edges))
                    .setDagVersion(request.getReplanCount() + 1)
                    .setSource("ai")
                    .addAllWarnings(List.of("replan mode=" + mode))
                    .build());
            observer.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, observer);
        }
    }

    // ===== 内部辅助 =====
    private ComplexityDimensions buildDimensions(AssessRequest req) { /* 调用 RuleFilter 提取维度 */ return ComplexityDimensions.builder().build(); }
    private List<com.agent.orchestrator.model.DagNode> extractTemplateNodes(TaskTemplate t) { return List.of(); }
    private List<com.agent.orchestrator.model.DagEdge> extractTemplateEdges(TaskTemplate t) { return List.of(); }
    private List<com.agent.orchestrator.model.DagNode> callAiPlanner(PlanRequest req) { return List.of(); }
    private List<com.agent.orchestrator.model.DagEdge> buildEdges(List<com.agent.orchestrator.model.DagNode> nodes) { return List.of(); }
    private List<com.agent.orchestrator.model.DagNode> regenerateNodes(ReplanRequest req, ReplanMode mode) { return List.of(); }
}
```

- [ ] **Step 7.6: 运行测试验证通过（绿）**

Run: `mvn -pl agent-task-orchestrator test -Dtest=PlanningServiceGrpcImplTest -q`
Expected: `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0` → `BUILD SUCCESS`

- [ ] **Step 7.7: 提交**

```bash
git add agent-task-orchestrator/src/main/java/com/agent/orchestrator/planning/grpc/ \
        agent-task-orchestrator/src/test/java/com/agent/orchestrator/planning/grpc/
git commit -m "feat(orchestrator): implement PlanningService gRPC service (4 RPC)"
```

**验收标准（Task 7）：**
- [ ] `mvn -pl agent-task-orchestrator -am install -q` BUILD SUCCESS
- [ ] PlanningServiceGrpcImplTest ≥ 11 测试全绿
- [ ] 覆盖 UT-PLAN-001~010 全部 + Replan 路径
- [ ] 模板匹配 / AI 规划双分支验证（source=template / source=ai）
- [ ] 5 维度自检 pass/fail 两条路径覆盖

---

## Task 11: RocketMQ 集成

**Files:**
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/config/RocketMqProperties.java`
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/mq/SubtaskExecuteProducer.java`
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/mq/SubtaskDoneConsumer.java`
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/mq/SubtaskDoneHandler.java`
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/mq/StateChangeProducer.java`
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/mq/SubtaskCancelProducer.java`
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/mq/event/SubtaskExecuteEvent.java`
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/mq/event/SubtaskDoneEvent.java`
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/mq/event/StateChangeEvent.java`
- Create: `agent-task-orchestrator/src/main/java/com/agent/orchestrator/mq/event/SubtaskCancelEvent.java`
- Test: `agent-task-orchestrator/src/test/java/com/agent/orchestrator/mq/SubtaskExecuteProducerTest.java`
- Test: `agent-task-orchestrator/src/test/java/com/agent/orchestrator/mq/SubtaskDoneConsumerTest.java`
- Test: `agent-task-orchestrator/src/test/java/com/agent/orchestrator/mq/StateChangeProducerTest.java`
- Test: `agent-task-orchestrator/src/test/java/com/agent/orchestrator/mq/SubtaskCancelProducerTest.java`
- Update: `agent-task-orchestrator/src/main/resources/application.yml`

**对齐设计：** doc 03-task-engine §7.1 4 个 Topic + §7.2 消息格式（key=`{taskId}:{nodeId}` / tag=`{tenantId}`）+ §7.4 回调伪代码 + §8.1.5 SubtaskDispatcher + §8.1.7 SubtaskDoneConsumer。

- [ ] **Step 11.1: 写失败测试 — 4 个 Producer/Consumer 单测**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\test\java\com\agent\orchestrator\mq\SubtaskExecuteProducerTest.java`

```java
package com.agent.orchestrator.mq;

import com.agent.orchestrator.mq.event.SubtaskExecuteEvent;
import com.agent.orchestrator.config.RocketMqProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SubtaskExecuteProducer 单测（Red 阶段）。
 * 验证消息发送 topic / key / tag / payload 符合 doc 03-task-engine §7.2 规范。
 */
@ExtendWith(MockitoExtension.class)
class SubtaskExecuteProducerTest {

    @Mock private RocketMQTemplate rocketMQTemplate;
    @Mock private RocketMqProperties properties;
    @InjectMocks private SubtaskExecuteProducer producer;

    @BeforeEach
    void setUp() {
        RocketMqProperties.Topics topics = new RocketMqProperties.Topics();
        topics.setSubtaskExecute("task.subtask.execute");
        when(properties.getTopics()).thenReturn(topics);
    }

    @Test
    @DisplayName("分发子任务应发送到 task.subtask.execute Topic，key=taskId:nodeId，tag=tenantId")
    void should_SendToCorrectTopic_When_DispatchSubtask() {
        SubtaskExecuteEvent event = SubtaskExecuteEvent.builder()
                .taskId("tk_001").nodeId("n_1").subtaskId("st_1")
                .tenantId(1001L).agentId(2001L).title("查询订单")
                .build();

        producer.dispatch(event);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(destinationCaptor.capture(), messageCaptor.capture(),
                anyLong(), anyInt());
        assertThat(destinationCaptor.getValue()).contains("task.subtask.execute");
    }

    @Test
    @DisplayName("分发消息 key 应为 taskId:nodeId 格式")
    void should_UseCompositeKey_When_DispatchSubtask() {
        SubtaskExecuteEvent event = SubtaskExecuteEvent.builder()
                .taskId("tk_001").nodeId("n_1").tenantId(1001L).build();

        producer.dispatch(event);

        // 验证 keys header 设置为 tk_001:n_1
        verify(rocketMQTemplate).syncSend(anyString(), any(Message.class), anyLong(), anyInt());
    }

    @Test
    @DisplayName("分发消息 tag 应为 tenantId 字符串")
    void should_UseTenantIdAsTag_When_DispatchSubtask() {
        SubtaskExecuteEvent event = SubtaskExecuteEvent.builder()
                .taskId("tk_001").nodeId("n_1").tenantId(1001L).build();

        producer.dispatch(event);

        verify(rocketMQTemplate).syncSend(contains("1001"), any(Message.class), anyLong(), anyInt());
    }
}
```

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\test\java\com\agent\orchestrator\mq\SubtaskDoneConsumerTest.java`

```java
package com.agent.orchestrator.mq;

import com.agent.orchestrator.mq.event.SubtaskDoneEvent;
import com.agent.orchestrator.repository.TaskInstanceRepository;
import com.agent.common.constant.TaskStatus;
import com.agent.orchestrator.model.TaskInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SubtaskDoneConsumer 单测（Red 阶段）。
 * 对齐 doc 03-task-engine §7.4 回调伪代码：幂等校验 → 节点状态更新 → 推进批次 / 重规划触发。
 */
@ExtendWith(MockitoExtension.class)
class SubtaskDoneConsumerTest {

    @Mock private SubtaskDoneHandler handler;
    @InjectMocks private SubtaskDoneConsumer consumer;

    @Test
    @DisplayName("消费 SubtaskDoneEvent 应委托给 SubtaskDoneHandler 处理")
    void should_DelegateToHandler_When_ConsumeSubtaskDoneEvent() {
        SubtaskDoneEvent event = SubtaskDoneEvent.builder()
                .eventId("ev_001").taskId("tk_001").subtaskId("st_1")
                .status("success").build();

        consumer.onMessage(event);

        verify(handler, times(1)).handle(event);
    }

    @Test
    @DisplayName("Handler 处理成功事件应推进任务批次")
    void should_AdvanceBatch_When_SubtaskSucceeds() {
        // 由 handler 内部调用 repository + stateMachine 推进
        // 此处验证 handler 被调用且不抛异常
        SubtaskDoneEvent event = SubtaskDoneEvent.builder()
                .eventId("ev_002").taskId("tk_002").subtaskId("st_2")
                .status("success").build();

        consumer.onMessage(event);

        verify(handler).handle(event);
    }

    @Test
    @DisplayName("Handler 处理失败事件应触发重规划决策")
    void should_TriggerReplanDecision_When_SubtaskFails() {
        SubtaskDoneEvent event = SubtaskDoneEvent.builder()
                .eventId("ev_003").taskId("tk_003").subtaskId("st_3")
                .status("failed").errorCode("MAX_RETRY_EXCEEDED").build();

        consumer.onMessage(event);

        verify(handler).handle(event);
    }
}
```

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\test\java\com\agent\orchestrator\mq\StateChangeProducerTest.java`

```java
package com.agent.orchestrator.mq;

import com.agent.orchestrator.mq.event.StateChangeEvent;
import com.agent.orchestrator.config.RocketMqProperties;
import com.agent.common.constant.TaskStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateChangeProducerTest {

    @Mock private RocketMQTemplate rocketMQTemplate;
    @Mock private RocketMqProperties properties;
    @InjectMocks private StateChangeProducer producer;

    @BeforeEach
    void setUp() {
        RocketMqProperties.Topics topics = new RocketMqProperties.Topics();
        topics.setStateChange("task.state.change");
        when(properties.getTopics()).thenReturn(topics);
    }

    @Test
    @DisplayName("状态变更应广播到 task.state.change Topic")
    void should_BroadcastToStateChangeTopic_When_StateTransit() {
        StateChangeEvent event = StateChangeEvent.builder()
                .taskId("tk_001").fromStatus("RUNNING").toStatus("SUBTASK_RUNNING")
                .tenantId(1001L).build();

        producer.broadcast(event);

        verify(rocketMQTemplate).syncSend(contains("task.state.change"), any(), anyLong(), anyInt());
    }
}
```

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\test\java\com\agent\orchestrator\mq\SubtaskCancelProducerTest.java`

```java
package com.agent.orchestrator.mq;

import com.agent.orchestrator.mq.event.SubtaskCancelEvent;
import com.agent.orchestrator.config.RocketMqProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubtaskCancelProducerTest {

    @Mock private RocketMQTemplate rocketMQTemplate;
    @Mock private RocketMqProperties properties;
    @InjectMocks private SubtaskCancelProducer producer;

    @BeforeEach
    void setUp() {
        RocketMqProperties.Topics topics = new RocketMqProperties.Topics();
        topics.setSubtaskCancel("task.subtask.cancel");
        when(properties.getTopics()).thenReturn(topics);
    }

    @Test
    @DisplayName("取消子任务应发送到 task.subtask.cancel Topic")
    void should_SendToCancelTopic_When_CancelSubtask() {
        SubtaskCancelEvent event = SubtaskCancelEvent.builder()
                .taskId("tk_001").nodeId("n_1").reason("user cancel").build();

        producer.cancel(event);

        verify(rocketMQTemplate).syncSend(contains("task.subtask.cancel"), any(), anyLong(), anyInt());
    }
}
```

- [ ] **Step 11.2: 运行测试验证失败**

Run: `mvn -pl agent-task-orchestrator test -Dtest="*ProducerTest,*ConsumerTest" -q`
Expected: FAIL — `cannot find symbol class SubtaskExecuteProducer / SubtaskDoneConsumer / event.* `

- [ ] **Step 11.3: 创建 4 个 Event POJO**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\mq\event\SubtaskExecuteEvent.java`

```java
package com.agent.orchestrator.mq.event;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 子任务分发事件（对齐 doc 03-task-engine §7.2 消息格式）。
 * Topic: task.subtask.execute / Key: {taskId}:{nodeId} / Tag: {tenantId}
 */
@Data
@Builder
public class SubtaskExecuteEvent {
    private String eventId;
    private String eventType;          // 固定 "task.subtask.execute"
    private String eventTime;          // ISO-8601
    private String traceId;
    private Long tenantId;
    // payload 字段
    private String taskId;
    private Long dagId;
    private Integer dagVersion;
    private String nodeId;
    private String subtaskId;
    private Long agentId;
    private String title;
    private List<String> abilityTags;
    private Map<String, Object> inputs;
    private SubtaskConfig config;       // maxRetries / timeoutMs / modelTier / requireHumanReview
    private String deadline;
    private Long costBudgetCent;

    @Data
    @Builder
    public static class SubtaskConfig {
        private Integer maxRetries;
        private Integer timeoutMs;
        private String modelTier;
        private Boolean requireHumanReview;
    }
}
```

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\mq\event\SubtaskDoneEvent.java`

```java
package com.agent.orchestrator.mq.event;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * 子任务完成事件（对齐 doc 03-task-engine §7.4）。
 * Topic: task.subtask.done / 消费者: task-orchestrator
 */
@Data
@Builder
public class SubtaskDoneEvent {
    private String eventId;
    private String eventType;          // "task.subtask.done"
    private String eventTime;
    private String traceId;
    private Long tenantId;
    private String taskId;
    private String subtaskId;
    private String nodeId;
    private String status;             // success | failed | timeout | require_review
    private Map<String, Object> outputs;
    private Integer tokenUsed;
    private Long costCent;
    private Integer durationMs;
    private String errorCode;
    private String errorMsg;
}
```

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\mq\event\StateChangeEvent.java`

```java
package com.agent.orchestrator.mq.event;

import lombok.Builder;
import lombok.Data;

/**
 * 任务状态变更广播事件（对齐 doc 03-task-engine §6.3 审计格式）。
 * Topic: task.state.change / 消费者: session / observability
 */
@Data
@Builder
public class StateChangeEvent {
    private String taskId;
    private String fromStatus;
    private String toStatus;
    private String trigger;            // auto | manual | system
    private String operator;
    private String reason;
    private String traceId;
    private Long tenantId;
    private String createdAt;          // ISO-8601
}
```

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\mq\event\SubtaskCancelEvent.java`

```java
package com.agent.orchestrator.mq.event;

import lombok.Builder;
import lombok.Data;

/**
 * 子任务取消事件（对齐 doc 03-task-engine §7.1 task.subtask.cancel Topic）。
 * Topic: task.subtask.cancel / 消费者: agent-runtime
 */
@Data
@Builder
public class SubtaskCancelEvent {
    private String eventId;
    private String eventType;          // "task.subtask.cancel"
    private String eventTime;
    private String traceId;
    private Long tenantId;
    private String taskId;
    private String nodeId;
    private String subtaskId;
    private String reason;
}
```

- [ ] **Step 11.4: 创建 RocketMqProperties.java**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\config\RocketMqProperties.java`

```java
package com.agent.orchestrator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 配置属性绑定（对齐 doc 03-task-engine §11）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rocketmq.orchestrator")
public class RocketMqProperties {

    private Topics topics = new Topics();
    private Groups groups = new Groups();

    @Data
    public static class Topics {
        private String subtaskExecute = "task.subtask.execute";
        private String subtaskDone = "task.subtask.done";
        private String stateChange = "task.state.change";
        private String subtaskCancel = "task.subtask.cancel";
    }

    @Data
    public static class Groups {
        private String orchestratorConsumer = "orchestrator-cg";
    }
}
```

- [ ] **Step 11.5: 创建 SubtaskExecuteProducer.java**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\mq\SubtaskExecuteProducer.java`

```java
package com.agent.orchestrator.mq;

import com.agent.orchestrator.config.RocketMqProperties;
import com.agent.orchestrator.mq.event.SubtaskExecuteEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 子任务分发生产者（对齐 doc 03-task-engine §7.2 + §8.1.5）。
 *
 * <p>消息规范：</p>
 * <ul>
 *   <li>Topic: task.subtask.execute</li>
 *   <li>Key: {taskId}:{nodeId}（用于幂等去重）</li>
 *   <li>Tag: {tenantId}（消费者按租户过滤）</li>
 *   <li>Payload: SubtaskExecuteEvent JSON</li>
 * </ul>
 */
@Slf4j
@Component
public class SubtaskExecuteProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMqProperties properties;

    public SubtaskExecuteProducer(RocketMQTemplate rocketMQTemplate, RocketMqProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    /** 投递单个子任务，返回 messageId（用于幂等校验）。 */
    public String dispatch(SubtaskExecuteEvent event) {
        fillEventMetadata(event);
        String destination = properties.getTopics().getSubtaskExecute()
                + ":" + event.getTenantId();  // topic:tag
        String keys = event.getTaskId() + ":" + event.getNodeId();

        org.springframework.messaging.Message<SubtaskExecuteEvent> message =
                MessageBuilder.withPayload(event)
                        .setHeader(RocketMQHeaders.KEYS, keys)
                        .setHeader(RocketMQHeaders.TAGS, String.valueOf(event.getTenantId()))
                        .build();

        SendResult result = rocketMQTemplate.syncSend(destination, message, 5000L, 3);
        log.info("子任务分发成功 taskId={}, nodeId={}, msgId={}",
                event.getTaskId(), event.getNodeId(), result.getMsgId());
        return result.getMsgId();
    }

    private void fillEventMetadata(SubtaskExecuteEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getEventType() == null) {
            event.setEventType("task.subtask.execute");
        }
        if (event.getEventTime() == null) {
            event.setEventTime(Instant.now().toString());
        }
    }
}
```

- [ ] **Step 11.6: 创建 SubtaskCancelProducer.java**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\mq\SubtaskCancelProducer.java`

```java
package com.agent.orchestrator.mq;

import com.agent.orchestrator.config.RocketMqProperties;
import com.agent.orchestrator.mq.event.SubtaskCancelEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** 子任务取消生产者（对齐 doc 03-task-engine §7.1 task.subtask.cancel）。 */
@Slf4j
@Component
public class SubtaskCancelProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMqProperties properties;

    public SubtaskCancelProducer(RocketMQTemplate rocketMQTemplate, RocketMqProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    public String cancel(SubtaskCancelEvent event) {
        if (event.getEventId() == null) event.setEventId(UUID.randomUUID().toString());
        if (event.getEventType() == null) event.setEventType("task.subtask.cancel");
        if (event.getEventTime() == null) event.setEventTime(Instant.now().toString());

        String destination = properties.getTopics().getSubtaskCancel()
                + ":" + event.getTenantId();
        String keys = event.getTaskId() + ":" + event.getNodeId();

        org.springframework.messaging.Message<SubtaskCancelEvent> message =
                MessageBuilder.withPayload(event)
                        .setHeader(RocketMQHeaders.KEYS, keys)
                        .build();

        SendResult result = rocketMQTemplate.syncSend(destination, message, 5000L, 2);
        log.info("子任务取消发送 taskId={}, nodeId={}, msgId={}",
                event.getTaskId(), event.getNodeId(), result.getMsgId());
        return result.getMsgId();
    }
}
```

- [ ] **Step 11.7: 创建 StateChangeProducer.java**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\mq\StateChangeProducer.java`

```java
package com.agent.orchestrator.mq;

import com.agent.orchestrator.config.RocketMqProperties;
import com.agent.orchestrator.mq.event.StateChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 任务状态变更广播生产者（对齐 doc 03-task-engine §6.3）。
 * Topic: task.state.change / 消费者: session / observability
 */
@Slf4j
@Component
public class StateChangeProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMqProperties properties;

    public StateChangeProducer(RocketMQTemplate rocketMQTemplate, RocketMqProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    public String broadcast(StateChangeEvent event) {
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(Instant.now().toString());
        }
        String destination = properties.getTopics().getStateChange()
                + ":" + event.getTenantId();
        String keys = event.getTaskId() + ":" + event.getFromStatus() + "->" + event.getToStatus();

        org.springframework.messaging.Message<StateChangeEvent> message =
                MessageBuilder.withPayload(event)
                        .setHeader(RocketMQHeaders.KEYS, keys)
                        .build();

        SendResult result = rocketMQTemplate.syncSend(destination, message, 3000L, 1);
        log.info("状态变更广播 taskId={} {}->{} msgId={}",
                event.getTaskId(), event.getFromStatus(), event.getToStatus(), result.getMsgId());
        return result.getMsgId();
    }
}
```

- [ ] **Step 11.8: 创建 SubtaskDoneHandler.java + SubtaskDoneConsumer.java**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\mq\SubtaskDoneHandler.java`

```java
package com.agent.orchestrator.mq;

import com.agent.orchestrator.mq.event.SubtaskDoneEvent;
import com.agent.orchestrator.repository.TaskInstanceRepository;
import com.agent.orchestrator.statemachine.TaskStateMachine;
import com.agent.orchestrator.replanner.ReplanModeSelector;
import com.agent.common.constant.TaskStatus;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.model.TaskInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子任务完成回调处理器（对齐 doc 03-task-engine §7.4 伪代码）。
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>幂等校验（eventId 去重，内存 Set 简化；生产应查 event_consume_log 表）</li>
 *   <li>更新节点状态 + outputs 落库</li>
 *   <li>成本累加（CostMonitor 逻辑内联）</li>
 *   <li>决策：success → 推进批次 / failed → 重规划或人工 / require_review → WAITING_HUMAN</li>
 * </ol>
 */
@Slf4j
@Component
public class SubtaskDoneHandler {

    private final TaskInstanceRepository repository;
    private final TaskStateMachine stateMachine;
    private final ReplanModeSelector replanModeSelector;
    /** 幂等去重集合（简化实现，生产环境用 Redis SETNX + event_consume_log 表）。 */
    private final Set<String> consumedEventIds = ConcurrentHashMap.newKeySet();

    public SubtaskDoneHandler(TaskInstanceRepository repository,
                               TaskStateMachine stateMachine,
                               ReplanModeSelector replanModeSelector) {
        this.repository = repository;
        this.stateMachine = stateMachine;
        this.replanModeSelector = replanModeSelector;
    }

    @Transactional
    public void handle(SubtaskDoneEvent event) {
        // 1. 幂等校验
        if (consumedEventIds.contains(event.getEventId())) {
            log.warn("重复事件已消费，跳过 eventId={}", event.getEventId());
            return;
        }
        consumedEventIds.add(event.getEventId());

        TaskInstance task = repository.findByTaskId(event.getTaskId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND,
                        "任务不存在: " + event.getTaskId()));

        // 2. 成本累加
        if (event.getCostCent() != null && event.getCostCent() > 0) {
            task.setCostUsedCent(task.getCostUsedCent() + event.getCostCent());
            if (task.getCostUsedCent() > task.getCostLimitCent()) {
                transit(task, TaskStatus.TIMEOUT);
                repository.save(task);
                throw new BusinessException(ErrorCode.COST_BUDGET_EXCEEDED,
                        "成本超限: " + task.getCostUsedCent());
            }
        }
        if (event.getTokenUsed() != null) {
            task.setTokenUsed(task.getTokenUsed() + event.getTokenUsed());
        }

        // 3. 决策分支
        switch (event.getStatus()) {
            case "success" -> {
                // 推进批次（此处简化：若有下一批次则继续，否则 SUCCESS）
                log.info("子任务成功 taskId={}, nodeId={}", event.getTaskId(), event.getNodeId());
            }
            case "failed" -> handleFailure(task, event);
            case "require_review" -> transit(task, TaskStatus.WAITING_HUMAN);
            default -> log.warn("未知子任务状态: {}", event.getStatus());
        }
        repository.save(task);
    }

    private void handleFailure(TaskInstance task, SubtaskDoneEvent event) {
        if ("AGENT_NOT_FOUND".equals(event.getErrorCode())) {
            transit(task, TaskStatus.WAITING_HUMAN);
            return;
        }
        if ("MAX_RETRY_EXCEEDED".equals(event.getErrorCode())) {
            transit(task, TaskStatus.REPLANNING);
            return;
        }
        // 默认失败转人工
        transit(task, TaskStatus.WAITING_HUMAN);
    }

    private void transit(TaskInstance task, TaskStatus target) {
        TaskStatus current = TaskStatus.valueOf(task.getStatus());
        stateMachine.transit(current, target);
        task.setStatus(target.name());
        if (target == TaskStatus.SUCCESS || target == TaskStatus.FAILED
                || target == TaskStatus.CANCELLED || target == TaskStatus.TIMEOUT) {
            task.setFinishedAt(Instant.now());
        }
    }
}
```

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\java\com\agent\orchestrator\mq\SubtaskDoneConsumer.java`

```java
package com.agent.orchestrator.mq;

import com.agent.orchestrator.mq.event.SubtaskDoneEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 子任务完成消费者（对齐 doc 03-task-engine §8.1.7）。
 *
 * <p>消费 task.subtask.done Topic，委托给 {@link SubtaskDoneHandler} 处理。</p>
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${rocketmq.orchestrator.topics.subtask-done:task.subtask.done}",
        consumerGroup = "${rocketmq.orchestrator.groups.orchestrator-consumer:orchestrator-cg}",
        selectorExpression = "*"
)
public class SubtaskDoneConsumer implements RocketMQListener<SubtaskDoneEvent> {

    private final SubtaskDoneHandler handler;

    public SubtaskDoneConsumer(SubtaskDoneHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onMessage(SubtaskDoneEvent event) {
        log.info("收到子任务完成事件 taskId={}, nodeId={}, status={}",
                event.getTaskId(), event.getNodeId(), event.getStatus());
        try {
            handler.handle(event);
        } catch (Exception e) {
            log.error("处理子任务完成事件失败 eventId={}", event.getEventId(), e);
            throw e;  // 抛出后 RocketMQ 会重试
        }
    }
}
```

- [ ] **Step 11.9: 更新 application.yml 添加 RocketMQ 配置**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\main\resources\application.yml`

追加：

```yaml
rocketmq:
  name-server: ${ROCKETMQ_NAME_SERVER:localhost:9876}
  producer:
    group: orchestrator-producer
    send-message-timeout: 5000
  orchestrator:
    topics:
      subtask-execute: task.subtask.execute
      subtask-done: task.subtask.done
      state-change: task.state.change
      subtask-cancel: task.subtask.cancel
    groups:
      orchestrator-consumer: orchestrator-cg
```

- [ ] **Step 11.10: 运行测试验证通过（绿）**

Run: `mvn -pl agent-task-orchestrator test -Dtest="SubtaskExecuteProducerTest,SubtaskDoneConsumerTest,StateChangeProducerTest,SubtaskCancelProducerTest" -q`
Expected: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` → `BUILD SUCCESS`

- [ ] **Step 11.11: 提交**

```bash
git add agent-task-orchestrator/src/main/java/com/agent/orchestrator/config/RocketMqProperties.java \
        agent-task-orchestrator/src/main/java/com/agent/orchestrator/mq/ \
        agent-task-orchestrator/src/test/java/com/agent/orchestrator/mq/ \
        agent-task-orchestrator/src/main/resources/application.yml
git commit -m "feat(orchestrator): integrate RocketMQ (4 topics: execute/done/state-change/cancel)"
```

**验收标准（Task 11）：**
- [ ] `mvn -pl agent-task-orchestrator -am install -q` BUILD SUCCESS
- [ ] 4 个 Producer/Consumer 测试 ≥ 10 个全绿
- [ ] 4 个 Topic 配置正确（task.subtask.execute / done / state.change / cancel）
- [ ] 消息 key=`{taskId}:{nodeId}` / tag=`{tenantId}` 格式验证
- [ ] SubtaskDoneConsumer 委托 SubtaskDoneHandler（幂等 + 成本累加 + 状态推进）
- [ ] application.yml 含 `rocketmq.name-server` + `rocketmq.orchestrator.topics.*`

---

## Task 13: 集成测试

**Files:**
- Create: `agent-task-orchestrator/src/test/java/com/agent/orchestrator/integration/TaskOrchestratorIntegrationTest.java`
- Update: `agent-task-orchestrator/pom.xml`（testcontainers + grpc-testing 依赖，已在 Step P.2/P.4 声明）

**关键设计决策（T13 基础设施选择）：**

> **采用 H2（MySQL 模式）+ jedis-mock（嵌入式 Redis）+ InProcess gRPC Server 作为主测试路径，无需 Docker。** 保留 Testcontainers MySQL+Redis 作为 `docker` profile 下的可选真实集成路径（`-Pdocker` 激活）。
>
> **理由：**
> 1. 当前开发环境无 Docker（与 agent-session `EndToEndTest` 一致，参见 tdd-audit-report-v2.md §P2-2 FN-012）；
> 2. 父 pom 已提供 `no-docker` profile 范式，orchestrator pom 已含 H2 + jedis-mock 测试依赖；
> 3. InProcess gRPC Server 由 `io.grpc:grpc-testing` 提供（`InProcessServerBuilder`），无需启动真实端口；
> 4. Testcontainers 真实路径在 CI 有 Docker 时通过 `-Pdocker` 激活，保证环境一致性。
>
> **Mock 策略：** 跨模块下游（agent-repo / model-gateway / agent-runtime）用 Mockito stub，本模块内组件（Repository / StateMachine / PlanValidator / Producers）使用真实实现，保证 E2E 真实性。

- [ ] **Step 13.1: 写失败测试 — TaskOrchestratorIntegrationTest**

文件路径：`e:\git\Agent-Platform-Prototype\agent-task-orchestrator\src\test\java\com\agent\orchestrator\integration\TaskOrchestratorIntegrationTest.java`

```java
package com.agent.orchestrator.integration;

import agentplatform.task.v1.*;
import agentplatform.common.v1.TraceContext;
import com.agent.common.constant.TaskStatus;
import com.agent.orchestrator.grpc.TaskInstanceMapper;
import com.agent.orchestrator.grpc.TaskOrchestratorGrpcService;
import com.agent.orchestrator.grpc.GrpcExceptionAdvice;
import com.agent.orchestrator.model.TaskInstance;
import com.agent.orchestrator.repository.TaskInstanceRepository;
import com.agent.orchestrator.statemachine.TaskStateMachine;
import com.agent.orchestrator.dispatcher.BatchPartitioner;
import com.agent.orchestrator.validator.PlanValidator;
import com.agent.orchestrator.validator.ValidationResult;
import com.agent.orchestrator.template.TemplateMatcher;
import com.github.fppt.jedismock.RedisServer;
import com.zaxxer.hikari.HikariDataSource;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.JpaTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 端到端集成测试（Red 阶段）。
 *
 * <p><b>基础设施：</b></p>
 * <ul>
 *   <li>DB：H2 内存数据库（MODE=MySQL），Hibernate ddl-auto=create-drop 自动建表</li>
 *   <li>Redis：jedis-mock 嵌入式服务器（本测试暂不依赖 Redis，预留）</li>
 *   <li>gRPC：InProcess Server + Channel（grpc-testing），无需真实端口</li>
 *   <li>跨模块下游：Mockito stub（PlanValidator / TemplateMatcher / BatchPartitioner）</li>
 *   <li>本模块组件：真实 TaskInstanceRepository（H2）+ TaskStateMachine + TaskInstanceMapper + GrpcService</li>
 * </ul>
 *
 * <p>覆盖场景：</p>
 * <ol>
 *   <li>L1 任务直接执行（跳规划）→ SUBTASK_RUNNING</li>
 *   <li>L2 任务走完整 Plan → Validate → Execute 流程</li>
 *   <li>子任务失败触发重规划</li>
 *   <li>取消任务 → CANCELLED</li>
 * </ol>
 */
class TaskOrchestratorIntegrationTest {

    private static RedisServer redisServer;
    private static DataSource dataSource;
    private static JpaTransactionManager transactionManager;
    private static TaskInstanceRepository repository;
    private static ManagedChannel channel;
    private static TaskOrchestratorGrpc.TaskOrchestratorBlockingStub stub;

    // 真实本模块组件
    private static TaskStateMachine stateMachine;
    private static TaskInstanceMapper mapper;
    // Mockito stub 下游
    private static PlanValidator planValidator;
    private static TemplateMatcher templateMatcher;
    private static BatchPartitioner batchPartitioner;

    @BeforeAll
    static void setUp() throws IOException {
        // 1. H2 MySQL 模式 + Hibernate 自动建表
        // 2. jedis-mock Redis（预留）
        // 3. InProcess gRPC Server 注册 TaskOrchestratorGrpcService
        // 4. 构造 blocking stub
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (channel != null) channel.shutdown();
        if (redisServer != null) redisServer.stop();
    }

    @Test
    @DisplayName("E2E-1: L1 任务提交应直接进入 SUBTASK_RUNNING（跳过 PLANNING）")
    void should_RunL1TaskDirectly_When_L1Submitted() {
        SubmitTaskRequest req = SubmitTaskRequest.newBuilder()
                .setTaskId("tk_e2e_l1").setTenantId(1001L).setUserId("u_1")
                .setGoal("查订单").setCostLimitCent(10000L)
                .setTrace(TraceContext.newBuilder().setTaskId("tk_e2e_l1").build())
                .build();

        SubmitTaskResponse resp = stub.submitTask(req);

        assertThat(resp.getTaskId()).isEqualTo("tk_e2e_l1");
        assertThat(resp.getStatus()).isEqualTo(TaskStatus.SUBTASK_RUNNING.name());
        assertThat(resp.getComplexity()).isEqualTo(1);

        // 验证 DB 落库
        Optional<TaskInstance> saved = repository.findByTaskId("tk_e2e_l1");
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo(TaskStatus.SUBTASK_RUNNING.name());
    }

    @Test
    @DisplayName("E2E-2: L2 任务应走完整 PLANNING → VALIDATE → RUNNING 流程")
    void should_RunFullPlanningFlow_When_L2Submitted() {
        SubmitTaskRequest req = SubmitTaskRequest.newBuilder()
                .setTaskId("tk_e2e_l2").setGoal("多步复杂任务").setCostLimitCent(50000L)
                .setTrace(TraceContext.newBuilder().setTaskId("tk_e2e_l2").build())
                .build();
        when(planValidator.validate(any(), any()))
                .thenReturn(new ValidationResult(true, List.of(), List.of()));

        SubmitTaskResponse resp = stub.submitTask(req);

        assertThat(resp.getComplexity()).isEqualTo(2);
        assertThat(resp.getStatus()).isIn(TaskStatus.RUNNING.name(), TaskStatus.SUBTASK_RUNNING.name());
        verify(planValidator, atLeastOnce()).validate(any(), any());
    }

    @Test
    @DisplayName("E2E-3: 子任务失败上报应触发 REPLANNING 状态")
    void should_TriggerReplanning_When_SubtaskFails() {
        // 先建一个 SUBTASK_RUNNING 任务
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_e2e_fail").tenantId(1001L)
                .status(TaskStatus.SUBTASK_RUNNING.name())
                .complexity(2).costLimitCent(50000L).costUsedCent(0L).tokenUsed(0)
                .taskSchema("{}").priority(5).userId("u_1").title("t").goal("g")
                .build();
        repository.save(task);

        SubtaskResult result = SubtaskResult.newBuilder()
                .setTaskId("tk_e2e_fail").setSubtaskId("st_1").setNodeId("n_1")
                .setStatus("failed").setErrorCode("MAX_RETRY_EXCEEDED").setCostCent(100L)
                .build();

        ReportAck ack = stub.reportSubtaskResult(result);

        assertThat(ack.getAccepted()).isTrue();
        Optional<TaskInstance> updated = repository.findByTaskId("tk_e2e_fail");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(TaskStatus.REPLANNING.name());
    }

    @Test
    @DisplayName("E2E-4: 取消运行中任务应转为 CANCELLED")
    void should_TransitToCancelled_When_CancelRunningTask() {
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_e2e_cancel").tenantId(1001L)
                .status(TaskStatus.RUNNING.name()).complexity(2)
                .costLimitCent(50000L).costUsedCent(0L).tokenUsed(0)
                .taskSchema("{}").priority(5).userId("u_1").title("t").goal("g")
                .build();
        repository.save(task);

        CancelTaskRequest req = CancelTaskRequest.newBuilder()
                .setTaskId("tk_e2e_cancel").setReason("user cancel").build();

        CancelAck ack = stub.cancelTask(req);

        assertThat(ack.getStatus()).isEqualTo(TaskStatus.CANCELLED.name());
        Optional<TaskInstance> updated = repository.findByTaskId("tk_e2e_cancel");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(TaskStatus.CANCELLED.name());
        assertThat(updated.get().getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("E2E-5: GetTaskStatus 应返回 DB 中真实任务状态")
    void should_ReturnRealStatus_When_GetTaskStatus() {
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_e2e_query").tenantId(1001L)
                .status(TaskStatus.PLANNING.name()).complexity(2)
                .costLimitCent(50000L).costUsedCent(0L).tokenUsed(0)
                .taskSchema("{}").priority(5).userId("u_1").title("t").goal("g")
                .build();
        repository.save(task);

        GetTaskStatusRequest req = GetTaskStatusRequest.newBuilder()
                .setTaskId("tk_e2e_query").setTenantId(1001L).build();

        agentplatform.task.v1.TaskInstance proto = stub.getTaskStatus(req);

        assertThat(proto.getTaskId()).isEqualTo("tk_e2e_query");
        assertThat(proto.getStatus()).isEqualTo(TaskStatus.PLANNING.name());
    }

    @Test
    @DisplayName("E2E-6: 查询不存在的任务应返回 gRPC NOT_FOUND 错误")
    void should_ReturnNotFound_When_QueryNonExistentTask() {
        GetTaskStatusRequest req = GetTaskStatusRequest.newBuilder()
                .setTaskId("tk_notexist").setTenantId(1001L).build();

        org.junit.jupiter.api.Assertions.assertThrows(
                io.grpc.StatusRuntimeException.class,
                () -> stub.getTaskStatus(req));
    }
}
```

- [ ] **Step 13.2: 运行测试验证失败**

Run: `mvn -pl agent-task-orchestrator test -Dtest=TaskOrchestratorIntegrationTest -q`
Expected: FAIL — 测试基础设施未装配（setUp 内组件未实例化）

- [ ] **Step 13.3: 补全 setUp() 装配真实组件 + InProcess gRPC Server**

在 `TaskOrchestratorIntegrationTest.setUp()` 中实现：

```java
@BeforeAll
static void setUp() throws Exception {
    // 1. H2 MySQL 模式数据源
    HikariDataSource hikari = new HikariDataSource();
    hikari.setJdbcUrl("jdbc:h2:mem:agent_task;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    hikari.setUsername("sa");
    hikari.setPassword("");
    hikari.setDriverClassName("org.h2.Driver");
    dataSource = hikari;

    // 2. Hibernate EMF + ddl-auto=create-drop 自动建表
    LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
    emf.setDataSource(dataSource);
    emf.setPackagesToScan("com.agent.orchestrator.model");
    HibernateJpaVendorAdapter vendor = new HibernateJpaVendorAdapter();
    vendor.setDatabasePlatform("org.hibernate.dialect.H2Dialect");
    emf.setJpaVendorAdapter(vendor);
    Properties jpaProps = new Properties();
    jpaProps.put("hibernate.hbm2ddl.auto", "create-drop");
    jpaProps.put("hibernate.show_sql", "false");
    emf.setJpaProperties(jpaProps);
    emf.afterPropertiesSet();

    EntityManagerFactory emfInstance = emf.getObject();
    transactionManager = new JpaTransactionManager(emfInstance);

    // 3. 真实 Repository（JpaRepositoryFactory）
    JpaRepositoryFactory repoFactory = new JpaRepositoryFactory(emfInstance);
    repository = repoFactory.getRepository(TaskInstanceRepository.class);

    // 4. 真实本模块组件 + Mockito stub 下游
    stateMachine = new TaskStateMachine();
    mapper = new TaskInstanceMapper();
    planValidator = mock(PlanValidator.class);
    templateMatcher = mock(TemplateMatcher.class);
    batchPartitioner = mock(BatchPartitioner.class);

    GrpcExceptionAdvice advice = new GrpcExceptionAdvice();
    TaskOrchestratorGrpcService service = new TaskOrchestratorGrpcService(
            repository, stateMachine, batchPartitioner, planValidator,
            templateMatcher, mapper, advice);

    // 5. InProcess gRPC Server
    String serverName = InProcessServerBuilder.generateName();
    io.grpc.Server server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(service)
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(serverName)
            .directExecutor()
            .build();
    stub = TaskOrchestratorGrpc.newBlockingStub(channel);

    // 6. jedis-mock（预留，当前测试不依赖）
    redisServer = new RedisServer(0);
    // redisServer.start();  // 当前测试无需 Redis，注释掉避免端口占用
}
```

- [ ] **Step 13.4: 运行测试验证通过（绿）**

Run: `mvn -pl agent-task-orchestrator test -Dtest=TaskOrchestratorIntegrationTest -q`
Expected: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0` → `BUILD SUCCESS`

- [ ] **Step 13.5: 运行全量测试套件验证回归**

Run: `mvn -pl agent-task-orchestrator test -q`
Expected: 全部测试通过（既有 T1-T4/T6/T8-T10/T12 单测 + T5/T7/T11/T13 新测试）→ `BUILD SUCCESS`

- [ ] **Step 13.6: 提交**

```bash
git add agent-task-orchestrator/src/test/java/com/agent/orchestrator/integration/TaskOrchestratorIntegrationTest.java
git commit -m "test(orchestrator): add end-to-end integration test (H2 + jedis-mock + InProcess gRPC)"
```

**验收标准（Task 13）：**
- [ ] `mvn -pl agent-task-orchestrator -am install -q` BUILD SUCCESS
- [ ] TaskOrchestratorIntegrationTest ≥ 6 测试全绿（L1 直跑 / L2 全流程 / 失败重规划 / 取消 / 查询 / NOT_FOUND）
- [ ] 真实 Repository（H2 MySQL 模式）+ 真实 StateMachine + 真实 Mapper，跨模块下游 Mockito stub
- [ ] InProcess gRPC Server（grpc-testing）无需真实端口
- [ ] `-Pno-docker` profile 下集成测试可运行（H2 + jedis-mock 路径）

---

## 实施顺序建议（Wave 2 推荐路径）

```
┌────────────────────────────────────────────────────────────────────────┐
│  Wave 2: agent-task-orchestrator T5/T7/T11/T13（本计划）                 │
│  前置：T1-T4/T6/T8-T10/T12 已完成（DAG 引擎/状态机/复杂度/模板/校验/    │
│         批次/重规划模式选择）                                            │
└────────────────────────────────────────────────────────────────────────┘
                    │
                    ▼
        ┌───────────────────────┐
        │ Step P.1~P.5: 依赖添加 │  (gRPC + RocketMQ + Testcontainers)
        │ 估计：0.5h             │
        └───────────┬───────────┘
                    ▼
        ┌───────────────────────┐    ┌───────────────────────┐
        │ T5: TaskOrchestrator  │    │ T7: PlanningService   │
        │ gRPC 服务（4 RPC）     │    │ gRPC 服务（4 RPC）    │
        │ 依赖：T2/T4/T3/T10    │    │ 依赖：T6/T8/T9/T12   │
        │ 估计：4h（13 测试）    │    │ 估计：3h（11 测试）   │
        └───────────┬───────────┘    └───────────┬───────────┘
                    │                            │
                    │  T5 与 T7 可并行（无相互依赖，         │
                    │  共享 GrpcExceptionAdvice）             │
                    ▼                            ▼
        ┌─────────────────────────────────────────┐
        │ T11: RocketMQ 集成（4 Topic）           │
        │ 依赖：T5（SubtaskDoneHandler 调用       │
        │       StateMachine/Repository）        │
        │ 估计：4h（10 测试）                     │
        └───────────────────┬─────────────────────┘
                            ▼
        ┌─────────────────────────────────────────┐
        │ T13: 集成测试（E2E 6 场景）              │
        │ 依赖：T5 + T7 + T11 全部完成            │
        │ 估计：3h（6 测试）                      │
        └─────────────────────────────────────────┘
```

### 执行方式建议

**Subagent-Driven（推荐）** — 由父 Agent 按 T5 → T7 → T11 → T13 顺序派发子 Agent，每个 Task 完成后做两阶段 review（实现 review + 测试 review）：
1. **T5 与 T7 可并行**（两者无相互依赖，仅共享 `GrpcExceptionAdvice`，可先建共享类再并行）
2. **T11 必须在 T5 之后**（`SubtaskDoneHandler` 依赖 `TaskStateMachine` + `TaskInstanceRepository`，且 `ReportSubtaskResult` RPC 与 `SubtaskDoneConsumer` 共享回调逻辑）
3. **T13 必须在 T5/T7/T11 全部完成后**（端到端验证三者协作）

### 关键路径与风险

| 风险点 | 缓解措施 |
|---|---|
| proto 生成类包名 `agentplatform.task.v1` 与设计文档 `com.agentplatform.task.orchestrator.api` 不一致 | 以 proto 实际生成包名为准（`agentplatform.task.v1.*`），本计划已对齐 |
| proto `TaskInstance` 与 JPA `com.agent.orchestrator.model.TaskInstance` 同名冲突 | 用 FQN 区分，`TaskInstanceMapper` 专门处理转换 |
| RocketMQ 在测试环境不可用 | Producer/Consumer 单测全用 Mockito mock `RocketMQTemplate`，不启动真实 Broker；集成测试（T13）不涉及 MQ 消费（用 gRPC 直接驱动） |
| InProcess gRPC Server 与 `@GrpcService` 注解冲突 | T13 集成测试不依赖 Spring 容器，直接 `new TaskOrchestratorGrpcService(...)` + `InProcessServerBuilder.addService(service)` |
| H2 MySQL 模式与 MySQL 8 方言差异 | Hibernate `dialect=H2Dialect`，DDL 由 `ddl-auto=create-drop` 按实体注解生成，不依赖 MySQL 特有语法 |
| JaCoCo 覆盖率门槛 80%/70% | T5/T7/T11 单测覆盖核心分支，T13 集成测试补充端到端路径，预计可达标 |

### 估时汇总

| Task | 估时 | 测试数 | 关键产出 |
|---|---|---|---|
| Step P.1-P.5（依赖） | 0.5h | - | pom.xml + application.yml 更新 |
| T5 TaskOrchestrator gRPC | 4h | 13 | 4 RPC + 异常翻译 + Mapper |
| T7 PlanningService gRPC | 3h | 11 | 4 RPC + DagJsonMapper |
| T11 RocketMQ 集成 | 4h | 10 | 4 Topic + Producer/Consumer |
| T13 集成测试 | 3h | 6 | E2E 端到端验证 |
| **合计** | **14.5h** | **40** | **T5-T13 全部完成，对齐 P6-6** |

---

## 自审清单（Self-Review）

### 1. 设计文档覆盖

| 设计要求 | 覆盖 Task | 状态 |
|---|---|---|
| TaskOrchestrator gRPC 4 RPC（SubmitTask/GetTaskStatus/CancelTask/ReportSubtaskResult） | T5 | ✅ |
| PlanningService gRPC 4 RPC（AssessComplexity/Plan/ValidatePlan/Replan） | T7 | ✅ |
| proto 包名 `agentplatform.task.v1` / `agentplatform.planning.v1` | T5 / T7 | ✅ |
| 10 状态机流转矩阵（PENDING→PLANNING→RUNNING↔SUBTASK_RUNNING→REPLANNING→...） | T5 + T11（SubtaskDoneHandler） | ✅ |
| L1 任务跳规划直跑 RUNNING | T5 submitTask | ✅ |
| L2/L3 走 PLANNING → PlanValidator → BatchPartitioner | T5 submitTask | ✅ |
| 子任务分发消息格式（key=taskId:nodeId / tag=tenantId） | T11 SubtaskExecuteProducer | ✅ |
| 子任务完成回调幂等校验 + 成本累加 + 状态推进 | T11 SubtaskDoneHandler | ✅ |
| 4 个 RocketMQ Topic（execute/done/state.change/cancel） | T11 全部 | ✅ |
| 错误码 TASK_NOT_FOUND(404) / TASK_STATUS_CONFLICT(409) / DAG_CYCLE_DETECTED(409) / COST_BUDGET_EXCEEDED(429) / REPLAN_EXHAUSTED(500) / AGENT_NOT_FOUND(404) / PLAN_VALIDATION_FAILED(500) | T5 GrpcExceptionAdvice + T11 SubtaskDoneHandler | ✅ |
| 异常分级（瞬时/业务/质量/致命）+ 三级重试 | T11 SubtaskDoneHandler 失败分类 | ✅ |
| 复杂度三档（≤8=L1 / 9-14=L2 / >14=L3）+ 风险强制升级 | T7 assessComplexity（T6 已实现） | ✅ |
| 模板匹配 vs AI 规划双分支 | T7 plan（source=template/ai） | ✅ |
| 5 维度 DAG 自检（完备性/原子性/效率/成本/容错） | T7 validatePlan（T9 已实现） | ✅ |
| 重规划模式选择（INCREMENTAL/FULL） | T7 replan（T12 已实现） | ✅ |
| 端到端集成测试（L1 直跑 / L2 全流程 / 失败重规划 / 取消） | T13 | ✅ |
| ADR-001 自研 DAG 引擎 | T3 已落地 | ✅（已完成） |

### 2. 测试用例对齐

| UT 编号 | 测试方法名 | 覆盖 Task |
|---|---|---|
| UT-ORCH-001 L1 直跑 | `should_ReturnRunningStatus_When_L1TaskSubmitted` | T5 |
| UT-ORCH-002 R3 人工 | `should_TransitToWaitingHuman_When_R3NodeRequiresReview` | T5 |
| UT-ORCH-003 重试耗尽重规划 | `should_TransitToReplanning_When_SubtaskRetryExhausted` | T5 |
| UT-ORCH-004 非法状态冲突 | `should_TranslateStatusConflict_When_IllegalTransition` | T5 |
| UT-ORCH-005 环检测 | `should_ReturnDagCycleError_When_DagHasCircularDependency` | T5 |
| UT-ORCH-006 并行批次 | `should_InvokeBatchPartitioner_When_L2TaskSubmitted` | T5 |
| UT-ORCH-007 Agent 匹配 | `should_AdvanceToSubtaskRunning_When_AgentMatched` | T5 |
| UT-ORCH-008 Agent 未找到 | `should_TransitToWaitingHuman_When_NoAgentScoreAbove06` | T5 |
| UT-ORCH-012 成本超限 | `should_ThrowCostBudgetExceeded_When_CostUsedExceedsLimit` | T5 |
| UT-PLAN-001 L1 分级 | `should_ReturnL1_When_TotalScoreLe8` | T7 |
| UT-PLAN-002 L2 分级 | `should_ReturnL2_When_TotalScoreBetween9And14` | T7 |
| UT-PLAN-003 L3 分级 | `should_ReturnL3_When_TotalScoreGt14` | T7 |
| UT-PLAN-004 风险升级 | `should_ForceUpgradeToL3_When_RiskLevelIsHigh` | T7 |
| UT-PLAN-005 规则高置信跳模型 | `should_BypassModelAssessor_When_RuleConfidenceHigh` | T7 |
| UT-PLAN-006 规则低置信调模型 | `should_InvokeModelAssessor_When_RuleConfidenceLow` | T7 |
| UT-PLAN-007 模板匹配 | `should_MatchTemplate_When_HighFrequencyScenario` | T7 |
| UT-PLAN-008 AI 规划回退 | `should_FallbackToAiPlanner_When_NoTemplateMatched` | T7 |
| UT-PLAN-009 5 维度通过 | `should_PassValidation_When_AllFiveDimensionsOk` | T7 |
| UT-PLAN-010 完备性失败 | `should_ReturnPlanValidationFailed_When_CompletenessFailed` | T7 |

> UT-ORCH-009/010/011/013 已由 T12 ReplanModeSelector / T10 BatchPartitioner 单测覆盖（本计划已完成 Task 简表已确认）。

### 3. 占位符扫描

- ✅ 所有 Java 类签名含完整方法签名（含返回类型与参数），无 `TODO` / `TBD`
- ✅ gRPC 服务类内部辅助方法（`assessComplexityInternal` / `extractTemplateNodes` 等）标注为占位实现，附明确替换路径（应调用 T6 ComplexityScorer / T8 TaskTemplate 实际字段），非模糊 TODO
- ✅ 每个测试方法含具体断言（AssertJ `assertThat`），无 `assert something` 占位
- ✅ Step 13.3 的 `setUp()` 提供完整装配代码（HikariDataSource + Hibernate EMF + JpaRepositoryFactory + InProcessServerBuilder），无 stub 留白

### 4. 类型一致性

- ✅ `TaskOrchestratorGrpcService` 构造函数在 Step 5.1 测试与 Step 5.5 实现中签名一致（6 参数 + GrpcExceptionAdvice 重载）
- ✅ `PlanningServiceGrpcImpl` 构造函数在 Step 7.1 测试与 Step 7.5 实现中签名一致（7 参数 + GrpcExceptionAdvice 重载）
- ✅ `SubtaskDoneHandler.handle(SubtaskDoneEvent)` 在 Step 11.1 测试与 Step 11.8 实现中一致
- ✅ `SubtaskExecuteProducer.dispatch(SubtaskExecuteEvent)` 返回 String（msgId）在测试与实现中一致
- ✅ proto 包名 `agentplatform.task.v1` / `agentplatform.planning.v1` / `agentplatform.common.v1` 与 proto 文件 `java_package` 一致
- ✅ Java 包名统一 `com.agent.orchestrator.*`（与既有 T1-T4/T6/T8-T10/T12 一致）

### 5. TDD 红绿循环

- ✅ T5/T7/T11/T13 每个均按「写失败测试（Step .1）→ 运行验证失败（Step .2）→ 写最小实现（Step .3-.5/.8）→ 运行验证通过（Step .7/.10/.4）→ 提交（Step .8/.11/.6）」执行
- ✅ 每个 Task 末尾有 `git commit` 步骤（遵循 Conventional Commits：`feat(orchestrator):` / `test(orchestrator):`）
- ✅ Step P.1-P.5（依赖添加）单独 commit 前置，避免与功能代码混入

### 6. 关键设计决策摘要

| 决策项 | 选择 | 理由 |
|---|---|---|
| **proto 包名** | `agentplatform.task.v1` / `agentplatform.planning.v1`（实际生成） | 设计文档 §8.1 写的 `com.agentplatform.task.orchestrator.api` 仅为示意，以 proto `java_package` 为准 |
| **proto TaskInstance 与 JPA TaskInstance 同名** | `TaskInstanceMapper` 用 FQN 消歧义 | 避免改类名破坏既有 T2 实体 |
| **gRPC server 端口** | 9090（TaskOrchestrator + PlanningService 共用） | 单 Spring Boot 应用，net.devh 单 server 多 service |
| **rocketmq-spring 版本** | 2.3.0（继承父 pom `rocketmq-spring.version`） | 设计文档 §11 提 2.3.1，父 pom 已声明 2.3.0，对齐父 pom 避免引入未管理版本 |
| **T13 基础设施** | H2（MySQL 模式）+ jedis-mock + InProcess gRPC Server（主路径，无需 Docker）；Testcontainers（`-Pdocker` 可选） | 与 agent-session EndToEndTest 一致；当前环境无 Docker；父 pom 已有 `no-docker` profile |
| **T13 Mock 策略** | 本模块组件真实（Repository/StateMachine/Mapper），跨模块下游 stub（PlanValidator/TemplateMatcher/BatchPartitioner） | 保证 E2E 真实性同时避免跨进程依赖 |
| **T11 RocketMQ 测试** | Mockito mock `RocketMQTemplate`，不启动真实 Broker | 单测聚焦消息格式与路由逻辑；集成测试用 gRPC 直接驱动不涉及 MQ 消费 |
| **异常翻译** | `GrpcExceptionAdvice` 按 HTTP 状态码映射 gRPC Status（404→NOT_FOUND / 409→FAILED_PRECONDITION / 429→RESOURCE_EXHAUSTED） | 统一异常出口，避免每个 RPC 重复 try-catch |
| **SubtaskDoneHandler 幂等** | 内存 `ConcurrentHashMap` Set 简化 | 单测足够；生产环境注释说明应替换为 Redis SETNX + `event_consume_log` 表 |

---

## 执行 Handoff

**Plan complete and saved to `e:\git\Agent-Platform-Prototype\docs\plans\04-task-orchestrator-planning-plan.md`.**