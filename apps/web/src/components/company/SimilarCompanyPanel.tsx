import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { getSimilarCompanies } from '../../api/companyService';
import CompanyLogo from '../common/CompanyLogo';
import RingGauge from '../common/RingGauge';
import TechRadarChart from './TechRadarChart';
import CompanyNeighborhoodGraph from './CompanyNeighborhoodGraph';
import CompanyMentions from './CompanyMentions';
import CompanyAiInsight from './CompanyAiInsight';
import CompanyTechHealthScore from './CompanyTechHealthScore';
import type { Company, SimilarCompany } from '../../types/company';

interface SimilarCompanyPanelProps {
    company: Company;
    onClose: () => void;
    onCompare: (company: Company) => void;
}

// Panel chi tiết 1 công ty ở CompanyExplorer: tech stack, bản đồ liên kết, tin tức, nhận định AI,
// và danh sách công ty có tech stack tương tự. CompanyExplorer remount panel này (qua `key`) mỗi
// khi đổi công ty được chọn.
export default function SimilarCompanyPanel({ company, onClose, onCompare }: SimilarCompanyPanelProps) {
    const [similar, setSimilar] = useState<SimilarCompany[] | null>(null);
    const [loading, setLoading] = useState(true);
    const [showGraph, setShowGraph] = useState(false);
    const navigate = useNavigate();

    useEffect(() => {
        let cancelled = false;
        getSimilarCompanies(company.id)
            .then(res => { if (!cancelled) setSimilar(res?.data ?? []); })
            .catch(() => { if (!cancelled) setSimilar([]); })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
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

            <CompanyTechHealthScore companyId={company.id} />

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
            ) : !similar || similar.length === 0 ? (
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
