package com.agent.proto;

import agentplatform.planning.v1.*;
import agentplatform.memory.v1.*;
import agentplatform.model.v1.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningMemoryModelProtoTest {

    @Test
    @DisplayName("DagNode 应持有文档 schema 定义的全部字段")
    void should_HoldAllFieldsFromDocSchema_When_DagNodeSerialized() throws Exception {
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
        assertThat(parsed.getNodeId()).isEqualTo("n1");
        assertThat(parsed.getTitle()).isEqualTo("查询用户订单");
        assertThat(parsed.getAbilityTagsCount()).isEqualTo(2);
        assertThat(parsed.getAbilityTags(0)).isEqualTo("query");
        assertThat(parsed.getTimeoutMs()).isEqualTo(30000);
        assertThat(parsed.getDependsOnCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("PlanResponse 应携带 DAG JSON、版本号与告警列表")
    void should_CarryDagJsonAndVersion_When_PlanResponseSerialized() throws Exception {
        PlanResponse resp = PlanResponse.newBuilder()
                .setDagJson("{\"nodes\":[]}")
                .setDagVersion(1)
                .setSource("ai")
                .setTemplateId(0L)
                .addWarnings("节点 n3 没有依赖")
                .build();
        PlanResponse parsed = PlanResponse.parseFrom(resp.toByteArray());
        assertThat(parsed.getDagJson()).isEqualTo("{\"nodes\":[]}");
        assertThat(parsed.getDagVersion()).isEqualTo(1);
        assertThat(parsed.getSource()).isEqualTo("ai");
        assertThat(parsed.getWarningsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("RecalledMemory 应携带评分与来源类型")
    void should_CarryScoresAndSourceType_When_RecalledMemorySerialized() throws Exception {
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
        assertThat(parsed.getMemoryId()).isEqualTo("mem_001");
        assertThat(parsed.getSourceType()).isEqualTo("task");
        assertThat(parsed.getImportanceScore()).isEqualTo(0.85, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("ChatRequest 应支持含 enableCot 与 promptCache 的 ModelParams")
    void should_SupportModelParamsWithEnableCotAndPromptCache_When_ChatRequestSerialized() throws Exception {
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
        assertThat(parsed.getScene()).isEqualTo("planning");
        assertThat(parsed.getTier()).isEqualTo("strong");
        assertThat(parsed.getParams().getEnableCot()).isTrue();
        assertThat(parsed.getEnablePromptCache()).isTrue();
        assertThat(parsed.getParams().getTemperature()).isEqualTo(0.7, org.assertj.core.data.Offset.offset(0.001));
        assertThat(parsed.getMessagesCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("ChatChunk 的 oneof 字段可容纳 toolCall 或 finish 之一")
    void should_HoldEitherToolCallOrFinish_When_ChatChunkOneofSerialized() throws Exception {
        ChatChunk finishChunk = ChatChunk.newBuilder()
                .setCallId("call_xxx")
                .setDelta("")
                .setFinish(FinishReason.STOP)
                .build();
        ChatChunk parsed = ChatChunk.parseFrom(finishChunk.toByteArray());
        assertThat(parsed.getFinish()).isEqualTo(FinishReason.STOP);
        assertThat(parsed.getExtraCase()).isEqualTo(ChatChunk.ExtraCase.FINISH);
    }
}
