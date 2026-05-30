import { apiClient } from '../utils/apiClient';

export const getCompareSearch = async (keywords = [], months = 6) => {
    let queryString = `months=${months}`;
    if (keywords && keywords.length > 0) {
        const keywordParams = keywords.map(kw => `keywords=${encodeURIComponent(kw)}`).join('&');
        queryString += `&${keywordParams}`;
    }
    return await apiClient(`/compare/search?${queryString}`, { method: 'GET' });
};

export const fetchCompareData = async (entity1, entity2) => {
    return await getCompareSearch([entity1, entity2]);
};
