import { apiClient } from '../utils/apiClient';

// Đăng ký tài khoản
export const registerUser = async (userData) => {
    // Endpoint: POST /auth/register
    return await apiClient('/auth/register', {
        method: 'POST',
        body: JSON.stringify(userData)
    });
};

// Đăng nhập
export const loginUser = async (credentials) => {
    // Endpoint: POST /auth/login
    // Payload dạng JSON để đồng nhất với backend Golang/Web
    return await apiClient('/auth/login', {
        method: 'POST',
        body: JSON.stringify(credentials)
    });
};

// Refresh token
export const refreshToken = async (refreshTokenValue) => {
    // Endpoint: POST /auth/refresh
    return await apiClient('/auth/refresh', {
        method: 'POST',
        body: JSON.stringify({ refresh_token: refreshTokenValue })
    });
};

// Đăng xuất
export const logoutUser = async () => {
    // Endpoint: POST /auth/logout
    return await apiClient('/auth/logout', {
        method: 'POST'
    });
};

// Lấy thông tin user hiện tại
export const getCurrentUser = async () => {
    // Endpoint: GET /auth/me
    return await apiClient('/auth/me', {
        method: 'GET'
    });
};

// Lấy trạng thái hệ thống (bảo trì, tính năng)
export const getSystemStatus = async () => {
    // Endpoint: GET /status
    return await apiClient('/status', {
        method: 'GET'
    });
};
