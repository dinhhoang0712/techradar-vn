import { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppProvider } from './contexts/AppContext';
import { ToastProvider } from './components/common/ToastProvider';
import UserLayout from './layouts/UserLayout';
import AdminLayout from './layouts/AdminLayout';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import NotFoundPage from './pages/NotFoundPage';
import ForbiddenPage from './pages/ForbiddenPage';
import PageLoader from './components/common/PageLoader';
import './styles/global.css';

const AdminDashboard = lazy(() => import('./pages/admin/AdminDashboard'));
const AdminModeration = lazy(() => import('./pages/admin/AdminModeration'));
const AdminReports = lazy(() => import('./pages/admin/AdminReports'));
const AdminCMS = lazy(() => import('./pages/admin/AdminCMS'));
const AdminClusters = lazy(() => import('./pages/admin/AdminClusters'));
const AdminSettings = lazy(() => import('./pages/admin/AdminSettings'));
const AdminAutomation = lazy(() => import('./pages/admin/AdminAutomation'));
const AdminUsers = lazy(() => import('./pages/admin/AdminUsers'));
const TrendDashboard = lazy(() => import('./pages/TrendDashboard'));
const ComparePage = lazy(() => import('./pages/ComparePage'));
const GraphExplorer = lazy(() => import('./pages/GraphExplorer'));
const ChatbotPage = lazy(() => import('./pages/ChatbotPage'));
const ClusterDashboard = lazy(() => import('./pages/ClusterDashboard'));
const SalaryPage = lazy(() => import('./pages/SalaryPage'));
const CompanyExplorer = lazy(() => import('./pages/CompanyExplorer'));
const UserProfile = lazy(() => import('./pages/UserProfile'));
const CareerPage = lazy(() => import('./pages/CareerPage'));
const InterviewPage = lazy(() => import('./pages/InterviewPage'));
const ReportPage = lazy(() => import('./pages/ReportPage'));
const NotificationsPage = lazy(() => import('./pages/NotificationsPage'));
const FeedPage = lazy(() => import('./pages/FeedPage'));
const PublicProfilePage = lazy(() => import('./pages/PublicProfilePage'));
const MessagesPage = lazy(() => import('./pages/MessagesPage'));

export default function App() {
  return (
    <AppProvider>
      <ToastProvider>
      <BrowserRouter>
        <Suspense fallback={<PageLoader />}>
        <Routes>
          {/* Màn hình xác thực (Không có layout header) */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/403" element={<ForbiddenPage />} />

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
            <Route path="clusters" element={<AdminClusters />} />
            <Route path="automation" element={<AdminAutomation />} />
            <Route path="settings" element={<AdminSettings />} />
          </Route>

          {/* Catch-all: Hiển thị trang 404 cho các route không tồn tại */}
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
        </Suspense>
    </BrowserRouter>
    </ToastProvider>
    </AppProvider>
  );
}
