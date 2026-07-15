import { apiClient } from '../utils/apiClient';

// POST /career — targetRole: string, currentSkills: array[string]
export const getCareerAdvice = async (targetRole, currentSkills = []) => {
    return await apiClient('/career', {
        method: 'POST',
        body: JSON.stringify({ target_role: targetRole, current_skills: currentSkills }),
    });
};
