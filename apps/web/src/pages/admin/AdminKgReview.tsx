import { useCallback, useEffect, useRef, useState } from 'react';
import {
    fetchTechAliasReviewQueue, fetchTechAliasReviewCount, approveTechAlias, rejectTechAlias,
    fetchCompanyDuplicates, mergeCompanyDuplicate, KG_REVIEW_CHANGED_EVENT,
} from '../../api/adminService';
import type { TechAliasReviewItem, CompanyDuplicateGroup } from '../../api/adminService';
import Modal from '../../components/common/Modal';
import { useToast } from '../../components/common/toastContext';
import './AdminKgReview.css';

const PAGE_SIZE = 20;
type Tab = 'tech' | 'company';

function formatDateTime(iso?: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleString('vi-VN');
}

export default function AdminKgReview() {
    const [tab, setTab] = useState<Tab>('tech');
    const notify = useToast();

    // Sliding tab indicator — measured from real button widths since the two tabs have
    // different label lengths + optional badges, so a fixed 50/50 split misaligns (badge spills past the pill).
    const tabRefs = useRef<Record<Tab, HTMLButtonElement | null>>({ tech: null, company: null });
    const [indicatorStyle, setIndicatorStyle] = useState<{ left: number; width: number } | null>(null);

    // --- Technology alias tab ---
    const [items, setItems] = useState<TechAliasReviewItem[]>([]);
    // Tổng số đang pending trong toàn bộ hàng đợi (Postgres COUNT, giống sidebar) — KHÔNG dùng
    // items.length cho badge vì items chỉ là 1 trang (PAGE_SIZE=20), sẽ lệch hẳn với con số thật
    // khi hàng đợi có hàng trăm dòng pending.
    const [totalPending, setTotalPending] = useState<number | null>(null);
    const [page, setPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [loadingItems, setLoadingItems] = useState(true);
    // Which side of the pair is canonical, per item id — defaults to 'b' (the LLM's suggestion).
    const [canonicalSide, setCanonicalSide] = useState<Record<number, 'a' | 'b'>>({});
    const [approveTarget, setApproveTarget] = useState<TechAliasReviewItem | null>(null);
    const [rejectTarget, setRejectTarget] = useState<TechAliasReviewItem | null>(null);
    const [busyId, setBusyId] = useState<number | null>(null);

    // --- Company duplicate tab ---
    const [groups, setGroups] = useState<CompanyDuplicateGroup[]>([]);
    const [loadingGroups, setLoadingGroups] = useState(false);
    const [groupsLoaded, setGroupsLoaded] = useState(false);
    const [canonicalCompanyId, setCanonicalCompanyId] = useState<Record<string, string>>({});
    const [mergeTarget, setMergeTarget] = useState<{ group: CompanyDuplicateGroup; canonicalId: string } | null>(null);
    const [mergingCore, setMergingCore] = useState<string | null>(null);

    const loadItems = useCallback(async (targetPage: number) => {
        try {
            setLoadingItems(true);
            const res = await fetchTechAliasReviewQueue(targetPage, PAGE_SIZE);
            const data = res?.data || [];
            setItems(data);
            setHasMore(data.length === PAGE_SIZE);
        } catch (error) {
            console.error('Failed to load tech alias review queue:', error);
            notify({ title: 'Không tải được hàng đợi alias', variant: 'error' });
        } finally {
            setLoadingItems(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => { loadItems(page); }, [page, loadItems]);

    const loadTotalPending = useCallback(() => {
        fetchTechAliasReviewCount()
            .then(res => setTotalPending(res?.data?.pending ?? 0))
            .catch(() => {});
    }, []);

    useEffect(() => {
        loadTotalPending();
        window.addEventListener(KG_REVIEW_CHANGED_EVENT, loadTotalPending);
        return () => window.removeEventListener(KG_REVIEW_CHANGED_EVENT, loadTotalPending);
    }, [loadTotalPending]);

    const loadGroups = useCallback(async () => {
        try {
            setLoadingGroups(true);
            const res = await fetchCompanyDuplicates();
            setGroups(res?.data || []);
            setGroupsLoaded(true);
        } catch (error) {
            console.error('Failed to detect company duplicates:', error);
            notify({ title: 'Không quét được công ty trùng lặp', variant: 'error' });
        } finally {
            setLoadingGroups(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => {
        if (tab === 'company' && !groupsLoaded) loadGroups();
    }, [tab, groupsLoaded, loadGroups]);

    useEffect(() => {
        const measure = () => {
            const el = tabRefs.current[tab];
            if (el) setIndicatorStyle({ left: el.offsetLeft, width: el.offsetWidth });
        };
        measure();
        window.addEventListener('resize', measure);
        return () => window.removeEventListener('resize', measure);
    }, [tab, items.length, groups.length]);

    const sideFor = (item: TechAliasReviewItem): 'a' | 'b' => canonicalSide[item.id] || 'b';
    const canonicalName = (item: TechAliasReviewItem) => (sideFor(item) === 'a' ? item.name_a : item.name_b);

    const handleConfirmApprove = async () => {
        if (!approveTarget) return;
        const id = approveTarget.id;
        setBusyId(id);
        try {
            await approveTechAlias(id, canonicalName(approveTarget));
            setItems(prev => prev.filter(i => i.id !== id));
            window.dispatchEvent(new Event(KG_REVIEW_CHANGED_EVENT));
            notify({ title: `Đã gộp "${approveTarget.name_a}" ↔ "${approveTarget.name_b}"`, variant: 'success' });
        } catch (error) {
            console.error('Failed to approve tech alias:', error);
            notify({ title: 'Không gộp được — thử lại sau', variant: 'error' });
        } finally {
            setBusyId(null);
            setApproveTarget(null);
        }
    };

    const handleConfirmReject = async () => {
        if (!rejectTarget) return;
        const id = rejectTarget.id;
        setBusyId(id);
        try {
            await rejectTechAlias(id);
            setItems(prev => prev.filter(i => i.id !== id));
            window.dispatchEvent(new Event(KG_REVIEW_CHANGED_EVENT));
            notify({ title: 'Đã từ chối — giữ 2 công nghệ tách biệt', variant: 'success' });
        } catch (error) {
            console.error('Failed to reject tech alias:', error);
            notify({ title: 'Không xử lý được — thử lại sau', variant: 'error' });
        } finally {
            setBusyId(null);
            setRejectTarget(null);
        }
    };

    const handleConfirmMerge = async () => {
        if (!mergeTarget) return;
        const { group, canonicalId } = mergeTarget;
        const core = group.normalized_core;
        setMergingCore(core);
        // Thử gộp TỪNG công ty độc lập — 1 công ty lỗi (VD token hết hạn giữa chừng) không được
        // phép chặn các công ty còn lại trong nhóm, và người dùng cần biết chính xác cái nào lỗi
        // thay vì 1 toast chung chung "thử lại sau" không rõ đã gộp được bao nhiêu.
        const duplicates = group.companies.filter(c => c.id !== canonicalId);
        const failed: { name: string; message: string }[] = [];
        for (const duplicate of duplicates) {
            try {
                // eslint-disable-next-line no-await-in-loop -- merges must apply sequentially against the same canonical node
                await mergeCompanyDuplicate(duplicate.id, canonicalId);
            } catch (error) {
                console.error(`Failed to merge company duplicate ${duplicate.id}:`, error);
                failed.push({ name: duplicate.name, message: (error as Error).message || 'lỗi không rõ' });
            }
        }

        const mergedCount = duplicates.length - failed.length;
        if (failed.length === 0) {
            setGroups(prev => prev.filter(g => g.normalized_core !== core));
            notify({ title: `Đã gộp ${group.companies.length} công ty`, variant: 'success' });
        } else if (mergedCount > 0) {
            notify({
                title: `Đã gộp ${mergedCount}/${duplicates.length} công ty — lỗi: ${failed.map(f => f.name).join(', ')}`,
                variant: 'error',
            });
            loadGroups();
        } else {
            notify({ title: `Không gộp được: ${failed[0].message}`, variant: 'error' });
            loadGroups();
        }

        setMergingCore(null);
        setMergeTarget(null);
    };

    return (
        <div className="kg-review">
            <div className="kg-review-header">
                <div className="kg-review-title">
                    <h2>Hàng đợi duyệt Knowledge Graph</h2>
                    <p>Xác nhận các công nghệ/công ty mà hệ thống nghi trùng lặp nhưng chưa đủ tự tin để tự gộp.</p>
                </div>
            </div>

            <div className="kg-tabs">
                <button
                    ref={el => { tabRefs.current.tech = el; }}
                    className={`kg-tab${tab === 'tech' ? ' active' : ''}`}
                    onClick={() => setTab('tech')}
                >
                    Alias Công nghệ
                    {!!totalPending && <span className="kg-tab-badge">{totalPending}</span>}
                </button>
                <button
                    ref={el => { tabRefs.current.company = el; }}
                    className={`kg-tab${tab === 'company' ? ' active' : ''}`}
                    onClick={() => setTab('company')}
                >
                    Công ty nghi trùng
                    {groups.length > 0 && <span className="kg-tab-badge">{groups.length}</span>}
                </button>
                {indicatorStyle && (
                    <div
                        className="kg-tab-indicator"
                        style={{ left: indicatorStyle.left, width: indicatorStyle.width }}
                    />
                )}
            </div>

            {tab === 'tech' && (
                <div className="kg-panel">
                    {loadingItems && <div className="kg-empty-state">Đang tải hàng đợi…</div>}
                    {!loadingItems && items.length === 0 && (
                        <div className="kg-empty-state">
                            <span className="kg-empty-icon">✓</span>
                            Không có alias nào đang chờ duyệt
                        </div>
                    )}
                    {!loadingItems && items.map(item => {
                        const side = sideFor(item);
                        return (
                            <div className="kg-card" key={item.id}>
                                <div className="kg-pair">
                                    <button
                                        className={`kg-pill${side === 'a' ? ' canonical' : ''}`}
                                        onClick={() => setCanonicalSide(prev => ({ ...prev, [item.id]: 'a' }))}
                                        title="Chọn làm tên chuẩn"
                                    >
                                        {item.name_a}
                                    </button>
                                    <span className="kg-pair-arrow">⇄</span>
                                    <button
                                        className={`kg-pill${side === 'b' ? ' canonical' : ''}`}
                                        onClick={() => setCanonicalSide(prev => ({ ...prev, [item.id]: 'b' }))}
                                        title="Chọn làm tên chuẩn"
                                    >
                                        <span>{item.name_b}</span>
                                        <span className="kg-suggested-tag">AI đề xuất</span>
                                    </button>
                                </div>
                                {item.llm_reasoning && (
                                    <p className="kg-reasoning">“{item.llm_reasoning}”</p>
                                )}
                                <div className="kg-card-footer">
                                    <span className="kg-time">{formatDateTime(item.created_at)}</span>
                                    <div className="kg-actions">
                                        <button
                                            className="kg-btn ghost"
                                            disabled={busyId === item.id}
                                            onClick={() => setRejectTarget(item)}
                                        >
                                            Từ chối
                                        </button>
                                        <button
                                            className="kg-btn primary"
                                            disabled={busyId === item.id}
                                            onClick={() => setApproveTarget(item)}
                                        >
                                            {busyId === item.id ? 'Đang gộp…' : 'Duyệt gộp'}
                                        </button>
                                    </div>
                                </div>
                            </div>
                        );
                    })}

                    {!loadingItems && (items.length > 0 || page > 0) && (
                        <div className="kg-pagination">
                            <button className="btn btn-ghost" disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))}>
                                ‹ Trang trước
                            </button>
                            <span className="pagination-page">Trang {page + 1}</span>
                            <button className="btn btn-ghost" disabled={!hasMore} onClick={() => setPage(p => p + 1)}>
                                Trang sau ›
                            </button>
                        </div>
                    )}
                </div>
            )}

            {tab === 'company' && (
                <div className="kg-panel">
                    <div className="kg-panel-toolbar">
                        <p className="kg-panel-hint">
                            Quét trực tiếp từ Neo4j (không lưu) — chỉ gợi ý dựa trên tên pháp lý, luôn cần xác nhận trước khi gộp.
                        </p>
                        <button className="kg-btn ghost sm" disabled={loadingGroups} onClick={loadGroups}>
                            {loadingGroups ? 'Đang quét…' : '↻ Quét lại'}
                        </button>
                    </div>

                    {loadingGroups && <div className="kg-empty-state">Đang quét Neo4j để tìm công ty nghi trùng…</div>}
                    {!loadingGroups && groups.length === 0 && (
                        <div className="kg-empty-state">
                            <span className="kg-empty-icon">✓</span>
                            Không phát hiện công ty nào nghi trùng lặp
                        </div>
                    )}
                    {!loadingGroups && groups.map(group => {
                        const selected = canonicalCompanyId[group.normalized_core] || group.companies[0]?.id;
                        return (
                            <div className="kg-card" key={group.normalized_core}>
                                <div className="kg-group-label">Nhóm: “{group.normalized_core}”</div>
                                <div className="kg-chip-list">
                                    {group.companies.map(c => (
                                        <button
                                            key={c.id}
                                            className={`kg-chip${selected === c.id ? ' canonical' : ''}`}
                                            onClick={() => setCanonicalCompanyId(prev => ({ ...prev, [group.normalized_core]: c.id }))}
                                            title="Chọn làm công ty chuẩn để gộp các công ty khác vào"
                                        >
                                            {selected === c.id && <span className="kg-chip-star">★</span>}
                                            {c.name}
                                        </button>
                                    ))}
                                </div>
                                <div className="kg-card-footer">
                                    <span className="kg-time">{group.companies.length} công ty trong nhóm</span>
                                    <button
                                        className="kg-btn primary"
                                        disabled={mergingCore === group.normalized_core}
                                        onClick={() => setMergeTarget({ group, canonicalId: selected })}
                                    >
                                        {mergingCore === group.normalized_core ? 'Đang gộp…' : 'Gộp nhóm này'}
                                    </button>
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}

            {approveTarget && (
                <Modal title="Xác nhận gộp công nghệ" onClose={() => setApproveTarget(null)} width="440px">
                    <p className="modal-body-text">
                        Gộp <strong>"{sideFor(approveTarget) === 'a' ? approveTarget.name_b : approveTarget.name_a}"</strong> vào{' '}
                        <strong>"{canonicalName(approveTarget)}"</strong> — áp dụng ngay trên Neo4j sống và lưu lại để lần sau tự nhận diện.
                    </p>
                    <div className="modal-actions">
                        <button className="btn btn-ghost" onClick={() => setApproveTarget(null)}>Hủy bỏ</button>
                        <button className="btn btn-primary" onClick={handleConfirmApprove}>Xác nhận gộp</button>
                    </div>
                </Modal>
            )}

            {rejectTarget && (
                <Modal title="Từ chối gộp" onClose={() => setRejectTarget(null)} width="420px">
                    <p className="modal-body-text">
                        Giữ <strong>"{rejectTarget.name_a}"</strong> và <strong>"{rejectTarget.name_b}"</strong> là 2 công nghệ tách biệt?
                    </p>
                    <div className="modal-actions">
                        <button className="btn btn-ghost" onClick={() => setRejectTarget(null)}>Hủy bỏ</button>
                        <button className="btn btn-primary" onClick={handleConfirmReject}>Từ chối gộp</button>
                    </div>
                </Modal>
            )}

            {mergeTarget && (
                <Modal title="Xác nhận gộp công ty" onClose={() => setMergeTarget(null)} width="480px">
                    <p className="modal-body-text">
                        Gộp {mergeTarget.group.companies.length - 1} công ty còn lại vào{' '}
                        <strong>"{mergeTarget.group.companies.find(c => c.id === mergeTarget.canonicalId)?.name}"</strong>?
                        Hành động này áp dụng ngay trên Neo4j sống và không thể tự động hoàn tác.
                    </p>
                    <div className="modal-actions">
                        <button className="btn btn-ghost" onClick={() => setMergeTarget(null)}>Hủy bỏ</button>
                        <button className="btn btn-danger" onClick={handleConfirmMerge}>Xác nhận gộp</button>
                    </div>
                </Modal>
            )}
        </div>
    );
}
