import { describe, it, expect } from 'vitest';
import { formatDateTime, formatShortDate, computePipelineSuccessRate } from './adminDashboardFormat';

describe('formatDateTime / formatShortDate', () => {
    it('falls back for missing or invalid input', () => {
        expect(formatDateTime(null)).toBe('Chưa có dữ liệu');
        expect(formatDateTime('not-a-date')).toBe('Chưa có dữ liệu');
        expect(formatShortDate(null)).toBe('');
        expect(formatShortDate('not-a-date')).toBe('');
    });

    it('formats a valid ISO date', () => {
        const iso = '2026-03-05T10:00:00Z';
        expect(formatDateTime(iso)).toBe(new Date(iso).toLocaleString('vi-VN'));
        expect(formatShortDate(iso)).toBe(new Date(iso).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' }));
    });
});

describe('computePipelineSuccessRate', () => {
    it('returns 0 when pipeline is missing', () => {
        expect(computePipelineSuccessRate(null)).toBe(0);
    });

    it('returns 100 when there is no processed/failed activity at all', () => {
        expect(computePipelineSuccessRate({})).toBe(100);
    });

    it('computes processed / (processed + failed) as a percentage', () => {
        const rate = computePipelineSuccessRate({
            articles_processed: 80, jobs_processed: 10, articles_failed: 5, jobs_failed: 5,
        });
        expect(rate).toBe(90);
    });
});
