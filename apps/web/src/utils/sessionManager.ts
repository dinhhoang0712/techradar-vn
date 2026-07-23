// Auth/session management used by apiClient's 401 handling.
// Kept out of apiClient.js on purpose: apiClient is a generic HTTP wrapper that
// should stay reusable anywhere (including public-only callers), so the
// session-specific concerns — token refresh and the "session expired" UX
// (clearing storage, toast, redirect to /login) — live in their own module.
import { showGlobalToast } from '../components/common/toastBridge';

// Single source of truth for the API prefix (also re-exported from apiClient.js
// so existing callers don't need to know this module exists).
export const API_BASE_URL = '/api/v1';

interface RefreshTokenResponse {
    access_token?: string;
    refresh_token?: string;
}

// Single-flight refresh: nhiều request 401 cùng lúc chỉ refresh 1 lần.
let refreshPromise: Promise<boolean> | null = null;

export const tryRefreshToken = async (): Promise<boolean> => {
    const refreshToken = localStorage.getItem('refresh_token');
    if (!refreshToken) return false;
    if (!refreshPromise) {
        refreshPromise = (async () => {
            try {
                const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ refresh_token: refreshToken }),
                });
                if (!res.ok) return false;
                const data: RefreshTokenResponse = await res.json(); // BARE: { access_token, refresh_token, ... }
                if (data && data.access_token) {
                    localStorage.setItem('access_token', data.access_token);
                    if (data.refresh_token) localStorage.setItem('refresh_token', data.refresh_token);
                    return true;
                }
                return false;
            } catch {
                return false;
            } finally {
                // cho phép lần refresh kế tiếp
                setTimeout(() => { refreshPromise = null; }, 0);
            }
        })();
    }
    return refreshPromise;
};

// Refresh thất bại (hoặc không áp dụng được) → đăng xuất: xoá token, báo cho
// người dùng và điều hướng về /login.
export const handleSessionExpired = (): void => {
    console.warn('Session expired or unauthorized. Redirecting to login...');
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('user');

    // Tránh loop redirect nếu đang ở trang login
    if (!window.location.pathname.includes('/login')) {
        showGlobalToast({
            title: 'Phiên đăng nhập đã hết hạn',
            body: 'Vui lòng đăng nhập lại.',
            variant: 'error',
            duration: 4000,
        });
        // Delay the redirect so the toast is actually visible before the page tears down.
        setTimeout(() => { window.location.href = '/login'; }, 1200);
    }
};
