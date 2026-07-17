import { useEffect, useState } from 'react';
import { getPublicStats } from '../../api/statsService';

// Companies is capped by the backend's underlying query (currently 500) — a count at
// that cap means the real number is at least that high, so show it as "500+".
const COMPANIES_CAP = 500;

function formatCount(n) {
    return typeof n === 'number' ? n.toLocaleString('vi-VN') : null;
}

export default function AuthStatsRow() {
    const [stats, setStats] = useState(null);

    useEffect(() => {
        let cancelled = false;
        getPublicStats()
            .then((res) => {
                const data = res?.data ?? res;
                if (!cancelled && data) setStats(data);
            })
            .catch(() => { /* decorative only — fail silently, just don't render the row */ });
        return () => { cancelled = true; };
    }, []);

    if (!stats) return null;

    return (
        <div className="auth-stats-row">
            <span className="auth-stat-chip">
                {formatCount(stats.companies)}{stats.companies >= COMPANIES_CAP ? '+' : ''} công ty
            </span>
            <span className="auth-stat-chip">{formatCount(stats.jobs)} tin tuyển dụng</span>
            <span className="auth-stat-chip">{formatCount(stats.users)} thành viên</span>
        </div>
    );
}
