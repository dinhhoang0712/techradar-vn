import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';
import type { TechSummary } from '../types/summarize';

// POST /chat/summarize — PUBLIC (không cần JWT)
export const summarizeTech = async (techName: string, period: string | null = null, format = 'paragraph'): Promise<ApiResponse<TechSummary> | TechSummary> => {
    return await apiClient('/chat/summarize', {
        method: 'POST',
        body: JSON.stringify({ tech_name: techName, period, format }),
    });
};
