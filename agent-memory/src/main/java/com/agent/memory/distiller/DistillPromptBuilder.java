package com.agent.memory.distiller;

import com.agent.memory.model.MemoryRecord;

import java.util.List;

/**
 * Distillation prompt builder (Plan 03 T4).
 *
 * <p>Constructs the system + user prompts sent to the model gateway for
 * memory distillation. System prompt defines the distillation task
 * (summarize N ACTIVE memories into a single &lt;= 500 char summary).
 * User prompt concatenates all source memory contents.
 */
public final class DistillPromptBuilder {

    /** Maximum summary length in characters (doc 04-memory §6.3). */
    public static final int MAX_SUMMARY_LENGTH = 500;

    private DistillPromptBuilder() {
    }

    /**
     * Build the system prompt that defines the distillation task.
     *
     * @param fragmentCount number of source memories being distilled
     * @return system prompt string
     */
    public static String buildSystemPrompt(int fragmentCount) {
        return String.format(
                "你是一个记忆蒸馏助手。请将以下 %d 条 ACTIVE 记忆蒸馏为 1 条不超过 %d 字的摘要。" +
                        "摘要应保留关键事实、人物、时间、事件，去除冗余细节。直接输出摘要文本，不要添加额外说明。",
                fragmentCount, MAX_SUMMARY_LENGTH);
    }

    /**
     * Build the user prompt that contains all source memory contents.
     *
     * @param records source ACTIVE memories
     * @return user prompt string with each record's content on a numbered line
     */
    public static String buildUserPrompt(List<MemoryRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("需要蒸馏的记忆内容如下：\n");
        for (int i = 0; i < records.size(); i++) {
            sb.append(i + 1).append(". ").append(records.get(i).getContent()).append('\n');
        }
        return sb.toString();
    }
}
