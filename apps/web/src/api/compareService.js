import { apiClient } from '../utils/apiClient';

// GET /compare/search — keywords: array[string], months: integer (default 6)
export const getCompareSearch = async (keywords = [], months = 6) => {
    const params = new URLSearchParams();
    keywords.forEach(kw => params.append('keywords', kw));
    params.append('months', months);
    return await apiClient(`/compare/search?${params.toString()}`, { method: 'GET' });
};

export const fetchCompareData = async (entity1, entity2) => {
    return await getCompareSearch([entity1, entity2]);
};
