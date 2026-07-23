import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';
import type { Company, SimilarCompany, CompanyMention, CompanyInsight, CompanyTechHealthScore } from '../types/company';

interface GetCompaniesOptions {
    q?: string;
    page?: number;
    size?: number;
}

/**
 * Lấy danh sách công ty kèm tech stack suy ra từ tin tuyển dụng.
 * Endpoint: GET /companies
 * q: lọc theo tên hoặc tech stack (không phân biệt hoa/thường)
 */
export const getCompanies = async ({ q, page, size }: GetCompaniesOptions = {}): Promise<ApiResponse<Company[]>> => {
    const params = new URLSearchParams();
    if (q) params.set('q', q);
    if (page != null) params.set('page', String(page));
    if (size != null) params.set('size', String(size));
    const query = params.toString();
    return await apiClient(`/companies${query ? `?${query}` : ''}`, {
        method: 'GET',
    });
};

/**
 * Lấy danh sách công ty có tech stack tương tự (Jaccard similarity).
 * Endpoint: GET /companies/{id}/similar
 */
export const getSimilarCompanies = async (companyId: string, limit?: number): Promise<ApiResponse<SimilarCompany[]>> => {
    const params = new URLSearchParams();
    if (limit != null) params.set('limit', String(limit));
    const query = params.toString();
    return await apiClient(`/companies/${encodeURIComponent(companyId)}/similar${query ? `?${query}` : ''}`, {
        method: 'GET',
    });
};

/**
 * Lấy tin tức nhắc đến công ty (quan hệ Article-[:MENTIONS]->Company).
 * Endpoint: GET /companies/{id}/mentions
 */
export const getCompanyMentions = async (companyId: string, limit?: number): Promise<ApiResponse<CompanyMention[]>> => {
    const params = new URLSearchParams();
    if (limit != null) params.set('limit', String(limit));
    const query = params.toString();
    return await apiClient(`/companies/${encodeURIComponent(companyId)}/mentions${query ? `?${query}` : ''}`, {
        method: 'GET',
    });
};

/**
 * Company Tech Health Score — điểm 0-100 dựa trên xu hướng tăng trưởng nhu cầu (tech_analytics)
 * của các công nghệ trong tech stack suy ra của công ty.
 * Endpoint: GET /companies/{id}/health-score
 */
export const getCompanyTechHealthScore = async (companyId: string): Promise<ApiResponse<CompanyTechHealthScore>> => {
    return await apiClient(`/companies/${encodeURIComponent(companyId)}/health-score`, {
        method: 'GET',
    });
};

/**
 * Sinh nhận định AI ngắn gọn về một công ty (tech stack, quy mô tuyển dụng...).
 * Endpoint: POST /company-insight
 */
export const getCompanyInsight = async (companyName: string): Promise<ApiResponse<CompanyInsight>> => {
    return await apiClient('/company-insight', {
        method: 'POST',
        body: JSON.stringify({ company_name: companyName }),
    });
};
