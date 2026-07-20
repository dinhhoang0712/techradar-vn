package com.techpulse.techradar.shared.paging;

/**
 * Clamped page size and computed row offset shared by every paginated use case.
 * Each call site keeps its own default/max size (they legitimately differ across features),
 * but the clamp-and-offset arithmetic is centralized here so it can't drift or be forgotten
 * (e.g. a missing {@code Math.max} on a negative page) between call sites.
 */
public record PageRequest(int size, int offset) {

    public static PageRequest of(int page, int size, int defaultSize, int maxSize) {
        int effectiveSize = size <= 0 ? defaultSize : Math.min(size, maxSize);
        int offset = Math.max(page, 0) * effectiveSize;
        return new PageRequest(effectiveSize, offset);
    }
}
