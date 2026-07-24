import { apiClient, API_BASE_URL } from '../utils/apiClient';
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

// GET /radar/export-png|export-csv — server-rendered "Top N công nghệ theo tăng trưởng" export,
// khác với nút Export PNG/CSV hiện có trên TrendDashboard (vốn xuất ảnh chụp/dữ liệu của đúng
// biểu đồ đang hiển thị). apiClient parse JSON nên không dùng lại được cho response nhị phân này.
const downloadRadarExport = async (kind: 'png' | 'csv', limit: number): Promise<void> => {
    const path = kind === 'png' ? 'export-png' : 'export-csv';
    const token = localStorage.getItem('access_token');
    const headers: Record<string, string> = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const response = await fetch(`${API_BASE_URL}/radar/${path}?limit=${limit}`, { headers });
    if (!response.ok) {
        throw new Error(`Xuất ${kind.toUpperCase()} thất bại (HTTP ${response.status})`);
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `radar_top${limit}.${kind}`;
    a.click();
    URL.revokeObjectURL(url);
};

export const downloadRadarTopPng = (limit = 20): Promise<void> => downloadRadarExport('png', limit);
export const downloadRadarTopCsv = (limit = 50): Promise<void> => downloadRadarExport('csv', limit);
