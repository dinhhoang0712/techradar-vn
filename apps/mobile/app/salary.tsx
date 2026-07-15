import React, { useEffect, useMemo, useState } from 'react';
import {
    View, Text, StyleSheet, ScrollView, TouchableOpacity,
    TextInput, ActivityIndicator, Platform,
} from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { DM } from '@/constants/theme';
import { getSalaryTop, getSalaryByTech } from '../api/salaryService';

// Field names match the backend's snake_case contract (see apps/web/src/pages/SalaryPage.jsx).
interface SalaryItem {
    tech_name: string;
    median_salary_mvnd: number;
    avg_salary_mvnd: number;
    min_salary_mvnd: number;
    max_salary_mvnd: number;
    p25_salary_mvnd: number;
    p75_salary_mvnd: number;
    salary_range: string;
    total_jobs: number;
    jobs_with_salary: number;
    top_co_techs?: string[];
}

function formatM(val?: number) {
    if (!val) return '—';
    return `${val.toFixed(1)}M`;
}

function salaryColor(value: number, max: number) {
    if (!value || !max) return DM.text3;
    const ratio = value / max;
    if (ratio > 0.75) return '#00d68f';
    if (ratio > 0.45) return '#54C5F8';
    return '#ffc94d';
}

export default function SalaryScreen() {
    const router = useRouter();
    const [data, setData] = useState<SalaryItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [search, setSearch] = useState('');
    const [sortBy, setSortBy] = useState<'median' | 'jobs' | 'max'>('median');
    const [selected, setSelected] = useState<SalaryItem | null>(null);
    const [detail, setDetail] = useState<any>(null);
    const [detailLoading, setDetailLoading] = useState(false);

    useEffect(() => {
        getSalaryTop(40, 3)
            .then((res) => setData(res?.data ?? []))
            .catch((err) => setError(err?.message || 'Không thể tải dữ liệu lương.'))
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        if (!selected) { setDetail(null); return; }
        setDetailLoading(true);
        getSalaryByTech(selected.tech_name)
            .then((res) => setDetail(res?.data ?? null))
            .catch(() => setDetail(null))
            .finally(() => setDetailLoading(false));
    }, [selected]);

    const filtered = useMemo(() => {
        let list = data.filter((d) => d.jobs_with_salary > 0);
        if (search.trim()) {
            const q = search.trim().toLowerCase();
            list = list.filter((d) => d.tech_name.toLowerCase().includes(q));
        }
        if (sortBy === 'median') list = [...list].sort((a, b) => b.median_salary_mvnd - a.median_salary_mvnd);
        else if (sortBy === 'jobs') list = [...list].sort((a, b) => b.total_jobs - a.total_jobs);
        else list = [...list].sort((a, b) => b.max_salary_mvnd - a.max_salary_mvnd);
        return list;
    }, [data, search, sortBy]);

    const topMedian = useMemo(() => Math.max(0, ...filtered.map((d) => d.median_salary_mvnd)), [filtered]);
    const topThree = filtered.slice(0, 3);
    const shownDetail = detail ?? selected;

    return (
        <View style={styles.container}>
            <View style={styles.header}>
                <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
                    <Ionicons name="arrow-back" size={24} color={DM.text} />
                </TouchableOpacity>
                <Text style={styles.headerTitle}>Salary Insights</Text>
                <View style={{ width: 24 }} />
            </View>

            {loading ? (
                <View style={styles.center}>
                    <ActivityIndicator size="large" color={DM.primary} />
                    <Text style={{ color: DM.text2, marginTop: 10 }}>Đang phân tích dữ liệu lương...</Text>
                </View>
            ) : error ? (
                <View style={styles.center}>
                    <Text style={styles.error}>{error}</Text>
                </View>
            ) : (
                <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
                    <View style={styles.top3Row}>
                        {topThree.map((t, i) => (
                            <TouchableOpacity key={t.tech_name} style={[styles.top3Card, { borderColor: ['#00d68f', '#54C5F8', '#ffc94d'][i] }]} onPress={() => setSelected(t)}>
                                <Text style={styles.top3Rank}>#{i + 1}</Text>
                                <Text style={styles.top3Name}>{t.tech_name}</Text>
                                <Text style={styles.top3Salary}>{formatM(t.median_salary_mvnd)} VND</Text>
                            </TouchableOpacity>
                        ))}
                    </View>

                    <View style={styles.searchRow}>
                        <TextInput
                            style={styles.searchInput}
                            placeholder="Tìm công nghệ..."
                            placeholderTextColor={DM.text3}
                            value={search}
                            onChangeText={setSearch}
                        />
                    </View>
                    <View style={styles.chipRow}>
                        {([['median', 'Median'], ['max', 'Cao nhất'], ['jobs', 'Nhiều jobs']] as const).map(([val, label]) => (
                            <TouchableOpacity key={val} style={[styles.chip, sortBy === val && styles.chipActive]} onPress={() => setSortBy(val)}>
                                <Text style={[styles.chipText, sortBy === val && styles.chipTextActive]}>{label}</Text>
                            </TouchableOpacity>
                        ))}
                    </View>

                    <View style={{ marginTop: 8 }}>
                        {filtered.map((item) => (
                            <TouchableOpacity
                                key={item.tech_name}
                                style={[styles.row, selected?.tech_name === item.tech_name && styles.rowSelected]}
                                onPress={() => setSelected(selected?.tech_name === item.tech_name ? null : item)}
                            >
                                <View style={styles.rowHeader}>
                                    <Text style={styles.rowName}>{item.tech_name}</Text>
                                    <Text style={[styles.rowSalary, { color: salaryColor(item.median_salary_mvnd, topMedian) }]}>
                                        {formatM(item.median_salary_mvnd)}
                                    </Text>
                                </View>
                                <View style={styles.barTrack}>
                                    <View style={[styles.barFill, {
                                        width: `${topMedian ? Math.min(100, (item.median_salary_mvnd / topMedian) * 100) : 0}%`,
                                        backgroundColor: salaryColor(item.median_salary_mvnd, topMedian),
                                    }]} />
                                </View>
                                <Text style={styles.rowMeta}>{item.salary_range} • {item.jobs_with_salary?.toLocaleString()} jobs</Text>
                            </TouchableOpacity>
                        ))}
                        {filtered.length === 0 && <Text style={styles.emptyText}>Không tìm thấy công nghệ nào</Text>}
                    </View>

                    {selected && (
                        <View style={styles.detailBox}>
                            <View style={styles.resultHeaderRow}>
                                <Text style={styles.sectionTitle}>{selected.tech_name}</Text>
                                <TouchableOpacity onPress={() => setSelected(null)}><Ionicons name="close" size={20} color={DM.text3} /></TouchableOpacity>
                            </View>
                            {detailLoading ? (
                                <ActivityIndicator size="small" color={DM.primary} />
                            ) : shownDetail ? (
                                <View style={styles.statsGrid}>
                                    <View style={styles.statBox}><Text style={styles.statLabel}>Median</Text><Text style={[styles.statValue, { color: '#00d68f' }]}>{formatM(shownDetail.median_salary_mvnd)}</Text></View>
                                    <View style={styles.statBox}><Text style={styles.statLabel}>Trung bình</Text><Text style={styles.statValue}>{formatM(shownDetail.avg_salary_mvnd)}</Text></View>
                                    <View style={styles.statBox}><Text style={styles.statLabel}>P25–P75</Text><Text style={styles.statValue}>{formatM(shownDetail.p25_salary_mvnd)}–{formatM(shownDetail.p75_salary_mvnd)}</Text></View>
                                    <View style={styles.statBox}><Text style={styles.statLabel}>Min–Max</Text><Text style={styles.statValue}>{formatM(shownDetail.min_salary_mvnd)}–{formatM(shownDetail.max_salary_mvnd)}</Text></View>
                                </View>
                            ) : null}
                            {shownDetail?.top_co_techs?.length > 0 && (
                                <View style={styles.chipRow}>
                                    {shownDetail.top_co_techs.map((t: string) => (
                                        <View key={t} style={styles.coTechChip}><Text style={styles.coTechChipText}>{t}</Text></View>
                                    ))}
                                </View>
                            )}
                        </View>
                    )}

                    <View style={{ height: 48 }} />
                </ScrollView>
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
    content: { padding: 20 },
    error: { color: '#ff6b6b', fontSize: 14, textAlign: 'center', paddingHorizontal: 24 },
    top3Row: { flexDirection: 'row', gap: 10, marginBottom: 16 },
    top3Card: { flex: 1, backgroundColor: DM.surface2, borderWidth: 1, borderRadius: DM.radius, padding: 12 },
    top3Rank: { fontSize: 10, color: DM.text3, fontWeight: '700' },
    top3Name: { fontSize: 13, fontWeight: '700', color: DM.text, marginTop: 2 },
    top3Salary: { fontSize: 13, fontWeight: '700', color: '#00d68f', marginTop: 4 },
    searchRow: { marginBottom: 10 },
    searchInput: {
        backgroundColor: DM.surface2, borderWidth: 1, borderColor: DM.border,
        borderRadius: DM.radiusSm, paddingHorizontal: 14, paddingVertical: 10, color: DM.text, fontSize: 14,
    },
    chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 8 },
    chip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20, borderWidth: 1, borderColor: DM.border, backgroundColor: DM.surface },
    chipActive: { backgroundColor: DM.primaryGlow, borderColor: DM.primary },
    chipText: { fontSize: 12, color: DM.text2 },
    chipTextActive: { color: DM.primaryLight, fontWeight: '600' },
    row: { paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: DM.border },
    rowSelected: { backgroundColor: DM.surface2 },
    rowHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 },
    rowName: { fontSize: 14, fontWeight: '600', color: DM.text },
    rowSalary: { fontSize: 14, fontWeight: '700' },
    barTrack: { height: 5, backgroundColor: DM.surface2, borderRadius: 3, overflow: 'hidden' },
    barFill: { height: '100%', borderRadius: 3 },
    rowMeta: { fontSize: 11, color: DM.text3, marginTop: 4 },
    emptyText: { textAlign: 'center', color: DM.text3, padding: 24 },
    detailBox: { marginTop: 20, backgroundColor: DM.surface, borderRadius: DM.radius, borderWidth: 1, borderColor: DM.border, padding: 16 },
    resultHeaderRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
    sectionTitle: { fontSize: 15, fontWeight: '700', color: DM.text },
    statsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
    statBox: { width: '47%', backgroundColor: DM.surface2, borderRadius: 8, padding: 10 },
    statLabel: { fontSize: 10, color: DM.text3, textTransform: 'uppercase' },
    statValue: { fontSize: 14, fontWeight: '700', color: DM.text, marginTop: 2 },
    coTechChip: { backgroundColor: 'rgba(255,255,255,0.06)', borderRadius: 20, paddingHorizontal: 10, paddingVertical: 3 },
    coTechChipText: { fontSize: 12, color: DM.text2 },
});
