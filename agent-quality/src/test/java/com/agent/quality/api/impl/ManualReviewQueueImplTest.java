package com.agent.quality.api.impl;

import com.agent.quality.enums.BadcaseSeverity;
import com.agent.quality.model.ManualReviewItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ManualReviewQueueImpl} 单元测试。
 */
class ManualReviewQueueImplTest {

    private final ManualReviewQueueImpl queue = new ManualReviewQueueImpl();

    @Test
    @DisplayName("push 多个条目后 pop: 按 FIFO 顺序返回")
    void should_PopInFifoOrder_When_MultipleItemsPushed() {
        ManualReviewItem first = new ManualReviewItem("bc-001", BadcaseSeverity.HIGH);
        ManualReviewItem second = new ManualReviewItem("bc-002", BadcaseSeverity.HIGH);

        queue.push(first);
        queue.push(second);

        assertThat(queue.size()).isEqualTo(2);

        ManualReviewItem popped1 = queue.pop();
        ManualReviewItem popped2 = queue.pop();

        assertThat(popped1).isNotNull();
        assertThat(popped1.getBadcaseId()).isEqualTo("bc-001");
        assertThat(popped2).isNotNull();
        assertThat(popped2.getBadcaseId()).isEqualTo("bc-002");
        assertThat(queue.size()).isZero();
    }

    @Test
    @DisplayName("队列为空时 pop: 返回 null")
    void should_ReturnNull_When_QueueEmpty() {
        assertThat(queue.size()).isZero();

        ManualReviewItem popped = queue.pop();

        assertThat(popped).isNull();
        assertThat(queue.peek()).isNull();
    }

    @Test
    @DisplayName("push null 条目: 跳过, 队列保持空")
    void should_Skip_When_ItemIsNull() {
        queue.push(null);

        assertThat(queue.size()).isZero();
        assertThat(queue.pop()).isNull();
    }

    @Test
    @DisplayName("push 条目时未设置入队时间: 自动填充 enqueuedAt")
    void should_AutoFillEnqueuedAt_When_Missing() {
        ManualReviewItem item = new ManualReviewItem();
        item.setBadcaseId("bc-100");
        item.setSeverity(BadcaseSeverity.HIGH);
        // 不设置 enqueuedAt, 触发自动填充逻辑

        queue.push(item);

        ManualReviewItem peeked = queue.peek();
        assertThat(peeked).isNotNull();
        assertThat(peeked.getEnqueuedAt()).isNotNull();
    }
}
