package com.agent.repo.integration;

import agentplatform.common.v1.TraceContext;
import agentplatform.repo.v1.AgentRepoGrpc;
import agentplatform.repo.v1.AgentResponse;
import agentplatform.repo.v1.CreateAgentRequest;
import agentplatform.repo.v1.GetAgentRequest;
import agentplatform.repo.v1.ListAgentsRequest;
import agentplatform.repo.v1.ListAgentsResponse;
import agentplatform.repo.v1.UpdateAgentRequest;
import com.agent.repo.api.impl.AgentLifecycleManagerImpl;
import com.agent.repo.api.impl.AgentQueryServiceImpl;
import com.agent.repo.api.impl.AgentRepositoryImpl;
import com.agent.repo.grpc.AgentMapper;
import com.agent.repo.grpc.AgentRepoGrpcService;
import com.agent.repo.grpc.GrpcExceptionAdvice;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 端到端集成测试（Plan 08 T6）：覆盖 6 个 E2E 场景。
 *
 * <p><b>基础设施（无 Docker / 无 JPA）：</b></p>
 * <ul>
 *   <li>本模块组件：真实 {@link AgentRepositoryImpl}（ConcurrentHashMap）+
 *       真实 {@link AgentQueryServiceImpl}（ConcurrentHashMap index）+
 *       真实 {@link AgentLifecycleManagerImpl}（ConcurrentHashMap statusMap）+
 *       真实 {@link AgentMapper} + 真实 {@link GrpcExceptionAdvice}</li>
 *   <li>gRPC：InProcess Server + Channel（grpc-testing），无需真实端口</li>
 *   <li>无 Spring 容器：手动 new 各组件，无 @SpringBootTest</li>
 * </ul>
 *
 * <p><b>注意：</b>{@code AgentRepoGrpcService.createAgent} 调用 {@code repo.save()} +
 * {@code lifecycleManager.register()}，但不调用 {@code queryService.index()}。
 * 因此 {@code listAgents} 场景需在测试中手动调用 {@code queryService.index()} 将 agent 注册到查询索引。</p>
 *
 * <p><b>覆盖场景：</b></p>
 * <ol>
 *   <li>E2E-1: CreateAgent → GetAgent → 验证字段一致</li>
 *   <li>E2E-2: CreateAgent 重复 name → gRPC ALREADY_EXISTS</li>
 *   <li>E2E-3: UpdateAgent → version 自增 + 字段更新</li>
 *   <li>E2E-4: UpdateAgent PUBLISHED 状态 → gRPC FAILED_PRECONDITION</li>
 *   <li>E2E-5: ListAgents 分页 + status 过滤</li>
 *   <li>E2E-6: GetAgent 不存在 → gRPC NOT_FOUND</li>
 * </ol>
 */
@DisplayName("AgentRepo 端到端集成测试（Plan 08 T6）")
class AgentRepoIntegrationTest {

    private static Server grpcServer;
    private static ManagedChannel channel;
    private static AgentRepoGrpc.AgentRepoBlockingStub stub;

    // 真实本模块组件（in-memory，无 JPA）
    private static AgentRepositoryImpl repository;
    private static AgentQueryServiceImpl queryService;
    private static AgentLifecycleManagerImpl lifecycleManager;

    @BeforeAll
    static void setUp() throws IOException {
        repository = new AgentRepositoryImpl();
        queryService = new AgentQueryServiceImpl();
        lifecycleManager = new AgentLifecycleManagerImpl();

        AgentMapper mapper = new AgentMapper();
        GrpcExceptionAdvice advice = new GrpcExceptionAdvice();

        AgentRepoGrpcService service = new AgentRepoGrpcService(
                repository, queryService, lifecycleManager, mapper, advice);

        String serverName = InProcessServerBuilder.generateName();
        grpcServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        stub = AgentRepoGrpc.newBlockingStub(channel);
    }

    @AfterAll
    static void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
    }

    @BeforeEach
    void clearState() {
        // Clear in-memory repository state between tests for isolation.
        // Note: lifecycleManager.clear() is package-private and cannot be called here;
        // each test uses unique agent IDs, so lifecycle state doesn't conflict.
        // queryService.index is only populated by explicit index() calls in E2E-5.
        repository.findAll().forEach(a -> repository.deleteById(a.getAgentId()));
    }

    // ===== E2E-1: CreateAgent → GetAgent =====

    @Test
    @DisplayName("E2E-1: CreateAgent → GetAgent 应返回完整字段")
    void should_CreateAndGetAgent_When_ValidRequest() {
        CreateAgentRequest createReq = CreateAgentRequest.newBuilder()
                .setAgentId("agent-e2e-001")
                .setName("代码审查助手")
                .setDescription("自动化代码审查 Agent")
                .setSystemPrompt("你是一个代码审查专家")
                .setAgentTier("standard")
                .setMaxSteps(15)
                .setMaxToken(8192)
                .addAbilityTags("code-review")
                .addAbilityTags("java")
                .addBoundTools("tool-static-analysis")
                .addBoundKnowledgeIds("kb-java-standards")
                .setTrace(TraceContext.newBuilder().setTaskId("trace-1").build())
                .build();

        AgentResponse createResp = stub.createAgent(createReq);
        assertThat(createResp.getAgentId()).isEqualTo("agent-e2e-001");
        assertThat(createResp.getName()).isEqualTo("代码审查助手");
        assertThat(createResp.getDescription()).isEqualTo("自动化代码审查 Agent");
        assertThat(createResp.getSystemPrompt()).isEqualTo("你是一个代码审查专家");
        assertThat(createResp.getAgentTier()).isEqualTo("standard");
        assertThat(createResp.getMaxSteps()).isEqualTo(15);
        assertThat(createResp.getMaxToken()).isEqualTo(8192);
        assertThat(createResp.getStatus()).isEqualTo("draft");
        assertThat(createResp.getVersion()).isEqualTo(1);
        assertThat(createResp.getAbilityTagsList()).containsExactly("code-review", "java");
        assertThat(createResp.getBoundToolsList()).containsExactly("tool-static-analysis");
        assertThat(createResp.getBoundKnowledgeIdsList()).containsExactly("kb-java-standards");
        assertThat(createResp.getCreatedAt()).isGreaterThan(0);
        assertThat(createResp.getUpdatedAt()).isGreaterThanOrEqualTo(createResp.getCreatedAt());

        // GetAgent should return the same data
        GetAgentRequest getReq = GetAgentRequest.newBuilder()
                .setAgentId("agent-e2e-001")
                .build();
        AgentResponse getResp = stub.getAgent(getReq);
        assertThat(getResp.getAgentId()).isEqualTo("agent-e2e-001");
        assertThat(getResp.getName()).isEqualTo("代码审查助手");
        assertThat(getResp.getVersion()).isEqualTo(1);
        assertThat(getResp.getStatus()).isEqualTo("draft");
    }

    // ===== E2E-2: CreateAgent duplicate name =====

    @Test
    @DisplayName("E2E-2: CreateAgent 重复 name 应返回 ALREADY_EXISTS")
    void should_ReturnAlreadyExists_When_DuplicateName() {
        CreateAgentRequest req1 = CreateAgentRequest.newBuilder()
                .setAgentId("agent-dup-001")
                .setName("唯一名称测试")
                .setAgentTier("standard")
                .build();
        stub.createAgent(req1);

        CreateAgentRequest req2 = CreateAgentRequest.newBuilder()
                .setAgentId("agent-dup-002")
                .setName("唯一名称测试")
                .setAgentTier("standard")
                .build();

        assertThatThrownBy(() -> stub.createAgent(req2))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
                });
    }

    // ===== E2E-3: UpdateAgent version increment =====

    @Test
    @DisplayName("E2E-3: UpdateAgent 应使 version 自增 + 字段更新")
    void should_IncrementVersion_When_UpdateAgent() {
        // Create agent first
        CreateAgentRequest createReq = CreateAgentRequest.newBuilder()
                .setAgentId("agent-update-001")
                .setName("更新测试 Agent")
                .setDescription("初始描述")
                .setAgentTier("standard")
                .setMaxSteps(10)
                .build();
        stub.createAgent(createReq);

        // Update agent
        UpdateAgentRequest updateReq = UpdateAgentRequest.newBuilder()
                .setAgentId("agent-update-001")
                .setName("更新后名称")
                .setDescription("更新后描述")
                .setAgentTier("advanced")
                .setMaxSteps(20)
                .setMaxToken(8192)
                .build();
        AgentResponse updateResp = stub.updateAgent(updateReq);

        assertThat(updateResp.getName()).isEqualTo("更新后名称");
        assertThat(updateResp.getDescription()).isEqualTo("更新后描述");
        assertThat(updateResp.getAgentTier()).isEqualTo("advanced");
        assertThat(updateResp.getMaxSteps()).isEqualTo(20);
        assertThat(updateResp.getVersion()).isEqualTo(2);

        // Verify via GetAgent
        AgentResponse getResp = stub.getAgent(GetAgentRequest.newBuilder()
                .setAgentId("agent-update-001").build());
        assertThat(getResp.getVersion()).isEqualTo(2);
        assertThat(getResp.getName()).isEqualTo("更新后名称");
    }

    // ===== E2E-4: UpdateAgent PUBLISHED → FAILED_PRECONDITION =====

    @Test
    @DisplayName("E2E-4: UpdateAgent PUBLISHED 状态应返回 FAILED_PRECONDITION")
    void should_ReturnFailedPrecondition_When_UpdatePublishedAgent() {
        // Create agent
        CreateAgentRequest createReq = CreateAgentRequest.newBuilder()
                .setAgentId("agent-published-001")
                .setName("已发布 Agent")
                .setAgentTier("standard")
                .build();
        stub.createAgent(createReq);

        // Manually transition to PUBLISHED via lifecycleManager
        lifecycleManager.transition("agent-published-001",
                com.agent.repo.enums.AgentStatus.PUBLISHED);
        // Also update the entity's status in repository
        repository.findById("agent-published-001").ifPresent(a -> {
            a.setStatus(com.agent.repo.enums.AgentStatus.PUBLISHED);
            repository.save(a);
        });

        // Attempt update should fail
        UpdateAgentRequest updateReq = UpdateAgentRequest.newBuilder()
                .setAgentId("agent-published-001")
                .setName("试图修改已发布")
                .setAgentTier("standard")
                .build();

        assertThatThrownBy(() -> stub.updateAgent(updateReq))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
                });
    }

    // ===== E2E-5: ListAgents pagination + status filter =====

    @Test
    @DisplayName("E2E-5: ListAgents 应支持分页 + status 过滤")
    void should_ReturnPaginatedResults_When_ListAgents() {
        // Create 5 agents and manually index them (createAgent doesn't auto-index)
        for (int i = 1; i <= 5; i++) {
            CreateAgentRequest req = CreateAgentRequest.newBuilder()
                    .setAgentId("agent-list-" + String.format("%03d", i))
                    .setName("列表 Agent " + i)
                    .setAgentTier("standard")
                    .build();
            AgentResponse resp = stub.createAgent(req);
            // Manually index for query service (createAgent doesn't call queryService.index())
            repository.findById(resp.getAgentId()).ifPresent(queryService::index);
        }

        // List page 0, size 3
        ListAgentsRequest listReq = ListAgentsRequest.newBuilder()
                .setPage(0)
                .setSize(3)
                .build();
        ListAgentsResponse listResp = stub.listAgents(listReq);

        assertThat(listResp.getTotal()).isEqualTo(5);
        assertThat(listResp.getPage()).isEqualTo(0);
        assertThat(listResp.getSize()).isEqualTo(3);
        assertThat(listResp.getItemsCount()).isEqualTo(3);

        // List page 1, size 3
        ListAgentsRequest listReq2 = ListAgentsRequest.newBuilder()
                .setPage(1)
                .setSize(3)
                .build();
        ListAgentsResponse listResp2 = stub.listAgents(listReq2);
        assertThat(listResp2.getItemsCount()).isEqualTo(2);

        // Filter by status = draft
        ListAgentsRequest filterReq = ListAgentsRequest.newBuilder()
                .setStatus("draft")
                .setPage(0)
                .setSize(10)
                .build();
        ListAgentsResponse filterResp = stub.listAgents(filterReq);
        assertThat(filterResp.getTotal()).isEqualTo(5);
        for (AgentResponse item : filterResp.getItemsList()) {
            assertThat(item.getStatus()).isEqualTo("draft");
        }
    }

    // ===== E2E-6: GetAgent not found =====

    @Test
    @DisplayName("E2E-6: GetAgent 不存在应返回 NOT_FOUND")
    void should_ReturnNotFound_When_AgentNotExists() {
        GetAgentRequest req = GetAgentRequest.newBuilder()
                .setAgentId("non-existent-agent")
                .build();

        assertThatThrownBy(() -> stub.getAgent(req))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                });
    }
}
