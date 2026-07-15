import { apiClient } from '../utils/apiClient';

// POST /agent — body: { query }. user_id is attached server-side from the JWT.
// Response: { answer, steps: [{ tool, input, output }] }
export const runAgent = async (query) => {
    return await apiClient('/agent', {
        method: 'POST',
        body: JSON.stringify({ query }),
    });
};
