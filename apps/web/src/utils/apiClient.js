// Utility func để call API với Base URL
// Dùng path tương đối để Vite proxy tự forward sang backend (tránh CORS)
const API_BASE_URL = '/api/v1';

// Single-flight refresh: nhiều request 401 cùng lúc chỉ refresh 1 lần.
let refreshPromise = null;

const tryRefreshToken = async () => {
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
                const data = await res.json(); // BARE: { access_token, refresh_token, ... }
                if (data && data.access_token) {
                    localStorage.setItem('access_token', data.access_token);
                    if (data.refresh_token) localStorage.setItem('refresh_token', data.refresh_token);
                    localStorage.setItem('login_timestamp', Date.now().toString());
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

export const apiClient = async (endpoint, options = {}, _retried = false) => {
    // 1. Kiểm tra thời gian phiên đăng nhập (900 giây = 15 phút)
    const loginTimestamp = localStorage.getItem('login_timestamp');
    if (loginTimestamp) {
        const diffSeconds = (Date.now() - parseInt(loginTimestamp)) / 1000;
        if (diffSeconds > 900) {
            console.warn('Session timeout reached (900s). Kicking to login...');
            localStorage.removeItem('access_token');
            localStorage.removeItem('refresh_token');
            localStorage.removeItem('user');
            localStorage.removeItem('login_timestamp');
            
            if (!window.location.pathname.includes('/login')) {
                alert('Phiên đăng nhập của bạn đã hết hạn (sau 15 phút). Vui lòng đăng nhập lại.');
                window.location.href = '/login';
            }
            const timeoutError = new Error('SESSION_TIMEOUT');
            timeoutError.status = 401;
            throw timeoutError;
        }
    }

    // Lấy token từ localStorage
    const token = localStorage.getItem('access_token');

    const headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...options.headers,
    };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const config = {
        ...options,
        headers,
    };

    try {
        const response = await fetch(`${API_BASE_URL}${endpoint}`, config);
        
        if (!response.ok) {
            // Handle Maintenance mode from server (503)
            if (response.status === 503) {
                const maintenanceError = new Error('SERVER_MAINTENANCE');
                maintenanceError.status = 503;
                throw maintenanceError;
            }

            if (response.status === 401) {
                // Thử refresh access_token 1 lần (trừ chính các endpoint /auth/*) trước khi đăng xuất.
                if (!_retried && !endpoint.startsWith('/auth/')) {
                    const refreshed = await tryRefreshToken();
                    if (refreshed) {
                        return apiClient(endpoint, options, true);
                    }
                }

                // Refresh thất bại → đăng xuất như cũ.
                console.warn('Session expired or unauthorized. Redirecting to login...');
                localStorage.removeItem('access_token');
                localStorage.removeItem('refresh_token');
                localStorage.removeItem('user');
                localStorage.removeItem('login_timestamp');
                
                // Tránh loop redirect nếu đang ở trang login
                if (!window.location.pathname.includes('/login')) {
                    alert('Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.');
                    window.location.href = '/login';
                }
                
                const authError = new Error('UNAUTHORIZED');
                authError.status = 401;
                throw authError;
            }
            
            // For other non-OK statuses, try to parse error message from backend
            let errorMsg = `HTTP Error ${response.status}`;
            try {
                const errData = await response.json();
                errorMsg = errData.message || errData.error || errData.detail || errorMsg;
            } catch (e) {
                // Ignore json parse error for error responses
            }
            const apiError = new Error(errorMsg);
            apiError.status = response.status;
            throw apiError;
        }
        
        return await response.json();
    } catch (error) {
        if (error.message === 'SERVER_MAINTENANCE') throw error;
        
        console.error('API call failed:', error);
        // Map network/connection errors to a specific type
        if (error instanceof TypeError && error.message === 'Failed to fetch') {
            const networkError = new Error('SERVER_CONNECTION_FAILED');
            networkError.status = 0;
            throw networkError;
        }
        throw error;
    }
};
