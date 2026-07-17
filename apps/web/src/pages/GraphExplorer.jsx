import { useState, useEffect, useLayoutEffect, useCallback, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import ForceGraph2D from 'react-force-graph-2d';
import { exploreGraph, analyzeRoad, filterGraph } from '../api/graphService';
import { useAppContext } from '../contexts/appContextStore';
import { useToast } from '../components/common/toastContext';
import MaintenancePage from './MaintenancePage';
import MaintenanceOverlay from '../components/common/MaintenanceOverlay';
import './GraphExplorer.css';

const PATH_HIGHLIGHT_COLOR = '#FFD700';

// Danh sách đầy đủ các loại quan hệ thực tế có trong dữ liệu Neo4j của backend (đối chiếu trực tiếp
// với code — không đoán): USES, REQUIRES, RELATED_TO, MENTIONS, POSTED_BY, HIRES_FOR là các loại đang
// được ghi/dùng; IS_TECHNOLOGY, LEADS_TO, IN_RING là loại cũ/khác module nhưng vẫn có thể xuất hiện
// khi truy vấn đồ thị chung nên vẫn cần màu + nhãn để không rơi về tiếng Anh mặc định.
const LINK_TYPE_COLORS = {
    USES: '#6C63FF',
    REQUIRES: '#00D68F',
    RELATED_TO: '#FF6584',
    MENTIONS: '#54C5F8',
    POSTED_BY: '#FFC94D',
    HIRES_FOR: '#FF9800',
    IS_TECHNOLOGY: '#9b8cff',
    LEADS_TO: '#4f9dff',
    IN_RING: '#f6b93b',
};

// Nhãn quan hệ hiển thị cho người dùng — backend thường trả tên quan hệ bằng tiếng Anh (uses,
// requires...), nên luôn dịch theo `type` thay vì hiện thẳng label thô từ backend.
const LINK_TYPE_LABELS = {
    USES: 'Sử dụng',
    REQUIRES: 'Yêu cầu',
    RELATED_TO: 'Liên quan',
    MENTIONS: 'Đề cập',
    POSTED_BY: 'Đăng bởi',
    HIRES_FOR: 'Tuyển cho',
    IS_TECHNOLOGY: 'Thuộc công nghệ',
    LEADS_TO: 'Dẫn đến',
    IN_RING: 'Cùng nhóm',
};
const linkTypeLabel = (link) => LINK_TYPE_LABELS[link?.type] || link?.label || link?.type || '';

const NODE_TYPES = {
    technology: { color: '#6C63FF', size: 10 },
    company: { color: '#FF6584', size: 14 },
    skill: { color: '#00D68F', size: 8 },
    location: { color: '#FFC94D', size: 12 },
    industry: { color: '#54C5F8', size: 12 },
    job: { color: '#FF9800', size: 12 },
};

// Canvas không đọc được biến CSS nên lấy trực tiếp giá trị hex của nền/--primary/--accent trong global.css
const CANVAS_BG = '#060810';
const PING_COLOR_PRIMARY = '#4f9dff';
const PING_COLOR_ACCENT = '#9b8cff';
const PING_PERIOD_MS = 1800;
const PING_RING_COUNT = 2;
const PING_MAX_GROWTH = 20;
// Từ ngưỡng này trở lên, chỉ hiện nhãn cho node đang focus/hover/trên lộ trình để đỡ rối chữ
const DENSE_NODE_THRESHOLD = 20;
const VALID_TABS = ['explore', 'journey', 'browse'];

// Dropdown gợi ý node dùng chung cho ô search "Khám phá" và 2 ô "Phân tích lộ trình"
function NodeSuggestDropdown({ results, onSelect }) {
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
    const fgRef = useRef();
    const canvasWrapperRef = useRef(null);
    const [canvasSize, setCanvasSize] = useState({ width: 0, height: 0 });

    // Đọc state ban đầu từ URL (?tab=&node=&depth=&from=&to=) để view có thể chia sẻ/bookmark
    // và không mất khi refresh trang — chỉ đọc 1 lần lúc mount, sau đó state là nguồn sự thật.
    const [searchParams, setSearchParams] = useSearchParams();

    // -- KHAI BÁO TẤT CẢ HOOKS Ở ĐÂY (TRƯỚC KHI RETURN) --
    const [graphData, setGraphData] = useState({ nodes: [], links: [] });
    const [loading, setLoading] = useState(false);

    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState([]);

    const [selectedEdge, setSelectedEdge] = useState(null);
    const [hoveredNode, setHoveredNode] = useState(null);
    const [filters, setFilters] = useState({ salary: 0, location: 'all' });

    const [focusNodeIds, setFocusNodeIds] = useState(() => {
        const n = searchParams.get('node');
        return n ? [n] : ['AI'];
    });
    const [depth, setDepth] = useState(() => (searchParams.get('depth') === '2' ? 2 : 1));
    const [location, setLocation] = useState('');
    const [minSalary, setMinSalary] = useState('');
    const [nodeCount, setNodeCount] = useState(0);

    // Lịch sử điều hướng (breadcrumb) ở tab Khám phá — cho phép quay lại node đã xem trước đó
    const [history, setHistory] = useState(() => {
        const n = searchParams.get('node');
        return [{ id: n || 'AI', label: n || 'AI' }];
    });

    const [activeFeature, setActiveFeature] = useState(() => {
        const t = searchParams.get('tab');
        return VALID_TABS.includes(t) ? t : 'explore';
    });
    const [pathStart, setPathStart] = useState(() => {
        const f = searchParams.get('from');
        return f ? { id: f, label: f, type: 'technology' } : null;
    });
    const [pathEnd, setPathEnd] = useState(() => {
        const t = searchParams.get('to');
        return t ? { id: t, label: t, type: 'technology' } : null;
    });
    const [activePath, setActivePath] = useState(null);

    const [journeyStartQuery, setJourneyStartQuery] = useState(() => searchParams.get('from') || '');
    const [journeyEndQuery, setJourneyEndQuery] = useState(() => searchParams.get('to') || '');
    const [journeyStartResults, setJourneyStartResults] = useState([]);
    const [journeyEndResults, setJourneyEndResults] = useState([]);

    // -- Ẩn/hiện theo loại node + chú giải thu gọn --
    const [hiddenTypes, setHiddenTypes] = useState(() => new Set());
    const [legendOpen, setLegendOpen] = useState(false);

    // -- Chế độ "Duyệt bộ lọc" (/graph/filter — không cần từ khóa gốc) --
    const [browseFilters, setBrowseFilters] = useState({ locations: [], nodeTypes: [], sentiment: '', minSalary: '', maxSalary: '' });
    const [browseResults, setBrowseResults] = useState([]);
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
                    const rawNodes = res.data.nodes || [];
                    const rawLinks = res.data.edges || res.data.links || [];

                    const nodes = rawNodes.map(n => ({
                        ...n,
                        id: n.id || n.keyword || n.name,
                        label: n.properties?.name || n.properties?.title || n.label || n.keyword || n.name || n.id,
                        type: ( (n.labels && n.labels[0]) || n.type || n.category || 'technology').toLowerCase()
                    }));

                    const links = rawLinks.map(l => ({
                        ...l,
                        source: l.source || l.source_id || l.from,
                        target: l.target || l.target_id || l.to,
                        type: (l.type || l.relation || 'RELATED_TO').toUpperCase(),
                        label: l.label || l.relation || l.type || ''
                    }));

                    setGraphData({ nodes, links });
                    setNodeCount(nodes.length);

                    setTimeout(() => {
                        if (fgRef.current) fgRef.current.zoomToFit(600, 60);
                    }, 500);
                }
            } catch (err) {
                console.error("Lỗi lấy dữ liệu graph:", err);
                if (err.message?.includes('403') || err.message?.includes('503')) {
                    context.updateSettings({ isGraphEnabled: false });
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
        const params = { tab: activeFeature };
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
            fgRef.current.d3Force('link').distance(200);
            fgRef.current.d3Force('charge').strength(-500);
            fgRef.current.d3ReheatSimulation();
        }
    }, [graphData]);

    // Tìm kiếm local (tab Khám phá)
    useEffect(() => {
        if (searchQuery.length > 0 && focusNodeIds.length > 0 && searchQuery.trim().toLowerCase() === focusNodeIds[0].toLowerCase()) {
            setSearchResults([]);
            return;
        }

        if (searchQuery.length > 0) {
            const lower = searchQuery.toLowerCase();
            const res = graphData.nodes.filter(n => (n.label || n.id).toLowerCase().includes(lower)).slice(0, 5);
            setSearchResults(res);
        } else {
            setSearchResults([]);
        }
    }, [searchQuery, graphData.nodes, focusNodeIds]);

    // Gợi ý node cho ô "Xuất phát" (tab Phân tích lộ trình)
    useEffect(() => {
        const alreadySelected = pathStart && journeyStartQuery.trim().toLowerCase() === (pathStart.label || pathStart.id).toLowerCase();
        if (journeyStartQuery.length > 0 && !alreadySelected) {
            const lower = journeyStartQuery.toLowerCase();
            setJourneyStartResults(graphData.nodes.filter(n => (n.label || n.id).toLowerCase().includes(lower)).slice(0, 5));
        } else {
            setJourneyStartResults([]);
        }
    }, [journeyStartQuery, graphData.nodes, pathStart]);

    // Gợi ý node cho ô "Điểm đến" (tab Phân tích lộ trình)
    useEffect(() => {
        const alreadySelected = pathEnd && journeyEndQuery.trim().toLowerCase() === (pathEnd.label || pathEnd.id).toLowerCase();
        if (journeyEndQuery.length > 0 && !alreadySelected) {
            const lower = journeyEndQuery.toLowerCase();
            setJourneyEndResults(graphData.nodes.filter(n => (n.label || n.id).toLowerCase().includes(lower)).slice(0, 5));
        } else {
            setJourneyEndResults([]);
        }
    }, [journeyEndQuery, graphData.nodes, pathEnd]);

    // Điều hướng đến 1 node ở tab Khám phá + cập nhật breadcrumb: nếu node đã có trong lịch sử
    // (VD: bấm lại 1 mục breadcrumb) thì cắt bớt các bước sau nó, không cộng dồn trùng lặp.
    const navigateToNode = useCallback((node) => {
        const keyword = node.label || node.id;
        setHistory(h => {
            const idx = h.findIndex(item => item.id.toLowerCase() === keyword.toLowerCase());
            if (idx !== -1) return h.slice(0, idx + 1);
            return [...h, { id: keyword, label: keyword }];
        });
        setFocusNodeIds([keyword]);
    }, []);

    const handleSearch = (node) => {
        const searchKeyword = node.label || node.id;
        setSearchQuery(searchKeyword);
        setSearchResults([]);
        navigateToNode(node);
        setTimeout(() => {
            if (fgRef.current) fgRef.current.centerAt(0, 0, 400);
        }, 300);
    };

    const handleSearchSubmit = (e) => {
        if (e.key === 'Enter' && searchQuery.trim() !== '') {
            navigateToNode({ id: searchQuery.trim(), label: searchQuery.trim() });
            setSearchResults([]);
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

    const handleZoomBy = (factor) => {
        if (!fgRef.current) return;
        fgRef.current.zoom(fgRef.current.zoom() * factor, 300);
    };

    const toggleFeature = (feat) => {
        setActiveFeature(feat);
        if (feat === 'explore') {
            setPathStart(null);
            setPathEnd(null);
            setActivePath(null);
        }
    };

    const toggleNodeTypeHidden = (type) => {
        setHiddenTypes(prev => {
            const next = new Set(prev);
            if (next.has(type)) next.delete(type);
            else next.add(type);
            return next;
        });
    };

    const toggleBrowseLocation = (loc) => {
        setBrowseFilters(f => ({
            ...f,
            locations: f.locations.includes(loc) ? f.locations.filter(l => l !== loc) : [...f.locations, loc],
        }));
    };

    const toggleBrowseNodeType = (nt) => {
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

    const handleBrowseResultClick = (node) => {
        const keyword = node.name || node.properties?.title || node.id;
        setActiveFeature('explore');
        setHistory([{ id: keyword, label: keyword }]);
        setFocusNodeIds([keyword]);
    };

    const handleJourneySelectStart = (node) => {
        setPathStart(node);
        setJourneyStartQuery(node.label || node.id);
        setJourneyStartResults([]);
    };

    const handleJourneySelectEnd = (node) => {
        setPathEnd(node);
        setJourneyEndQuery(node.label || node.id);
        setJourneyEndResults([]);
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
        const onKeyDown = (e) => {
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
                        const pathNodes = res.data.nodes.map(n => ({
                            ...n,
                            id: n.id || n.keyword || n.name,
                            label: n.properties?.name || n.properties?.title || n.label || n.keyword || n.name || n.id,
                            type: ( (n.labels && n.labels[0]) || n.type || n.category || 'technology').toLowerCase()
                        }));

                        const pathLinks = res.data.edges.map(l => ({
                            ...l,
                            source: l.source || l.source_id || l.from,
                            target: l.target || l.target_id || l.to,
                            type: (l.type || l.relation || 'RELATED_TO').toUpperCase(),
                            label: l.label || l.relation || l.type || ''
                        }));

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


    const isNodeDimmed = useCallback((node) => {
        if (node.type === 'company') {
            if (filters.salary > 0 && (node.avg_salary || 0) < filters.salary) return true;
            if (filters.location !== 'all' && node.location !== filters.location) return true;
        }
        return false;
    }, [filters]);

    // Ẩn hẳn node/link theo loại đã tắt trong chú giải (khác với "làm mờ" theo bộ lọc lương/địa điểm ở trên)
    const isNodeTypeShown = useCallback((node) => !hiddenTypes.has(node.type), [hiddenTypes]);

    const isLinkTypeShown = useCallback((link) => {
        const s = typeof link.source === 'object' ? link.source : null;
        const t = typeof link.target === 'object' ? link.target : null;
        if (s && hiddenTypes.has(s.type)) return false;
        if (t && hiddenTypes.has(t.type)) return false;
        return true;
    }, [hiddenTypes]);

    const paintNode = useCallback((node, ctx, globalScale) => {
        const nt = NODE_TYPES[node.type] || { color: '#9FA8C7', size: 8 };
        const dimmed = isNodeDimmed(node);
        const isCenter = focusNodeIds.includes(node.id);
        const isPathNode = activeFeature === 'journey' && activePath?.nodes.some(n => n.id === node.id);
        const r = nt.size + (isCenter ? 4 : 0);

        ctx.globalAlpha = dimmed ? 0.15 : 1;
        if (isCenter) {
            ctx.shadowBlur = 10; ctx.shadowColor = nt.color;
        }
        if (isPathNode) {
            ctx.shadowBlur = 14; ctx.shadowColor = PATH_HIGHLIGHT_COLOR;
            ctx.lineWidth = 2 / globalScale;
            ctx.strokeStyle = PATH_HIGHLIGHT_COLOR;
        }

        ctx.beginPath();
        ctx.arc(node.x, node.y, r, 0, 2 * Math.PI);
        ctx.fillStyle = nt.color;
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
                ctx.arc(node.x, node.y, ringRadius, 0, 2 * Math.PI);
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
            ctx.fillText(node.label || node.id, node.x, node.y + r + 10);
        }
        ctx.globalAlpha = 1;
    }, [focusNodeIds, isNodeDimmed, activePath, activeFeature, graphData.nodes.length, hoveredNode]);

    const handleNodeClick = useCallback((node) => {
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

    const handleNodeHover = useCallback((node) => {
        setHoveredNode(node || null);
        document.body.style.cursor = node ? 'pointer' : 'default';
    }, []);

    const handleLinkClick = useCallback((link) => {
        setSelectedEdge(link);
    }, []);

    const isLinkInPath = useCallback((link) => {
        if (activeFeature !== 'journey' || !activePath) return false;
        return activePath.links.some(l => {
            const s1 = typeof l.source === 'object' ? l.source.id : l.source;
            const t1 = typeof l.target === 'object' ? l.target.id : l.target;
            const s2 = typeof link.source === 'object' ? link.source.id : link.source;
            const t2 = typeof link.target === 'object' ? link.target.id : link.target;
            return (s1 === s2 && t1 === t2) || (s1 === t2 && t1 === s2);
        });
    }, [activePath, activeFeature]);

    const linkColor = useCallback((link) => isLinkInPath(link) ? PATH_HIGHLIGHT_COLOR : (LINK_TYPE_COLORS[link.type] || '#5c6494'), [isLinkInPath]);
    const linkWidth = useCallback((link) => isLinkInPath(link) ? 5 : (selectedEdge === link ? 3 : 1.2), [selectedEdge, isLinkInPath]);

    const paintLink = useCallback((link, ctx, globalScale) => {
        if (globalScale < 1.2) return;

        const start = link.source;
        const end = link.target;
        if (typeof start !== 'object' || typeof end !== 'object') return;

        const label = linkTypeLabel(link);
        const fontSize = 13 / globalScale;
        ctx.font = `bold ${fontSize}px Inter, sans-serif`;
        const textWidth = ctx.measureText(label).width;

        const textPos = {
            x: start.x + (end.x - start.x) / 2,
            y: start.y + (end.y - start.y) / 2
        };

        const relAngle = Math.atan2(end.y - start.y, end.x - start.x);

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
    const showEmptyJourney = !loading && activeFeature === 'journey' && !activePath && pathStart && pathEnd;

    const edgeEndpointLabel = (endpoint) => (typeof endpoint === 'object' ? (endpoint?.label || endpoint?.id) : endpoint);
    const edgeSourceLabel = selectedEdge ? edgeEndpointLabel(selectedEdge.source) : null;
    const edgeTargetLabel = selectedEdge ? edgeEndpointLabel(selectedEdge.target) : null;

    // Chỉ liệt kê trong chú giải những loại quan hệ THỰC SỰ xuất hiện ở đồ thị đang xem — tránh liệt
    // kê hết cả 9 loại quan hệ có thể có trong hệ thống dù đa số không liên quan đến view hiện tại.
    const presentLinkTypes = [...new Set(graphData.links.map(l => l.type))].filter(t => t in LINK_TYPE_COLORS);

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
                        <div className="filter-panel card">
                            <h3 className="filter-title">Bộ lọc</h3>
                            <div className="filter-group">
                                <label className="filter-label">Địa điểm</label>
                                <div className="pill-group">
                                    {['Hồ Chí Minh', 'Hà Nội', 'Đà Nẵng'].map(loc => (
                                        <button
                                            type="button" key={loc}
                                            className={`chip${browseFilters.locations.includes(loc) ? ' active' : ''}`}
                                            onClick={() => toggleBrowseLocation(loc)}
                                        >
                                            {loc}
                                        </button>
                                    ))}
                                </div>
                            </div>
                            <div className="filter-group">
                                <label className="filter-label">Loại node</label>
                                <div className="pill-group">
                                    {['Technology', 'Company', 'Job', 'Skill', 'Article'].map(nt => (
                                        <button
                                            type="button" key={nt}
                                            className={`chip${browseFilters.nodeTypes.includes(nt) ? ' active' : ''}`}
                                            onClick={() => toggleBrowseNodeType(nt)}
                                        >
                                            {nt}
                                        </button>
                                    ))}
                                </div>
                            </div>
                            <div className="filter-group">
                                <label className="filter-label">Cảm xúc</label>
                                <div className="pill-group">
                                    {[['', 'Tất cả'], ['positive', 'Tích cực'], ['negative', 'Tiêu cực'], ['neutral', 'Trung lập']].map(([val, label]) => (
                                        <button
                                            type="button" key={val || 'all'}
                                            className={`pill${browseFilters.sentiment === val ? ' active' : ''}`}
                                            onClick={() => setBrowseFilters(f => ({ ...f, sentiment: val }))}
                                        >
                                            {label}
                                        </button>
                                    ))}
                                </div>
                            </div>
                            <div className="filter-group">
                                <label className="filter-label">Mức lương (triệu/tháng)</label>
                                <div style={{ display: 'flex', gap: 8 }}>
                                    <input
                                        className="search-input" type="number" placeholder="Từ"
                                        value={browseFilters.minSalary}
                                        onChange={e => setBrowseFilters(f => ({ ...f, minSalary: e.target.value }))}
                                    />
                                    <input
                                        className="search-input" type="number" placeholder="Đến"
                                        value={browseFilters.maxSalary}
                                        onChange={e => setBrowseFilters(f => ({ ...f, maxSalary: e.target.value }))}
                                    />
                                </div>
                                <span className="form-hint">Chỉ áp dụng cho node Job có lương ghi rõ số</span>
                            </div>
                            <button className="btn btn-primary w-full" onClick={handleBrowseSearch} disabled={browseLoading}>
                                {browseLoading ? 'Đang tìm...' : 'Áp dụng bộ lọc'}
                            </button>
                        </div>

                        <div className="graph-canvas-wrapper browse-results-wrapper">
                            {browseError && <p style={{ color: 'var(--danger-light)', padding: 16 }}>{browseError}</p>}
                            {!browseError && browseSearched && !browseLoading && browseResults.length === 0 && (
                                <p style={{ color: 'var(--text-3)', padding: 16 }}>Không tìm thấy node nào phù hợp.</p>
                            )}
                            {!browseSearched && !browseLoading && (
                                <p style={{ color: 'var(--text-3)', padding: 16 }}>Chọn bộ lọc bên trái rồi bấm "Áp dụng bộ lọc".</p>
                            )}
                            <div className="browse-results-grid">
                                {browseResults.map(node => {
                                    const nt = NODE_TYPES[(node.type || '').toLowerCase()] || { color: '#9FA8C7' };
                                    return (
                                        <button
                                            type="button"
                                            key={node.id}
                                            className="browse-result-card"
                                            style={{ borderLeftColor: nt.color }}
                                            onClick={() => handleBrowseResultClick(node)}
                                        >
                                            <span className="browse-result-type" style={{ background: nt.color + '22', color: nt.color }}>{node.type}</span>
                                            <strong className="browse-result-name">{node.name || node.properties?.title || '(không tên)'}</strong>
                                            {node.properties?.salary && <span className="browse-result-meta">💰 {node.properties.salary}</span>}
                                            {node.properties?.location && <span className="browse-result-meta">📍 {node.properties.location}</span>}
                                        </button>
                                    );
                                })}
                            </div>
                        </div>
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

                        <div className="graph-dock">
                            <div className="dock-group">
                                <span className="dock-count">{nodeCount} nodes</span>
                            </div>
                            <div className="dock-group">
                                <button type="button" className="dock-btn" title="Thu nhỏ" aria-label="Thu nhỏ" onClick={() => handleZoomBy(0.75)}>−</button>
                                <button type="button" className="dock-btn" title="Phóng to" aria-label="Phóng to" onClick={() => handleZoomBy(1.33)}>+</button>
                                <button type="button" className="dock-btn" title="Vừa khung hình" aria-label="Vừa khung hình" onClick={handleFitView}>⤢</button>
                                <button type="button" className={`dock-btn${legendOpen ? ' active' : ''}`} title="Chú giải" aria-label="Chú giải" aria-expanded={legendOpen} onClick={() => setLegendOpen(o => !o)}>i</button>
                            </div>
                        </div>

                        {legendOpen && (
                            <div className="graph-legend-panel">
                                <div className="legend-section">
                                    <span className="legend-section-title">Cách dùng</span>
                                    <ul className="legend-tips">
                                        <li>Màu của node và cạnh nối thể hiện loại node/quan hệ — xem bảng màu bên dưới</li>
                                        <li>Node có viền sáng nhấp nháy là node bạn đang xem hiện tại</li>
                                        <li>Bấm vào 1 node để khám phá tiếp từ node đó</li>
                                        <li>Kéo để di chuyển khung nhìn, cuộn chuột hoặc bấm +/− để phóng to/thu nhỏ</li>
                                        <li>Bấm vào 1 cạnh nối để xem chi tiết mối quan hệ</li>
                                        <li>Đường dẫn ở góc trên-trái (nếu có) để quay lại node đã xem trước đó</li>
                                    </ul>
                                </div>
                                <div className="legend-sep" />
                                <div className="legend-section">
                                    <span className="legend-section-title">Loại node <em>(bấm để ẩn/hiện)</em></span>
                                    <div className="legend-grid">
                                        {Object.entries(NODE_TYPES).map(([type, cfg]) => {
                                            const isHidden = hiddenTypes.has(type);
                                            return (
                                                <button
                                                    type="button" key={type}
                                                    className={`legend-item legend-item-toggle${isHidden ? ' off' : ''}`}
                                                    aria-pressed={!isHidden}
                                                    onClick={() => toggleNodeTypeHidden(type)}
                                                >
                                                    <span className="legend-dot" style={{ background: cfg.color }} />
                                                    {type.charAt(0).toUpperCase() + type.slice(1)}
                                                </button>
                                            );
                                        })}
                                    </div>
                                </div>
                                {presentLinkTypes.length > 0 && (
                                    <>
                                        <div className="legend-sep" />
                                        <div className="legend-section">
                                            <span className="legend-section-title">Loại quan hệ <em>(trong đồ thị đang xem)</em></span>
                                            <div className="legend-grid">
                                                {presentLinkTypes.map(type => (
                                                    <div key={type} className="legend-item">
                                                        <span className="legend-line" style={{ background: LINK_TYPE_COLORS[type] }} />
                                                        {LINK_TYPE_LABELS[type] || type}
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    </>
                                )}
                            </div>
                        )}
                    </div>
                )}

                {activeFeature !== 'browse' && (activePath || selectedEdge) && (
                    <div className="graph-side-panels">
                        {activePath && (
                            <div className="journey-panel card">
                                <div className="jp-header">
                                    <h3>Lộ trình kết nối</h3>
                                    <button className="btn btn-ghost" aria-label="Đóng lộ trình" onClick={clearJourney}>✕</button>
                                </div>
                                <div className="jp-body">
                                    <div className="jp-summary">
                                        Từ <b>{pathStart?.label || pathStart?.id}</b> đến <b>{pathEnd?.label || pathEnd?.id}</b>
                                    </div>
                                    <div className="jp-steps">
                                        {activePath.links.map((link, i) => {
                                            const source = activePath.nodes[i];
                                            const target = activePath.nodes[i + 1];
                                            return (
                                                <div key={i} className="jp-step">
                                                    <div className="jp-step-node">
                                                        <span className="jp-step-dot" style={{ background: NODE_TYPES[source.type]?.color }} />
                                                        {source.label || source.id}
                                                    </div>
                                                    <div className="jp-step-link" style={{ borderLeftColor: LINK_TYPE_COLORS[link.type] }}>
                                                        <span className="jp-step-label">{linkTypeLabel(link)}</span>
                                                    </div>
                                                    {i === activePath.links.length - 1 && (
                                                        <div className="jp-step-node">
                                                            <span className="jp-step-dot" style={{ background: NODE_TYPES[target.type]?.color }} />
                                                            {target.label || target.id}
                                                        </div>
                                                    )}
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                            </div>
                        )}
                        {selectedEdge && (
                            <div className="edge-panel card">
                                <div className="ep-header">
                                    <h3>Chi tiết mối quan hệ</h3>
                                    <button className="btn btn-ghost" aria-label="Đóng chi tiết quan hệ" onClick={() => setSelectedEdge(null)}>✕</button>
                                </div>
                                <div className="ep-body">
                                    {edgeSourceLabel && edgeTargetLabel && (
                                        <p className="ep-sentence">
                                            <b>{edgeSourceLabel}</b> <span style={{ color: LINK_TYPE_COLORS[selectedEdge.type] }}>{linkTypeLabel(selectedEdge).toLowerCase()}</span> <b>{edgeTargetLabel}</b>
                                        </p>
                                    )}
                                    <div className="ep-row">
                                        <span className="ep-label">Loại quan hệ</span>
                                        <span className="ep-type" style={{ color: LINK_TYPE_COLORS[selectedEdge.type] }}>{LINK_TYPE_LABELS[selectedEdge.type] || selectedEdge.type}</span>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}
