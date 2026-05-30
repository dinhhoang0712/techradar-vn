import { apiClient } from '../utils/apiClient';

/**
 * Lấy thông tin profile của người dùng hiện tại.
 * Endpoint: GET /user/profile
 * Yêu cầu: Bearer token hợp lệ trong header Authorization.
 * @returns {Promise<Object>} Profile data của user hiện tại.
 */
export const getUserProfile = async () => {
    return await apiClient('/user/profile', {
        method: 'GET',
    });
};

/**
 * Cập nhật thông tin profile của người dùng hiện tại.
 * Endpoint: PUT /user/profile
 * Yêu cầu: Bearer token hợp lệ trong header Authorization.
 * @param {Object} profileData - Dữ liệu profile cần cập nhật.
 * @param {string} [profileData.full_name] - Họ tên đầy đủ.
 * @param {string} [profileData.bio] - Giới thiệu.
 * @param {string} [profileData.job_role] - Vai trò.
 * @param {string} [profileData.location] - Địa điểm.
 * @param {string} [profileData.password] - Mật khẩu.
 * @param {string[]} [profileData.technologies] - Danh sách công nghệ.
 * @returns {Promise<Object>} Profile data sau khi cập nhật.
 */
export const updateUserProfile = async (profileData) => {
    return await apiClient('/user/profile', {
        method: 'PUT',
        body: JSON.stringify(profileData),
    });
};
