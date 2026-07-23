import { useState, useEffect } from 'react';
import { getCompanyMentions } from '../../api/companyService';
import type { CompanyMention } from '../../types/company';

// Tin tức gần nhất có nhắc đến công ty — dùng trong SimilarCompanyPanel.
export default function CompanyMentions({ companyId }: { companyId: string }) {
    const [mentions, setMentions] = useState<CompanyMention[] | null>(null);
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
