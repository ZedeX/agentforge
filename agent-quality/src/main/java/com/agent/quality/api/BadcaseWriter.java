package com.agent.quality.api;

import com.agent.quality.model.BadcaseRecord;

/**
 * Badcase 写入器 (doc 11-detail-flow F9, PRD §四(二)4).
 *
 * <p>L4 校验失败且 retry_count &gt; max（MAX_RETRY_EXCEEDED）后，
 * 将失败样本写入 badcase 表用于后续治理（模型微调 / Prompt 优化 / 规则补强）.</p>
 */
public interface BadcaseWriter {

    /**
     * 写入一条 Badcase 记录.
     *
     * @param record Badcase 记录（含 badcaseId / taskId / category / severity / content / failureReason）
     */
    void write(BadcaseRecord record);
}
