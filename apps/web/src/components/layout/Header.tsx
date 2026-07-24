import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useState, useRef, useEffect } from 'react';
import { getUserProfile } from '../../api/userService';
import { performLogout } from '../../utils/authSession';
import { useOutsideClick } from '../../hooks/useOutsideClick';
import { useMessagingContext } from '../../contexts/messagingStore';
import NotificationBell from '../notifications/NotificationBell';
import Avatar from '../common/Avatar';
import './Header.css';

interface HeaderProfile {
    full_name: string;
    email: string;
    role: string;
    avatar_url: string;
}

const primaryNavItems = [
    { path: '/dashboard', label: 'Radar' },
    { path: '/feed', label: 'Bảng tin' },
    { path: '/companies', label: 'Công ty' },
    { path: '/salary', label: 'Lương' },
    { path: '/compare', label: 'So sánh' },
];

const toolsNavItems = [
    { path: '/graph', label: 'Đồ thị' },
    { path: '/clusters', label: 'Cụm CN' },
    { path: '/career', label: 'Career' },
    { path: '/interview', label: 'Phỏng vấn thử' },
    { path: '/report', label: 'Báo cáo' },
    { path: '/chat', label: 'AI Chat' },
];

export default function Header() {
    const [menuOpen, setMenuOpen] = useState(false);
    const [toolsOpen, setToolsOpen] = useState(false);
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
    const [profile, setProfile] = useState<HeaderProfile | null>(null);
    const menuRef = useRef<HTMLDivElement>(null);
    const toolsRef = useRef<HTMLDivElement>(null);
    const navigate = useNavigate();
    const location = useLocation();
    // Header luôn render bên trong <MessagingProvider> (xem App.jsx) nên context không bao giờ undefined ở đây.
    const { conversations } = useMessagingContext()!;

    const unreadMessages = conversations.reduce((sum, c) => sum + (c.unread_count || 0), 0);
    const isToolsActive = toolsNavItems.some((item) => location.pathname.startsWith(item.path));

    // Fetch profile khi component mount
    useEffect(() => {
        const token = localStorage.getItem('access_token');
        if (!token) return;

        getUserProfile()
            .then((res) => {
                const data = (res && 'data' in res ? res.data : res) ?? {};
                const flatData: HeaderProfile = {
                    full_name: data.user?.full_name || data.full_name || '',
                    email: data.user?.email || data.email || '',
                    role: data.user?.role || data.role || 'user',
                    avatar_url: data.profile?.avatar_url || data.avatar_url || '',
                };
                setProfile(flatData);
            })
            .catch((err) => {
                console.warn('[Header] Failed to load profile:', err);
            });
    }, []);

    // Đóng dropdown khi click ra ngoài
    useOutsideClick(menuRef, () => setMenuOpen(false));
    useOutsideClick(toolsRef, () => setToolsOpen(false));

    const handleLogout = () => performLogout(navigate);

    const displayName = profile?.full_name || 'Người dùng';
    const displayEmail = profile?.email || 'user@techradar.vn';

    return (
        <header className="site-header">
            <div className="header-inner">
                {/* Logo */}
                <NavLink to="/dashboard" className="header-logo">
                    <svg className="logo-mark" viewBox="0 0 64 64" aria-hidden="true">
                        <defs>
                            <linearGradient id="logoGrad" x1="0%" y1="100%" x2="100%" y2="0%">
                                <stop offset="0%" stopColor="#4f9dff" />
                                <stop offset="100%" stopColor="#9b8cff" />
                            </linearGradient>
                            <filter id="logoGlow" x="-60%" y="-60%" width="220%" height="220%">
                                <feGaussianBlur stdDeviation="1.3" result="blur" />
                                <feMerge>
                                    <feMergeNode in="blur" />
                                    <feMergeNode in="SourceGraphic" />
                                </feMerge>
                            </filter>
                        </defs>

                        {/* radar dial */}
                        <circle cx="30" cy="36" r="22" fill="none" stroke="#6b74a0" strokeWidth="3" opacity="0.5" />

                        {/* rotating sweep */}
                        <g className="logo-sweep">
                            <path d="M30,36 L26.18,14.33 A22,22 0 0,1 35.69,14.75 Z" fill="url(#logoGrad)" opacity="0.18" />
                            <line x1="30" y1="36" x2="35.69" y2="14.75" stroke="url(#logoGrad)" strokeWidth="2" strokeLinecap="round" opacity="0.9" />
                            <circle cx="35.69" cy="14.75" r="2" fill="#f4f7ff" />
                        </g>

                        {/* breakout trend line */}
                        <g filter="url(#logoGlow)">
                            <path d="M16,46 L30,36 L44,22 L54,10" fill="none" stroke="url(#logoGrad)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
                            <circle className="logo-ping-ring" cx="54" cy="10" r="5" fill="none" stroke="#bdb2ff" strokeWidth="1.4" />
                            <circle className="logo-ping-ring logo-ping-ring--delay" cx="54" cy="10" r="5" fill="none" stroke="#bdb2ff" strokeWidth="1.4" />
                            <circle cx="16" cy="46" r="2.8" fill="#4f9dff" />
                            <circle cx="30" cy="36" r="3.2" fill="#82c0ff" />
                            <circle cx="44" cy="22" r="3.8" fill="#bdb2ff" />
                            <circle cx="54" cy="10" r="9" fill="#9b8cff" opacity="0.32" />
                            <circle cx="54" cy="10" r="5" fill="#f4f7ff" />
                        </g>
                    </svg>
                    <span className="logo-text">Tech<span className="logo-accent">Radar</span></span>
                </NavLink>

                {/* Navbar */}
                <nav className={`header-nav ${mobileMenuOpen ? 'mobile-open' : ''}`} aria-label="Main navigation">
                    {primaryNavItems.map(({ path, label }) => (
                        <NavLink
                            key={path}
                            to={path}
                            onClick={() => setMobileMenuOpen(false)}
                            className={({ isActive }) =>
                                `nav-link${isActive ? ' nav-link--active' : ''}`
                            }
                        >
                            {label}
                        </NavLink>
                    ))}

                    <div className="nav-more" ref={toolsRef}>
                        <button
                            type="button"
                            className={`nav-link nav-more-trigger${toolsOpen ? ' open' : ''}${isToolsActive ? ' nav-link--active' : ''}`}
                            onClick={() => setToolsOpen((o) => !o)}
                            aria-expanded={toolsOpen}
                        >
                            Công cụ
                            <svg className="nav-more-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <polyline points="6 9 12 15 18 9"></polyline>
                            </svg>
                        </button>
                        <div className={`nav-more-menu${toolsOpen ? ' open' : ''}`}>
                            {toolsNavItems.map(({ path, label }) => (
                                <NavLink
                                    key={path}
                                    to={path}
                                    onClick={() => { setToolsOpen(false); setMobileMenuOpen(false); }}
                                    className={({ isActive }) =>
                                        `nav-more-item${isActive ? ' active' : ''}`
                                    }
                                >
                                    {label}
                                </NavLink>
                            ))}
                        </div>
                    </div>
                </nav>

                {/* Right actions */}
                <div className="header-actions">
                    {/* Mobile Menu Toggle - Moved here to be near user icon */}
                    <button
                        className="mobile-menu-btn show-mobile"
                        onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                        aria-label="Toggle menu"
                    >
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            {mobileMenuOpen ? (
                                <path d="M18 6L6 18M6 6l12 12"></path>
                            ) : (
                                <path d="M3 12h18M3 6h18M3 18h18"></path>
                            )}
                        </svg>
                    </button>
                    <button
                        type="button"
                        className="header-icon-btn"
                        title="Tin nhắn"
                        aria-label="Tin nhắn"
                        onClick={() => navigate('/messages')}
                    >
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8z"></path>
                        </svg>
                        {unreadMessages > 0 && (
                            <span className="header-icon-badge">{unreadMessages > 99 ? '99+' : unreadMessages}</span>
                        )}
                    </button>
                    <NotificationBell />
                    <div className="avatar-wrap" ref={menuRef}>
                        <button
                            type="button"
                            className={`header-avatar${menuOpen ? ' active' : ''}`}
                            title="Tài khoản"
                            onClick={() => setMenuOpen(o => !o)}
                        >
                            <Avatar user={profile} size={36} />
                        </button>
                        {menuOpen && (
                            <div className="avatar-dropdown">
                                <div className="dropdown-header">
                                    <div className="dropdown-avatar">
                                        <Avatar user={profile} size={40} />
                                    </div>
                                    <div>
                                        <div className="dropdown-name">{displayName}</div>
                                        <div className="dropdown-email">{displayEmail}</div>
                                    </div>
                                </div>
                                <div className="dropdown-divider" />
                                {profile?.role === 'admin' && (
                                    <button
                                        className="dropdown-item"
                                        onClick={() => { setMenuOpen(false); navigate('/admin'); }}
                                    >
                                        <span>Trang quản trị</span>
                                    </button>
                                )}
                                <button
                                    className="dropdown-item"
                                    onClick={() => { setMenuOpen(false); navigate('/profile'); }}
                                >
                                    <span>Thông tin cá nhân</span>
                                </button>
                                <div className="dropdown-divider" />
                                <button className="dropdown-item danger" onClick={handleLogout}>
                                    <span>Đăng xuất</span>
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </header>
    );
}
