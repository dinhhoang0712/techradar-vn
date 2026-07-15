import { useState, useEffect, useCallback, useRef } from 'react';
import ForceGraph2D from 'react-force-graph-2d';
import { exploreGraph, analyzeRoad, filterGraph } from '../api/graphService';
import { useAppContext } from '../contexts/appContextStore';
import { useToast } from '../components/common/toastContext';
import MaintenancePage from './MaintenancePage';
import MaintenanceOverlay from '../components/common/MaintenanceOverlay';
import './GraphExplorer.css';

const PATH_HIGHLIGHT_COLOR = '#FFD700';

const LINK_TYPE_COLORS = {
    USES: '#6C63FF',
    REQUIRES: '#00D68F',
    LOCATED_IN: '#FFC94D',
    RELATED_TO: '#FF6584',
    TAGGED_WITH: '#54C5F8',
};

const NODE_TYPES = {
    technology: { color: '#6C63FF', size: 10 },
    company: { color: '#FF6584', size: 14 },
    skill: { color: '#00D68F', size: 8 },
    location: { color: '#FFC94D', size: 12 },
    industry: { color: '#54C5F8', size: 12 },
    job: { color: '#FF9800', size: 12 },
};

export default function GraphExplorer() {
    const context = useAppContext();
    const settings = context?.settings;
    const notify = useToast();
    const fgRef = useRef();

    // -- KHAI BÁO TẤT CẢ HOOKS Ở ĐÂY (TRƯỚC KHI RETURN) --
    const [graphData, setGraphData] = useState({ nodes: [], links: [] });
    const [loading, setLoading] = useState(false);
    
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState([]);
    
    const [selectedEdge, setSelectedEdge] = useState(null);
    const [hoveredNode, setHoveredNode] = useState(null);
    const [filters, setFilters] = useState({ salary: 0, location: 'all' });
    
    const [focusNodeIds, setFocusNodeIds] = useState(['Golang']); 
    const [depth, setDepth] = useState(1);
    const [location, setLocation] = useState('');
    const [minSalary, setMinSalary] = useState('');
    const [nodeCount, setNodeCount] = useState(0);
    
    const [activeFeature, setActiveFeature] = useState('explore'); 
    const [pathStart, setPathStart] = useState(null);
    const [pathEnd, setPathEnd] = useState(null);
    const [activePath, setActivePath] = useState(null);
    
    const [journeyStartQuery, setJourneyStartQuery] = useState('');
    const [journeyEndQuery, setJourneyEndQuery] = useState('');

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

    // Điều chỉnh lực đẩy
    useEffect(() => {
        if (fgRef.current) {
            fgRef.current.d3Force('link').distance(200); 
            fgRef.current.d3Force('charge').strength(-500); 
            fgRef.current.d3ReheatSimulation(); 
        }
    }, [graphData]);

    // Tìm kiếm local
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

    const handleSearch = (node) => {
        const searchKeyword = node.label || node.id;
        setSearchQuery(searchKeyword);
        setSearchResults([]);
        setFocusNodeIds([searchKeyword]);
        setTimeout(() => {
            if (fgRef.current) fgRef.current.centerAt(0, 0, 400);
        }, 300);
    };

    const handleSearchSubmit = (e) => {
        if (e.key === 'Enter' && searchQuery.trim() !== '') {
            setFocusNodeIds([searchQuery.trim()]);
            setSearchResults([]);
        }
    };

    const handleReset = () => {
        setFocusNodeIds(['Golang']);
        setDepth(1);
        setSearchQuery('');
        setLocation('');
        setMinSalary('');
        setSelectedEdge(null);
        setPathStart(null);
        setPathEnd(null);
        setActivePath(null);
        setFilters({ salary: 0, location: 'all' });
    };

    const handleFocusNode = () => {
        if (fgRef.current && graphData.nodes.length > 0) {
            fgRef.current.centerAt(0, 0, 600);
        }
    };

    const toggleFeature = (feat) => {
        setActiveFeature(feat);
        if (feat === 'explore') {
            setPathStart(null);
            setPathEnd(null);
            setActivePath(null);
        }
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
        setFocusNodeIds([keyword]);
        setActiveFeature('explore');
    };

    const handleJourneySelectStart = (node) => {
        setPathStart(node);
        setJourneyStartQuery(node.label || node.id);
    };

    const handleJourneySelectEnd = (node) => {
        setPathEnd(node);
        setJourneyEndQuery(node.label || node.id);
    };

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


    const isNodeVisible = useCallback((node) => {
        if (node.type === 'company') {
            if (filters.salary > 0 && (node.avg_salary || 0) < filters.salary) return false;
            if (filters.location !== 'all' && node.location !== filters.location) return false;
        }
        return true;
    }, [filters]);

    const paintNode = useCallback((node, ctx, globalScale) => {
        const nt = NODE_TYPES[node.type] || { color: '#9FA8C7', size: 8 };
        const visible = isNodeVisible(node);
        const isCenter = focusNodeIds.includes(node.id);
        const isPathNode = activeFeature === 'journey' && activePath?.nodes.some(n => n.id === node.id);
        const r = nt.size + (isCenter ? 4 : 0);

        ctx.globalAlpha = visible ? 1 : 0.15;
        if (isCenter) {
            ctx.shadowBlur = 16; ctx.shadowColor = nt.color;
        }
        if (isPathNode) {
            ctx.shadowBlur = 20; ctx.shadowColor = PATH_HIGHLIGHT_COLOR;
            ctx.lineWidth = 2 / globalScale;
            ctx.strokeStyle = PATH_HIGHLIGHT_COLOR;
        }

        ctx.beginPath();
        ctx.arc(node.x, node.y, r, 0, 2 * Math.PI);
        ctx.fillStyle = nt.color;
        ctx.fill();

        if (isPathNode) ctx.stroke();

        ctx.shadowBlur = 0;

        if (globalScale >= 0.8) {
            ctx.font = `600 ${Math.min(14 / globalScale, 12)}px Inter, sans-serif`;
            ctx.fillStyle = '#E8EAF6';
            ctx.textAlign = 'center';
            ctx.fillText(node.label || node.id, node.x, node.y + r + 10);
        }
        ctx.globalAlpha = 1;
    }, [focusNodeIds, isNodeVisible, activePath, activeFeature]);

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
            const searchKeyword = node.label || node.id;
            setFocusNodeIds([searchKeyword]);
            if (fgRef.current) {
                fgRef.current.centerAt(node.x, node.y, 600);
                fgRef.current.zoom(2, 800);
            }
        }
    }, [pathStart, pathEnd, activeFeature]);

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

        const label = link.label || link.type;
        const fontSize = 18 / globalScale; 
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

    return (
        <div className="graph-page">
            <div className="graph-search-bar card">
                <div className="feature-switcher-tabs">
                    <button className={`feat-tab${activeFeature === 'explore' ? ' active' : ''}`} onClick={() => toggleFeature('explore')}>Khám phá</button>
                    <button className={`feat-tab${activeFeature === 'journey' ? ' active' : ''}`} onClick={() => toggleFeature('journey')}>Phân tích lộ trình</button>
                    <button className={`feat-tab${activeFeature === 'browse' ? ' active' : ''}`} onClick={() => toggleFeature('browse')}>Duyệt bộ lọc</button>
                </div>

                {activeFeature === 'browse' ? (
                    <p style={{ color: 'var(--text-3)', fontSize: '0.85rem', margin: 0 }}>
                        Duyệt toàn bộ đồ thị theo địa điểm, loại node và cảm xúc — không cần nhập từ khóa gốc.
                    </p>
                ) : activeFeature === 'explore' ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                        <div className="search-input-wrap">
                            <input
                                className="search-input"
                                placeholder="Tìm kiếm/Nhập keyword và ấn Enter..."
                                value={searchQuery}
                                onChange={e => setSearchQuery(e.target.value)}
                                onKeyDown={handleSearchSubmit}
                            />
                            {searchResults.length > 0 && (
                                <div className="search-dropdown">
                                    {searchResults.map(n => (
                                        <button key={n.id} className="search-result-item" onClick={() => handleSearch(n)}>
                                            <span className="srd-type-badge" style={{ background: NODE_TYPES[n.type]?.color + '33', color: NODE_TYPES[n.type]?.color }}>
                                                {n.type}
                                            </span>
                                            {n.label || n.id}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                ) : (
                    <div className="journey-search-row">
                        {/* Start node */}
                        <div className="journey-search-field">
                            <label className="journey-search-label">
                                <span className="journey-tag start">Xuất phát</span>
                                {pathStart && <span className="journey-selected-badge" style={{ background: NODE_TYPES[pathStart.type]?.color + '33', color: NODE_TYPES[pathStart.type]?.color }}>✓ {pathStart.label || pathStart.id}</span>}
                            </label>
                            <input
                                className="search-input"
                                placeholder="Nhập điểm đi..."
                                value={journeyStartQuery}
                                onChange={e => setJourneyStartQuery(e.target.value)}
                                onKeyDown={(e) => { if(e.key === 'Enter') handleJourneySelectStart({ id: journeyStartQuery, label: journeyStartQuery, type: 'technology' })}}
                            />
                        </div>

                        <div className="journey-arrow">→</div>

                        {/* End node */}
                        <div className="journey-search-field">
                            <label className="journey-search-label">
                                <span className="journey-tag end">Điểm đến</span>
                                {pathEnd && <span className="journey-selected-badge" style={{ background: NODE_TYPES[pathEnd.type]?.color + '33', color: NODE_TYPES[pathEnd.type]?.color }}>✓ {pathEnd.label || pathEnd.id}</span>}
                            </label>
                            <input
                                className="search-input"
                                placeholder="Nhập điểm đến..."
                                value={journeyEndQuery}
                                onChange={e => setJourneyEndQuery(e.target.value)}
                                onKeyDown={(e) => { if(e.key === 'Enter') handleJourneySelectEnd({ id: journeyEndQuery, label: journeyEndQuery, type: 'technology' })}}
                            />
                        </div>

                        {/* Action buttons */}
                        <div style={{ display: 'flex', gap: '8px', alignSelf: 'flex-end' }}>
                            <button 
                                className="btn btn-primary" 
                                onClick={() => {
                                    if(journeyStartQuery) handleJourneySelectStart({ id: journeyStartQuery, label: journeyStartQuery, type: 'technology' });
                                    if(journeyEndQuery) handleJourneySelectEnd({ id: journeyEndQuery, label: journeyEndQuery, type: 'technology' });
                                }}
                                disabled={!journeyStartQuery || !journeyEndQuery}
                            >
                                Tìm đường
                            </button>
                            {(pathStart || pathEnd) && (
                                <button className="btn btn-ghost" onClick={() => { setPathStart(null); setPathEnd(null); setActivePath(null); setJourneyStartQuery(''); setJourneyEndQuery(''); }}>
                                    Xóa
                                </button>
                            )}
                        </div>
                    </div>
                )}

                {activeFeature !== 'browse' && (
                <div className="graph-controls">
                    {activeFeature !== 'journey' && (
                        <div className="control-group-inline">
                            <label className="control-label">Depth</label>
                            <div className="pill-group">
                                <button className={`pill${depth === 1 ? ' active' : ''}`} onClick={() => setDepth(1)}>1 hop</button>
                                <button className={`pill${depth === 2 ? ' active' : ''}`} onClick={() => setDepth(2)}>2 hops</button>
                            </div>
                        </div>
                    )}
                    <button className="btn btn-ghost" onClick={handleFocusNode}>Focus</button>
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
                                {['Hồ Chí Minh', 'Hà Nội', 'Đà Nẵng'].map(loc => (
                                    <label key={loc} className="filter-checkbox-row">
                                        <input type="checkbox" checked={browseFilters.locations.includes(loc)} onChange={() => toggleBrowseLocation(loc)} />
                                        {loc}
                                    </label>
                                ))}
                            </div>
                            <div className="filter-group">
                                <label className="filter-label">Loại node</label>
                                {['Technology', 'Company', 'Job', 'Skill', 'Article'].map(nt => (
                                    <label key={nt} className="filter-checkbox-row">
                                        <input type="checkbox" checked={browseFilters.nodeTypes.includes(nt)} onChange={() => toggleBrowseNodeType(nt)} />
                                        {nt}
                                    </label>
                                ))}
                            </div>
                            <div className="filter-group">
                                <label className="filter-label">Cảm xúc</label>
                                {[['', 'Tất cả'], ['positive', 'Tích cực'], ['negative', 'Tiêu cực'], ['neutral', 'Trung lập']].map(([val, label]) => (
                                    <label key={val || 'all'} className="filter-checkbox-row">
                                        <input
                                            type="radio"
                                            name="browse-sentiment"
                                            checked={browseFilters.sentiment === val}
                                            onChange={() => setBrowseFilters(f => ({ ...f, sentiment: val }))}
                                        />
                                        {label}
                                    </label>
                                ))}
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
                <div className="graph-canvas-wrapper">
                    {loading && <div style={{ position: 'absolute', top: 20, left: 20, zIndex: 10, color: 'var(--text)' }}>Đang tải đồ thị...</div>}
                    <ForceGraph2D
                        ref={fgRef}
                        graphData={graphData}
                        nodeCanvasObject={paintNode}
                        nodeCanvasObjectMode={() => 'replace'}
                        onNodeClick={handleNodeClick}
                        onNodeHover={handleNodeHover}
                        onLinkClick={handleLinkClick}
                        linkColor={linkColor}
                        linkWidth={linkWidth}
                        linkCanvasObject={paintLink}
                        linkCanvasObjectMode={() => 'after'}
                        linkDirectionalArrowLength={8}
                        linkDirectionalArrowRelPos={0.8}
                        backgroundColor="#000000"
                    />

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

                    <div className="graph-stats-badge">
                        <span>{nodeCount} nodes</span>
                    </div>

                    <div className="graph-legend">
                        {Object.entries(NODE_TYPES).map(([type, cfg]) => (
                            <div key={type} className="legend-item">
                                <span className="legend-dot" style={{ background: cfg.color }} />
                                <span>{type.charAt(0).toUpperCase() + type.slice(1)}</span>
                            </div>
                        ))}
                    </div>
                </div>
                )}

                {activeFeature !== 'browse' && (activePath || selectedEdge) && (
                    <div className="graph-side-panels">
                        {activePath && (
                            <div className="journey-panel card">
                                <div className="jp-header">
                                    <h3>Lộ trình kết nối</h3>
                                    <button className="btn btn-ghost" onClick={() => { setPathStart(null); setPathEnd(null); setActivePath(null); }}>X</button>
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
                                                        <span className="jp-step-label">{link.label || link.type}</span>
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
                                    <button className="btn btn-ghost" onClick={() => setSelectedEdge(null)}>X</button>
                                </div>
                                <div className="ep-body">
                                    <div className="ep-row">
                                        <span className="ep-label">Quan hệ</span>
                                        <span className="ep-type" style={{ color: LINK_TYPE_COLORS[selectedEdge.type] }}>{selectedEdge.type}</span>
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
