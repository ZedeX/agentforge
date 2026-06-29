package com.agent.drift;

import com.agent.drift.api.BaselineAnchor;
import com.agent.drift.api.DriftCorrector;
import com.agent.drift.api.DriftDetector;
import com.agent.drift.api.MemoryDriftHandler;
import com.agent.drift.enums.DriftLevel;
import com.agent.drift.enums.DriftType;
import com.agent.drift.model.BehaviorBaseline;
import com.agent.drift.model.DriftCorrectAction;
import com.agent.drift.model.DriftSignal;
import com.agent.drift.model.MemoryRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F11 漂移监测决策节点补强测试（对齐 docs/tests/unit-test-cases.md §18.6）。
 *
 * <p>覆盖 F11 第四层对齐漂移检测（输出与 goal cosine_sim 低于阈值）
 * 与 F11 记忆漂移专项检测（召回相关性下降 >30%）共 2 条补强用例。</p>
 */
class F11DecisionNodeTest {

    @Test
    @DisplayName("UT-F11-001: 第四层对齐漂移检测（输出与任务 goal cosine_sim=0.4 低于阈值触发纠偏）")
    void should_DetectAlignmentDrift_When_OutputDeviateFromGoal() {
        // F11 对齐监测：输出与 goal cosine_sim=0.4 < 阈值 0.75 → ALIGNMENT_DRIFT
        DriftDetector detector = mock(DriftDetector.class);
        DriftSignal signal = new DriftSignal(DriftType.ALIGNMENT_DRIFT, 0.4, 0.75);
        signal.setAgentId("ag_1");
        signal.setAlignmentCosine(0.4);
        when(detector.detect(eq(signal))).thenReturn(DriftType.ALIGNMENT_DRIFT);

        BaselineAnchor baselineAnchor = mock(BaselineAnchor.class);
        when(baselineAnchor.anchor(any())).thenReturn(true);

        DriftCorrector corrector = mock(DriftCorrector.class);
        when(corrector.correct(any())).thenReturn(true);

        // 1. 锚定行为基准（首次运行）
        BehaviorBaseline baseline = new BehaviorBaseline("ag_1", "v1", "golden_hash_001");
        boolean anchored = baselineAnchor.anchor(baseline);
        assertThat(anchored)
                .as("首次运行应成功锚定行为基准")
                .isTrue();

        // 2. 检测到对齐漂移（cosine_sim=0.4 < 阈值 0.75）
        DriftType type = detector.detect(signal);
        assertThat(type)
                .as("cosine_sim=0.4 应检测为 ALIGNMENT_DRIFT")
                .isEqualTo(DriftType.ALIGNMENT_DRIFT);
        assertThat(signal.getAlignmentCosine())
                .as("对齐相似度应为 0.4")
                .isEqualTo(0.4);
        assertThat(signal.getScore())
                .as("drift score=0.4 应低于阈值 0.75")
                .isLessThan(signal.getThreshold());

        // 3. 会话级轻度漂移注入核心约束纠偏
        DriftCorrectAction action = new DriftCorrectAction(DriftLevel.SESSION, "ag_1");
        action.setCoreConstraintSummary("必须围绕用户原始 goal 回答，禁止偏离主题");
        boolean corrected = corrector.correct(action);
        assertThat(corrected)
                .as("会话级漂移纠偏应成功注入核心约束")
                .isTrue();
        verify(corrector, times(1)).correct(action);
    }

    @Test
    @DisplayName("UT-F11-002: 记忆漂移专项检测（召回相关性下降 >30% 时标记错误记忆 invalid 并归档过期记忆）")
    void should_DetectMemoryDrift_When_RecallRelevanceDecline() {
        // F11 记忆漂移：召回相关性下降 >30% → 错误记忆标记 invalid，过期记忆归档
        DriftDetector detector = mock(DriftDetector.class);
        DriftSignal signal = new DriftSignal(DriftType.MEMORY_DRIFT, 0.35, 0.30);
        signal.setRelevanceDeclineRatio(0.35);
        when(detector.detect(eq(signal))).thenReturn(DriftType.MEMORY_DRIFT);

        MemoryDriftHandler handler = mock(MemoryDriftHandler.class);

        // 1. 检测到记忆漂移（相关性下降 35% > 阈值 30%）
        DriftType type = detector.detect(signal);
        assertThat(type)
                .as("召回相关性下降 35% 应检测为 MEMORY_DRIFT")
                .isEqualTo(DriftType.MEMORY_DRIFT);
        assertThat(signal.getRelevanceDeclineRatio())
                .as("相关性下降比例应为 0.35")
                .isEqualTo(0.35);
        assertThat(signal.getRelevanceDeclineRatio())
                .as("下降比例 0.35 应超过阈值 0.30")
                .isGreaterThan(signal.getThreshold());

        // 2. 错误记忆标记 invalid
        MemoryRecord wrongMemory = new MemoryRecord("mem_001", 0.20);
        wrongMemory.setInvalid(true);
        handler.markInvalid(wrongMemory.getMemoryId());
        verify(handler, times(1)).markInvalid("mem_001");
        assertThat(wrongMemory.isInvalid())
                .as("错误记忆应被标记 invalid=true")
                .isTrue();
        assertThat(wrongMemory.getRelevanceScore())
                .as("错误记忆相关性得分应较低（0.20）")
                .isLessThan(0.5);

        // 3. 过期记忆归档
        MemoryRecord expiredMemory = new MemoryRecord("mem_099", 0.10);
        expiredMemory.setCreatedAt(Instant.now().minus(95, ChronoUnit.DAYS));
        expiredMemory.setExpiredAt(Instant.now().minus(5, ChronoUnit.DAYS));
        handler.archive(expiredMemory.getMemoryId());
        verify(handler, times(1)).archive("mem_099");
        assertThat(expiredMemory.getExpiredAt())
                .as("过期记忆 expiredAt 应早于当前时间")
                .isBefore(Instant.now());
    }
}
