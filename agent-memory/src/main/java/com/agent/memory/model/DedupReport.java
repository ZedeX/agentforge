package com.agent.memory.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 去重报告（Plan 03 T9）。
 *
 * <ul>
 *   <li>dropped — 完全相同 hash 被丢弃的数量</li>
 *   <li>merged — 余弦 ≥ cosineHigh 被合并的数量</li>
 *   <li>related — 余弦 cosineLow~cosineHigh 被标记关联的数量</li>
 *   <li>kept — 保留未变的数量</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
public class DedupReport {

    private int dropped;
    private int merged;
    private int related;
    private int kept;

    public DedupReport(int dropped, int merged, int related, int kept) {
        this.dropped = dropped;
        this.merged = merged;
        this.related = related;
        this.kept = kept;
    }

    /** 去重处理的总量。 */
    public int total() {
        return dropped + merged + related + kept;
    }

    @Override
    public String toString() {
        return String.format("DedupReport{dropped=%d, merged=%d, related=%d, kept=%d, total=%d}",
                dropped, merged, related, kept, total());
    }
}
