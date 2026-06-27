package com.agent.proto;

import agentplatform.task.v1.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskProtoTest {

    @Test
    void taskInstance_roundTripAllDatabaseFields() throws Exception {
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
    void submitTaskResponse_carriesTaskIdAndStatus() throws Exception {
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
    void subtaskResult_carriesAllRequiredFields() throws Exception {
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
