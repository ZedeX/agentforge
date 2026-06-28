package com.agent.proto;

import agentplatform.tool.v1.*;
import agentplatform.knowledge.v1.*;
import agentplatform.agent_runtime.v1.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolKnowledgeRuntimeProtoTest {

    @Test
    @DisplayName("ToolInvokeRequest 应携带 riskLevel 与 promptCacheKey")
    void should_CarryRiskLevelAndPromptCacheKey_When_ToolInvokeRequestSerialized() throws Exception {
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
        assertThat(parsed.getCallId()).isEqualTo("call_xxx");
        assertThat(parsed.getRiskLevel()).isEqualTo(1);
        assertThat(parsed.getPromptCacheKey()).isEqualTo("cache:tool:tl_query_order:u_123");
    }

    @Test
    @DisplayName("ToolRegistry 应持有 riskLevel 与 executorType")
    void should_HoldRiskLevelAndExecutorType_When_ToolRegistrySerialized() throws Exception {
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
        assertThat(parsed.getName()).isEqualTo("query_order");
        assertThat(parsed.getRiskLevel()).isEqualTo(1);
        assertThat(parsed.getExecutorType()).isEqualTo("proxy");
        assertThat(parsed.getTimeoutMs()).isEqualTo(5000);
    }

    @Test
    @DisplayName("KnowledgeQuery 应携带 ACL roles 与 topK")
    void should_CarryAclRolesAndTopK_When_KnowledgeQuerySerialized() throws Exception {
        KnowledgeQuery q = KnowledgeQuery.newBuilder()
                .setKbId("kb_001")
                .setQuery("退货政策是什么")
                .setTopK(5)
                .setUserId("u_123")
                .addRoles("cs_agent")
                .build();
        KnowledgeQuery parsed = KnowledgeQuery.parseFrom(q.toByteArray());
        assertThat(parsed.getKbId()).isEqualTo("kb_001");
        assertThat(parsed.getTopK()).isEqualTo(5);
        assertThat(parsed.getRolesCount()).isEqualTo(1);
        assertThat(parsed.getRoles(0)).isEqualTo("cs_agent");
    }

    @Test
    @DisplayName("AgentState 应携带 currentStep 与 tokenUsed")
    void should_CarryCurrentStepAndTokenUsed_When_AgentStateSerialized() throws Exception {
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
        assertThat(parsed.getCurrentStep()).isEqualTo(3);
        assertThat(parsed.getCurrentThink()).isEqualTo("需要先查询用户订单");
        assertThat(parsed.getTokenUsed()).isEqualTo(8500);
        assertThat(parsed.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("StartAgentRequest 应携带全部配置字段")
    void should_CarryAllConfigFields_When_StartAgentRequestSerialized() throws Exception {
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
        assertThat(parsed.getSubtaskId()).isEqualTo("st_001");
        assertThat(parsed.getAgentId()).isEqualTo(1001L);
        assertThat(parsed.getMaxSteps()).isEqualTo(10);
        assertThat(parsed.getCostBudgetCent()).isEqualTo(5000L);
    }
}
