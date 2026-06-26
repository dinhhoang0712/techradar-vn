import AsyncStorage from '@react-native-async-storage/async-storage';
import { router } from 'expo-router';
import { Platform, Alert } from 'react-native';

// On mobile, localhost is usually not reachable from Android emulator.
// The project currently targets the deployed backend by default.
const API_BASE_URL = 'https://datamining.ankkun.space/api/v1';

const AUTH_ENDPOINTS = ['/auth/login', '/auth/register', '/auth/refresh'];

const isAuthEndpoint = (endpoint) => AUTH_ENDPOINTS.some((prefix) => endpoint.startsWith(prefix));
let isAuthRedirectPending = false;
let authRedirectTimer = null;

const parseResponseBody = async (response) => {
    const text = await response.text();
    if (!text) return null;

    try {
        return JSON.parse(text);
    } catch {
        return text;
    }
};

const extractErrorMessage = (payload, fallback) => {
    if (!payload) return fallback;
    if (typeof payload === 'string') return payload.trim() || fallback;
    return payload.message || payload.error || payload.detail || fallback;
};

const clearSession = async () => {
    await AsyncStorage.multiRemove(['access_token', 'refresh_token', 'login_timestamp']);
};

// Single-flight refresh: nhiều request 401 cùng lúc chỉ refresh 1 lần.
let refreshPromise = null;

const tryRefreshToken = async () => {
    const refreshToken = await AsyncStorage.getItem('refresh_token');
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
                    const pairs = [['access_token', data.access_token], ['login_timestamp', String(Date.now())]];
                    if (data.refresh_token) pairs.push(['refresh_token', data.refresh_token]);
                    await AsyncStorage.multiSet(pairs);
                    return true;
                }
                return false;
            } catch {
                return false;
            } finally {
                setTimeout(() => { refreshPromise = null; }, 0);
            }
        })();
    }
    return refreshPromise;
};

const redirectToLogin = () => {
    if (authRedirectTimer) {
        clearTimeout(authRedirectTimer);
        authRedirectTimer = null;
    }

    isAuthRedirectPending = false;
    router.replace('/login');
};

const notifySessionExpired = (title, message) => {
    if (isAuthRedirectPending) return;
    isAuthRedirectPending = true;

    if (Platform.OS === 'web') {
        window.alert(message);
        window.location.href = '/login';
        return;
    }

    Alert.alert(title, message, [
        { text: 'OK', onPress: redirectToLogin },
    ], { cancelable: false });

    authRedirectTimer = setTimeout(redirectToLogin, 15000);
};

export const apiClient = async (endpoint, options = {}, _retried = false) => {
    try {
        const loginTimestamp = await AsyncStorage.getItem('login_timestamp');
        if (loginTimestamp) {
            const diffSeconds = (Date.now() - parseInt(loginTimestamp, 10)) / 1000;
            if (diffSeconds > 900) {
                console.warn('Session timeout reached (900s). Clearing session...');
                await clearSession();

                notifySessionExpired(
                    'Het phien',
                    'Phien dang nhap da het han sau 15 phut. Vui long dang nhap lai.'
                );

                throw new Error('SESSION_TIMEOUT');
            }
        }

        const token = await AsyncStorage.getItem('access_token');

        const headers = {
            'Content-Type': 'application/json',
            ...options.headers,
        };

        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const config = {
            ...options,
            headers,
        };

        console.log(`[API Call] ${API_BASE_URL}${endpoint}`);

        const response = await fetch(`${API_BASE_URL}${endpoint}`, config);
        const payload = await parseResponseBody(response);

        if (!response.ok) {
            const message = extractErrorMessage(payload, `API error: ${response.status}`);

            if (response.status === 401 && !isAuthEndpoint(endpoint)) {
                // Thử refresh access_token 1 lần trước khi đăng xuất.
                if (!_retried) {
                    const refreshed = await tryRefreshToken();
                    if (refreshed) {
                        return apiClient(endpoint, options, true);
                    }
                }

                console.warn('Unauthorized. Clearing session...');
                await clearSession();

                notifySessionExpired(
                    'Loi xac thuc',
                    'Phien dang nhap cua ban da het han. Vui long dang nhap lai.'
                );
            }

            const error = new Error(message);
            error.status = response.status;
            error.payload = payload;
            throw error;
        }

        return payload;
    } catch (error) {
        console.error('Mobile API call failed:', error);
        throw error;
    }
};
