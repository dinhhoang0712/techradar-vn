import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';
import type { LoginCredentials, RegisterData, AuthTokens, User } from '../types/auth';

export const loginUser = async (credentials: LoginCredentials): Promise<AuthTokens> => {
    return await apiClient('/auth/login', {
        method: 'POST',
        body: JSON.stringify(credentials)
    });
};

export const registerUser = async (userData: RegisterData): Promise<AuthTokens & { status?: string }> => {
    return await apiClient('/auth/register', {
        method: 'POST',
        body: JSON.stringify(userData)
    });
};

export const refreshToken = async (refreshTokenValue: string): Promise<ApiResponse<AuthTokens>> => {
    return await apiClient('/auth/refresh', {
        method: 'POST',
        body: JSON.stringify({ refresh_token: refreshTokenValue })
    });
};

export const logoutUser = async (): Promise<unknown> => {
    return await apiClient('/auth/logout', {
        method: 'POST'
    });
};

export const getCurrentUser = async (): Promise<ApiResponse<User> | User> => {
    return await apiClient('/auth/me', {
        method: 'GET'
    });
};

export const getSystemStatus = async (): Promise<Record<string, unknown>> => {
    return await apiClient('/status', {
        method: 'GET'
    });
};

export const forgotPassword = async (email: string): Promise<unknown> => {
    return await apiClient('/auth/forgot-password', {
        method: 'POST',
        body: JSON.stringify({ email })
    });
};

export const resetPassword = async (token: string, newPassword: string): Promise<unknown> => {
    return await apiClient('/auth/reset-password', {
        method: 'POST',
        body: JSON.stringify({ token, new_password: newPassword })
    });
};
