import { apiClient } from '../utils/apiClient';

export const exploreGraph = async (keywords = [], depth = 1) => {
    let queryString = `depth=${depth}`;
    if (keywords && keywords.length > 0) {
        const keywordParams = keywords.map(kw => `keywords=${encodeURIComponent(kw)}`).join('&');
        queryString += `&${keywordParams}`;
    }
    return await apiClient(`/graph/explore?${queryString}`, { method: 'GET' });
};

export const fetchGraphData = exploreGraph;

export const analyzeRoad = async (from, to) => {
    const fromEnc = encodeURIComponent(from);
    const toEnc = encodeURIComponent(to);
    return await apiClient(`/graph/road_analysis?from=${fromEnc}&to=${toEnc}`, { method: 'GET' });
};
