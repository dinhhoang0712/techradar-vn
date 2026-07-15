import { apiClient } from '../utils/apiClient';

/**
 * Lấy danh sách job phù hợp với kỹ năng trong hồ sơ người dùng hiện tại.
 * Endpoint: GET /jobs/matches
 * Yêu cầu: Bearer token hợp lệ trong header Authorization.
 * @param {Object} [filters]
 * @param {string} [filters.location] - Lọc theo địa điểm (chứa chuỗi, không phân biệt hoa thường).
 * @param {number} [filters.minSalary] - Lương tối thiểu (triệu VND).
 * @param {number} [filters.limit] - Số lượng job trả về (mặc định 20).
 * @returns {Promise<Object>} ApiResponse chứa danh sách job đã xếp hạng theo score.
 */
export const getJobMatches = async ({ location, minSalary, limit } = {}) => {
    const params = new URLSearchParams();
    if (location) params.set('location', location);
    if (minSalary != null) params.set('min_salary', minSalary);
    if (limit != null) params.set('limit', limit);
    const query = params.toString();
    return await apiClient(`/jobs/matches${query ? `?${query}` : ''}`, {
        method: 'GET',
    });
};
