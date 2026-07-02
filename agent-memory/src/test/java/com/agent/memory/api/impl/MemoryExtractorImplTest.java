package com.agent.memory.api.impl;

import com.agent.memory.enums.MemoryType;
import com.agent.memory.enums.TaskOutcome;
import com.agent.memory.extractor.MemoryExtractRule;
import com.agent.memory.model.ExtractedMemory;
import com.agent.memory.model.TaskResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryExtractorImplTest {

    /** Permissive rule (minLength=0, no blacklist) — for testing extraction logic without filtering. */
    private static MemoryExtractRule permissiveRule() {
        return new MemoryExtractRule(0, "");
    }

    /** Default rule (minLength=20, blacklist=spam) — for testing filtering. */
    private static MemoryExtractRule defaultRule() {
        return new MemoryExtractRule(20, "spam,test_placeholder");
    }

    // ============ extract(TaskResult, MemoryType) 基础提取 ============

    @Test
    @DisplayName("extract 对 EPISODIC 类型应提取步骤序列并设置 source 为 taskId")
    void should_ExtractStepSequence_When_TypeIsEpisodic() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(permissiveRule());
        TaskResult task = new TaskResult("tk_001", TaskOutcome.SUCCESS, "查询订单");
        task.setSteps(Arrays.asList("step1", "step2", "step3"));

        ExtractedMemory memory = extractor.extract(task, MemoryType.EPISODIC);

        assertThat(memory).isNotNull();
        assertThat(memory.getType()).isEqualTo(MemoryType.EPISODIC);
        assertThat(memory.getSource()).isEqualTo("tk_001");
        assertThat(memory.getStepSequence()).containsExactly("step1", "step2", "step3");
        assertThat(memory.getExtractedAt()).isNotNull();
    }

    @Test
    @DisplayName("extract 对 SEMANTIC 类型应将 goal 作为 fact 提取")
    void should_ExtractGoalAsFact_When_TypeIsSemantic() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(permissiveRule());
        TaskResult task = new TaskResult("tk_002", TaskOutcome.SUCCESS, "用户偏好：简体中文");

        ExtractedMemory memory = extractor.extract(task, MemoryType.SEMANTIC);

        assertThat(memory.getFact()).isEqualTo("用户偏好：简体中文");
        assertThat(memory.getStepSequence()).isEmpty();
    }

    @Test
    @DisplayName("extract 对 PROCEDURAL 类型应取首步作为 patternTemplate")
    void should_ExtractFirstStepAsPattern_When_TypeIsProcedural() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(permissiveRule());
        TaskResult task = new TaskResult("tk_003", TaskOutcome.SUCCESS, "目标");
        task.setSteps(Arrays.asList("打开浏览器", "输入URL", "提交表单"));

        ExtractedMemory memory = extractor.extract(task, MemoryType.PROCEDURAL);

        assertThat(memory.getPatternTemplate()).isEqualTo("打开浏览器");
    }

    @Test
    @DisplayName("extract 对 REFLECTIVE 类型应提取失败反思")
    void should_ExtractReflection_When_TypeIsReflective() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(permissiveRule());
        TaskResult task = new TaskResult("tk_004", TaskOutcome.FAILURE, "数据库连接失败排查任务");

        ExtractedMemory memory = extractor.extract(task, MemoryType.REFLECTIVE);

        assertThat(memory).isNotNull();
        assertThat(memory.getType()).isEqualTo(MemoryType.REFLECTIVE);
        assertThat(memory.getFact()).contains("任务失败").contains("数据库连接失败排查任务");
        assertThat(memory.getSource()).isEqualTo("tk_004");
    }

    @Test
    @DisplayName("extract 对 REFLECTIVE 类型且 TIMEOUT 应提取超时反思")
    void should_ExtractTimeoutReflection_When_TypeIsReflectiveAndTimeout() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(permissiveRule());
        TaskResult task = new TaskResult("tk_005", TaskOutcome.TIMEOUT, "长时间运行的批量处理任务");

        ExtractedMemory memory = extractor.extract(task, MemoryType.REFLECTIVE);

        assertThat(memory).isNotNull();
        assertThat(memory.getFact()).contains("任务超时未完成").contains("批量处理任务");
    }

    @Test
    @DisplayName("extract 对 null TaskResult 应返回 null")
    void should_ReturnNull_When_TaskResultIsNull() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(permissiveRule());
        ExtractedMemory memory = extractor.extract(null, MemoryType.EPISODIC);
        assertThat(memory).isNull();
    }

    @Test
    @DisplayName("extract 对 null MemoryType 应返回 null")
    void should_ReturnNull_When_MemoryTypeIsNull() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(permissiveRule());
        TaskResult task = new TaskResult("tk_006", TaskOutcome.SUCCESS, "查询订单");
        ExtractedMemory memory = extractor.extract(task, null);
        assertThat(memory).isNull();
    }

    // ============ extractFromTaskResult 自动分流 ============

    @Test
    @DisplayName("extractFromTaskResult 对 SUCCESS 应产出 PROCEDURAL 记忆")
    void should_ExtractProcedural_When_OutcomeIsSuccess() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(permissiveRule());
        TaskResult task = new TaskResult("tk_success", TaskOutcome.SUCCESS, "订单查询任务完成");
        task.setSteps(Arrays.asList("接收请求", "查询数据库", "返回结果"));

        List<ExtractedMemory> results = extractor.extractFromTaskResult(task);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getType()).isEqualTo(MemoryType.PROCEDURAL);
        assertThat(results.get(0).getPatternTemplate()).isEqualTo("接收请求");
    }

    @Test
    @DisplayName("extractFromTaskResult 对 FAILURE 应产出 REFLECTIVE 记忆")
    void should_ExtractReflective_When_OutcomeIsFailure() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(permissiveRule());
        TaskResult task = new TaskResult("tk_fail", TaskOutcome.FAILURE, "数据库连接失败排查任务");

        List<ExtractedMemory> results = extractor.extractFromTaskResult(task);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getType()).isEqualTo(MemoryType.REFLECTIVE);
        assertThat(results.get(0).getFact()).contains("任务失败");
    }

    @Test
    @DisplayName("extractFromTaskResult 对 TIMEOUT 应产出 REFLECTIVE 记忆")
    void should_ExtractReflective_When_OutcomeIsTimeout() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(permissiveRule());
        TaskResult task = new TaskResult("tk_timeout", TaskOutcome.TIMEOUT, "长时间运行的批量处理任务");

        List<ExtractedMemory> results = extractor.extractFromTaskResult(task);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getType()).isEqualTo(MemoryType.REFLECTIVE);
        assertThat(results.get(0).getFact()).contains("任务超时未完成");
    }

    @Test
    @DisplayName("extractFromTaskResult 对 PARTIAL 应同时产出 PROCEDURAL 和 REFLECTIVE")
    void should_ExtractBoth_When_OutcomeIsPartial() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(permissiveRule());
        TaskResult task = new TaskResult("tk_partial", TaskOutcome.PARTIAL, "部分完成的订单处理任务");
        task.setSteps(Arrays.asList("校验参数", "查询订单", "发送通知失败"));

        List<ExtractedMemory> results = extractor.extractFromTaskResult(task);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ExtractedMemory::getType)
                .containsExactlyInAnyOrder(MemoryType.PROCEDURAL, MemoryType.REFLECTIVE);
    }

    @Test
    @DisplayName("extractFromTaskResult 对 null TaskResult 应返回空列表")
    void should_ReturnEmpty_When_TaskResultIsNull() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(permissiveRule());
        List<ExtractedMemory> results = extractor.extractFromTaskResult(null);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("extractFromTaskResult 对 null outcome 应返回空列表")
    void should_ReturnEmpty_When_OutcomeIsNull() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(permissiveRule());
        TaskResult task = new TaskResult("tk_null", null, "任务目标内容足够长");
        List<ExtractedMemory> results = extractor.extractFromTaskResult(task);
        assertThat(results).isEmpty();
    }

    // ============ 内容过滤 ============

    @Test
    @DisplayName("extract 应过滤长度不足 20 字符的内容（返回 null）")
    void should_FilterContent_When_LengthBelowMin() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(defaultRule());
        TaskResult task = new TaskResult("tk_short", TaskOutcome.SUCCESS, "短目标");

        ExtractedMemory memory = extractor.extract(task, MemoryType.SEMANTIC);

        assertThat(memory).as("内容长度不足 20 字符应被过滤").isNull();
    }

    @Test
    @DisplayName("extract 应过滤命中黑名单关键词的内容（返回 null）")
    void should_FilterContent_When_BlacklistKeywordHit() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(defaultRule());
        TaskResult task = new TaskResult("tk_spam", TaskOutcome.SUCCESS, "这是一段包含 spam 关键词的测试内容");

        ExtractedMemory memory = extractor.extract(task, MemoryType.SEMANTIC);

        assertThat(memory).as("命中黑名单关键词应被过滤").isNull();
    }

    @Test
    @DisplayName("extractFromTaskResult 应过滤低质量内容并返回空列表")
    void should_ReturnEmpty_When_ContentFiltered() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(defaultRule());
        TaskResult task = new TaskResult("tk_filtered", TaskOutcome.SUCCESS, "短内容");

        List<ExtractedMemory> results = extractor.extractFromTaskResult(task);

        assertThat(results).as("内容被过滤后应返回空列表").isEmpty();
    }

    @Test
    @DisplayName("extract 应允许长度足够且无黑名单关键词的内容")
    void should_AllowContent_When_PassesFilter() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl(defaultRule());
        TaskResult task = new TaskResult("tk_ok", TaskOutcome.SUCCESS, "这是一个足够长的合法任务目标内容用于测试过滤逻辑");

        ExtractedMemory memory = extractor.extract(task, MemoryType.SEMANTIC);

        assertThat(memory).as("合法内容不应被过滤").isNotNull();
        assertThat(memory.getFact()).isEqualTo("这是一个足够长的合法任务目标内容用于测试过滤逻辑");
    }

    // ============ MemoryExtractRule 单元测试 ============

    @Test
    @DisplayName("MemoryExtractRule 应正确解析黑名单关键词")
    void should_ParseBlacklistKeywords_When_CsvProvided() {
        MemoryExtractRule rule = new MemoryExtractRule(10, "spam, ads,  junk ");

        assertThat(rule.getMinContentLength()).isEqualTo(10);
        assertThat(rule.getBlacklistKeywords()).containsExactlyInAnyOrder("spam", "ads", "junk");
    }

    @Test
    @DisplayName("MemoryExtractRule 对空 CSV 应返回空集合")
    void should_ReturnEmptySet_When_CsvEmpty() {
        MemoryExtractRule rule = new MemoryExtractRule(20, "");

        assertThat(rule.getBlacklistKeywords()).isEmpty();
    }
}
