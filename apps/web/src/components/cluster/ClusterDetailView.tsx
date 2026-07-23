import ForceGraph2D from 'react-force-graph-2d';
import type { ForceGraphMethods, NodeObject, LinkObject } from 'react-force-graph-2d';
import type { RefObject } from 'react';
import { CHART_PALETTE as COLORS } from '../../utils/chartPalette';
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
    return (
        <div className="cluster-detail-container">
            <div className="cluster-graph-card">
                <button className="btn-back-floating" onClick={onBack} title="Quay lại danh sách">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                        <line x1="19" y1="12" x2="5" y2="12"></line>
                        <polyline points="12 19 5 12 12 5"></polyline>
                    </svg>
                </button>

                {detailLoading ? (
                    <div className="loading-overlay">
                        Đang tải chi tiết cụm...
                    </div>
                ) : cluster ? (
                    <div className="graph-wrapper">
                        <ForceGraph2D
                            ref={fgRef}
                            graphData={graphData}
                            nodeCanvasObject={paintNode}
                            linkCanvasObject={paintLink}
                            onNodeHover={onNodeHover}
                            enableNodeDrag={true}
                            enableZoomInteraction={true}
                            enablePanInteraction={true}
                            backgroundColor="#0a0a0a"
                        />
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
                                    {cluster.overridden && <span className="badge badge-primary">Đã chỉnh sửa thủ công</span>}
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
