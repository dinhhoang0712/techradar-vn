import { Outlet, Navigate } from 'react-router-dom';
import { useState, useEffect } from 'react';
import AdminSidebar from '../components/layout/AdminSidebar';
import { getCurrentUser } from '../api/authService';
import './AdminLayout.css';

export default function AdminLayout() {
    const [collapsed, setCollapsed] = useState(false);
    // 'checking' | 'allowed' | 'unauthenticated' | 'forbidden'
    const [authStatus, setAuthStatus] = useState(() =>
        localStorage.getItem('access_token') ? 'checking' : 'unauthenticated');

    useEffect(() => {
        if (authStatus !== 'checking') return;
        getCurrentUser()
            .then((user) => setAuthStatus(user?.role === 'admin' ? 'allowed' : 'forbidden'))
            .catch(() => setAuthStatus('forbidden'));
    }, [authStatus]);

    if (authStatus === 'checking') {
        return (
            <div className="flex-center" style={{ minHeight: '100vh' }}>
                <div className="loading-spinner" />
            </div>
        );
    }
    if (authStatus === 'unauthenticated') return <Navigate to="/login" replace />;
    if (authStatus === 'forbidden') return <Navigate to="/403" replace />;

    return (
        <div className="admin-layout">
            <AdminSidebar 
                collapsed={collapsed} 
                onToggle={() => setCollapsed(!collapsed)} 
            />
            <div className={`admin-main ${collapsed ? 'expanded' : ''}`}>
                <header className="admin-mobile-header show-mobile">
                    <button className="mobile-menu-btn" onClick={() => document.querySelector('.sidebar').classList.toggle('mobile-open')}>
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M3 12h18M3 6h18M3 18h18"></path>
                        </svg>
                    </button>
                    <span className="logo-text">Admin <span className="logo-accent">Panel</span></span>
                </header>
                <main className="admin-content">
                    <Outlet />
                </main>
            </div>
        </div>
    );
}
