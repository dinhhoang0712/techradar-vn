import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';

export interface PublicStats {
    companies?: number;
    jobs?: number;
    users?: number;
    [key: string]: unknown;
}

// GET /stats/public — real companies/jobs/users counts, public (no auth), for marketing chips.
export const getPublicStats = async (): Promise<ApiResponse<PublicStats> | PublicStats> => {
    return await apiClient('/stats/public');
};
