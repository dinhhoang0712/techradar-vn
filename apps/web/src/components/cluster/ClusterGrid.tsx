import { CHART_PALETTE as COLORS } from '../../utils/chartPalette';
import RingGauge from '../common/RingGauge';
import type { ClusterSummary } from '../../types/cluster';

interface ClusterGridProps {
    clusters: ClusterSummary[];
    searchQuery: string;
    onSelect: (clusterId: number) => void;
    onLookupTech: () => void;
    techLookupLoading: boolean;
    techLookupError: string;
}

// Lưới tổng quan tất cả cụm công nghệ — bấm 1 thẻ để xem chi tiết. Khi tìm kiếm không khớp cụm nào,
// cho phép tra cứu theo tên công nghệ cụ thể (cluster có thể chứa công nghệ đó dù tên cụm/domain
// không khớp từ khoá).
export default function ClusterGrid({
    clusters, searchQuery, onSelect,
    onLookupTech, techLookupLoading, techLookupError,
}: ClusterGridProps) {
    return (
        <div className="cluster-grid">
            {clusters.map(cluster => {
                const color = COLORS[cluster.cluster_id % COLORS.length];
                const orbitCount = 3 + (cluster.cluster_id % 4); // 3-6 vệ tinh, đổi theo từng cụm cho sinh động
                const orbitRadius = 16;
                return (
                    <button
                        type="button"
                        key={cluster.cluster_id}
                        className="cluster-grid-item"
                        onClick={() => onSelect(cluster.cluster_id)}
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
                        {(cluster.overridden || cluster.is_coherent === false) && (
                            <div className="cluster-card-badges">
                                {cluster.overridden && <span className="badge badge-primary">Đã chỉnh sửa</span>}
                                {cluster.is_coherent === false && <span className="badge badge-down">Chưa mạch lạc</span>}
                            </div>
                        )}
                    </button>
                );
            })}
            {clusters.length === 0 && (
                <div style={{ gridColumn: '1 / -1', textAlign: 'center', padding: '60px', color: 'var(--text-3)' }}>
                    <p>Không tìm thấy cụm nào phù hợp.</p>
                    {searchQuery.trim() && (
                        <>
                            <button
                                type="button"
                                className="btn btn-secondary"
                                style={{ marginTop: 16 }}
                                onClick={onLookupTech}
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
    );
}
