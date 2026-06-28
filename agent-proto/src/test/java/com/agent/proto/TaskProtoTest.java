package com.agent.proto;

import agentplatform.task.v1.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskProtoTest {

    @Test
    @DisplayName("TaskInstance 序列化后所有数据库相关字段应保持原值")
    void should_RoundTripAllFields_When_TaskInstanceSerialized() throws Exception {
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
        assertThat(parsed.getTaskId()).isEqualTo("tk_yyy");
        assertThat(parsed.getTenantId()).isEqualTo(1001L);
        assertThat(parsed.getStatus()).isEqualTo("PENDING");
        assertThat(parsed.getComplexity()).isEqualTo(3);
        assertThat(parsed.getCostLimitCent()).isEqualTo(5000L);
        assertThat(parsed.getCreatedAt()).isEqualTo(1719405600000L);
    }

    @Test
    @DisplayName("SubmitTaskResponse 应携带 taskId 与 status")
    void should_CarryTaskIdAndStatus_When_SubmitTaskResponseSerialized() throws Exception {
        SubmitTaskResponse resp = SubmitTaskResponse.newBuilder()
                .setTaskId("tk_yyy")
                .setStatus("PENDING")
                .setComplexity(0)
                .setSubmittedAt(1719405600000L)
                .build();
        SubmitTaskResponse parsed = SubmitTaskResponse.parseFrom(resp.toByteArray());
        assertThat(parsed.getTaskId()).isEqualTo("tk_yyy");
        assertThat(parsed.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("SubtaskResult 应携带所有必填字段")
    void should_CarryAllRequiredFields_When_SubtaskResultSerialized() throws Exception {
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
        assertThat(parsed.getSubtaskId()).isEqualTo("st_001");
        assertThat(parsed.getStatus()).isEqualTo("success");
        assertThat(parsed.getTokenUsed()).isEqualTo(1520);
    }
}
