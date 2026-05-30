import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppProvider } from './contexts/AppContext';
import UserLayout from './layouts/UserLayout';
import AdminLayout from './layouts/AdminLayout';
import AdminDashboard from './pages/admin/AdminDashboard';
import AdminCMS from './pages/admin/AdminCMS';
import AdminSettings from './pages/admin/AdminSettings';
import AdminUsers from './pages/admin/AdminUsers';
import TrendDashboard from './pages/TrendDashboard';
import ComparePage from './pages/ComparePage';
import GraphExplorer from './pages/GraphExplorer';
import ChatbotPage from './pages/ChatbotPage';
import ClusterDashboard from './pages/ClusterDashboard';
import UserProfile from './pages/UserProfile';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import './styles/global.css';

export default function App() {
  return (
    <AppProvider>
      <BrowserRouter>
        <Routes>
          {/* Màn hình xác thực (Không có layout header) */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* Module dành cho Người dùng */}
        <Route element={<UserLayout />}>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<TrendDashboard />} />
          <Route path="/compare" element={<ComparePage />} />
          <Route path="/graph" element={<GraphExplorer />} />
          <Route path="/clusters" element={<ClusterDashboard />} />
          <Route path="/chat" element={<ChatbotPage />} />
          <Route path="/profile" element={<UserProfile />} />
        </Route>

          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<Navigate to="dashboard" replace />} />
            <Route path="dashboard" element={<AdminDashboard />} />
            <Route path="users" element={<AdminUsers />} />
            <Route path="cms" element={<AdminCMS />} />
            <Route path="settings" element={<AdminSettings />} />
          </Route>

          {/* Catch-all: Điều hướng các route không tồn tại về trang chủ */}
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
    </BrowserRouter>
    </AppProvider>
  );
}
