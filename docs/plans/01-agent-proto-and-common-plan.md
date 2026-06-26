# agent-proto + agent-common Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建 Agent 平台的 Protobuf 契约层与公共工具模块，为所有 11 个微服务提供 gRPC 接口定义、DTO、异常、工具类基础

**Architecture:** Monorepo 结构，agent-proto 模块只包含 .proto 文件与生成的 Java stub，agent-common 模块包含 DTO、异常体系、工具类、Spring 通用配置。两者均为 jar 包被其他服务依赖。

**Tech Stack:** Java 17 / Spring Boot 3.2 / protobuf-maven-plugin 0.6.1 / grpc-java 1.62.2 / Lombok / Jackson / JUnit 5 / Mockito

---

## 文件结构总览

### 模块 A：agent-proto（Protobuf 契约层）

| 文件 | 职责 |
|---|---|
| `agent-proto/pom.xml` | Maven 配置 + protobuf-maven-plugin 0.6.1 + grpc 1.62.2 |
| `agent-proto/src/main/proto/common.proto` | TraceContext / Error / Pagination 公共消息 |
| `agent-proto/src/main/proto/task.proto` | TaskOrchestrator 服务 + TaskInstance 等消息 |
| `agent-proto/src/main/proto/planning.proto` | PlanningService 服务 + DagNode / DagEdge |
| `agent-proto/src/main/proto/memory.proto` | MemoryService 服务 + MemoryRecord / RecalledMemory |
| `agent-proto/src/main/proto/model.proto` | ModelGateway 服务（含 StreamChat server streaming） |
| `agent-proto/src/main/proto/tool.proto` | ToolGateway 服务 + ToolInvokeRequest/Response / ToolRegistry |
| `agent-proto/src/main/proto/knowledge.proto` | KnowledgeService 服务（Ingest/Retrieve/VersionManage） |
| `agent-proto/src/main/proto/agent_runtime.proto` | AgentRuntime 服务（StartAgent/Step/GetState/Pause/Resume） |
| `agent-proto/src/test/java/com/agent/proto/ProtoCompileTest.java` | 验证生成的 stub 可序列化 |

### 模块 B：agent-common（公共工具层）

| 文件 | 职责 |
|---|---|
| `agent-common/pom.xml` | 依赖 agent-proto + Lombok + Jackson + spring-boot-starter |
| `agent-common/src/main/java/com/agent/common/dto/PageRequest.java` | 分页请求 DTO |
| `agent-common/src/main/java/com/agent/common/dto/PageResponse.java` | 分页响应 DTO |
| `agent-common/src/main/java/com/agent/common/dto/ApiResponse.java` | 统一响应封装 |
| `agent-common/src/main/java/com/agent/common/exception/ErrorCode.java` | 错误码枚举 |
| `agent-common/src/main/java/com/agent/common/exception/BusinessException.java` | 业务异常 |
| `agent-common/src/main/java/com/agent/common/exception/GlobalExceptionHandler.java` | 全局异常处理器 |
| `agent-common/src/main/java/com/agent/common/constant/TaskStatus.java` | 任务状态枚举（10 状态） |
| `agent-common/src/main/java/com/agent/common/constant/ComplexityLevel.java` | 复杂度等级 L1/L2/L3 |
| `agent-common/src/main/java/com/agent/common/constant/AgentStatus.java` | Agent 状态枚举 |
| `agent-common/src/main/java/com/agent/common/constant/RiskLevel.java` | 风险等级 R1/R2/R3 |
| `agent-common/src/main/java/com/agent/common/context/TraceContext.java` | 链路上下文 |
| `agent-common/src/main/java/com/agent/common/utils/JsonUtils.java` | JSON 序列化工具 |
| `agent-common/src/main/java/com/agent/common/utils/TraceUtils.java` | Trace ID 工具（ThreadLocal） |
| `agent-common/src/main/java/com/agent/common/utils/TokenEstimator.java` | Token 估算器（中文 1.7 倍） |

---

## Task 1: 创建 agent-proto Maven 项目骨架

**Files:**
- Create: `agent-proto/pom.xml`

- [ ] **Step 1.1: 创建 agent-proto/pom.xml 文件**

文件路径：`e:\git\Agent-Platform-Prototype\agent-proto\pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.agentplatform</groupId>
    <artifactId>agent-proto</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    <name>agent-proto</name>
    <description>Agent Platform Protobuf Contracts &amp; gRPC stubs</description>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <protobuf.version>3.25.1</protobuf.version>
        <grpc.version>1.62.2</grpc.version>
        <protobuf.maven.plugin.version>0.6.1</protobuf.maven.plugin.version>
        <os.maven.plugin.version>1.7.1</os.maven.plugin.version>
        <junit.version>5.10.2</junit.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-netty-shaded</artifactId>
            <version>${grpc.version}</version>
        </dependency>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-protobuf</artifactId>
            <version>${grpc.version}</version>
        </dependency>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-stub</artifactId>
            <version>${grpc.version}</version>
        </dependency>
        <dependency>
            <groupId>com.google.protobuf</groupId>
            <artifactId>protobuf-java</artifactId>
            <version>${protobuf.version}</version>
        </dependency>
        <dependency>
            <groupId>javax.annotation</groupId>
            <artifactId>javax.annotation-api</artifactId>
            <version>1.3.2</version>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <extensions>
            <extension>
                <groupId>kr.motd.maven</groupId>
                <artifactId>os-maven-plugin</artifactId>
                <version>${os.maven.plugin.version}</version>
            </extension>
        </extensions>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.12.1</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.xolstice.maven.plugins</groupId>
                <artifactId>protobuf-maven-plugin</artifactId>
                <version>${protobuf.maven.plugin.version}</version>
                <configuration>
                    <protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>
                    <pluginId>grpc-java</pluginId>
                    <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>compile</goal>
                            <goal>compile-custom</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 1.2: 运行 `mvn validate` 验证 pom 配置正确**

Run（在 `e:\git\Agent-Platform-Prototype` 目录下执行）：

```bash
cd agent-proto && mvn validate
```

Expected: `BUILD SUCCESS`，输出包含 `agent-proto ... (from ...agent-proto/pom.xml)`

- [ ] **Step 1.3: 提交骨架**

```bash
git add agent-proto/pom.xml
git commit -m "feat(proto): add agent-proto maven skeleton with protobuf-maven-plugin 0.6.1 and grpc 1.62.2"
```

---

## Task 2: common.proto 公共消息定义

**Files:**
- Create: `agent-proto/src/main/proto/common.proto`
- Create: `agent-proto/src/test/java/com/agent/proto/CommonProtoTest.java`

- [ ] **Step 2.1: 编写失败测试 common.proto 字段可读写**

文件路径：`agent-proto/src/test/java/com/agent/proto/CommonProtoTest.java`

```java
package com.agent.proto;

import agentplatform.common.v1.TraceContext;
import agentplatform.common.v1.Error;
import agentplatform.common.v1.Pagination;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommonProtoTest {

    @Test
    void traceContext_canRoundTripAllFields() {
        TraceContext ctx = TraceContext.newBuilder()
                .setTenantId(1001L)
                .setUserId("u_123")
                .setSessionId("ss_a1b2c3d4")
                .setTaskId("tk_yyy")
                .setSubtaskId("st_001")
                .setTraceId("trace-abc")
                .setSpanId("span-def")
                .build();

        TraceContext parsed = TraceContext.parseFrom(ctx.toByteArray());
        assertEquals(1001L, parsed.getTenantId());
        assertEquals("u_123", parsed.getUserId());
        assertEquals("ss_a1b2c3d4", parsed.getSessionId());
        assertEquals("tk_yyy", parsed.getTaskId());
        assertEquals("st_001", parsed.getSubtaskId());
        assertEquals("trace-abc", parsed.getTraceId());
        assertEquals("span-def", parsed.getSpanId());
    }

    @Test
    void error_carriesCodeAndMessageAndDetails() {
        Error err = Error.newBuilder()
                .setCode("TASK_NOT_FOUND")
                .setMessage("任务不存在")
                .setDetails("{\"taskId\":\"tk_xxx\"}")
                .build();
        Error parsed = Error.parseFrom(err.toByteArray());
        assertEquals("TASK_NOT_FOUND", parsed.getCode());
        assertEquals("任务不存在", parsed.getMessage());
        assertEquals("{\"taskId\":\"tk_xxx\"}", parsed.getDetails());
    }

    @Test
    void pagination_holdsPageAndSizeAndTotal() {
        Pagination p = Pagination.newBuilder()
                .setPage(1)
                .setSize(20)
                .setTotal(135L)
                .build();
        Pagination parsed = Pagination.parseFrom(p.toByteArray());
        assertEquals(1, parsed.getPage());
        assertEquals(20, parsed.getSize());
        assertEquals(135L, parsed.getTotal());
    }
}
```

- [ ] **Step 2.2: 运行测试验证编译失败（红）**

Run：

```bash
cd agent-proto && mvn test -Dtest=CommonProtoTest
```

Expected: FAIL，编译错误 `package agentplatform.common.v1 does not exist`（因为 proto 还没创建）

- [ ] **Step 2.3: 编写 common.proto 实现**

文件路径：`agent-proto/src/main/proto/common.proto`

```protobuf
syntax = "proto3";
package agentplatform.common.v1;

option java_multiple_files = true;
option java_package = "agentplatform.common.v1";

// 链路上下文，全链路透传（doc 02-api §0.3 + §3）
message TraceContext {
  int64 tenant_id = 1;
  string user_id = 2;
  string session_id = 3;
  string task_id = 4;
  string subtask_id = 5;
  string trace_id = 6;
  string span_id = 7;
}

// 统一错误响应（doc 02-api §0.4）
message Error {
  string code = 1;        // 错误码，如 TASK_NOT_FOUND
  string message = 2;     // 人类可读错误信息
  string details = 3;     // JSON 字符串，结构化详情
}

// 分页响应（doc 02-api §0.4）
message Pagination {
  int32 page = 1;
  int32 size = 2;
  int64 total = 3;
}
```

- [ ] **Step 2.4: 运行 protobuf:compile 生成 Java 类**

Run：

```bash
cd agent-proto && mvn protobuf:compile
```

Expected: `BUILD SUCCESS`，`target/generated-sources/protobuf/java/agentplatform/common/v1/` 下生成 `TraceContext.java`、`Error.java`、`Pagination.java`

- [ ] **Step 2.5: 运行测试验证通过（绿）**

Run：

```bash
cd agent-proto && mvn test -Dtest=CommonProtoTest
```

Expected: PASS，3 个测试全绿

- [ ] **Step 2.6: 提交**

```bash
git add agent-proto/src/main/proto/common.proto agent-proto/src/test/java/com/agent/proto/CommonProtoTest.java
git commit -m "feat(proto): add common.proto with TraceContext/Error/Pagination messages"
```

---

## Task 3: task.proto TaskOrchestrator 服务定义

**Files:**
- Create: `agent-proto/src/main/proto/task.proto`
- Create: `agent-proto/src/test/java/com/agent/proto/TaskProtoTest.java`

字段依据：`docs/01-database/database-schema-design.md` §2.1 task_instance 表

- [ ] **Step 3.1: 编写失败测试 TaskInstance 可序列化**

文件路径：`agent-proto/src/test/java/com/agent/proto/TaskProtoTest.java`

```java
package com.agent.proto;

import agentplatform.task.v1.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskProtoTest {

    @Test
    void taskInstance_roundTripAllDatabaseFields() {
        TaskInstance task = TaskInstance.newBuilder()
                .setTaskId("tk_yyy")
                .setTenantId(1001L)
                .setSessionId("ss_a1b2c3d4")
                .setUserId("u_123")
                .setTitle("生成周报并邮件发送")
                .setGoal("汇总本周销售数据生成周报")
                .setComplexity(3)
                .setStatus("PENDING")
                .setTaskSchema("{\"objective\":\"周报\"}")
                .setDagId(9001L)
                .setAgentId(1001L)
                .setPriority(5)
                .setParentTaskId("")
                .setReplanCount(0)
                .setCostLimitCent(5000L)
                .setCostUsedCent(0L)
                .setTokenUsed(0)
                .setStartedAt(0L)
                .setFinishedAt(0L)
                .setErrorCode("")
                .setErrorMsg("")
                .setResultSummary("")
                .setCreatedAt(1719405600000L)
                .setUpdatedAt(1719405600000L)
                .build();

        TaskInstance parsed = TaskInstance.parseFrom(task.toByteArray());
        assertEquals("tk_yyy", parsed.getTaskId());
        assertEquals(1001L, parsed.getTenantId());
        assertEquals("PENDING", parsed.getStatus());
        assertEquals(3, parsed.getComplexity());
        assertEquals(5000L, parsed.getCostLimitCent());
        assertEquals(1719405600000L, parsed.getCreatedAt());
    }

    @Test
    void submitTaskResponse_carriesTaskIdAndStatus() {
        SubmitTaskResponse resp = SubmitTaskResponse.newBuilder()
                .setTaskId("tk_yyy")
                .setStatus("PENDING")
                .setComplexity(0)
                .setSubmittedAt(1719405600000L)
                .build();
        SubmitTaskResponse parsed = SubmitTaskResponse.parseFrom(resp.toByteArray());
        assertEquals("tk_yyy", parsed.getTaskId());
        assertEquals("PENDING", parsed.getStatus());
    }

    @Test
    void subtaskResult_carriesAllRequiredFields() {
        SubtaskResult result = SubtaskResult.newBuilder()
                .setTaskId("tk_yyy")
                .setSubtaskId("st_001")
                .setNodeId("n1")
                .setStatus("success")
                .setOutputJson("{\"orders\":3}")
                .setTokenUsed(1520)
                .setCostCent(120L)
                .setDurationMs(2400)
                .build();
        SubtaskResult parsed = SubtaskResult.parseFrom(result.toByteArray());
        assertEquals("st_001", parsed.getSubtaskId());
        assertEquals("success", parsed.getStatus());
        assertEquals(1520, parsed.getTokenUsed());
    }
}
```

- [ ] **Step 3.2: 运行测试验证失败（红）**

Run：

```bash
cd agent-proto && mvn test -Dtest=TaskProtoTest
```

Expected: FAIL，编译错误 `package agentplatform.task.v1 does not exist`

- [ ] **Step 3.3: 编写 task.proto 实现**

文件路径：`agent-proto/src/main/proto/task.proto`

```protobuf
syntax = "proto3";
package agentplatform.task.v1;

import "common.proto";

option java_multiple_files = true;
option java_package = "agentplatform.task.v1";

// 任务编排服务（doc 02-api §6，端口 8084）
service TaskOrchestrator {
  // 提交任务（gateway 转发）
  rpc SubmitTask(SubmitTaskRequest) returns (SubmitTaskResponse);
  // 查询任务状态
  rpc GetTaskStatus(GetTaskStatusRequest) returns (TaskInstance);
  // 取消任务
  rpc CancelTask(CancelTaskRequest) returns (CancelAck);
  // 子任务完成回调（Agent Runtime 上报）
  rpc ReportSubtaskResult(SubtaskResult) returns (ReportAck);
}

// 提交任务请求（对应 doc 02-api §1.3 POST /api/v1/tasks）
message SubmitTaskRequest {
  string task_id = 1;
  int64 tenant_id = 2;
  string user_id = 3;
  string session_id = 4;
  string title = 5;
  string goal = 6;
  int32 priority = 7;
  int64 cost_limit_cent = 8;
  agentplatform.common.v1.TraceContext trace = 99;
}

message SubmitTaskResponse {
  string task_id = 1;
  string status = 2;
  int32 complexity = 3;        // 0=未评估 1=L1 2=L2 3=L3
  int64 submitted_at = 4;      // epoch millis
}

message GetTaskStatusRequest {
  string task_id = 1;
  int64 tenant_id = 2;
  agentplatform.common.v1.TraceContext trace = 99;
}

message CancelTaskRequest {
  string task_id = 1;
  int64 tenant_id = 2;
  string reason = 3;
  agentplatform.common.v1.TraceContext trace = 99;
}

message CancelAck {
  string task_id = 1;
  string status = 2;
  int64 cancelled_at = 3;
}

message ReportAck {
  bool accepted = 1;
  string message = 2;
}

// 子任务结果上报（对应 doc 02-api §7.2）
message SubtaskResult {
  string task_id = 1;
  string subtask_id = 2;
  string node_id = 3;
  string status = 4;            // success | failed | timeout
  string output_json = 5;
  int32 token_used = 6;
  int64 cost_cent = 7;
  int32 duration_ms = 8;
  string error_code = 9;
  string error_msg = 10;
  agentplatform.common.v1.TraceContext trace = 99;
}

// 任务实例（字段严格对应 doc 01-database §2.1 task_instance 表）
message TaskInstance {
  string task_id = 1;
  int64 tenant_id = 2;
  string session_id = 3;
  string user_id = 4;
  string title = 5;
  string goal = 6;
  int32 complexity = 7;        // 1=L1 2=L2 3=L3
  string status = 8;           // 任务状态机 10 状态之一
  string task_schema = 9;      // 标准 Task Schema JSON
  int64 dag_id = 10;
  int64 agent_id = 11;
  int32 priority = 12;
  string parent_task_id = 13;
  int32 replan_count = 14;
  int64 cost_limit_cent = 15;
  int64 cost_used_cent = 16;
  int32 token_used = 17;
  int64 started_at = 18;       // epoch millis
  int64 finished_at = 19;
  string error_code = 20;
  string error_msg = 21;
  string result_summary = 22;
  int64 created_at = 23;
  int64 updated_at = 24;
}

// 标准 Task Schema（doc 02-api §1.3 constraints）
message TaskSchema {
  string objective = 1;
  string deliverable = 2;
  string constraints_json = 3;
  string resources_json = 4;
}
```

- [ ] **Step 3.4: 运行 protobuf:compile + protobuf:compile-custom 生成 Java 类与 gRPC stub**

Run：

```bash
cd agent-proto && mvn protobuf:compile protobuf:compile-custom
```

Expected: `BUILD SUCCESS`，生成 `TaskInstance.java`、`SubmitTaskRequest.java` 等，以及 `TaskOrchestratorGrpc.java`

- [ ] **Step 3.5: 运行测试验证通过（绿）**

Run：

```bash
cd agent-proto && mvn test -Dtest=TaskProtoTest
```

Expected: PASS，3 个测试全绿

- [ ] **Step 3.6: 提交**

```bash
git add agent-proto/src/main/proto/task.proto agent-proto/src/test/java/com/agent/proto/TaskProtoTest.java
git commit -m "feat(proto): add task.proto with TaskOrchestrator service and TaskInstance matching task_instance table schema"
```

---

## Task 4: planning.proto + memory.proto + model.proto 服务定义

**Files:**
- Create: `agent-proto/src/main/proto/planning.proto`
- Create: `agent-proto/src/main/proto/memory.proto`
- Create: `agent-proto/src/main/proto/model.proto`
- Create: `agent-proto/src/test/java/com/agent/proto/PlanningMemoryModelProtoTest.java`

依据：doc 02-api §4（memory）、§5（model）、§6（task 中的 PlanningService），doc 01-database §2.3 DAG 节点结构

- [ ] **Step 4.1: 编写失败测试三个 proto 字段**

文件路径：`agent-proto/src/test/java/com/agent/proto/PlanningMemoryModelProtoTest.java`

```java
package com.agent.proto;

import agentplatform.planning.v1.*;
import agentplatform.memory.v1.*;
import agentplatform.model.v1.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanningMemoryModelProtoTest {

    @Test
    void dagNode_holdsAllFieldsFromDocSchema() {
        DagNode node = DagNode.newBuilder()
                .setNodeId("n1")
                .setNodeType("subtask")
                .setSubtaskId("st_001")
                .setTitle("查询用户订单")
                .setGoal("查询最近 7 天订单")
                .setAgentId(1001L)
                .addAbilityTags("query")
                .addAbilityTags("order")
                .setInputsJson("{\"userId\":\"u_123\"}")
                .setOutputsJson("{}")
                .setMaxRetries(2)
                .setTimeoutMs(30000)
                .setModelTier("middle")
                .addDependsOn("n0")
                .setStatus("pending")
                .build();
        DagNode parsed = DagNode.parseFrom(node.toByteArray());
        assertEquals("n1", parsed.getNodeId());
        assertEquals("查询用户订单", parsed.getTitle());
        assertEquals(2, parsed.getAbilityTagsCount());
        assertEquals("query", parsed.getAbilityTags(0));
        assertEquals(30000, parsed.getTimeoutMs());
        assertEquals(1, parsed.getDependsOnCount());
    }

    @Test
    void planResponse_carriesDagJsonAndVersion() {
        PlanResponse resp = PlanResponse.newBuilder()
                .setDagJson("{\"nodes\":[]}")
                .setDagVersion(1)
                .setSource("ai")
                .setTemplateId(0L)
                .addWarnings("节点 n3 没有依赖")
                .build();
        PlanResponse parsed = PlanResponse.parseFrom(resp.toByteArray());
        assertEquals("{\"nodes\":[]}", parsed.getDagJson());
        assertEquals(1, parsed.getDagVersion());
        assertEquals("ai", parsed.getSource());
        assertEquals(1, parsed.getWarningsCount());
    }

    @Test
    void recalledMemory_carriesScoresAndSourceType() {
        RecalledMemory m = RecalledMemory.newBuilder()
                .setMemoryId("mem_001")
                .setContent("用户偏好周末下单")
                .setSourceType("task")
                .setSourceTaskId("tk_yyy")
                .setImportanceScore(0.85)
                .setRelevanceScore(0.92)
                .setCreatedAt(1719405600000L)
                .build();
        RecalledMemory parsed = RecalledMemory.parseFrom(m.toByteArray());
        assertEquals("mem_001", parsed.getMemoryId());
        assertEquals("task", parsed.getSourceType());
        assertEquals(0.85, parsed.getImportanceScore(), 0.001);
    }

    @Test
    void chatRequest_supportsModelParamsWithEnableCotAndPromptCache() {
        ModelParams params = ModelParams.newBuilder()
                .setTemperature(0.7)
                .setMaxTokens(2048)
                .setTopP(0.9)
                .addStop("</end>")
                .setEnableCot(true)
                .setRequireSource(false)
                .build();
        ChatRequest req = ChatRequest.newBuilder()
                .setCallId("call_xxx")
                .setTaskId("tk_yyy")
                .setScene("planning")
                .setTier("strong")
                .setPreferredModel("qwen-max")
                .addMessages(Message.newBuilder().setRole("user").setContent("帮我做规划").build())
                .setParams(params)
                .setEnablePromptCache(true)
                .build();
        ChatRequest parsed = ChatRequest.parseFrom(req.toByteArray());
        assertEquals("planning", parsed.getScene());
        assertEquals("strong", parsed.getTier());
        assertTrue(parsed.getParams().getEnableCot());
        assertTrue(parsed.getEnablePromptCache());
        assertEquals(0.7, parsed.getParams().getTemperature(), 0.001);
        assertEquals(1, parsed.getMessagesCount());
    }

    @Test
    void chatChunk_oneofCanHoldEitherToolCallOrFinish() {
        ChatChunk finishChunk = ChatChunk.newBuilder()
                .setCallId("call_xxx")
                .setDelta("")
                .setFinish(FinishReason.STOP)
                .build();
        ChatChunk parsed = ChatChunk.parseFrom(finishChunk.toByteArray());
        assertEquals(FinishReason.STOP, parsed.getFinish());
        assertEquals(ChatChunk.ExtraCase.FINISH, parsed.getExtraCase());
    }
}
```

- [ ] **Step 4.2: 运行测试验证失败（红）**

Run：

```bash
cd agent-proto && mvn test -Dtest=PlanningMemoryModelProtoTest
```

Expected: FAIL，编译错误 `package agentplatform.planning.v1 does not exist` / `agentplatform.memory.v1` / `agentplatform.model.v1`

- [ ] **Step 4.3: 编写 planning.proto**

文件路径：`agent-proto/src/main/proto/planning.proto`

```protobuf
syntax = "proto3";
package agentplatform.planning.v1;

import "common.proto";

option java_multiple_files = true;
option java_package = "agentplatform.planning.v1";

// 智能规划服务（doc 02-api §6，端口 8086）
service PlanningService {
  // 复杂度评估
  rpc AssessComplexity(AssessRequest) returns (AssessResponse);
  // 生成规划（DAG）
  rpc Plan(PlanRequest) returns (PlanResponse);
  // 规划自检
  rpc ValidatePlan(ValidateRequest) returns (ValidateResponse);
  // 重规划
  rpc Replan(ReplanRequest) returns (PlanResponse);
}

message AssessRequest {
  string task_id = 1;
  string title = 2;
  string goal = 3;
  agentplatform.common.v1.TraceContext trace = 99;
}

message AssessResponse {
  int32 complexity = 1;       // 1=L1 2=L2 3=L3
  string reason = 2;
  repeated string suggested_ability_tags = 3;
}

message PlanRequest {
  string task_id = 1;
  string task_schema_json = 2;
  bool prefer_template = 3;
  agentplatform.common.v1.TraceContext trace = 99;
}

message PlanResponse {
  string dag_json = 1;        // nodes + edges 的 JSON 字符串
  int32 dag_version = 2;
  string source = 3;          // template | ai
  int64 template_id = 4;
  repeated string warnings = 5;
}

message ValidateRequest {
  string task_id = 1;
  string dag_json = 2;
  agentplatform.common.v1.TraceContext trace = 99;
}

message ValidateResponse {
  bool valid = 1;
  repeated string errors = 2;
  repeated string warnings = 3;
}

message ReplanRequest {
  string task_id = 1;
  string reason = 2;          // 触发重规划原因（如 DAG_CYCLE_DETECTED、COMPLETENESS_FAIL）
  int32 replan_count = 3;
  string previous_dag_json = 4;
  agentplatform.common.v1.TraceContext trace = 99;
}

// DAG 节点（字段对应 doc 01-database §2.3 nodes JSON 结构）
message DagNode {
  string node_id = 1;
  string node_type = 2;        // subtask | decision | sync
  string subtask_id = 3;
  string title = 4;
  string goal = 5;
  int64 agent_id = 6;
  repeated string ability_tags = 7;
  string inputs_json = 8;
  string outputs_json = 9;
  int32 max_retries = 10;
  int32 timeout_ms = 11;
  string model_tier = 12;
  repeated string depends_on = 13;
  string status = 14;          // pending | running | success | failed | skipped
}

// DAG 依赖边（字段对应 doc 01-database §2.4 edges JSON 结构）
message DagEdge {
  string from = 1;
  string to = 2;
  string dep_type = 3;         // data | logic | none
  string param_mapping_json = 4;
}

// 完整 DAG 结构
message Dag {
  int64 dag_id = 1;
  string task_id = 2;
  int32 version = 3;
  repeated DagNode nodes = 4;
  repeated DagEdge edges = 5;
  string source = 6;           // template | ai
  int64 template_id = 7;
}
```

- [ ] **Step 4.4: 编写 memory.proto**

文件路径：`agent-proto/src/main/proto/memory.proto`

```protobuf
syntax = "proto3";
package agentplatform.memory.v1;

import "common.proto";

option java_multiple_files = true;
option java_package = "agentplatform.memory.v1";

// 记忆管理服务（doc 02-api §4，端口 8088）
service MemoryService {
  // 写入长期记忆（任务完成后）
  rpc WriteLongTerm(WriteLongTermRequest) returns (WriteAck);
  // 长期记忆召回（多路融合）
  rpc Recall(RecallRequest) returns (RecallResponse);
  // 蒸馏触发
  rpc TriggerDistill(DistillRequest) returns (DistillAck);
  // 记忆查询（按 ID）
  rpc GetMemoryById(GetMemoryByIdRequest) returns (MemoryRecord);
}

message WriteLongTermRequest {
  int64 agent_id = 1;
  string user_id = 2;
  string domain = 3;
  string memory_type = 4;      // episodic | semantic | procedural
  string content = 5;
  repeated string tags = 6;
  string source_task_id = 7;
  agentplatform.common.v1.TraceContext trace = 99;
}

message WriteAck {
  string memory_id = 1;
  bool deduplicated = 2;
  string merged_memory_id = 3;
}

message RecallRequest {
  string session_id = 1;
  int64 agent_id = 2;
  string query = 3;
  repeated string strategies = 4;   // [vector, keyword, time, tag]
  int32 top_k = 5;
  int32 token_budget = 6;
  agentplatform.common.v1.TraceContext trace = 99;
}

message RecallResponse {
  repeated RecalledMemory memories = 1;
  RecallMeta meta = 2;
}

message RecalledMemory {
  string memory_id = 1;
  string content = 2;
  string source_type = 3;          // task | user | system
  string source_task_id = 4;
  double importance_score = 5;
  double relevance_score = 6;
  int64 created_at = 7;
}

message RecallMeta {
  int32 total_hits = 1;
  int32 returned = 2;
  int32 token_used = 3;
  string strategies_used = 4;
}

message DistillRequest {
  int64 agent_id = 1;
  string user_id = 2;
  string session_id = 3;
  int32 max_memories = 4;
  agentplatform.common.v1.TraceContext trace = 99;
}

message DistillAck {
  int32 distilled_count = 1;
  int32 merged_count = 2;
  int32 pruned_count = 3;
}

message GetMemoryByIdRequest {
  string memory_id = 1;
  agentplatform.common.v1.TraceContext trace = 99;
}

// 长期记忆记录（字段对应 doc 01-database memory_long_term 表）
message MemoryRecord {
  string memory_id = 1;
  int64 agent_id = 2;
  string user_id = 3;
  string domain = 4;
  string memory_type = 5;
  string content = 6;
  repeated string tags = 7;
  string source_task_id = 8;
  double importance_score = 9;
  int32 access_count = 10;
  int64 created_at = 11;
  int64 updated_at = 12;
}
```

- [ ] **Step 4.5: 编写 model.proto**

文件路径：`agent-proto/src/main/proto/model.proto`

```protobuf
syntax = "proto3";
package agentplatform.model.v1;

import "common.proto";

option java_multiple_files = true;
option java_package = "agentplatform.model.v1";

// 模型网关服务（doc 02-api §5，端口 8094）
service ModelGateway {
  // 同步调用
  rpc Chat(ChatRequest) returns (ChatResponse);
  // 流式调用（server streaming）
  rpc StreamChat(ChatRequest) returns (stream ChatChunk);
  // Token 计数
  rpc CountTokens(CountTokensRequest) returns (CountTokensResponse);
  // 模型列表
  rpc ListModels(ListModelsRequest) returns (ListModelsResponse);
}

message ChatRequest {
  string call_id = 1;
  string task_id = 2;
  string scene = 3;                 // intent | planning | tool_call | summary | audit
  string tier = 4;                  // light | middle | strong
  string preferred_model = 5;       // 可指定，否则按路由
  repeated Message messages = 6;
  ModelParams params = 7;
  bool enable_prompt_cache = 8;
  agentplatform.common.v1.TraceContext trace = 99;
}

message Message {
  string role = 1;                  // system | user | assistant | tool
  string content = 2;
  repeated ToolCall tool_calls = 3;
  string tool_call_id = 4;
}

message ToolCall {
  string call_id = 1;
  string tool_id = 2;
  string name = 3;
  string input_json = 4;
  string status = 5;                // pending | success | failed
  string output_json = 6;
}

message ModelParams {
  double temperature = 1;
  int32 max_tokens = 2;
  double top_p = 3;
  repeated string stop = 4;
  bool enable_cot = 5;              // 强制思维链
  bool require_source = 6;         // 要求来源标注
}

message ChatResponse {
  string call_id = 1;
  string model = 2;                 // 实际使用的模型
  string provider = 3;
  string content = 4;
  repeated ToolCall tool_calls = 5;
  int32 input_tokens = 6;
  int32 output_tokens = 7;
  int64 cost_cent = 8;
  bool cache_hit = 9;
  int32 duration_ms = 10;
}

message ChatChunk {
  string call_id = 1;
  string delta = 2;
  oneof extra {
    ToolCall tool_call = 3;
    FinishReason finish = 4;
  }
}

enum FinishReason {
  FINISH_REASON_UNSPECIFIED = 0;
  STOP = 1;
  LENGTH = 2;
  TOOL_CALLS = 3;
  CONTENT_FILTER = 4;
}

message CountTokensRequest {
  string model = 1;
  repeated Message messages = 2;
}

message CountTokensResponse {
  int32 token_count = 1;
}

message ListModelsRequest {
  string tier = 1;                   // light | middle | strong | all
  agentplatform.common.v1.TraceContext trace = 99;
}

message ModelInfo {
  string model_id = 1;
  string display_name = 2;
  string provider = 3;
  string tier = 4;
  int32 max_context = 5;
  bool supports_streaming = 6;
  bool supports_tool_call = 7;
  double price_input_per_1k_cent = 8;
  double price_output_per_1k_cent = 9;
}

message ListModelsResponse {
  repeated ModelInfo models = 1;
}
```

- [ ] **Step 4.6: 运行 protobuf:compile + compile-custom 验证生成**

Run：

```bash
cd agent-proto && mvn protobuf:compile protobuf:compile-custom
```

Expected: `BUILD SUCCESS`，生成 `DagNode.java`、`PlanResponse.java`、`RecalledMemory.java`、`ChatRequest.java`、`ChatChunk.java`、`FinishReason.java`、`ModelGatewayGrpc.java`、`MemoryServiceGrpc.java`、`PlanningServiceGrpc.java`

- [ ] **Step 4.7: 运行测试验证通过（绿）**

Run：

```bash
cd agent-proto && mvn test -Dtest=PlanningMemoryModelProtoTest
```

Expected: PASS，5 个测试全绿

- [ ] **Step 4.8: 提交**

```bash
git add agent-proto/src/main/proto/planning.proto agent-proto/src/main/proto/memory.proto agent-proto/src/main/proto/model.proto agent-proto/src/test/java/com/agent/proto/PlanningMemoryModelProtoTest.java
git commit -m "feat(proto): add planning.proto(DagNode/DagEdge), memory.proto(RecalledMemory), model.proto(StreamChat with ModelParams)"
```

---

## Task 5: tool.proto + knowledge.proto + agent_runtime.proto 服务定义

**Files:**
- Create: `agent-proto/src/main/proto/tool.proto`
- Create: `agent-proto/src/main/proto/knowledge.proto`
- Create: `agent-proto/src/main/proto/agent_runtime.proto`
- Create: `agent-proto/src/test/java/com/agent/proto/ToolKnowledgeRuntimeProtoTest.java`

依据：doc 02-api §3（tool）、§8（knowledge）、§7（agent_runtime），doc 02-api §1.3 task 状态

- [ ] **Step 5.1: 编写失败测试三个 proto 字段**

文件路径：`agent-proto/src/test/java/com/agent/proto/ToolKnowledgeRuntimeProtoTest.java`

```java
package com.agent.proto;

import agentplatform.tool.v1.*;
import agentplatform.knowledge.v1.*;
import agentplatform.agent_runtime.v1.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolKnowledgeRuntimeProtoTest {

    @Test
    void toolInvokeRequest_carriesRiskLevelAndPromptCacheKey() {
        ToolInvokeRequest req = ToolInvokeRequest.newBuilder()
                .setCallId("call_xxx")
                .setTaskId("tk_yyy")
                .setStepNo(1)
                .setAgentId(1001L)
                .setToolId("tl_query_order")
                .setToolVersion(1)
                .setInputJson("{\"userId\":\"u_123\"}")
                .setRiskLevel(1)
                .setPromptCacheKey("cache:tool:tl_query_order:u_123")
                .build();
        ToolInvokeRequest parsed = ToolInvokeRequest.parseFrom(req.toByteArray());
        assertEquals("call_xxx", parsed.getCallId());
        assertEquals(1, parsed.getRiskLevel());
        assertEquals("cache:tool:tl_query_order:u_123", parsed.getPromptCacheKey());
    }

    @Test
    void toolRegistry_holdsRiskLevelAndExecutorType() {
        ToolRegistry reg = ToolRegistry.newBuilder()
                .setToolId("tl_query_order")
                .setName("query_order")
                .setDisplayName("查询订单")
                .setDescription("根据用户ID查询订单列表")
                .setToolType("atomic")
                .setRiskLevel(1)
                .setExecutorType("proxy")
                .setEndpoint("grpc://order-service/OrderService/QueryOrder")
                .setTimeoutMs(5000)
                .setInputSchemaJson("{\"type\":\"object\"}")
                .setOutputSchemaJson("{\"type\":\"object\"}")
                .build();
        ToolRegistry parsed = ToolRegistry.parseFrom(reg.toByteArray());
        assertEquals("query_order", parsed.getName());
        assertEquals(1, parsed.getRiskLevel());
        assertEquals("proxy", parsed.getExecutorType());
        assertEquals(5000, parsed.getTimeoutMs());
    }

    @Test
    void knowledgeQuery_carriesAclRolesAndTopK() {
        KnowledgeQuery q = KnowledgeQuery.newBuilder()
                .setKbId("kb_001")
                .setQuery("退货政策是什么")
                .setTopK(5)
                .setUserId("u_123")
                .addRoles("cs_agent")
                .build();
        KnowledgeQuery parsed = KnowledgeQuery.parseFrom(q.toByteArray());
        assertEquals("kb_001", parsed.getKbId());
        assertEquals(5, parsed.getTopK());
        assertEquals(1, parsed.getRolesCount());
        assertEquals("cs_agent", parsed.getRoles(0));
    }

    @Test
    void agentState_carriesCurrentStepAndTokenUsed() {
        AgentState state = AgentState.newBuilder()
                .setAgentInstanceId("ai_xxx")
                .setTaskId("tk_yyy")
                .setSubtaskId("st_001")
                .setNodeId("n1")
                .setCurrentStep(3)
                .setCurrentThink("需要先查询用户订单")
                .setMaxSteps(10)
                .setTokenUsed(8500)
                .setTokenBudget(60000)
                .setStatus("RUNNING")
                .build();
        AgentState parsed = AgentState.parseFrom(state.toByteArray());
        assertEquals(3, parsed.getCurrentStep());
        assertEquals("需要先查询用户订单", parsed.getCurrentThink());
        assertEquals(8500, parsed.getTokenUsed());
        assertEquals("RUNNING", parsed.getStatus());
    }

    @Test
    void startAgentRequest_carriesAllConfigFields() {
        StartAgentRequest req = StartAgentRequest.newBuilder()
                .setTaskId("tk_yyy")
                .setSubtaskId("st_001")
                .setNodeId("n1")
                .setAgentId(1001L)
                .setAgentVersion(2)
                .setInputsJson("{\"userId\":\"u_123\"}")
                .setMaxSteps(10)
                .setTokenBudget(60000)
                .setCostBudgetCent(5000L)
                .build();
        StartAgentRequest parsed = StartAgentRequest.parseFrom(req.toByteArray());
        assertEquals("st_001", parsed.getSubtaskId());
        assertEquals(1001L, parsed.getAgentId());
        assertEquals(10, parsed.getMaxSteps());
        assertEquals(5000L, parsed.getCostBudgetCent());
    }
}
```

- [ ] **Step 5.2: 运行测试验证失败（红）**

Run：

```bash
cd agent-proto && mvn test -Dtest=ToolKnowledgeRuntimeProtoTest
```

Expected: FAIL，编译错误 `package agentplatform.tool.v1` / `agentplatform.knowledge.v1` / `agentplatform.agent_runtime.v1` does not exist

- [ ] **Step 5.3: 编写 tool.proto**

文件路径：`agent-proto/src/main/proto/tool.proto`

```protobuf
syntax = "proto3";
package agentplatform.tool.v1;

import "common.proto";

option java_multiple_files = true;
option java_package = "agentplatform.tool.v1";

// 工具网关服务（doc 02-api §3.2，端口 8090）
service ToolGateway {
  // 工具调用统一入口（ADR-005）
  rpc Invoke(ToolInvokeRequest) returns (ToolInvokeResponse);
  // 注册工具（管理流）
  rpc RegisterTool(RegisterToolRequest) returns (RegisterToolAck);
  // 工具列表
  rpc ListTools(ListToolsRequest) returns (ListToolsResponse);
  // 工具注册详情
  rpc GetToolRegistry(GetToolRegistryRequest) returns (ToolRegistry);
}

message ToolInvokeRequest {
  string call_id = 1;
  string task_id = 2;
  int32 step_no = 3;
  int64 agent_id = 4;
  string tool_id = 5;
  int32 tool_version = 6;
  string input_json = 7;          // JSON 字符串，符合 inputSchema
  int32 risk_level = 8;           // 1=R1 2=R2 3=R3（执行前校验）
  string prompt_cache_key = 9;    // 用于结果缓存与审计
  agentplatform.common.v1.TraceContext trace = 99;
}

message ToolInvokeResponse {
  string call_id = 1;
  string status = 2;               // success | failed | timeout | blocked
  string output_json = 3;
  string error_code = 4;
  string error_msg = 5;
  int32 duration_ms = 6;
  int64 cost_cent = 7;
  int32 token_used = 8;
  bool from_cache = 9;
}

message RegisterToolRequest {
  string name = 1;
  string display_name = 2;
  string description = 3;
  repeated string scene_tags = 4;
  string tool_type = 5;            // atomic | composite
  int32 risk_level = 6;
  string input_schema_json = 7;
  string output_schema_json = 8;
  string error_codes_json = 9;
  string executor_type = 10;       // general | proxy | sandbox
  string endpoint = 11;           // grpc://...
  int32 timeout_ms = 12;
  string undo_action = 13;
  agentplatform.common.v1.TraceContext trace = 99;
}

message RegisterToolAck {
  string tool_id = 1;
  int32 version = 2;
  bool approved = 3;
}

message ListToolsRequest {
  string scene_tag = 1;
  int32 risk_level_max = 2;
  int32 page = 3;
  int32 size = 4;
  agentplatform.common.v1.TraceContext trace = 99;
}

message ListToolsResponse {
  repeated ToolRegistry tools = 1;
  agentplatform.common.v1.Pagination pagination = 2;
}

message GetToolRegistryRequest {
  string tool_id = 1;
  int32 version = 2;               // 0 = 最新版本
  agentplatform.common.v1.TraceContext trace = 99;
}

// 工具注册信息（字段对应 doc 02-api §3.1 POST /api/v1/tools）
message ToolRegistry {
  string tool_id = 1;
  string name = 2;
  string display_name = 3;
  string description = 4;
  repeated string scene_tags = 5;
  string tool_type = 6;
  int32 risk_level = 7;
  string input_schema_json = 8;
  string output_schema_json = 9;
  string error_codes_json = 10;
  string executor_type = 11;
  string endpoint = 12;
  int32 timeout_ms = 13;
  string undo_action = 14;
  int32 version = 15;
  bool approved = 16;
  int64 created_at = 17;
  int64 updated_at = 18;
}
```

- [ ] **Step 5.4: 编写 knowledge.proto**

文件路径：`agent-proto/src/main/proto/knowledge.proto`

```protobuf
syntax = "proto3";
package agentplatform.knowledge.v1;

import "common.proto";

option java_multiple_files = true;
option java_package = "agentplatform.knowledge.v1";

// 知识库服务（doc 02-api §8.2，端口 8098）
service KnowledgeService {
  // 检索（内部 gRPC）
  rpc Retrieve(KnowledgeQuery) returns (KnowledgeResult);
  // 文档入库（切片+向量化）
  rpc Ingest(IngestRequest) returns (IngestResponse);
  // 版本管理（创建/回滚/列表）
  rpc VersionManage(VersionManageRequest) returns (VersionManageResponse);
}

message KnowledgeQuery {
  string kb_id = 1;
  string query = 2;
  int32 top_k = 3;
  string user_id = 4;              // ACL 过滤
  repeated string roles = 5;
  agentplatform.common.v1.TraceContext trace = 99;
}

message KnowledgeResult {
  repeated KnowledgeChunk chunks = 1;
  int32 total_hits = 2;
  int32 token_used = 3;
}

message KnowledgeChunk {
  string chunk_id = 1;
  string doc_id = 2;
  string content = 3;
  double score = 4;
  string source = 5;               // 文件名 / URL
  int32 page = 6;
  map<string, string> metadata = 7;
}

message IngestRequest {
  string kb_id = 1;
  string doc_id = 2;
  string file_url = 3;            // MinIO URL
  string title = 4;
  string acl_json = 5;            // {"roles":["cs_agent"]}
  string chunk_strategy = 6;      // fixed | recursive | sentence
  int32 chunk_size = 7;
  int32 chunk_overlap = 8;
  agentplatform.common.v1.TraceContext trace = 99;
}

message IngestResponse {
  string doc_id = 1;
  int32 chunk_count = 2;
  int32 token_count = 3;
  string status = 4;              // success | failed | partial
  string error_msg = 5;
}

message VersionManageRequest {
  string action = 1;              // create | rollback | list
  string kb_id = 2;
  string doc_id = 3;
  int32 target_version = 4;       // rollback 时指定
  string reason = 5;
  agentplatform.common.v1.TraceContext trace = 99;
}

message VersionManageResponse {
  string action = 1;
  int32 current_version = 2;
  repeated DocVersion versions = 3;
}

message DocVersion {
  string doc_id = 1;
  int32 version = 2;
  int64 created_at = 3;
  string created_by = 4;
  string change_log = 5;
  bool active = 6;
}
```

- [ ] **Step 5.5: 编写 agent_runtime.proto**

文件路径：`agent-proto/src/main/proto/agent_runtime.proto`

```protobuf
syntax = "proto3";
package agentplatform.agent_runtime.v1;

import "common.proto";

option java_multiple_files = true;
option java_package = "agentplatform.agent_runtime.v1";

// Agent 运行时服务（doc 02-api §7，端口 8092，ADR-002 无状态）
service AgentRuntime {
  // 启动 Agent 实例（由 task-orchestrator 通过 RocketMQ 触发或 gRPC 触发）
  rpc StartAgent(StartAgentRequest) returns (StartAgentResponse);
  // 单步执行
  rpc Step(StepRequest) returns (StepResponse);
  // 获取状态
  rpc GetState(GetStateRequest) returns (AgentState);
  // 暂停
  rpc Pause(PauseRequest) returns (PauseResponse);
  // 恢复
  rpc Resume(ResumeRequest) returns (ResumeResponse);
}

message StartAgentRequest {
  string task_id = 1;
  string subtask_id = 2;
  string node_id = 3;
  int64 agent_id = 4;
  int32 agent_version = 5;
  string inputs_json = 6;
  int32 max_steps = 7;
  int32 token_budget = 8;
  int64 cost_budget_cent = 9;
  agentplatform.common.v1.TraceContext trace = 99;
}

message StartAgentResponse {
  string agent_instance_id = 1;
  string status = 2;               // STARTED | RUNNING
  int64 started_at = 3;
}

message StepRequest {
  string agent_instance_id = 1;
  agentplatform.common.v1.TraceContext trace = 99;
}

message StepResponse {
  string agent_instance_id = 1;
  int32 step_no = 2;
  string phase = 3;               // think | act | observe | reflect
  string action_type = 4;
  string action_target = 5;
  string input_json = 6;
  string output_json = 7;
  int32 token_used = 8;
  int64 cost_cent = 9;
  int32 duration_ms = 10;
  string status = 11;             // success | failed | waiting_tool | waiting_human
  bool finished = 12;
}

message GetStateRequest {
  string agent_instance_id = 1;
  agentplatform.common.v1.TraceContext trace = 99;
}

// Agent 运行时状态（ADR-002：状态外置 Redis runtime:{agentInstanceId}:state）
message AgentState {
  string agent_instance_id = 1;
  string task_id = 2;
  string subtask_id = 3;
  string node_id = 4;
  int32 current_step = 5;
  string current_think = 6;
  int32 max_steps = 7;
  int32 token_used = 8;
  int32 token_budget = 9;
  int64 cost_used_cent = 10;
  int64 cost_budget_cent = 11;
  string status = 12;             // STARTED | RUNNING | PAUSED | SUCCESS | FAILED | WAITING_HUMAN
  int64 started_at = 13;
  int64 updated_at = 14;
}

message PauseRequest {
  string agent_instance_id = 1;
  string reason = 2;
  agentplatform.common.v1.TraceContext trace = 99;
}

message PauseResponse {
  string agent_instance_id = 1;
  string status = 2;               // PAUSED
  int64 paused_at = 3;
}

message ResumeRequest {
  string agent_instance_id = 1;
  agentplatform.common.v1.TraceContext trace = 99;
}

message ResumeResponse {
  string agent_instance_id = 1;
  string status = 2;               // RUNNING
  int64 resumed_at = 3;
}
```

- [ ] **Step 5.6: 运行 protobuf:compile + compile-custom 验证 7 个服务全部编译**

Run：

```bash
cd agent-proto && mvn clean protobuf:compile protobuf:compile-custom
```

Expected: `BUILD SUCCESS`，确认生成 `ToolGatewayGrpc.java`、`KnowledgeServiceGrpc.java`、`AgentRuntimeGrpc.java`，至此 7 个服务 stub 全部生成

- [ ] **Step 5.7: 运行测试验证通过（绿）**

Run：

```bash
cd agent-proto && mvn test -Dtest=ToolKnowledgeRuntimeProtoTest
```

Expected: PASS，5 个测试全绿

- [ ] **Step 5.8: 提交**

```bash
git add agent-proto/src/main/proto/tool.proto agent-proto/src/main/proto/knowledge.proto agent-proto/src/main/proto/agent_runtime.proto agent-proto/src/test/java/com/agent/proto/ToolKnowledgeRuntimeProtoTest.java
git commit -m "feat(proto): complete 7 gRPC services - ToolGateway(risk_level/prompt_cache_key), KnowledgeService, AgentRuntime(AgentState)"
```

---

## Task 6: 创建 agent-common Maven 项目骨架 + ErrorCode 枚举 + BusinessException

**Files:**
- Create: `agent-common/pom.xml`
- Create: `agent-common/src/main/java/com/agent/common/exception/ErrorCode.java`
- Create: `agent-common/src/main/java/com/agent/common/exception/BusinessException.java`
- Create: `agent-common/src/test/java/com/agent/common/exception/BusinessExceptionTest.java`

错误码来源：doc 02-api §0.5 错误码规范

- [ ] **Step 6.1: 创建 agent-common/pom.xml**

文件路径：`e:\git\Agent-Platform-Prototype\agent-common\pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.agentplatform</groupId>
    <artifactId>agent-common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    <name>agent-common</name>
    <description>Agent Platform Common DTOs, Exceptions, Utils</description>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-boot.version>3.2.4</spring-boot.version>
        <lombok.version>1.18.32</lombok.version>
        <jackson.version>2.16.1</jackson.version>
        <junit.version>5.10.2</junit.version>
        <mockito.version>5.11.0</mockito.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.agentplatform</groupId>
            <artifactId>agent-proto</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
            <version>${spring-boot.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>${spring-boot.version}</version>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <scope>provided</scope>
        </dependency>

        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
            <version>${jackson.version}</version>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>${mockito.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.12.1</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 6.2: 运行 mvn validate 验证 pom 配置**

Run（在项目根目录下）：

```bash
cd agent-common && mvn validate
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6.3: 编写失败测试 BusinessExceptionTest**

文件路径：`agent-common/src/test/java/com/agent/common/exception/BusinessExceptionTest.java`

```java
package com.agent.common.exception;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BusinessExceptionTest {

    @Test
    void construct_withErrorCodeAndMessage_setsFields() {
        BusinessException ex = new BusinessException(ErrorCode.TASK_NOT_FOUND, "任务不存在");
        assertEquals(ErrorCode.TASK_NOT_FOUND, ex.getErrorCode());
        assertEquals("任务不存在", ex.getMessage());
        assertEquals(404, ex.getErrorCode().getHttpStatus());
        assertEquals("TASK_NOT_FOUND", ex.getErrorCode().getCode());
    }

    @Test
    void construct_withDetails_returnsDetailsMap() {
        Map<String, Object> details = Map.of("taskId", "tk_xxx");
        BusinessException ex = new BusinessException(ErrorCode.VALIDATION_FAILED, "参数错误", details);
        assertEquals(details, ex.getDetails());
        assertEquals("VALIDATION_FAILED", ex.getErrorCode().getCode());
        assertEquals(400, ex.getErrorCode().getHttpStatus());
    }

    @Test
    void construct_withCause_preservesCause() {
        Throwable cause = new RuntimeException("db connection lost");
        BusinessException ex = new BusinessException(ErrorCode.INTERNAL, "内部错误", cause);
        assertSame(cause, ex.getCause());
        assertEquals(500, ex.getErrorCode().getHttpStatus());
    }

    @Test
    void errorCode_forbiddenHasHttpStatus403() {
        assertEquals(403, ErrorCode.FORBIDDEN.getHttpStatus());
        assertEquals(403, ErrorCode.TOOL_RISK_DENIED.getHttpStatus());
    }

    @Test
    void errorCode_costBudgetExceededIs429() {
        assertEquals(429, ErrorCode.COST_BUDGET_EXCEEDED.getHttpStatus());
        assertEquals(429, ErrorCode.RATE_LIMITED.getHttpStatus());
    }

    @Test
    void errorCode_dagCycleDetectedIs409() {
        assertEquals(409, ErrorCode.DAG_CYCLE_DETECTED.getHttpStatus());
        assertEquals(409, ErrorCode.TASK_STATUS_CONFLICT.getHttpStatus());
    }

    @Test
    void errorCode_businessLogicErrorsAre500() {
        assertEquals(500, ErrorCode.COMPLETENESS_FAIL.getHttpStatus());
        assertEquals(500, ErrorCode.REPLAN_EXHAUSTED.getHttpStatus());
        assertEquals(500, ErrorCode.HALLUCINATION_SUSPECTED.getHttpStatus());
        assertEquals(500, ErrorCode.FACT_INCONSISTENCY.getHttpStatus());
    }

    @Test
    void errorCode_timeoutIs504() {
        assertEquals(504, ErrorCode.TIMEOUT.getHttpStatus());
        assertEquals(504, ErrorCode.TOOL_TIMEOUT.getHttpStatus());
        assertEquals(504, ErrorCode.MODEL_TIMEOUT.getHttpStatus());
    }

    @Test
    void errorCode_runtimeErrorsAre500() {
        assertEquals(500, ErrorCode.MAX_STEPS_EXCEEDED.getHttpStatus());
        assertEquals(500, ErrorCode.CONTEXT_WINDOW_EXHAUSTED.getHttpStatus());
        assertEquals(500, ErrorCode.MODEL_GATEWAY_ERROR.getHttpStatus());
    }
}
```

- [ ] **Step 6.4: 运行测试验证失败（红）**

Run：

```bash
cd agent-common && mvn test -Dtest=BusinessExceptionTest
```

Expected: FAIL，编译错误 `package com.agent.common.exception does not exist`

- [ ] **Step 6.5: 编写 ErrorCode 枚举实现**

文件路径：`agent-common/src/main/java/com/agent/common/exception/ErrorCode.java`

```java
package com.agent.common.exception;

/**
 * 平台统一错误码（对应 doc 02-api §0.5 错误码规范）
 */
public enum ErrorCode {

    // 成功
    OK("OK", 200, "成功"),

    // 401 未认证
    UNAUTHENTICATED("UNAUTHENTICATED", 401, "未认证"),

    // 403 无权限
    FORBIDDEN("FORBIDDEN", 403, "无权限"),
    TOOL_RISK_DENIED("TOOL_RISK_DENIED", 403, "工具风险被拒绝"),

    // 404 资源不存在
    TASK_NOT_FOUND("TASK_NOT_FOUND", 404, "任务不存在"),
    AGENT_NOT_FOUND("AGENT_NOT_FOUND", 404, "Agent 不存在"),
    TOOL_NOT_FOUND("TOOL_NOT_FOUND", 404, "工具不存在"),

    // 400 参数校验
    VALIDATION_FAILED("VALIDATION_FAILED", 400, "参数校验失败"),
    PARAM_INVALID("PARAM_INVALID", 400, "参数非法"),
    CONTENT_BLOCKED("CONTENT_BLOCKED", 400, "内容被拦截"),

    // 409 状态冲突
    TASK_STATUS_CONFLICT("TASK_STATUS_CONFLICT", 409, "任务状态冲突"),
    DAG_CYCLE_DETECTED("DAG_CYCLE_DETECTED", 409, "DAG 检测到环"),
    DAG_VERSION_CONFLICT("DAG_VERSION_CONFLICT", 409, "DAG 版本冲突"),

    // 429 限流
    RATE_LIMITED("RATE_LIMITED", 429, "限流"),
    QUOTA_EXCEEDED("QUOTA_EXCEEDED", 429, "配额超限"),
    COST_BUDGET_EXCEEDED("COST_BUDGET_EXCEEDED", 429, "成本预算超限"),

    // 500 内部错误
    INTERNAL("INTERNAL", 500, "内部错误"),
    MODEL_GATEWAY_ERROR("MODEL_GATEWAY_ERROR", 500, "模型网关错误"),
    COMPLETENESS_FAIL("COMPLETENESS_FAIL", 500, "完整性校验失败"),
    REPLAN_EXHAUSTED("REPLAN_EXHAUSTED", 500, "重规划次数耗尽"),
    HALLUCINATION_SUSPECTED("HALLUCINATION_SUSPECTED", 500, "疑似幻觉"),
    FACT_INCONSISTENCY("FACT_INCONSISTENCY", 500, "事实不一致"),
    MAX_STEPS_EXCEEDED("MAX_STEPS_EXCEEDED", 500, "超过最大步数"),
    CONTEXT_WINDOW_EXHAUSTED("CONTEXT_WINDOW_EXHAUSTED", 500, "上下文窗口耗尽"),

    // 503 服务不可用
    DEPENDENCY_DOWN("DEPENDENCY_DOWN", 503, "依赖服务不可用"),
    CIRCUIT_OPEN("CIRCUIT_OPEN", 503, "熔断开启"),

    // 504 超时
    TIMEOUT("TIMEOUT", 504, "超时"),
    TOOL_TIMEOUT("TOOL_TIMEOUT", 504, "工具调用超时"),
    MODEL_TIMEOUT("MODEL_TIMEOUT", 504, "模型调用超时");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
```

- [ ] **Step 6.6: 编写 BusinessException 实现**

文件路径：`agent-common/src/main/java/com/agent/common/exception/BusinessException.java`

```java
package com.agent.common.exception;

import java.util.Collections;
import java.util.Map;

/**
 * 业务异常基类，所有平台业务异常均继承此类。
 * 携带 ErrorCode + 消息 + 结构化详情 Map + 可选 cause。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.details = Collections.emptyMap();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = Collections.emptyMap();
    }

    public BusinessException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Collections.emptyMap() : details;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = Collections.emptyMap();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
```

- [ ] **Step 6.7: 运行测试验证通过（绿）**

Run：

```bash
cd agent-common && mvn test -Dtest=BusinessExceptionTest
```

Expected: PASS，9 个测试全绿

- [ ] **Step 6.8: 提交**

```bash
git add agent-common/pom.xml agent-common/src/main/java/com/agent/common/exception/ErrorCode.java agent-common/src/main/java/com/agent/common/exception/BusinessException.java agent-common/src/test/java/com/agent/common/exception/BusinessExceptionTest.java
git commit -m "feat(common): add agent-common skeleton with ErrorCode enum and BusinessException"
```

---

## Task 7: agent-common 常量枚举（TaskStatus / ComplexityLevel / AgentStatus / RiskLevel）

**Files:**
- Create: `agent-common/src/main/java/com/agent/common/constant/TaskStatus.java`
- Create: `agent-common/src/main/java/com/agent/common/constant/ComplexityLevel.java`
- Create: `agent-common/src/main/java/com/agent/common/constant/AgentStatus.java`
- Create: `agent-common/src/main/java/com/agent/common/constant/RiskLevel.java`
- Create: `agent-common/src/test/java/com/agent/common/constant/ConstantsEnumTest.java`

状态机依据：用户任务描述给出 10 状态；Agent 状态 0/1/2/3；RiskLevel R1/R2/R3 + executor 字段

- [ ] **Step 7.1: 编写失败测试 ConstantsEnumTest**

文件路径：`agent-common/src/test/java/com/agent/common/constant/ConstantsEnumTest.java`

```java
package com.agent.common.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConstantsEnumTest {

    @Test
    void taskStatus_hasAllTenStates() {
        assertEquals(10, TaskStatus.values().length);
        assertEquals("PENDING", TaskStatus.PENDING.name());
        assertEquals("PLANNING", TaskStatus.PLANNING.name());
        assertEquals("RUNNING", TaskStatus.RUNNING.name());
        assertEquals("SUBTASK_RUNNING", TaskStatus.SUBTASK_RUNNING.name());
        assertEquals("WAITING_HUMAN", TaskStatus.WAITING_HUMAN.name());
        assertEquals("REPLANNING", TaskStatus.REPLANNING.name());
        assertEquals("SUCCESS", TaskStatus.SUCCESS.name());
        assertEquals("FAILED", TaskStatus.FAILED.name());
        assertEquals("CANCELLED", TaskStatus.CANCELLED.name());
        assertEquals("TIMEOUT", TaskStatus.TIMEOUT.name());
    }

    @Test
    void taskStatus_isTerminal_distinguishesTerminalStates() {
        assertTrue(TaskStatus.SUCCESS.isTerminal());
        assertTrue(TaskStatus.FAILED.isTerminal());
        assertTrue(TaskStatus.CANCELLED.isTerminal());
        assertTrue(TaskStatus.TIMEOUT.isTerminal());
        assertFalse(TaskStatus.PENDING.isTerminal());
        assertFalse(TaskStatus.RUNNING.isTerminal());
        assertFalse(TaskStatus.WAITING_HUMAN.isTerminal());
    }

    @Test
    void complexityLevel_l1HasCorrectRanges() {
        assertEquals(3, ComplexityLevel.values().length);
        assertEquals(1, ComplexityLevel.L1.getLevel());
        assertEquals("L1", ComplexityLevel.L1.getCode());
        assertEquals(5, ComplexityLevel.L1.getStepRange());
        assertEquals(3, ComplexityLevel.L1.getToolRange());
        assertEquals(500L, ComplexityLevel.L1.getCostLimitCent());
    }

    @Test
    void complexityLevel_l2HasCorrectRanges() {
        assertEquals(2, ComplexityLevel.L2.getLevel());
        assertEquals(10, ComplexityLevel.L2.getStepRange());
        assertEquals(5, ComplexityLevel.L2.getToolRange());
        assertEquals(2000L, ComplexityLevel.L2.getCostLimitCent());
    }

    @Test
    void complexityLevel_l3HasCorrectRanges() {
        assertEquals(3, ComplexityLevel.L3.getLevel());
        assertEquals(30, ComplexityLevel.L3.getStepRange());
        assertEquals(10, ComplexityLevel.L3.getToolRange());
        assertEquals(10000L, ComplexityLevel.L3.getCostLimitCent());
    }

    @Test
    void complexityLevel_fromLevel_resolvesCode() {
        assertEquals(ComplexityLevel.L1, ComplexityLevel.fromLevel(1));
        assertEquals(ComplexityLevel.L2, ComplexityLevel.fromLevel(2));
        assertEquals(ComplexityLevel.L3, ComplexityLevel.fromLevel(3));
    }

    @Test
    void agentStatus_hasFourStatesWithCode() {
        assertEquals(4, AgentStatus.values().length);
        assertEquals(0, AgentStatus.DRAFT.getCode());
        assertEquals(1, AgentStatus.ONLINE.getCode());
        assertEquals(2, AgentStatus.OFFLINE.getCode());
        assertEquals(3, AgentStatus.SUSPENDED.getCode());
    }

    @Test
    void agentStatus_fromCode_resolvesEnum() {
        assertEquals(AgentStatus.DRAFT, AgentStatus.fromCode(0));
        assertEquals(AgentStatus.ONLINE, AgentStatus.fromCode(1));
        assertEquals(AgentStatus.OFFLINE, AgentStatus.fromCode(2));
        assertEquals(AgentStatus.SUSPENDED, AgentStatus.fromCode(3));
    }

    @Test
    void riskLevel_r1HasGeneralExecutor() {
        assertEquals(3, RiskLevel.values().length);
        assertEquals("R1", RiskLevel.R1.getCode());
        assertEquals(1, RiskLevel.R1.getLevel());
        assertEquals("general", RiskLevel.R1.getExecutor());
    }

    @Test
    void riskLevel_r2HasProxyExecutor() {
        assertEquals("R2", RiskLevel.R2.getCode());
        assertEquals(2, RiskLevel.R2.getLevel());
        assertEquals("proxy", RiskLevel.R2.getExecutor());
    }

    @Test
    void riskLevel_r3HasSandboxExecutor() {
        assertEquals("R3", RiskLevel.R3.getCode());
        assertEquals(3, RiskLevel.R3.getLevel());
        assertEquals("sandbox", RiskLevel.R3.getExecutor());
    }
}
```

- [ ] **Step 7.2: 运行测试验证失败（红）**

Run：

```bash
cd agent-common && mvn test -Dtest=ConstantsEnumTest
```

Expected: FAIL，编译错误 `package com.agent.common.constant does not exist`

- [ ] **Step 7.3: 编写 TaskStatus 枚举**

文件路径：`agent-common/src/main/java/com/agent/common/constant/TaskStatus.java`

```java
package com.agent.common.constant;

/**
 * 任务状态机 10 状态（doc 08-flow state-machines-and-sequences.md）
 *
 * 状态流转：PENDING → PLANNING → RUNNING ↔ SUBTASK_RUNNING → REPLANNING → RUNNING ...
 * 终态：SUCCESS / FAILED / CANCELLED / TIMEOUT
 */
public enum TaskStatus {

    PENDING(false),
    PLANNING(false),
    RUNNING(false),
    SUBTASK_RUNNING(false),
    WAITING_HUMAN(false),
    REPLANNING(false),
    SUCCESS(true),
    FAILED(true),
    CANCELLED(true),
    TIMEOUT(true);

    private final boolean terminal;

    TaskStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
```

- [ ] **Step 7.4: 编写 ComplexityLevel 枚举**

文件路径：`agent-common/src/main/java/com/agent/common/constant/ComplexityLevel.java`

```java
package com.agent.common.constant;

/**
 * 任务复杂度等级（doc 02-api §1.3 complexity）
 * - L1 简单：单步或少步操作，低预算
 * - L2 中等：少量工具调用与多步推理
 * - L3 复杂：多工具编排、长链路、高预算
 */
public enum ComplexityLevel {

    L1(1, "L1", 5, 3, 500L),
    L2(2, "L2", 10, 5, 2000L),
    L3(3, "L3", 30, 10, 10000L);

    private final int level;
    private final String code;
    private final int stepRange;       // 推荐最大步数
    private final int toolRange;       // 推荐最大工具数
    private final long costLimitCent;  // 默认成本上限（分）

    ComplexityLevel(int level, String code, int stepRange, int toolRange, long costLimitCent) {
        this.level = level;
        this.code = code;
        this.stepRange = stepRange;
        this.toolRange = toolRange;
        this.costLimitCent = costLimitCent;
    }

    public int getLevel() {
        return level;
    }

    public String getCode() {
        return code;
    }

    public int getStepRange() {
        return stepRange;
    }

    public int getToolRange() {
        return toolRange;
    }

    public long getCostLimitCent() {
        return costLimitCent;
    }

    public static ComplexityLevel fromLevel(int level) {
        for (ComplexityLevel c : values()) {
            if (c.level == level) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown ComplexityLevel: " + level);
    }
}
```

- [ ] **Step 7.5: 编写 AgentStatus 枚举**

文件路径：`agent-common/src/main/java/com/agent/common/constant/AgentStatus.java`

```java
package com.agent.common.constant;

/**
 * Agent 生命周期状态（doc 02-api §2.1）
 */
public enum AgentStatus {

    DRAFT(0),
    ONLINE(1),
    OFFLINE(2),
    SUSPENDED(3);

    private final int code;

    AgentStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static AgentStatus fromCode(int code) {
        for (AgentStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown AgentStatus code: " + code);
    }
}
```

- [ ] **Step 7.6: 编写 RiskLevel 枚举**

文件路径：`agent-common/src/main/java/com/agent/common/constant/RiskLevel.java`

```java
package com.agent.common.constant;

/**
 * 工具风险等级（doc 02-api §3.1 riskLevel，doc 00-overview §1.1 安全优先）
 *
 * - R1（general）：低风险，本地直接执行
 * - R2（proxy）：中风险，代理转发到业务系统
 * - R3（sandbox）：高风险，沙箱执行 + 双人复核
 */
public enum RiskLevel {

    R1(1, "R1", "general"),
    R2(2, "R2", "proxy"),
    R3(3, "R3", "sandbox");

    private final int level;
    private final String code;
    private final String executor;

    RiskLevel(int level, String code, String executor) {
        this.level = level;
        this.code = code;
        this.executor = executor;
    }

    public int getLevel() {
        return level;
    }

    public String getCode() {
        return code;
    }

    public String getExecutor() {
        return executor;
    }

    public static RiskLevel fromLevel(int level) {
        for (RiskLevel r : values()) {
            if (r.level == level) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown RiskLevel: " + level);
    }
}
```

- [ ] **Step 7.7: 运行测试验证通过（绿）**

Run：

```bash
cd agent-common && mvn test -Dtest=ConstantsEnumTest
```

Expected: PASS，11 个测试全绿

- [ ] **Step 7.8: 提交**

```bash
git add agent-common/src/main/java/com/agent/common/constant/ agent-common/src/test/java/com/agent/common/constant/ConstantsEnumTest.java
git commit -m "feat(common): add TaskStatus(10 states), ComplexityLevel(L1/L2/L3), AgentStatus, RiskLevel(R1/R2/R3) enums"
```

---

## Task 8: agent-common 工具类（JsonUtils / TraceUtils / TokenEstimator）+ TraceContext

**Files:**
- Create: `agent-common/src/main/java/com/agent/common/context/TraceContext.java`
- Create: `agent-common/src/main/java/com/agent/common/utils/JsonUtils.java`
- Create: `agent-common/src/main/java/com/agent/common/utils/TraceUtils.java`
- Create: `agent-common/src/main/java/com/agent/common/utils/TokenEstimator.java`
- Create: `agent-common/src/test/java/com/agent/common/utils/UtilsTest.java`

Token 系数依据：用户任务描述（中文按 1.7 倍系数估算，参考 doc 09 §3.2.c）

- [ ] **Step 8.1: 编写失败测试 UtilsTest**

文件路径：`agent-common/src/test/java/com/agent/common/utils/UtilsTest.java`

```java
package com.agent.common.utils;

import com.agent.common.context.TraceContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

    @AfterEach
    void clearThreadLocal() {
        TraceUtils.clear();
    }

    // ----- JsonUtils -----

    @Test
    void jsonUtils_toJsonAndFromJson_roundTripsObject() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "订单查询");
        data.put("count", 3);
        data.put("active", true);

        String json = JsonUtils.toJson(data);
        assertTrue(json.contains("\"name\":\"订单查询\""));
        assertTrue(json.contains("\"count\":3"));
        assertTrue(json.contains("\"active\":true"));

        Map<String, Object> parsed = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {});
        assertEquals("订单查询", parsed.get("name"));
        assertEquals(3, ((Number) parsed.get("count")).intValue());
        assertEquals(true, parsed.get("active"));
    }

    @Test
    void jsonUtils_toMap_convertsJsonString() {
        String json = "{\"taskId\":\"tk_yyy\",\"status\":\"PENDING\"}";
        Map<String, Object> map = JsonUtils.toMap(json);
        assertEquals("tk_yyy", map.get("taskId"));
        assertEquals("PENDING", map.get("status"));
    }

    @Test
    void jsonUtils_toJson_returnsNullForNull() {
        assertNull(JsonUtils.toJson(null));
        assertNull(JsonUtils.fromJson(null, new TypeReference<Map<String, Object>>() {}));
    }

    // ----- TraceUtils -----

    @Test
    void traceUtils_generateTraceId_returns32CharHex() {
        String traceId = TraceUtils.generateTraceId();
        assertNotNull(traceId);
        assertEquals(32, traceId.length());
        assertTrue(traceId.matches("[0-9a-f]{32}"));
    }

    @Test
    void traceUtils_generateTraceId_isUnique() {
        String a = TraceUtils.generateTraceId();
        String b = TraceUtils.generateTraceId();
        assertNotEquals(a, b);
    }

    @Test
    void traceUtils_setAndGetThreadLocal_roundTrip() {
        TraceContext ctx = TraceContext.builder()
                .tenantId(1001L)
                .userId("u_123")
                .sessionId("ss_a1b2c3d4")
                .taskId("tk_yyy")
                .subtaskId("st_001")
                .traceId("trace-abc")
                .spanId("span-def")
                .build();
        TraceUtils.setTrace(ctx);
        TraceContext got = TraceUtils.currentTrace();
        assertSame(ctx, got);
        assertEquals(1001L, got.getTenantId());
        assertEquals("trace-abc", got.getTraceId());
    }

    @Test
    void traceUtils_currentTrace_returnsNullWhenUnset() {
        assertNull(TraceUtils.currentTrace());
    }

    @Test
    void traceUtils_clear_removesThreadLocal() {
        TraceContext ctx = TraceContext.builder().traceId("x").build();
        TraceUtils.setTrace(ctx);
        TraceUtils.clear();
        assertNull(TraceUtils.currentTrace());
    }

    // ----- TokenEstimator -----

    @Test
    void tokenEstimator_emptyString_returnsZero() {
        assertEquals(0, TokenEstimator.estimateTokens(""));
        assertEquals(0, TokenEstimator.estimateTokens(null));
    }

    @Test
    void tokenEstimator_pureEnglish_uses4CharPerTokenHeuristic() {
        // 8 个英文字符 -> 2 token（4 字符/token）
        String text = "abcdefgh";
        assertEquals(2, TokenEstimator.estimateTokens(text));
    }

    @Test
    void tokenEstimator_pureChinese_applies1point7Coefficient() {
        // 10 个中文字符 -> 10 * 1.7 = 17.0 -> 17 token
        String text = "我是一名智能助手帮助用户查询订单";
        assertEquals(10, text.length());
        assertEquals(17, TokenEstimator.estimateTokens(text));
    }

    @Test
    void tokenEstimator_mixedContent_sumsChineseAndEnglish() {
        // 3 中文 + 4 英文 = 3*1.7 + 4/4 = 5.1 + 1 = 6.1 -> 6 token
        String text = "查订单abcd";
        assertEquals(6, TokenEstimator.estimateTokens(text));
    }
}
```

- [ ] **Step 8.2: 运行测试验证失败（红）**

Run：

```bash
cd agent-common && mvn test -Dtest=UtilsTest
```

Expected: FAIL，编译错误 `package com.agent.common.utils does not exist` / `package com.agent.common.context does not exist`

- [ ] **Step 8.3: 编写 TraceContext 类**

文件路径：`agent-common/src/main/java/com/agent/common/context/TraceContext.java`

```java
package com.agent.common.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 链路上下文（与 agent-proto 的 agentplatform.common.v1.TraceContext 字段一致）。
 * 用于 Java 业务代码内传递，避免直接依赖 protobuf 类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceContext {

    private Long tenantId;
    private String userId;
    private String sessionId;
    private String taskId;
    private String subtaskId;
    private String traceId;
    private String spanId;
}
```

- [ ] **Step 8.4: 编写 JsonUtils 实现**

文件路径：`agent-common/src/main/java/com/agent/common/utils/JsonUtils.java`

```java
package com.agent.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

/**
 * JSON 序列化工具，封装 Jackson ObjectMapper 单例。
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtils() {
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object to JSON: " + e.getMessage(), e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON: " + e.getMessage(), e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(String json) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JSON to Map: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 8.5: 编写 TraceUtils 实现**

文件路径：`agent-common/src/main/java/com/agent/common/utils/TraceUtils.java`

```java
package com.agent.common.utils;

import com.agent.common.context.TraceContext;

import java.security.SecureRandom;

/**
 * 链路工具，提供 TraceID 生成与 ThreadLocal 传递。
 */
public final class TraceUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int TRACE_ID_LENGTH = 32;

    private static final ThreadLocal<TraceContext> TRACE_HOLDER = new ThreadLocal<>();

    private TraceUtils() {
    }

    /**
     * 生成 32 字符十六进制 traceId（与 SkyWalking/常见 TraceID 兼容）。
     */
    public static String generateTraceId() {
        byte[] bytes = new byte[TRACE_ID_LENGTH / 2];
        RANDOM.nextBytes(bytes);
        char[] out = new char[TRACE_ID_LENGTH];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    public static void setTrace(TraceContext ctx) {
        TRACE_HOLDER.set(ctx);
    }

    public static TraceContext currentTrace() {
        return TRACE_HOLDER.get();
    }

    public static void clear() {
        TRACE_HOLDER.remove();
    }
}
```

- [ ] **Step 8.6: 编写 TokenEstimator 实现**

文件路径：`agent-common/src/main/java/com/agent/common/utils/TokenEstimator.java`

```java
package com.agent.common.utils;

/**
 * Token 估算器（参考 doc 09 §3.2.c）。
 *
 * 规则：
 * - 中文字符按 1.7 倍系数估算（即 1 中文字符 ≈ 1.7 token）
 * - 英文/ASCII 字符按 4 字符/token 估算
 * - 累加后向下取整
 */
public final class TokenEstimator {

    private static final double CHINESE_COEFFICIENT = 1.7;
    private static final int ENGLISH_CHARS_PER_TOKEN = 4;

    private TokenEstimator() {
    }

    /**
     * 估算给定文本的 token 数。
     *
     * @param text 待估算文本，null 或空字符串返回 0
     * @return 估算的 token 数
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int chineseCount = 0;
        int otherCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChinese(c)) {
                chineseCount++;
            } else {
                otherCount++;
            }
        }

        double chineseTokens = chineseCount * CHINESE_COEFFICIENT;
        double englishTokens = (double) otherCount / ENGLISH_CHARS_PER_TOKEN;
        return (int) Math.floor(chineseTokens + englishTokens);
    }

    private static boolean isChinese(char c) {
        // CJK Unified Ideographs 基本区与扩展 A 区
        return (c >= '\u4E00' && c <= '\u9FFF')
                || (c >= '\u3400' && c <= '\u4DBF');
    }
}
```

- [ ] **Step 8.7: 运行测试验证通过（绿）**

Run：

```bash
cd agent-common && mvn test -Dtest=UtilsTest
```

Expected: PASS，11 个测试全绿

- [ ] **Step 8.8: 运行全部测试做最终回归**

Run：

```bash
cd agent-proto && mvn test
cd ../agent-common && mvn test
```

Expected: 两个模块所有测试全绿（agent-proto 16 个，agent-common 31 个，共 47 个测试）

- [ ] **Step 8.9: 提交**

```bash
git add agent-common/src/main/java/com/agent/common/context/TraceContext.java agent-common/src/main/java/com/agent/common/utils/ agent-common/src/test/java/com/agent/common/utils/UtilsTest.java
git commit -m "feat(common): add JsonUtils, TraceUtils(ThreadLocal), TokenEstimator(1.7x chinese) and TraceContext"
```

---

## 完成验收

完成所有 8 个 Task 后，执行最终验证：

- [ ] **Final Step: 全量编译 + 全量测试**

Run：

```bash
cd agent-proto && mvn clean install -DskipTests
cd ../agent-common && mvn clean test
```

Expected:
- agent-proto：`BUILD SUCCESS`，生成 jar 安装至本地仓库
- agent-common：`BUILD SUCCESS`，47 个测试全绿

- [ ] **Final Step: 验证 .proto 文件数量**

Run：

```bash
dir /B agent-proto\src\main\proto\
```

Expected: 列出 8 个 .proto 文件：`common.proto` / `task.proto` / `planning.proto` / `memory.proto` / `model.proto` / `tool.proto` / `knowledge.proto` / `agent_runtime.proto`

- [ ] **Final Step: 验证生成的 gRPC 服务数量**

Run：

```bash
dir /S /B agent-proto\target\generated-sources\protobuf\grpc-java\*Grpc.java
```

Expected: 7 个 `*Grpc.java` 文件：`TaskOrchestratorGrpc` / `PlanningServiceGrpc` / `MemoryServiceGrpc` / `ModelGatewayGrpc` / `ToolGatewayGrpc` / `KnowledgeServiceGrpc` / `AgentRuntimeGrpc`

---

## Self-Review 自检结果

**1. Spec 覆盖检查：**
- Task 1 → pom.xml + protobuf-maven-plugin 0.6.1 + grpc 1.62.2 ✓
- Task 2 → common.proto (TraceContext / Error / Pagination) ✓
- Task 3 → task.proto + TaskInstance 字段严格对齐 doc 01-database §2.1 task_instance 表（task_id / tenant_id / session_id / user_id / title / goal / complexity / status / task_schema / dag_id / agent_id / priority / parent_task_id / replan_count / cost_limit_cent / cost_used_cent / token_used / started_at / finished_at / error_code / error_msg / result_summary / created_at / updated_at 共 23 字段 ✓）
- Task 4 → planning.proto (DagNode/DagEdge) + memory.proto (RecalledMemory) + model.proto (StreamChat + ModelParams 含 enable_cot / enable_prompt_cache / temperature / top_p) ✓
- Task 5 → tool.proto (含 risk_level / prompt_cache_key) + knowledge.proto + agent_runtime.proto (AgentState 含 currentStep / currentThink / tokenUsed) ✓
- Task 6 → agent-common pom.xml + ErrorCode（含 UNAUTHENTICATED / RATE_LIMITED / CONTENT_BLOCKED / TOOL_NOT_FOUND / PARAM_INVALID / FORBIDDEN / QUOTA_EXCEEDED / COST_BUDGET_EXCEEDED / DAG_CYCLE_DETECTED / COMPLETENESS_FAIL / REPLAN_EXHAUSTED / HALLUCINATION_SUSPECTED / FACT_INCONSISTENCY / TIMEOUT / MAX_STEPS_EXCEEDED / CONTEXT_WINDOW_EXHAUSTED 等）+ BusinessException ✓
- Task 7 → TaskStatus (10 状态) + ComplexityLevel (L1/L2/L3 含 stepRange/toolRange/costLimitCent) + AgentStatus (0/1/2/3) + RiskLevel (R1/R2/R3 含 executor) ✓
- Task 8 → JsonUtils + TraceUtils + TokenEstimator (中文 1.7 倍) + TraceContext ✓

**2. 占位符扫描：** 无 TODO / TBD / "add error handling" / "similar to Task N" 等模式，所有代码步骤含完整代码 ✓

**3. 类型一致性检查：**
- TraceContext 在 common.proto 与 Java 类字段名一致（tenantId/userId/sessionId/taskId/subtaskId/traceId/spanId）✓
- TaskStatus 枚举名与 task.proto status 字段值一致（PENDING/PLANNING/RUNNING/.../TIMEOUT）✓
- ComplexityLevel.getLevel() 与 task.proto complexity 字段值一致（1=L1, 2=L2, 3=L3）✓
- RiskLevel.getLevel() 与 tool.proto risk_level 字段值一致（1=R1, 2=R2, 3=R3）✓
- ErrorCode getCode() 值与 doc 02-api §0.5 错误码表一致 ✓
- 所有 .proto 文件 package 命名规则一致：`agentplatform.<domain>.v1` ✓
- 所有 .proto java_package 命名规则一致：`agentplatform.<domain>.v1` ✓
