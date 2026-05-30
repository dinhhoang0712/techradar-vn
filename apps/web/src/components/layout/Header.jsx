import { NavLink, useNavigate } from 'react-router-dom';
import { useState, useRef, useEffect } from 'react';
import { logoutUser } from '../../api/authService';
import { getUserProfile } from '../../api/userService';
import './Header.css';

const navItems = [
    { path: '/dashboard', label: 'Radar' },
    { path: '/compare', label: 'So sánh' },
    { path: '/graph', label: 'Đồ thị' },
    { path: '/clusters', label: 'Cụm Công Nghệ' },
    { path: '/chat', label: 'AI Chat' },
];

export default function Header() {
    const [menuOpen, setMenuOpen] = useState(false);
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
    const [profile, setProfile] = useState(null);
    const menuRef = useRef();
    const navigate = useNavigate();

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
        }
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, []);

    const handleLogout = async () => {
        try {
            await logoutUser();
        } catch (_) {
            // Bỏ qua lỗi server, vẫn logout phía client
        } finally {
            localStorage.removeItem('access_token');
            localStorage.removeItem('refresh_token');
            navigate('/login');
        }
    };

    // Lấy chữ cái đầu để hiển thị avatar
    const avatarLetter = profile?.full_name
        ? profile.full_name.charAt(0).toUpperCase()
        : profile?.email
        ? profile.email.charAt(0).toUpperCase()
        : 'U';

    const displayName = profile?.full_name || 'Người dùng';
    const displayEmail = profile?.email || 'user@techradar.vn';

    return (
        <header className="site-header">
            <div className="header-inner">
                {/* Logo */}
                <div className="header-logo">
                    <span className="logo-text">Tech<span className="logo-accent">Radar</span></span>
                </div>

                {/* Navbar */}
                <nav className={`header-nav ${mobileMenuOpen ? 'mobile-open' : ''}`} aria-label="Main navigation">
                    {navItems.map(({ path, label }) => (
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
                    <div className="avatar-wrap" ref={menuRef}>
                        <div
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
                        </div>
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
                                <button className="dropdown-item">
                                    <span>Cài đặt</span>
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
