package com.agent.quality.api.impl;

import com.agent.quality.api.BadcaseWriter;
import com.agent.quality.entity.BadcaseRecordEntity;
import com.agent.quality.grpc.QualityMapper;
import com.agent.quality.model.BadcaseRecord;
import com.agent.quality.repository.BadcaseRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Badcase 写入器 JPA 实现（委托给 BadcaseRecordRepository）。
 *
 * <p>与 {@link BadcaseWriterImpl}（内存实现）并存：BadcaseWriterImpl 用于测试，
 * 本实现用于生产环境（@Primary 优先注入）。</p>
 */
@Component
@Primary
public class JpaBadcaseWriterImpl implements BadcaseWriter {

    private static final Logger log = LoggerFactory.getLogger(JpaBadcaseWriterImpl.class);

    private final BadcaseRecordRepository repository;
    private final QualityMapper mapper;

    public JpaBadcaseWriterImpl(BadcaseRecordRepository repository, QualityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

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
        BadcaseRecordEntity entity = mapper.toEntity(record);
        repository.save(entity);
        log.info("Badcase 写入成功(JPA): badcaseId={}, taskId={}, category={}, severity={}",
                record.getBadcaseId(), record.getTaskId(), record.getCategory(), record.getSeverity());
    }
}
