import { useCallback, useState } from 'react';
import type { ReportHistoryEntry, ReportResult } from '../types/report';

const STORAGE_KEY = 'techradar.reportHistory';
const MAX_ENTRIES = 10;

function readStorage(): ReportHistoryEntry[] {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (!raw) return [];
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
}

function writeStorage(entries: ReportHistoryEntry[]) {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
    } catch {
        // localStorage đầy/bị chặn (private mode) — lịch sử chỉ là tiện ích, bỏ qua lỗi.
    }
}

// Lưu các báo cáo đã tạo trên trình duyệt để xem lại mà không phải gọi LLM lại (tốn tiền + thời gian).
// Không đồng bộ nhiều tab qua storage event vì đây chỉ là tiện ích cá nhân, không phải nguồn dữ liệu chính.
export function useReportHistory() {
    const [entries, setEntries] = useState<ReportHistoryEntry[]>(() => readStorage());

    const addEntry = useCallback((period: string, topN: number, result: ReportResult) => {
        setEntries(prev => {
            const next: ReportHistoryEntry[] = [
                { id: `${Date.now()}-${period}`, period, topN, savedAt: new Date().toISOString(), result },
                ...prev.filter(e => !(e.period === period && e.topN === topN)),
            ].slice(0, MAX_ENTRIES);
            writeStorage(next);
            return next;
        });
    }, []);

    const removeEntry = useCallback((id: string) => {
        setEntries(prev => {
            const next = prev.filter(e => e.id !== id);
            writeStorage(next);
            return next;
        });
    }, []);

    const clearHistory = useCallback(() => {
        setEntries([]);
        writeStorage([]);
    }, []);

    return { entries, addEntry, removeEntry, clearHistory };
}
