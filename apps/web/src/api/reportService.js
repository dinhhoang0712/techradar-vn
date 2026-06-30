import { apiClient } from '../utils/apiClient';

// GET /report?period=X&topN=10&format=markdown — PUBLIC (không cần JWT)
export const generateReport = async (period, topN = 10, format = 'markdown') => {
    const params = new URLSearchParams({ period, topN, format });
    return await apiClient(`/report?${params.toString()}`, { method: 'GET' });
};
