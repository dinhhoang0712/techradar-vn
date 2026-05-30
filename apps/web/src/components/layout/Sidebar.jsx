import { NavLink } from 'react-router-dom';
import './Sidebar.css';

const NAV_ITEMS = [
    { to: '/dashboard', icon: '📊', label: 'Dashboard' },
    { to: '/compare', icon: '⚖️', label: 'So sánh' },
    { to: '/graph', icon: '🕸️', label: 'Graph Explorer' },
    { to: '/chat', icon: '🤖', label: 'AI Chatbot' },
];

export default function Sidebar({ collapsed, onToggle }) {
    return (
        <aside className={`sidebar${collapsed ? ' collapsed' : ''}`}>
            <div className="sidebar-header">
                {!collapsed && (
                    <div className="sidebar-brand">
                        <span className="brand-icon">🚀</span>
                        <span className="brand-name">Tech Radar</span>
                    </div>
                )}
                <button className="collapse-btn" onClick={onToggle} title="Toggle sidebar">
                    {collapsed ? '›' : '‹'}
                </button>
            </div>

            <nav className="sidebar-nav">
                {NAV_ITEMS.map(item => (
                    <NavLink
                        key={item.to}
                        to={item.to}
                        className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
                        title={collapsed ? item.label : ''}
                    >
                        <span className="nav-icon">{item.icon}</span>
                        {!collapsed && <span className="nav-label">{item.label}</span>}
                    </NavLink>
                ))}
            </nav>

            {!collapsed && (
                <div className="sidebar-footer">
                    <div className="sidebar-badge">
                        <span className="dot live" />
                        <span>Data: Tháng 3/2026</span>
                    </div>
                </div>
            )}
        </aside>
    );
}
