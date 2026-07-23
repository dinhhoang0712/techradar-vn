import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';
import type { CareerAdvice, CareerRoadmap, CareerSimulationResult } from '../types/career';

// POST /career — user_id tự động inject từ JWT trong Spring Boot
export const getCareerAdvice = async (targetRole: string, currentSkills: string[] = []): Promise<ApiResponse<CareerAdvice>> => {
    return await apiClient('/career', {
        method: 'POST',
        body: JSON.stringify({ target_role: targetRole, current_skills: currentSkills }),
    });
};

// GET /career/roadmap — gộp /recommend + /career + /jobs/matches cho user hiện tại trong 1 lệnh gọi.
export const getCareerRoadmap = async (): Promise<ApiResponse<CareerRoadmap>> => {
    return await apiClient('/career/roadmap', { method: 'GET' });
};

// GET /career/simulate — "nếu tôi học công nghệ X thì sao?" (job match, lương, dự báo xu hướng).
export const simulateCareerMove = async (technology: string): Promise<ApiResponse<CareerSimulationResult>> => {
    return await apiClient(`/career/simulate?technology=${encodeURIComponent(technology)}`, { method: 'GET' });
};
