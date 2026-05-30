import React, { useState, useMemo, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  TextInput,
  FlatList,
  StatusBar,
  Platform,
  Dimensions,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { WebView } from 'react-native-webview';
import { getClusters, getClusterById } from '../../api/clusterService';

const SCREEN_HEIGHT = Dimensions.get('window').height;

// ─── Colors ───────────────────────────────────────────────────────────────────
const COLORS = [
  '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEEAD',
  '#D4A5A5', '#9B59B6', '#3498DB', '#E67E22', '#2ECC71',
  '#F1C40F', '#E74C3C', '#1ABC9C', '#34495E', '#FF9FF3',
  '#00D2D3', '#54A0FF', '#5F27CD', '#C8D6E5', '#FF9F43',
  '#01A3A4', '#EE5253',
];



// ─── Types ───────────────────────────────────────────────────────────────────
interface Cluster {
  cluster_id: number;
  label: string;
  label_en: string;
  description: string;
  domain: string;
  confidence: number;
  member_count: number;
  sample_techs: string[];
}



// ─── Build force-graph HTML for a cluster ────────────────────────────────────
function buildGraphHtml(cluster: Cluster, color: string): string {
  const centerId = `cluster-${cluster.cluster_id}`;
  const safeTechs = cluster.sample_techs || [];

  const nodes = [
    { id: centerId, name: cluster.label, isCenter: true, val: 80 },
    ...safeTechs.map(tech => ({ id: `tech-${tech}`, name: tech, isCenter: false, val: 10 })),
  ];

  const links = safeTechs.map(tech => ({
    source: centerId,
    target: `tech-${tech}`,
  }));

  const graphDataStr = JSON.stringify({ nodes, links });

  return `<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0"/>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { background: #0a0a0a; overflow: hidden; width: 100vw; height: 100vh; }
    #graph { width: 100%; height: 100%; }
  </style>
  <script src="https://unpkg.com/force-graph"></script>
</head>
<body>
  <div id="graph"></div>
  <script>
    window.onerror = function(msg, url, line, col, error) {
      document.body.innerHTML = '<div style="color:red; font-size:14px; padding:20px; word-wrap:break-word;">JS Error: ' + msg + '<br>Line: ' + line + '</div>';
    };
  </script>
  <script>
    const COLOR = '${color}';
    const gData = ${graphDataStr};

    const Graph = ForceGraph()(document.getElementById('graph'))
      .backgroundColor('#0a0a0a')
      .graphData(gData)
      .nodeCanvasObject((node, ctx, globalScale) => {
        const isCenter = node.isCenter;
        // Center node radius ~22, child ~8
        const r = isCenter ? 22 : 8;

        // Glow for center
        if (isCenter) {
          const nx = node.x || 0;
          const ny = node.y || 0;
          const grad = ctx.createRadialGradient(nx, ny, r * 0.5, nx, ny, r * 3);
          grad.addColorStop(0, COLOR + '55');
          grad.addColorStop(1, 'transparent');
          ctx.beginPath();
          ctx.arc(nx, ny, r * 3, 0, 2 * Math.PI);
          ctx.fillStyle = grad;
          ctx.fill();
        }

        // Node circle
        const nx = node.x || 0;
        const ny = node.y || 0;
        ctx.beginPath();
        ctx.arc(nx, ny, r, 0, 2 * Math.PI, false);
        ctx.fillStyle = COLOR;
        ctx.globalAlpha = isCenter ? 1 : 0.75;
        ctx.fill();

        // White ring for center
        if (isCenter) {
          ctx.globalAlpha = 1;
          ctx.lineWidth = 3 / globalScale;
          ctx.strokeStyle = '#ffffff';
          ctx.stroke();
        }
        ctx.globalAlpha = 1;

        // Labels: always show center, show children when zoomed or always on small clusters
        const alwaysShowChild = gData.nodes.length <= 12;
        const showLabel = isCenter || alwaysShowChild || globalScale > 1.2;
        if (showLabel) {
          const fontSize = isCenter
            ? Math.max(10, 15 / globalScale)
            : Math.max(8, 11 / globalScale);
          ctx.font = (isCenter ? 'bold ' : '') + fontSize + 'px sans-serif';
          ctx.textAlign = 'center';
          ctx.textBaseline = 'top';
          ctx.fillStyle = isCenter ? '#ffffff' : '#dddddd';
          // Shadow for readability
          ctx.shadowColor = '#000';
          ctx.shadowBlur = 4;
          ctx.fillText(node.name, nx, ny + r + 3 / globalScale);
          ctx.shadowBlur = 0;
        }
      })
      .nodePointerAreaPaint((node, color, ctx) => {
        const nx = node.x || 0;
        const ny = node.y || 0;
        const r = node.isCenter ? 22 : 8;
        ctx.beginPath();
        ctx.arc(nx, ny, r, 0, 2 * Math.PI);
        ctx.fillStyle = color;
        ctx.fill();
      })
      .linkCanvasObject((link, ctx, globalScale) => {
        const s = link.source, t = link.target;
        if (typeof s !== 'object' || typeof t !== 'object') return;

        const sx = s.x || 0;
        const sy = s.y || 0;
        const tx = t.x || 0;
        const ty = t.y || 0;

        // Gradient line from center outward
        const grad = ctx.createLinearGradient(sx, sy, tx, ty);
        grad.addColorStop(0, COLOR + 'cc');
        grad.addColorStop(1, COLOR + '44');

        ctx.beginPath();
        ctx.moveTo(sx, sy);
        ctx.lineTo(tx, ty);
        ctx.strokeStyle = grad;
        ctx.lineWidth = 2 / globalScale;
        ctx.stroke();
      })
      .linkDirectionalParticles(0);

    // Proper force config AFTER graph init
    Graph.d3Force('charge').strength(-350);
    Graph.d3Force('link').distance(110);

    // Wait for simulation then fit
    setTimeout(() => {
      if (Graph.zoomToFit) Graph.zoomToFit(700, 40);
    }, 600);
  </script>
</body>
</html>`;
}


// ─── Main Screen ─────────────────────────────────────────────────────────────
export default function ClusterScreen() {
  const insets = useSafeAreaInsets();
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCluster, setSelectedCluster] = useState<Cluster | null>(null);
  const [clusters, setClusters] = useState<Cluster[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  React.useEffect(() => {
    const fetchClusters = async () => {
      try {
        setLoading(true);
        const response = await getClusters();
        const data = response.data || response;
        const dataArray = Array.isArray(data) ? data : Object.values(data);
        setClusters(dataArray);
      } catch (err: any) {
        setError(err.message || 'Failed to load clusters');
        console.error("Error fetching clusters:", err);
      } finally {
        setLoading(false);
      }
    };
    fetchClusters();
  }, []);

  const filteredClusters = useMemo(() => {
    const q = searchQuery.toLowerCase();
    return clusters.filter(c => {
      if (!q) return true;
      return (
        c.label?.toLowerCase().includes(q) ||
        c.domain?.toLowerCase().includes(q) ||
        c.sample_techs?.some(t => t.toLowerCase().includes(q))
      );
    });
  }, [searchQuery, clusters]);

  if (selectedCluster) {
    return (
      <DetailView
        cluster={selectedCluster}
        onBack={() => setSelectedCluster(null)}
        insets={insets}
      />
    );
  }

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      <StatusBar barStyle="light-content" backgroundColor="#000" />

      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Phân cụm Công nghệ</Text>
        <Text style={styles.headerSub}>
          {clusters.length} cụm · Chọn để xem chi tiết
        </Text>
      </View>

      {/* Search */}
      <View style={styles.searchContainer}>
        <Ionicons name="search" size={18} color="#666" style={{ marginRight: 8 }} />
        <TextInput
          style={styles.searchInput}
          placeholder="Tìm cụm hoặc công nghệ..."
          placeholderTextColor="#555"
          value={searchQuery}
          onChangeText={setSearchQuery}
        />
        {searchQuery.length > 0 && (
          <TouchableOpacity onPress={() => setSearchQuery('')}>
            <Ionicons name="close-circle" size={18} color="#555" />
          </TouchableOpacity>
        )}
      </View>

      {/* Grid */}
      {loading ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <Text style={{ color: '#888' }}>Đang tải dữ liệu cụm...</Text>
        </View>
      ) : error ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <Text style={{ color: '#ff6b6b' }}>Lỗi: {error}</Text>
        </View>
      ) : (
        <FlatList
          data={filteredClusters}
          keyExtractor={item => String(item.cluster_id)}
          numColumns={2}
          contentContainerStyle={styles.gridContent}
          columnWrapperStyle={styles.columnWrapper}
          showsVerticalScrollIndicator={false}
          ListEmptyComponent={
            <View style={styles.emptyContainer}>
              <Ionicons name="search-outline" size={48} color="#333" />
              <Text style={styles.emptyText}>Không tìm thấy cụm phù hợp</Text>
            </View>
          }
          renderItem={({ item }) => (
            <ClusterCard cluster={item} onPress={() => setSelectedCluster(item)} />
          )}
        />
      )}
    </View>
  );
}

// ─── Cluster Card ─────────────────────────────────────────────────────────────
function ClusterCard({ cluster, onPress }: { cluster: Cluster; onPress: () => void }) {
  const color = COLORS[cluster.cluster_id % COLORS.length];

  const confidence = Math.round(cluster.confidence * 100);

  return (
    <TouchableOpacity
      style={[styles.card, { borderTopColor: color }]}
      onPress={onPress}
      activeOpacity={0.75}
    >
      <View style={[styles.domainBadge, { backgroundColor: color + '22' }]}>
        <Text style={[styles.domainText, { color }]} numberOfLines={1}>
          {cluster.domain}
        </Text>
      </View>

      <Text style={styles.cardLabel} numberOfLines={2}>{cluster.label}</Text>

      <View style={styles.cardStats}>
        <View style={styles.statPill}>
          <Text style={styles.statText}>{cluster.n_members || cluster.member_count || 0}</Text>
        </View>
        <View style={styles.statPill}>
          <Text style={styles.statText}>{confidence}%</Text>
        </View>
      </View>

      <View style={styles.techPreview}>
        {(cluster.sample_techs || cluster.members || []).slice(0, 3).map(tech => (
          <View key={tech} style={[styles.techChipSmall, { borderColor: color + '55' }]}>
            <Text style={styles.techChipSmallText} numberOfLines={1}>{tech}</Text>
          </View>
        ))}
        {(cluster.sample_techs || cluster.members || []).length > 3 && (
          <Text style={styles.moreTechs}>+{(cluster.sample_techs || cluster.members || []).length - 3}</Text>
        )}
      </View>
    </TouchableOpacity>
  );
}

// ─── Detail View (Graph + Info) ───────────────────────────────────────────────
function DetailView({
  cluster,
  onBack,
  insets,
}: {
  cluster: Cluster;
  onBack: () => void;
  insets: { top: number; bottom: number };
}) {
  const [detail, setDetail] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  React.useEffect(() => {
    const fetchDetail = async () => {
      try {
        const res = await getClusterById(cluster.cluster_id);
        setDetail(res.data || res);
      } catch (err) {
        console.error("Detail load error:", err);
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [cluster.cluster_id]);

  const color = COLORS[cluster.cluster_id % COLORS.length];

  const confidence = Math.round((detail?.confidence ?? cluster.confidence) * 100);
  const techList = detail?.members || detail?.sample_techs || cluster.sample_techs || [];
  const patchedCluster = { ...cluster, ...detail, sample_techs: techList };
  const htmlContent = buildGraphHtml(patchedCluster as Cluster, color);

  // Graph takes ~50% of screen height, info panel below
  const graphHeight = Math.round(SCREEN_HEIGHT * 0.48);

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      {/* Top bar */}
      <View style={styles.detailTopBar}>
        <TouchableOpacity onPress={onBack} style={styles.backButton}>
          <Ionicons name="arrow-back" size={22} color="#fff" />
        </TouchableOpacity>
        <Text style={styles.detailTopTitle} numberOfLines={1}>{cluster.label}</Text>
        <View style={{ width: 40 }} />
      </View>

      {/* Force Graph */}
      <View style={[styles.graphContainer, { height: graphHeight }]}>
        {Platform.OS === 'web' ? (
          <iframe
            key={`web-iframe-${techList.length}`}
            // @ts-ignore
            srcDoc={htmlContent}
            style={{ width: '100%', height: '100%', border: 'none', backgroundColor: '#0a0a0a' }}
          />
        ) : (
          <WebView
            key={`app-webview-${techList.length}`}
            source={{ html: htmlContent }}
            style={{ flex: 1, backgroundColor: '#0a0a0a' }}
            scrollEnabled={false}
            bounces={false}
            javaScriptEnabled={true}
            domStorageEnabled={true}
            originWhitelist={['*']}
          />
        )}

        <View style={[styles.graphLabelBadge, { borderColor: color + '55' }]}>
          <Text style={[styles.graphLabelText, { color }]}>{cluster.domain}</Text>
        </View>
      </View>

      {/* Info panel */}
      <ScrollView
        style={{ flex: 1 }}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={[styles.detailScroll, { paddingBottom: insets.bottom + 24 }]}
      >
        {/* Hero */}
        <View style={[styles.detailHero, { borderLeftColor: color }]}>
          <Text style={styles.detailLabel}>{cluster.label}</Text>
          <Text style={styles.detailLabelEn}>{cluster.label_en}</Text>
          <Text style={styles.detailDesc}>{cluster.description}</Text>
        </View>

        <View style={styles.statsRow}>
          <StatBox label="Công nghệ" value={String(detail?.n_members || detail?.member_count || cluster.member_count || 0)} color={color} />
          <View style={styles.statsDivider} />
          <StatBox label="Tin cậy" value={`${confidence}%`} color={color} />
          <View style={styles.statsDivider} />
          <StatBox label="Cụm #" value={`#${cluster.cluster_id}`} color={color} />
        </View>

        {/* Tech chips */}
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Danh sách Công nghệ ({techList.length})</Text>
        </View>

        {loading ? (
          <Text style={{ color: '#888', marginTop: 10 }}>Đang tải danh sách công nghệ...</Text>
        ) : (
          <View style={styles.techGrid}>
            {techList.map((tech: string) => (
              <View
                key={tech}
                style={[styles.techChip, { borderColor: color + '55', backgroundColor: color + '11' }]}
              >
                <Text style={styles.techChipText}>{tech}</Text>
              </View>
            ))}
          </View>
        )}
      </ScrollView>
    </View>
  );
}

function StatBox({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <View style={styles.statBox}>
      <Text style={styles.statBoxValue}>{value}</Text>
      <Text style={styles.statBoxLabel}>{label}</Text>
    </View>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────
const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000000' },

  header: { paddingHorizontal: 20, paddingTop: 16, paddingBottom: 12 },
  headerTitle: { fontSize: 26, fontWeight: '800', color: '#FFFFFF', letterSpacing: -0.5 },
  headerSub: { fontSize: 13, color: '#888', marginTop: 4 },

  searchContainer: {
    flexDirection: 'row', alignItems: 'center',
    marginHorizontal: 16, marginBottom: 12,
    paddingHorizontal: 14,
    paddingVertical: Platform.OS === 'android' ? 8 : 11,
    backgroundColor: '#1a1a1a', borderRadius: 14,
    borderWidth: 1, borderColor: '#2a2a2a',
  },
  searchInput: { flex: 1, fontSize: 14, color: '#fff' },

  gridContent: { paddingHorizontal: 12, paddingBottom: 24 },
  columnWrapper: { gap: 10, marginBottom: 10 },

  card: {
    flex: 1, backgroundColor: '#111', borderRadius: 16, padding: 14,
    borderTopWidth: 3, borderWidth: 1, borderColor: '#1e1e1e',
  },
  domainBadge: {
    flexDirection: 'row', alignItems: 'center', alignSelf: 'flex-start',
    paddingHorizontal: 8, paddingVertical: 3, borderRadius: 20, marginBottom: 8,
  },
  domainText: { fontSize: 10, fontWeight: '700', letterSpacing: 0.3 },
  cardLabel: { fontSize: 13, fontWeight: '700', color: '#FFFFFF', marginBottom: 8, lineHeight: 18 },
  cardStats: { flexDirection: 'row', gap: 6, marginBottom: 10 },
  statPill: {
    flexDirection: 'row', alignItems: 'center', gap: 4,
    backgroundColor: '#1e1e1e', paddingHorizontal: 7, paddingVertical: 3, borderRadius: 20,
  },
  statText: { fontSize: 10, color: '#888', fontWeight: '600' },
  techPreview: { flexDirection: 'row', flexWrap: 'wrap', gap: 4 },
  techChipSmall: {
    paddingHorizontal: 6, paddingVertical: 2, borderRadius: 6,
    borderWidth: 1, borderColor: '#333', backgroundColor: '#1a1a1a',
  },
  techChipSmallText: { fontSize: 9, color: '#aaa', fontWeight: '500' },
  moreTechs: { fontSize: 9, color: '#555', alignSelf: 'center', fontWeight: '600' },

  emptyContainer: { flex: 1, alignItems: 'center', paddingTop: 80, gap: 12 },
  emptyText: { color: '#444', fontSize: 15 },

  // Detail
  detailTopBar: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: 16, paddingVertical: 12,
    borderBottomWidth: 1, borderBottomColor: '#1a1a1a',
  },
  backButton: {
    width: 40, height: 40, borderRadius: 20,
    backgroundColor: '#1a1a1a', alignItems: 'center', justifyContent: 'center',
  },
  detailTopTitle: {
    flex: 1, textAlign: 'center', fontSize: 15,
    fontWeight: '700', color: '#fff', marginHorizontal: 8,
  },

  // Graph
  graphContainer: {
    width: '100%', backgroundColor: '#0a0a0a',
    borderBottomWidth: 1, borderBottomColor: '#1a1a1a',
    position: 'relative',
  },
  graphLabelBadge: {
    position: 'absolute', top: 12, left: 12,
    flexDirection: 'row', alignItems: 'center', gap: 6,
    backgroundColor: 'rgba(0,0,0,0.7)', paddingHorizontal: 10, paddingVertical: 4,
    borderRadius: 99, borderWidth: 1,
  },
  graphLabelDot: { width: 8, height: 8, borderRadius: 4 },
  graphLabelText: { fontSize: 11, fontWeight: '700' },

  detailScroll: { padding: 20 },
  detailHero: { borderLeftWidth: 4, paddingLeft: 16, marginBottom: 20 },
  detailLabel: { fontSize: 22, fontWeight: '800', color: '#fff', marginBottom: 2 },
  detailLabelEn: { fontSize: 13, color: '#555', marginBottom: 10, fontStyle: 'italic' },
  detailDesc: { fontSize: 13, color: '#aaa', lineHeight: 20 },

  statsRow: {
    flexDirection: 'row', backgroundColor: '#111', borderRadius: 16,
    borderWidth: 1, borderColor: '#1e1e1e', marginBottom: 20, padding: 16,
  },
  statBox: { flex: 1, alignItems: 'center' },
  statsDivider: { width: 1, backgroundColor: '#1e1e1e' },
  statBoxValue: { fontSize: 18, fontWeight: '800', color: '#fff', marginBottom: 2 },
  statBoxLabel: { fontSize: 10, color: '#666' },

  sectionHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 12 },
  sectionTitle: { fontSize: 15, fontWeight: '700', color: '#fff' },
  techGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  techChip: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: 12, paddingVertical: 7,
    borderRadius: 24, borderWidth: 1, gap: 6,
  },
  techDot: { width: 6, height: 6, borderRadius: 3 },
  techChipText: { fontSize: 13, color: '#ddd', fontWeight: '500' },
});
