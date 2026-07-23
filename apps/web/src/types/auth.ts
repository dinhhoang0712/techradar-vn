// Domain types cho auth/user — dùng chung giữa authService, AppContext, UserProfile, layouts.
export interface User {
    id: string;
    full_name?: string;
    email?: string;
    role?: string;
    avatar_url?: string;
    [key: string]: unknown;
}

export interface LoginCredentials {
    email: string;
    password: string;
}

export interface RegisterData {
    full_name: string;
    email: string;
    password: string;
    [key: string]: unknown;
}

export interface AuthTokens {
    access_token: string;
    refresh_token?: string;
    [key: string]: unknown;
}
