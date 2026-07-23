import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';

export interface RecommendationItem {
    tech_name: string;
    [key: string]: unknown;
}

export interface RecommendationResult {
    recommendations: RecommendationItem[];
    based_on?: string[];
}

// POST /recommend — user_id tự động inject từ JWT trong Spring Boot
export const getRecommendations = async (currentTechs: string[] = [], limit = 10): Promise<ApiResponse<RecommendationResult> | RecommendationResult> => {
    return await apiClient('/recommend', {
        method: 'POST',
        body: JSON.stringify({ current_techs: currentTechs, limit }),
    });
};
