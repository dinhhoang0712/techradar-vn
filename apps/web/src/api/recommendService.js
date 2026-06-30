import { apiClient } from '../utils/apiClient';

// POST /recommend — user_id tự động inject từ JWT trong Spring Boot
export const getRecommendations = async (currentTechs = [], limit = 10) => {
    return await apiClient('/recommend', {
        method: 'POST',
        body: JSON.stringify({ current_techs: currentTechs, limit }),
    });
};
