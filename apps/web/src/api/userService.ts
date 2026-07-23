import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';
import type { UserProfileData, UpdateProfilePayload } from '../types/userProfile';

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
