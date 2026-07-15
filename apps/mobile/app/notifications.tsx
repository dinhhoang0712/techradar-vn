import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
    View, Text, StyleSheet, FlatList, TouchableOpacity,
    ActivityIndicator, Platform,
} from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { DM } from '@/constants/theme';
import {
    getNotifications, markNotificationRead, markAllNotificationsRead, streamNotifications,
} from '../api/notificationService';

const PAGE_SIZE = 20;

const ICON_BY_TYPE: Record<string, string> = {
    TREND_ALERT: 'trending-up',
};

function iconFor(type: string) {
    return ICON_BY_TYPE[type] || 'notifications-outline';
}

function formatDateTime(iso?: string) {
    if (!iso) return '';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleString('vi-VN');
}

interface Notif { id: string | number; type: string; title: string; body?: string; link?: string; read: boolean; created_at?: string }

export default function NotificationsScreen() {
    const router = useRouter();
    const [items, setItems] = useState<Notif[]>([]);
    const [offset, setOffset] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [loading, setLoading] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);
    const [error, setError] = useState(false);
    const streamRef = useRef<{ abort: () => void } | null>(null);

    const loadFirstPage = useCallback(async () => {
        setLoading(true);
        setError(false);
        try {
            const list = await getNotifications({ limit: PAGE_SIZE, offset: 0 });
            setItems(Array.isArray(list) ? list : []);
            setOffset(list.length);
            setHasMore(list.length === PAGE_SIZE);
        } catch (err) {
            console.warn('[Notifications] load failed:', err);
            setError(true);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadFirstPage();
    }, [loadFirstPage]);

    useEffect(() => {
        streamRef.current = streamNotifications(
            (n) => {
                if (n?._poll) return; // native poll signal without a real payload — ignore, next manual refresh picks it up
                setItems((prev) => [n, ...prev.filter((x) => x.id !== n.id)]);
            },
            (err) => console.warn('[Notifications] stream error:', err),
        );
        return () => streamRef.current?.abort();
    }, []);

    const loadMore = async () => {
        setLoadingMore(true);
        try {
            const list = await getNotifications({ limit: PAGE_SIZE, offset });
            setItems((prev) => [...prev, ...(Array.isArray(list) ? list : [])]);
            setOffset((o) => o + list.length);
            setHasMore(list.length === PAGE_SIZE);
        } catch (err) {
            console.warn('[Notifications] load more failed:', err);
        } finally {
            setLoadingMore(false);
        }
    };

    const handleItemPress = async (n: Notif) => {
        if (!n.read) {
            setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
            try { await markNotificationRead(n.id); } catch { /* optimistic */ }
        }
        if (n.link) router.push(n.link as any);
    };

    const handleMarkAll = async () => {
        setItems((prev) => prev.map((x) => ({ ...x, read: true })));
        try { await markAllNotificationsRead(); } catch { /* optimistic */ }
    };

    const unreadCount = items.filter((n) => !n.read).length;

    return (
        <View style={styles.container}>
            <View style={styles.header}>
                <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
                    <Ionicons name="arrow-back" size={24} color={DM.text} />
                </TouchableOpacity>
                <Text style={styles.headerTitle}>Thông báo</Text>
                <View style={{ width: 24 }} />
            </View>

            {unreadCount > 0 && (
                <TouchableOpacity style={styles.markAllBtn} onPress={handleMarkAll}>
                    <Text style={styles.markAllBtnText}>Đánh dấu tất cả đã đọc</Text>
                </TouchableOpacity>
            )}

            {loading ? (
                <View style={styles.center}><ActivityIndicator size="large" color={DM.primary} /></View>
            ) : error ? (
                <View style={styles.center}>
                    <Text style={{ color: DM.text2 }}>Không tải được thông báo.</Text>
                    <TouchableOpacity onPress={loadFirstPage} style={{ marginTop: 12 }}>
                        <Text style={{ color: DM.primary, fontWeight: '600' }}>Thử lại</Text>
                    </TouchableOpacity>
                </View>
            ) : items.length === 0 ? (
                <View style={styles.center}><Text style={{ color: DM.text3 }}>Chưa có thông báo nào.</Text></View>
            ) : (
                <FlatList
                    data={items}
                    keyExtractor={(item) => String(item.id)}
                    contentContainerStyle={{ padding: 16 }}
                    renderItem={({ item }) => (
                        <TouchableOpacity
                            style={[styles.item, !item.read && styles.itemUnread]}
                            onPress={() => handleItemPress(item)}
                        >
                            <Ionicons name={iconFor(item.type) as any} size={18} color={item.read ? DM.text3 : DM.primaryLight} style={{ marginTop: 2 }} />
                            <View style={{ flex: 1 }}>
                                <Text style={styles.itemTitle}>{item.title}</Text>
                                {item.body ? <Text style={styles.itemBody}>{item.body}</Text> : null}
                                <Text style={styles.itemTime}>{formatDateTime(item.created_at)}</Text>
                            </View>
                            {!item.read && <View style={styles.dot} />}
                        </TouchableOpacity>
                    )}
                    ListFooterComponent={hasMore ? (
                        <TouchableOpacity style={styles.loadMoreBtn} onPress={loadMore} disabled={loadingMore}>
                            <Text style={styles.loadMoreText}>{loadingMore ? 'Đang tải…' : 'Tải thêm'}</Text>
                        </TouchableOpacity>
                    ) : null}
                />
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: DM.bg,
        ...(Platform.OS === 'web' && { alignSelf: 'center', width: '100%', maxWidth: 480 }),
    },
    center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
    header: {
        flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
        paddingHorizontal: 16, paddingTop: Platform.OS === 'android' ? 48 : 56, paddingBottom: 16,
        borderBottomWidth: 1, borderBottomColor: DM.border, backgroundColor: DM.surface,
    },
    backButton: { padding: 8, marginLeft: -8 },
    headerTitle: { fontSize: 18, fontWeight: '800', color: DM.text },
    markAllBtn: { alignSelf: 'flex-end', paddingHorizontal: 16, paddingTop: 12 },
    markAllBtnText: { fontSize: 12, color: DM.primaryLight, fontWeight: '600' },
    item: {
        flexDirection: 'row', gap: 10, padding: 14, borderRadius: DM.radiusSm,
        backgroundColor: DM.surface, borderWidth: 1, borderColor: DM.border, marginBottom: 8,
    },
    itemUnread: { borderColor: DM.border2 },
    itemTitle: { fontSize: 13, fontWeight: '600', color: DM.text },
    itemBody: { fontSize: 12, color: DM.text2, marginTop: 2 },
    itemTime: { fontSize: 11, color: DM.text3, marginTop: 4 },
    dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: DM.primary, marginTop: 4 },
    loadMoreBtn: { alignItems: 'center', paddingVertical: 16 },
    loadMoreText: { color: DM.text2, fontSize: 13, fontWeight: '600' },
});
