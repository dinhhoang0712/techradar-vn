import { DM } from '@/constants/theme';
import React, { useEffect, useRef, useState } from 'react';
import {
    ActivityIndicator,
    Dimensions,
    KeyboardAvoidingView,
    Platform,
    ScrollView,
    StyleSheet,
    Text,
    TextInput,
    TouchableOpacity,
    View,
    Alert
} from 'react-native';
import { WebView } from 'react-native-webview';
import { exploreGraph, analyzeRoad } from '../../api/graphService';
import { getSystemStatus } from '../../api/authService';
import MaintenanceOverlay from '../../components/MaintenanceOverlay';

const LINK_COLORS: Record<string, string> = {
    USES: '#6C63FF',
    REQUIRES: '#00D68F',
    LOCATED_IN: '#FFC94D',
    RELATED_TO: '#FF6584',
    TAGGED_WITH: '#54C5F8',
};

const NODE_TYPES: any = {
    technology: { color: '#6C63FF', size: 10 },
    job: { color: '#00D68F', size: 6 },
    company: { color: '#FFC94D', size: 8 },
    location: { color: '#FF6584', size: 8 },
    requirement: { color: '#54C5F8', size: 6 },
};

const getNodeDisplayLabel = (node: any) => {
    const props = node?.properties || {};
    const candidates = [
        node?.label,
        props.name,
        props.full_name,
        props.title,
        props.job_title,
        props.company_name,
        props.keyword,
        props.tech_name,
        props.label,
        props.value,
        node?.name,
        node?.title,
        node?.company_name,
        node?.keyword,
        node?.tech_name,
        node?.id,
    ];

    for (const candidate of candidates) {
        if (typeof candidate === 'string' && candidate.trim()) {
            return candidate.trim();
        }
    }

    for (const value of Object.values(props)) {
        if (typeof value === 'string' && value.trim()) {
            return value.trim();
        }
    }

    return String(node?.id ?? '');
};

const HTML_CONTENT = `
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0"/>
    <style> body { margin: 0; padding: 0; background-color: #050505; overflow: hidden; } </style>
    <script src="https://unpkg.com/force-graph"></script>
</head>
<body>
    <div id="graph"></div>
    <script>
        const colorMap = {
            'Technology': '#6C63FF',
            'Job': '#00D68F',
            'Company': '#FFC94D',
            'Location': '#FF6584',
            'Requirement': '#54C5F8'
        };
        const sizeMap = {
            'Technology': 5,
            'Job': 3.5,
            'Company': 4.5,
            'Location': 4.5,
            'Requirement': 3.5
        };

        const getNodeLabel = (node) => {
            const props = node.properties || {};
            const candidates = [
                node.label,
                props.name,
                props.full_name,
                props.title,
                props.job_title,
                props.company_name,
                props.keyword,
                props.tech_name,
                props.label,
                props.value,
                node.name,
                node.title,
                node.company_name,
                node.keyword,
                node.tech_name,
                node.id
            ];

            for (const candidate of candidates) {
                if (typeof candidate === 'string' && candidate.trim()) {
                    return candidate.trim();
                }
            }

            for (const value of Object.values(props)) {
                if (typeof value === 'string' && value.trim()) {
                    return value.trim();
                }
            }

            return String(node.id || '');
        };

        const Graph = ForceGraph()(document.getElementById('graph'))
            .backgroundColor('#050505')
            .nodeRelSize(5)
            .nodeColor(n => colorMap[n.labels?.[0]] || '#9FA8C7')
            .nodeLabel(n => getNodeLabel(n));
            
        // Giảm lực đẩy và khoảng cách cạnh để gom các node lại gần nhau
        Graph.d3Force('charge').strength(-100);
        Graph.d3Force('link').distance(10);

        Graph.nodeCanvasObject((node, ctx, globalScale) => {
                const label = getNodeLabel(node);
                
                // Draw node circle (Phân loại kích thước)
                const r = sizeMap[node.labels?.[0]] || 4;
                const color = colorMap[node.labels?.[0]] || '#9FA8C7';
                ctx.fillStyle = color;
                ctx.beginPath(); ctx.arc(node.x, node.y, r, 0, 2 * Math.PI, false); ctx.fill();

                // Only draw label if zoomed in enough
                if (globalScale > 0.8) {
                    const fontSize = 10/globalScale; 
                    ctx.font = fontSize + "px Sans-Serif";
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'middle';
                    ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
                    ctx.fillText(label, node.x, node.y + (r + 4) / globalScale);
                }
            })
            .nodePointerAreaPaint((node, color, ctx) => {
                const r = sizeMap[node.labels?.[0]] || 4;
                ctx.fillStyle = color;
                ctx.beginPath(); ctx.arc(node.x, node.y, r, 0, 2 * Math.PI, false); ctx.fill();
            })
            .linkColor(() => 'rgba(255,255,255,0.18)') // Sáng hơn một chút xíu
            .linkWidth(0.8) // Dày hơn xíu
            .linkCanvasObject((link, ctx, globalScale) => {
                const start = link.source;
                const end = link.target;
                if (typeof start !== 'object' || typeof end !== 'object') return;
                const isJourneyLink = link.isJourneyPath === true;
                const dx = end.x - start.x;
                const dy = end.y - start.y;
                const angle = Math.atan2(dy, dx);

                // 1. Draw the line
                ctx.beginPath();
                ctx.moveTo(start.x, start.y);
                ctx.lineTo(end.x, end.y);
                ctx.strokeStyle = isJourneyLink ? '#FFD166' : 'rgba(255, 255, 255, 0.7)';
                ctx.lineWidth = (isJourneyLink ? 2.6 : 1.5) / globalScale;
                ctx.stroke();

                if (isJourneyLink) {
                    const targetRadius = (sizeMap[end.labels?.[0]] || 4) + 2;
                    const arrowLength = 13 / globalScale;
                    const arrowWidth = 7 / globalScale;
                    const tipX = end.x - Math.cos(angle) * targetRadius;
                    const tipY = end.y - Math.sin(angle) * targetRadius;

                    ctx.beginPath();
                    ctx.moveTo(tipX, tipY);
                    ctx.lineTo(
                        tipX - arrowLength * Math.cos(angle) + arrowWidth * Math.sin(angle),
                        tipY - arrowLength * Math.sin(angle) - arrowWidth * Math.cos(angle)
                    );
                    ctx.lineTo(
                        tipX - arrowLength * Math.cos(angle) - arrowWidth * Math.sin(angle),
                        tipY - arrowLength * Math.sin(angle) + arrowWidth * Math.cos(angle)
                    );
                    ctx.closePath();
                    ctx.fillStyle = '#FFD166';
                    ctx.fill();
                }

                // 2. Draw the label (always visible)
                const label = link.type || '';
                if (label) {
                    const fontSize = 9 / globalScale;
                    ctx.font = 'bold ' + fontSize + 'px Sans-Serif';

                    const textPos = {
                        x: start.x + (end.x - start.x) / 2,
                        y: start.y + (end.y - start.y) / 2
                    };

                    ctx.save();
                    ctx.translate(textPos.x, textPos.y);
                    ctx.rotate(angle);

                    const textWidth = ctx.measureText(label).width;
                    const pad = fontSize * 0.4;

                    ctx.fillStyle = 'rgba(5, 5, 5, 0.85)';
                    ctx.fillRect(-textWidth / 2 - pad, -fontSize / 2 - pad, textWidth + pad * 2, fontSize + pad * 2);

                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'middle';
                    ctx.fillStyle = 'rgba(255, 255, 255, 0.95)';
                    ctx.fillText(label, 0, 0);
                    ctx.restore();
                }
            })
            .onNodeClick(node => {
                const label = getNodeLabel(node);
                const msg = JSON.stringify({ type: 'nodeClick', node: { id: node.id, label } });
                if (window.ReactNativeWebView) {
                    window.ReactNativeWebView.postMessage(msg);
                } else {
                    window.parent.postMessage(msg, '*');
                }
            });

        const handleMessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                if (data.type === 'updateGraph') {
                    Graph.graphData(data.graphData);
                    setTimeout(() => {
                        const nodeCount = data.graphData.nodes.length;
                        if (nodeCount < 5) {
                            // Nếu có quá ít node, không dùng zoomToFit vì nó sẽ phóng to khổng lồ
                            Graph.centerAt(0, 0, 400);
                            Graph.zoom(1.2, 400);
                        } else {
                            Graph.zoomToFit(400, 40);
                            // Sửa lại thời gian đợi: Đợi animation 400ms chạy xong mới kiểm tra và clamp zoom
                            setTimeout(() => {
                                if (Graph.zoom() > 1.2) {
                                    Graph.zoom(1.2, 300);
                                    Graph.centerAt(0, 0, 300);
                                }
                            }, 450);
                        }
                    }, 100);
                }
            } catch(e) {}
        };
        
        window.addEventListener('message', handleMessage);
        document.addEventListener('message', handleMessage); // For some older Android
    </script>
</body>
</html>
`;

export default function GraphScreen() {
    const webviewRef = useRef<WebView>(null);
    const [activeFeature, setActiveFeature] = useState<'explore' | 'journey'>('explore');

    // Explore state
    const [searchQuery, setSearchQuery] = useState('');
    const [focusLabel, setFocusLabel] = useState('Golang');
    const [depth, setDepth] = useState(1);
    const [location, setLocation] = useState('');
    const [minSalary, setMinSalary] = useState('');
    
    // Journey state
    const [pathStart, setPathStart] = useState<string>('');
    const [pathEnd, setPathEnd] = useState<string>('');

    const [loading, setLoading] = useState(false);
    const [stats, setStats] = useState({ nodes: 0, links: 0 });
    const [isMaintenance, setIsMaintenance] = useState(false);

    // Handle web message events
    useEffect(() => {
        if (Platform.OS === 'web') {
            const handleWebMessage = (event: MessageEvent) => {
                try {
                    const data = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
                    if (data.type === 'nodeClick') {
                        setSearchQuery(data.node.label || getNodeDisplayLabel(data.node));
                    }
                } catch (e) {}
            };
            window.addEventListener('message', handleWebMessage);
            return () => window.removeEventListener('message', handleWebMessage);
        }
    }, []);

    const fetchExplore = async (keyword: string, d: number, loc: string = '', sal: string = '') => {
        setLoading(true);
        try {
            const res = await exploreGraph([keyword], d, loc, sal);
            if (res?.data) {
                const rawNodes = res.data.nodes || [];
                const rawLinks = res.data.edges || res.data.links || [];
                
                const nodes = rawNodes.map((n: any) => ({
                    ...n,
                    id: n.id || n.keyword || n.name || getNodeDisplayLabel(n),
                    label: getNodeDisplayLabel(n),
                }));
                const links = rawLinks.map((l: any) => ({
                    ...l,
                    source: l.source || l.source_id || l.from,
                    target: l.target || l.target_id || l.to,
                    type: (l.type || l.relation || 'RELATED_TO').toUpperCase()
                }));

                const gData = { nodes, links };
                setStats({ nodes: gData.nodes.length, links: gData.links.length });
                const message = JSON.stringify({ type: 'updateGraph', graphData: gData });
                if (Platform.OS === 'web') {
                    (webviewRef.current as any)?.contentWindow?.postMessage(message, '*');
                } else {
                    webviewRef.current?.postMessage(message);
                }
            }
        } catch (error) {
            console.error("Explore API error:", error);
            Alert.alert("Lỗi", "Không thể lấy dữ liệu đồ thị");
        } finally {
            setLoading(false);
        }
    };

    const fetchJourney = async () => {
        if (!pathStart || !pathEnd) return;
        setLoading(true);
        try {
            const res = await analyzeRoad(pathStart, pathEnd);
            if (res?.data) {
                // The backend returns { paths: [ { nodes: [], links: [] } ] }
                let nodes: any[] = [];
                let links: any[] = [];
                
                if (res.data.paths && res.data.paths.length > 0) {
                    res.data.paths.forEach((p: any) => {
                        nodes = [...nodes, ...(p.nodes || [])];
                        links = [...links, ...(p.edges || p.links || [])];
                    });
                } else if (res.data.nodes && (res.data.edges || res.data.links)) {
                    nodes = res.data.nodes;
                    links = res.data.edges || res.data.links;
                }

                // Chuẩn hóa dữ liệu
                nodes = nodes.map(n => ({
                    ...n,
                    id: n.id || n.keyword || n.name || getNodeDisplayLabel(n),
                    label: getNodeDisplayLabel(n),
                }));
                links = links.map(l => ({
                    ...l,
                    source: l.source || l.source_id || l.from,
                    target: l.target || l.target_id || l.to,
                    type: (l.type || l.relation || 'RELATED_TO').toUpperCase(),
                    isJourneyPath: true
                }));

                // Deduplicate
                const uniqueNodes = Array.from(new Map(nodes.map(n => [n.id, n])).values());
                const uniqueLinks = Array.from(new Map(links.map(l => [`${l.source}-${l.target}`, l])).values());

                const gData = { nodes: uniqueNodes, links: uniqueLinks };
                setStats({ nodes: uniqueNodes.length, links: uniqueLinks.length });
                const message = JSON.stringify({ type: 'updateGraph', graphData: gData });
                if (Platform.OS === 'web') {
                    (webviewRef.current as any)?.contentWindow?.postMessage(message, '*');
                } else {
                    webviewRef.current?.postMessage(message);
                }
            } else {
                Alert.alert("Thông báo", "Không tìm thấy đường đi");
            }
        } catch (error) {
            console.error("Journey API error:", error);
            Alert.alert("Lỗi", "Không thể phân tích lộ trình");
        } finally {
            setLoading(false);
        }
    };

    // Initial load
    useEffect(() => {
        const checkStatus = async () => {
            try {
                const res = await getSystemStatus();
                if (res && (res.feature_graph === false || res.feature_graph === 'false')) {
                    setIsMaintenance(true);
                } else {
                    setIsMaintenance(false);
                }
            } catch(e) {}
        };
        checkStatus();
        const interval = setInterval(checkStatus, 30000);

        if (activeFeature === 'explore') {
            fetchExplore(focusLabel, depth, location, minSalary);
        }

        return () => clearInterval(interval);
    }, [activeFeature]);

    const handleSearch = () => {
        if (!searchQuery.trim()) return;
        setFocusLabel(searchQuery.trim());
        if (activeFeature === 'explore') {
            fetchExplore(searchQuery.trim(), depth, location, minSalary);
        }
    };

    const onMessage = (event: any) => {
        try {
            const data = JSON.parse(event.nativeEvent.data);
            if (data.type === 'nodeClick') {
                const label = data.node.label || getNodeDisplayLabel(data.node);
                if (activeFeature === 'explore') {
                    setFocusLabel(label);
                    setSearchQuery(label);
                    fetchExplore(label, depth, location, minSalary);
                } else {
                    if (!pathStart) {
                        setPathStart(label);
                    } else if (!pathEnd) {
                        setPathEnd(label);
                    } else {
                        setPathStart(label);
                        setPathEnd('');
                    }
                }
            }
        } catch (e) {}
    };

    return (
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={styles.container}>
            <MaintenanceOverlay visible={isMaintenance} />
            <View style={styles.header}>
                <Text style={styles.headerTitle}>Đồ thị quan hệ</Text>
            </View>

            <View style={styles.featureTabs}>
                <TouchableOpacity
                    style={[styles.featTab, activeFeature === 'explore' && styles.featTabActive]}
                    onPress={() => setActiveFeature('explore')}
                >
                    <Text style={[styles.featTabText, activeFeature === 'explore' && styles.featTabTextActive]}>
                        Khám phá
                    </Text>
                </TouchableOpacity>
                <TouchableOpacity
                    style={[styles.featTab, activeFeature === 'journey' && styles.featTabActive]}
                    onPress={() => setActiveFeature('journey')}
                >
                    <Text style={[styles.featTabText, activeFeature === 'journey' && styles.featTabTextActive]}>
                        Phân tích lộ trình
                    </Text>
                </TouchableOpacity>
            </View>

            <View style={styles.card}>
                {activeFeature === 'explore' ? (
                    <>
                        <View style={styles.searchWrap}>
                            <TextInput
                                style={styles.searchInput}
                                placeholder="Tìm node: Golang, VNG, Docker..."
                                placeholderTextColor={DM.text3}
                                value={searchQuery}
                                onChangeText={setSearchQuery}
                                onSubmitEditing={handleSearch}
                            />
                            <TouchableOpacity onPress={handleSearch} style={styles.btnSecondary}>
                                <Text style={styles.btnSecondaryText}>Tìm</Text>
                            </TouchableOpacity>
                        </View>

                        <View style={styles.controlsRow}>
                            <Text style={styles.controlLabel}>DEPTH:</Text>
                            <View style={styles.pillGroup}>
                                {[1, 2].map(d => (
                                    <TouchableOpacity
                                        key={d}
                                        style={[styles.pill, depth === d && styles.pillActive]}
                                        onPress={() => {
                                            setDepth(d);
                                            fetchExplore(focusLabel, d, location, minSalary);
                                        }}
                                    >
                                        <Text style={[styles.pillText, depth === d && styles.pillTextActive]}>{d}</Text>
                                    </TouchableOpacity>
                                ))}
                            </View>
                        </View>
                    </>
                ) : (
                    <View style={styles.journeyContainer}>
                        {/* Xuất phát */}
                        <View style={styles.journeyField}>
                            <View style={styles.journeyLabelRow}>
                                <View style={[styles.journeyTag, { backgroundColor: 'rgba(108, 99, 255, 0.15)' }]}>
                                    <Text style={[styles.journeyTagText, { color: '#6C63FF' }]}>Xuất phát</Text>
                                </View>
                            </View>
                            <TextInput
                                style={styles.searchInput}
                                placeholder="Nhập điểm đi... (VD: Java)"
                                placeholderTextColor={DM.text3}
                                value={pathStart}
                                onChangeText={setPathStart}
                            />
                        </View>

                        <View style={styles.journeyArrowWrap}>
                            <Text style={styles.journeyArrow}>↓</Text>
                        </View>

                        {/* Điểm đến */}
                        <View style={styles.journeyField}>
                            <View style={styles.journeyLabelRow}>
                                <View style={[styles.journeyTag, { backgroundColor: 'rgba(0, 214, 143, 0.15)' }]}>
                                    <Text style={[styles.journeyTagText, { color: '#00D68F' }]}>Điểm đến</Text>
                                </View>
                            </View>
                            <TextInput
                                style={styles.searchInput}
                                placeholder="Nhập điểm đến... (VD: React)"
                                placeholderTextColor={DM.text3}
                                value={pathEnd}
                                onChangeText={setPathEnd}
                            />
                        </View>

                        {/* Actions */}
                        <View style={styles.journeyActionRow}>
                            <TouchableOpacity onPress={fetchJourney} style={[styles.btnPrimary, { flex: 1 }]}>
                                <Text style={styles.btnPrimaryText}>Tìm Lộ Trình</Text>
                            </TouchableOpacity>
                            {(pathStart !== '' || pathEnd !== '') && (
                                <TouchableOpacity 
                                    onPress={() => { setPathStart(''); setPathEnd(''); }} 
                                    style={[styles.btnSecondary, { paddingHorizontal: 20 }]}
                                >
                                    <Text style={styles.btnSecondaryText}>Xóa</Text>
                                </TouchableOpacity>
                            )}
                        </View>
                    </View>
                )}
            </View>

            <View style={styles.graphCard}>
                {loading && (
                    <View style={styles.loadingOverlay}>
                        <ActivityIndicator size="large" color={DM.primary} />
                    </View>
                )}
                {Platform.OS === 'web' ? (
                    <iframe
                        // @ts-ignore
                        ref={webviewRef}
                        srcDoc={HTML_CONTENT}
                        style={{ width: '100%', height: '100%', border: 'none', backgroundColor: '#050505' }}
                    />
                ) : (
                    <WebView
                        ref={webviewRef}
                        source={{ html: HTML_CONTENT }}
                        onMessage={onMessage}
                        style={styles.webview}
                        scrollEnabled={false}
                        bounces={false}
                        javaScriptEnabled={true}
                    />
                )}
                <View style={styles.statsBadge}>
                    <Text style={styles.statsText}>{stats.nodes} nodes · {stats.links} links</Text>
                </View>
            </View>

        </KeyboardAvoidingView>
    );
}

const styles = StyleSheet.create({
    container: { 
        flex: 1, 
        backgroundColor: DM.bg, 
        paddingTop: 48,
        ...(Platform.OS === 'web' && { alignSelf: 'center', width: '100%' })
    },
    header: { paddingHorizontal: 16, marginBottom: 12 },
    headerTitle: { fontSize: 20, fontWeight: '800', color: DM.text },

    featureTabs: {
        flexDirection: 'row', marginHorizontal: 16, marginBottom: 12,
        backgroundColor: 'rgba(13,15,26,0.4)', borderRadius: 12,
        borderWidth: 1, borderColor: DM.border, padding: 4, gap: 4,
    },
    featTab: {
        flex: 1, alignItems: 'center', justifyContent: 'center',
        paddingVertical: 9, borderRadius: 8,
    },
    featTabActive: {
        backgroundColor: DM.primary,
        elevation: 4,
    },
    featTabText: { fontSize: 13, fontWeight: '600', color: DM.text2 },
    featTabTextActive: { color: '#000' },

    card: {
        backgroundColor: DM.surface, borderWidth: 1, borderColor: DM.border,
        borderRadius: DM.radius, padding: 14, marginHorizontal: 16, marginBottom: 12,
    },
    searchWrap: {
        flexDirection: 'row', alignItems: 'center', gap: 8
    },
    searchInput: { 
        flex: 1, color: '#FFFFFF', fontSize: 13, paddingVertical: 10, paddingHorizontal: 12,
        backgroundColor: '#050505', borderRadius: DM.radiusSm, borderWidth: 1, borderColor: DM.border,
        minHeight: 44, textAlignVertical: 'center',
        ...(Platform.OS === 'android' && {
            includeFontPadding: false,
            paddingTop: 0,
            paddingBottom: 0,
        }),
    },
    
    // Journey Styles
    journeyContainer: { gap: 4 },
    journeyField: { gap: 6 },
    journeyLabelRow: { flexDirection: 'row', alignItems: 'center' },
    journeyTag: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 6 },
    journeyTagText: { fontSize: 11, fontWeight: '700' },
    journeyArrowWrap: { alignItems: 'center', marginVertical: -2 },
    journeyArrow: { color: DM.text3, fontSize: 16, fontWeight: '800' },
    journeyActionRow: { flexDirection: 'row', gap: 8, marginTop: 12 },
    
    controlsRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 12 },
    controlLabel: { fontSize: 10, fontWeight: '700', color: DM.text3 },
    pillGroup: { flexDirection: 'row', gap: 4 },
    pill: {
        paddingHorizontal: 10, paddingVertical: 5, borderRadius: 99,
        borderWidth: 1, borderColor: DM.border, backgroundColor: DM.surface2,
    },
    pillActive: { backgroundColor: DM.primaryGlow, borderColor: DM.primary },
    pillText: { fontSize: 11, fontWeight: '500', color: DM.text2 },
    pillTextActive: { color: DM.primaryLight, fontWeight: '600' },
    
    btnSecondary: {
        paddingHorizontal: 16, paddingVertical: 10, borderRadius: DM.radiusSm,
        backgroundColor: DM.surface2, borderWidth: 1, borderColor: DM.border,
    },
    btnSecondaryText: { fontSize: 12, color: DM.text, fontWeight: '600' },
    btnPrimary: {
        paddingVertical: 12, borderRadius: DM.radiusSm, backgroundColor: DM.primary,
        alignItems: 'center'
    },
    btnPrimaryText: { fontSize: 13, color: '#000', fontWeight: '700' },

    graphCard: {
        flex: 1, backgroundColor: DM.bg2, borderWidth: 1, borderColor: DM.border,
        borderRadius: DM.radius, marginHorizontal: 16, marginBottom: 16,
        overflow: 'hidden', position: 'relative'
    },
    webview: { flex: 1, backgroundColor: 'transparent' },
    loadingOverlay: {
        position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
        backgroundColor: 'rgba(5,5,5,0.7)', justifyContent: 'center', alignItems: 'center',
        zIndex: 10
    },
    statsBadge: {
        position: 'absolute', bottom: 12, left: 12,
        backgroundColor: 'rgba(0,0,0,0.6)', paddingHorizontal: 10, paddingVertical: 4,
        borderRadius: 99, borderWidth: 1, borderColor: DM.border
    },
    statsText: { color: DM.text2, fontSize: 10, fontWeight: '600' }
});
