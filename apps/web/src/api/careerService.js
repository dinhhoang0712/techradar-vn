import { apiClient } from '../utils/apiClient';

// POST /career — user_id tự động inject từ JWT trong Spring Boot
export const getCareerAdvice = async (targetRole, currentSkills = []) => {
    return await apiClient('/career', {
        method: 'POST',
        body: JSON.stringify({ target_role: targetRole, current_skills: currentSkills }),
    });
};
