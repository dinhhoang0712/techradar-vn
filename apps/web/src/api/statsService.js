import { apiClient } from '../utils/apiClient';

// GET /stats/public — real companies/jobs/users counts, public (no auth), for marketing chips.
export const getPublicStats = async () => {
    return await apiClient('/stats/public');
};
