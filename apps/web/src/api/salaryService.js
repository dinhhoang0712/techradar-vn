import { apiClient } from '../utils/apiClient';

// GET /salary/top — top N techs ranked by median salary
export const getSalaryTop = async (limit = 20, minJobs = 1) => {
    const params = new URLSearchParams({ limit, min_jobs: minJobs });
    return await apiClient(`/salary/top?${params}`, { method: 'GET' });
};

// GET /salary/tech/:techName — salary detail + co-required techs
export const getSalaryByTech = async (techName) => {
    return await apiClient(`/salary/tech/${encodeURIComponent(techName)}`, { method: 'GET' });
};