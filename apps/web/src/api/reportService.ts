import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';
import type { ReportResult } from '../types/report';

// GET /report?period=X&topN=10&format=markdown — PUBLIC (không cần JWT)
export const generateReport = async (period: string, topN = 10, format = 'markdown'): Promise<ApiResponse<ReportResult> | ReportResult> => {
    const params = new URLSearchParams({ period, topN: String(topN), format });
    return await apiClient(`/report?${params.toString()}`, { method: 'GET' });
};
