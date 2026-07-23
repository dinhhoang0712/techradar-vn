import ForceGraph2D from 'react-force-graph-2d';
import type { ForceGraphMethods, NodeObject, LinkObject } from 'react-force-graph-2d';
import type { RefObject } from 'react';
import { useLayoutEffect, useRef, useState } from 'react';
import { CHART_PALETTE as COLORS } from '../../utils/chartPalette';
import { formatDateTime } from '../../utils/formatDateTime';
import RingGauge from '../common/RingGauge';
import type { ClusterDetail, ClusterGraphNode, ClusterGraphLink } from '../../types/cluster';

type ClusterFgMethods = ForceGraphMethods<NodeObject<ClusterGraphNode>, LinkObject<ClusterGraphNode, ClusterGraphLink>>;

interface ClusterDetailViewProps {
    fgRef: RefObject<ClusterFgMethods | undefined>;
    cluster: ClusterDetail | null;
    detailLoading: boolean;
    graphData: { nodes: ClusterGraphNode[]; links: LinkObject<ClusterGraphNode, ClusterGraphLink>[] };
    paintNode: (node: NodeObject<ClusterGraphNode>, ctx: CanvasRenderingContext2D, globalScale: number) => void;
    paintLink: (link: LinkObject<ClusterGraphNode, ClusterGraphLink>, ctx: CanvasRenderingContext2D) => void;
    onNodeHover: (node: NodeObject<ClusterGraphNode> | null) => void;
    onBack: () => void;
}

// Chi tiết 1 cụm công nghệ: đồ thị mạng lưới (cụm + các công nghệ thành viên) bên trái, bảng thông
// tin (domain, độ tin cậy, mô tả, danh sách công nghệ, outlier) bên phải.
export default function ClusterDetailView({
    fgRef, cluster, detailLoading, graphData, paintNode, paintLink, onNodeHover, onBack,
}: ClusterDetailViewProps) {
    // ForceGraph2D không tự đo container — nếu không truyền width/height tường minh nó mặc định
    // theo window.innerWidth/innerHeight, khiến canvas to hơn hẳn .graph-wrapper (bị overflow:hidden
    // cắt bớt) và zoomToFit căn giữa lệch hẳn ra ngoài phần nhìn thấy được.
    const wrapperRef = useRef<HTMLDivElement>(null);
    const [size, setSize] = useState({ width: 0, height: 0 });

    useLayoutEffect(() => {
        const el = wrapperRef.current;
        if (!el) return undefined;
        const update = () => setSize({ width: el.clientWidth, height: el.clientHeight });
        update();
        const ro = new ResizeObserver(update);
        ro.observe(el);
        return () => ro.disconnect();
    }, [cluster]);

    return (
        <div className="cluster-detail-container">
            <div className="cluster-graph-card">
                <button className="btn-back-floating" onClick={onBack} title="Quay lại danh sách">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                        <line x1="19" y1="12" x2="5" y2="12"></line>
                        <polyline points="12 19 5 12 12 5"></polyline>
                    </svg>
                </button>

                {cluster && (
                    <button
                        className="btn-fit-view-floating"
                        onClick={() => fgRef.current?.zoomToFit(400, 50)}
                        title="Căn giữa đồ thị"
                    >
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                            <polyline points="15 3 21 3 21 9"></polyline>
                            <polyline points="9 21 3 21 3 15"></polyline>
                            <line x1="21" y1="3" x2="14" y2="10"></line>
                            <line x1="3" y1="21" x2="10" y2="14"></line>
                        </svg>
                    </button>
                )}

                {detailLoading ? (
                    <div className="loading-overlay">
                        Đang tải chi tiết cụm...
                    </div>
                ) : cluster ? (
                    <div className="graph-wrapper" ref={wrapperRef}>
                        {size.width > 0 && size.height > 0 && (
                            <ForceGraph2D
                                ref={fgRef}
                                width={size.width}
                                height={size.height}
                                graphData={graphData}
                                nodeCanvasObject={paintNode}
                                linkCanvasObject={paintLink}
                                onNodeHover={onNodeHover}
                                enableNodeDrag={true}
                                enableZoomInteraction={true}
                                enablePanInteraction={true}
                                backgroundColor="#0a0a0a"
                            />
                        )}
                    </div>
                ) : null}
            </div>

            <div className="cluster-info-card">
                {detailLoading ? (
                    <div className="loading-text">Đang tải...</div>
                ) : cluster ? (
                    <>
                        <div className="cluster-detail-header">
                            <span className="cluster-domain-badge" style={{ background: COLORS[cluster.cluster_id % COLORS.length] + '33', color: COLORS[cluster.cluster_id % COLORS.length] }}>
                                {cluster.domain}
                            </span>
                            <h2 className="cluster-detail-title">{cluster.label}</h2>
                            <p className="cluster-subtitle">{cluster.label_en || 'Cluster Overview'}</p>
                            {(cluster.overridden || cluster.is_coherent === false) && (
                                <div className="cluster-detail-flags">
                                    {cluster.overridden && (
                                        <span
                                            className="badge badge-primary"
                                            title={[
                                                cluster.overridden_by && `bởi ${cluster.overridden_by}`,
                                                cluster.overridden_at && `lúc ${formatDateTime(cluster.overridden_at)}`,
                                            ].filter(Boolean).join(' ') || undefined}
                                        >
                                            Đã chỉnh sửa thủ công
                                        </span>
                                    )}
                                    {cluster.is_coherent === false && <span className="badge badge-down">AI đánh giá: chưa mạch lạc</span>}
                                </div>
                            )}
                        </div>

                        <div className="cluster-stats-row">
                            <div className="cluster-stat-box">
                                <div className="stat-val">{cluster.n_members}</div>
                                <div className="stat-label">Công nghệ</div>
                            </div>
                            <div className="cluster-stat-box cluster-stat-box-gauge">
                                <RingGauge percent={(cluster.confidence || 0) * 100} size={44} strokeWidth={5} label={`${Math.round((cluster.confidence || 0) * 100)}%`} />
                                <div className="stat-label">Tin cậy</div>
                            </div>
                            <div className="cluster-stat-box">
                                <div className="stat-val">#{cluster.cluster_id}</div>
                                <div className="stat-label">Cụm #</div>
                            </div>
                        </div>

                        {cluster.is_coherent === false && cluster.coherence_reason && (
                            <p className="cluster-coherence-reason">"{cluster.coherence_reason}"</p>
                        )}

                        <p className="cluster-description-text">{cluster.description}</p>

                        {cluster.outliers && cluster.outliers.length > 0 && (
                            <div className="cluster-tech-section cluster-outliers">
                                <h3 className="section-subtitle">Công nghệ lệch nhóm ({cluster.outliers.length})</h3>
                                <div className="pill-group">
                                    {cluster.outliers.map(name => <span key={name} className="pill">{name}</span>)}
                                </div>
                            </div>
                        )}

                        <div className="cluster-tech-section">
                            <h3 className="section-subtitle">Danh sách Công nghệ ({cluster.members?.length || 0})</h3>
                            <div className="cluster-tech-list">
                                {(cluster.members || []).map(tech => (
                                    <span key={tech} className="tech-tag" style={{
                                        border: `1px solid ${COLORS[cluster.cluster_id % COLORS.length]}55`,
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
    );
}
