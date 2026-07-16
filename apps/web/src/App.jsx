import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppProvider } from './contexts/AppContext';
import { ToastProvider } from './components/common/ToastProvider';
import UserLayout from './layouts/UserLayout';
import AdminLayout from './layouts/AdminLayout';
import AdminDashboard from './pages/admin/AdminDashboard';
import AdminModeration from './pages/admin/AdminModeration';
import AdminReports from './pages/admin/AdminReports';
import AdminCMS from './pages/admin/AdminCMS';
import AdminSettings from './pages/admin/AdminSettings';
import AdminUsers from './pages/admin/AdminUsers';
import TrendDashboard from './pages/TrendDashboard';
import ComparePage from './pages/ComparePage';
import GraphExplorer from './pages/GraphExplorer';
import ChatbotPage from './pages/ChatbotPage';
import ClusterDashboard from './pages/ClusterDashboard';
import SalaryPage from './pages/SalaryPage';
import CompanyExplorer from './pages/CompanyExplorer';
import UserProfile from './pages/UserProfile';
import CareerPage from './pages/CareerPage';
import InterviewPage from './pages/InterviewPage';
import ReportPage from './pages/ReportPage';
import NotificationsPage from './pages/NotificationsPage';
import FeedPage from './pages/FeedPage';
import PublicProfilePage from './pages/PublicProfilePage';
import MessagesPage from './pages/MessagesPage';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import NotFoundPage from './pages/NotFoundPage';
import './styles/global.css';

export default function App() {
  return (
    <AppProvider>
      <ToastProvider>
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
          <Route path="/salary" element={<SalaryPage />} />
          <Route path="/companies" element={<CompanyExplorer />} />
          <Route path="/chat" element={<ChatbotPage />} />
          <Route path="/profile" element={<UserProfile />} />
          <Route path="/career" element={<CareerPage />} />
          <Route path="/interview" element={<InterviewPage />} />
          <Route path="/report" element={<ReportPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/feed" element={<FeedPage />} />
          <Route path="/users/:id" element={<PublicProfilePage />} />
          <Route path="/messages" element={<MessagesPage />} />
        </Route>

          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<Navigate to="dashboard" replace />} />
            <Route path="dashboard" element={<AdminDashboard />} />
            <Route path="moderation" element={<AdminModeration />} />
            <Route path="reports" element={<AdminReports />} />
            <Route path="users" element={<AdminUsers />} />
            <Route path="cms" element={<AdminCMS />} />
            <Route path="settings" element={<AdminSettings />} />
          </Route>

          {/* Catch-all: Hiển thị trang 404 cho các route không tồn tại */}
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
    </BrowserRouter>
    </ToastProvider>
    </AppProvider>
  );
}
