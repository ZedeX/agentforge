package com.agent.quality.api.impl;

import com.agent.quality.api.BadcaseWriter;
import com.agent.quality.model.BadcaseRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Badcase 写入器内存实现 (doc 11-detail-flow F9, PRD §四(二)4).
 *
 * <p>骨架阶段策略：使用 {@link CopyOnWriteArrayList} 在内存中保存 Badcase 记录,
 * 供离线分析、人工审核队列消费及测试读取。生产环境由 {@link JpaBadcaseWriterImpl} 替代。</p>
 *
 * <p>write() 仅做最小合法性校验（null 跳过、badcaseId 缺失时自动补建），
 * 不在此处直接决定是否推送人工审核队列 —— 该决策属于编排层职责。</p>
 *
 * <p>本实现用于测试场景（不依赖 Spring 容器 / JPA），
 * 生产 Spring 容器中由 {@link JpaBadcaseWriterImpl}（@Primary）注入。</p>
 */
@Component("inMemoryBadcaseWriter")
public class BadcaseWriterImpl implements BadcaseWriter {

    private static final Logger log = LoggerFactory.getLogger(BadcaseWriterImpl.class);

    /** 内存存储: 线程安全的 CopyOnWriteArrayList, 读多写少场景. */
    private final List<BadcaseRecord> store = new CopyOnWriteArrayList<>();

    @Override
    public void write(BadcaseRecord record) {
        if (record == null) {
            log.warn("Badcase 写入收到空记录, 跳过");
            return;
        }
        if (record.getBadcaseId() == null || record.getBadcaseId().isBlank()) {
            String generated = "bc-" + System.nanoTime();
            record.setBadcaseId(generated);
            log.debug("Badcase 缺失 badcaseId, 自动生成: {}", generated);
        }
        store.add(record);
        log.info("Badcase 写入成功: badcaseId={}, taskId={}, category={}, severity={}",
                record.getBadcaseId(), record.getTaskId(), record.getCategory(), record.getSeverity());
    }

    /** 返回不可变副本, 供测试 / 离线读取使用. */
    public List<BadcaseRecord> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(store));
    }

    /** 按 badcaseId 查找, 未命中返回 null. */
    public BadcaseRecord findById(String badcaseId) {
        if (badcaseId == null) {
            return null;
        }
        return store.stream()
                .filter(r -> badcaseId.equals(r.getBadcaseId()))
                .findFirst()
                .orElse(null);
    }

    /** 当前内存中存储的 Badcase 数量. */
    public int size() {
        return store.size();
    }

    /** 清空内存存储 (仅供测试使用). */
    public void clear() {
        store.clear();
    }
}
