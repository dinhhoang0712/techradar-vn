import { apiClient } from '../utils/apiClient';

/**
 * Lấy danh sách công ty kèm tech stack suy ra từ tin tuyển dụng.
 * Endpoint: GET /companies
 */
export const getCompanies = async () => {
    return await apiClient('/companies', {
        method: 'GET',
    });
};

/**
 * Lấy danh sách công ty có tech stack tương tự (Jaccard similarity).
 * Endpoint: GET /companies/{id}/similar
 * @param {string} companyId
 * @param {number} [limit]
 */
export const getSimilarCompanies = async (companyId, limit) => {
    const params = new URLSearchParams();
    if (limit != null) params.set('limit', limit);
    const query = params.toString();
    return await apiClient(`/companies/${encodeURIComponent(companyId)}/similar${query ? `?${query}` : ''}`, {
        method: 'GET',
    });
};
