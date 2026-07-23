import { useEffect, useMemo, useState, useCallback } from 'react';
import type { FormEvent } from 'react';
import {
    fetchClusters,
    fetchClusterDetail,
    updateClusterLabel,
} from '../../api/adminService';
import type { UpdateClusterLabelFields } from '../../api/adminService';
import type { ClusterSummary, ClusterDetail } from '../../types/cluster';
import Modal from '../../components/common/Modal';
import RingGauge from '../../components/common/RingGauge';
import { useToast } from '../../components/common/toastContext';
import './AdminClusters.css';

const LOW_CONFIDENCE_THRESHOLD = 0.6;
const DOMAIN_PALETTE = ['primary', 'accent', 'green', 'yellow', 'danger'];

function domainClass(domain?: string): string {
    if (!domain) return 'domain-badge-primary';
    let hash = 0;
    for (let i = 0; i < domain.length; i++) hash = (hash * 31 + domain.charCodeAt(i)) >>> 0;
    return `domain-badge-${DOMAIN_PALETTE[hash % DOMAIN_PALETTE.length]}`;
}

function needsReview(cluster: ClusterSummary): boolean {
    return !cluster.is_coherent || (cluster.confidence ?? 0) < LOW_CONFIDENCE_THRESHOLD;
}

interface ClusterLabelForm {
    label: string;
    labelEn: string;
    description: string;
    domain: string;
}

export default function AdminClusters() {
    const [clusters, setClusters] = useState<ClusterSummary[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [query, setQuery] = useState('');

    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [detail, setDetail] = useState<ClusterDetail | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [form, setForm] = useState<ClusterLabelForm>({ label: '', labelEn: '', description: '', domain: '' });
    const [saving, setSaving] = useState(false);

    const notify = useToast();

    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const res = await fetchClusters();
            setClusters(Array.isArray(res?.data) ? res.data : []);
        } catch (e) {
            setError((e as Error).message || 'Không tải được danh sách cụm công nghệ');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { load(); }, [load]);

    const domainOptions = useMemo(
        () => [...new Set(clusters.map(c => c.domain).filter(Boolean))].sort(),
        [clusters]
    );

    const filtered = useMemo(() => {
        const q = query.trim().toLowerCase();
        if (!q) return clusters;
        return clusters.filter(c =>
            c.label?.toLowerCase().includes(q) ||
            c.label_en?.toLowerCase().includes(q) ||
            c.domain?.toLowerCase().includes(q)
        );
    }, [clusters, query]);

    const reviewQueue = useMemo(
        () => filtered.filter(needsReview).sort((a, b) => (a.confidence ?? 0) - (b.confidence ?? 0)),
        [filtered]
    );
    const rest = useMemo(
        () => filtered.filter(c => !needsReview(c)).sort((a, b) => a.cluster_id - b.cluster_id),
        [filtered]
    );

    const openDetail = async (cluster: ClusterSummary) => {
        setSelectedId(cluster.cluster_id);
        setDetail(null);
        setDetailLoading(true);
        try {
            const res = await fetchClusterDetail(cluster.cluster_id);
            const d = res?.data;
            setDetail(d ?? null);
            setForm({
                label: d?.label || '',
                labelEn: d?.label_en || '',
                description: d?.description || '',
                domain: d?.domain || '',
            });
        } catch (e) {
            console.error('Failed to load cluster detail:', e);
            notify({ title: 'Không tải được chi tiết cụm', variant: 'error' });
            setSelectedId(null);
        } finally {
            setDetailLoading(false);
        }
    };

    const closeDetail = () => { setSelectedId(null); setDetail(null); };

    const handleSave = async (e: FormEvent) => {
        e.preventDefault();
        if (!detail) return;
        const fields: UpdateClusterLabelFields = {};
        if (form.label.trim() && form.label !== detail.label) fields.label = form.label.trim();
        if (form.labelEn.trim() && form.labelEn !== detail.label_en) fields.labelEn = form.labelEn.trim();
        if (form.description.trim() && form.description !== detail.description) fields.description = form.description.trim();
        if (form.domain.trim() && form.domain !== detail.domain) fields.domain = form.domain.trim();

        if (Object.keys(fields).length === 0) {
            notify({ title: 'Chưa có thay đổi nào để lưu', variant: 'error' });
            return;
        }

        setSaving(true);
        try {
            const res = await updateClusterLabel(detail.cluster_id, fields);
            const updated = res?.data;
            if (!updated) return;
            setDetail(updated);
            setClusters(prev => prev.map(c => (c.cluster_id === updated.cluster_id ? { ...c, ...updated } : c)));
            notify({ title: 'Đã lưu nhãn cụm', variant: 'success' });
        } catch (e) {
            notify({ title: 'Lưu thất bại', body: (e as Error).message, variant: 'error' });
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="admin-clusters">
            <div className="clusters-header">
                <div className="clusters-title">
                    <h2>Cụm Công nghệ (AI Clustering)</h2>
                    <p>Xem lại và chỉnh sửa nhãn do AI gán cho từng cụm công nghệ — cụm có độ tin cậy thấp hoặc chưa mạch lạc được ưu tiên hiển thị trước.</p>
                </div>
                <div className="clusters-search">
                    <input
                        type="text"
                        placeholder="Tìm theo tên hoặc lĩnh vực…"
                        value={query}
                        onChange={e => setQuery(e.target.value)}
                    />
                </div>
            </div>

            {loading && (
                <div className="admin-loading-container">
                    <div className="loading-spinner"></div>
                    <p>Đang tải danh sách cụm…</p>
                </div>
            )}

            {!loading && error && (
                <div className="clusters-error card">
                    <p>{error}</p>
                    <button className="btn btn-secondary" onClick={load}>Thử lại</button>
                </div>
            )}

            {!loading && !error && filtered.length === 0 && (
                <div className="clusters-empty card">Không tìm thấy cụm nào phù hợp.</div>
            )}

            {!loading && !error && reviewQueue.length > 0 && (
                <section className="clusters-section">
                    <h3 className="clusters-section-title needs-review">
                        <span className="review-dot" /> Cần xem xét ({reviewQueue.length})
                    </h3>
                    <div className="clusters-grid">
                        {reviewQueue.map(c => (
                            <ClusterCard key={c.cluster_id} cluster={c} flagged onOpen={() => openDetail(c)} />
                        ))}
                    </div>
                </section>
            )}

            {!loading && !error && rest.length > 0 && (
                <section className="clusters-section">
                    <h3 className="clusters-section-title">Tất cả cụm ({rest.length})</h3>
                    <div className="clusters-grid">
                        {rest.map(c => (
                            <ClusterCard key={c.cluster_id} cluster={c} onOpen={() => openDetail(c)} />
                        ))}
                    </div>
                </section>
            )}

            {selectedId !== null && (
                <Modal
                    title={detail ? `Cụm #${detail.cluster_id} — ${detail.label}` : `Cụm #${selectedId}`}
                    onClose={closeDetail}
                    width="640px"
                >
                    {detailLoading && (
                        <div className="admin-loading-container" style={{ minHeight: 160 }}>
                            <div className="loading-spinner"></div>
                            <p>Đang tải chi tiết…</p>
                        </div>
                    )}

                    {!detailLoading && detail && (
                        <div className="cluster-detail">
                            <div className="cluster-detail-meta">
                                <RingGauge percent={(detail.confidence ?? 0) * 100} size={48} strokeWidth={5} label={`${Math.round((detail.confidence ?? 0) * 100)}%`} />
                                <div className="cluster-detail-meta-text">
                                    <span className={`domain-badge ${domainClass(detail.domain)}`}>{detail.domain}</span>
                                    <span>{detail.n_members} công nghệ</span>
                                    {detail.overridden && <span className="badge badge-primary">Đã chỉnh sửa thủ công</span>}
                                    {!detail.is_coherent && <span className="badge badge-down">AI đánh giá: chưa mạch lạc</span>}
                                </div>
                            </div>

                            {!detail.is_coherent && detail.coherence_reason && (
                                <p className="cluster-coherence-reason">"{detail.coherence_reason}"</p>
                            )}

                            {(detail.outliers?.length ?? 0) > 0 && (
                                <div className="cluster-outliers">
                                    <strong>Công nghệ lệch nhóm:</strong>
                                    <div className="pill-group">
                                        {detail.outliers!.map(name => <span key={name} className="pill">{name}</span>)}
                                    </div>
                                </div>
                            )}

                            <div className="cluster-members">
                                <strong>Thành viên ({detail.members?.length ?? 0}):</strong>
                                <div className="pill-group">
                                    {(detail.members ?? []).map(name => <span key={name} className="pill">{name}</span>)}
                                </div>
                            </div>

                            <form className="modal-form cluster-edit-form" onSubmit={handleSave}>
                                <div className="form-row">
                                    <div className="form-group">
                                        <label>Tên nhãn (Tiếng Việt)</label>
                                        <input type="text" value={form.label} onChange={e => setForm(f => ({ ...f, label: e.target.value }))} />
                                    </div>
                                    <div className="form-group">
                                        <label>Tên nhãn (English)</label>
                                        <input type="text" value={form.labelEn} onChange={e => setForm(f => ({ ...f, labelEn: e.target.value }))} />
                                    </div>
                                </div>
                                <div className="form-group">
                                    <label>Lĩnh vực</label>
                                    <input
                                        type="text"
                                        list="cluster-domain-options"
                                        value={form.domain}
                                        onChange={e => setForm(f => ({ ...f, domain: e.target.value }))}
                                    />
                                    <datalist id="cluster-domain-options">
                                        {domainOptions.map(d => <option key={d} value={d} />)}
                                    </datalist>
                                </div>
                                <div className="form-group">
                                    <label>Mô tả</label>
                                    <textarea
                                        rows={3}
                                        value={form.description}
                                        onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                                    />
                                </div>
                                {detail.overridden_by && (
                                    <p className="cluster-override-meta">
                                        Đã sửa lần cuối bởi {detail.overridden_by}
                                        {detail.overridden_at ? ` · ${new Date(detail.overridden_at).toLocaleString('vi-VN')}` : ''}
                                    </p>
                                )}
                                <div className="modal-actions">
                                    <button type="button" className="btn btn-ghost" onClick={closeDetail} disabled={saving}>Đóng</button>
                                    <button type="submit" className="btn btn-primary" disabled={saving}>
                                        {saving ? 'Đang lưu…' : 'Lưu nhãn'}
                                    </button>
                                </div>
                            </form>
                        </div>
                    )}
                </Modal>
            )}
        </div>
    );
}

interface ClusterCardProps {
    cluster: ClusterSummary;
    flagged?: boolean;
    onOpen: () => void;
}

function ClusterCard({ cluster, flagged, onOpen }: ClusterCardProps) {
    return (
        <button className={`cluster-card card${flagged ? ' flagged' : ''}`} onClick={onOpen}>
            <div className="cluster-card-top">
                <span className={`domain-badge ${domainClass(cluster.domain)}`}>{cluster.domain}</span>
                <RingGauge percent={(cluster.confidence ?? 0) * 100} size={36} strokeWidth={4} label={Math.round((cluster.confidence ?? 0) * 100)} />
            </div>
            <h4 className="cluster-card-label">{cluster.label}</h4>
            <p className="cluster-card-label-en">{cluster.label_en}</p>
            <div className="cluster-card-footer">
                <span>{cluster.n_members} công nghệ</span>
                {cluster.overridden && <span className="badge badge-primary">Đã sửa</span>}
                {!cluster.is_coherent && <span className="badge badge-down">Chưa mạch lạc</span>}
            </div>
        </button>
    );
}
