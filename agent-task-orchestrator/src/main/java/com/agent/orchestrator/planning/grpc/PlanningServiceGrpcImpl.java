package com.agent.orchestrator.planning.grpc;

import agentplatform.planning.v1.AssessRequest;
import agentplatform.planning.v1.AssessResponse;
import agentplatform.planning.v1.PlanRequest;
import agentplatform.planning.v1.PlanResponse;
import agentplatform.planning.v1.PlanningServiceGrpc;
import agentplatform.planning.v1.ReplanRequest;
import agentplatform.planning.v1.ValidateRequest;
import agentplatform.planning.v1.ValidateResponse;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.assessor.ComplexityDimensions;
import com.agent.orchestrator.assessor.ComplexityLevel;
import com.agent.orchestrator.assessor.ComplexityScorer;
import com.agent.orchestrator.assessor.RuleFilter;
import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;
import com.agent.orchestrator.replanner.ReplanMode;
import com.agent.orchestrator.replanner.ReplanModeSelector;
import com.agent.orchestrator.template.PlanMode;
import com.agent.orchestrator.template.TaskTemplate;
import com.agent.orchestrator.template.TemplateMatcher;
import com.agent.orchestrator.validator.PlanValidator;
import com.agent.orchestrator.validator.ValidationContext;
import com.agent.orchestrator.validator.ValidationResult;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PlanningService gRPC 服务端实现（对齐 planning.proto 4 RPC + doc 03-task-engine §8.2.1）。
 *
 * <p>职责：</p>
 * <ol>
 *   <li><b>assessComplexity</b>：规则初筛（{@link RuleFilter#quickFilter}）
 *       → 6 维度评分（{@link ComplexityScorer#score}，内部已实现风险强制升级逻辑）
 *       → 映射为 {@link AssessResponse}。</li>
 *   <li><b>plan</b>：prefer_template=true 时调用 {@link TemplateMatcher#match}；
 *       命中走 {@link PlanMode#TEMPLATE}，未命中走 {@link PlanMode#AI}
 *       （AI 分支 TODO：接入 model-gateway）→ {@link DagJsonMapper} 序列化 → 5 维度自检。</li>
 *   <li><b>validatePlan</b>：{@link DagJsonMapper} 反序列化 → 构造 {@link ValidationContext}
 *       → {@link PlanValidator#validate(ValidationContext)} → 映射结果。</li>
 *   <li><b>replan</b>：构造 {@link ReplanModeSelector.ReplanContext} →
 *       {@link ReplanModeSelector#selectOrAbort} → INCREMENTAL/FULL 分支重新 plan
 *       → ABORT 抛 {@link ErrorCode#REPLAN_EXHAUSTED}（由全局 @GrpcAdvice 翻译为 gRPC status）。</li>
 * </ol>
 *
 * <p>设计说明：</p>
 * <ul>
 *   <li><b>不依赖 {@code GrpcExceptionAdvice}</b>：本服务抛出的 {@link BusinessException}
 *       由 T5 子 Agent 创建的全局 {@code @GrpcAdvice} 拦截翻译为 gRPC status，
 *       服务实现层保持纯业务逻辑。</li>
 *   <li><b>构造注入</b>：7 个协作者全部构造注入，便于单测以 Mockito 注入 mock。</li>
 *   <li><b>同包名歧义</b>：proto 生成类 {@code agentplatform.planning.v1.DagNode/DagEdge}
 *       与 JPA 实体 {@code com.agent.orchestrator.model.DagNode/DagEdge} 同名，
 *       本类中 DAG 节点/边一律使用 JPA 实体（import 自 {@code com.agent.orchestrator.model}），
 *       proto 类仅在泛型/方法签名处以 FQN 引用。</li>
 * </ul>
 */
@Slf4j
@GrpcService
public class PlanningServiceGrpcImpl extends PlanningServiceGrpc.PlanningServiceImplBase {

    /** 单任务最大重规划次数上限（对齐 doc 11 F5.D4 max_replan=2）。 */
    private static final int DEFAULT_MAX_REPLAN = 2;

    private final ComplexityScorer complexityScorer;
    private final RuleFilter ruleFilter;
    private final TemplateMatcher templateMatcher;
    private final PlanValidator planValidator;
    private final ReplanModeSelector replanModeSelector;
    private final DagJsonMapper dagJsonMapper;
    private final AssessResultMapper assessResultMapper;

    /**
     * 主构造器：7 个协作者构造注入。
     *
     * @param complexityScorer    复杂度评分器
     * @param ruleFilter          规则初筛器
     * @param templateMatcher     模板匹配器
     * @param planValidator       5 维度校验器
     * @param replanModeSelector  重规划模式选择器
     * @param dagJsonMapper       DAG ↔ JSON 序列化器
     * @param assessResultMapper  评估结果 → proto 映射器
     */
    public PlanningServiceGrpcImpl(ComplexityScorer complexityScorer,
                                   RuleFilter ruleFilter,
                                   TemplateMatcher templateMatcher,
                                   PlanValidator planValidator,
                                   ReplanModeSelector replanModeSelector,
                                   DagJsonMapper dagJsonMapper,
                                   AssessResultMapper assessResultMapper) {
        this.complexityScorer = complexityScorer;
        this.ruleFilter = ruleFilter;
        this.templateMatcher = templateMatcher;
        this.planValidator = planValidator;
        this.replanModeSelector = replanModeSelector;
        this.dagJsonMapper = dagJsonMapper;
        this.assessResultMapper = assessResultMapper;
    }

    // ====================== RPC 1: assessComplexity ======================

    /**
     * 复杂度评估：规则初筛 → 6 维度评分 → 风险强制升级 → proto 映射。
     *
     * <p>规则初筛置信度 ≥ {@link RuleFilter#CONFIDENCE_THRESHOLD} 时跳过模型精判
     * （直接使用规则候选等级）；&lt; 阈值时调用模型精判（当前以 {@link ComplexityScorer}
     * 作为规则路径评分代理，后续接入 model-gateway 后替换）。</p>
     */
    @Override
    @Transactional
    public void assessComplexity(AssessRequest request, StreamObserver<AssessResponse> observer) {
        log.info("assessComplexity taskId={}, goal={}", request.getTaskId(), request.getGoal());

        // 1. 规则初筛
        RuleFilter.Result ruleResult = ruleFilter.quickFilter(request.getGoal());
        log.debug("规则初筛 taskId={}, confidence={}, candidate={}, matchedRule={}",
                request.getTaskId(), ruleResult.getConfidence(),
                ruleResult.getCandidateLevel(), ruleResult.getMatchedRule());

        // 2. 6 维度评分（ComplexityScorer 内部已实现 risk=3 强制升级 L3 逻辑）
        ComplexityDimensions dims = buildDimensions(request, ruleResult);
        ComplexityLevel level = complexityScorer.score(dims);

        // 3. 映射为 proto 响应
        AssessResponse response = assessResultMapper.toAssessResponse(
                level, buildAssessReason(ruleResult, level), suggestAbilityTags(level));
        observer.onNext(response);
        observer.onCompleted();
    }

    // ====================== RPC 2: plan ======================

    /**
     * 生成规划 DAG：模板匹配优先，未命中走 AI 规划；5 维度自检后返回。
     *
     * <p>分支：</p>
     * <ul>
     *   <li>{@code prefer_template=true} 且模板命中 → source=template，复用模板 DAG；</li>
     *   <li>否则 → source=ai，调用 AI planner 生成 DAG（TODO：接入 model-gateway）。</li>
     * </ul>
     */
    @Override
    @Transactional
    public void plan(PlanRequest request, StreamObserver<PlanResponse> observer) {
        log.info("plan taskId={}, preferTemplate={}", request.getTaskId(), request.getPreferTemplate());

        String source;
        long templateId = 0L;
        List<DagNode> nodes;
        List<DagEdge> edges;

        // 1. 模板匹配
        TaskTemplate matched = null;
        if (request.getPreferTemplate()) {
            // PlanRequest 暂未承载 sceneTags 字段，传入空列表（后续可扩展从 task_schema_json 提取）
            matched = templateMatcher.match(request.getTaskSchemaJson(), Collections.emptyList());
        }

        // 2. 分支：模板 / AI
        if (matched != null) {
            source = PlanMode.TEMPLATE.name().toLowerCase();
            templateId = parseTemplateId(matched.getTemplateId());
            nodes = matched.getDagNodes() == null ? Collections.emptyList() : matched.getDagNodes();
            edges = matched.getDagEdges() == null ? Collections.emptyList() : matched.getDagEdges();
            log.debug("模板匹配命中 templateId={}, nodes={}, edges={}",
                    templateId, nodes.size(), edges.size());
        } else {
            source = PlanMode.AI.name().toLowerCase();
            nodes = callAiPlanner(request);
            edges = buildEdgesForNodes(nodes);
            log.debug("AI 规划节点数={}, 边数={}", nodes.size(), edges.size());
        }

        // 3. 5 维度自检（自检失败不抛异常，仅追加 warnings）
        ValidationResult vr = runValidation(nodes, edges);
        List<String> warnings = new ArrayList<>(vr.getWarnings());
        if (!vr.isAllPass()) {
            warnings.addAll(vr.getErrors());
        }

        // 4. 构造响应
        observer.onNext(PlanResponse.newBuilder()
                .setDagJson(dagJsonMapper.toDagJson(nodes, edges))
                .setDagVersion(1)
                .setSource(source)
                .setTemplateId(templateId)
                .addAllWarnings(warnings)
                .build());
        observer.onCompleted();
    }

    // ====================== RPC 3: validatePlan ======================

    /**
     * 5 维度综合自检：反序列化 DAG → 构造上下文 → 调用 {@link PlanValidator#validate}。
     */
    @Override
    @Transactional
    public void validatePlan(ValidateRequest request, StreamObserver<ValidateResponse> observer) {
        log.info("validatePlan taskId={}", request.getTaskId());

        // 1. 反序列化 DAG JSON
        List<DagNode> nodes = dagJsonMapper.fromDagJsonNodes(request.getDagJson());
        List<DagEdge> edges = dagJsonMapper.fromDagJsonEdges(request.getDagJson());

        // 2. 构造校验上下文 + 执行 5 维度自检
        ValidationResult vr = runValidation(nodes, edges);

        // 3. 映射为 proto 响应
        observer.onNext(ValidateResponse.newBuilder()
                .setValid(vr.isAllPass())
                .addAllErrors(vr.getErrors())
                .addAllWarnings(vr.getWarnings())
                .build());
        observer.onCompleted();
    }

    // ====================== RPC 4: replan ======================

    /**
     * 重规划：选择 INCREMENTAL/FULL/ABORT 模式 → 重新生成 DAG。
     *
     * <p>当 {@link ReplanModeSelector#selectOrAbort} 返回 {@link ReplanMode#ABORT}
     * 时抛 {@link ErrorCode#REPLAN_EXHAUSTED}（由全局 @GrpcAdvice 拦截）。</p>
     */
    @Override
    @Transactional
    public void replan(ReplanRequest request, StreamObserver<PlanResponse> observer) {
        log.info("replan taskId={}, reason={}, replanCount={}",
                request.getTaskId(), request.getReason(), request.getReplanCount());

        // 1. 构造重规划上下文 + 选择模式
        ReplanModeSelector.ReplanContext context = buildReplanContext(request);
        ReplanMode mode = replanModeSelector.selectOrAbort(context);
        log.info("重规划模式选择 taskId={}, mode={}", request.getTaskId(), mode);

        // 2. ABORT 模式 → 抛 REPLAN_EXHAUSTED（触发状态机转 WAITING_HUMAN）
        if (mode == ReplanMode.ABORT) {
            throw new BusinessException(ErrorCode.REPLAN_EXHAUSTED,
                    "重规划次数耗尽：replan_count=" + request.getReplanCount()
                            + ", max_replan=" + DEFAULT_MAX_REPLAN);
        }

        // 3. 重新生成 DAG（INCREMENTAL 保留已成功节点，FULL 全量重生成；当前以 stub 实现）
        List<DagNode> nodes = regenerateNodes(request, mode);
        List<DagEdge> edges = buildEdgesForNodes(nodes);

        // 4. 5 维度自检
        ValidationResult vr = runValidation(nodes, edges);
        List<String> warnings = new ArrayList<>(vr.getWarnings());
        warnings.add("replan_mode=" + mode);
        if (!vr.isAllPass()) {
            warnings.addAll(vr.getErrors());
        }

        // 5. 构造响应（dag_version 递增）
        observer.onNext(PlanResponse.newBuilder()
                .setDagJson(dagJsonMapper.toDagJson(nodes, edges))
                .setDagVersion(request.getReplanCount() + 1)
                .setSource("ai")
                .addAllWarnings(warnings)
                .build());
        observer.onCompleted();
    }

    // ====================== 内部辅助方法 ======================

    /**
     * 根据 AssessRequest + 规则初筛结果构造 6 维度评分。
     *
     * <p>当前简化实现：以规则候选等级推断默认维度分（L1→全 1，L2→risk=2 其余 1，L3→risk=3 其余 2）。
     * 后续接入 ModelAssessor 后，由模型精判填入真实维度值。</p>
     */
    private ComplexityDimensions buildDimensions(AssessRequest request, RuleFilter.Result ruleResult) {
        ComplexityLevel candidate = ruleResult.getCandidateLevel();
        if (candidate == null) {
            // 无候选 → 默认 L1 维度
            return ComplexityDimensions.builder()
                    .goal(1).execution(1).domain(1).knowledge(1).risk(1).context(1)
                    .build();
        }
        switch (candidate) {
            case L1:
                return ComplexityDimensions.builder()
                        .goal(1).execution(1).domain(1).knowledge(1).risk(1).context(1)
                        .build();
            case L2:
                return ComplexityDimensions.builder()
                        .goal(2).execution(2).domain(2).knowledge(2).risk(2).context(2)
                        .build();
            case L3:
                return ComplexityDimensions.builder()
                        .goal(3).execution(3).domain(2).knowledge(2).risk(3).context(2)
                        .build();
            default:
                return ComplexityDimensions.builder().build();
        }
    }

    /**
     * 构造评估原因说明（用于审计）。
     */
    private String buildAssessReason(RuleFilter.Result ruleResult, ComplexityLevel finalLevel) {
        return "rule=" + ruleResult.getMatchedRule()
                + ",confidence=" + ruleResult.getConfidence()
                + ",level=" + finalLevel;
    }

    /**
     * 根据复杂度等级建议能力标签（简化映射，后续可由配置驱动）。
     */
    private List<String> suggestAbilityTags(ComplexityLevel level) {
        switch (level) {
            case L1:
                return List.of("query");
            case L2:
                return List.of("query", "tool_call");
            case L3:
                return List.of("query", "tool_call", "cross_domain");
            default:
                return Collections.emptyList();
        }
    }

    /**
     * 解析模板 ID（String → long；非数字时返回 0）。
     */
    private long parseTemplateId(String templateId) {
        if (templateId == null || templateId.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(templateId);
        } catch (NumberFormatException e) {
            log.warn("模板 ID 非数字格式 templateId={}, 已回退为 0", templateId);
            return 0L;
        }
    }

    /**
     * 调用 AI 规划器（TODO：接入 model-gateway.Chat 生成 DAG）。
     *
     * <p>当前为 stub 实现，返回空节点列表。后续接入 model-gateway 后替换为真实调用。</p>
     */
    private List<DagNode> callAiPlanner(PlanRequest request) {
        log.info("AI 规划器调用 taskId={}（stub 实现，返回空 DAG）", request.getTaskId());
        return Collections.emptyList();
    }

    /**
     * 根据节点列表构造默认空边列表（TODO：实际由 AI planner / 模板同时返回 edges）。
     */
    private List<DagEdge> buildEdgesForNodes(List<DagNode> nodes) {
        return Collections.emptyList();
    }

    /**
     * 执行 5 维度自检（封装 planValidator.validate 调用与上下文构造）。
     */
    private ValidationResult runValidation(List<DagNode> nodes, List<DagEdge> edges) {
        ValidationContext context = ValidationContext.builder()
                .nodes(nodes)
                .edges(edges)
                .deliverables(Collections.emptyList())
                .costLimitCent(0L) // 不限制预算（成本维度跳过）
                .estimatedCostCent(0L)
                .maxRetries(DEFAULT_MAX_REPLAN)
                .build();
        return planValidator.validate(context);
    }

    /**
     * 根据重规划模式重新生成节点列表。
     *
     * <p>当前为 stub 实现：INCREMENTAL 返回空列表（保留 frozenNodes 由调用方处理），
     * FULL 返回空列表（全量重生成）。后续接入真实 planner 后替换。</p>
     */
    private List<DagNode> regenerateNodes(ReplanRequest request, ReplanMode mode) {
        log.info("重新生成 DAG taskId={}, mode={}（stub 实现）", request.getTaskId(), mode);
        return Collections.emptyList();
    }

    /**
     * 构造 {@link ReplanModeSelector.ReplanContext}：从 ReplanRequest 提取关键字段。
     *
     * <p>简化策略：默认 failedCount=1 / totalCount=5 / otherOutputsValid=true
     * （倾向选择 INCREMENTAL）；reason=requirement_change 时由 ReplanModeSelector
     * 内部规则映射为 FULL。后续可由调用方从 previous_dag_json 推断真实 failedCount。</p>
     */
    private ReplanModeSelector.ReplanContext buildReplanContext(ReplanRequest request) {
        return ReplanModeSelector.ReplanContext.builder()
                .failedCount(1)
                .totalCount(5)
                .otherOutputsValid(true)
                .triggerReason(request.getReason())
                .replanCount(request.getReplanCount())
                .maxReplan(DEFAULT_MAX_REPLAN)
                .build();
    }
}
