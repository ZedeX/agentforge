package com.agent.repo.grpc;

import agentplatform.repo.v1.AgentResponse;
import agentplatform.repo.v1.CreateAgentRequest;
import agentplatform.repo.v1.GetAgentRequest;
import agentplatform.repo.v1.ListAgentsRequest;
import agentplatform.repo.v1.ListAgentsResponse;
import agentplatform.repo.v1.UpdateAgentRequest;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.repo.api.AgentLifecycleManager;
import com.agent.repo.api.AgentQueryService;
import com.agent.repo.api.AgentRepository;
import com.agent.repo.enums.AgentStatus;
import com.agent.repo.enums.AgentTier;
import com.agent.repo.model.AgentDefinition;
import com.agent.repo.model.PageResult;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentRepoGrpcService} 单测（Plan 08 T4，覆盖 4 RPC 正常流 + 异常流）。
 *
 * <p>纯单测：mock {@link AgentRepository} / {@link AgentQueryService} /
 * {@link AgentLifecycleManager}，使用真实 {@link AgentMapper} +
 * 真实 {@link GrpcExceptionAdvice}，用 capturing StreamObserver 捕获 onNext/onError。</p>
 *
 * <p>验证场景：</p>
 * <ul>
 *   <li>CreateAgent 正常流 → onNext + onCompleted，验证 agentId/name/agentTier</li>
 *   <li>CreateAgent 重复 name（existsByName=true）→ onError ALREADY_EXISTS</li>
 *   <li>GetAgent 正常流 → 返回完整字段</li>
 *   <li>GetAgent 不存在（findById 返回 empty）→ onError NOT_FOUND</li>
 *   <li>UpdateAgent 正常流 → version 自增</li>
 *   <li>UpdateAgent PUBLISHED 状态 → onError FAILED_PRECONDITION</li>
 *   <li>ListAgents 分页 → 返回 items + total</li>
 * </ul>
 */
@DisplayName("AgentRepoGrpcService gRPC 服务（Plan 08 T4）")
class AgentRepoGrpcServiceTest {

    private AgentRepository repo;
    private AgentQueryService queryService;
    private AgentLifecycleManager lifecycleManager;
    private AgentRepoGrpcService grpcService;

    @BeforeEach
    void setUp() {
        repo = mock(AgentRepository.class);
        queryService = mock(AgentQueryService.class);
        lifecycleManager = mock(AgentLifecycleManager.class);
        AgentMapper mapper = new AgentMapper();
        GrpcExceptionAdvice advice = new GrpcExceptionAdvice();
        grpcService = new AgentRepoGrpcService(repo, queryService, lifecycleManager, mapper, advice);
    }

    // ===== RPC 1: CreateAgent =====

    @Test
    @DisplayName("Should_CreateAgent_When_NameUnique: 正常创建 → onNext + onCompleted，验证 agentId/name/agentTier")
    void should_CreateAgent_When_NameUnique() {
        // given
        CreateAgentRequest req = CreateAgentRequest.newBuilder()
                .setAgentId("agent-001")
                .setName("Code Reviewer")
                .setDescription("代码审查 Agent")
                .addAbilityTags("code_review")
                .addAbilityTags("qa")
                .setSystemPrompt("You are a code reviewer.")
                .setAgentTier("advanced")
                .setMaxSteps(20)
                .setMaxToken(8192)
                .addBoundTools("tool-search")
                .addBoundKnowledgeIds("kb-001")
                .build();
        when(repo.existsByName("Code Reviewer")).thenReturn(false);
        // 模拟 save 返回带 id/timestamps 的 entity
        when(repo.save(any(AgentDefinition.class))).thenAnswer(invocation -> {
            AgentDefinition e = invocation.getArgument(0);
            AgentDefinition saved = new AgentDefinition();
            saved.setId(1L);
            saved.setAgentId(e.getAgentId());
            saved.setName(e.getName());
            saved.setDescription(e.getDescription());
            saved.setAbilityTags(e.getAbilityTags());
            saved.setSystemPrompt(e.getSystemPrompt());
            saved.setAgentTier(e.getAgentTier());
            saved.setMaxSteps(e.getMaxSteps());
            saved.setMaxToken(e.getMaxToken());
            saved.setStatus(AgentStatus.DRAFT);
            saved.setVersion(1);
            saved.setBoundTools(e.getBoundTools());
            saved.setBoundKnowledgeIds(e.getBoundKnowledgeIds());
            saved.setCreatedAt(System.currentTimeMillis());
            saved.setUpdatedAt(System.currentTimeMillis());
            return saved;
        });

        // when
        CapturingObserver<AgentResponse> obs = new CapturingObserver<>();
        grpcService.createAgent(req, obs);

        // then
        assertThat(obs.completed).isTrue();
        assertThat(obs.value).isNotNull();
        assertThat(obs.value.getAgentId()).isEqualTo("agent-001");
        assertThat(obs.value.getName()).isEqualTo("Code Reviewer");
        assertThat(obs.value.getDescription()).isEqualTo("代码审查 Agent");
        assertThat(obs.value.getAbilityTagsList()).containsExactly("code_review", "qa");
        assertThat(obs.value.getSystemPrompt()).isEqualTo("You are a code reviewer.");
        assertThat(obs.value.getAgentTier()).isEqualTo("advanced");
        assertThat(obs.value.getMaxSteps()).isEqualTo(20);
        assertThat(obs.value.getMaxToken()).isEqualTo(8192);
        assertThat(obs.value.getStatus()).isEqualTo("draft");
        assertThat(obs.value.getVersion()).isEqualTo(1);
        assertThat(obs.value.getBoundToolsList()).containsExactly("tool-search");
        assertThat(obs.value.getBoundKnowledgeIdsList()).containsExactly("kb-001");
        assertThat(obs.value.getCreatedAt()).isPositive();
        assertThat(obs.value.getUpdatedAt()).isPositive();

        // 验证调用链
        verify(repo).existsByName("Code Reviewer");
        verify(repo).save(any(AgentDefinition.class));
        verify(lifecycleManager).register(any(AgentDefinition.class));
    }

    @Test
    @DisplayName("Should_ReturnAlreadyExists_When_NameDuplicate: 重复 name → onError ALREADY_EXISTS")
    void should_ReturnAlreadyExists_When_NameDuplicate() {
        // given
        CreateAgentRequest req = CreateAgentRequest.newBuilder()
                .setAgentId("agent-002")
                .setName("Existing Agent")
                .setDescription("desc")
                .setSystemPrompt("prompt")
                .build();
        when(repo.existsByName("Existing Agent")).thenReturn(true);

        // when
        CapturingObserver<AgentResponse> obs = new CapturingObserver<>();
        grpcService.createAgent(req, obs);

        // then
        assertThat(obs.completed).isFalse();
        assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) obs.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(sre.getStatus().getDescription()).contains("AGENT_ALREADY_EXISTS");
        // 验证未调用 save / register
        verify(repo, never()).save(any(AgentDefinition.class));
        verify(lifecycleManager, never()).register(any(AgentDefinition.class));
    }

    // ===== RPC 2: GetAgent =====

    @Test
    @DisplayName("Should_ReturnAgent_When_Found: 正常查询 → 返回完整字段")
    void should_ReturnAgent_When_Found() {
        // given
        GetAgentRequest req = GetAgentRequest.newBuilder()
                .setAgentId("agent-001")
                .build();
        AgentDefinition entity = buildSampleEntity("agent-001", "Code Reviewer", AgentStatus.DRAFT, 1);
        when(repo.findById("agent-001")).thenReturn(Optional.of(entity));

        // when
        CapturingObserver<AgentResponse> obs = new CapturingObserver<>();
        grpcService.getAgent(req, obs);

        // then
        assertThat(obs.completed).isTrue();
        assertThat(obs.value).isNotNull();
        assertThat(obs.value.getAgentId()).isEqualTo("agent-001");
        assertThat(obs.value.getName()).isEqualTo("Code Reviewer");
        assertThat(obs.value.getAgentTier()).isEqualTo("standard");
        assertThat(obs.value.getStatus()).isEqualTo("draft");
        assertThat(obs.value.getVersion()).isEqualTo(1);
        assertThat(obs.value.getMaxSteps()).isEqualTo(10);
        assertThat(obs.value.getMaxToken()).isEqualTo(4096);
        verify(repo).findById("agent-001");
    }

    @Test
    @DisplayName("Should_ReturnNotFound_When_AgentMissing: 不存在 → onError NOT_FOUND")
    void should_ReturnNotFound_When_AgentMissing() {
        // given
        GetAgentRequest req = GetAgentRequest.newBuilder()
                .setAgentId("agent-missing")
                .build();
        when(repo.findById("agent-missing")).thenReturn(Optional.empty());

        // when
        CapturingObserver<AgentResponse> obs = new CapturingObserver<>();
        grpcService.getAgent(req, obs);

        // then
        assertThat(obs.completed).isFalse();
        assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) obs.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(sre.getStatus().getDescription()).contains("AGENT_NOT_FOUND");
    }

    // ===== RPC 3: UpdateAgent =====

    @Test
    @DisplayName("Should_UpdateAgent_When_StatusDraft: 正常更新 → version 自增")
    void should_UpdateAgent_When_StatusDraft() {
        // given
        UpdateAgentRequest req = UpdateAgentRequest.newBuilder()
                .setAgentId("agent-001")
                .setName("Code Reviewer v2")
                .setDescription("updated desc")
                .setSystemPrompt("updated prompt")
                .setAgentTier("advanced")
                .setMaxSteps(30)
                .setMaxToken(16384)
                .addAbilityTags("code_review")
                .setChangeLog("update tier and limits")
                .build();
        AgentDefinition existing = buildSampleEntity("agent-001", "Code Reviewer", AgentStatus.DRAFT, 1);
        when(repo.findById("agent-001")).thenReturn(Optional.of(existing));
        // 模拟 save 返回合并后的 entity（version 已自增）
        when(repo.save(any(AgentDefinition.class))).thenAnswer(invocation -> {
            AgentDefinition e = invocation.getArgument(0);
            // 模拟 JPA 持久化（保持 id/createdAt，更新 updatedAt）
            e.setUpdatedAt(System.currentTimeMillis());
            return e;
        });

        // when
        CapturingObserver<AgentResponse> obs = new CapturingObserver<>();
        grpcService.updateAgent(req, obs);

        // then
        assertThat(obs.completed).isTrue();
        assertThat(obs.value).isNotNull();
        assertThat(obs.value.getName()).isEqualTo("Code Reviewer v2");
        assertThat(obs.value.getDescription()).isEqualTo("updated desc");
        assertThat(obs.value.getAgentTier()).isEqualTo("advanced");
        assertThat(obs.value.getMaxSteps()).isEqualTo(30);
        assertThat(obs.value.getMaxToken()).isEqualTo(16384);
        // version 自增 1 → 2
        assertThat(obs.value.getVersion()).isEqualTo(2);
        verify(repo).findById("agent-001");
        verify(repo).save(any(AgentDefinition.class));
    }

    @Test
    @DisplayName("Should_ReturnFailedPrecondition_When_StatusPublished: PUBLISHED 状态 → onError FAILED_PRECONDITION")
    void should_ReturnFailedPrecondition_When_StatusPublished() {
        // given
        UpdateAgentRequest req = UpdateAgentRequest.newBuilder()
                .setAgentId("agent-001")
                .setName("Code Reviewer v2")
                .setDescription("updated desc")
                .setSystemPrompt("updated prompt")
                .setAgentTier("advanced")
                .build();
        AgentDefinition existing = buildSampleEntity("agent-001", "Code Reviewer", AgentStatus.PUBLISHED, 1);
        when(repo.findById("agent-001")).thenReturn(Optional.of(existing));

        // when
        CapturingObserver<AgentResponse> obs = new CapturingObserver<>();
        grpcService.updateAgent(req, obs);

        // then
        assertThat(obs.completed).isFalse();
        assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) obs.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(sre.getStatus().getDescription()).contains("AGENT_STATUS_CONFLICT");
        // 验证未调用 save
        verify(repo, never()).save(any(AgentDefinition.class));
    }

    // ===== RPC 4: ListAgents =====

    @Test
    @DisplayName("Should_ReturnPagedResult_When_ListAgents: 分页查询 → 返回 items + total")
    void should_ReturnPagedResult_When_ListAgents() {
        // given
        ListAgentsRequest req = ListAgentsRequest.newBuilder()
                .setStatus("draft")
                .setNameContains("Code")
                .setPage(0)
                .setSize(10)
                .build();
        List<AgentDefinition> items = Arrays.asList(
                buildSampleEntity("agent-001", "Code Reviewer", AgentStatus.DRAFT, 1),
                buildSampleEntity("agent-002", "Code Generator", AgentStatus.DRAFT, 1));
        PageResult<AgentDefinition> page = new PageResult<>(items, 2L, 0, 10);
        when(queryService.query(any())).thenReturn(page);

        // when
        CapturingObserver<ListAgentsResponse> obs = new CapturingObserver<>();
        grpcService.listAgents(req, obs);

        // then
        assertThat(obs.completed).isTrue();
        assertThat(obs.value).isNotNull();
        assertThat(obs.value.getItemsList()).hasSize(2);
        assertThat(obs.value.getTotal()).isEqualTo(2L);
        assertThat(obs.value.getPage()).isEqualTo(0);
        assertThat(obs.value.getSize()).isEqualTo(10);
        assertThat(obs.value.getItems(0).getAgentId()).isEqualTo("agent-001");
        assertThat(obs.value.getItems(0).getName()).isEqualTo("Code Reviewer");
        assertThat(obs.value.getItems(1).getAgentId()).isEqualTo("agent-002");
        assertThat(obs.value.getItems(1).getName()).isEqualTo("Code Generator");
        verify(queryService).query(any());
    }

    @Test
    @DisplayName("Should_ReturnEmptyPage_When_NoMatch: 无匹配 → 返回空 items + total=0")
    void should_ReturnEmptyPage_When_NoMatch() {
        // given
        ListAgentsRequest req = ListAgentsRequest.newBuilder()
                .setStatus("archived")
                .setPage(0)
                .setSize(10)
                .build();
        PageResult<AgentDefinition> emptyPage = new PageResult<>(Collections.emptyList(), 0L, 0, 10);
        when(queryService.query(any())).thenReturn(emptyPage);

        // when
        CapturingObserver<ListAgentsResponse> obs = new CapturingObserver<>();
        grpcService.listAgents(req, obs);

        // then
        assertThat(obs.completed).isTrue();
        assertThat(obs.value).isNotNull();
        assertThat(obs.value.getItemsList()).isEmpty();
        assertThat(obs.value.getTotal()).isEqualTo(0L);
    }

    // ===== helpers =====

    /**
     * 构造测试用 AgentDefinition entity（带 id / timestamps）。
     */
    private AgentDefinition buildSampleEntity(String agentId, String name, AgentStatus status, int version) {
        AgentDefinition entity = new AgentDefinition(agentId, name);
        entity.setId(1L);
        entity.setDescription("desc for " + name);
        entity.setAbilityTags(Arrays.asList("qa", "reasoning"));
        entity.setSystemPrompt("system prompt");
        entity.setAgentTier(AgentTier.STANDARD);
        entity.setMaxSteps(10);
        entity.setMaxToken(4096);
        entity.setStatus(status);
        entity.setVersion(version);
        entity.setBoundTools(Arrays.asList("tool-search"));
        entity.setBoundKnowledgeIds(Arrays.asList("kb-001"));
        entity.setCreatedAt(1700000000000L);
        entity.setUpdatedAt(1700000000000L);
        return entity;
    }

    /**
     * Capturing StreamObserver，捕获 onNext/onError/onCompleted。
     */
    private static class CapturingObserver<T> implements StreamObserver<T> {
        T value;
        Throwable error;
        boolean completed;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
