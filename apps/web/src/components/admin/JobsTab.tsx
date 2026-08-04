import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';
import StatCard from './StatCard';
import type { JobMarketDashboard } from '../../types/admin';
import { EXPERIENCE_LEVELS } from '../../constants/experienceLevels';

// Sắp theo thứ tự cấp bậc tự nhiên (Intern → Lead) thay vì theo số lượng — dễ đọc phân bố hơn
// vì người xem mặc định kỳ vọng thứ tự tiến trình nghề nghiệp, không phải sort theo magnitude.
function sortByExperienceLevel(jobsByLevel: JobMarketDashboard['jobs_by_level']) {
    if (!jobsByLevel) return [];
    const order = new Map(EXPERIENCE_LEVELS.map((l, i) => [l, i]));
    return [...jobsByLevel].sort((a, b) => (order.get(a.level) ?? 99) - (order.get(b.level) ?? 99));
}

// Tab "Việc làm & Công nghệ" của AdminDashboard: số job đã index/cảnh báo đã gửi + top công nghệ +
// phân bố job theo cấp độ kinh nghiệm + tỷ lệ user đã khai cấp độ hiện tại.
export default function JobsTab({ jobs }: { jobs: JobMarketDashboard }) {
    const levelData = sortByExperienceLevel(jobs.jobs_by_level);
    const completionPercent = jobs.total_users > 0
        ? Math.round((jobs.users_with_current_level / jobs.total_users) * 100)
        : 0;

    return (
        <>
            <div className="stat-cards">
                <StatCard icon="💼" label="Job đã được index" value={jobs.total_jobs_indexed} accent="primary" />
                <StatCard icon="🔔" label="Cảnh báo việc làm phù hợp đã gửi" value={jobs.job_match_alerts_sent} accent="green" />
                <StatCard
                    icon="🎯"
                    label="User đã khai cấp độ hiện tại"
                    value={`${jobs.users_with_current_level}/${jobs.total_users} (${completionPercent}%)`}
                    accent="accent"
                    valueSize="md"
                />
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

            <div className="chart-card">
                <h3>Phân bố job theo cấp độ kinh nghiệm</h3>
                <div style={{ width: '100%', height: 320 }}>
                    {levelData.length > 0 ? (
                        <ResponsiveContainer>
                            <BarChart data={levelData} margin={{ top: 10, right: 20, left: 0, bottom: 10 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                                <XAxis dataKey="level" stroke="var(--text-3)" />
                                <YAxis stroke="var(--text-3)" allowDecimals={false} />
                                <Tooltip contentStyle={{ backgroundColor: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text)' }} />
                                <Bar dataKey="job_count" name="Số lượng job" fill="var(--accent)" radius={[6, 6, 0, 0]} />
                            </BarChart>
                        </ResponsiveContainer>
                    ) : (
                        <div className="flex-center chart-empty" style={{ height: '100%' }}>
                            Chưa có job nào được phân loại cấp độ
                        </div>
                    )}
                </div>
            </div>
        </>
    );
}
