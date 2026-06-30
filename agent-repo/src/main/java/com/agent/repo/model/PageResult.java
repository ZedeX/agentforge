package com.agent.repo.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Paginated result wrapper (skeleton, mirrors agent-common PageResult).
 *
 * <p>Carries the current page items + total count + page/size metadata.</p>
 *
 * @param <T> item type
 */
public class PageResult<T> {

    private final List<T> items;
    private final long total;
    private final int page;
    private final int size;

    public PageResult(List<T> items, long total, int page, int size) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<T> getItems() { return items; }

    public long getTotal() { return total; }

    public int getPage() { return page; }

    public int getSize() { return size; }

    /** Total pages = ceil(total / size), returns 0 if size <= 0. */
    public int getTotalPages() {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / size);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
