import { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    getCompanies, getSimilarCompanies, getCompanyMentions, getCompanyInsight,
} from '../api/companyService';
import CompanyLogo from '../components/common/CompanyLogo';
import RingGauge from '../components/common/RingGauge';
import TechRadarChart from '../components/company/TechRadarChart';
import CompanyNeighborhoodGraph from '../components/company/CompanyNeighborhoodGraph';
import CompareCompaniesPanel from '../components/company/CompareCompaniesPanel';
import './CompanyExplorer.css';

function CompanyMentions({ companyId }) {
    const [mentions, setMentions] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let cancelled = false;
        const fetchMentions = () => {
            setLoading(true);
            getCompanyMentions(companyId, 5)
                .then(res => { if (!cancelled) setMentions(res?.data ?? []); })
                .catch(() => { if (!cancelled) setMentions([]); })
                .finally(() => { if (!cancelled) setLoading(false); });
        };
        fetchMentions();
        return () => { cancelled = true; };
    }, [companyId]);

    if (loading) return <div className="detail-loading"><div className="loading-spinner" /></div>;
    if (!mentions?.length) return <p className="company-empty-hint">Chưa có tin tức nào nhắc đến công ty này.</p>;

    return (
        <ul className="company-mentions-list">
            {mentions.map(m => (
                <li key={m.id} className="company-mention-item">
                    <a href={m.url} target="_blank" rel="noopener noreferrer" className="company-mention-title">
                        {m.title}
                    </a>
                    <span className="company-mention-meta">{m.sourcePlatform} · {m.publishDate || 'Chưa rõ ngày'}</span>
                </li>
            ))}
        </ul>
    );
}

function CompanyAiInsight({ company }) {
    const [requested, setRequested] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [insight, setInsight] = useState(null);

    const handleRequest = () => {
        setRequested(true);
        setLoading(true);
        setError('');
        getCompanyInsight(company.name)
            .then(res => setInsight(res?.data ?? null))
            .catch(() => setError('Không thể tạo nhận định AI lúc này.'))
            .finally(() => setLoading(false));
    };

    if (!requested) {
        return (
            <button type="button" className="btn btn-secondary" onClick={handleRequest}>
                ✨ Xem nhận định AI
            </button>
        );
    }

    return (
        <div className="company-ai-insight">
            {loading && <p className="ai-summary-status">Đang tạo nhận định...</p>}
            {error && <p className="ai-summary-status ai-summary-error">{error}</p>}
            {!loading && !error && insight && (
                <>
                    <p className="ai-summary-text">{insight.summary}</p>
                    {insight.highlights?.length > 0 && (
                        <ul className="company-ai-highlights">
                            {insight.highlights.map((h, i) => <li key={i}>{h}</li>)}
                        </ul>
                    )}
                </>
            )}
        </div>
    );
}

function SimilarPanel({ company, onClose, onCompare }) {
    const [similar, setSimilar] = useState(null);
    const [loading, setLoading] = useState(true);
    const [showGraph, setShowGraph] = useState(false);
    const navigate = useNavigate();

    useEffect(() => {
        getSimilarCompanies(company.id)
            .then(res => setSimilar(res?.data ?? []))
            .catch(() => setSimilar([]))
            .finally(() => setLoading(false));
    }, [company]);

    const hasMeta = company.industry || company.size;

    return (
        <div className="company-detail-panel">
            <div className="company-detail-header">
                <div className="company-detail-identity">
                    <CompanyLogo name={company.name} size={44} />
                    <div>
                        <h3 className="company-detail-title">{company.name}</h3>
                        <p className="company-detail-sub">{company.location || 'Chưa rõ địa điểm'} · {company.job_count} tin tuyển dụng</p>
                        {hasMeta && (
                            <div className="company-meta-badges">
                                <span className="company-meta-badge">{company.industry || 'Chưa rõ ngành'}</span>
                                <span className="company-meta-badge">{company.size || 'Chưa rõ quy mô'}</span>
                            </div>
                        )}
                    </div>
                </div>
                <button className="detail-close" onClick={onClose} aria-label="Đóng">✕</button>
            </div>

            <div className="company-detail-stack">
                <p className="detail-section-label">Tech stack</p>
                <div className="skills-chips">
                    {company.tech_stack.map(t => (
                        <span key={t} className="skill-chip skill-chip--have">{t}</span>
                    ))}
                </div>
            </div>

            <div className="company-detail-section">
                <p className="detail-section-label">Hồ sơ công nghệ (Tech DNA)</p>
                <TechRadarChart series={[{ name: company.name, techStack: company.tech_stack, color: '#4f9dff' }]} height={220} />
            </div>

            <div className="company-actions-row">
                <button type="button" className="btn btn-ghost" onClick={() => onCompare(company)}>
                    So sánh với công ty khác
                </button>
                <button
                    type="button"
                    className="btn btn-ghost"
                    onClick={() => navigate('/interview', { state: { targetCompany: company.name } })}
                >
                    Luyện phỏng vấn công ty này
                </button>
                <CompanyAiInsight company={company} />
            </div>

            <div className="company-detail-section">
                <p className="detail-section-label">
                    Bản đồ liên kết
                    <button type="button" className="detail-section-toggle" onClick={() => setShowGraph(v => !v)}>
                        {showGraph ? 'Ẩn' : 'Hiện'}
                    </button>
                </p>
                {showGraph && <CompanyNeighborhoodGraph companyName={company.name} height={280} />}
            </div>

            <div className="company-detail-section">
                <p className="detail-section-label">Tin tức liên quan</p>
                <CompanyMentions companyId={company.id} />
            </div>

            <p className="detail-section-label">Công ty có tech stack tương tự</p>
            {loading ? (
                <div className="detail-loading"><div className="loading-spinner" /></div>
            ) : similar.length === 0 ? (
                <p className="company-empty-hint">Chưa tìm thấy công ty nào có tech stack trùng lặp.</p>
            ) : (
                <div className="similar-company-list">
                    {similar.map((s) => {
                        const scorePercent = Math.round((s.score || 0) * 100);
                        return (
                            <div key={s.id} className="similar-company-row">
                                <RingGauge percent={scorePercent} size={36} strokeWidth={4} label={scorePercent} />
                                <div className="similar-company-info">
                                    <div className="similar-company-identity">
                                        <CompanyLogo name={s.name} size={44} />
                                        <span className="similar-company-name">{s.name}</span>
                                    </div>
                                    {s.location && <span className="similar-company-location">{s.location}</span>}
                                    <div className="skills-chips">
                                        {s.shared_techs.map(t => (
                                            <span key={t} className="skill-chip skill-chip--have">{t}</span>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

export default function CompanyExplorer() {
    const [companies, setCompanies] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [search, setSearch] = useState('');
    const [selected, setSelected] = useState(null);
    const [compareSeed, setCompareSeed] = useState(null);

    useEffect(() => {
        getCompanies()
            .then(res => setCompanies(res?.data ?? []))
            .catch(() => setError('Không thể tải danh sách công ty. Vui lòng thử lại.'))
            .finally(() => setLoading(false));
    }, []);

    const filtered = useMemo(() => {
        if (!search.trim()) return companies;
        const q = search.trim().toLowerCase();
        return companies.filter(c =>
            c.name.toLowerCase().includes(q) ||
            c.tech_stack.some(t => t.toLowerCase().includes(q))
        );
    }, [companies, search]);

    if (loading) return (
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

    if (error) return (
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

            <div className={`company-layout${selected ? ' has-detail' : ''}`}>
                <div className="company-grid">
                    {filtered.map(c => (
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
                    {filtered.length === 0 && (
                        <p className="company-empty-hint">Không tìm thấy công ty nào phù hợp.</p>
                    )}
                </div>

                {selected && (
                    <SimilarPanel
                        key={selected.id}
                        company={selected}
                        onClose={() => setSelected(null)}
                        onCompare={(c) => setCompareSeed([c])}
                    />
                )}
            </div>

            {compareSeed !== null && (
                <CompareCompaniesPanel
                    companies={companies}
                    initialSelected={compareSeed}
                    onClose={() => setCompareSeed(null)}
                />
            )}
        </div>
    );
}
