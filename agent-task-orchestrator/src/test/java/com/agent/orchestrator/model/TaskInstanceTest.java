package com.agent.orchestrator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TaskInstance 实体单元测试（Red 阶段：实现尚未存在，预期编译失败）。
 *
 * <p>对齐 doc 01-database §2.1 task_instance 表 23 业务字段：
 * id / task_id / tenant_id / session_id / user_id / title / goal /
 * complexity / status / task_schema / dag_id / agent_id / priority /
 * parent_task_id / replan_count / cost_limit_cent / cost_used_cent /
 * token_used / started_at / finished_at / error_code / error_msg /
 * result_summary。</p>
 *
 * <p>风格参考 agent-session SessionTest.java：使用 JUnit 5 Assertions。</p>
 */
class TaskInstanceTest {

    @Test
    @DisplayName("通过 Builder 构建时应正确设置所有业务字段")
    void should_SetAllBusinessFields_When_UsingBuilder() {
        Instant started = Instant.parse("2026-06-28T10:00:00Z");
        Instant finished = Instant.parse("2026-06-28T10:05:00Z");

        TaskInstance task = TaskInstance.builder()
                .id(1L)
                .taskId("tk_001")
                .tenantId(1001L)
                .sessionId("ss_abc")
                .userId("u_001")
                .title("生成周报")
                .goal("汇总本周销售数据生成周报并发送")
                .complexity(3)
                .status("RUNNING")
                .taskSchema("{\"overallGoal\":\"周报\"}")
                .dagId(10086L)
                .agentId(2001L)
                .priority(5)
                .parentTaskId(null)
                .replanCount(0)
                .costLimitCent(10000L)
                .costUsedCent(1500L)
                .tokenUsed(8000)
                .startedAt(started)
                .finishedAt(finished)
                .errorCode(null)
                .errorMsg(null)
                .resultSummary("周报已生成")
                .build();

        assertThat(task.getId()).isEqualTo(1L);
        assertThat(task.getTaskId()).isEqualTo("tk_001");
        assertThat(task.getTenantId()).isEqualTo(1001L);
        assertThat(task.getSessionId()).isEqualTo("ss_abc");
        assertThat(task.getUserId()).isEqualTo("u_001");
        assertThat(task.getTitle()).isEqualTo("生成周报");
        assertThat(task.getGoal()).isEqualTo("汇总本周销售数据生成周报并发送");
        assertThat(task.getComplexity()).isEqualTo(3);
        assertThat(task.getStatus()).isEqualTo("RUNNING");
        assertThat(task.getTaskSchema()).isEqualTo("{\"overallGoal\":\"周报\"}");
        assertThat(task.getDagId()).isEqualTo(10086L);
        assertThat(task.getAgentId()).isEqualTo(2001L);
        assertThat(task.getPriority()).isEqualTo(5);
        assertThat(task.getParentTaskId()).isNull();
        assertThat(task.getReplanCount()).isEqualTo(0);
        assertThat(task.getCostLimitCent()).isEqualTo(10000L);
        assertThat(task.getCostUsedCent()).isEqualTo(1500L);
        assertThat(task.getTokenUsed()).isEqualTo(8000);
        assertThat(task.getStartedAt()).isEqualTo(started);
        assertThat(task.getFinishedAt()).isEqualTo(finished);
        assertThat(task.getErrorCode()).isNull();
        assertThat(task.getErrorMsg()).isNull();
        assertThat(task.getResultSummary()).isEqualTo("周报已生成");
    }

    @Test
    @DisplayName("Builder 生成的 toString 应包含字段名")
    void should_GenerateToStringContainingFieldName_When_UsingBuilder() {
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_toString")
                .status("PENDING")
                .build();

        String str = task.toString();
        // Lombok @Data 生成的 toString 包含字段名=值 格式
        assertThat(str).isNotNull();
        assertThat(str.contains("taskId=tk_toString")).as("toString should contain taskId field").isTrue();
        assertThat(str.contains("status=PENDING")).as("toString should contain status field").isTrue();
    }

    @Test
    @DisplayName("equals/hashCode 应基于所有字段实现，同值实例相等，不同值实例不等")
    void should_ImplementEqualsAndHashCodeByAllFields_When_InstancesHaveSameOrDifferentValues() {
        TaskInstance t1 = TaskInstance.builder().taskId("tk_a").status("PENDING").build();
        TaskInstance t2 = TaskInstance.builder().taskId("tk_a").status("PENDING").build();
        TaskInstance t3 = TaskInstance.builder().taskId("tk_a").status("RUNNING").build();

        // Lombok @Data 默认基于所有非静态字段生成 equals/hashCode
        assertThat(t1).as("同值实例应相等").isEqualTo(t2);
        assertThat(t1.hashCode()).as("同值实例 hashCode 应相等").isEqualTo(t2.hashCode());
        assertThat(t1.equals(t3)).as("status 不同应不等").isFalse();
    }

    @Test
    @DisplayName("无参构造函数创建实例时引用类型字段应为 null，基本类型字段为默认值")
    void should_CreateInstanceWithNullFields_When_UsingNoArgsConstructor() {
        TaskInstance task = new TaskInstance();
        // @NoArgsConstructor 创建实例后所有引用类型字段为 null，基本类型为默认值
        assertThat(task.getTaskId()).isNull();
        assertThat(task.getStatus()).isNull();
        assertThat(task.getReplanCount()).isEqualTo(0); // int 默认 0
    }

    @Test
    @DisplayName("全参构造函数应正确设置所有字段")
    void should_SetAllFields_When_UsingAllArgsConstructor() {
        Instant now = Instant.now();
        TaskInstance task = new TaskInstance(
                99L, "tk_ctor", 1001L, null, "u_001",
                "title", "goal", 2, "PLANNING", "{}",
                null, null, 5, null, 0, 0L, 0L, 0,
                null, null, null, null, null);

        assertThat(task.getId()).isEqualTo(99L);
        assertThat(task.getTaskId()).isEqualTo("tk_ctor");
        assertThat(task.getStatus()).isEqualTo("PLANNING");
        assertThat(task.getComplexity()).isEqualTo(2);
        assertThat(task.getPriority()).isEqualTo(5);
        assertThat(task.getReplanCount()).isEqualTo(0);
        assertThat(task.getCostUsedCent()).isEqualTo(0L);
    }

    @Test
    @DisplayName("PrePersist 应初始化 priority/replan_count/cost_used_cent/token_used 等默认值并填充时间戳")
    void should_InitializeDefaultValuesForCostAndCounters_When_PrePersistIsCalled() {
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_default")
                .tenantId(1001L)
                .userId("u_001")
                .title("默认值测试")
                .goal("goal")
                .complexity(1)
                .status("PENDING")
                .taskSchema("{}")
                .costLimitCent(5000L)
                .build();
        // PrePersist 应填充 priority / replan_count / cost_used_cent / token_used 默认值
        task.prePersist();

        assertThat(task.getPriority()).as("priority 默认应为 5").isEqualTo(5);
        assertThat(task.getReplanCount()).as("replan_count 默认应为 0").isEqualTo(0);
        assertThat(task.getCostUsedCent()).as("cost_used_cent 默认应为 0").isEqualTo(0L);
        assertThat(task.getTokenUsed()).as("token_used 默认应为 0").isEqualTo(0);
        assertThat(task.getCreatedAt()).as("createdAt 应被 PrePersist 填充").isNotNull();
        assertThat(task.getUpdatedAt()).as("updatedAt 应被 PrePersist 填充").isNotNull();
    }
}
