import { useNavigate } from 'react-router-dom';
import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';
import StatCard from './StatCard';
import type { SocialDashboard } from '../../types/admin';

// Tab "Cộng đồng" của AdminDashboard: tổng bài viết/bình luận/lượt thích/follow + báo cáo chờ
// duyệt + top người đăng bài.
export default function SocialTab({ social }: { social: SocialDashboard }) {
    const navigate = useNavigate();
    const pendingReports = social.pending_reports ?? 0;

    return (
        <>
            <div className="stat-cards">
                <StatCard icon="📝" label="Tổng bài viết" value={social.total_posts} accent="primary" />
                <StatCard icon="🆕" label="Bài viết hôm nay" value={social.posts_today} accent="accent" />
                <StatCard icon="💬" label="Tổng bình luận" value={social.total_comments} accent="primary" />
                <StatCard icon="❤️" label="Tổng lượt thích" value={social.total_likes} accent="danger" />
                <StatCard icon="👥" label="Tổng lượt follow" value={social.total_follows} accent="accent" />
                <StatCard
                    icon="🚩"
                    label="Báo cáo chờ duyệt"
                    value={pendingReports}
                    accent={pendingReports > 0 ? 'yellow' : 'green'}
                    caption={pendingReports > 0 ? 'Nhấn để xem và xử lý →' : 'Không có báo cáo nào chờ duyệt'}
                    onClick={() => navigate('/admin/reports')}
                />
            </div>

            <div className="chart-card">
                <h3>Top người dùng hoạt động nhiều nhất</h3>
                <div style={{ width: '100%', height: 360 }}>
                    {social.top_posters && social.top_posters.length > 0 ? (
                        <ResponsiveContainer>
                            <BarChart data={social.top_posters} layout="vertical" margin={{ top: 10, right: 30, left: 20, bottom: 0 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                                <XAxis type="number" stroke="var(--text-3)" allowDecimals={false} />
                                <YAxis type="category" dataKey="full_name" stroke="var(--text-3)" width={140} />
                                <Tooltip contentStyle={{ backgroundColor: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text)' }} />
                                <Bar dataKey="post_count" name="Số bài viết" fill="var(--primary)" radius={[0, 6, 6, 0]} />
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
