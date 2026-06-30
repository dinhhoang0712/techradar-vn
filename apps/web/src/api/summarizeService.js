import { apiClient } from '../utils/apiClient';

// POST /chat/summarize — PUBLIC (không cần JWT)
export const summarizeTech = async (techName, period = null, format = 'paragraph') => {
    return await apiClient('/chat/summarize', {
        method: 'POST',
        body: JSON.stringify({ tech_name: techName, period, format }),
    });
};
