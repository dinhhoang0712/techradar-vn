# Frontend Development Guide — TechRadar VN

> Tài liệu chi tiết về kiến trúc, phát triển và best practices cho React frontend.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Tech Stack](#2-tech-stack)
3. [Cấu trúc dự án](#3-cấu-trúc-dự án)
4. [Component Architecture](#4-component-architecture)
5. [State Management](#5-state-management)
6. [Routing](#6-routing)
7. [API Integration](#7-api-integration)
8. [Styling](#8-styling)
9. [Testing](#9-testing)
10. [Build & Deployment](#10-build--deployment)
11. [Best Practices](#11-best-practices)

---

## 1. Tổng quan

Frontend TechRadar VN là Single Page Application (SPA) được xây dựng với:

- **React 19** với các tính năng modern (concurrent rendering, automatic batching)
- **TypeScript** (strict, `allowJs: false`) — toàn bộ `src/` là `.ts`/`.tsx`, không còn file JS nào
- **Vite 7** cho fast development và optimized builds
- **React Router DOM 7** cho client-side routing
- **Recharts 3** cho data visualization
- **react-force-graph-2d** (dùng d3-force nội bộ) cho graph visualization — không có dependency
  `d3` trực tiếp

### Mục tiêu thiết kế

- **Performance**: Fast initial load, smooth interactions
- **User Experience**: Intuitive UI, responsive design
- **Maintainability**: Clean component structure, reusable patterns
- **Accessibility**: WCAG compliant, keyboard navigation support

---

## 2. Tech Stack

### 2.1 Core Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| React | 19.2.0 | UI framework |
| React DOM | 19.2.0 | React DOM renderer |
| Vite | 7.3.1 | Build tool & dev server |
| React Router DOM | 7.13.1 | Client-side routing |
| Recharts | 3.7.0 | Chart library |
| react-force-graph-2d | 1.29.1 | Force-directed graph (dùng d3-force nội bộ) |
| react-select | 5.10.2 | Select component |
| html2canvas | 1.4.1 | Screenshot export |

### 2.2 Development Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| @vitejs/plugin-react | 5.1.1 | Vite React plugin |
| TypeScript | 6.0.3 | **Bắt buộc** — `strict: true`, `allowJs: false` (không phải "optional") |
| ESLint | 9.39.1 | Linting |
| Vitest | 4.1.9 | Testing framework |
| Testing Library | 16.x | Component testing |

---

## 3. Cấu trúc dự án

```
apps/web/src/
├── main.tsx                      # Application entry point
├── index.css                     # Global styles
├── App.tsx                       # Root component + route table
├── App.css                       # App styles
│
├── api/                          # API client layer — flat named exports per file, KHÔNG phải
│   │                              namespace object (vd. authService.ts exports loginUser/
│   │                              registerUser/refreshToken/getCurrentUser trực tiếp)
│   ├── authService.ts, userService.ts, adminService.ts, statsService.ts
│   ├── trendService.ts           # KHÔNG phải "radarService" — getRadarTop4/getRadarTop10/
│   │                              getRadarSearch/streamRadar (SSE)
│   ├── compareService.ts, graphService.ts, chatService.ts, clusterService.ts
│   ├── careerService.ts, reportService.ts, summarizeService.ts, salaryService.ts
│   ├── forecastService.ts, recommendService.ts, notificationService.ts
│   ├── companyService.ts, jobService.ts
│   ├── messagingService.ts       # direct messages (incl. manual SSE fetch/parse)
│   ├── socialService.ts          # feed/posts/follow/comments
│   ├── interviewService.ts, agentService.ts
├── utils/
│   └── apiClient.ts              # HTTP client — MỘT function `apiClient()`, không phải class
│                                  # `ApiClient`; token lưu localStorage key access_token/
│                                  # refresh_token (snake_case, không phải accessToken/camelCase)
│
├── components/                   # Reusable components
│   ├── layout/
│   │   ├── Header.tsx
│   │   ├── AdminSidebar.tsx
│   │   └── Footer.tsx
│   ├── notifications/
│   │   └── NotificationBell.tsx
│   ├── social/
│   │   └── PostCard.tsx          # shared like/comment/delete card (Feed + PublicProfile)
│   └── common/
│       ├── Avatar.tsx, Modal.tsx
│       └── toastContext.tsx
│
├── contexts/                     # React contexts — appContextStore.tsx KHÔNG chứa auth
│   │                              (login/logout/token) — chỉ giữ maintenance/feature-flag state
│   │                              (isWebMaintenance/isChatEnabled/isGraphEnabled từ GET /status)
│   ├── appContextStore.tsx
│   └── messagingStore.tsx        # app-wide SSE connection
│
├── pages/                        # Page components
│   ├── auth/
│   │   ├── LoginPage.tsx         # gồm cả forgot/reset-password (modal), không có trang riêng
│   │   └── RegisterPage.tsx
│   ├── TrendDashboard.tsx        # Tech radar dashboard
│   ├── GraphExplorer.tsx         # Explore / Road Analysis / Browse Filters / Graph Analytics
│   ├── ChatbotPage.tsx           # RAG chat + Agent mode toggle
│   ├── ClusterDashboard.tsx      # Clustering visualization
│   ├── ComparePage.tsx           # Technology comparison
│   ├── SalaryPage.tsx            # Salary analytics
│   ├── CompanyExplorer.tsx       # company directory + similar-company panel
│   ├── CareerPage.tsx            # Career path assistant + job-match card
│   ├── InterviewPage.tsx         # AI mock interview (turn-based, /interview)
│   ├── ReportPage.tsx            # Trend reports
│   ├── FeedPage.tsx              # social feed
│   ├── MessagesPage.tsx          # direct messaging (SSE)
│   ├── NotificationsPage.tsx
│   ├── PublicProfilePage.tsx     # /users/:id (follow + message entry point)
│   ├── UserProfile.tsx           # Own profile
│   ├── MaintenancePage.tsx       # Maintenance mode
│   ├── ForbiddenPage.tsx         # 403
│   ├── NotFoundPage.tsx          # 404 — route "*" trỏ vào đây, KHÔNG redirect về /dashboard
│   └── admin/                    # Admin pages (AdminLayout)
│       ├── AdminDashboard.tsx
│       ├── AdminAutomation.tsx   # manual job triggers
│       ├── AdminModeration.tsx, AdminReports.tsx, AdminClusters.tsx
│       ├── AdminUsers.tsx, AdminCMS.tsx, AdminSettings.tsx
│
├── layouts/                      # Page layouts
│   ├── UserLayout.tsx            # wraps Header/Footer + <MessagingProvider> for all user pages
│   └── AdminLayout.tsx           # Admin layout
│
├── hooks/                        # Custom hooks
├── types/                        # Shared TS types
│
└── utils/                        # Utility functions (formatters, validators, apiClient — xem trên)
```

> **Auth gating:** không có route-level guard (`<PrivateRoute>`) trong cây route — mọi trang dưới
> `UserLayout` render kể cả chưa đăng nhập. "Yêu cầu đăng nhập" chỉ được enforce ở **tầng data**:
> `apiClient` đính kèm `Authorization: Bearer <token>` nếu có trong `localStorage`; nếu backend
> trả 401, client thử refresh 1 lần rồi mới điều hướng về `/login`.

---

## 4. Component Architecture

### 4.1 Component Hierarchy

```
App
└── Layout (MainLayout)
    ├── Header
    │   ├── Logo
    │   ├── Navigation
    │   ├── NotificationBell
    │   └── UserMenu
    ├── Sidebar
    │   └── NavigationLinks
    ├── Main Content
    │   └── Page Components
    └── Footer
```

### 4.2 Component Types

**Layout Components:**
- Wrappers that provide structure to pages
- Handle navigation, headers, footers
- No business logic

**Page Components:**
- Route-level components
- Contain business logic for specific features
- Compose smaller components

**UI Components:**
- Reusable, presentational components
- Receive data via props
- Emit events via callbacks

**Container Components:**
- Connect to API layer
- Manage state
- Pass data to presentational components

### 4.3 Component Example

```jsx
// Presentational Component
const TechCard = ({ tech, onClick }) => {
  return (
    <div className="tech-card" onClick={() => onClick(tech)}>
      <h3>{tech.name}</h3>
      <p>{tech.category}</p>
      <div className="metrics">
        <span>Growth: {tech.growthRate}%</span>
        <span>Jobs: {tech.jobCount}</span>
      </div>
    </div>
  );
};

// Container Component
const TrendDashboard = () => {
  const [technologies, setTechnologies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchTopTechnologies()
      .then(data => {
        setTechnologies(data);
        setLoading(false);
      })
      .catch(err => {
        setError(err.message);
        setLoading(false);
      });
  }, []);

  const handleTechClick = (tech) => {
    // Navigate to tech detail or open comparison
  };

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error} />;

  return (
    <div className="trend-dashboard">
      <h2>Trending Technologies</h2>
      <div className="tech-grid">
        {technologies.map(tech => (
          <TechCard 
            key={tech.name} 
            tech={tech} 
            onClick={handleTechClick} 
          />
        ))}
      </div>
    </div>
  );
};
```

---

## 5. State Management

### 5.1 Auth state — không có `AuthContext`/`useAuth`

**Không có Context nào cho auth.** `AppContext` (`contexts/appContextStore.ts` +
`contexts/AppContext.tsx`) chỉ giữ **maintenance/feature-flag state**
(`isWebMaintenance`, `isAppMaintenance`, `isChatEnabled`, `isGraphEnabled`), fetch từ `GET
/status` mỗi 30s khi tab visible — hoàn toàn không liên quan login/logout/user/token:

```ts
// contexts/appContextStore.ts
export interface AppSettings {
    isWebMaintenance: boolean;
    isAppMaintenance: boolean;
    isChatEnabled: boolean;
    isGraphEnabled: boolean;
}

export interface AppContextValue {
    settings: AppSettings;
    updateSettings: (updates: Partial<AppSettings>) => void;
}

export const AppContext = createContext<AppContextValue | undefined>(undefined);
export const useAppContext = (): AppContextValue | undefined => useContext(AppContext);
```

Auth token đọc trực tiếp từ `localStorage` (`access_token`/`refresh_token`, snake_case —
KHÔNG phải `accessToken`/`refreshToken`) bởi `utils/apiClient.ts` cho mỗi request, và bởi từng
component cần biết trạng thái đăng nhập (vd. `Header.tsx` đọc
`localStorage.getItem('access_token')` trực tiếp). Cần thông tin user hiện tại thì gọi
`getCurrentUser()` (`api/authService.ts`) tại chỗ, không có global "current user" state được
share qua Context. `LoginPage.tsx` tự quản lý toàn bộ luồng login/register/forgot/reset qua
`api/authService.ts` (`loginUser`, `registerUser`, `forgotPassword`, `resetPassword`) và ghi
token vào `localStorage` sau khi thành công.

### 5.2 Local State

```jsx
// Using useState for local component state
const ChatbotPage = () => {
  const [messages, setMessages] = useState([]);
  const [inputValue, setInputValue] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [sessionId, setSessionId] = useState(null);

  const handleSendMessage = async () => {
    if (!inputValue.trim()) return;

    const userMessage = { role: 'user', content: inputValue };
    setMessages(prev => [...prev, userMessage]);
    setInputValue('');
    setIsStreaming(true);

    try {
      const response = await api.chat.sendMessage(sessionId, userMessage.content);
      setSessionId(response.session_id);
      
      const assistantMessage = { 
        role: 'assistant', 
        content: response.answer,
        sources: response.sources 
      };
      setMessages(prev => [...prev, assistantMessage]);
    } catch (error) {
      const errorMessage = { 
        role: 'assistant', 
        content: 'Sorry, something went wrong.' 
      };
      setMessages(prev => [...prev, errorMessage]);
    } finally {
      setIsStreaming(false);
    }
  };

  return (
    <div className="chatbot-page">
      <MessageList messages={messages} />
      <MessageInput 
        value={inputValue}
        onChange={setInputValue}
        onSend={handleSendMessage}
        disabled={isStreaming}
      />
    </div>
  );
};
```

### 5.3 Derived State

```jsx
// Use useMemo for expensive computations
const ComparePage = () => {
  const [tech1, setTech1] = useState(null);
  const [tech2, setTech2] = useState(null);

  // Derived state - compute comparison score
  const comparisonScore = useMemo(() => {
    if (!tech1 || !tech2) return null;
    
    const growthDiff = Math.abs(tech1.growthRate - tech2.growthRate);
    const jobDiff = Math.abs(tech1.jobCount - tech2.jobCount);
    const articleDiff = Math.abs(tech1.articleCount - tech2.articleCount);
    
    // Normalize and compute score
    const normalizedGrowth = growthDiff / 100;
    const normalizedJobs = jobDiff / Math.max(tech1.jobCount, tech2.jobCount);
    const normalizedArticles = articleDiff / Math.max(tech1.articleCount, tech2.articleCount);
    
    return 1 - (normalizedGrowth * 0.4 + normalizedJobs * 0.3 + normalizedArticles * 0.3);
  }, [tech1, tech2]);

  return (
    <div className="compare-page">
      <TechSelector onSelect={setTech1} selected={tech1} label="Technology 1" />
      <TechSelector onSelect={setTech2} selected={tech2} label="Technology 2" />
      {comparisonScore !== null && (
        <ComparisonScore score={comparisonScore} />
      )}
    </div>
  );
};
```

### 5.4 Messaging Context (NEW — SSE, app-wide)

`MessagingContext` (`contexts/MessagingContext.jsx` + `messagingStore.js`) mở **một** kết nối SSE
duy nhất cho cả app (mount tại `UserLayout`, không phải per-page), dùng `fetch` +
`ReadableStream` thủ công thay vì `new EventSource(...)` — trình duyệt không cho `EventSource`
gắn header `Authorization`, nên phải tự đọc stream để gắn bearer token (cùng pattern với
`notificationService`'s `streamNotifications`).

Hành vi khi nhận tin nhắn mới qua SSE:
1. Nếu thread đang mở → append trực tiếp vào `messagesByConversation[conversationId]`.
2. Nếu conversation chưa có trong danh sách → gọi lại `refreshConversations()` để lấy đủ
   `other_user`.
3. Ngược lại → patch `last_message_*` + tăng `unread_count` (trừ khi đang mở đúng thread đó) và
   đẩy conversation đó lên đầu danh sách.
4. Nếu đang mở đúng thread đó → gọi luôn `markConversationRead`.

Guard: bỏ qua hoàn toàn (không fetch, không mở SSE) nếu `localStorage.access_token` không tồn
tại. API surface qua `useMessagingContext()`: `conversations, conversationsLoading,
messagesByConversation, activeConversationId, refreshConversations, loadMessages,
selectConversation, openConversationWith, send`.

---

## 6. Routing

### 6.1 Route Configuration

> **Quan trọng — khác với ví dụ "lý tưởng" trước đây:** codebase thực tế **KHÔNG có** route-level
> guard kiểu `<ProtectedRoute>`. Mọi route dưới `UserLayout` (và cả `/admin/*`) render bất kể đã
> đăng nhập hay chưa; việc "cần đăng nhập" chỉ được enforce ở tầng gọi API (`apiClient` gắn bearer
> token nếu có, 401 → thử refresh → điều hướng `/login` nếu thất bại). Bảng route thật, rút gọn từ
> `App.jsx`:

```jsx
// App.jsx (rút gọn, đúng theo code thật — không có ProtectedRoute)
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import UserLayout from './layouts/UserLayout';
import AdminLayout from './layouts/AdminLayout';

import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import TrendDashboard from './pages/TrendDashboard';
import ComparePage from './pages/ComparePage';
import GraphExplorer from './pages/GraphExplorer';
import ClusterDashboard from './pages/ClusterDashboard';
import SalaryPage from './pages/SalaryPage';
import CompanyExplorer from './pages/CompanyExplorer';   // NEW
import ChatbotPage from './pages/ChatbotPage';
import UserProfile from './pages/UserProfile';
import CareerPage from './pages/CareerPage';
import InterviewPage from './pages/InterviewPage';       // NEW
import ReportPage from './pages/ReportPage';
import NotificationsPage from './pages/NotificationsPage';
import FeedPage from './pages/FeedPage';                 // NEW
import PublicProfilePage from './pages/PublicProfilePage'; // NEW
import MessagesPage from './pages/MessagesPage';         // NEW
import AdminDashboard from './pages/admin/AdminDashboard';
import AdminUsers from './pages/admin/AdminUsers';
import AdminCMS from './pages/admin/AdminCMS';
import AdminSettings from './pages/admin/AdminSettings';

const App = () => (
  <BrowserRouter>
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<UserLayout />}>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<TrendDashboard />} />
        <Route path="compare" element={<ComparePage />} />
        <Route path="graph" element={<GraphExplorer />} />
        <Route path="clusters" element={<ClusterDashboard />} />
        <Route path="salary" element={<SalaryPage />} />
        <Route path="companies" element={<CompanyExplorer />} />   {/* NEW */}
        <Route path="chat" element={<ChatbotPage />} />
        <Route path="profile" element={<UserProfile />} />
        <Route path="career" element={<CareerPage />} />
        <Route path="interview" element={<InterviewPage />} />     {/* NEW */}
        <Route path="report" element={<ReportPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="feed" element={<FeedPage />} />                {/* NEW */}
        <Route path="users/:id" element={<PublicProfilePage />} />  {/* NEW */}
        <Route path="messages" element={<MessagesPage />} />        {/* NEW */}
      </Route>

      <Route path="admin" element={<AdminLayout />}>
        <Route index element={<AdminDashboard />} />
        <Route path="automation" element={<AdminAutomation />} />   {/* thiếu trong danh sách import trên */}
        <Route path="moderation" element={<AdminModeration />} />
        <Route path="reports" element={<AdminReports />} />
        <Route path="clusters" element={<AdminClusters />} />
        <Route path="users" element={<AdminUsers />} />
        <Route path="cms" element={<AdminCMS />} />
        <Route path="settings" element={<AdminSettings />} />
      </Route>

      <Route path="/403" element={<ForbiddenPage />} />
      {/* Catch-all thật là NotFoundPage (404), KHÔNG redirect về /dashboard */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  </BrowserRouter>
);

export default App;
```

### 6.2 Navigation

```jsx
import { useNavigate, useLocation } from 'react-router-dom';

const NavigationMenu = () => {
  const navigate = useNavigate();
  const location = useLocation();

  // Thứ tự thật trong Header.jsx — thêm 4 mục mới (Bảng tin, Tin nhắn, Công ty, Phỏng vấn thử)
  const menuItems = [
    { path: '/dashboard', label: 'Radar', icon: '📊' },
    { path: '/feed', label: 'Bảng tin', icon: '📰' },        // NEW
    { path: '/messages', label: 'Tin nhắn', icon: '💬' },    // NEW
    { path: '/compare', label: 'So sánh', icon: '⚖️' },
    { path: '/graph', label: 'Đồ thị', icon: '🕸️' },
    { path: '/clusters', label: 'Cụm CN', icon: '🎯' },
    { path: '/salary', label: 'Lương', icon: '💰' },
    { path: '/companies', label: 'Công ty', icon: '🏢' },    // NEW
    { path: '/career', label: 'Career', icon: '🚀' },
    { path: '/interview', label: 'Phỏng vấn thử', icon: '🎤' }, // NEW
    { path: '/report', label: 'Báo cáo', icon: '📄' },
    { path: '/chat', label: 'AI Chat', icon: '🤖' },
  ];
  // `/users/:id` (PublicProfilePage) không có mục nav riêng — chỉ vào qua link tác giả
  // bài đăng/gợi ý follow/thread tin nhắn.

  return (
    <nav className="navigation-menu">
      {menuItems.map(item => (
        <button
          key={item.path}
          className={`nav-item ${location.pathname === item.path ? 'active' : ''}`}
          onClick={() => navigate(item.path)}
        >
          <span className="icon">{item.icon}</span>
          <span className="label">{item.label}</span>
        </button>
      ))}
    </nav>
  );
};
```

---

## 7. API Integration

### 7.1 HTTP Client

Không phải class `ApiClient` — thật là MỘT function `apiClient<T>(endpoint, options)`
(`utils/apiClient.ts`), dùng **path tương đối** (`/api/v1` + endpoint, không đọc
`VITE_API_URL`/`import.meta.env`) để Vite dev-proxy / Nginx tự forward sang backend, tránh CORS.
Token key trong `localStorage` là `access_token`/`refresh_token` (snake_case). Có single-flight
refresh (nhiều request 401 cùng lúc chỉ gọi `/auth/refresh` một lần), và map lỗi kết nối/503
thành `ApiError` riêng (`SERVER_CONNECTION_FAILED`, `SERVER_MAINTENANCE`) thay vì `Error` chung:

```ts
// utils/apiClient.ts (rút gọn)
const API_BASE_URL = '/api/v1';
let refreshPromise: Promise<boolean> | null = null; // single-flight refresh

const tryRefreshToken = async (): Promise<boolean> => { /* POST /auth/refresh, single-flight */ };

export const apiClient = async <T = unknown>(
    endpoint: string,
    options: RequestInit = {},
    _retried = false,
): Promise<T> => {
    const token = localStorage.getItem('access_token');
    const headers: Record<string, string> = {
        'Content-Type': 'application/json', 'Accept': 'application/json',
        ...(options.headers as Record<string, string> | undefined),
    };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const response = await fetch(`${API_BASE_URL}${endpoint}`, { ...options, headers });

    if (!response.ok) {
        if (response.status === 503) throw new ApiError('SERVER_MAINTENANCE', 503);
        if (response.status === 401) {
            if (!_retried && !endpoint.startsWith('/auth/') && await tryRefreshToken()) {
                return apiClient<T>(endpoint, options, true);
            }
            // refresh thất bại → xoá token, toast, redirect /login sau 1.2s
            throw new ApiError('UNAUTHORIZED', 401);
        }
        // ... parse message lỗi từ backend, throw ApiError(status)
    }
    return await response.json();
};
```

### 7.2 API Modules

**Không có object namespace nào gọi `apiClient.get(...)`/`apiClient.post(...)`** — `apiClient` là
MỘT function nhận `(endpoint, options)` như `fetch` thật (§7.1). Mỗi service module export các
function rời (flat named exports), tự truyền `{ method, body: JSON.stringify(...) }`:

```ts
// api/authService.ts (rút gọn, tên thật)
import { apiClient } from '../utils/apiClient';

export const loginUser = async (credentials: LoginCredentials): Promise<AuthTokens> =>
    await apiClient('/auth/login', { method: 'POST', body: JSON.stringify(credentials) });

export const registerUser = async (userData: RegisterData) =>
    await apiClient('/auth/register', { method: 'POST', body: JSON.stringify(userData) });

export const getCurrentUser = async () => await apiClient('/auth/me', { method: 'GET' });
// + logoutUser, refreshToken, getSystemStatus, forgotPassword, resetPassword

// api/trendService.ts — KHÔNG phải "radarService"/"radar" object
export const getRadarTop4 = async () => await apiClient('/radar/top4', { method: 'GET' });
export const getRadarTop10 = async () => await apiClient('/radar/top10', { method: 'GET' });
export const getRadarSearch = async (keywords: string[] = [], months = 6) => {
    const params = new URLSearchParams();
    keywords.forEach(kw => params.append('keywords', kw));
    params.append('months', String(months));
    return await apiClient(`/radar/search?${params.toString()}`, { method: 'GET' });
};
// GET /radar/stream — SSE, đẩy snapshot top4/top10 mới ngay khi ETL rebuild xong, không cần F5.
// Dùng utils/sseStream.ts (openSseStream), trả về AbortController để .abort() khi unmount.
export const streamRadar = (onSnapshot, onError) =>
    openSseStream('/radar/stream', (data) => onSnapshot(data), onError);
```

Các module khác (`companyService.ts`, `jobService.ts`, `socialService.ts`, `messagingService.ts`,
`interviewService.ts`, ...) theo đúng pattern này — flat function export, gọi `apiClient(endpoint,
{ method, body })` trực tiếp, KHÔNG qua `.get()/.post()/.delete()`.
`messagingService.ts`'s `streamConversations` dùng raw `fetch` + đọc `ReadableStream` thủ công
(không phải `new EventSource(...)`, vì `EventSource` không set được header `Authorization`).

### 7.3 Using API in Components

```tsx
import { useState, useEffect } from 'react';
import { getRadarTop4, getRadarTop10, streamRadar } from '../api/trendService';

const TrendDashboard = () => {
  const [top4, setTop4] = useState([]);
  const [top10, setTop10] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [top4Data, top10Data] = await Promise.all([
          getRadarTop4(),
          getRadarTop10(),
        ]);
        setTop4(top4Data.data);
        setTop10(top10Data.data);
      } catch (error) {
        console.error('Failed to fetch radar data:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchData();

    // Real code also opens an SSE stream here (streamRadar) so a backend rebuild pushes fresh
    // snapshots without waiting for a manual refresh — abort() on unmount.
  }, []);

  if (loading) return <LoadingSpinner />;

  return (
    <div className="trend-dashboard">
      <RadarChart data={top4} />
      <TopTechList data={top10} />
    </div>
  );
};
```

---

## 8. Styling

### 8.1 Plain CSS per Component

Không dùng CSS Modules (không có file `*.module.css` nào trong repo) — mỗi component có 1 file
`.css` cùng tên, import trực tiếp (`import './TrendDashboard.css'`), class name toàn cục:

```css
/* TrendDashboard.css */
.trend-dashboard {
  padding: 2rem;
  max-width: 1200px;
  margin: 0 auto;
}

.trend-dashboard__header {
  margin-bottom: 2rem;
}

.trend-dashboard__title {
  font-size: 2rem;
  font-weight: 600;
  color: #1a1a1a;
}

.trend-dashboard__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1.5rem;
}

.tech-card {
  background: white;
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  transition: transform 0.2s, box-shadow 0.2s;
}

.tech-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
}
```

```jsx
// TrendDashboard.jsx
import './TrendDashboard.css';

const TrendDashboard = () => {
  return (
    <div className="trend-dashboard">
      <header className="trend-dashboard__header">
        <h1 className="trend-dashboard__title">Technology Trends</h1>
      </header>
      <div className="trend-dashboard__grid">
        {technologies.map(tech => (
          <TechCard key={tech.name} tech={tech} />
        ))}
      </div>
    </div>
  );
};
```

### 8.2 Global Styles

```css
/* index.css */
:root {
  --primary-color: #3b82f6;
  --secondary-color: #10b981;
  --danger-color: #ef4444;
  --warning-color: #f59e0b;
  --text-primary: #1f2937;
  --text-secondary: #6b7280;
  --background-primary: #ffffff;
  --background-secondary: #f3f4f6;
  --border-color: #e5e7eb;
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.05);
  --shadow-md: 0 4px 6px rgba(0, 0, 0, 0.1);
  --shadow-lg: 0 10px 15px rgba(0, 0, 0, 0.1);
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
  color: var(--text-primary);
  background-color: var(--background-secondary);
  line-height: 1.6;
}

button {
  cursor: pointer;
  font-family: inherit;
}

input, textarea, select {
  font-family: inherit;
}

a {
  color: var(--primary-color);
  text-decoration: none;
}

a:hover {
  text-decoration: underline;
}
```

### 8.3 Responsive Design

```css
/* TrendDashboard.css */
.trend-dashboard__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.5rem;
}

@media (min-width: 640px) {
  .trend-dashboard__grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .trend-dashboard__grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (min-width: 1280px) {
  .trend-dashboard__grid {
    grid-template-columns: repeat(4, 1fr);
  }
}
```

---

## 9. Testing

### 9.1 Component Testing with Vitest

```jsx
// TechCard.test.jsx
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import TechCard from './TechCard';

describe('TechCard', () => {
  const mockTech = {
    name: 'React',
    category: 'Frontend',
    growthRate: 42.1,
    jobCount: 1240,
  };

  it('renders tech information', () => {
    render(<TechCard tech={mockTech} onClick={() => {}} />);
    
    expect(screen.getByText('React')).toBeInTheDocument();
    expect(screen.getByText('Frontend')).toBeInTheDocument();
    expect(screen.getByText(/42\.1%/)).toBeInTheDocument();
    expect(screen.getByText(/1240/)).toBeInTheDocument();
  });

  it('calls onClick when clicked', () => {
    const handleClick = vi.fn();
    render(<TechCard tech={mockTech} onClick={handleClick} />);
    
    screen.getByText('React').click();
    expect(handleClick).toHaveBeenCalledWith(mockTech);
  });
});
```

### 9.2 Integration Testing

Real file is `TrendDashboard.test.tsx`, mocks `../api/trendService` (not `../api/radar`) with the
real export names (`getRadarTop4`/`getRadarTop10`/`getRadarSearch`/`streamRadar`) via
`vi.mock(...)` + `vi.mocked()`. It also covers behavior this simplified example skips entirely:
`streamRadar`'s SSE callback firing a live update, and its returned `AbortController.abort()`
being called on unmount — real-time snapshot push is core to how `TrendDashboard` works (§7.3),
so the test suite exercises it, not just the one-shot `Promise.all` fetch:

```tsx
// TrendDashboard.test.tsx
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import TrendDashboard from './TrendDashboard';
import { getRadarTop4, getRadarTop10, getRadarSearch, streamRadar } from '../api/trendService';

vi.mock('../api/trendService');

describe('TrendDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(streamRadar).mockReturnValue(new AbortController());
  });

  it('displays tech data after loading', async () => {
    vi.mocked(getRadarTop4).mockResolvedValue({ data: [{ name: 'React', growth_rate: 42.1, job_count: 1240 }] } as any);
    vi.mocked(getRadarTop10).mockResolvedValue({ data: [] } as any);

    render(<BrowserRouter><TrendDashboard /></BrowserRouter>);

    await waitFor(() => expect(screen.getByText('React')).toBeInTheDocument());
  });

  it('aborts the SSE stream on unmount', () => {
    const abortController = new AbortController();
    const abortSpy = vi.spyOn(abortController, 'abort');
    vi.mocked(streamRadar).mockReturnValue(abortController);

    const { unmount } = render(<BrowserRouter><TrendDashboard /></BrowserRouter>);
    unmount();

    expect(abortSpy).toHaveBeenCalled();
  });
});
```

---

## 10. Build & Deployment

### 10.1 Development

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Run linter
npm run lint

# Run tests
npm test
npm run test:watch
```

### 10.2 Production Build

```bash
# Build for production
npm run build

# Preview production build
npm run preview
```

### 10.3 Docker Build

```dockerfile
# apps/web/Dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### 10.4 Nginx Configuration

```nginx
# apps/web/nginx.conf
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    # Serve static files
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Proxy API requests to backend
    location /api {
        proxy_pass http://spring-api:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
        
        # Disable buffering for SSE
        proxy_buffering off;
    }

    # Cache static assets
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

---

## 11. Best Practices

### 1. Component Organization

```jsx
// Good - Small, focused components
const TechCard = ({ tech, onClick }) => (
  <div className="tech-card" onClick={() => onClick(tech)}>
    <TechName name={tech.name} />
    <TechCategory category={tech.category} />
    <TechMetrics growth={tech.growthRate} jobs={tech.jobCount} />
  </div>
);

// Bad - Large, monolithic component
const TechCard = ({ tech, onClick }) => (
  <div className="tech-card" onClick={() => onClick(tech)}>
    <h3>{tech.name}</h3>
    <p>{tech.category}</p>
    <div className="metrics">
      <span>Growth: {tech.growthRate}%</span>
      <span>Jobs: {tech.jobCount}</span>
    </div>
    {/* ... 50 more lines ... */}
  </div>
);
```

### 2. Prop Validation

Không dùng `prop-types` (không có package này trong `package.json`) — validation qua TypeScript
`interface`/`type` ở compile time, bắt buộc vì `tsconfig.json` bật `strict: true`:

```tsx
interface Tech {
  name: string;
  category: string;
  growthRate: number;
  jobCount: number;
}

interface TechCardProps {
  tech: Tech;
  onClick: (tech: Tech) => void;
}

const TechCard = ({ tech, onClick }: TechCardProps) => { /* ... */ };
```

### 3. Error Boundaries

```jsx
class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true };
  }

  componentDidCatch(error, errorInfo) {
    console.error('Error caught by boundary:', error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return <ErrorMessage />;
    }
    return this.props.children;
  }
}

// Usage
<ErrorBoundary>
  <TrendDashboard />
</ErrorBoundary>
```

### 4. Performance Optimization

```jsx
// Good - Use React.memo for expensive components
const TechCard = React.memo(({ tech, onClick }) => {
  return (
    <div className="tech-card" onClick={() => onClick(tech)}>
      {/* ... */}
    </div>
  );
});

// Good - Use useCallback for event handlers
const handleTechClick = useCallback((tech) => {
  navigate(`/tech/${tech.name}`);
}, [navigate]);

// Good - Use useMemo for expensive computations
const sortedTechs = useMemo(() => {
  return technologies.sort((a, b) => b.growthRate - a.growthRate);
}, [technologies]);
```

### 5. Code Splitting

```jsx
// Good - Lazy load heavy components
const GraphExplorer = React.lazy(() => import('./pages/GraphExplorer'));
const ChatbotPage = React.lazy(() => import('./pages/ChatbotPage'));

// Usage with Suspense
<Suspense fallback={<LoadingSpinner />}>
  <Routes>
    <Route path="/graph" element={<GraphExplorer />} />
    <Route path="/chat" element={<ChatbotPage />} />
  </Routes>
</Suspense>
```

### 6. Accessibility

```jsx
// Good - Accessible form
<form onSubmit={handleSubmit}>
  <label htmlFor="email">Email</label>
  <input
    id="email"
    type="email"
    value={email}
    onChange={(e) => setEmail(e.target.value)}
    aria-required="true"
    aria-invalid={errors.email ? 'true' : 'false'}
    aria-describedby={errors.email ? 'email-error' : undefined}
  />
  {errors.email && (
    <span id="email-error" role="alert">
      {errors.email}
    </span>
  )}
  <button type="submit">Submit</button>
</form>

// Good - Accessible navigation
<nav aria-label="Main navigation">
  <ul>
    <li><a href="/radar">Tech Radar</a></li>
    <li><a href="/graph">Graph Explorer</a></li>
  </ul>
</nav>
```

---

## Resources

- [React Documentation](https://react.dev/)
- [Vite Documentation](https://vitejs.dev/)
- [React Router Documentation](https://reactrouter.com/)
- [Recharts Documentation](https://recharts.org/)
- [Testing Library Documentation](https://testing-library.com/)
- [Web Accessibility Initiative (WAI)](https://www.w3.org/WAI/)
