import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useState, useRef, useEffect } from 'react';
import { logoutUser } from '../../api/authService';
import { getUserProfile } from '../../api/userService';
import { useMessagingContext } from '../../contexts/messagingStore';
import NotificationBell from '../notifications/NotificationBell';
import './Header.css';

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
    const [profile, setProfile] = useState(null);
    const menuRef = useRef();
    const toolsRef = useRef();
    const navigate = useNavigate();
    const location = useLocation();
    const { conversations } = useMessagingContext();

    const unreadMessages = conversations.reduce((sum, c) => sum + (c.unread_count || 0), 0);
    const isToolsActive = toolsNavItems.some((item) => location.pathname.startsWith(item.path));

    // Fetch profile khi component mount
    useEffect(() => {
        const token = localStorage.getItem('access_token');
        if (!token) return;

        getUserProfile()
            .then((res) => {
                const data = res?.data ?? res ?? {};
                const flatData = {
                    full_name: data.user?.full_name || data.full_name || '',
                    email: data.user?.email || data.email || '',
                };
                setProfile(flatData);
            })
            .catch((err) => {
                console.warn('[Header] Failed to load profile:', err);
            });
    }, []);

    // Đóng dropdown khi click ra ngoài
    useEffect(() => {
        function handleClick(e) {
            if (menuRef.current && !menuRef.current.contains(e.target)) {
                setMenuOpen(false);
            }
            if (toolsRef.current && !toolsRef.current.contains(e.target)) {
                setToolsOpen(false);
            }
        }
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, []);

    const handleLogout = async () => {
        try {
            await logoutUser();
        } catch {
            // Bỏ qua lỗi server, vẫn logout phía client
        } finally {
            localStorage.removeItem('access_token');
            localStorage.removeItem('refresh_token');
            navigate('/login');
        }
    };

    const displayName = profile?.full_name || 'Người dùng';
    const displayEmail = profile?.email || 'user@techradar.vn';

    return (
        <header className="site-header">
            <div className="header-inner">
                {/* Logo */}
                <NavLink to="/dashboard" className="header-logo">
                    <span className="logo-ping" aria-hidden="true">
                        <span className="ping-ring"></span>
                        <span className="ping-dot"></span>
                    </span>
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
                            <div className="avatar-icon">
                                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                                    <circle cx="12" cy="7" r="4"></circle>
                                </svg>
                            </div>
                        </button>
                        {menuOpen && (
                            <div className="avatar-dropdown">
                                <div className="dropdown-header">
                                    <div className="dropdown-avatar">
                                        <div className="avatar-icon-large">
                                            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                                                <circle cx="12" cy="7" r="4"></circle>
                                            </svg>
                                        </div>
                                    </div>
                                    <div>
                                        <div className="dropdown-name">{displayName}</div>
                                        <div className="dropdown-email">{displayEmail}</div>
                                    </div>
                                </div>
                                <div className="dropdown-divider" />
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
