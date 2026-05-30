import { useState, useMemo, useEffect, useRef, useCallback } from 'react';
import ForceGraph2D from 'react-force-graph-2d';
import { getClusters, getClusterById } from '../api/clusterService';
import './ClusterDashboard.css';

const COLORS = [
    '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEEAD',
    '#D4A5A5', '#9B59B6', '#3498DB', '#E67E22', '#2ECC71',
    '#F1C40F', '#E74C3C', '#1ABC9C', '#34495E', '#FF9FF3',
    '#00D2D3', '#54A0FF', '#5F27CD', '#C8D6E5', '#FF9F43',
    '#01A3A4', '#EE5253'
];

export default function ClusterDashboard() {
    const fgRef = useRef();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedClusterId, setSelectedClusterId] = useState(null);
    const [selectedClusterDetail, setSelectedClusterDetail] = useState(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [hoveredNode, setHoveredNode] = useState(null);
    const [clusterData, setClusterData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

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
            } finally {
                setDetailLoading(false);
            }
        };
        fetchDetail();
    }, [selectedClusterId]);

    // --- Lọc danh sách cụm cho Màn hình Grid (khi chưa chọn cụm nào) ---
    const filteredClusters = useMemo(() => {
        const query = searchQuery.toLowerCase();
        return clusterData.filter(cluster => {
            if (!query) return true;
            if (cluster.label?.toLowerCase().includes(query)) return true;
            if (cluster.domain?.toLowerCase().includes(query)) return true;
            if (cluster.sample_techs?.some(t => t.toLowerCase().includes(query))) return true;
            return false;
        });
    }, [searchQuery, clusterData]);

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
        const techList = cluster.members || cluster.sample_techs || [];
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
                <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-3)' }}>Đang tải dữ liệu cụm...</div>
            ) : error ? (
                <div style={{ textAlign: 'center', padding: '60px', color: '#ff6b6b' }}>Lỗi: {error}</div>
            ) : selectedClusterId === null ? (
                <div className="cluster-grid">
                    {filteredClusters.map(cluster => (
                        <div 
                            key={cluster.cluster_id} 
                            className="cluster-grid-item"
                            onClick={() => setSelectedClusterId(cluster.cluster_id)}
                            style={{ borderTop: `4px solid ${COLORS[cluster.cluster_id % COLORS.length]}` }}
                        >
                            <span className="cluster-domain-badge" style={{ background: COLORS[cluster.cluster_id % COLORS.length] + '22', color: COLORS[cluster.cluster_id % COLORS.length] }}>
                                {cluster.domain}
                            </span>
                            <h3>{cluster.label}</h3>
                            <p className="cluster-desc-short">{cluster.description}</p>
                            <div className="cluster-stats">
                                <span><strong>{cluster.n_members || cluster.member_count || 0}</strong> công nghệ</span>
                                <span>Tin cậy: <strong>{cluster.confidence ? Math.round(cluster.confidence * 100) : 0}%</strong></span>
                            </div>
                        </div>
                    ))}
                    {filteredClusters.length === 0 && (
                        <div style={{ gridColumn: '1 / -1', textAlign: 'center', padding: '60px', color: 'var(--text-3)' }}>
                            Không tìm thấy cụm nào phù hợp.
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
                                    <p className="cluster-subtitle">{selectedClusterDetail.description_en || 'Cluster Overview'}</p>
                                </div>
                                
                                <div className="cluster-stats-row">
                                    <div className="cluster-stat-box">
                                        <div className="stat-val">{selectedClusterDetail.n_members || selectedClusterDetail.member_count}</div>
                                        <div className="stat-label">Công nghệ</div>
                                    </div>
                                    <div className="cluster-stat-box">
                                        <div className="stat-val">{selectedClusterDetail.confidence ? Math.round(selectedClusterDetail.confidence * 100) : 0}%</div>
                                        <div className="stat-label">Tin cậy</div>
                                    </div>
                                    <div className="cluster-stat-box">
                                        <div className="stat-val">#{selectedClusterDetail.cluster_id}</div>
                                        <div className="stat-label">Cụm #</div>
                                    </div>
                                </div>

                                <p className="cluster-description-text">{selectedClusterDetail.description}</p>

                                <div className="cluster-tech-section">
                                    <h3 className="section-subtitle">Danh sách Công nghệ ({selectedClusterDetail.members?.length || 0})</h3>
                                    <div className="cluster-tech-list">
                                        {(selectedClusterDetail.members || selectedClusterDetail.sample_techs || []).map(tech => (
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
