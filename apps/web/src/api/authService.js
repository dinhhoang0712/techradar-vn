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

export const forgotPassword = async (email) => {
    return await apiClient('/auth/forgot-password', {
        method: 'POST',
        body: JSON.stringify({ email })
    });
};

export const resetPassword = async (token, newPassword) => {
    return await apiClient('/auth/reset-password', {
        method: 'POST',
        body: JSON.stringify({ token, new_password: newPassword })
    });
};

// Export tên hàm mock cũ để duy trì tương thích tạm thời
export const loginMock = loginUser;
export const registerMock = registerUser;
