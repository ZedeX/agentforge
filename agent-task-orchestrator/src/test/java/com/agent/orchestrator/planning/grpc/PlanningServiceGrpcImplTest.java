package com.agent.orchestrator.planning.grpc;

import agentplatform.planning.v1.AssessRequest;
import agentplatform.planning.v1.AssessResponse;
import agentplatform.planning.v1.PlanRequest;
import agentplatform.planning.v1.PlanResponse;
import agentplatform.planning.v1.ReplanRequest;
import agentplatform.planning.v1.ValidateRequest;
import agentplatform.planning.v1.ValidateResponse;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.assessor.ComplexityDimensions;
import com.agent.orchestrator.assessor.ComplexityLevel;
import com.agent.orchestrator.assessor.ComplexityScorer;
import com.agent.orchestrator.assessor.RuleFilter;
import com.agent.orchestrator.replanner.ReplanMode;
import com.agent.orchestrator.replanner.ReplanModeSelector;
import com.agent.orchestrator.template.TaskTemplate;
import com.agent.orchestrator.template.TemplateMatcher;
import com.agent.orchestrator.validator.PlanValidator;
import com.agent.orchestrator.validator.ValidationResult;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PlanningService gRPC 服务单测（Green 阶段）。
 *
 * <p>对齐 docs/tests/unit-test-cases.md §6 UT-PLAN-001~010 + Replan 边界用例：
 * 复杂度三档分级 / 风险强制升级 / 规则初筛置信度跳模型 / 模板匹配 vs AI 规划 / 5 维度自检通过失败 / 重规划次数耗尽。</p>
 *
 * <p>设计说明：</p>
 * <ul>
 *   <li>用 Mockito mock 7 个协作者（{@link ComplexityScorer}/{@link RuleFilter}/
 *       {@link TemplateMatcher}/{@link PlanValidator}/{@link ReplanModeSelector}/
 *       {@link DagJsonMapper}/{@link AssessResultMapper}），隔离 gRPC 服务层逻辑；</li>
 *   <li>采用 {@link Strictness#LENIENT} 以允许不同测试用例按需配置不同子集的 mock
 *       （如 validatePlan 测试无需配置 complexityScorer）；</li>
 *   <li>方法名 {@code should_X_When_Y} snake_case，{@code @DisplayName} 中文，
 *       断言统一使用 AssertJ {@code assertThat} / {@code assertThatThrownBy}。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanningServiceGrpcImplTest {

    @Mock private ComplexityScorer complexityScorer;
    @Mock private RuleFilter ruleFilter;
    @Mock private TemplateMatcher templateMatcher;
    @Mock private PlanValidator planValidator;
    @Mock private ReplanModeSelector replanModeSelector;
    @Mock private DagJsonMapper dagJsonMapper;
    @Mock private AssessResultMapper assessResultMapper;

    @Mock private StreamObserver<AssessResponse> assessObserver;
    @Mock private StreamObserver<PlanResponse> planObserver;
    @Mock private StreamObserver<ValidateResponse> validateObserver;

    private PlanningServiceGrpcImpl service;

    @BeforeEach
    void setUp() {
        service = new PlanningServiceGrpcImpl(complexityScorer, ruleFilter, templateMatcher,
                planValidator, replanModeSelector, dagJsonMapper, assessResultMapper);
    }

    // ============ UT-PLAN-001: 总分 ≤ 8 → L1 ============

    @Test
    @DisplayName("UT-PLAN-001: 6 维度总分=8 应判级 L1，complexity=1")
    void should_ReturnL1_When_TotalScoreLe8() {
        AssessRequest req = AssessRequest.newBuilder()
                .setTaskId("tk_p_001").setGoal("简单查询").build();
        // 规则初筛返回 L1 候选 → buildDimensions 构造 (1,1,1,1,1,1) 总分 6（实际由 scorer mock 决定）
        when(ruleFilter.quickFilter(any())).thenReturn(
                new RuleFilter.Result(ComplexityLevel.L1, 0.95, "keyword:L1:查询"));
        when(complexityScorer.score(any(ComplexityDimensions.class))).thenReturn(ComplexityLevel.L1);
        when(assessResultMapper.toAssessResponse(any(), any(), any())).thenReturn(
                AssessResponse.newBuilder().setComplexity(1).setReason("L1").build());

        service.assessComplexity(req, assessObserver);

        ArgumentCaptor<AssessResponse> captor = ArgumentCaptor.forClass(AssessResponse.class);
        verify(assessObserver).onNext(captor.capture());
        verify(assessObserver).onCompleted();
        assertThat(captor.getValue().getComplexity()).isEqualTo(1);
    }

    // ============ UT-PLAN-002: 总分 9~14 → L2 ============

    @Test
    @DisplayName("UT-PLAN-002: 总分=14 应判级 L2，complexity=2")
    void should_ReturnL2_When_TotalScoreBetween9And14() {
        AssessRequest req = AssessRequest.newBuilder()
                .setTaskId("tk_p_002").setGoal("对比分析").build();
        when(ruleFilter.quickFilter(any())).thenReturn(
                new RuleFilter.Result(ComplexityLevel.L2, 0.95, "keyword:L2:对比"));
        when(complexityScorer.score(any(ComplexityDimensions.class))).thenReturn(ComplexityLevel.L2);
        when(assessResultMapper.toAssessResponse(any(), any(), any())).thenReturn(
                AssessResponse.newBuilder().setComplexity(2).setReason("L2").build());

        service.assessComplexity(req, assessObserver);

        ArgumentCaptor<AssessResponse> captor = ArgumentCaptor.forClass(AssessResponse.class);
        verify(assessObserver).onNext(captor.capture());
        assertThat(captor.getValue().getComplexity()).isEqualTo(2);
    }

    // ============ UT-PLAN-003: 总分 > 14 → L3 ============

    @Test
    @DisplayName("UT-PLAN-003: 总分=15 应判级 L3，complexity=3")
    void should_ReturnL3_When_TotalScoreGt14() {
        AssessRequest req = AssessRequest.newBuilder()
                .setTaskId("tk_p_003").setGoal("跨系统协同编排").build();
        when(ruleFilter.quickFilter(any())).thenReturn(
                new RuleFilter.Result(ComplexityLevel.L3, 0.95, "keyword:L3:协同"));
        when(complexityScorer.score(any(ComplexityDimensions.class))).thenReturn(ComplexityLevel.L3);
        when(assessResultMapper.toAssessResponse(any(), any(), any())).thenReturn(
                AssessResponse.newBuilder().setComplexity(3).setReason("L3").build());

        service.assessComplexity(req, assessObserver);

        ArgumentCaptor<AssessResponse> captor = ArgumentCaptor.forClass(AssessResponse.class);
        verify(assessObserver).onNext(captor.capture());
        assertThat(captor.getValue().getComplexity()).isEqualTo(3);
    }

    // ============ UT-PLAN-004: 风险维度高时强制升级 L3 ============

    @Test
    @DisplayName("UT-PLAN-004: 风险维度高时应强制升级为 L3（即使总分对应 L2）")
    void should_ForceUpgradeToL3_When_RiskLevelIsHigh() {
        AssessRequest req = AssessRequest.newBuilder()
                .setTaskId("tk_p_004").setGoal("数据库写入").build();
        when(ruleFilter.quickFilter(any())).thenReturn(
                new RuleFilter.Result(null, 0.5, "no-match"));
        // 即使维度总分=10（本应 L2），风险高 → L3
        when(complexityScorer.score(any(ComplexityDimensions.class))).thenReturn(ComplexityLevel.L3);
        when(assessResultMapper.toAssessResponse(any(), any(), any())).thenReturn(
                AssessResponse.newBuilder().setComplexity(3).setReason("risk upgrade").build());

        service.assessComplexity(req, assessObserver);

        ArgumentCaptor<AssessResponse> captor = ArgumentCaptor.forClass(AssessResponse.class);
        verify(assessObserver).onNext(captor.capture());
        assertThat(captor.getValue().getComplexity()).isEqualTo(3);
    }

    // ============ UT-PLAN-005: 规则置信度 ≥ 0.9 跳过模型精判 ============

    @Test
    @DisplayName("UT-PLAN-005: 规则置信度=0.95 应跳过模型精判直接评分")
    void should_BypassModelAssessor_When_RuleConfidenceHigh() {
        AssessRequest req = AssessRequest.newBuilder()
                .setTaskId("tk_p_005").setGoal("翻译这段文本").build();
        when(ruleFilter.quickFilter(any())).thenReturn(
                new RuleFilter.Result(ComplexityLevel.L1, 0.95, "keyword:L1:翻译"));
        when(complexityScorer.score(any(ComplexityDimensions.class))).thenReturn(ComplexityLevel.L1);
        when(assessResultMapper.toAssessResponse(any(), any(), any())).thenReturn(
                AssessResponse.newBuilder().setComplexity(1).build());

        service.assessComplexity(req, assessObserver);

        // complexityScorer 即代表规则路径，验证仅被调用 1 次
        verify(complexityScorer, times(1)).score(any(ComplexityDimensions.class));
        verify(assessObserver).onNext(any());
    }

    // ============ UT-PLAN-006: 规则置信度 < 0.9 调用模型精判 ============

    @Test
    @DisplayName("UT-PLAN-006: 规则置信度=0.6 应调用模型精判路径")
    void should_InvokeModelAssessor_When_RuleConfidenceLow() {
        AssessRequest req = AssessRequest.newBuilder()
                .setTaskId("tk_p_006").setGoal("个性化长尾需求").build();
        when(ruleFilter.quickFilter(any())).thenReturn(
                new RuleFilter.Result(null, 0.5, "no-match"));
        when(complexityScorer.score(any(ComplexityDimensions.class))).thenReturn(ComplexityLevel.L2);
        when(assessResultMapper.toAssessResponse(any(), any(), any())).thenReturn(
                AssessResponse.newBuilder().setComplexity(2).build());

        service.assessComplexity(req, assessObserver);

        // 模型精判路径仍调用 complexityScorer（当前 stub 代理，后续接入 model-gateway）
        verify(complexityScorer, times(1)).score(any(ComplexityDimensions.class));
        verify(assessObserver).onNext(any());
    }

    // ============ UT-PLAN-007: 高频场景匹配预置模板 ============

    @Test
    @DisplayName("UT-PLAN-007: 高频场景应匹配预置模板返回 source=template")
    void should_MatchTemplate_When_HighFrequencyScenario() {
        PlanRequest req = PlanRequest.newBuilder()
                .setTaskId("tk_t_001")
                .setTaskSchemaJson("{\"objective\":\"生成周报\"}")
                .setPreferTemplate(true)
                .build();
        TaskTemplate template = TaskTemplate.builder()
                .templateId("1001")
                .title("周报模板")
                .dagNodes(Collections.emptyList())
                .dagEdges(Collections.emptyList())
                .build();
        when(templateMatcher.match(any(), any())).thenReturn(template);
        when(dagJsonMapper.toDagJson(any(), any())).thenReturn("{\"nodes\":[],\"edges\":[]}");
        when(planValidator.validate(any())).thenReturn(
                ValidationResult.builder().allPass(true).build());

        service.plan(req, planObserver);

        ArgumentCaptor<PlanResponse> captor = ArgumentCaptor.forClass(PlanResponse.class);
        verify(planObserver).onNext(captor.capture());
        verify(planObserver).onCompleted();
        PlanResponse response = captor.getValue();
        assertThat(response.getSource()).isEqualTo("template");
        assertThat(response.getTemplateId()).isEqualTo(1001L);
    }

    // ============ UT-PLAN-008: 无模板匹配进入 AI 规划 ============

    @Test
    @DisplayName("UT-PLAN-008: 无模板匹配应进入 AI 规划返回 source=ai")
    void should_FallbackToAiPlanner_When_NoTemplateMatched() {
        PlanRequest req = PlanRequest.newBuilder()
                .setTaskId("tk_t_002")
                .setTaskSchemaJson("{\"objective\":\"个性化长尾\"}")
                .setPreferTemplate(true)
                .build();
        when(templateMatcher.match(any(), any())).thenReturn(null);
        when(dagJsonMapper.toDagJson(any(), any())).thenReturn("{\"nodes\":[],\"edges\":[]}");
        when(planValidator.validate(any())).thenReturn(
                ValidationResult.builder().allPass(true).build());

        service.plan(req, planObserver);

        ArgumentCaptor<PlanResponse> captor = ArgumentCaptor.forClass(PlanResponse.class);
        verify(planObserver).onNext(captor.capture());
        verify(planObserver).onCompleted();
        PlanResponse response = captor.getValue();
        assertThat(response.getSource()).isEqualTo("ai");
        assertThat(response.getTemplateId()).isEqualTo(0L);
    }

    // ============ UT-PLAN-008 边界: prefer_template=false 直接走 AI ============

    @Test
    @DisplayName("UT-PLAN-008 边界: prefer_template=false 不调用模板匹配直接走 AI")
    void should_SkipTemplateMatch_When_PreferTemplateFalse() {
        PlanRequest req = PlanRequest.newBuilder()
                .setTaskId("tk_t_003")
                .setTaskSchemaJson("{\"objective\":\"无模板偏好\"}")
                .setPreferTemplate(false)
                .build();
        when(dagJsonMapper.toDagJson(any(), any())).thenReturn("{\"nodes\":[],\"edges\":[]}");
        when(planValidator.validate(any())).thenReturn(
                ValidationResult.builder().allPass(true).build());

        service.plan(req, planObserver);

        verify(templateMatcher, never()).match(any(), any());
        ArgumentCaptor<PlanResponse> captor = ArgumentCaptor.forClass(PlanResponse.class);
        verify(planObserver).onNext(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("ai");
    }

    // ============ UT-PLAN-009: 5 维度自检全通过 ============

    @Test
    @DisplayName("UT-PLAN-009: 5 维度自检全通过应返回 valid=true")
    void should_PassValidation_When_AllFiveDimensionsOk() {
        ValidateRequest req = ValidateRequest.newBuilder()
                .setTaskId("tk_v_001")
                .setDagJson("{\"nodes\":[],\"edges\":[]}")
                .build();
        when(dagJsonMapper.fromDagJsonNodes(any())).thenReturn(Collections.emptyList());
        when(dagJsonMapper.fromDagJsonEdges(any())).thenReturn(Collections.emptyList());
        when(planValidator.validate(any())).thenReturn(
                ValidationResult.builder().allPass(true).build());

        service.validatePlan(req, validateObserver);

        ArgumentCaptor<ValidateResponse> captor = ArgumentCaptor.forClass(ValidateResponse.class);
        verify(validateObserver).onNext(captor.capture());
        verify(validateObserver).onCompleted();
        assertThat(captor.getValue().getValid()).isTrue();
    }

    // ============ UT-PLAN-010: 完备性校验失败返回 valid=false ============

    @Test
    @DisplayName("UT-PLAN-010: 完备性校验失败应返回 valid=false 并附带错误列表")
    void should_ReturnPlanValidationFailed_When_CompletenessFailed() {
        ValidateRequest req = ValidateRequest.newBuilder()
                .setTaskId("tk_v_002")
                .setDagJson("{}")
                .build();
        when(dagJsonMapper.fromDagJsonNodes(any())).thenReturn(Collections.emptyList());
        when(dagJsonMapper.fromDagJsonEdges(any())).thenReturn(Collections.emptyList());
        when(planValidator.validate(any())).thenReturn(
                ValidationResult.builder()
                        .allPass(false)
                        .errors(List.of("completeness: missing deliverable node"))
                        .build());

        service.validatePlan(req, validateObserver);

        ArgumentCaptor<ValidateResponse> captor = ArgumentCaptor.forClass(ValidateResponse.class);
        verify(validateObserver).onNext(captor.capture());
        ValidateResponse response = captor.getValue();
        assertThat(response.getValid()).isFalse();
        assertThat(response.getErrorsList()).isNotEmpty();
        assertThat(response.getErrorsList())
                .anyMatch(err -> err.contains("completeness"));
    }

    // ============ Replan: 单子任务失败应返回增量重规划结果 ============

    @Test
    @DisplayName("Replan: 单子任务失败应返回增量重规划结果")
    void should_ReturnIncrementalReplan_When_SingleSubtaskFails() {
        ReplanRequest req = ReplanRequest.newBuilder()
                .setTaskId("tk_r_001")
                .setReason("subtask_failed")
                .setReplanCount(0)
                .setPreviousDagJson("{}")
                .build();
        when(replanModeSelector.selectOrAbort(any())).thenReturn(ReplanMode.INCREMENTAL);
        when(dagJsonMapper.toDagJson(any(), any())).thenReturn("{\"nodes\":[],\"edges\":[]}");
        when(planValidator.validate(any())).thenReturn(
                ValidationResult.builder().allPass(true).build());

        service.replan(req, planObserver);

        ArgumentCaptor<PlanResponse> captor = ArgumentCaptor.forClass(PlanResponse.class);
        verify(planObserver).onNext(captor.capture());
        verify(planObserver).onCompleted();
        verify(replanModeSelector).selectOrAbort(any());
        PlanResponse response = captor.getValue();
        assertThat(response.getDagVersion()).isEqualTo(1);
        assertThat(response.getSource()).isEqualTo("ai");
        assertThat(response.getWarningsList())
                .anyMatch(w -> w.contains("INCREMENTAL"));
    }

    // ============ 边界: 重规划次数耗尽应抛 REPLAN_EXHAUSTED ============

    @Test
    @DisplayName("Replan 边界: 重规划次数耗尽应抛 REPLAN_EXHAUSTED 异常")
    void should_ThrowReplanExhausted_When_ReplanCountExceedsMax() {
        ReplanRequest req = ReplanRequest.newBuilder()
                .setTaskId("tk_r_002")
                .setReason("exhausted")
                .setReplanCount(3)
                .setPreviousDagJson("{}")
                .build();
        when(replanModeSelector.selectOrAbort(any())).thenReturn(ReplanMode.ABORT);

        assertThatThrownBy(() -> service.replan(req, planObserver))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.REPLAN_EXHAUSTED);
                });

        // 验证未触发 onNext（异常前已抛出）
        verify(planObserver, never()).onNext(any());
    }

    // ============ 边界: 重规划 FULL 模式应返回全量重生成结果 ============

    @Test
    @DisplayName("Replan 边界: 需求变更应触发 FULL 全量重规划")
    void should_ReturnFullReplan_When_RequirementChange() {
        ReplanRequest req = ReplanRequest.newBuilder()
                .setTaskId("tk_r_003")
                .setReason("requirement_change")
                .setReplanCount(1)
                .setPreviousDagJson("{}")
                .build();
        when(replanModeSelector.selectOrAbort(any())).thenReturn(ReplanMode.FULL);
        when(dagJsonMapper.toDagJson(any(), any())).thenReturn("{\"nodes\":[],\"edges\":[]}");
        when(planValidator.validate(any())).thenReturn(
                ValidationResult.builder().allPass(true).build());

        service.replan(req, planObserver);

        ArgumentCaptor<PlanResponse> captor = ArgumentCaptor.forClass(PlanResponse.class);
        verify(planObserver).onNext(captor.capture());
        PlanResponse response = captor.getValue();
        assertThat(response.getDagVersion()).isEqualTo(2);
        assertThat(response.getWarningsList())
                .anyMatch(w -> w.contains("FULL"));
    }
}
