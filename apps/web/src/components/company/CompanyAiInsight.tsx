import { useState } from 'react';
import { getCompanyInsight } from '../../api/companyService';
import type { CompanyInsight } from '../../types/company';

// Nhận định AI về công ty — chỉ gọi API khi người dùng bấm xem (tránh tốn LLM call cho mọi công ty).
export default function CompanyAiInsight({ company }: { company: { name: string } }) {
    const [requested, setRequested] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [insight, setInsight] = useState<CompanyInsight | null>(null);

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
                    {insight.highlights && insight.highlights.length > 0 && (
                        <ul className="company-ai-highlights">
                            {insight.highlights.map((h, i) => <li key={i}>{h}</li>)}
                        </ul>
                    )}
                </>
            )}
        </div>
    );
}
