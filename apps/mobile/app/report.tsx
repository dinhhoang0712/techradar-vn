import React, { useState } from 'react';
import {
    View, Text, StyleSheet, ScrollView, TouchableOpacity,
    TextInput, ActivityIndicator, Platform, Alert,
} from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import * as FileSystem from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { DM } from '@/constants/theme';
import { generateReport } from '../api/reportService';

const PERIOD_PRESETS = (() => {
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth() + 1;
    const quarter = Math.ceil(month / 3);
    const presets: { label: string; value: string }[] = [];
    for (let i = 0; i < 4; i++) {
        let q = quarter - i;
        let y = year;
        while (q < 1) { q += 4; y -= 1; }
        presets.push({ label: `Q${q} ${y}`, value: `${y}-Q${q}` });
    }
    presets.push({ label: `Năm ${year}`, value: `${year}` });
    presets.push({ label: `Năm ${year - 1}`, value: `${year - 1}` });
    return presets;
})();

const TOP_N_OPTIONS = [5, 10, 15, 20];

// Simple markdown: bold, bullet list, heading — same convention as (tabs)/chat.tsx.
function renderMarkdown(text: string) {
    return text.split('\n').map((line, i) => {
        if (line.startsWith('- ') || /^\d+\. /.test(line)) {
            return <Text key={i} style={styles.mdBullet}>  •  {renderInline(line.replace(/^- /, '').replace(/^\d+\. /, ''))}</Text>;
        }
        if (/^#{1,3} /.test(line)) {
            return <Text key={i} style={styles.mdHeading}>{renderInline(line.replace(/^#+\s/, ''))}</Text>;
        }
        if (line.trim() === '') return <Text key={i}>{'\n'}</Text>;
        return <Text key={i} style={styles.mdP}>{renderInline(line)}</Text>;
    });
}

function renderInline(text: string): (string | React.ReactElement)[] {
    const parts: (string | React.ReactElement)[] = [];
    let rest = text, k = 0;
    while (rest.length > 0) {
        const m = rest.match(/\*\*(.+?)\*\*/);
        if (!m) { parts.push(rest); break; }
        const idx = rest.indexOf(m[0]);
        if (idx > 0) parts.push(rest.slice(0, idx));
        parts.push(<Text key={k++} style={styles.mdBold}>{m[1]}</Text>);
        rest = rest.slice(idx + m[0].length);
    }
    return parts;
}

interface TopTech { name: string; cluster_label?: string; job_count?: number; growth_rate?: number }
interface ReportResult { period: string; generated_at?: string; top_techs?: TopTech[]; report?: string }

export default function ReportScreen() {
    const router = useRouter();
    const [period, setPeriod] = useState(PERIOD_PRESETS[0].value);
    const [topN, setTopN] = useState(10);
    const [result, setResult] = useState<ReportResult | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const handleGenerate = async () => {
        if (!period.trim()) return;
        setLoading(true);
        setError('');
        setResult(null);
        try {
            const res = await generateReport(period.trim(), topN, 'markdown');
            setResult(res?.data ?? res);
        } catch (err: any) {
            setError(err?.message || 'Không thể tạo báo cáo. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    };

    const handleShare = async () => {
        if (!result?.report) return;
        try {
            const fileUri = `${FileSystem.cacheDirectory}techradar-report-${result.period || period}.md`;
            await FileSystem.writeAsStringAsync(fileUri, result.report, { encoding: FileSystem.EncodingType.UTF8 });
            const canShare = await Sharing.isAvailableAsync();
            if (canShare) {
                await Sharing.shareAsync(fileUri, { mimeType: 'text/markdown', dialogTitle: 'Chia sẻ báo cáo' });
            } else {
                Alert.alert('Không thể chia sẻ', 'Thiết bị này không hỗ trợ chia sẻ file.');
            }
        } catch (err) {
            console.warn('[Report] share failed:', err);
            Alert.alert('Lỗi', 'Không thể lưu/chia sẻ báo cáo.');
        }
    };

    return (
        <View style={styles.container}>
            <View style={styles.header}>
                <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
                    <Ionicons name="arrow-back" size={24} color={DM.text} />
                </TouchableOpacity>
                <Text style={styles.headerTitle}>Báo cáo xu hướng</Text>
                <View style={{ width: 24 }} />
            </View>

            <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
                <Text style={styles.label}>Kỳ báo cáo</Text>
                <View style={styles.chipRow}>
                    {PERIOD_PRESETS.map((p) => (
                        <TouchableOpacity key={p.value} style={[styles.chip, period === p.value && styles.chipActive]} onPress={() => setPeriod(p.value)}>
                            <Text style={[styles.chipText, period === p.value && styles.chipTextActive]}>{p.label}</Text>
                        </TouchableOpacity>
                    ))}
                </View>
                <TextInput
                    style={styles.input}
                    value={period}
                    onChangeText={setPeriod}
                    placeholder="VD: 2024-Q4, 2024-12, 2024"
                    placeholderTextColor={DM.text3}
                />

                <Text style={[styles.label, { marginTop: 20 }]}>Top N công nghệ</Text>
                <View style={styles.chipRow}>
                    {TOP_N_OPTIONS.map((n) => (
                        <TouchableOpacity key={n} style={[styles.chip, topN === n && styles.chipActive]} onPress={() => setTopN(n)}>
                            <Text style={[styles.chipText, topN === n && styles.chipTextActive]}>Top {n}</Text>
                        </TouchableOpacity>
                    ))}
                </View>

                <TouchableOpacity
                    style={[styles.submitBtn, (loading || !period.trim()) && styles.submitBtnDisabled]}
                    onPress={handleGenerate}
                    disabled={loading || !period.trim()}
                >
                    {loading ? <ActivityIndicator size="small" color="#000" /> : <Text style={styles.submitBtnText}>TẠO BÁO CÁO</Text>}
                </TouchableOpacity>

                {result?.report && (
                    <TouchableOpacity style={styles.shareBtn} onPress={handleShare}>
                        <Ionicons name="share-outline" size={16} color={DM.text} />
                        <Text style={styles.shareBtnText}>Chia sẻ / Lưu (.md)</Text>
                    </TouchableOpacity>
                )}

                {error ? <Text style={styles.error}>{error}</Text> : null}

                {result?.top_techs && result.top_techs.length > 0 && (
                    <View style={styles.resultBox}>
                        <View style={styles.resultHeaderRow}>
                            <Text style={styles.sectionTitle}>Top {result.top_techs.length} công nghệ nổi bật</Text>
                            <Text style={styles.periodBadge}>{result.period}</Text>
                        </View>
                        {result.top_techs.map((t, i) => (
                            <View key={t.name || i} style={styles.techRow}>
                                <Text style={styles.techRank}>#{i + 1}</Text>
                                <View style={{ flex: 1 }}>
                                    <Text style={styles.techName}>{t.name}</Text>
                                    <Text style={styles.techCluster}>{t.cluster_label || '—'}</Text>
                                </View>
                                <Text style={styles.techJobs}>{t.job_count?.toLocaleString() || '—'}</Text>
                                <Text style={[styles.techGrowth, { color: (t.growth_rate ?? 0) >= 0 ? '#00d68f' : '#ff5252' }]}>
                                    {t.growth_rate != null ? `${t.growth_rate >= 0 ? '+' : ''}${Number(t.growth_rate).toFixed(1)}%` : '—'}
                                </Text>
                            </View>
                        ))}
                    </View>
                )}

                {result?.report && (
                    <View style={styles.resultBox}>
                        <Text style={styles.sectionTitle}>Nội dung báo cáo</Text>
                        {result.generated_at && (
                            <Text style={styles.generatedAt}>Tạo lúc: {new Date(result.generated_at).toLocaleString('vi-VN')}</Text>
                        )}
                        <View style={{ marginTop: 8 }}>{renderMarkdown(result.report)}</View>
                    </View>
                )}

                <View style={{ height: 48 }} />
            </ScrollView>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: DM.bg,
        ...(Platform.OS === 'web' && { alignSelf: 'center', width: '100%', maxWidth: 480 }),
    },
    header: {
        flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
        paddingHorizontal: 16, paddingTop: Platform.OS === 'android' ? 48 : 56, paddingBottom: 16,
        borderBottomWidth: 1, borderBottomColor: DM.border, backgroundColor: DM.surface,
    },
    backButton: { padding: 8, marginLeft: -8 },
    headerTitle: { fontSize: 18, fontWeight: '800', color: DM.text },
    content: { padding: 24 },
    label: { fontSize: 12, fontWeight: '700', color: DM.text3, marginBottom: 8, letterSpacing: 0.5 },
    chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 10 },
    chip: { paddingHorizontal: 14, paddingVertical: 6, borderRadius: 20, borderWidth: 1, borderColor: DM.border, backgroundColor: DM.surface },
    chipActive: { backgroundColor: DM.primaryGlow, borderColor: DM.primary },
    chipText: { fontSize: 12, color: DM.text2 },
    chipTextActive: { color: DM.primaryLight, fontWeight: '600' },
    input: {
        backgroundColor: DM.surface2, borderWidth: 1, borderColor: DM.border,
        borderRadius: DM.radiusSm, paddingHorizontal: 16, paddingVertical: 12, fontSize: 14, color: DM.text,
    },
    submitBtn: {
        backgroundColor: DM.primary, borderRadius: 12, paddingVertical: 16,
        alignItems: 'center', justifyContent: 'center', marginTop: 20,
    },
    submitBtnDisabled: { opacity: 0.5 },
    submitBtnText: { fontSize: 14, fontWeight: '900', color: '#000', letterSpacing: 1 },
    shareBtn: {
        flexDirection: 'row', gap: 8, alignItems: 'center', justifyContent: 'center',
        borderWidth: 1, borderColor: DM.border, borderRadius: 12, paddingVertical: 12, marginTop: 10,
    },
    shareBtnText: { fontSize: 13, color: DM.text, fontWeight: '600' },
    error: { marginTop: 12, color: '#ff6b6b', fontSize: 13, padding: 12, backgroundColor: 'rgba(255,107,107,0.1)', borderRadius: 8 },
    resultBox: { marginTop: 24 },
    resultHeaderRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
    sectionTitle: { fontSize: 15, fontWeight: '700', color: DM.text },
    periodBadge: {
        backgroundColor: DM.primaryGlow, borderWidth: 1, borderColor: DM.primary,
        borderRadius: 16, color: DM.primaryLight, fontSize: 11, fontWeight: '700', paddingHorizontal: 10, paddingVertical: 3,
    },
    techRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: DM.border },
    techRank: { color: DM.primary, fontWeight: '700', fontSize: 13, width: 24 },
    techName: { fontWeight: '600', color: DM.text, fontSize: 14 },
    techCluster: { color: DM.text3, fontSize: 11, marginTop: 2 },
    techJobs: { color: DM.text2, fontSize: 12 },
    techGrowth: { fontSize: 12, fontWeight: '700', width: 56, textAlign: 'right' },
    generatedAt: { color: DM.text3, fontSize: 11, marginTop: 4 },
    mdP: { color: DM.text2, fontSize: 13, lineHeight: 20, marginBottom: 4 },
    mdHeading: { color: DM.text, fontSize: 14, fontWeight: '700', marginTop: 10, marginBottom: 4 },
    mdBullet: { color: DM.text2, fontSize: 13, lineHeight: 20, marginBottom: 4 },
    mdBold: { fontWeight: '700', color: DM.text },
});
