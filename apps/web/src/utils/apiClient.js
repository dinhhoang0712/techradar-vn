// Utility func để call API với Base URL
// Dùng path tương đối để Vite proxy tự forward sang backend (tránh CORS)
const API_BASE_URL = '/api/v1';

export const apiClient = async (endpoint, options = {}) => {
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
                // Xử lý khi hết phiên đăng nhập hoặc Token không hợp lệ
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
