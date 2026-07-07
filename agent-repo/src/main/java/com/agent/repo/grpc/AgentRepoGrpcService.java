package com.agent.repo.grpc;

import agentplatform.repo.v1.AgentRepoGrpc;
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
import com.agent.repo.model.AgentDefinition;
import com.agent.repo.model.PageResult;
import com.agent.repo.model.RepoQuery;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Optional;

/**
 * AgentRepo gRPC 服务端实现（Plan 08 T4，4 RPC）。
 *
 * <p>覆盖 {@link AgentRepoGrpc.AgentRepoImplBase} 的 4 个 RPC：
 * {@code createAgent} / {@code getAgent} / {@code updateAgent} / {@code listAgents}。</p>
 *
 * <p>职责：proto request → 调用 {@link AgentRepository} / {@link AgentQueryService} /
 * {@link AgentLifecycleManager} → {@link AgentMapper} 转 proto response → 下发 observer。
 * 异常通过 {@link GrpcExceptionAdvice} 统一翻译为 gRPC Status。</p>
 *
 * <p>关键业务规则：</p>
 * <ul>
 *   <li>CreateAgent：name 唯一性校验（重复抛 AGENT_ALREADY_EXISTS → ALREADY_EXISTS），
 *       保存后注册到 {@link AgentLifecycleManager}（初始 DRAFT 态）</li>
 *   <li>GetAgent：findById 返回空抛 AGENT_NOT_FOUND → NOT_FOUND</li>
 *   <li>UpdateAgent：PUBLISHED 状态禁止直接改（抛 AGENT_STATUS_CONFLICT → FAILED_PRECONDITION），
 *       version 自增后保存</li>
 *   <li>ListAgents：委托 {@link AgentQueryService#query} 分页查询</li>
 * </ul>
 */
@Slf4j
@GrpcService
public class AgentRepoGrpcService extends AgentRepoGrpc.AgentRepoImplBase {

    private final AgentRepository repo;
    private final AgentQueryService queryService;
    private final AgentLifecycleManager lifecycleManager;
    private final AgentMapper mapper;
    private final GrpcExceptionAdvice exceptionAdvice;

    public AgentRepoGrpcService(AgentRepository repo,
                                AgentQueryService queryService,
                                AgentLifecycleManager lifecycleManager,
                                AgentMapper mapper,
                                GrpcExceptionAdvice exceptionAdvice) {
        this.repo = repo;
        this.queryService = queryService;
        this.lifecycleManager = lifecycleManager;
        this.mapper = mapper;
        this.exceptionAdvice = exceptionAdvice;
    }

    // ===== RPC 1: CreateAgent =====

    @Override
    public void createAgent(CreateAgentRequest request,
                            StreamObserver<AgentResponse> responseObserver) {
        try {
            // name 唯一性校验（重复抛 AGENT_ALREADY_EXISTS → ALREADY_EXISTS）
            if (repo.existsByName(request.getName())) {
                log.warn("createAgent name already exists agentId={} name={}",
                        request.getAgentId(), request.getName());
                throw new BusinessException(ErrorCode.AGENT_ALREADY_EXISTS,
                        "Agent 名称已存在: " + request.getName());
            }
            AgentDefinition entity = mapper.toEntity(request);
            AgentDefinition saved = repo.save(entity);
            // 注册到生命周期管理器（初始 DRAFT 态）
            lifecycleManager.register(saved);
            log.info("createAgent success agentId={} name={} tier={}",
                    saved.getAgentId(), saved.getName(), saved.getAgentTier());
            responseObserver.onNext(mapper.toResponse(saved));
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 2: GetAgent =====

    @Override
    public void getAgent(GetAgentRequest request,
                         StreamObserver<AgentResponse> responseObserver) {
        try {
            String agentId = request.getAgentId();
            Optional<AgentDefinition> found = repo.findById(agentId);
            if (found.isEmpty()) {
                log.warn("getAgent not found agentId={}", agentId);
                throw new BusinessException(ErrorCode.AGENT_NOT_FOUND,
                        "Agent 不存在: " + agentId);
            }
            AgentDefinition entity = found.get();
            log.debug("getAgent success agentId={} name={}", agentId, entity.getName());
            responseObserver.onNext(mapper.toResponse(entity));
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 3: UpdateAgent =====

    @Override
    public void updateAgent(UpdateAgentRequest request,
                            StreamObserver<AgentResponse> responseObserver) {
        try {
            String agentId = request.getAgentId();
            Optional<AgentDefinition> found = repo.findById(agentId);
            if (found.isEmpty()) {
                log.warn("updateAgent not found agentId={}", agentId);
                throw new BusinessException(ErrorCode.AGENT_NOT_FOUND,
                        "Agent 不存在: " + agentId);
            }
            AgentDefinition existing = found.get();
            // PUBLISHED 状态禁止直接改（状态机约束）
            if (existing.getStatus() == AgentStatus.PUBLISHED) {
                log.warn("updateAgent status conflict agentId={} status=PUBLISHED", agentId);
                throw new BusinessException(ErrorCode.AGENT_STATUS_CONFLICT,
                        "已发布 Agent 禁止直接修改，需先回滚到 DRAFT: " + agentId);
            }
            // 合并可更新字段（不覆盖 id/agentId/status/version/createdAt）
            AgentDefinition merged = mapper.mergeEntity(request, existing);
            // version 自增
            merged.setVersion(existing.getVersion() + 1);
            AgentDefinition saved = repo.save(merged);
            log.info("updateAgent success agentId={} name={} version={}",
                    saved.getAgentId(), saved.getName(), saved.getVersion());
            responseObserver.onNext(mapper.toResponse(saved));
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 4: ListAgents =====

    @Override
    public void listAgents(ListAgentsRequest request,
                           StreamObserver<ListAgentsResponse> responseObserver) {
        try {
            RepoQuery query = mapper.toQuery(request);
            PageResult<AgentDefinition> page = queryService.query(query);
            log.debug("listAgents success status={} page={} size={} total={}",
                    request.getStatus(), query.getPage(), query.getSize(), page.getTotal());
            responseObserver.onNext(mapper.toListResponse(page));
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }
}
