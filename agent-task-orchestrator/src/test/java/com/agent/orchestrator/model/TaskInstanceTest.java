package com.agent.orchestrator.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void builder_shouldSetAllBusinessFields() {
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

        assertEquals(1L, task.getId());
        assertEquals("tk_001", task.getTaskId());
        assertEquals(1001L, task.getTenantId());
        assertEquals("ss_abc", task.getSessionId());
        assertEquals("u_001", task.getUserId());
        assertEquals("生成周报", task.getTitle());
        assertEquals("汇总本周销售数据生成周报并发送", task.getGoal());
        assertEquals(3, task.getComplexity());
        assertEquals("RUNNING", task.getStatus());
        assertEquals("{\"overallGoal\":\"周报\"}", task.getTaskSchema());
        assertEquals(10086L, task.getDagId());
        assertEquals(2001L, task.getAgentId());
        assertEquals(5, task.getPriority());
        assertNull(task.getParentTaskId());
        assertEquals(0, task.getReplanCount());
        assertEquals(10000L, task.getCostLimitCent());
        assertEquals(1500L, task.getCostUsedCent());
        assertEquals(8000, task.getTokenUsed());
        assertEquals(started, task.getStartedAt());
        assertEquals(finished, task.getFinishedAt());
        assertNull(task.getErrorCode());
        assertNull(task.getErrorMsg());
        assertEquals("周报已生成", task.getResultSummary());
    }

    @Test
    void builder_shouldGenerateToStringContainingFieldName() {
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_toString")
                .status("PENDING")
                .build();

        String str = task.toString();
        // Lombok @Data 生成的 toString 包含字段名=值 格式
        assertNotNull(str);
        assertTrue(str.contains("taskId=tk_toString"), "toString should contain taskId field");
        assertTrue(str.contains("status=PENDING"), "toString should contain status field");
    }

    @Test
    void data_shouldImplementEqualsAndHashCodeByAllFields() {
        TaskInstance t1 = TaskInstance.builder().taskId("tk_a").status("PENDING").build();
        TaskInstance t2 = TaskInstance.builder().taskId("tk_a").status("PENDING").build();
        TaskInstance t3 = TaskInstance.builder().taskId("tk_a").status("RUNNING").build();

        // Lombok @Data 默认基于所有非静态字段生成 equals/hashCode
        assertEquals(t1, t2, "同值实例应相等");
        assertEquals(t1.hashCode(), t2.hashCode(), "同值实例 hashCode 应相等");
        assertFalse(t1.equals(t3), "status 不同应不等");
    }

    @Test
    void noArgsConstructor_shouldCreateInstanceWithNullFields() {
        TaskInstance task = new TaskInstance();
        // @NoArgsConstructor 创建实例后所有引用类型字段为 null，基本类型为默认值
        assertNull(task.getTaskId());
        assertNull(task.getStatus());
        assertEquals(0, task.getReplanCount()); // int 默认 0
    }

    @Test
    void allArgsConstructor_shouldSetAllFields() {
        Instant now = Instant.now();
        TaskInstance task = new TaskInstance(
                99L, "tk_ctor", 1001L, null, "u_001",
                "title", "goal", 2, "PLANNING", "{}",
                null, null, 5, null, 0, 0L, 0L, 0,
                null, null, null, null, null);

        assertEquals(99L, task.getId());
        assertEquals("tk_ctor", task.getTaskId());
        assertEquals("PLANNING", task.getStatus());
        assertEquals(2, task.getComplexity());
        assertEquals(5, task.getPriority());
        assertEquals(0, task.getReplanCount());
        assertEquals(0L, task.getCostUsedCent());
    }

    @Test
    void prePersist_shouldInitializeDefaultValuesForCostAndCounters() {
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

        assertEquals(5, task.getPriority(), "priority 默认应为 5");
        assertEquals(0, task.getReplanCount(), "replan_count 默认应为 0");
        assertEquals(0L, task.getCostUsedCent(), "cost_used_cent 默认应为 0");
        assertEquals(0, task.getTokenUsed(), "token_used 默认应为 0");
        assertNotNull(task.getCreatedAt(), "createdAt 应被 PrePersist 填充");
        assertNotNull(task.getUpdatedAt(), "updatedAt 应被 PrePersist 填充");
    }
}
