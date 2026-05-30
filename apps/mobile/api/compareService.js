import { apiClient } from '../utils/apiClient';

// GET /compare/search — keywords: array[string], months: integer (default 6)
export const getCompareSearch = async (keywords = [], months = 12) => {
    const params = new URLSearchParams();
    keywords.forEach(kw => params.append('keywords', kw));
    params.append('months', months.toString());
    return await apiClient(`/compare/search?${params.toString()}`, { method: 'GET' });
};
