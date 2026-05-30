import { apiClient } from '../utils/apiClient';

// GET /radar/top4 — Không có tham số
export const getRadarTop4 = async () => {
    return await apiClient('/radar/top4', { method: 'GET' });
};

// GET /radar/top10 — Không có tham số
export const getRadarTop10 = async () => {
    return await apiClient('/radar/top10', { method: 'GET' });
};

// GET /radar/search — keywords: array[string], months: integer (default 6)
export const getRadarSearch = async (keywords = [], months = 6) => {
    const params = new URLSearchParams();
    keywords.forEach(kw => params.append('keywords', kw));
    params.append('months', months);
    return await apiClient(`/radar/search?${params.toString()}`, { method: 'GET' });
};

export const fetchTrends = getRadarTop4;
