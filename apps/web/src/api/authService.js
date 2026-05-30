import { apiClient } from '../utils/apiClient';

export const loginUser = async (credentials) => {
    return await apiClient('/auth/login', {
        method: 'POST',
        body: JSON.stringify(credentials)
    });
};

export const registerUser = async (userData) => {
    return await apiClient('/auth/register', {
        method: 'POST',
        body: JSON.stringify(userData)
    });
};

export const refreshToken = async (refreshTokenValue) => {
    return await apiClient('/auth/refresh', {
        method: 'POST',
        body: JSON.stringify({ refresh_token: refreshTokenValue })
    });
};

export const logoutUser = async () => {
    return await apiClient('/auth/logout', {
        method: 'POST'
    });
};

export const getCurrentUser = async () => {
    return await apiClient('/auth/me', {
        method: 'GET'
    });
};

export const getSystemStatus = async () => {
    return await apiClient('/status', {
        method: 'GET'
    });
};

// Export tên hàm mock cũ để duy trì tương thích tạm thời
export const loginMock = loginUser;
export const registerMock = registerUser;
