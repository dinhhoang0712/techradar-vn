import {
    PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import StatCard from './StatCard';
import type { MessagingDashboard } from '../../types/admin';

const PIE_COLORS = ['var(--primary)', 'var(--green)', 'var(--yellow)', 'var(--danger-light)', 'var(--accent)'];

// Tab "Tin nhắn & Thông báo" của AdminDashboard: tổng hội thoại/tin nhắn + thông báo theo loại.
export default function MessagingTab({ messaging }: { messaging: MessagingDashboard }) {
    return (
        <>
            <div className="stat-cards">
                <StatCard icon="📨" label="Tổng cuộc trò chuyện" value={messaging.total_conversations} accent="primary" />
                <StatCard icon="✉️" label="Tổng tin nhắn" value={messaging.total_messages} accent="primary" />
                <StatCard icon="🆕" label="Tin nhắn hôm nay" value={messaging.messages_today} accent="accent" />
            </div>

            <div className="chart-card">
                <h3>Thông báo theo loại</h3>
                <div style={{ width: '100%', height: 340 }}>
                    {messaging.notifications_by_type && messaging.notifications_by_type.length > 0 ? (
                        <ResponsiveContainer>
                            <PieChart>
                                <Pie
                                    data={messaging.notifications_by_type}
                                    dataKey="count"
                                    nameKey="type"
                                    cx="50%"
                                    cy="50%"
                                    innerRadius={70}
                                    outerRadius={110}
                                    paddingAngle={3}
                                >
                                    {messaging.notifications_by_type.map((entry, idx) => (
                                        <Cell key={entry.type} fill={PIE_COLORS[idx % PIE_COLORS.length]} />
                                    ))}
                                </Pie>
                                <Tooltip contentStyle={{ backgroundColor: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text)' }} />
                                <Legend />
                            </PieChart>
                        </ResponsiveContainer>
                    ) : (
                        <div className="flex-center chart-empty" style={{ height: '100%' }}>Chưa có dữ liệu</div>
                    )}
                </div>
            </div>
        </>
    );
}
