import { useState, useEffect, useLayoutEffect, useCallback, useMemo, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import ForceGraph2D from 'react-force-graph-2d';
import type { ForceGraphMethods, NodeObject, LinkObject } from 'react-force-graph-2d';
import { exploreGraph, analyzeRoad, filterGraph } from '../api/graphService';
import { useAppContext } from '../contexts/appContextStore';
import { useToast } from '../components/common/toastContext';
import { useNodeSuggest } from '../hooks/useNodeSuggest';
import { normalizeGraphNodes, normalizeGraphLinks } from '../utils/graphNormalize';
import type { GraphNode, GraphLink } from '../utils/graphNormalize';
import { NODE_TYPES, DEFAULT_NODE_TYPE, COMMUNITY_PALETTE, OTHER_COMMUNITY_COLOR } from '../utils/graphNodeTypes';
import { PATH_HIGHLIGHT_COLOR, LINK_TYPE_COLORS, LINK_TYPE_LABELS, linkTypeLabel } from '../utils/graphLinkTypes';
import BrowseFilterPanel from '../components/graph/BrowseFilterPanel';
import type { BrowseFilters } from '../components/graph/BrowseFilterPanel';
import BrowseResultsGrid from '../components/graph/BrowseResultsGrid';
import GraphLegendPanel from '../components/graph/GraphLegendPanel';
import JourneyPanel from '../components/graph/JourneyPanel';
import EdgeDetailPanel from '../components/graph/EdgeDetailPanel';
import type { ResolvedGraphLink } from '../components/graph/EdgeDetailPanel';
import MaintenancePage from './MaintenancePage';
import MaintenanceOverlay from '../components/common/MaintenanceOverlay';
import type { RawGraphNode } from '../utils/graphNormalize';
import './GraphExplorer.css';

// Canvas không đọc được biến CSS nên lấy trực tiếp giá trị hex của nền/--primary/--accent trong global.css
const CANVAS_BG = '#060810';
const PING_COLOR_PRIMARY = '#4f9dff';
const PING_COLOR_ACCENT = '#9b8cff';
const PING_PERIOD_MS = 1800;
const PING_RING_COUNT = 2;
const PING_MAX_GROWTH = 20;
// Từ ngưỡng này trở lên, chỉ hiện nhãn cho node đang focus/hover/trên lộ trình để đỡ rối chữ
const DENSE_NODE_THRESHOLD = 20;
// Bán kính node technology ở chế độ Phân tích đồ thị, nội suy theo pagerank_score chuẩn hoá
// trong khoảng dữ liệu hiện có (không phải giá trị tuyệt đối, vì thang PageRank phụ thuộc số
// node/cạnh của đồ thị con đang xem).
const ANALYTICS_MIN_RADIUS = 6;
const ANALYTICS_MAX_RADIUS = 22;

type FeatureTab = 'explore' | 'journey' | 'browse';
const VALID_TABS: FeatureTab[] = ['explore', 'journey', 'browse'];

// Chỉ khai báo `type`/`label` — `source`/`target` do LinkObject<N,L> tự thêm dưới dạng
// `string | number | NodeObject<N>` (string lúc khởi tạo, bị react-force-graph-2d mutate thành
// tham chiếu node khi simulation chạy). Khai báo lại 2 field này ở đây sẽ giao với union đó và
// thu hẹp về đúng `string`, khiến paintLink/isLinkTypeShown không bao giờ narrow được sang node.
interface GraphLinkExtra {
    type: string;
    label: string;
    [key: string]: unknown;
}

type GExploreLink = LinkObject<GraphNode, GraphLinkExtra>;

interface SelectableNode {
    id: string;
    label?: string;
    type?: string;
}

// Dropdown gợi ý node dùng chung cho ô search "Khám phá" và 2 ô "Phân tích lộ trình"
function NodeSuggestDropdown({ results, onSelect }: { results: GraphNode[]; onSelect: (node: GraphNode) => void }) {
    if (results.length === 0) return null;
    return (
        <div className="search-dropdown">
            {results.map(n => (
                <button key={n.id} className="search-result-item" onClick={() => onSelect(n)}>
                    <span className="srd-type-badge" style={{ background: NODE_TYPES[n.type]?.color + '33', color: NODE_TYPES[n.type]?.color }}>
                        {n.type}
                    </span>
                    {n.label || n.id}
                </button>
            ))}
        </div>
    );
}

export default function GraphExplorer() {
    const context = useAppContext();
    const settings = context?.settings;
    const notify = useToast();
    const fgRef = useRef<ForceGraphMethods<NodeObject<GraphNode>, GExploreLink> | undefined>(undefined);
    const canvasWrapperRef = useRef<HTMLDivElement>(null);
    const [canvasSize, setCanvasSize] = useState({ width: 0, height: 0 });

    // Đọc state ban đầu từ URL (?tab=&node=&depth=&from=&to=) để view có thể chia sẻ/bookmark
    // và không mất khi refresh trang — chỉ đọc 1 lần lúc mount, sau đó state là nguồn sự thật.
    const [searchParams, setSearchParams] = useSearchParams();

    // -- KHAI BÁO TẤT CẢ HOOKS Ở ĐÂY (TRƯỚC KHI RETURN) --
    const [graphData, setGraphData] = useState<{ nodes: GraphNode[]; links: GExploreLink[] }>({ nodes: [], links: [] });
    const [loading, setLoading] = useState(false);

    const [searchQuery, setSearchQuery] = useState('');

    const [selectedEdge, setSelectedEdge] = useState<ResolvedGraphLink | null>(null);
    const [hoveredNode, setHoveredNode] = useState<NodeObject<GraphNode> | null>(null);
    const [filters, setFilters] = useState({ salary: 0, location: 'all' });

    const [focusNodeIds, setFocusNodeIds] = useState<string[]>(() => {
        const n = searchParams.get('node');
        return n ? [n] : ['AI'];
    });
    const [depth, setDepth] = useState<1 | 2>(() => (searchParams.get('depth') === '2' ? 2 : 1));
    const [location, setLocation] = useState('');
    const [minSalary, setMinSalary] = useState('');
    const [nodeCount, setNodeCount] = useState(0);

    // Lịch sử điều hướng (breadcrumb) ở tab Khám phá — cho phép quay lại node đã xem trước đó
    const [history, setHistory] = useState<{ id: string; label: string }[]>(() => {
        const n = searchParams.get('node');
        return [{ id: n || 'AI', label: n || 'AI' }];
    });

    const [activeFeature, setActiveFeature] = useState<FeatureTab>(() => {
        const t = searchParams.get('tab');
        return t && (VALID_TABS as string[]).includes(t) ? (t as FeatureTab) : 'explore';
    });
    const [pathStart, setPathStart] = useState<SelectableNode | null>(() => {
        const f = searchParams.get('from');
        return f ? { id: f, label: f, type: 'technology' } : null;
    });
    const [pathEnd, setPathEnd] = useState<SelectableNode | null>(() => {
        const t = searchParams.get('to');
        return t ? { id: t, label: t, type: 'technology' } : null;
    });
    const [activePath, setActivePath] = useState<{ nodes: GraphNode[]; links: GraphLink[] } | null>(null);

    const [journeyStartQuery, setJourneyStartQuery] = useState(() => searchParams.get('from') || '');
    const [journeyEndQuery, setJourneyEndQuery] = useState(() => searchParams.get('to') || '');

    // -- Ẩn/hiện theo loại node + chú giải thu gọn --
    const [hiddenTypes, setHiddenTypes] = useState<Set<string>>(() => new Set());
    const [legendOpen, setLegendOpen] = useState(false);

    // Chế độ "Phân tích đồ thị": tô màu node technology theo cộng đồng (Louvain) + đổi kích cỡ
    // theo PageRank, thay cho màu/kích cỡ cố định theo loại node — xem paintNode bên dưới.
    const [analyticsView, setAnalyticsView] = useState(false);

    // -- Chế độ "Duyệt bộ lọc" (/graph/filter — không cần từ khóa gốc) --
    const [browseFilters, setBrowseFilters] = useState<BrowseFilters>({ locations: [], nodeTypes: [], sentiment: '', minSalary: '', maxSalary: '' });
    const [browseResults, setBrowseResults] = useState<RawGraphNode[]>([]);
    const [browseLoading, setBrowseLoading] = useState(false);
    const [browseError, setBrowseError] = useState('');
    const [browseSearched, setBrowseSearched] = useState(false);

    // -- TẤT CẢ EFFECT VÀ CALLBACK PHẢI Ở ĐÂY --

    // Fetch data từ backend khi focusNodeIds hoặc depth thay đổi
    useEffect(() => {
        const fetchGraph = async () => {
            if (!settings || settings.isGraphEnabled === false) return;
            if (focusNodeIds.length === 0) return;

            setLoading(true);
            try {
                const res = await exploreGraph(focusNodeIds, depth, location, minSalary);
                if (res?.data) {
                    const nodes = normalizeGraphNodes(res.data.nodes);
                    const links = normalizeGraphLinks(res.data.edges || res.data.links);

                    setGraphData({ nodes, links });
                    setNodeCount(nodes.length);

                    setTimeout(() => {
                        if (fgRef.current) fgRef.current.zoomToFit(600, 60);
                    }, 500);
                }
            } catch (err) {
                console.error("Lỗi lấy dữ liệu graph:", err);
                const message = (err as Error)?.message || '';
                if (message.includes('403') || message.includes('503')) {
                    context?.updateSettings({ isGraphEnabled: false });
                } else {
                    notify({ title: 'Không tải được dữ liệu đồ thị', body: 'Vui lòng thử lại sau.', variant: 'error' });
                }
            } finally {
                setLoading(false);
            }
        };
        fetchGraph();
        // context/settings intentionally omitted: context is a new object every render
        // and would refetch on every render; settings?.isGraphEnabled already covers the one field that matters.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [focusNodeIds, depth, location, minSalary, settings?.isGraphEnabled]);

    // Đo kích thước thật của khung canvas để truyền vào ForceGraph2D — nếu không, thư viện
    // mặc định lấy window.innerWidth/innerHeight làm kích thước vẽ, khiến canvas "phình" to hơn
    // khung chứa (flexbox min-height:auto) và đẩy dock/legend/tooltip ra ngoài vùng nhìn thấy.
    useLayoutEffect(() => {
        const el = canvasWrapperRef.current;
        if (!el) return undefined;
        const update = () => setCanvasSize({ width: el.clientWidth, height: el.clientHeight });
        update();
        const ro = new ResizeObserver(update);
        ro.observe(el);
        return () => ro.disconnect();
    }, [activeFeature]);

    // Đồng bộ view hiện tại lên URL (tab/node/depth hoặc from/to) — giúp chia sẻ/bookmark/refresh
    // không mất trạng thái. Dùng replace để tránh spam lịch sử trình duyệt; back/forward của tab đã
    // có breadcrumb riêng đảm nhiệm.
    useEffect(() => {
        const params: Record<string, string> = { tab: activeFeature };
        if (activeFeature === 'explore' && focusNodeIds[0]) {
            params.node = focusNodeIds[0];
            params.depth = String(depth);
        } else if (activeFeature === 'journey' && pathStart && pathEnd) {
            params.from = pathStart.id;
            params.to = pathEnd.id;
        }
        setSearchParams(params, { replace: true });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [activeFeature, focusNodeIds, depth, pathStart, pathEnd]);


    // Điều chỉnh lực đẩy
    useEffect(() => {
        if (fgRef.current) {
            fgRef.current.d3Force('link')?.distance(200);
            fgRef.current.d3Force('charge')?.strength(-500);
            fgRef.current.d3ReheatSimulation();
        }
    }, [graphData]);

    // Gợi ý node cho ô search "Khám phá" + 2 ô "Xuất phát/Điểm đến" của tab lộ trình — dẫn xuất
    // thuần từ graphData/query hiện tại nên dùng useMemo (qua hook dùng chung), không cần state/effect riêng.
    const searchResults = useNodeSuggest(searchQuery, graphData.nodes, focusNodeIds[0]);
    const journeyStartResults = useNodeSuggest(journeyStartQuery, graphData.nodes, pathStart?.label || pathStart?.id);
    const journeyEndResults = useNodeSuggest(journeyEndQuery, graphData.nodes, pathEnd?.label || pathEnd?.id);

    // Điều hướng đến 1 node ở tab Khám phá + cập nhật breadcrumb: nếu node đã có trong lịch sử
    // (VD: bấm lại 1 mục breadcrumb) thì cắt bớt các bước sau nó, không cộng dồn trùng lặp.
    const navigateToNode = useCallback((node: SelectableNode) => {
        const keyword = node.label || node.id;
        setHistory(h => {
            const idx = h.findIndex(item => item.id.toLowerCase() === keyword.toLowerCase());
            if (idx !== -1) return h.slice(0, idx + 1);
            return [...h, { id: keyword, label: keyword }];
        });
        setFocusNodeIds([keyword]);
    }, []);

    const handleSearch = (node: SelectableNode) => {
        const searchKeyword = node.label || node.id;
        setSearchQuery(searchKeyword);
        navigateToNode(node);
        setTimeout(() => {
            if (fgRef.current) fgRef.current.centerAt(0, 0, 400);
        }, 300);
    };

    const handleSearchSubmit = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter' && searchQuery.trim() !== '') {
            navigateToNode({ id: searchQuery.trim(), label: searchQuery.trim() });
        }
    };

    const handleReset = () => {
        setFocusNodeIds(['AI']);
        setHistory([{ id: 'AI', label: 'AI' }]);
        setDepth(1);
        setSearchQuery('');
        setLocation('');
        setMinSalary('');
        setSelectedEdge(null);
        setPathStart(null);
        setPathEnd(null);
        setActivePath(null);
        setFilters({ salary: 0, location: 'all' });
        setHiddenTypes(new Set());
    };

    const handleFitView = () => {
        if (fgRef.current) fgRef.current.zoomToFit(600, 60);
    };

    const handleZoomBy = (factor: number) => {
        if (!fgRef.current) return;
        fgRef.current.zoom(fgRef.current.zoom() * factor, 300);
    };

    const toggleFeature = (feat: FeatureTab) => {
        setActiveFeature(feat);
        if (feat === 'explore') {
            setPathStart(null);
            setPathEnd(null);
            setActivePath(null);
        }
    };

    const toggleNodeTypeHidden = (type: string) => {
        setHiddenTypes(prev => {
            const next = new Set(prev);
            if (next.has(type)) next.delete(type);
            else next.add(type);
            return next;
        });
    };

    const toggleBrowseLocation = (loc: string) => {
        setBrowseFilters(f => ({
            ...f,
            locations: f.locations.includes(loc) ? f.locations.filter(l => l !== loc) : [...f.locations, loc],
        }));
    };

    const toggleBrowseNodeType = (nt: string) => {
        setBrowseFilters(f => ({
            ...f,
            nodeTypes: f.nodeTypes.includes(nt) ? f.nodeTypes.filter(t => t !== nt) : [...f.nodeTypes, nt],
        }));
    };

    const handleBrowseSearch = async () => {
        setBrowseLoading(true);
        setBrowseError('');
        setBrowseSearched(true);
        try {
            const res = await filterGraph(browseFilters);
            setBrowseResults(res?.data || []);
        } catch (err) {
            console.error('Lỗi duyệt bộ lọc:', err);
            setBrowseError('Không thể tải kết quả. Vui lòng thử lại.');
            setBrowseResults([]);
        } finally {
            setBrowseLoading(false);
        }
    };

    const handleBrowseResultClick = (node: RawGraphNode) => {
        const keyword = node.name || node.properties?.title || node.id || '';
        setActiveFeature('explore');
        setHistory([{ id: keyword, label: keyword }]);
        setFocusNodeIds([keyword]);
    };

    const handleJourneySelectStart = (node: SelectableNode) => {
        setPathStart(node);
        setJourneyStartQuery(node.label || node.id);
    };

    const handleJourneySelectEnd = (node: SelectableNode) => {
        setPathEnd(node);
        setJourneyEndQuery(node.label || node.id);
    };

    // Dùng chung cho nút "Xóa" ở toolbar, nút ✕ trên panel lộ trình, và phím Escape —
    // để 3 lối tắt này luôn xóa sạch y hệt nhau (kể cả 2 ô nhập), không có trạng thái nửa vời.
    const clearJourney = useCallback(() => {
        setPathStart(null);
        setPathEnd(null);
        setActivePath(null);
        setJourneyStartQuery('');
        setJourneyEndQuery('');
    }, []);

    // Đảo chiều Từ ↔ Đến — thao tác chuẩn của mọi UI tìm đường (Google Maps, tìm vé máy bay...)
    const handleSwapJourney = () => {
        setPathStart(pathEnd);
        setPathEnd(pathStart);
        setJourneyStartQuery(journeyEndQuery);
        setJourneyEndQuery(journeyStartQuery);
    };

    // Phím Escape đóng panel/legend đang mở — nhất quán với các nút ✕/Xóa tương ứng
    useEffect(() => {
        const onKeyDown = (e: KeyboardEvent) => {
            if (e.key !== 'Escape') return;
            if (legendOpen) { setLegendOpen(false); return; }
            if (selectedEdge) { setSelectedEdge(null); return; }
            if (activeFeature === 'journey' && activePath) { clearJourney(); }
        };
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, [legendOpen, selectedEdge, activeFeature, activePath, clearJourney]);

    // Server-side path finding
    useEffect(() => {
        if (activeFeature === 'journey' && pathStart && pathEnd) {
            const fetchPath = async () => {
                if (!settings || settings.isGraphEnabled === false) return;

                setLoading(true);
                try {
                    const res = await analyzeRoad(pathStart.id, pathEnd.id);
                    if (res?.data && res.data.found) {
                        const pathNodes = normalizeGraphNodes(res.data.nodes);
                        const pathLinks = normalizeGraphLinks(res.data.edges);

                        setActivePath({ nodes: pathNodes, links: pathLinks });

                        setGraphData({
                            nodes: pathNodes.map(pn => ({ ...pn })),
                            links: pathLinks.map(pl => ({ ...pl }))
                        });
                        setNodeCount(pathNodes.length);

                        setTimeout(() => {
                            if (fgRef.current) fgRef.current.zoomToFit(800, 50);
                        }, 500);
                    } else {
                        setActivePath(null);
                        notify({ title: 'Không tìm thấy đường đi', body: `Không có kết nối nào giữa "${pathStart.label || pathStart.id}" và "${pathEnd.label || pathEnd.id}".`, variant: 'error' });
                    }
                } catch (error) {
                    console.error("Lỗi tìm đường đi:", error);
                    setActivePath(null);
                    notify({ title: 'Không thể tìm đường đi', body: 'Vui lòng thử lại sau.', variant: 'error' });
                } finally {
                    setLoading(false);
                }
            };
            fetchPath();
        }
        // settings intentionally omitted: only settings?.isGraphEnabled matters here.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [pathStart, pathEnd, activeFeature, settings?.isGraphEnabled]);


    // Khoảng pagerank_score thực tế trong đồ thị con đang xem, để nội suy bán kính node ở chế độ
    // Phân tích đồ thị — tính lại mỗi khi graphData đổi, KHÔNG tính trong paintNode (chạy mỗi khung
    // hình animation, tính lại toàn bộ node ở đó sẽ rất tốn).
    const analyticsStats = useMemo(() => {
        const techNodes = graphData.nodes.filter(n => n.type === 'technology');
        const scores = techNodes
            .map(n => n.properties?.pagerank_score)
            .filter((v): v is number => typeof v === 'number');

        const communityCounts = new Map<number, number>();
        for (const n of techNodes) {
            const communityId = n.properties?.community_id;
            if (typeof communityId === 'number') {
                communityCounts.set(communityId, (communityCounts.get(communityId) || 0) + 1);
            }
        }

        return {
            hasData: scores.length > 0,
            min: scores.length > 0 ? Math.min(...scores) : 0,
            max: scores.length > 0 ? Math.max(...scores) : 0,
            communityCounts,
        };
    }, [graphData.nodes]);

    const isNodeDimmed = useCallback((node: NodeObject<GraphNode>) => {
        if (node.type === 'company') {
            if (filters.salary > 0 && (Number(node.avg_salary) || 0) < filters.salary) return true;
            if (filters.location !== 'all' && node.location !== filters.location) return true;
        }
        return false;
    }, [filters]);

    // Ẩn hẳn node/link theo loại đã tắt trong chú giải (khác với "làm mờ" theo bộ lọc lương/địa điểm ở trên)
    const isNodeTypeShown = useCallback((node: NodeObject<GraphNode>) => !hiddenTypes.has(node.type), [hiddenTypes]);

    const isLinkTypeShown = useCallback((link: GExploreLink) => {
        const s = typeof link.source === 'object' ? link.source : null;
        const t = typeof link.target === 'object' ? link.target : null;
        if (s && hiddenTypes.has(s.type)) return false;
        if (t && hiddenTypes.has(t.type)) return false;
        return true;
    }, [hiddenTypes]);

    const paintNode = useCallback((node: NodeObject<GraphNode>, ctx: CanvasRenderingContext2D, globalScale: number) => {
        const nt = NODE_TYPES[node.type] || DEFAULT_NODE_TYPE;
        // Phân tích đồ thị chỉ áp dụng cho technology (PageRank/community chỉ tồn tại trên
        // Technology node) — loại khác giữ nguyên màu/kích cỡ theo NODE_TYPES như bình thường.
        const useAnalyticsStyle = analyticsView && node.type === 'technology';
        const communityId = node.properties?.community_id;
        const nodeColor = useAnalyticsStyle
            ? (typeof communityId === 'number' && communityId < COMMUNITY_PALETTE.length
                ? COMMUNITY_PALETTE[communityId]
                : OTHER_COMMUNITY_COLOR)
            : nt.color;
        const analyticsRadius = (() => {
            const score = node.properties?.pagerank_score;
            if (typeof score !== 'number' || analyticsStats.max === analyticsStats.min) return ANALYTICS_MIN_RADIUS;
            const t = (score - analyticsStats.min) / (analyticsStats.max - analyticsStats.min);
            return ANALYTICS_MIN_RADIUS + t * (ANALYTICS_MAX_RADIUS - ANALYTICS_MIN_RADIUS);
        })();
        const dimmed = isNodeDimmed(node);
        const isCenter = focusNodeIds.includes(node.id);
        const isPathNode = activeFeature === 'journey' && !!activePath?.nodes.some(n => n.id === node.id);
        const r = (useAnalyticsStyle ? analyticsRadius : nt.size) + (isCenter ? 4 : 0);
        const x = node.x || 0;
        const y = node.y || 0;

        ctx.globalAlpha = dimmed ? 0.15 : 1;
        if (isCenter) {
            ctx.shadowBlur = 10; ctx.shadowColor = nodeColor;
        }
        if (isPathNode) {
            ctx.shadowBlur = 14; ctx.shadowColor = PATH_HIGHLIGHT_COLOR;
            ctx.lineWidth = 2 / globalScale;
            ctx.strokeStyle = PATH_HIGHLIGHT_COLOR;
        }

        ctx.beginPath();
        ctx.arc(x, y, r, 0, 2 * Math.PI);
        ctx.fillStyle = nodeColor;
        ctx.fill();

        if (isPathNode) ctx.stroke();

        ctx.shadowBlur = 0;

        // Sonar ping: vòng tròn lan tỏa liên tục quanh node đang được focus
        if (isCenter) {
            ctx.save();
            const cyclePos = (Date.now() % PING_PERIOD_MS) / PING_PERIOD_MS;
            for (let i = 0; i < PING_RING_COUNT; i++) {
                const progress = (cyclePos + i / PING_RING_COUNT) % 1;
                const ringRadius = r + progress * PING_MAX_GROWTH;
                ctx.beginPath();
                ctx.arc(x, y, ringRadius, 0, 2 * Math.PI);
                ctx.lineWidth = 1.5 / globalScale;
                ctx.strokeStyle = i % 2 === 0 ? PING_COLOR_PRIMARY : PING_COLOR_ACCENT;
                ctx.globalAlpha = (1 - progress) * 0.45;
                ctx.stroke();
            }
            ctx.restore();
        }

        // Đồ thị đông node (VD: depth=2) dễ chồng chữ — chỉ hiện nhãn cho node đang focus/hover/trên
        // lộ trình, còn lại ẩn nhãn để đỡ rối, thay vì hiện nhãn của toàn bộ node cùng lúc.
        const isDense = graphData.nodes.length > DENSE_NODE_THRESHOLD;
        const shouldLabel = !isDense || isCenter || isPathNode || hoveredNode?.id === node.id;

        if (globalScale >= 0.8 && shouldLabel) {
            ctx.font = `600 ${Math.min(14 / globalScale, 12)}px Inter, sans-serif`;
            ctx.fillStyle = '#E8EAF6';
            ctx.textAlign = 'center';
            ctx.fillText(node.label || node.id, x, y + r + 10);
        }
        ctx.globalAlpha = 1;
    }, [focusNodeIds, isNodeDimmed, activePath, activeFeature, graphData.nodes.length, hoveredNode, analyticsView, analyticsStats]);

    const handleNodeClick = useCallback((node: NodeObject<GraphNode>) => {
        setSelectedEdge(null);

        if (activeFeature === 'journey') {
            if (!pathStart) {
                handleJourneySelectStart(node);
            } else if (!pathEnd && pathStart.id !== node.id) {
                handleJourneySelectEnd(node);
            } else {
                setPathStart(node);
                setPathEnd(null);
                setActivePath(null);
            }
        } else {
            navigateToNode(node);
            if (fgRef.current) {
                fgRef.current.centerAt(node.x, node.y, 600);
                fgRef.current.zoom(2, 800);
            }
        }
    }, [pathStart, pathEnd, activeFeature, navigateToNode]);

    const handleNodeHover = useCallback((node: NodeObject<GraphNode> | null) => {
        setHoveredNode(node || null);
        document.body.style.cursor = node ? 'pointer' : 'default';
    }, []);

    // handleNodeHover chỉnh thẳng document.body.style.cursor — nếu rời trang khi đang hover 1 node
    // (con trỏ đang là 'pointer'), phải trả lại 'default' lúc unmount, kẻo các trang khác thừa hưởng nhầm.
    useEffect(() => () => { document.body.style.cursor = 'default'; }, []);

    const handleLinkClick = useCallback((link: GExploreLink) => {
        setSelectedEdge(link as unknown as ResolvedGraphLink);
    }, []);

    const isLinkInPath = useCallback((link: GExploreLink) => {
        if (activeFeature !== 'journey' || !activePath) return false;
        return activePath.links.some(l => {
            const s1 = l.source;
            const t1 = l.target;
            const s2 = typeof link.source === 'object' ? link.source?.id : link.source;
            const t2 = typeof link.target === 'object' ? link.target?.id : link.target;
            return (s1 === s2 && t1 === t2) || (s1 === t2 && t1 === s2);
        });
    }, [activePath, activeFeature]);

    const linkColor = useCallback((link: GExploreLink) => isLinkInPath(link) ? PATH_HIGHLIGHT_COLOR : (LINK_TYPE_COLORS[link.type] || '#5c6494'), [isLinkInPath]);
    const linkWidth = useCallback((link: GExploreLink) => isLinkInPath(link) ? 5 : (selectedEdge === (link as unknown as ResolvedGraphLink) ? 3 : 1.2), [selectedEdge, isLinkInPath]);

    const paintLink = useCallback((link: GExploreLink, ctx: CanvasRenderingContext2D, globalScale: number) => {
        if (globalScale < 1.2) return;

        const start = link.source;
        const end = link.target;
        if (typeof start !== 'object' || typeof end !== 'object' || !start || !end) return;

        const label = linkTypeLabel(link);
        const fontSize = 13 / globalScale;
        ctx.font = `bold ${fontSize}px Inter, sans-serif`;
        const textWidth = ctx.measureText(label).width;

        const startX = start.x || 0;
        const startY = start.y || 0;
        const endX = end.x || 0;
        const endY = end.y || 0;

        const textPos = {
            x: startX + (endX - startX) / 2,
            y: startY + (endY - startY) / 2
        };

        const relAngle = Math.atan2(endY - startY, endX - startX);

        let rotation = relAngle;
        if (rotation > Math.PI / 2) rotation -= Math.PI;
        if (rotation < -Math.PI / 2) rotation += Math.PI;

        ctx.save();
        ctx.translate(textPos.x, textPos.y);
        ctx.rotate(rotation);

        ctx.fillStyle = 'rgba(13, 15, 26, 0.85)';
        ctx.beginPath();
        const padding = 3 / globalScale;
        const h = fontSize + padding * 2;
        const w = textWidth + padding * 4;
        ctx.roundRect(-w / 2, -h / 2, w, h, 3 / globalScale);
        ctx.fill();

        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillStyle = LINK_TYPE_COLORS[link.type] || '#9FA8C7';
        ctx.fillText(label, 0, 0);
        ctx.restore();
    }, []);

    // -- CUỐI CÙNG MỚI ĐẾN CÁC LỆNH RETURN ĐIỀU KIỆN --

    if (!settings) {
        return (
            <div className="graph-page flex-center" style={{ minHeight: '100vh', background: 'var(--bg)' }}>
                <div className="loading-spinner"></div>
                <span style={{ color: 'var(--text-3)', marginLeft: 12 }}>Đang kiểm tra trạng thái...</span>
            </div>
        );
    }

    if (settings.isGraphEnabled === false) {
        return (
            <MaintenanceOverlay>
                <MaintenancePage message="Chúng tôi đang bảo trì graph theo định kỳ. Vui lòng quay lại sau." />
            </MaintenanceOverlay>
        );
    }

    const showEmptyExplore = !loading && activeFeature === 'explore' && graphData.nodes.length === 0;
    const showEmptyJourney = !loading && activeFeature === 'journey' && !activePath && !!pathStart && !!pathEnd;

    // Chỉ liệt kê trong chú giải những loại quan hệ THỰC SỰ xuất hiện ở đồ thị đang xem — tránh liệt
    // kê hết cả 9 loại quan hệ có thể có trong hệ thống dù đa số không liên quan đến view hiện tại.
    const presentLinkTypes = [...new Set(graphData.links.map(l => l.type))].filter((t): t is string => !!t && t in LINK_TYPE_COLORS);

    return (
        <div className="graph-page">
            <div className="graph-page-header">
                <h1 className="graph-title">Bản đồ <span className="graph-title-accent">Công nghệ</span></h1>
                <p className="graph-subtitle">Khám phá mối liên hệ giữa công nghệ, công ty, kỹ năng và vị trí qua đồ thị tri thức — bấm vào 1 node để xem tiếp, cuộn để phóng to/thu nhỏ.</p>
            </div>

            <div className="graph-toolbar card">
                <div className="feature-switcher-tabs">
                    <button className={`feat-tab${activeFeature === 'explore' ? ' active' : ''}`} onClick={() => toggleFeature('explore')}>Khám phá</button>
                    <button className={`feat-tab${activeFeature === 'journey' ? ' active' : ''}`} onClick={() => toggleFeature('journey')}>Lộ trình</button>
                    <button className={`feat-tab${activeFeature === 'browse' ? ' active' : ''}`} onClick={() => toggleFeature('browse')}>Duyệt bộ lọc</button>
                </div>

                <div className="toolbar-main">
                    {activeFeature === 'browse' && (
                        <p className="toolbar-hint">
                            Duyệt toàn bộ đồ thị theo địa điểm, loại node và cảm xúc — không cần nhập từ khóa gốc.
                        </p>
                    )}

                    {activeFeature === 'explore' && (
                        <div className="search-input-wrap">
                            <input
                                className="search-input"
                                placeholder="Tìm kiếm/Nhập keyword và ấn Enter..."
                                value={searchQuery}
                                onChange={e => setSearchQuery(e.target.value)}
                                onKeyDown={handleSearchSubmit}
                            />
                            <NodeSuggestDropdown results={searchResults} onSelect={handleSearch} />
                        </div>
                    )}

                    {activeFeature === 'journey' && (
                        <div className="journey-inline-row">
                            <div className="journey-field">
                                <span className="journey-tag start">Từ</span>
                                <div className="search-input-wrap">
                                    <input
                                        className="search-input"
                                        placeholder="Điểm xuất phát..."
                                        value={journeyStartQuery}
                                        onChange={e => setJourneyStartQuery(e.target.value)}
                                        onKeyDown={(e) => { if (e.key === 'Enter') handleJourneySelectStart({ id: journeyStartQuery, label: journeyStartQuery, type: 'technology' }); }}
                                    />
                                    <NodeSuggestDropdown results={journeyStartResults} onSelect={handleJourneySelectStart} />
                                </div>
                            </div>

                            <button type="button" className="journey-arrow" title="Đổi chiều" aria-label="Đổi chiều xuất phát/điểm đến" onClick={handleSwapJourney}>⇄</button>

                            <div className="journey-field">
                                <span className="journey-tag end">Đến</span>
                                <div className="search-input-wrap">
                                    <input
                                        className="search-input"
                                        placeholder="Điểm đến..."
                                        value={journeyEndQuery}
                                        onChange={e => setJourneyEndQuery(e.target.value)}
                                        onKeyDown={(e) => { if (e.key === 'Enter') handleJourneySelectEnd({ id: journeyEndQuery, label: journeyEndQuery, type: 'technology' }); }}
                                    />
                                    <NodeSuggestDropdown results={journeyEndResults} onSelect={handleJourneySelectEnd} />
                                </div>
                            </div>

                            <div className="journey-actions">
                                <button
                                    className="btn btn-primary"
                                    onClick={() => {
                                        if (journeyStartQuery) handleJourneySelectStart({ id: journeyStartQuery, label: journeyStartQuery, type: 'technology' });
                                        if (journeyEndQuery) handleJourneySelectEnd({ id: journeyEndQuery, label: journeyEndQuery, type: 'technology' });
                                    }}
                                    disabled={!journeyStartQuery || !journeyEndQuery}
                                >
                                    Tìm đường
                                </button>
                                {(pathStart || pathEnd) && (
                                    <button className="btn btn-ghost" onClick={clearJourney}>
                                        Xóa
                                    </button>
                                )}
                            </div>
                        </div>
                    )}
                </div>

                {activeFeature !== 'browse' && (
                    <div className="toolbar-controls">
                        {activeFeature === 'explore' && (
                            <div className="pill-group">
                                <button className={`pill${depth === 1 ? ' active' : ''}`} onClick={() => setDepth(1)}>1 hop</button>
                                <button className={`pill${depth === 2 ? ' active' : ''}`} onClick={() => setDepth(2)}>2 hops</button>
                            </div>
                        )}
                        <button className="btn btn-ghost" onClick={handleFitView}>Vừa khung</button>
                        <button className="btn btn-secondary" onClick={handleReset}>Reset</button>
                    </div>
                )}
            </div>

            <div className="graph-body">
                {activeFeature === 'browse' ? (
                    <>
                        <BrowseFilterPanel
                            filters={browseFilters}
                            onToggleLocation={toggleBrowseLocation}
                            onToggleNodeType={toggleBrowseNodeType}
                            onSentimentChange={(val) => setBrowseFilters(f => ({ ...f, sentiment: val }))}
                            onMinSalaryChange={(val) => setBrowseFilters(f => ({ ...f, minSalary: val }))}
                            onMaxSalaryChange={(val) => setBrowseFilters(f => ({ ...f, maxSalary: val }))}
                            onSearch={handleBrowseSearch}
                            loading={browseLoading}
                        />

                        <BrowseResultsGrid
                            results={browseResults}
                            error={browseError}
                            searched={browseSearched}
                            loading={browseLoading}
                            onResultClick={handleBrowseResultClick}
                        />
                    </>
                ) : (
                    <div className="graph-canvas-wrapper" ref={canvasWrapperRef}>
                        {activeFeature === 'explore' && history.length > 1 && (
                            <nav className="graph-breadcrumb" aria-label="Lịch sử điều hướng">
                                {history.map((item, i) => (
                                    <span key={`${item.id}-${i}`} className="breadcrumb-item-wrap">
                                        {i > 0 && <span className="breadcrumb-sep" aria-hidden="true">›</span>}
                                        {i === history.length - 1 ? (
                                            <span className="breadcrumb-item current">{item.label}</span>
                                        ) : (
                                            <button type="button" className="breadcrumb-item" onClick={() => navigateToNode(item)}>
                                                {item.label}
                                            </button>
                                        )}
                                    </span>
                                ))}
                            </nav>
                        )}

                        {loading && (
                            <div className="graph-loading-overlay">
                                <div className="loading-spinner"></div>
                                <span>{activeFeature === 'journey' ? 'Đang tìm đường đi...' : 'Đang tải đồ thị...'}</span>
                            </div>
                        )}

                        {(showEmptyExplore || showEmptyJourney) && (
                            <div className="graph-empty-state">
                                {showEmptyExplore ? (
                                    <p>Không tìm thấy dữ liệu cho "<b>{focusNodeIds[0]}</b>". Thử một từ khóa khác.</p>
                                ) : (
                                    <p>Không tìm thấy đường đi giữa "<b>{pathStart?.label || pathStart?.id}</b>" và "<b>{pathEnd?.label || pathEnd?.id}</b>".</p>
                                )}
                            </div>
                        )}

                        {canvasSize.width > 0 && canvasSize.height > 0 && (
                            <ForceGraph2D
                                ref={fgRef}
                                width={canvasSize.width}
                                height={canvasSize.height}
                                graphData={graphData}
                                autoPauseRedraw={false}
                                nodeCanvasObject={paintNode}
                                nodeCanvasObjectMode={() => 'replace'}
                                nodeVisibility={isNodeTypeShown}
                                linkVisibility={isLinkTypeShown}
                                onNodeClick={handleNodeClick}
                                onNodeHover={handleNodeHover}
                                onLinkClick={handleLinkClick}
                                linkColor={linkColor}
                                linkWidth={linkWidth}
                                linkCanvasObject={paintLink}
                                linkCanvasObjectMode={() => 'after'}
                                linkDirectionalArrowLength={8}
                                linkDirectionalArrowRelPos={0.8}
                                backgroundColor={CANVAS_BG}
                            />
                        )}

                        {hoveredNode && (
                            <div className="node-tooltip">
                                <div className="nt-header">
                                    <span className="nt-type-badge" style={{ background: NODE_TYPES[hoveredNode.type]?.color + '33', color: NODE_TYPES[hoveredNode.type]?.color }}>
                                        {hoveredNode.type}
                                    </span>
                                    <strong>{hoveredNode.label || hoveredNode.id}</strong>
                                </div>
                            </div>
                        )}

                        {analyticsView && !analyticsStats.hasData && (
                            <div className="graph-analytics-hint">
                                Chưa có dữ liệu phân tích đồ thị cho các công nghệ đang hiển thị — admin cần chạy
                                "Phân tích đồ thị" ở trang Vận hành trước.
                            </div>
                        )}

                        <div className="graph-dock">
                            <div className="dock-group">
                                <span className="dock-count">{nodeCount} nodes</span>
                            </div>
                            <div className="dock-group">
                                <button type="button" className="dock-btn" title="Thu nhỏ" aria-label="Thu nhỏ" onClick={() => handleZoomBy(0.75)}>−</button>
                                <button type="button" className="dock-btn" title="Phóng to" aria-label="Phóng to" onClick={() => handleZoomBy(1.33)}>+</button>
                                <button type="button" className="dock-btn" title="Vừa khung hình" aria-label="Vừa khung hình" onClick={handleFitView}>⤢</button>
                                <button
                                    type="button"
                                    className={`dock-btn${analyticsView ? ' active' : ''}`}
                                    title="Phân tích đồ thị (kích cỡ = PageRank, màu = cộng đồng công nghệ)"
                                    aria-label="Phân tích đồ thị"
                                    aria-pressed={analyticsView}
                                    onClick={() => setAnalyticsView(v => !v)}
                                >◎</button>
                                <button type="button" className={`dock-btn${legendOpen ? ' active' : ''}`} title="Chú giải" aria-label="Chú giải" aria-expanded={legendOpen} onClick={() => setLegendOpen(o => !o)}>i</button>
                            </div>
                        </div>

                        {legendOpen && (
                            <GraphLegendPanel
                                hiddenTypes={hiddenTypes}
                                onToggleNodeType={toggleNodeTypeHidden}
                                presentLinkTypes={presentLinkTypes}
                                analyticsView={analyticsView}
                                communityCounts={analyticsStats.communityCounts}
                            />
                        )}
                    </div>
                )}

                {activeFeature !== 'browse' && (activePath || selectedEdge) && (
                    <div className="graph-side-panels">
                        {activePath && (
                            <JourneyPanel
                                pathStart={pathStart}
                                pathEnd={pathEnd}
                                activePath={activePath}
                                onClose={clearJourney}
                            />
                        )}
                        {selectedEdge && (
                            <EdgeDetailPanel edge={selectedEdge} onClose={() => setSelectedEdge(null)} />
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}
