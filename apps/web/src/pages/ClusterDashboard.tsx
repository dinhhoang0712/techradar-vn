import { useState, useMemo, useEffect, useRef, useCallback } from 'react';
import type { NodeObject, LinkObject, ForceGraphMethods } from 'react-force-graph-2d';
import { getClusters, getClusterById, getClusterByTech } from '../api/clusterService';
import { CHART_PALETTE as COLORS } from '../utils/chartPalette';
import { useToast } from '../components/common/toastContext';
import { useAsync } from '../hooks/useAsync';
import ClusterGrid from '../components/cluster/ClusterGrid';
import ClusterDetailView from '../components/cluster/ClusterDetailView';
import type { ClusterSummary, ClusterDetail, ClusterGraphNode, ClusterGraphLink, ClusterByTechResult } from '../types/cluster';
import './ClusterDashboard.css';

type ClusterFgMethods = ForceGraphMethods<NodeObject<ClusterGraphNode>, LinkObject<ClusterGraphNode, ClusterGraphLink>>;

export default function ClusterDashboard() {
    const notify = useToast();
    const fgRef = useRef<ClusterFgMethods | undefined>(undefined);
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedClusterId, setSelectedClusterId] = useState<number | null>(null);
    const [hoveredNode, setHoveredNode] = useState<NodeObject<ClusterGraphNode> | null>(null);
    const [techLookupLoading, setTechLookupLoading] = useState(false);
    const [techLookupError, setTechLookupError] = useState('');

    // useAsync tự bỏ qua response trả về muộn — tránh race condition khi đổi cụm được chọn nhanh.
    const { data: clusterData, loading, error } = useAsync<ClusterSummary[]>(
        async () => {
            const response = await getClusters();
            // Depending on the backend response structure, it could be response.data or response directly.
            // It also could be an object mapping or an array.
            const data = ('data' in response ? response.data : response) as ClusterSummary[] | Record<string, ClusterSummary>;
            return Array.isArray(data) ? data : Object.values(data);
        },
        [],
        { initialData: [], onError: (err) => console.error('Error fetching clusters:', err) },
    );

    const { data: selectedClusterDetail, loading: detailLoading } = useAsync<ClusterDetail | null>(
        async () => {
            if (selectedClusterId === null) return null;
            const response = await getClusterById(selectedClusterId);
            return ('data' in response ? response.data : response) ?? null;
        },
        [selectedClusterId],
        {
            onError: (err) => {
                console.error('Error fetching cluster detail:', err);
                notify({ title: 'Không tải được chi tiết cụm', body: 'Vui lòng thử lại.', variant: 'error' });
            },
        },
    );

    // --- Lọc danh sách cụm cho Màn hình Grid (khi chưa chọn cụm nào) ---
    // Chỉ lọc theo label/domain — danh sách công nghệ thành viên không có trong ClusterSummary
    // (chỉ ClusterDetail mới có `members`), nên tra cứu theo tên công nghệ dùng handleLookupTech bên dưới.
    const filteredClusters = useMemo(() => {
        const query = searchQuery.toLowerCase();
        return (clusterData ?? []).filter(cluster => {
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
            const withData = res as Partial<{ data: ClusterByTechResult }> & Partial<ClusterByTechResult>;
            const clusterId = withData.data?.cluster_id ?? withData.cluster_id;
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
        if (!selectedClusterDetail) return { nodes: [] as ClusterGraphNode[], links: [] as LinkObject<ClusterGraphNode, ClusterGraphLink>[] };

        const nodes: ClusterGraphNode[] = [];
        const links: LinkObject<ClusterGraphNode, ClusterGraphLink>[] = [];
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

    // Căn giữa đồ thị mỗi khi vừa load xong dữ liệu 1 cụm — clear timeout cũ khi effect chạy lại
    // (đổi cụm nhanh) để tránh 1 zoomToFit trễ ghi đè lên đồ thị của cụm mới hơn.
    useEffect(() => {
        if (selectedClusterDetail && fgRef.current && graphData.nodes.length > 0) {
            // Tùy chỉnh Physics để 1 cụm duy nhất phân bố đẹp hơn
            fgRef.current.d3Force('charge')?.strength(-300);
            fgRef.current.d3Force('link')?.distance(80);

            const timer = setTimeout(() => {
                if (fgRef.current) {
                    fgRef.current.zoomToFit(600, 50);
                }
            }, 300);
            return () => clearTimeout(timer);
        }
        return undefined;
    }, [selectedClusterDetail, graphData]);

    const handleNodeHover = useCallback((node: NodeObject<ClusterGraphNode> | null) => {
        setHoveredNode(node || null);
        document.body.style.cursor = node ? 'pointer' : 'default';
    }, []);

    // handleNodeHover chỉnh thẳng document.body.style.cursor — trả lại 'default' lúc unmount phòng
    // khi rời trang đang hover 1 node (con trỏ đang là 'pointer').
    useEffect(() => () => { document.body.style.cursor = 'default'; }, []);

    // Canvas Paint Logic: Vẽ hình dáng Node
    const paintNode = useCallback((node: NodeObject<ClusterGraphNode>, ctx: CanvasRenderingContext2D, globalScale: number) => {
        try {
            if (!node || typeof node.name !== 'string') return;

            // Highlight node nếu nó đang được hover hoặc là node cha (luôn nổi bật một chút)
            const isHovered = !!hoveredNode && hoveredNode.id === node.id;
            const isRelated = !!hoveredNode && (hoveredNode.isCenter || node.isCenter); // Rất đơn giản vì đồ thị giờ chỉ có 1 cụm

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
            const showLabel = node.isCenter || globalScale > 1.2 || isHovered || (hoveredNode?.isCenter ?? false);

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
    const paintLink = useCallback((link: LinkObject<ClusterGraphNode, ClusterGraphLink>, ctx: CanvasRenderingContext2D) => {
        try {
            const sourceNode = link.source;
            const targetNode = link.target;
            if (!sourceNode || !targetNode || typeof sourceNode !== 'object' || typeof targetNode !== 'object') return;

            // Trong Màn hình chi tiết, chỉ có 1 cụm nên ta có thể luôn cho hiện đường nối mờ,
            // và sáng rực lên khi hover.
            const isHovered = !!hoveredNode && (hoveredNode.id === sourceNode.id || hoveredNode.id === targetNode.id);

            let opacity = 0.2; // Mặc định mờ mờ cho đẹp
            let lineWidth = 1;

            if (isHovered || hoveredNode?.isCenter) {
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
                <div style={{ textAlign: 'center', padding: '60px', color: 'var(--danger-light)' }}>Lỗi: {(error as Error)?.message || 'Failed to fetch clusters'}</div>
            ) : selectedClusterId === null ? (
                <ClusterGrid
                    clusters={filteredClusters}
                    searchQuery={searchQuery}
                    onSelect={setSelectedClusterId}
                    onLookupTech={handleLookupTech}
                    techLookupLoading={techLookupLoading}
                    techLookupError={techLookupError}
                />
            ) : (
                <ClusterDetailView
                    fgRef={fgRef}
                    cluster={selectedClusterDetail}
                    detailLoading={detailLoading}
                    graphData={graphData}
                    paintNode={paintNode}
                    paintLink={paintLink}
                    onNodeHover={handleNodeHover}
                    onBack={() => setSelectedClusterId(null)}
                />
            )}
        </div>
    );
}
