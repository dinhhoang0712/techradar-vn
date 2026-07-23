import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';
import type { SalaryTech } from '../types/salary';

// GET /salary/top — top N techs ranked by median salary
export const getSalaryTop = async (limit = 20, minJobs = 1): Promise<ApiResponse<SalaryTech[]>> => {
    const params = new URLSearchParams({ limit: String(limit), min_jobs: String(minJobs) });
    return await apiClient(`/salary/top?${params}`, { method: 'GET' });
};

// GET /salary/tech/:techName — salary detail + co-required techs
export const getSalaryByTech = async (techName: string): Promise<ApiResponse<SalaryTech>> => {
    return await apiClient(`/salary/tech/${encodeURIComponent(techName)}`, { method: 'GET' });
};
