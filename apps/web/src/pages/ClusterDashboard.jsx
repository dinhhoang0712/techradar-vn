import { useState, useMemo, useEffect, useRef, useCallback } from 'react';
import ForceGraph2D from 'react-force-graph-2d';
import { getClusters, getClusterById, getClusterByTech } from '../api/clusterService';
import { CHART_PALETTE as COLORS } from '../utils/chartPalette';
import { useToast } from '../components/common/toastContext';
import RingGauge from '../components/common/RingGauge';
import './ClusterDashboard.css';

export default function ClusterDashboard() {
    const notify = useToast();
    const fgRef = useRef();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedClusterId, setSelectedClusterId] = useState(null);
    const [selectedClusterDetail, setSelectedClusterDetail] = useState(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [hoveredNode, setHoveredNode] = useState(null);
    const [clusterData, setClusterData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [techLookupLoading, setTechLookupLoading] = useState(false);
    const [techLookupError, setTechLookupError] = useState('');

    useEffect(() => {
        const fetchClusters = async () => {
            try {
                setLoading(true);
                const response = await getClusters();
                // Depending on the backend response structure, it could be response.data or response directly.
                // It also could be an object mapping or an array.
                const data = response.data || response;
                const dataArray = Array.isArray(data) ? data : Object.values(data);
                setClusterData(dataArray);
            } catch (err) {
                setError(err.message || 'Failed to fetch clusters');
                console.error("Error fetching clusters:", err);
            } finally {
                setLoading(false);
            }
        };
        fetchClusters();
    }, []);
    
    // --- Lấy dữ liệu chi tiết của 1 cụm khi được chọn ---
    useEffect(() => {
        if (selectedClusterId === null) {
            setSelectedClusterDetail(null);
            return;
        }
        const fetchDetail = async () => {
            try {
                setDetailLoading(true);
                const response = await getClusterById(selectedClusterId);
                const data = response.data || response;
                setSelectedClusterDetail(data);
            } catch (err) {
                console.error("Error fetching cluster detail:", err);
                notify({ title: 'Không tải được chi tiết cụm', body: 'Vui lòng thử lại.', variant: 'error' });
            } finally {
                setDetailLoading(false);
            }
        };
        fetchDetail();
    }, [selectedClusterId, notify]);

    // --- Lọc danh sách cụm cho Màn hình Grid (khi chưa chọn cụm nào) ---
    // Chỉ lọc theo label/domain — danh sách công nghệ thành viên không có trong ClusterSummary
    // (chỉ ClusterDetail mới có `members`), nên tra cứu theo tên công nghệ dùng handleLookupTech bên dưới.
    const filteredClusters = useMemo(() => {
        const query = searchQuery.toLowerCase();
        return clusterData.filter(cluster => {
            if (!query) return true;
            if (cluster.label?.toLowerCase().includes(query)) return true;
            if (cluster.domain?.toLowerCase().includes(query)) return true;
            return false;
        });
    }, [searchQuery, clusterData]);

    // --- Tra cứu cụm theo tên công nghệ khi tìm kiếm không khớp cụm nào ---
    const handleLookupTech = async () => {
        if (!searchQuery.trim()) return;
        setTechLookupLoading(true);
        setTechLookupError('');
        try {
            const res = await getClusterByTech(searchQuery.trim());
            const clusterId = res?.data?.cluster_id ?? res?.cluster_id;
            if (clusterId === undefined || clusterId === null) {
                setTechLookupError('Không tìm thấy cụm cho công nghệ này.');
                return;
            }
            setSelectedClusterId(clusterId);
        } catch (err) {
            console.error('Lỗi tra cứu cụm theo công nghệ:', err);
            setTechLookupError('Không thể tra cứu lúc này.');
        } finally {
            setTechLookupLoading(false);
        }
    };

    // --- Tạo Graph Data chỉ dành riêng cho Cụm đang chọn ---
    const graphData = useMemo(() => {
        if (!selectedClusterDetail) return { nodes: [], links: [] };

        const nodes = [];
        const links = [];
        const cluster = selectedClusterDetail;
        const color = COLORS[cluster.cluster_id % COLORS.length];
        const centerId = `cluster-${cluster.cluster_id}`;

        // Node Tâm
        nodes.push({
            id: centerId,
            name: cluster.label,
            isCenter: true,
            color: color,
            val: 60
        });

        // Các Node Vệ tinh
        const techList = cluster.members || [];
        techList.forEach(tech => {
            const techId = `tech-${tech}`;
            nodes.push({
                id: techId,
                name: tech,
                isCenter: false,
                color: color,
                val: 8
            });

            // Đường nối
            links.push({
                source: centerId,
                target: techId,
                color: color
            });
        });

        return { nodes, links };
    }, [selectedClusterDetail]);

    // Căn giữa đồ thị mỗi khi vừa load xong dữ liệu 1 cụm
    useEffect(() => {
        if (selectedClusterDetail && fgRef.current && graphData.nodes.length > 0) {
            // Tùy chỉnh Physics để 1 cụm duy nhất phân bố đẹp hơn
            fgRef.current.d3Force('charge').strength(-300);
            fgRef.current.d3Force('link').distance(80);
            
            setTimeout(() => {
                if (fgRef.current) {
                    fgRef.current.zoomToFit(600, 50);
                }
            }, 300);
        }
    }, [selectedClusterDetail, graphData]);

    const handleNodeHover = useCallback((node) => {
        setHoveredNode(node || null);
        document.body.style.cursor = node ? 'pointer' : 'default';
    }, []);

    // Canvas Paint Logic: Vẽ hình dáng Node
    const paintNode = useCallback((node, ctx, globalScale) => {
        try {
            if (!node || typeof node.name !== 'string') return;
            
            // Highlight node nếu nó đang được hover hoặc là node cha (luôn nổi bật một chút)
            const isHovered = hoveredNode && hoveredNode.id === node.id;
            const isRelated = hoveredNode && (hoveredNode.isCenter || node.isCenter); // Rất đơn giản vì đồ thị giờ chỉ có 1 cụm
            
            let opacity = 1;
            if (hoveredNode && !isHovered && !isRelated) opacity = 0.3;

            let r = Math.sqrt(node.val || 1) * (node.isCenter ? 2.5 : 1.5);
            if (isHovered && !node.isCenter) r *= 1.5;

            // Vẽ hình tròn
            ctx.beginPath();
            ctx.arc(node.x || 0, node.y || 0, r, 0, 2 * Math.PI, false);
            ctx.fillStyle = node.color || '#999';
            ctx.globalAlpha = opacity;
            ctx.fill();

            // Thêm viền trắng cho Node tâm
            if (node.isCenter) {
                ctx.lineWidth = 2 / globalScale;
                ctx.strokeStyle = '#ffffff';
                ctx.stroke();
            }

            // Vẽ Text Label
            // Luôn hiện tên Node Tâm. Hiện tên Node Con khi zoom đủ gần hoặc khi hover.
            const showLabel = node.isCenter || globalScale > 1.2 || isHovered || (hoveredNode && hoveredNode.isCenter);

            if (showLabel && opacity > 0.1) {
                const fontSize = node.isCenter ? 14 / globalScale : 11 / globalScale;
                ctx.font = `${node.isCenter ? 'bold' : 'normal'} ${fontSize}px Inter, sans-serif`;
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';
                ctx.fillStyle = node.isCenter ? '#ffffff' : '#dddddd';
                
                const labelY = node.isCenter ? (node.y || 0) + r + (10 / globalScale) : (node.y || 0) + r + (8 / globalScale);
                ctx.fillText(node.name, node.x || 0, labelY);
            }

            ctx.globalAlpha = 1; // reset
        } catch (err) {
            console.error("Error painting node:", err);
        }
    }, [hoveredNode]);

    // Canvas Paint Logic: Vẽ đường nối (Link)
    const paintLink = useCallback((link, ctx) => {
        try {
            const sourceNode = link.source;
            const targetNode = link.target;
            if (!sourceNode || !targetNode || typeof sourceNode !== 'object' || typeof targetNode !== 'object') return;

            // Trong Màn hình chi tiết, chỉ có 1 cụm nên ta có thể luôn cho hiện đường nối mờ, 
            // và sáng rực lên khi hover.
            const isHovered = hoveredNode && (hoveredNode.id === sourceNode.id || hoveredNode.id === targetNode.id);
            
            let opacity = 0.2; // Mặc định mờ mờ cho đẹp
            let lineWidth = 1;

            if (isHovered || (hoveredNode && hoveredNode.isCenter)) {
                opacity = 0.8;
                lineWidth = 2;
            }

            ctx.beginPath();
            ctx.moveTo(sourceNode.x || 0, sourceNode.y || 0);
            ctx.lineTo(targetNode.x || 0, targetNode.y || 0);
            ctx.strokeStyle = link.color || '#999';
            ctx.globalAlpha = opacity;
            ctx.lineWidth = lineWidth;
            ctx.stroke();
            ctx.globalAlpha = 1;
        } catch (err) {
            console.error("Error painting link:", err);
        }
    }, [hoveredNode]);


    return (
        <div className="cluster-page">
            <div className="cluster-header">
                <div>
                    <h1 className="cluster-title">Danh mục Phân cụm Công nghệ</h1>
                    <p style={{ color: 'var(--text-3)', marginTop: '8px' }}>
                        {selectedClusterId === null ? "Chọn một cụm để khám phá hệ sinh thái chi tiết." : `Đang xem hệ sinh thái của cụm: ${selectedClusterDetail?.label || '...'}`}
                    </p>
                </div>
                
                {/* Chỉ hiện ô tìm kiếm ở màn hình Grid (hoặc có thể giữ lại tùy ý) */}
                {selectedClusterId === null && (
                    <div className="cluster-search-wrap">
                        <input 
                            type="text" 
                            className="cluster-search-input" 
                            placeholder="Tìm kiếm cụm hoặc công nghệ..." 
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                    </div>
                )}
            </div>

            {/* MÀN HÌNH 1: LƯỚI TỔNG QUAN */}
            {loading ? (
                <div className="cluster-grid">
                    {Array.from({ length: 6 }).map((_, i) => (
                        <div className="cluster-grid-item cluster-grid-item-skeleton" key={i}>
                            <div className="skeleton cluster-skel-badge" />
                            <div className="skeleton cluster-skel-title" />
                            <div className="skeleton cluster-skel-line" />
                            <div className="skeleton cluster-skel-line" style={{ width: '80%' }} />
                            <div className="cluster-stats">
                                <div className="skeleton cluster-skel-stat" />
                                <div className="skeleton cluster-skel-stat" />
                            </div>
                        </div>
                    ))}
                </div>
            ) : error ? (
                <div style={{ textAlign: 'center', padding: '60px', color: 'var(--danger-light)' }}>Lỗi: {error}</div>
            ) : selectedClusterId === null ? (
                <div className="cluster-grid">
                    {filteredClusters.map(cluster => {
                        const color = COLORS[cluster.cluster_id % COLORS.length];
                        const orbitCount = 3 + (cluster.cluster_id % 4); // 3-6 vệ tinh, đổi theo từng cụm cho sinh động
                        const orbitRadius = 16;
                        return (
                            <button
                                type="button"
                                key={cluster.cluster_id}
                                className="cluster-grid-item"
                                onClick={() => setSelectedClusterId(cluster.cluster_id)}
                                style={{ borderTop: `4px solid ${color}` }}
                            >
                                <div className="cluster-orbit-preview" aria-hidden="true">
                                    <div className="cluster-orbit-ring">
                                        {Array.from({ length: orbitCount }).map((_, i) => {
                                            const angle = (360 / orbitCount) * i;
                                            return (
                                                <span
                                                    key={i}
                                                    className="cluster-orbit-dot"
                                                    style={{
                                                        background: color,
                                                        transform: `rotate(${angle}deg) translate(${orbitRadius}px) rotate(-${angle}deg)`,
                                                    }}
                                                />
                                            );
                                        })}
                                    </div>
                                    <span className="cluster-orbit-center" style={{ background: color, boxShadow: `0 0 8px ${color}` }} />
                                </div>
                                <span className="cluster-domain-badge" style={{ background: color + '22', color }}>
                                    {cluster.domain}
                                </span>
                                <h3>{cluster.label}</h3>
                                <p className="cluster-desc-short">{cluster.label_en}</p>
                                <div className="cluster-stats">
                                    <span><strong>{cluster.n_members || 0}</strong> công nghệ</span>
                                    <span className="cluster-confidence-inline">
                                        <RingGauge percent={(cluster.confidence || 0) * 100} size={28} strokeWidth={3} label={Math.round((cluster.confidence || 0) * 100)} />
                                        Tin cậy
                                    </span>
                                </div>
                                {cluster.overridden && (
                                    <div className="cluster-card-badges">
                                        <span className="badge badge-primary">Đã chỉnh sửa</span>
                                    </div>
                                )}
                            </button>
                        );
                    })}
                    {filteredClusters.length === 0 && (
                        <div style={{ gridColumn: '1 / -1', textAlign: 'center', padding: '60px', color: 'var(--text-3)' }}>
                            <p>Không tìm thấy cụm nào phù hợp.</p>
                            {searchQuery.trim() && (
                                <>
                                    <button
                                        type="button"
                                        className="btn btn-secondary"
                                        style={{ marginTop: 16 }}
                                        onClick={handleLookupTech}
                                        disabled={techLookupLoading}
                                    >
                                        {techLookupLoading ? 'Đang tra cứu...' : `Tìm cụm chứa công nghệ "${searchQuery.trim()}"`}
                                    </button>
                                    {techLookupError && (
                                        <p style={{ color: 'var(--danger-light)', marginTop: 12 }}>{techLookupError}</p>
                                    )}
                                </>
                            )}
                        </div>
                    )}
                </div>
            ) : (
                /* MÀN HÌNH 2: CHI TIẾT 1 CỤM (GRAPH + INFO PANEL) */
                <div className="cluster-detail-container">
                    
                    {/* Đồ thị mạng lưới (chỉ vẽ 1 cụm) */}
                    <div className="cluster-graph-card">
                        <button className="btn-back-floating" onClick={() => setSelectedClusterId(null)} title="Quay lại danh sách">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                                <line x1="19" y1="12" x2="5" y2="12"></line>
                                <polyline points="12 19 5 12 12 5"></polyline>
                            </svg>
                        </button>
                        
                        {detailLoading ? (
                            <div className="loading-overlay">
                                Đang tải chi tiết cụm...
                            </div>
                        ) : selectedClusterDetail ? (
                            <div className="graph-wrapper">
                                <ForceGraph2D
                                    ref={fgRef}
                                    graphData={graphData}
                                    nodeCanvasObject={paintNode}
                                    linkCanvasObject={paintLink}
                                    onNodeHover={handleNodeHover}
                                    enableNodeDrag={true}
                                    enableZoomPanInteraction={true}
                                    backgroundColor="#0a0a0a"
                                />
                            </div>
                        ) : null}
                    </div>

                    {/* Bảng thông tin chi tiết */}
                    <div className="cluster-info-card">
                        {detailLoading ? (
                             <div className="loading-text">Đang tải...</div>
                        ) : selectedClusterDetail ? (
                            <>
                                <div className="cluster-detail-header">
                                    <span className="cluster-domain-badge" style={{ background: COLORS[selectedClusterDetail.cluster_id % COLORS.length] + '33', color: COLORS[selectedClusterDetail.cluster_id % COLORS.length] }}>
                                        {selectedClusterDetail.domain}
                                    </span>
                                    <h2 className="cluster-detail-title">{selectedClusterDetail.label}</h2>
                                    <p className="cluster-subtitle">{selectedClusterDetail.label_en || 'Cluster Overview'}</p>
                                    {(selectedClusterDetail.overridden || selectedClusterDetail.is_coherent === false) && (
                                        <div className="cluster-detail-flags">
                                            {selectedClusterDetail.overridden && <span className="badge badge-primary">Đã chỉnh sửa thủ công</span>}
                                            {selectedClusterDetail.is_coherent === false && <span className="badge badge-down">AI đánh giá: chưa mạch lạc</span>}
                                        </div>
                                    )}
                                </div>

                                <div className="cluster-stats-row">
                                    <div className="cluster-stat-box">
                                        <div className="stat-val">{selectedClusterDetail.n_members}</div>
                                        <div className="stat-label">Công nghệ</div>
                                    </div>
                                    <div className="cluster-stat-box cluster-stat-box-gauge">
                                        <RingGauge percent={(selectedClusterDetail.confidence || 0) * 100} size={44} strokeWidth={5} label={`${Math.round((selectedClusterDetail.confidence || 0) * 100)}%`} />
                                        <div className="stat-label">Tin cậy</div>
                                    </div>
                                    <div className="cluster-stat-box">
                                        <div className="stat-val">#{selectedClusterDetail.cluster_id}</div>
                                        <div className="stat-label">Cụm #</div>
                                    </div>
                                </div>

                                {selectedClusterDetail.is_coherent === false && selectedClusterDetail.coherence_reason && (
                                    <p className="cluster-coherence-reason">"{selectedClusterDetail.coherence_reason}"</p>
                                )}

                                <p className="cluster-description-text">{selectedClusterDetail.description}</p>

                                {selectedClusterDetail.outliers?.length > 0 && (
                                    <div className="cluster-tech-section cluster-outliers">
                                        <h3 className="section-subtitle">Công nghệ lệch nhóm ({selectedClusterDetail.outliers.length})</h3>
                                        <div className="pill-group">
                                            {selectedClusterDetail.outliers.map(name => <span key={name} className="pill">{name}</span>)}
                                        </div>
                                    </div>
                                )}

                                <div className="cluster-tech-section">
                                    <h3 className="section-subtitle">Danh sách Công nghệ ({selectedClusterDetail.members?.length || 0})</h3>
                                    <div className="cluster-tech-list">
                                        {(selectedClusterDetail.members || []).map(tech => (
                                            <span key={tech} className="tech-tag" style={{ 
                                                border: `1px solid ${COLORS[selectedClusterDetail.cluster_id % COLORS.length]}55`, 
                                            }}>
                                                {tech}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            </>
                        ) : null}
                    </div>
                </div>
            )}
        </div>
    );
}
