package com.agent.memory.api.impl;

import com.agent.memory.enums.MemoryType;
import com.agent.memory.enums.TaskOutcome;
import com.agent.memory.model.ExtractedMemory;
import com.agent.memory.model.TaskResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryExtractorImplTest {

    @Test
    @DisplayName("extract 对 EPISODIC 类型应提取步骤序列并设置 source 为 taskId")
    void should_ExtractStepSequence_When_TypeIsEpisodic() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl();
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
        MemoryExtractorImpl extractor = new MemoryExtractorImpl();
        TaskResult task = new TaskResult("tk_002", TaskOutcome.SUCCESS, "用户偏好：简体中文");

        ExtractedMemory memory = extractor.extract(task, MemoryType.SEMANTIC);

        assertThat(memory.getFact()).isEqualTo("用户偏好：简体中文");
        assertThat(memory.getStepSequence()).isEmpty();
    }

    @Test
    @DisplayName("extract 对 PROCEDURAL 类型应取首步作为 patternTemplate")
    void should_ExtractFirstStepAsPattern_When_TypeIsProcedural() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl();
        TaskResult task = new TaskResult("tk_003", TaskOutcome.SUCCESS, "目标");
        task.setSteps(Arrays.asList("打开浏览器", "输入URL", "提交表单"));

        ExtractedMemory memory = extractor.extract(task, MemoryType.PROCEDURAL);

        assertThat(memory.getPatternTemplate()).isEqualTo("打开浏览器");
    }

    @Test
    @DisplayName("extract 对 null TaskResult 应返回 null")
    void should_ReturnNull_When_TaskResultIsNull() {
        MemoryExtractorImpl extractor = new MemoryExtractorImpl();
        ExtractedMemory memory = extractor.extract(null, MemoryType.EPISODIC);
        assertThat(memory).isNull();
    }
}