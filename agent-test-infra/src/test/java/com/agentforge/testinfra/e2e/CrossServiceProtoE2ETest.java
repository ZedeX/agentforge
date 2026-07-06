package com.agentforge.testinfra.e2e;

import agentplatform.common.v1.TraceContext;
import agentplatform.repo.v1.AgentRepoGrpc;
import agentplatform.repo.v1.AgentResponse;
import agentplatform.repo.v1.CreateAgentRequest;
import agentplatform.repo.v1.GetAgentRequest;
import agentplatform.repo.v1.ListAgentsRequest;
import agentplatform.repo.v1.ListAgentsResponse;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cross-service e2e contract test: agent-gateway → agent-repo gRPC call chain.
 *
 * <p>Validates proto message serialization round-trip + TraceContext propagation
 * across service boundaries using in-process gRPC (no Docker, no network).
 * This is a "protocol contract" test — it ensures the proto schema in
 * {@code agent-proto/src/main/proto/repo.proto} can be marshaled by a client
 * and unmarshaled by a server without field loss.</p>
 *
 * <p>Run via: {@code mvn -Pe2e-perf -pl agent-test-infra test -Dtest=CrossServiceProtoE2ETest}</p>
 *
 * <p>Local-safe: no Docker dependency. Server/channel lifecycle managed manually in
 * {@link #setUp()} / {@link #tearDown()} via {@code shutdownNow()} for deterministic cleanup.</p>
 */
class CrossServiceProtoE2ETest {

    private ManagedChannel channel;
    private io.grpc.Server rawServer;
    private MockAgentRepoServer server;

    @BeforeEach
    void setUp() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = new MockAgentRepoServer();
        rawServer = InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(server)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    }

    @AfterEach
    void tearDown() {
        if (rawServer != null) rawServer.shutdownNow();
        if (channel != null) channel.shutdownNow();
    }

    @Test
    @DisplayName("CreateAgent RPC 应返回完整字段且 TraceContext 在 client→server 间正确透传")
    void should_PropagateTraceContext_When_CreateAgentCalled() {
        TraceContext trace = TraceContext.newBuilder()
                .setTenantId(1001L)
                .setUserId("u_001")
                .setSessionId("ss_001")
                .setTaskId("task_001")
                .setTraceId("trace_abc_001")
                .setSpanId("span_001")
                .build();

        CreateAgentRequest request = CreateAgentRequest.newBuilder()
                .setAgentId("agt_e2e_001")
                .setName("Industry Research Agent")
                .setDescription("E2E test agent")
                .addAbilityTags("research")
                .addAbilityTags("analysis")
                .setSystemPrompt("You are a senior analyst")
                .setAgentTier("ADVANCED")
                .setMaxSteps(20)
                .setMaxToken(8192)
                .addBoundTools("web_search")
                .addBoundKnowledgeIds("kb_001")
                .setTrace(trace)
                .build();

        AgentResponse response = AgentRepoGrpc.newBlockingStub(channel).createAgent(request);

        assertThat(response.getAgentId()).isEqualTo("agt_e2e_001");
        assertThat(response.getName()).isEqualTo("Industry Research Agent");
        assertThat(response.getAgentTier()).isEqualTo("ADVANCED");
        assertThat(response.getStatus()).isEqualTo("DRAFT");
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getAbilityTagsList()).containsExactly("research", "analysis");
        assertThat(response.getBoundToolsList()).containsExactly("web_search");
        assertThat(response.getBoundKnowledgeIdsList()).containsExactly("kb_001");
        assertThat(response.getCreatedAt()).isGreaterThan(0);

        // Server-side capture: verify trace context was propagated
        TraceContext captured = server.lastTrace.get();
        assertThat(captured).isNotNull();
        assertThat(captured.getTenantId()).isEqualTo(1001L);
        assertThat(captured.getUserId()).isEqualTo("u_001");
        assertThat(captured.getTraceId()).isEqualTo("trace_abc_001");
        assertThat(captured.getSpanId()).isEqualTo("span_001");
    }

    @Test
    @DisplayName("GetAgent RPC 对不存在的 agentId 应返回 NOT_FOUND gRPC status")
    void should_ReturnNotFoundStatus_When_AgentIdDoesNotExist() {
        GetAgentRequest request = GetAgentRequest.newBuilder()
                .setAgentId("agt_not_exist_999")
                .build();

        assertThatThrownBy(() -> AgentRepoGrpc.newBlockingStub(channel).getAgent(request))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("NOT_FOUND")
                .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    @DisplayName("CreateAgent 后 GetAgent 应返回相同字段，验证 proto round-trip 一致性")
    void should_RoundTripAgent_When_CreateThenGet() {
        CreateAgentRequest create = CreateAgentRequest.newBuilder()
                .setAgentId("agt_roundtrip_001")
                .setName("Round Trip Agent")
                .setDescription("proto round-trip validation")
                .setSystemPrompt("prompt")
                .setAgentTier("STANDARD")
                .build();

        AgentRepoGrpc.AgentRepoBlockingStub stub = AgentRepoGrpc.newBlockingStub(channel);
        AgentResponse created = stub.createAgent(create);

        GetAgentRequest get = GetAgentRequest.newBuilder()
                .setAgentId("agt_roundtrip_001")
                .build();
        AgentResponse fetched = stub.getAgent(get);

        assertThat(fetched.getAgentId()).isEqualTo(created.getAgentId());
        assertThat(fetched.getName()).isEqualTo(created.getName());
        assertThat(fetched.getDescription()).isEqualTo(created.getDescription());
        assertThat(fetched.getSystemPrompt()).isEqualTo(created.getSystemPrompt());
        assertThat(fetched.getAgentTier()).isEqualTo(created.getAgentTier());
        assertThat(fetched.getStatus()).isEqualTo(created.getStatus());
        assertThat(fetched.getVersion()).isEqualTo(created.getVersion());
        assertThat(fetched.getCreatedAt()).isEqualTo(created.getCreatedAt());
    }

    @Test
    @DisplayName("ListAgents RPC 应返回所有已创建的 Agent 并正确分页")
    void should_ListAgents_When_MultipleAgentsCreated() {
        AgentRepoGrpc.AgentRepoBlockingStub stub = AgentRepoGrpc.newBlockingStub(channel);
        for (int i = 1; i <= 3; i++) {
            stub.createAgent(CreateAgentRequest.newBuilder()
                    .setAgentId("agt_list_" + i)
                    .setName("Agent " + i)
                    .setDescription("desc")
                    .setSystemPrompt("prompt")
                    .build());
        }

        ListAgentsResponse response = stub.listAgents(ListAgentsRequest.newBuilder()
                .setPage(0)
                .setSize(10)
                .build());

        assertThat(response.getItemsCount()).isEqualTo(3);
        assertThat(response.getTotal()).isEqualTo(3L);
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getItemsList()).extracting(AgentResponse::getAgentId)
                .containsExactlyInAnyOrder("agt_list_1", "agt_list_2", "agt_list_3");
    }

    /**
     * Mock AgentRepo server implementation — stores agents in a ConcurrentHashMap,
     * captures the last received TraceContext for assertion.
     */
    private static class MockAgentRepoServer extends AgentRepoGrpc.AgentRepoImplBase {
        private final ConcurrentHashMap<String, AgentResponse> store = new ConcurrentHashMap<>();
        final AtomicReference<TraceContext> lastTrace = new AtomicReference<>();

        @Override
        public void createAgent(CreateAgentRequest request, StreamObserver<AgentResponse> responseObserver) {
            if (request.hasTrace()) {
                lastTrace.set(request.getTrace());
            }
            if (store.containsKey(request.getAgentId())) {
                responseObserver.onError(Status.ALREADY_EXISTS
                        .withDescription("Agent already exists: " + request.getAgentId())
                        .asRuntimeException());
                return;
            }
            long now = System.currentTimeMillis();
            AgentResponse response = AgentResponse.newBuilder()
                    .setAgentId(request.getAgentId())
                    .setName(request.getName())
                    .setDescription(request.getDescription())
                    .addAllAbilityTags(request.getAbilityTagsList())
                    .setSystemPrompt(request.getSystemPrompt())
                    .setAgentTier(request.getAgentTier().isEmpty() ? "STANDARD" : request.getAgentTier())
                    .setMaxSteps(request.getMaxSteps() == 0 ? 10 : request.getMaxSteps())
                    .setMaxToken(request.getMaxToken() == 0 ? 4096 : request.getMaxToken())
                    .setStatus("DRAFT")
                    .setVersion(1)
                    .addAllBoundTools(request.getBoundToolsList())
                    .addAllBoundKnowledgeIds(request.getBoundKnowledgeIdsList())
                    .setCreatedAt(now)
                    .setUpdatedAt(now)
                    .build();
            store.put(request.getAgentId(), response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void getAgent(GetAgentRequest request, StreamObserver<AgentResponse> responseObserver) {
            AgentResponse response = store.get(request.getAgentId());
            if (response == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Agent not found: " + request.getAgentId())
                        .asRuntimeException());
                return;
            }
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void updateAgent(agentplatform.repo.v1.UpdateAgentRequest request,
                                StreamObserver<AgentResponse> responseObserver) {
            AgentResponse existing = store.get(request.getAgentId());
            if (existing == null) {
                responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
                return;
            }
            AgentResponse updated = existing.toBuilder()
                    .setName(request.getName())
                    .setDescription(request.getDescription())
                    .setVersion(existing.getVersion() + 1)
                    .setUpdatedAt(System.currentTimeMillis())
                    .build();
            store.put(request.getAgentId(), updated);
            responseObserver.onNext(updated);
            responseObserver.onCompleted();
        }

        @Override
        public void listAgents(ListAgentsRequest request, StreamObserver<ListAgentsResponse> responseObserver) {
            List<AgentResponse> all = new ArrayList<>(store.values());
            int page = request.getPage();
            int size = request.getSize() == 0 ? 10 : request.getSize();
            int from = Math.min(page * size, all.size());
            int to = Math.min(from + size, all.size());
            List<AgentResponse> pageItems = all.subList(from, to);

            responseObserver.onNext(ListAgentsResponse.newBuilder()
                    .addAllItems(pageItems)
                    .setTotal(all.size())
                    .setPage(page)
                    .setSize(size)
                    .build());
            responseObserver.onCompleted();
        }
    }
}
