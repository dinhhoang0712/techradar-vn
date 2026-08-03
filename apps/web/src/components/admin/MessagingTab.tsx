import {
    PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import type { TooltipContentProps } from 'recharts';
import type { ValueType, NameType } from 'recharts/types/component/DefaultTooltipContent';
import StatCard from './StatCard';
import { labelOf } from '../notifications/notifLabels';
import type { MessagingDashboard } from '../../types/admin';

const PIE_COLORS = ['var(--primary)', 'var(--green)', 'var(--yellow)', 'var(--danger-light)', 'var(--accent)'];

// recharts erases payload.color/fill for Pie tooltips (kept for 2.x parity), which leaves the
// default Tooltip's item text hard-coded to black — invisible against a dark background. A custom
// tooltip sidesteps that by reading the fill we stashed on the data item itself (payload.color).
function NotificationTooltip({ active, payload }: Partial<TooltipContentProps<ValueType, NameType>>) {
    if (!active || !payload?.length) return null;
    const entry = payload[0];
    const data = entry.payload as { color?: string } | undefined;
    return (
        <div className="chart-tooltip">
            <div className="tooltip-row">
                <span className="tooltip-dot" style={{ background: data?.color }} />
                <span className="tooltip-tech">{entry.name}</span>
                <span className="tooltip-jobs">{entry.value}</span>
            </div>
        </div>
    );
}

// Tab "Tin nhắn & Thông báo" của AdminDashboard: tổng hội thoại/tin nhắn + thông báo theo loại.
export default function MessagingTab({ messaging }: { messaging: MessagingDashboard }) {
    const notifChartData = (messaging.notifications_by_type ?? []).map((entry, idx) => ({
        ...entry,
        label: labelOf(entry.type),
        color: PIE_COLORS[idx % PIE_COLORS.length],
    }));

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
                    {notifChartData.length > 0 ? (
                        <ResponsiveContainer>
                            <PieChart>
                                <Pie
                                    data={notifChartData}
                                    dataKey="count"
                                    nameKey="label"
                                    cx="50%"
                                    cy="50%"
                                    innerRadius={70}
                                    outerRadius={110}
                                    paddingAngle={3}
                                >
                                    {notifChartData.map(entry => (
                                        <Cell key={entry.type} fill={entry.color} />
                                    ))}
                                </Pie>
                                <Tooltip content={<NotificationTooltip />} />
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
