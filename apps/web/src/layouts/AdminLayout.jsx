import { Outlet } from 'react-router-dom';
import { useState } from 'react';
import AdminSidebar from '../components/layout/AdminSidebar';
import './AdminLayout.css';

export default function AdminLayout() {
    const [collapsed, setCollapsed] = useState(false);

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
