import { DM } from '@/constants/theme';
import React, { useMemo, useState, useEffect } from 'react';
import {
    Dimensions,
    Platform,
    ScrollView, StyleSheet,
    Text,
    TouchableOpacity,
    useWindowDimensions,
    View,
    ActivityIndicator,
} from 'react-native';
import { LineChart } from 'react-native-gifted-charts';
import { getCompareSearch } from '../../api/compareService';
import { getRadarTop10 } from '../../api/trendService';

const windowWidth = Dimensions.get('window').width;
const isWeb = Platform.OS === 'web';
const isMobile = Platform.OS === 'ios' || Platform.OS === 'android';
const screenWidth = isWeb ? Math.min(windowWidth, 480) : windowWidth;
const maxLabels = isMobile ? 4 : 6;

// chartConfig removed
const PALETTE = [
    '#6C63FF', '#00D68F', '#FF6584', '#FFC94D', '#54C5F8',
    '#FF8C00', '#7FBA00', '#E040FB', '#FF5252', '#00B4D8'
];

const roundAxisLimit = (value: number) => {
    if (value <= 100) return 100;
    if (value <= 250) return Math.ceil(value / 50) * 50;
    return Math.ceil(value / 100) * 100;
};

const getAxisLimit = (value: number) => {
    if (value < 99) return 100;
    return roundAxisLimit(value * 1.15);
};

export default function CompareScreen() {
    const { width: windowWidthNow } = useWindowDimensions();
    const currentScreenWidth = isWeb ? Math.min(windowWidthNow, 480) : windowWidthNow;
    const [selectedTechIds, setSelectedTechIds] = useState<string[]>([]);
    const [timeRange, setTimeRange] = useState(12);
    const [showDropdown, setShowDropdown] = useState(false);
    const [tooltipPoint, setTooltipPoint] = useState<any>(null);
    const [techOptions, setTechOptions] = useState<any[]>([]);
    const [compareData, setCompareData] = useState<any[]>([]);
    const [loadingTop, setLoadingTop] = useState(true);
    const [loadingCompare, setLoadingCompare] = useState(false);

    const chartWidth = Math.max(260, currentScreenWidth - 64);
    const chartHeight = isMobile ? 260 : 300;

    // Fetch initial options
    useEffect(() => {
        const fetchOptions = async () => {
            setLoadingTop(true);
            try {
                const res = await getRadarTop10();
                if (res?.data) {
                    const opts = res.data.map((item: any, i: number) => ({
                        id: item.keyword,
                        name: item.keyword,
                        color: PALETTE[i % PALETTE.length]
                    }));
                    setTechOptions(opts);
                    if (opts.length >= 2) {
                        setSelectedTechIds([opts[0].id, opts[1].id]);
                    }
                }
            } catch (error) {
                console.error("Lỗi lấy danh sách gợi ý", error);
            } finally {
                setLoadingTop(false);
            }
        };
        fetchOptions();
    }, []);

    // Fetch compare data when selection or time changes
    useEffect(() => {
        if (selectedTechIds.length === 0) {
            setCompareData([]);
            return;
        }

        const fetchCompare = async () => {
            setLoadingCompare(true);
            try {
                const res = await getCompareSearch(selectedTechIds, timeRange);
                if (res?.data) {
                    // Cắt theo timeRange giả lập (do endpoint GET search hiện tại trả về hết)
                    setCompareData(res.data);
                } else {
                    setCompareData([]);
                }
            } catch (error) {
                console.error("Lỗi Compare API", error);
                setCompareData([]);
            } finally {
                setLoadingCompare(false);
            }
        };
        fetchCompare();
    }, [selectedTechIds, timeRange]);

    const colorMap = useMemo(() => {
        const map: any = {};
        selectedTechIds.forEach((id, i) => {
            const opt = techOptions.find(t => t.id === id);
            map[id] = opt?.color || PALETTE[i % PALETTE.length];
        });
        return map;
    }, [selectedTechIds, techOptions]);

    const statsArr = useMemo(() => {
        if (!compareData || !compareData.length) return [];
        return compareData.map((techItem) => {
            return {
                id: techItem.keyword,
                name: techItem.keyword,
                color: colorMap[techItem.keyword] || '#fff',
                yoy: techItem.yoy_rate || 0,
                mom: techItem.mom_rate || 0,
                total: techItem.growth_rate || 0,
            };
        });
    }, [compareData, colorMap]);

    const rawChartData = useMemo(() => {
        if (!compareData || !compareData.length) return [];
        const mergedMap: any = {};

        compareData.forEach(techItem => {
            const kw = techItem.keyword;
            const history = techItem.monthly || [];

            history.forEach((point: any) => {
                const m = `T${point.month}/${point.year}`;

                if (!mergedMap[m]) {
                    mergedMap[m] = { month: m, rawSort: point.year * 100 + point.month };
                }
                mergedMap[m][kw] = point.job_count || 0;
            });
        });

        const sorted = Object.values(mergedMap).sort((a: any, b: any) => a.rawSort - b.rawSort);
        // Lọc theo timeRange
        const sliceStart = Math.max(0, sorted.length - timeRange);
        const visibleRows = sorted.slice(sliceStart);
        const baseVals: any = {};

        selectedTechIds.forEach((kw) => {
            const firstValidRow: any = visibleRows.find((row: any) => row[kw] > 0);
            baseVals[kw] = firstValidRow ? firstValidRow[kw] : null;
        });

        return visibleRows.map((row: any) => {
            const growthRow: any = { month: row.month, rawSort: row.rawSort };
            selectedTechIds.forEach((kw) => {
                const baseVal = baseVals[kw];
                growthRow[kw] = baseVal !== null && baseVal > 0
                    ? Math.round(((Number(row[kw] || 0) - baseVal) / baseVal) * 100)
                    : 0;
            });
            return growthRow;
        });
    }, [compareData, timeRange, selectedTechIds]);

    const chartAxis = useMemo(() => {
        const values = rawChartData.flatMap((row: any) =>
            selectedTechIds.map((id: string) => Number(row[id] || 0))
        );
        const maxGrowth = Math.max(0, ...values);
        const minGrowth = Math.min(0, ...values);
        const maxValue = getAxisLimit(maxGrowth);
        const mostNegativeValue = getAxisLimit(Math.abs(minGrowth));

        return {
            maxValue,
            mostNegativeValue,
            noOfSectionsBelowXAxis: 4,
        };
    }, [rawChartData, selectedTechIds]);

    const giftedChartData = useMemo(() => {
        if (rawChartData.length === 0) return [];
        const step = Math.max(1, Math.floor(rawChartData.length / maxLabels));

        return selectedTechIds.map((id: string) => {
            return {
                id,
                color: colorMap[id] || DM.primary,
                data: rawChartData.map((row: any, i: number) => ({
                    value: row[id] || 0,
                    label: i === 0 || i === rawChartData.length - 1 || i % step === 0 ? row.month : '',
                    labelTextStyle: { color: DM.text3, fontSize: 10 },
                    month: row.month,
                }))
            };
        });
    }, [rawChartData, selectedTechIds, colorMap]);

    const removeTech = (id: string) => {
        if (selectedTechIds.length > 2) {
            setSelectedTechIds((prev: string[]) => prev.filter((t: string) => t !== id));
        }
    };

    const toggleTech = (id: string) => {
        setSelectedTechIds((prev: string[]) =>
            prev.includes(id)
                ? prev.length > 2 ? prev.filter((t: string) => t !== id) : prev
                : prev.length < 5 ? [...prev, id] : prev
        );
    };

    const chartViewportWidth = Math.max(220, chartWidth - 40);

    const lineChartSpacing = useMemo(() => {
        if (rawChartData.length <= 1) return 32;
        const initialSpacing = 10;
        const endSpacing = 16;
        return Math.max(16, (chartViewportWidth - initialSpacing - endSpacing) / Math.max(1, rawChartData.length - 1));
    }, [chartViewportWidth, rawChartData.length]);

    if (loadingTop) {
        return (
            <View style={[styles.container, { justifyContent: 'center', alignItems: 'center' }]}>
                <ActivityIndicator size="large" color={DM.primary} />
                <Text style={{ color: DM.text2, marginTop: 10 }}>Đang tải gợi ý so sánh...</Text>
            </View>
        );
    }

    return (
        <View style={styles.container}>
            <ScrollView showsVerticalScrollIndicator={false} nestedScrollEnabled>
                {/* Header */}
                <View style={styles.header}>
                    <Text style={styles.headerTitle}>So sánh</Text>
                </View>

                {/* ── Controls Card ── */}
                <View style={styles.card}>
                    <View style={styles.controlsGrid}>
                        {/* CHỌN CÔNG NGHỆ */}
                        <View style={[styles.controlGroup, { width: '45%' }]}>
                            <Text style={styles.controlLabel}>CÔNG NGHỆ (2-5)</Text>
                            <View style={[styles.selectBox, { height: 30 }]}>
                                <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ flex: 1 }}>
                                    <View style={styles.selectTagRow}>
                                        {selectedTechIds.length > 0 && (() => {
                                            const id = selectedTechIds[0];
                                            const tech = techOptions.find((t: any) => t.id === id) as any;
                                            return (
                                                <View key={id} style={styles.selectedTag}>
                                                    <View style={[styles.tagDot, { backgroundColor: tech?.color || colorMap[id] }]} />
                                                    <Text style={styles.tagText}>{tech?.name || id}</Text>
                                                    <TouchableOpacity onPress={() => removeTech(id)} style={styles.tagRemove}>
                                                        <Text style={styles.tagRemoveText}>×</Text>
                                                    </TouchableOpacity>
                                                </View>
                                            );
                                        })()}
                                        {selectedTechIds.length > 1 && (
                                            <View style={[styles.selectedTag, { paddingRight: 6 }]}>
                                                <Text style={styles.tagText}>+{selectedTechIds.length - 1}</Text>
                                            </View>
                                        )}
                                    </View>
                                </ScrollView>
                                <View style={styles.selectDivider} />
                                <TouchableOpacity style={styles.dropdownToggle} onPress={() => setShowDropdown(d => !d)}>
                                    <Text style={styles.dropdownToggleText}>▼</Text>
                                </TouchableOpacity>
                            </View>
                        </View>

                        {/* THỜI GIAN */}
                        <View style={[styles.controlGroup, { width: '50%' }]}>
                            <Text style={styles.controlLabel}>THỜI GIAN</Text>
                            <View style={styles.pillGroup}>
                                {[3, 6, 12].map(v => (
                                    <TouchableOpacity
                                        key={v}
                                        style={[styles.pill, timeRange === v && styles.pillActive]}
                                        onPress={() => setTimeRange(v)}
                                    >
                                        <Text style={[styles.pillText, timeRange === v && styles.pillTextActive]}>
                                            {v} tháng
                                        </Text>
                                    </TouchableOpacity>
                                ))}
                            </View>
                        </View>
                    </View>

                    {/* Dropdown panel */}
                    {showDropdown && (
                        <View style={styles.dropdownPanel}>
                            <ScrollView style={styles.dropdownScroll} nestedScrollEnabled>
                                {techOptions.filter((t: any) => !selectedTechIds.includes(t.id)).map((t: any) => (
                                    <TouchableOpacity
                                        key={t.id}
                                        style={styles.dropdownItem}
                                        onPress={() => { if (selectedTechIds.length < 5) { toggleTech(t.id); setShowDropdown(false); } }}
                                    >
                                        <Text style={styles.dropdownItemText}>{t.name}</Text>
                                    </TouchableOpacity>
                                ))}
                            </ScrollView>
                        </View>
                    )}
                </View>

                {/* ── Stats Cards ── */}
                {loadingCompare ? (
                    <ActivityIndicator size="small" color={DM.primaryLight} style={{ marginVertical: 20 }} />
                ) : (
                    <View style={styles.statsRow}>
                        {statsArr.map((s: any) => (
                            <View key={s.id} style={[styles.compareCard, { borderColor: s.color + '55' }]}>
                                {/* Header: dot + name */}
                                <View style={styles.csHeader}>
                                    <View style={[styles.csDot, { backgroundColor: s.color }]} />
                                    <Text style={styles.csName} numberOfLines={1}>{s.name}</Text>
                                </View>

                                {/* Big percentage */}
                                <Text style={[styles.csBig, { color: s.color }]}>{s.total >= 0 ? '+' : ''}{typeof s.total === 'number' ? s.total.toFixed(2) : s.total}%</Text>

                                {/* YOY / MOM */}
                                <View style={styles.csMetaRow}>
                                    <View style={styles.csMeta}>
                                        <Text style={styles.csMetaLabel}>YOY</Text>
                                        <Text style={[styles.csMetaVal, { color: s.yoy >= 0 ? DM.green : DM.accent }]}>
                                            {s.yoy >= 0 ? '+' : ''}{typeof s.yoy === 'number' ? s.yoy.toFixed(2) : s.yoy}%
                                        </Text>
                                    </View>
                                    <View style={styles.csMeta}>
                                        <Text style={styles.csMetaLabel}>MOM</Text>
                                        <Text style={[styles.csMetaVal, { color: s.mom >= 0 ? DM.green : DM.accent }]}>
                                            {s.mom >= 0 ? '+' : ''}{typeof s.mom === 'number' ? s.mom.toFixed(2) : s.mom}%
                                        </Text>
                                    </View>
                                </View>
                            </View>
                        ))}
                    </View>
                )}

                {/* ── Chart ── */}
                {!loadingCompare && rawChartData.length > 0 && (
                    <View style={styles.card}>
                        <Text style={styles.sectionTitle}>So sánh % Tăng trưởng (so với tháng đầu)</Text>
                        <View style={styles.chartFrame}>
                            <LineChart
                                data={giftedChartData[0]?.data || []}
                                data2={giftedChartData[1]?.data}
                                data3={giftedChartData[2]?.data}
                                data4={giftedChartData[3]?.data}
                                data5={giftedChartData[4]?.data}
                                color1={giftedChartData[0]?.color}
                                color2={giftedChartData[1]?.color}
                                color3={giftedChartData[2]?.color}
                                color4={giftedChartData[3]?.color}
                                color5={giftedChartData[4]?.color}
                                dataPointsColor1={giftedChartData[0]?.color}
                                dataPointsColor2={giftedChartData[1]?.color}
                                dataPointsColor3={giftedChartData[2]?.color}
                                dataPointsColor4={giftedChartData[3]?.color}
                                dataPointsColor5={giftedChartData[4]?.color}
                                width={chartViewportWidth}
                                height={chartHeight - 40}
                                curved
                                thickness={2}
                                spacing={lineChartSpacing}
                                initialSpacing={10}
                                endSpacing={16}
                                disableScroll
                                yAxisColor={DM.border}
                                xAxisColor={DM.border}
                                yAxisTextStyle={{ color: DM.text3, fontSize: 10 }}
                                xAxisLabelTextStyle={{ color: DM.text3, fontSize: 10 }}
                                rulesColor={DM.border}
                                rulesType="dashed"
                                yAxisLabelSuffix="%"
                                noOfSections={4}
                                maxValue={chartAxis.maxValue}
                                mostNegativeValue={chartAxis.mostNegativeValue}
                                noOfSectionsBelowXAxis={chartAxis.noOfSectionsBelowXAxis}
                                hideDataPoints={false}
                                dataPointsRadius={3}
                                pointerConfig={{
                                    pointerStripUptoDataPoint: true,
                                    pointerStripColor: DM.primary,
                                    pointerStripWidth: 2,
                                    strokeDashArray: [2, 5],
                                    pointerColor: DM.primary,
                                    radius: 4,
                                    pointerLabelWidth: 160,
                                    pointerLabelHeight: 120,
                                    pointerLabelComponent: (items: any) => {
                                        if (!items || !items.length) return null;
                                        const month = items[0]?.month || '';
                                        return (
                                            <View style={styles.tooltipBox}>
                                                <Text style={styles.tooltipMonth}>{month}</Text>
                                                {items.map((item: any, idx: number) => {
                                                    const techId = selectedTechIds[idx];
                                                    return (
                                                        <View key={idx} style={styles.tooltipRow}>
                                                            <View style={[styles.tooltipDot, { backgroundColor: colorMap[techId] }]} />
                                                            <Text style={styles.tooltipName}>{techId}</Text>
                                                            <Text style={[styles.tooltipValue, { color: item.value >= 0 ? DM.green : DM.accent }]}>
                                                                {(item.value >= 0 ? '+' : '') + item.value + '%'}
                                                            </Text>
                                                        </View>
                                                    );
                                                })}
                                            </View>
                                        );
                                    },
                                }}
                            />
                        </View>

                        {/* Legend */}
                        <View style={styles.legendRow}>
                            {selectedTechIds.map((id: string) => {
                                return (
                                    <View key={id} style={styles.legendItem}>
                                        <View style={[styles.legendDot, { backgroundColor: colorMap[id] }]} />
                                        <Text style={styles.legendText}>{id}</Text>
                                    </View>
                                );
                            })}
                        </View>
                    </View>
                )}

                {/* Footer */}
            </ScrollView>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: DM.bg,
        paddingTop: 48,
        ...(Platform.OS === 'web' && { alignSelf: 'center', width: '100%' })
    },
    header: { paddingHorizontal: 16, marginBottom: 16 },
    headerTitle: { fontSize: 20, fontWeight: '800', color: DM.text },

    // Controls
    card: {
        backgroundColor: DM.surface, borderWidth: 1, borderColor: DM.border,
        borderRadius: DM.radius, padding: 16, marginHorizontal: 16, marginBottom: 12,
    },
    controlsGrid: {
        flexDirection: 'row' as const, flexWrap: 'wrap' as const, gap: 16,
        alignItems: 'flex-start' as const,
    },
    controlGroup: { gap: 6 },
    controlLabel: {
        fontSize: 10, fontWeight: '700', color: DM.text3,
        letterSpacing: 0.8, textTransform: 'uppercase' as const, marginBottom: 4,
    },
    selectBox: {
        flexDirection: 'row' as const, alignItems: 'center' as const,
        backgroundColor: DM.surface2, borderWidth: 1, borderColor: DM.border,
        borderRadius: DM.radiusSm, minHeight: 38, paddingLeft: 6,
    },
    selectTagRow: { flexDirection: 'row' as const, alignItems: 'center' as const, gap: 4, paddingVertical: 4 },
    selectedTag: {
        flexDirection: 'row' as const, alignItems: 'center' as const, gap: 3,
        backgroundColor: DM.primaryGlow, borderRadius: 4, paddingHorizontal: 6, paddingVertical: 3,
    },
    tagDot: { width: 7, height: 7, borderRadius: 4 },
    tagText: { fontSize: 11, color: DM.primaryLight, fontWeight: '600' },
    tagRemove: { marginLeft: 2, paddingHorizontal: 2 },
    tagRemoveText: { color: DM.primaryLight, fontSize: 13, fontWeight: '700' },
    clearBtn: { paddingHorizontal: 8, justifyContent: 'center' as const },
    clearBtnText: { color: DM.text3, fontSize: 16, fontWeight: '400' },
    selectDivider: { width: 1, height: 20, backgroundColor: DM.border, marginHorizontal: 4 },
    dropdownToggle: { paddingHorizontal: 10, justifyContent: 'center' as const, height: 38 },
    dropdownToggleText: { color: DM.text3, fontSize: 10 },
    dropdownPanel: {
        marginTop: 8, backgroundColor: DM.surface2, borderRadius: DM.radiusSm,
        borderWidth: 1, borderColor: DM.border, overflow: 'hidden' as const,
    },
    dropdownScroll: { maxHeight: 220 },
    dropdownItem: {
        paddingHorizontal: 14, paddingVertical: 12,
        borderBottomWidth: 1, borderBottomColor: DM.border,
    },
    dropdownItemText: { fontSize: 13, color: DM.text2 },
    pillGroup: { flexDirection: 'row' as const, gap: 4, flexWrap: 'wrap' as const },
    pill: {
        paddingHorizontal: 12, paddingVertical: 6, borderRadius: 99,
        borderWidth: 1, borderColor: DM.border, backgroundColor: DM.surface2,
    },
    pillActive: { backgroundColor: DM.primaryGlow, borderColor: DM.primary },
    pillText: { fontSize: 11, fontWeight: '500', color: DM.text2 },
    pillTextActive: { color: DM.primaryLight, fontWeight: '600' },

    statsRow: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        paddingHorizontal: 16,
        justifyContent: 'space-between',
        marginBottom: 4
    },
    compareCard: {
        backgroundColor: DM.surface, borderWidth: 1, borderRadius: DM.radius,
        padding: 16, width: (screenWidth - 44) / 2,
        marginBottom: 12,
    },
    csHeader: { flexDirection: 'row' as const, alignItems: 'center' as const, gap: 8, marginBottom: 8 },
    csDot: { width: 10, height: 10, borderRadius: 5 },
    csName: { fontWeight: '700', fontSize: 14, color: DM.text },
    csBig: { fontSize: 32, fontWeight: '800', marginBottom: 12 },
    csMetaRow: { flexDirection: 'row' as const, gap: 16, marginBottom: 10 },
    csMeta: { gap: 2 },
    csMetaLabel: { fontSize: 10, color: DM.text3, fontWeight: '600', textTransform: 'uppercase' as const },
    csMetaVal: { fontSize: 14, fontWeight: '700', color: DM.text },

    // Chart
    sectionTitle: { fontSize: 15, fontWeight: '700', color: DM.text, marginBottom: 12 },
    chartFrame: { marginLeft: -10, paddingRight: 20 },
    chart: { borderRadius: DM.radius, marginLeft: -8 },
    legendRow: { flexDirection: 'row' as const, flexWrap: 'wrap' as const, gap: 12, marginTop: 12 },
    legendItem: { flexDirection: 'row' as const, alignItems: 'center' as const, gap: 5 },
    legendDot: { width: 8, height: 8, borderRadius: 4 },
    legendText: { fontSize: 11, color: DM.text2 },

    // Tooltip styles
    tooltipBox: {
        backgroundColor: DM.surface2,
        borderWidth: 1,
        borderColor: DM.border,
        borderRadius: DM.radiusSm,
        padding: 10,
        width: 140,
        marginLeft: -70,
        marginTop: -20,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.3,
        shadowRadius: 6,
        elevation: 8,
    },
    tooltipMonth: {
        color: DM.text,
        fontSize: 12,
        fontWeight: '700',
        marginBottom: 6,
    },
    tooltipRow: {
        flexDirection: 'row' as const,
        alignItems: 'center' as const,
        marginBottom: 4,
    },
    tooltipDot: {
        width: 6,
        height: 6,
        borderRadius: 3,
        marginRight: 6,
    },
    tooltipName: {
        flex: 1,
        color: DM.text2,
        fontSize: 10,
    },
    tooltipValue: {
        color: DM.text,
        fontSize: 11,
        fontWeight: '600',
    },
});
