import { apiClient } from '../utils/apiClient';

/**
 * Lấy danh sách công ty kèm tech stack suy ra từ tin tuyển dụng.
 * Endpoint: GET /companies
 * @param {{q?: string, page?: number, size?: number}} [opts] q: lọc theo tên hoặc tech stack (không phân biệt hoa/thường)
 */
export const getCompanies = async ({ q, page, size } = {}) => {
    const params = new URLSearchParams();
    if (q) params.set('q', q);
    if (page != null) params.set('page', page);
    if (size != null) params.set('size', size);
    const query = params.toString();
    return await apiClient(`/companies${query ? `?${query}` : ''}`, {
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

/**
 * Lấy tin tức nhắc đến công ty (quan hệ Article-[:MENTIONS]->Company).
 * Endpoint: GET /companies/{id}/mentions
 * @param {string} companyId
 * @param {number} [limit]
 */
export const getCompanyMentions = async (companyId, limit) => {
    const params = new URLSearchParams();
    if (limit != null) params.set('limit', limit);
    const query = params.toString();
    return await apiClient(`/companies/${encodeURIComponent(companyId)}/mentions${query ? `?${query}` : ''}`, {
        method: 'GET',
    });
};

/**
 * Sinh nhận định AI ngắn gọn về một công ty (tech stack, quy mô tuyển dụng...).
 * Endpoint: POST /company-insight
 * @param {string} companyName
 */
export const getCompanyInsight = async (companyName) => {
    return await apiClient('/company-insight', {
        method: 'POST',
        body: JSON.stringify({ company_name: companyName }),
    });
};
