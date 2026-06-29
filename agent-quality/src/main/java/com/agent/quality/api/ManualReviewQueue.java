package com.agent.quality.api;

import com.agent.quality.model.ManualReviewItem;

/**
 * 人工审核队列 (doc 11-detail-flow F9, PRD §四(二)4).
 *
 * <p>高严重度 Badcase（severityScore ≥ 0.8）推送至人工审核队列，
 * 由审核人员领取后填写 reviewResult 回写.</p>
 */
public interface ManualReviewQueue {

    /**
     * 推送一条人工审核条目到队列尾部.
     *
     * @param item 人工审核条目（含 badcaseId / severity）
     */
    void push(ManualReviewItem item);

    /**
     * 从队列头部弹出一条人工审核条目供审核人员领取.
     *
     * @return 人工审核条目；队列空时返回 null
     */
    ManualReviewItem pop();
}
