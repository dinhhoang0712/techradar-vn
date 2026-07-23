import { describe, it, expect } from 'vitest';
import { normalizeSession, normalizeSessions, sortSessionsNewestFirst, formatTime } from './chatSession';

describe('normalizeSession', () => {
    it('returns null when neither session_id nor id is present', () => {
        expect(normalizeSession({})).toBe(null);
        expect(normalizeSession(null)).toBe(null);
    });

    it('prefers session_id over id, defaults title and created_at', () => {
        const result = normalizeSession({ session_id: 'abc' });
        expect(result!.id).toBe('abc');
        expect(result!.session_id).toBe('abc');
        expect(result!.title).toBe('Cuộc trò chuyện mới');
        expect(result!.created_at).toBeTruthy();
    });

    it('keeps an explicit title/created_at', () => {
        const result = normalizeSession({ id: 'x', title: 'Hello', created_at: '2026-01-01T00:00:00Z' });
        expect(result!.title).toBe('Hello');
        expect(result!.created_at).toBe('2026-01-01T00:00:00Z');
    });
});

describe('sortSessionsNewestFirst', () => {
    it('sorts descending by created_at', () => {
        const sessions = [
            { id: 'old', created_at: '2026-01-01T00:00:00Z' },
            { id: 'new', created_at: '2026-01-02T00:00:00Z' },
        ];
        expect(sessions.sort(sortSessionsNewestFirst).map(s => s.id)).toEqual(['new', 'old']);
    });
});

describe('normalizeSessions', () => {
    it('accepts either a bare array or a {data: [...]} payload', () => {
        const bare = normalizeSessions([{ id: 'a' }]);
        expect(bare).toHaveLength(1);

        const wrapped = normalizeSessions({ data: [{ id: 'b' }] });
        expect(wrapped).toHaveLength(1);
    });

    it('drops entries with no id and sorts the rest newest-first', () => {
        const result = normalizeSessions([
            { id: 'old', created_at: '2026-01-01T00:00:00Z' },
            {},
            { id: 'new', created_at: '2026-01-02T00:00:00Z' },
        ]);
        expect(result.map(s => s.id)).toEqual(['new', 'old']);
    });
});

describe('formatTime', () => {
    it('returns an empty string for a falsy input', () => {
        expect(formatTime(null)).toBe('');
        expect(formatTime('')).toBe('');
    });

    it('formats recent timestamps as relative time', () => {
        const now = new Date();
        expect(formatTime(now.toISOString())).toBe('vừa xong');

        const fiveMinAgo = new Date(now.getTime() - 5 * 60000);
        expect(formatTime(fiveMinAgo.toISOString())).toBe('5 phút trước');

        const threeHoursAgo = new Date(now.getTime() - 3 * 3600000);
        expect(formatTime(threeHoursAgo.toISOString())).toBe('3 giờ trước');
    });

    it('falls back to a locale date string for timestamps older than a day', () => {
        const twoDaysAgo = new Date(Date.now() - 2 * 86400000);
        expect(formatTime(twoDaysAgo.toISOString())).toBe(twoDaysAgo.toLocaleDateString('vi-VN'));
    });
});
