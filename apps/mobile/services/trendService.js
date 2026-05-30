import { apiClient } from '../utils/apiClient';

export const getRadarTop4 = async () => {
    return await apiClient('/radar/top4', { method: 'GET' });
};

export const getRadarTop10 = async () => {
    return await apiClient('/radar/top10', { method: 'GET' });
};

export const getRadarSearch = async (keywords = [], months = 6) => {
    // Lưu ý: React Native fetch không hỗ trợ tự động xử lý mảng trong URLSearchParams một cách đồng nhất
    // Chúng ta sẽ tự xây dựng query string
    let queryString = `months=${months}`;
    if (keywords && keywords.length > 0) {
        const keywordParams = keywords.map(kw => `keywords=${encodeURIComponent(kw)}`).join('&');
        queryString += `&${keywordParams}`;
    }
    
    return await apiClient(`/radar/search?${queryString}`, { method: 'GET' });
};

export const fetchTrends = getRadarTop4;
