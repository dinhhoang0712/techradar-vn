import { useState, useEffect, useCallback, useRef } from 'react';
import { getCompanies } from '../api/companyService';
import CompanyLogo from '../components/common/CompanyLogo';
import CompareCompaniesPanel from '../components/company/CompareCompaniesPanel';
import SimilarCompanyPanel from '../components/company/SimilarCompanyPanel';
import type { Company } from '../types/company';
import './CompanyExplorer.css';

const PAGE_SIZE = 24;

export default function CompanyExplorer() {
    const [companies, setCompanies] = useState<Company[]>([]);
    const [initialLoading, setInitialLoading] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);
    const [error, setError] = useState('');
    const [search, setSearch] = useState('');
    const [debouncedSearch, setDebouncedSearch] = useState('');
    const [hasMore, setHasMore] = useState(true);
    const [selected, setSelected] = useState<Company | null>(null);
    const [compareSeed, setCompareSeed] = useState<Company[] | null>(null);
    const [compareOptions, setCompareOptions] = useState<Company[] | null>(null);

    // Backpressure bookkeeping (refs, not state, so the IntersectionObserver callback and the
    // fetch itself always read the latest value instead of one captured at effect-setup time):
    //   loadingRef   — at most one /companies request in flight; a scroll trigger while a
    //                  request is already running is dropped rather than queued.
    //   hasMoreRef   — stop observing once the backend has confirmed there's nothing left to page in.
    //   pageRef      — next page to fetch; advances only after a page successfully lands.
    //   requestIdRef — discards a page response that resolves after a newer search superseded it.
    const loadingRef = useRef(false);
    const hasMoreRef = useRef(true);
    const pageRef = useRef(0);
    const requestIdRef = useRef(0);

    // Debounce the search box so every keystroke doesn't fire a request.
    useEffect(() => {
        const t = setTimeout(() => setDebouncedSearch(search.trim()), 300);
        return () => clearTimeout(t);
    }, [search]);

    const loadPage = useCallback(async (targetPage: number, targetQuery: string) => {
        if (loadingRef.current) return;
        loadingRef.current = true;
        const requestId = ++requestIdRef.current;
        setLoadingMore(true);
        try {
            const res = await getCompanies({ q: targetQuery || undefined, page: targetPage, size: PAGE_SIZE });
            if (requestId !== requestIdRef.current) return; // stale — a newer search superseded this page
            const data = res?.data ?? [];
            setCompanies(prev => (targetPage === 0 ? data : [...prev, ...data]));
            pageRef.current = targetPage;
            const more = data.length === PAGE_SIZE;
            hasMoreRef.current = more;
            setHasMore(more);
            setError('');
        } catch {
            if (requestId !== requestIdRef.current) return;
            setError('Không thể tải danh sách công ty. Vui lòng thử lại.');
        } finally {
            if (requestId === requestIdRef.current) loadingRef.current = false;
            setLoadingMore(false);
            setInitialLoading(false);
        }
    }, []);

    // A settled search term always restarts the list from page 0 (the old list stays on screen,
    // undisturbed, until the fresh page 0 actually lands — same as the previous Prev/Next version).
    useEffect(() => {
        pageRef.current = 0;
        hasMoreRef.current = true;
        setHasMore(true);
        loadPage(0, debouncedSearch);
    }, [debouncedSearch, loadPage]);

    const loadNextPage = useCallback(() => {
        if (loadingRef.current || !hasMoreRef.current) return;
        loadPage(pageRef.current + 1, debouncedSearch);
    }, [loadPage, debouncedSearch]);

    // Infinite scroll: a sentinel element sits right after the grid, but it only enters the DOM
    // once the first page has actually rendered (see the JSX below) — a plain useEffect keyed on
    // the sentinel ref wouldn't re-run once that happens, so a callback ref is used instead: React
    // invokes it exactly when the node mounts (with a 200px lookahead so the next page is ready
    // before the user hits bottom) and again with `null` when it unmounts or `loadNextPage`
    // changes identity (a new search term). loadNextPage's own guards make repeated intersection
    // triggers safe even if the observer fires again before the previous page finished loading.
    const observerRef = useRef<IntersectionObserver | null>(null);
    const sentinelRef = useCallback((node: HTMLDivElement | null) => {
        observerRef.current?.disconnect();
        observerRef.current = null;
        if (!node) return;
        const observer = new IntersectionObserver(
            (entries) => {
                if (entries[0]?.isIntersecting) loadNextPage();
            },
            { rootMargin: '200px' },
        );
        observer.observe(node);
        observerRef.current = observer;
    }, [loadNextPage]);

    // The compare picker needs a broader pool of companies than one page of the grid, so it's
    // loaded lazily (only once, the first time the panel opens) instead of upfront.
    useEffect(() => {
        if (compareSeed === null || compareOptions !== null) return;
        getCompanies({ size: 100 })
            .then(res => setCompareOptions(res?.data ?? []))
            .catch(() => setCompareOptions([]));
    }, [compareSeed, compareOptions]);

    if (initialLoading) return (
        <div className="company-explorer">
            <div className="company-hero">
                <h1 className="company-title">Công ty & Tech Stack</h1>
                <p className="company-subtitle">
                    Tech stack suy ra từ tin tuyển dụng — tìm công ty đang dùng công nghệ bạn quan tâm, hoặc công ty tương tự
                </p>
            </div>
            <div className="company-grid">
                {Array.from({ length: 8 }).map((_, i) => (
                    <div className="company-card card company-card-skeleton" key={i}>
                        <div className="company-card-header">
                            <div className="company-card-identity">
                                <div className="skeleton company-skel-logo" />
                                <div className="skeleton company-skel-name" />
                            </div>
                            <div className="skeleton company-skel-jobs" />
                        </div>
                        <div className="skeleton company-skel-location" />
                        <div className="skills-chips">
                            <div className="skeleton company-skel-chip" />
                            <div className="skeleton company-skel-chip" />
                            <div className="skeleton company-skel-chip" />
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );

    if (error && companies.length === 0) return (
        <div className="company-explorer company-error">
            <div className="error-box">
                <div style={{ fontSize: '3rem' }}>🏢</div>
                <h2>Chưa có dữ liệu</h2>
                <p>{error}</p>
                <button className="btn btn-primary" onClick={() => window.location.reload()}>Thử lại</button>
            </div>
        </div>
    );

    return (
        <div className="company-explorer">
            <div className="company-hero">
                <h1 className="company-title">Công ty & Tech Stack</h1>
                <p className="company-subtitle">
                    Tech stack suy ra từ tin tuyển dụng — tìm công ty đang dùng công nghệ bạn quan tâm, hoặc công ty tương tự
                </p>
            </div>

            <div className="company-toolbar">
                <input
                    className="company-search form-input"
                    placeholder="Tìm công ty hoặc công nghệ (VD: React, AWS)..."
                    value={search}
                    onChange={e => setSearch(e.target.value)}
                />
                <button type="button" className="btn btn-secondary" onClick={() => setCompareSeed([])}>
                    So sánh nhiều công ty
                </button>
            </div>

            {error && companies.length > 0 && (
                <p className="company-empty-hint">{error}</p>
            )}

            <div className={`company-layout${selected ? ' has-detail' : ''}`}>
                <div className="company-grid">
                    {companies.map(c => (
                        <button
                            type="button"
                            key={c.id}
                            className={`company-card card${selected?.id === c.id ? ' selected' : ''}`}
                            onClick={() => setSelected(selected?.id === c.id ? null : c)}
                        >
                            <div className="company-card-header">
                                <div className="company-card-identity">
                                    <CompanyLogo name={c.name} size={44} />
                                    <span className="company-card-name">{c.name}</span>
                                </div>
                                <span className="company-card-jobs">{c.job_count} tin</span>
                            </div>
                            {c.location && <span className="company-card-location">{c.location}</span>}
                            {(c.industry || c.size) && (
                                <span className="company-card-meta">{[c.industry, c.size].filter(Boolean).join(' · ')}</span>
                            )}
                            <div className="skills-chips">
                                {c.tech_stack.slice(0, 6).map(t => (
                                    <span key={t} className="skill-chip skill-chip--have">{t}</span>
                                ))}
                                {c.tech_stack.length > 6 && (
                                    <span className="skill-chip skill-chip--missing">+{c.tech_stack.length - 6}</span>
                                )}
                            </div>
                        </button>
                    ))}
                    {companies.length === 0 && (
                        <p className="company-empty-hint">Không tìm thấy công ty nào phù hợp.</p>
                    )}
                </div>

                {selected && (
                    <SimilarCompanyPanel
                        key={selected.id}
                        company={selected}
                        onClose={() => setSelected(null)}
                        onCompare={(c) => setCompareSeed([c])}
                    />
                )}
            </div>

            {companies.length > 0 && (
                <div className="company-scroll-status" data-testid="scroll-sentinel" ref={sentinelRef}>
                    {loadingMore && <span className="scroll-status-loading">Đang tải thêm công ty...</span>}
                    {!loadingMore && !hasMore && <span className="scroll-status-end">Đã hiển thị toàn bộ công ty phù hợp.</span>}
                </div>
            )}

            {compareSeed !== null && (
                <CompareCompaniesPanel
                    companies={compareOptions ?? []}
                    initialSelected={compareSeed}
                    onClose={() => setCompareSeed(null)}
                />
            )}
        </div>
    );
}
