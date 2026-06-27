package com.agent.proto;

import agentplatform.planning.v1.*;
import agentplatform.memory.v1.*;
import agentplatform.model.v1.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanningMemoryModelProtoTest {

    @Test
    void dagNode_holdsAllFieldsFromDocSchema() throws Exception {
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
    void planResponse_carriesDagJsonAndVersion() throws Exception {
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
    void recalledMemory_carriesScoresAndSourceType() throws Exception {
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
    void chatRequest_supportsModelParamsWithEnableCotAndPromptCache() throws Exception {
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
    void chatChunk_oneofCanHoldEitherToolCallOrFinish() throws Exception {
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
