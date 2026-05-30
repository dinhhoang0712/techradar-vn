import { DM } from '@/constants/theme';
import { useRouter } from 'expo-router';
import React from 'react';
import { Platform, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

const isWeb = Platform.OS === 'web';

export default function Footer() {
    const year = new Date().getFullYear();
    const router = useRouter();

    return (
        <View style={styles.footer}>
            {/* Main footer content */}
            <View style={styles.inner}>
                {/* Brand */}
                <View style={styles.brand}>
                    <Text style={styles.logo}>Tech<Text style={styles.logoAccent}>Radar</Text></Text>
                    <Text style={styles.tagline}>Theo dõi xu hướng công nghệ thông minh</Text>
                </View>

                {/* Links */}
                <View style={styles.linksRow}>
                    <View style={styles.col}>
                        <Text style={styles.colTitle}>TÍNH NĂNG</Text>
                        <TouchableOpacity onPress={() => router.push('/')}><Text style={styles.link}>Radar Dashboard</Text></TouchableOpacity>
                        <TouchableOpacity onPress={() => router.push('/compare')}><Text style={styles.link}>So sánh Tech</Text></TouchableOpacity>
                        <TouchableOpacity onPress={() => router.push('/graph')}><Text style={styles.link}>Đồ thị quan hệ</Text></TouchableOpacity>
                        <TouchableOpacity onPress={() => router.push('/chat')}><Text style={styles.link}>AI Tư vấn</Text></TouchableOpacity>
                    </View>
                    <View style={styles.col}>
                        <Text style={styles.colTitle}>DỮ LIỆU</Text>
                        <Text style={styles.link}>Nguồn dữ liệu</Text>
                        <Text style={styles.link}>Cập nhật realtime</Text>
                        <Text style={styles.link}>Báo cáo xu hướng</Text>
                    </View>
                </View>
            </View>

            {/* Bottom bar */}
            <View style={styles.bottom}>
                <Text style={styles.bottomText}>© {year} TechRadar · Dữ liệu cập nhật 1h trước</Text>
                <View style={styles.statusRow}>
                    <View style={styles.statusDot} />
                    <Text style={styles.statusText}>Hệ thống hoạt động bình thường</Text>
                </View>
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    footer: {
        backgroundColor: DM.bg2,
        borderTopWidth: 1,
        borderTopColor: DM.border,
        marginTop: 24,
    },
    inner: {
        flexDirection: 'row' as const,
        flexWrap: 'wrap' as const,
        justifyContent: 'space-between',
        paddingHorizontal: 24,
        paddingVertical: 24,
        gap: 24,
    },
    brand: {
        maxWidth: 200,
        gap: 6,
    },
    logo: { fontSize: 18, fontWeight: '800', color: DM.text },
    logoAccent: { color: DM.primaryLight },
    tagline: { fontSize: 12, color: DM.text3, lineHeight: 18 },
    linksRow: {
        flexDirection: 'row' as const,
        gap: 40,
    },
    col: { gap: 6 },
    colTitle: {
        fontSize: 10, fontWeight: '700', color: DM.text2,
        letterSpacing: 1, marginBottom: 4,
    },
    link: { fontSize: 12, color: DM.text3, lineHeight: 20 },
    bottom: {
        flexDirection: 'row' as const,
        alignItems: 'center' as const,
        justifyContent: 'space-between',
        paddingHorizontal: 24,
        paddingVertical: 12,
        borderTopWidth: 1,
        borderTopColor: DM.border,
        flexWrap: 'wrap' as const,
        gap: 8,
    },
    bottomText: { fontSize: 11, color: DM.text3 },
    statusRow: { flexDirection: 'row' as const, alignItems: 'center' as const, gap: 6 },
    statusDot: {
        width: 7, height: 7, borderRadius: 4,
        backgroundColor: DM.green,
    },
    statusText: { fontSize: 11, color: DM.text3 },
});
