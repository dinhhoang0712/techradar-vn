import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';
import StatCard from './StatCard';
import type { JobMarketDashboard } from '../../types/admin';

// Tab "Việc làm & Công nghệ" của AdminDashboard: số job đã index/cảnh báo đã gửi + top công nghệ.
export default function JobsTab({ jobs }: { jobs: JobMarketDashboard }) {
    return (
        <>
            <div className="stat-cards">
                <StatCard icon="💼" label="Job đã được index" value={jobs.total_jobs_indexed} accent="primary" />
                <StatCard icon="🔔" label="Cảnh báo việc làm phù hợp đã gửi" value={jobs.job_match_alerts_sent} accent="green" />
            </div>

            <div className="chart-card">
                <h3>Top công nghệ được yêu cầu nhiều nhất</h3>
                <div style={{ width: '100%', height: 380 }}>
                    {jobs.top_technologies && jobs.top_technologies.length > 0 ? (
                        <ResponsiveContainer>
                            <BarChart data={jobs.top_technologies} margin={{ top: 10, right: 20, left: 0, bottom: 10 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                                <XAxis dataKey="name" stroke="var(--text-3)" interval={0} angle={-30} textAnchor="end" height={70} />
                                <YAxis stroke="var(--text-3)" allowDecimals={false} />
                                <Tooltip contentStyle={{ backgroundColor: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text)' }} />
                                <Bar dataKey="job_count" name="Số lượng job yêu cầu" fill="var(--accent)" radius={[6, 6, 0, 0]} />
                            </BarChart>
                        </ResponsiveContainer>
                    ) : (
                        <div className="flex-center chart-empty" style={{ height: '100%' }}>Chưa có dữ liệu</div>
                    )}
                </div>
            </div>
        </>
    );
}
