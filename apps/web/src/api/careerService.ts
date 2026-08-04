import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';
import type { CareerAdvice, CareerRoadmap, CareerSimulationResult, LevelMoveSimulationResult } from '../types/career';

// POST /career — user_id tự động inject từ JWT trong Spring Boot. current_level không cần gửi ở
// đây — backend tự tra user_profile.current_level theo user_id khi không có trong request.
export const getCareerAdvice = async (targetRole: string, currentSkills: string[] = [], targetLevel?: string): Promise<ApiResponse<CareerAdvice>> => {
    return await apiClient('/career', {
        method: 'POST',
        body: JSON.stringify({
            target_role: targetRole,
            current_skills: currentSkills,
            ...(targetLevel ? { target_level: targetLevel } : {}),
        }),
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

// GET /career/simulate-level — "nếu tôi lên cấp X thì sao?" (job match, lương ở cấp độ đó).
export const simulateLevelMove = async (targetLevel: string): Promise<ApiResponse<LevelMoveSimulationResult>> => {
    return await apiClient(`/career/simulate-level?target_level=${encodeURIComponent(targetLevel)}`, { method: 'GET' });
};
