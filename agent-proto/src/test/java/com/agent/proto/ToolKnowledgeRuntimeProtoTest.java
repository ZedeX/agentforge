package com.agent.proto;

import agentplatform.tool.v1.*;
import agentplatform.knowledge.v1.*;
import agentplatform.agent_runtime.v1.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolKnowledgeRuntimeProtoTest {

    @Test
    void toolInvokeRequest_carriesRiskLevelAndPromptCacheKey() throws Exception {
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
    void toolRegistry_holdsRiskLevelAndExecutorType() throws Exception {
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
    void knowledgeQuery_carriesAclRolesAndTopK() throws Exception {
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
    void agentState_carriesCurrentStepAndTokenUsed() throws Exception {
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
    void startAgentRequest_carriesAllConfigFields() throws Exception {
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
