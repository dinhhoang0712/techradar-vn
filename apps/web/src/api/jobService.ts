import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';
import type { JobMatch } from '../types/career';

interface GetJobMatchesFilters {
    location?: string;
    minSalary?: number;
    level?: string;
    limit?: number;
}

/**
 * Lấy danh sách job phù hợp với kỹ năng trong hồ sơ người dùng hiện tại.
 * Endpoint: GET /jobs/matches
 * Yêu cầu: Bearer token hợp lệ trong header Authorization.
 */
export const getJobMatches = async ({ location, minSalary, level, limit }: GetJobMatchesFilters = {}): Promise<ApiResponse<JobMatch[]> | JobMatch[]> => {
    const params = new URLSearchParams();
    if (location) params.set('location', location);
    if (minSalary != null) params.set('min_salary', String(minSalary));
    if (level) params.set('level', level);
    if (limit != null) params.set('limit', String(limit));
    const query = params.toString();
    return await apiClient(`/jobs/matches${query ? `?${query}` : ''}`, {
        method: 'GET',
    });
};
