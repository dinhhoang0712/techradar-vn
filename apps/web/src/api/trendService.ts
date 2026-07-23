import { apiClient } from '../utils/apiClient';
import { openSseStream } from '../utils/sseStream';
import type { ApiResponse } from '../types/api';
import type { RadarTop4Item, RadarTop10Item, RadarSearchResponse, RadarSnapshotEvent } from '../types/trend';

// GET /radar/top4 — Không có tham số
export const getRadarTop4 = async (): Promise<ApiResponse<RadarTop4Item[]>> => {
    return await apiClient('/radar/top4', { method: 'GET' });
};

// GET /radar/top10 — Không có tham số
export const getRadarTop10 = async (): Promise<ApiResponse<RadarTop10Item[]>> => {
    return await apiClient('/radar/top10', { method: 'GET' });
};

// GET /radar/search — keywords: array[string], months: integer (default 6)
export const getRadarSearch = async (keywords: string[] = [], months = 6): Promise<RadarSearchResponse> => {
    const params = new URLSearchParams();
    keywords.forEach(kw => params.append('keywords', kw));
    params.append('months', String(months));
    return await apiClient(`/radar/search?${params.toString()}`, { method: 'GET' });
};

// GET /radar/stream — SSE: đẩy snapshot top4/top10 mới ngay khi ETL rebuild xong ở backend,
// để TrendDashboard cập nhật số liệu real-time thay vì phải đợi F5.
// Trả về AbortController; gọi .abort() khi unmount để đóng stream.
export const streamRadar = (
    onSnapshot: (snapshot: RadarSnapshotEvent) => void,
    onError?: (err: Error) => void,
): AbortController => {
    return openSseStream('/radar/stream', (data) => onSnapshot(data as RadarSnapshotEvent), onError);
};
