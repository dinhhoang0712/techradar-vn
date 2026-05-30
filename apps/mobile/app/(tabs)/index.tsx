import { DM } from '@/constants/theme';
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import React, { useCallback, useMemo, useRef, useState, useEffect } from 'react';
import {
  Alert,
  Dimensions, Platform,
  ScrollView, StyleSheet,
  Text,
  TouchableOpacity,
  useWindowDimensions,
  View,
  ActivityIndicator
} from 'react-native';
import { BarChart, LineChart } from 'react-native-gifted-charts';
import { captureRef } from 'react-native-view-shot';
import { getRadarTop4, getRadarTop10 } from '../../api/trendService';
import { getCompareSearch } from '../../api/compareService';

const windowWidth = Dimensions.get('window').width;
const isWeb = Platform.OS === 'web';
const isMobile = Platform.OS === 'ios' || Platform.OS === 'android';
const screenWidth = isWeb ? Math.min(windowWidth, 480) : windowWidth;
const maxLabels = isMobile ? 4 : 6;

const TIME_OPTIONS = [
  { label: '3 tháng', value: 3 },
  { label: '6 tháng', value: 6 },
  { label: '12 tháng', value: 12 },
];

const PALETTE = [
  '#6C63FF', '#00D68F', '#FF6584', '#FFC94D', '#54C5F8',
  '#FF8C00', '#7FBA00', '#E040FB', '#FF5252', '#00B4D8'
];

// Removed chartConfig for chart-kit

const roundAxisLimit = (value: number) => {
  if (value <= 100) return 100;
  if (value <= 250) return Math.ceil(value / 50) * 50;
  return Math.ceil(value / 100) * 100;
};

const getAxisLimit = (value: number) => {
  if (value < 99) return 100;
  return roundAxisLimit(value * 1.15);
};

const roundJobAxisLimit = (value: number) => {
  if (value <= 100) return 100;
  if (value <= 500) return Math.ceil(value / 50) * 50;
  if (value <= 1000) return Math.ceil(value / 100) * 100;
  return Math.ceil(value / 500) * 500;
};

const getJobAxisLimit = (value: number) => {
  if (value <= 100) return 100;
  return roundJobAxisLimit(value * 1.15);
};

function transformToChartData(apiData) {
  if (!apiData?.data || !Array.isArray(apiData.data)) return [];
  const mergedMap = {};
  apiData.data.forEach((techItem) => {
    const kw = techItem.keyword;
    const history = techItem.monthly || [];
    history.forEach((point) => {
      const m = `T${point.month}/${point.year}`;
      if (!mergedMap[m]) {
        mergedMap[m] = { month: m, rawSort: point.year * 100 + point.month };
      }
      mergedMap[m][kw] = point.job_count || 0;
    });
  });
  return Object.values(mergedMap).sort((a: any, b: any) => a.rawSort - b.rawSort);
}

function toGrowthData(timelineData, keywords) {
  if (!timelineData.length) return [];
  const baseVals = {};
  keywords.forEach((kw) => {
    const firstValidRow = timelineData.find((row) => row[kw] > 0);
    baseVals[kw] = firstValidRow ? firstValidRow[kw] : null;
  });

  return timelineData.map((row) => {
    const g = { month: row.month };
    keywords.forEach((kw) => {
      const b = baseVals[kw];
      if (b !== null && b > 0) {
        g[kw] = Math.round(((row[kw] - b) / b) * 100);
      } else {
        g[kw] = 0;
      }
    });
    return g;
  });
}

export default function DashboardScreen() {
  const chartRef = useRef<View>(null);
  const { width: windowWidthNow } = useWindowDimensions();
  const currentScreenWidth = isWeb ? Math.min(windowWidthNow, 480) : windowWidthNow;

  // API State
  const [top4Data, setTop4Data] = useState([]);
  const [top10Data, setTop10Data] = useState([]);
  const [timelineData, setTimelineData] = useState([]);
  const [allTechs, setAllTechs] = useState([]);
  const [loadingTop, setLoadingTop] = useState(true);
  const [loadingChart, setLoadingChart] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // UI State
  const [timeRange, setTimeRange] = useState(6);
  const [chartMode, setChartMode] = useState('line');
  const [showDropdown, setShowDropdown] = useState(false);
  const [selectedTechIds, setSelectedTechIds] = useState<string[]>([]);

  const radarChartWidth = Math.max(260, currentScreenWidth - 64);
  const radarChartHeight = 280;

  // Fetch initial top data
  useEffect(() => {
    const fetchTopData = async () => {
      setLoadingTop(false);
      setLoadingTop(true);
      try {
        const [t4res, t10res] = await Promise.all([getRadarTop4(), getRadarTop10()]);
        if (t4res?.data) setTop4Data(t4res.data);
        if (t10res?.data) {
          setTop10Data(t10res.data);
          const mappedTechs = t10res.data.map((item: any, i: number) => ({
            id: item.keyword,
            name: item.keyword,
            color: PALETTE[i % PALETTE.length]
          }));
          setAllTechs(mappedTechs);
          if (mappedTechs.length > 0) {
            setSelectedTechIds(mappedTechs.slice(0, 3).map((t: any) => t.id));
          }
        }
      } catch (err: any) {
        console.error("Error fetching top data", err);
        setError("Không thể kết nối tới Backend (8080). Hãy chắc chắn server đã chạy.");
      } finally {
        setLoadingTop(false);
      }
    };
    fetchTopData();
  }, []);

  // Fetch chart data when selection changes
  useEffect(() => {
    if (selectedTechIds.length === 0) return;
    const fetchChart = async () => {
      setLoadingChart(true);
      try {
        const res = await getCompareSearch(selectedTechIds, timeRange);
        setTimelineData(transformToChartData(res));
      } catch (error) {
        console.error("Error fetching chart data", error);
        setTimelineData([]);
      } finally {
        setLoadingChart(false);
      }
    };
    fetchChart();
  }, [selectedTechIds, timeRange]);

  const chartTimelineData = useMemo(() => {
    const sliceStart = Math.max(0, timelineData.length - timeRange);
    return timelineData.slice(sliceStart);
  }, [timelineData, timeRange]);
  const growthData = useMemo(() => toGrowthData(chartTimelineData, selectedTechIds), [chartTimelineData, selectedTechIds]);
  const visibleData = chartMode === 'growth' ? growthData : chartTimelineData;

  const growthChartAxis = useMemo(() => {
    const values = growthData.flatMap((row: any) =>
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
  }, [growthData, selectedTechIds]);

  const jobChartMaxValue = useMemo(() => {
    const values = chartTimelineData.flatMap((row: any) =>
      selectedTechIds.map((id: string) => Number(row[id] || 0))
    );
    return getJobAxisLimit(Math.max(0, ...values));
  }, [chartTimelineData, selectedTechIds]);

  const chartViewportWidth = Math.max(220, radarChartWidth - 40);
  const barGroupSpacing = visibleData.length >= 12 ? 6 : 12;
  const adaptiveBarWidth = useMemo(() => {
    const groups = Math.max(1, visibleData.length);
    const seriesCount = Math.max(1, selectedTechIds.length);
    const availableWidth = chartViewportWidth - 24 - groups * barGroupSpacing;
    const fitWidth = availableWidth / Math.max(1, groups * seriesCount) - 2;
    return Math.max(4, Math.min(12, fitWidth));
  }, [barGroupSpacing, chartViewportWidth, selectedTechIds.length, visibleData.length]);

  const giftedChartData = useMemo(() => {
    if (visibleData.length === 0) return [];
    const step = Math.max(1, Math.floor(visibleData.length / maxLabels));

    return selectedTechIds.map((id: string) => {
      const tech = allTechs.find((t: any) => t.id === id) as any;
      return {
        id,
        color: tech?.color || DM.primary,
        data: visibleData.map((row: any, i: number) => ({
          value: row[id] || 0,
          label: i === 0 || i === visibleData.length - 1 || i % step === 0 ? row.month : '',
          labelTextStyle: { color: DM.text3, fontSize: 10 },
          month: row.month,
        }))
      };
    });
  }, [visibleData, selectedTechIds, allTechs]);

  const groupedBarData = useMemo(() => {
    if (visibleData.length === 0 || selectedTechIds.length === 0) return [];
    const step = Math.max(1, Math.floor(visibleData.length / maxLabels));
    const result: any[] = [];
    const numTechs = selectedTechIds.length;

    visibleData.forEach((row: any, i: number) => {
      selectedTechIds.forEach((id: string, techIndex: number) => {
        const tech = allTechs.find((t: any) => t.id === id) as any;
        const isFirst = techIndex === 0;
        const isLast = techIndex === numTechs - 1;
        
        result.push({
          value: Number(Math.max(0, row[id] || 0)),
          frontColor: tech?.color || DM.primary,
          label: (isFirst && (i === 0 || i === visibleData.length - 1 || i % step === 0)) ? row.month : '',
          labelTextStyle: { color: DM.text3, fontSize: 10 },
          spacing: isLast ? barGroupSpacing : 2,
        });
      });
    });
    return result;
  }, [barGroupSpacing, visibleData, selectedTechIds, allTechs]);

  const toggleTech = (id: string) => {
    setSelectedTechIds((prev: string[]) =>
      prev.includes(id)
        ? prev.length > 1 ? prev.filter((t: string) => t !== id) : prev
        : prev.length < 5 ? [...prev, id] : prev
    );
  };

  const removeTech = (id: string) => {
    if (selectedTechIds.length > 1) {
      setSelectedTechIds((prev: string[]) => prev.filter((t: string) => t !== id));
    }
  };

  const lineChartSpacing = useMemo(() => {
    if (visibleData.length <= 1) return 32;
    const initialSpacing = 10;
    const endSpacing = 16;
    return Math.max(16, (chartViewportWidth - initialSpacing - endSpacing) / Math.max(1, visibleData.length - 1));
  }, [chartViewportWidth, visibleData.length]);

  const barChartWidth = useMemo(() => {
    return Math.max(220, radarChartWidth - 40);
  }, [radarChartWidth]);

  const handleExportCSV = useCallback(async () => {
    const headers = ['Month', ...selectedTechIds];
    const rows = visibleData.map((row: any) => [row.month, ...selectedTechIds.map((id: string) => row[id] ?? 0)]);
    const csv = [headers, ...rows].map((r: any) => r.join(',')).join('\n');

    if (isWeb) {
      const blob = new Blob([csv], { type: 'text/csv' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = 'tech_trend_export.csv'; a.click();
      URL.revokeObjectURL(url);
    } else {
      const uri = (FileSystem.cacheDirectory ?? '') + 'tech_trend_export.csv';
      await FileSystem.writeAsStringAsync(uri, csv, { encoding: FileSystem.EncodingType.UTF8 });
      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(uri, { mimeType: 'text/csv', dialogTitle: 'Chia sẻ dữ liệu CSV' });
      } else {
        Alert.alert('Không hỗ trợ', 'Thiết bị không hỗ trợ chia sẻ file');
      }
    }
  }, [visibleData, selectedTechIds]);

  const handleExportPNG = useCallback(async () => {
    if (isWeb) {
      try {
        const el = document.getElementById('main-chart-wrapper');
        if (!el) return;
        const { default: html2canvas } = await import('html2canvas' as any);
        const canvas = await html2canvas(el, { backgroundColor: '#0d0f1a' });
        const a = document.createElement('a');
        a.href = canvas.toDataURL('image/png');
        a.download = 'tech_trend_chart.png';
        a.click();
      } catch (e) {
        Alert.alert('Lỗi', 'Không thể export PNG');
      }
    } else {
      try {
        if (!chartRef.current) {
          Alert.alert('Lỗi', 'Không tìm thấy biểu đồ để xuất ảnh');
          return;
        }

        const uri = await captureRef(chartRef.current, {
          format: 'png',
          quality: 1,
          result: 'tmpfile',
        });

        if (await Sharing.isAvailableAsync()) {
          await Sharing.shareAsync(uri, {
            mimeType: 'image/png',
            dialogTitle: 'Chia sẻ ảnh biểu đồ',
          });
        } else {
          Alert.alert('Không hỗ trợ', 'Thiết bị không hỗ trợ chia sẻ file');
        }
      } catch (e) {
        console.error('Export PNG failed:', e);
        const message = e instanceof Error ? e.message : 'Khong the xuat PNG. Vui long thu lai.';
        Alert.alert('Loi xuat PNG', message);
      }
    }
  }, [chartRef]);

  if (error) {
    return (
      <View style={[styles.container, { justifyContent: 'center', alignItems: 'center', padding: 20 }]}>
        <Text style={{ color: '#ff6b6b', textAlign: 'center', marginBottom: 20 }}>{error}</Text>
        <TouchableOpacity style={styles.btnPrimary} onPress={() => window.location.reload()}>
          <Text style={styles.btnPrimaryText}>Thử lại</Text>
        </TouchableOpacity>
      </View>
    );
  }

  if (loadingTop) {
    return (
      <View style={[styles.container, { justifyContent: 'center', alignItems: 'center' }]}>
        <ActivityIndicator size="large" color={DM.primary} />
        <Text style={{ color: DM.text2, marginTop: 10 }}>Đang tải dữ liệu xu hướng...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={false} nestedScrollEnabled>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Tech<Text style={styles.headerAccent}>Radar</Text></Text>
        </View>

        <View style={styles.statsRow}>
          {top4Data.map((t: any, i) => (
            <View key={t.industry || i} style={styles.statCard}>
              <View style={styles.statHeader}>
                <Text style={styles.statName} numberOfLines={1}>{t.industry}</Text>
                <View style={[styles.badge, t.growth_rate > 30 ? styles.badgeUp : t.growth_rate < 0 ? styles.badgeDown : styles.badgeFlat]}>
                  <Text style={[styles.badgeText, t.growth_rate > 30 ? styles.badgeUpText : t.growth_rate < 0 ? styles.badgeDownText : styles.badgeFlatText]}>
                    {t.growth_rate > 0 ? '+' : ''}{typeof t.growth_rate === 'number' ? t.growth_rate.toFixed(2) : t.growth_rate}%
                  </Text>
                </View>
              </View>
              <Text style={styles.statJobs}>{t.job_count?.toLocaleString() || 0} <Text style={styles.statJobsLabel}>jobs</Text></Text>
              <Text style={styles.statMeta}>MoM: {t.mom_rate > 0 ? '+' : ''}{typeof t.mom_rate === 'number' ? t.mom_rate.toFixed(2) : t.mom_rate}%</Text>
            </View>
          ))}
        </View>

        <View style={styles.card}>
          <View style={[styles.controlRow, { justifyContent: 'space-between' }]}>
            <View style={[styles.controlGroup, { width: '45%' }]}>
              <Text style={styles.controlLabel}>CÔNG NGHỆ</Text>
              <View style={[styles.selectBox, { height: 30 }]}>
                <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ flex: 1 }}>
                  <View style={styles.tagRow}>
                    {selectedTechIds.length > 0 && (() => {
                      const id = selectedTechIds[0];
                      const tech = allTechs.find((t: any) => t.id === id) as any;
                      return (
                        <View key={id} style={styles.selectedTag}>
                          <View style={[styles.tagDot, { backgroundColor: tech?.color }]} />
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
                <TouchableOpacity
                  style={styles.dropdownToggle}
                  onPress={() => setShowDropdown(d => !d)}
                >
                  <Text style={styles.dropdownToggleText}>▼</Text>
                </TouchableOpacity>
              </View>
            </View>

            <View style={[styles.controlGroup, { width: '50%' }]}>
              <Text style={styles.controlLabel}>THỜI GIAN</Text>
              <View style={styles.pillGroup}>
                {TIME_OPTIONS.map(opt => (
                  <TouchableOpacity
                    key={opt.value}
                    style={[styles.pill, timeRange === opt.value && styles.pillActive]}
                    onPress={() => setTimeRange(opt.value)}
                  >
                    <Text style={[styles.pillText, timeRange === opt.value && styles.pillTextActive]}>
                      {opt.label}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          </View>

          {showDropdown && (
            <View style={styles.dropdownPanel}>
              <ScrollView style={styles.dropdownScroll} nestedScrollEnabled>
                {allTechs.filter((t: any) => !selectedTechIds.includes(t.id)).map((t: any) => (
                  <TouchableOpacity
                    key={t.id}
                    style={styles.dropdownItem}
                    onPress={() => { toggleTech(t.id); setShowDropdown(false); }}
                  >
                    <Text style={styles.dropdownItemText}>{t.name}</Text>
                  </TouchableOpacity>
                ))}
              </ScrollView>
            </View>
          )}

          <View style={[styles.controlRow, { marginTop: 12 }]}>
            <View style={styles.controlGroup}>
              <Text style={styles.controlLabel}>DẠNG BIỂU ĐỒ</Text>
              <View style={styles.pillGroup}>
                {[
                  { key: 'line', label: 'Line' },
                  { key: 'bar', label: 'Bar' },
                  { key: 'growth', label: 'Tăng trưởng %' },
                ].map(mode => (
                  <TouchableOpacity
                    key={mode.key}
                    style={[styles.pill, chartMode === mode.key && styles.pillActive]}
                    onPress={() => setChartMode(mode.key)}
                  >
                    <Text style={[styles.pillText, chartMode === mode.key && styles.pillTextActive]}>
                      {mode.label}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          </View>

          <View style={[styles.exportRow, { marginTop: 14 }]}>
            <TouchableOpacity style={styles.btnSecondary} onPress={handleExportPNG}>
              <Text style={styles.btnSecondaryText}>
                {isWeb ? 'Export PNG' : 'Chia sẻ ảnh'}
              </Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.btnPrimary} onPress={handleExportCSV}>
              <Text style={styles.btnPrimaryText}>
                {isWeb ? 'Export CSV' : 'Chia sẻ CSV'}
              </Text>
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.card} ref={chartRef} nativeID="main-chart-wrapper" collapsable={false}>
          <View style={styles.chartHeader}>
            <Text style={styles.sectionTitle}>
              {chartMode === 'growth' ? 'Tăng trưởng % theo thời gian' : 'Số lượng Job Postings theo thời gian'}
            </Text>
            {loadingChart && <ActivityIndicator size="small" color={DM.primaryLight} />}
          </View>

          {visibleData.length > 0 ? (
            <View style={styles.chartFrame}>
              {chartMode === 'bar' ? (
                <BarChart
                  data={groupedBarData}
                  width={barChartWidth}
                  height={radarChartHeight - 40}
                  barWidth={adaptiveBarWidth}
                  initialSpacing={10}
                  endSpacing={16}
                  disableScroll
                  yAxisTextStyle={{ color: DM.text3, fontSize: 10 }}
                  xAxisLabelTextStyle={{ color: DM.text3, fontSize: 10 }}
                  yAxisColor={DM.border}
                  xAxisColor={DM.border}
                  rulesColor={DM.border}
                  rulesType="dashed"
                  noOfSections={4}
                  maxValue={jobChartMaxValue}
                />
              ) : (
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
                  height={radarChartHeight - 40}
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
                  yAxisLabelSuffix={chartMode === 'growth' ? '%' : ''}
                  noOfSections={4}
                  maxValue={chartMode === 'growth' ? growthChartAxis.maxValue : jobChartMaxValue}
                  mostNegativeValue={chartMode === 'growth' ? growthChartAxis.mostNegativeValue : undefined}
                  noOfSectionsBelowXAxis={chartMode === 'growth' ? growthChartAxis.noOfSectionsBelowXAxis : undefined}
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
                            const tech = allTechs.find((t: any) => t.id === techId) as any;
                            return (
                              <View key={idx} style={styles.tooltipRow}>
                                <View style={[styles.tooltipDot, { backgroundColor: tech?.color }]} />
                                <Text style={styles.tooltipName}>{tech?.name}</Text>
                                <Text style={[styles.tooltipValue, chartMode === 'growth' && { color: item.value >= 0 ? DM.green : DM.accent }]}>
                                  {chartMode === 'growth' ? (item.value > 0 ? '+' : '') + item.value + '%' : item.value + ' jobs'}
                                </Text>
                              </View>
                            );
                          })}
                        </View>
                      );
                    },
                  }}
                />
              )}
            </View>
          ) : (
            <View style={{ height: radarChartHeight, justifyContent: 'center', alignItems: 'center' }}>
              <Text style={{ color: DM.text3 }}>Không có dữ liệu</Text>
            </View>
          )}

          <View style={styles.legendRow}>
            {selectedTechIds.map((id: string) => {
              const tech = allTechs.find((t: any) => t.id === id) as any;
              return (
                <View key={id} style={styles.legendItem}>
                  <View style={[styles.legendDot, { backgroundColor: tech?.color }]} />
                  <Text style={styles.legendText}>{tech?.name}</Text>
                </View>
              );
            })}
          </View>
        </View>

        <View style={styles.card}>
          <Text style={styles.sectionTitle}>Top 10 Công nghệ Hot nhất</Text>
          {top10Data.map((t: any, i: number) => (
            <View key={t.keyword} style={styles.top10Item}>
              <Text style={styles.top10Rank}>#{i + 1}</Text>
              <View style={[styles.top10Dot, { backgroundColor: PALETTE[i % PALETTE.length] }]} />
              <Text style={styles.top10Name}>{t.keyword}</Text>
              <Text style={styles.top10Jobs}>{t.job_count?.toLocaleString()} jobs</Text>
            </View>
          ))}
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: DM.bg,
    paddingTop: 48,
    ...(Platform.OS === 'web' && {
      alignSelf: 'center',
      width: '100%',
    })
  },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 16, marginBottom: 16, zIndex: 100,
  },
  headerTitle: { fontSize: 20, fontWeight: '800', color: DM.text },
  headerAccent: { color: DM.primaryLight },
  statsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    paddingHorizontal: 16,
    justifyContent: 'space-between',
    marginBottom: 4
  },
  statCard: {
    backgroundColor: DM.surface, borderWidth: 1, borderColor: DM.border,
    borderRadius: DM.radius, padding: 14,
    width: (screenWidth - 44) / 2,
    marginBottom: 12,
  },
  statHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 },
  statName: { fontWeight: '600', fontSize: 13, color: DM.text2 },
  statJobs: { fontSize: 22, fontWeight: '800', color: DM.text },
  statJobsLabel: { fontSize: 11, fontWeight: '400', color: DM.text3 },
  statMeta: { fontSize: 10, color: DM.text3, marginTop: 4 },
  badge: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 99, borderWidth: 1 },
  badgeUp: { backgroundColor: DM.greenGlow, borderColor: DM.green },
  badgeDown: { backgroundColor: DM.accentGlow, borderColor: DM.accent },
  badgeFlat: { backgroundColor: DM.yellowGlow, borderColor: DM.yellow },
  badgeText: { fontSize: 10, fontWeight: '700' },
  badgeUpText: { color: DM.green },
  badgeDownText: { color: DM.accent },
  badgeFlatText: { color: DM.yellow },
  badgePrimary: {
    backgroundColor: DM.primaryGlow, borderWidth: 1, borderColor: DM.primary,
    paddingHorizontal: 8, paddingVertical: 2, borderRadius: 99,
  },
  badgePrimaryText: { color: DM.primaryLight, fontSize: 10, fontWeight: '600' },
  card: {
    backgroundColor: DM.surface, borderWidth: 1, borderColor: DM.border,
    borderRadius: DM.radius, padding: 16, marginHorizontal: 16, marginBottom: 12,
  },
  controlRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 16, flexWrap: 'wrap' },
  controlGroup: { gap: 6 },
  controlLabel: { fontSize: 10, fontWeight: '700', color: DM.text3, letterSpacing: 0.8, marginBottom: 4 },
  selectBox: {
    flexDirection: 'row', alignItems: 'center',
    backgroundColor: DM.surface2, borderWidth: 1, borderColor: DM.border,
    borderRadius: DM.radiusSm, minHeight: 38, paddingLeft: 6,
  },
  tagRow: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingVertical: 4 },
  selectedTag: {
    flexDirection: 'row', alignItems: 'center', gap: 3,
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
  pillGroup: { flexDirection: 'row', gap: 4, flexWrap: 'wrap' },
  pill: {
    paddingHorizontal: 12, paddingVertical: 6, borderRadius: 99,
    borderWidth: 1, borderColor: DM.border, backgroundColor: DM.surface2,
  },
  pillActive: { backgroundColor: DM.primaryGlow, borderColor: DM.primary },
  pillText: { fontSize: 11, fontWeight: '500', color: DM.text2 },
  pillTextActive: { color: DM.primaryLight, fontWeight: '600' },
  exportRow: {
    flexDirection: 'row', gap: 8, justifyContent: 'flex-end',
    borderTopWidth: 1, borderTopColor: DM.border, paddingTop: 12,
  },
  btnSecondary: {
    paddingHorizontal: 14, paddingVertical: 8, borderRadius: DM.radiusSm,
    backgroundColor: DM.surface2, borderWidth: 1, borderColor: DM.border,
  },
  btnSecondaryText: { fontSize: 12, fontWeight: '600', color: DM.text2 },
  btnPrimary: {
    paddingHorizontal: 14, paddingVertical: 8, borderRadius: DM.radiusSm,
    backgroundColor: DM.primary,
  },
  btnPrimaryText: { fontSize: 12, fontWeight: '600', color: '#000' },
  chartHeader: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    marginBottom: 16, flexWrap: 'wrap', gap: 8,
  },
  sectionTitle: { fontSize: 15, fontWeight: '700', color: DM.text },
  chartFrame: { marginLeft: -10, paddingRight: 20 },
  chart: { borderRadius: DM.radius, marginLeft: -8 },
  legendRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginTop: 12 },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  legendDot: { width: 8, height: 8, borderRadius: 4 },
  legendText: { fontSize: 11, color: DM.text2 },
  tooltipDismiss: {
    alignSelf: 'flex-end' as const, marginTop: 6,
    paddingHorizontal: 10, paddingVertical: 4,
    backgroundColor: DM.surface2, borderRadius: DM.radiusSm,
    borderWidth: 1, borderColor: DM.border,
  },
  tooltipDismissText: { fontSize: 11, color: DM.text3 },
  top10Item: {
    flexDirection: 'row', alignItems: 'center', gap: 8,
    paddingVertical: 8, paddingHorizontal: 10, borderRadius: DM.radiusSm,
    backgroundColor: DM.bg2, borderWidth: 1, borderColor: DM.border, marginBottom: 6,
  },
  top10Hot: { borderColor: DM.accent, backgroundColor: 'rgba(255, 101, 132, 0.05)' },
  top10Rank: { color: DM.text3, fontWeight: '700', fontSize: 11, width: 24 },
  top10Dot: { width: 10, height: 10, borderRadius: 5 },
  top10Name: { flex: 1, fontWeight: '600', color: DM.text, fontSize: 13 },
  top10Jobs: { color: DM.text3, fontSize: 11 },
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
    flexDirection: 'row',
    alignItems: 'center',
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
