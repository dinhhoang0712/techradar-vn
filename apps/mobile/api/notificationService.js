import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import { apiClient } from '../utils/apiClient';

const API_BASE_URL = 'https://datamining.ankkun.space/api/v1';

// GET /notifications — danh sách thông báo (mới nhất trước)
export const getNotifications = async ({ limit, offset } = {}) => {
    const params = new URLSearchParams();
    if (limit != null) params.set('limit', limit.toString());
    if (offset != null) params.set('offset', offset.toString());
    const qs = params.toString();
    const res = await apiClient(`/notifications${qs ? `?${qs}` : ''}`);
    return res?.data ?? res ?? [];
};

// GET /notifications/unread-count
export const getUnreadCount = async () => {
    const res = await apiClient('/notifications/unread-count');
    return res?.data ?? res ?? 0;
};

// POST /notifications/{id}/read
export const markNotificationRead = async (id) =>
    apiClient(`/notifications/${id}/read`, { method: 'POST' });

// POST /notifications/read-all
export const markAllNotificationsRead = async () =>
    apiClient('/notifications/read-all', { method: 'POST' });

// Realtime updates: true SSE on web (fetch + ReadableStream, matching apps/web);
// 30s polling on native, since RN fetch streaming isn't used elsewhere in this app
// (services/chatService.js falls back the same way). Both return an object with .abort().
export const streamNotifications = (onNotification, onError) => {
    if (Platform.OS === 'web') {
        return streamNotificationsWeb(onNotification, onError);
    }
    return pollNotifications(onNotification, onError);
};

function streamNotificationsWeb(onNotification, onError) {
    const controller = new AbortController();
    (async () => {
        try {
            const token = await AsyncStorage.getItem('access_token');
            const res = await fetch(`${API_BASE_URL}/notifications/stream`, {
                headers: {
                    Accept: 'text/event-stream',
                    ...(token ? { Authorization: `Bearer ${token}` } : {}),
                },
                signal: controller.signal,
            });
            if (!res.ok || !res.body) throw new Error(`SSE ${res.status}`);

            const reader = res.body.getReader();
            const decoder = new TextDecoder('utf-8');
            let buffer = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                buffer += decoder.decode(value, { stream: true });

                const lines = buffer.split('\n');
                buffer = lines.pop();

                for (const line of lines) {
                    if (!line || line.startsWith(':') || line.startsWith('event:')) continue;
                    if (line.startsWith('data:')) {
                        const raw = line.slice(5).trimStart();
                        try {
                            onNotification(JSON.parse(raw));
                        } catch {
                            /* dòng data không phải JSON — bỏ qua */
                        }
                    }
                }
            }
        } catch (err) {
            if (err?.name !== 'AbortError') onError?.(err);
        }
    })();
    return controller;
}

function pollNotifications(onNotification, onError) {
    let stopped = false;
    let firstTick = true;
    const seenIds = new Set();

    const tick = async () => {
        if (stopped) return;
        try {
            const list = await getNotifications({ limit: 20 });
            if (firstTick) {
                // Chỉ ghi nhận thông báo đã có sẵn, không báo lại lịch sử cũ.
                list.forEach((n) => seenIds.add(n.id));
                firstTick = false;
                return;
            }
            list.forEach((n) => {
                if (!seenIds.has(n.id)) {
                    seenIds.add(n.id);
                    onNotification(n);
                }
            });
        } catch (err) {
            onError?.(err);
        }
    };

    tick();
    const intervalId = setInterval(tick, 30000);
    return { abort: () => { stopped = true; clearInterval(intervalId); } };
}
