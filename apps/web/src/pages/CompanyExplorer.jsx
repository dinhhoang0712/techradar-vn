import { useState, useEffect, useMemo } from 'react';
import { getCompanies, getSimilarCompanies } from '../api/companyService';
import CompanyLogo from '../components/common/CompanyLogo';
import RingGauge from '../components/common/RingGauge';
import './CompanyExplorer.css';

function SimilarPanel({ company, onClose }) {
    const [similar, setSimilar] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        getSimilarCompanies(company.id)
            .then(res => setSimilar(res?.data ?? []))
            .catch(() => setSimilar([]))
            .finally(() => setLoading(false));
    }, [company]);

    return (
        <div className="company-detail-panel">
            <div className="company-detail-header">
                <div className="company-detail-identity">
                    <CompanyLogo name={company.name} size={44} />
                    <div>
                        <h3 className="company-detail-title">{company.name}</h3>
                        <p className="company-detail-sub">{company.location || 'Chưa rõ địa điểm'} · {company.job_count} tin tuyển dụng</p>
                    </div>
                </div>
                <button className="detail-close" onClick={onClose}>✕</button>
            </div>

            <div className="company-detail-stack">
                <p className="detail-section-label">Tech stack</p>
                <div className="skills-chips">
                    {company.tech_stack.map(t => (
                        <span key={t} className="skill-chip skill-chip--have">{t}</span>
                    ))}
                </div>
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

            <input
                className="company-search form-input"
                placeholder="Tìm công ty hoặc công nghệ (VD: React, AWS)..."
                value={search}
                onChange={e => setSearch(e.target.value)}
            />

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
                    <SimilarPanel key={selected.id} company={selected} onClose={() => setSelected(null)} />
                )}
            </div>
        </div>
    );
}
