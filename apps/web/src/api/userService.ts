import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';
import type { UserProfileData, UpdateProfilePayload, UserDataExport } from '../types/userProfile';

/**
 * Lấy thông tin profile của người dùng hiện tại.
 * Endpoint: GET /user/profile
 * Yêu cầu: Bearer token hợp lệ trong header Authorization.
 */
export const getUserProfile = async (): Promise<ApiResponse<UserProfileData> | UserProfileData> => {
    return await apiClient('/user/profile', {
        method: 'GET',
    });
};

/**
 * Cập nhật thông tin profile của người dùng hiện tại.
 * Endpoint: PUT /user/profile
 * Yêu cầu: Bearer token hợp lệ trong header Authorization.
 */
export const updateUserProfile = async (profileData: UpdateProfilePayload): Promise<ApiResponse<UserProfileData> | UserProfileData> => {
    return await apiClient('/user/profile', {
        method: 'PUT',
        body: JSON.stringify(profileData),
    });
};

/**
 * Upload avatar (base64). Trả về { data: { avatar_url } }.
 * Endpoint: POST /user/avatar
 */
export const uploadAvatar = async (contentType: string, dataBase64: string): Promise<ApiResponse<{ avatar_url: string }>> => {
    return await apiClient('/user/avatar', {
        method: 'POST',
        body: JSON.stringify({ content_type: contentType, data_base64: dataBase64 }),
    });
};

/**
 * Xuất toàn bộ dữ liệu cá nhân (GDPR data portability): tài khoản, hồ sơ, bài viết, bình luận.
 * Endpoint: GET /user/data-export
 */
export const exportUserData = async (): Promise<ApiResponse<UserDataExport>> => {
    return await apiClient('/user/data-export', {
        method: 'GET',
    });
};

/**
 * Xóa vĩnh viễn tài khoản hiện tại (GDPR right to erasure). Yêu cầu xác nhận mật khẩu hiện tại.
 * Endpoint: DELETE /user/account
 */
export const deleteAccount = async (currentPassword: string): Promise<ApiResponse<void>> => {
    return await apiClient('/user/account', {
        method: 'DELETE',
        body: JSON.stringify({ current_password: currentPassword }),
    });
};
