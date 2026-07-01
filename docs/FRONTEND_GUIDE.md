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
- **Vite 7** cho fast development và optimized builds
- **React Router DOM 7** cho client-side routing
- **Recharts 3** cho data visualization
- **D3.js 7** và react-force-graph-2d cho graph visualization

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
| D3.js | 7.9.0 | Data visualization |
| react-force-graph-2d | 1.29.1 | Force-directed graph |
| react-select | 5.10.2 | Select component |
| html2canvas | 1.4.1 | Screenshot export |

### 2.2 Development Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| @vitejs/plugin-react | 5.1.1 | Vite React plugin |
| TypeScript | 5.x | Type checking (optional) |
| ESLint | 9.39.1 | Linting |
| Vitest | 4.1.9 | Testing framework |
| Testing Library | 16.x | Component testing |

---

## 3. Cấu trúc dự án

```
apps/web/src/
├── main.jsx                      # Application entry point
├── index.css                     # Global styles
├── App.jsx                       # Root component
├── App.css                       # App styles
│
├── api/                          # API client layer
│   ├── client.js                 # HTTP client with interceptors
│   ├── auth.js                   # Auth API
│   ├── radar.js                  # Radar API
│   ├── graph.js                  # Graph API
│   ├── chat.js                   # Chat API
│   ├── clustering.js             # Clustering API
│   ├── compare.js                # Compare API
│   ├── career.js                 # Career API
│   └── user.js                   # User API
│
├── components/                   # Reusable components
│   ├── layout/                   # Layout components
│   │   ├── Header.jsx            # App header
│   │   ├── Sidebar.jsx           # Navigation sidebar
│   │   ├── Footer.jsx            # App footer
│   │   └── Layout.jsx            # Main layout wrapper
│   └── notifications/            # Notification components
│       ├── NotificationBell.jsx  # Notification bell icon
│       └── NotificationPanel.jsx # Notification dropdown
│
├── contexts/                     # React contexts
│   └── AuthContext.jsx           # Authentication state
│
├── pages/                        # Page components
│   ├── auth/                     # Auth pages
│   │   ├── LoginPage.jsx
│   │   ├── RegisterPage.jsx
│   │   └── ForgotPasswordPage.jsx
│   ├── TrendDashboard.jsx        # Tech radar dashboard
│   ├── GraphExplorer.jsx         # Knowledge graph explorer
│   ├── ChatbotPage.jsx           # RAG chat interface
│   ├── ClusterDashboard.jsx      # Clustering visualization
│   ├── ComparePage.jsx           # Technology comparison
│   ├── CareerPage.jsx            # Career path assistant
│   ├── ReportPage.jsx            # Trend reports
│   ├── SalaryPage.jsx            # Salary analytics
│   ├── UserProfile.jsx           # User profile
│   ├── MaintenancePage.jsx       # Maintenance mode
│   └── admin/                    # Admin pages
│       ├── AdminDashboard.jsx
│       ├── UserManagement.jsx
│       ├── CMSManagement.jsx
│       └── SettingsPage.jsx
│
├── layouts/                      # Page layouts
│   ├── AuthLayout.jsx            # Auth page layout
│   ├── MainLayout.jsx            # Main app layout
│   └── AdminLayout.jsx           # Admin layout
│
├── utils/                        # Utility functions
│   ├── formatters.js             # Data formatting
│   └── validators.js             # Form validation
│
├── data/                         # Static data
│   └── mockData.js               # Development mock data
│
├── assets/                       # Static assets
│   └── react.svg
│
└── styles/                       # Shared styles
    └── global.css
```

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

### 5.1 Auth Context

```jsx
// contexts/AuthContext.jsx
import { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [token, setToken] = useState(localStorage.getItem('accessToken'));

  useEffect(() => {
    // Check token validity on mount
    if (token) {
      fetchUserProfile()
        .then(profile => {
          setUser(profile);
          setLoading(false);
        })
        .catch(() => {
          logout();
          setLoading(false);
        });
    } else {
      setLoading(false);
    }
  }, [token]);

  const login = async (email, password) => {
    const response = await api.auth.login({ email, password });
    setToken(response.accessToken);
    localStorage.setItem('accessToken', response.accessToken);
    localStorage.setItem('refreshToken', response.refreshToken);
    setUser({ id: response.userId, email: response.email, role: response.role });
    return response;
  };

  const logout = () => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
  };

  const refreshAccessToken = async () => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
      logout();
      return;
    }

    try {
      const response = await api.auth.refresh({ refreshToken });
      setToken(response.accessToken);
      localStorage.setItem('accessToken', response.accessToken);
      localStorage.setItem('refreshToken', response.refreshToken);
      return response.accessToken;
    } catch (error) {
      logout();
      throw error;
    }
  };

  const value = {
    user,
    token,
    loading,
    login,
    logout,
    refreshAccessToken,
    isAuthenticated: !!user,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
};
```

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

---

## 6. Routing

### 6.1 Route Configuration

```jsx
// main.jsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { Layout } from './components/layout/Layout';

// Pages
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import TrendDashboard from './pages/TrendDashboard';
import GraphExplorer from './pages/GraphExplorer';
import ChatbotPage from './pages/ChatbotPage';
import ClusterDashboard from './pages/ClusterDashboard';
import ComparePage from './pages/ComparePage';
import CareerPage from './pages/CareerPage';
import ReportPage from './pages/ReportPage';
import SalaryPage from './pages/SalaryPage';
import UserProfile from './pages/UserProfile';
import MaintenancePage from './pages/MaintenancePage';

// Admin pages
import AdminDashboard from './pages/admin/AdminDashboard';
import UserManagement from './pages/admin/UserManagement';
import CMSManagement from './pages/admin/CMSManagement';
import SettingsPage from './pages/admin/SettingsPage';

// Protected Route Component
const ProtectedRoute = ({ children, requireAdmin = false }) => {
  const { isAuthenticated, user, loading } = useAuth();

  if (loading) return <div>Loading...</div>;

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (requireAdmin && user?.role !== 'ADMIN') {
    return <Navigate to="/" replace="/" />;
  }

  return children;
};

const App = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public routes */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          
          {/* Protected routes */}
          <Route path="/" element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }>
            <Route index element={<Navigate to="/radar" replace />} />
            <Route path="radar" element={<TrendDashboard />} />
            <Route path="graph" element={<GraphExplorer />} />
            <Route path="chat" element={<ChatbotPage />} />
            <Route path="clustering" element={<ClusterDashboard />} />
            <Route path="compare" element={<ComparePage />} />
            <Route path="career" element={<CareerPage />} />
            <Route path="report" element={<ReportPage />} />
            <Route path="salary" element={<SalaryPage />} />
            <Route path="profile" element={<UserProfile />} />
            
            {/* Admin routes */}
            <Route path="admin" element={
              <ProtectedRoute requireAdmin>
                <AdminDashboard />
              </ProtectedRoute>
            }>
              <Route index element={<Navigate to="/admin/users" replace />} />
              <Route path="users" element={<UserManagement />} />
              <Route path="cms" element={<CMSManagement />} />
              <Route path="settings" element={<SettingsPage />} />
            </Route>
          </Route>
          
          {/* Maintenance mode */}
          <Route path="/maintenance" element={<MaintenancePage />} />
          
          {/* Catch all */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
};

export default App;
```

### 6.2 Navigation

```jsx
import { useNavigate, useLocation } from 'react-router-dom';

const NavigationMenu = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems = [
    { path: '/radar', label: 'Tech Radar', icon: '📊' },
    { path: '/graph', label: 'Graph Explorer', icon: '🕸️' },
    { path: '/chat', label: 'AI Chat', icon: '🤖' },
    { path: '/clustering', label: 'Clustering', icon: '🎯' },
    { path: '/compare', label: 'Compare', icon: '⚖️' },
    { path: '/career', label: 'Career', icon: '🚀' },
  ];

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

```jsx
// api/client.js
class ApiClient {
  constructor() {
    this.baseURL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
    this.token = localStorage.getItem('accessToken');
  }

  setToken(token) {
    this.token = token;
    if (token) {
      localStorage.setItem('accessToken', token);
    } else {
      localStorage.removeItem('accessToken');
    }
  }

  async request(endpoint, options = {}) {
    const url = `${this.baseURL}/api/v1${endpoint}`;
    
    const headers = {
      'Content-Type': 'application/json',
      ...options.headers,
    };

    if (this.token) {
      headers['Authorization'] = `Bearer ${this.token}`;
    }

    const config = {
      ...options,
      headers,
    };

    try {
      const response = await fetch(url, config);
      
      if (!response.ok) {
        if (response.status === 401) {
          // Token expired, try refresh
          const newToken = await this.refreshToken();
          if (newToken) {
            headers['Authorization'] = `Bearer ${newToken}`;
            return this.request(endpoint, { ...options, headers });
          }
          // Refresh failed, redirect to login
          window.location.href = '/login';
          throw new Error('Session expired');
        }
        const error = await response.json();
        throw new Error(error.message || 'Request failed');
      }

      return await response.json();
    } catch (error) {
      throw error;
    }
  }

  async get(endpoint) {
    return this.request(endpoint, { method: 'GET' });
  }

  async post(endpoint, data) {
    return this.request(endpoint, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async put(endpoint, data) {
    return this.request(endpoint, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async delete(endpoint) {
    return this.request(endpoint, { method: 'DELETE' });
  }

  async refreshToken() {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) return null;

    try {
      const response = await fetch(`${this.baseURL}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      if (!response.ok) return null;

      const data = await response.json();
      this.setToken(data.accessToken);
      return data.accessToken;
    } catch (error) {
      return null;
    }
  }
}

const apiClient = new ApiClient();
export default apiClient;
```

### 7.2 API Modules

```jsx
// api/auth.js
import apiClient from './client';

export const auth = {
  login: (email, password) => 
    apiClient.post('/auth/login', { email, password }),
  
  register: (fullName, email, password) => 
    apiClient.post('/auth/register', { fullName, email, password }),
  
  logout: () => 
    apiClient.post('/auth/logout'),
  
  refreshToken: (refreshToken) => 
    apiClient.post('/auth/refresh', { refreshToken }),
  
  forgotPassword: (email) => 
    apiClient.post('/auth/forgot-password', { email }),
  
  resetPassword: (token, newPassword) => 
    apiClient.post('/auth/reset-password', { token, newPassword }),
  
  getProfile: () => 
    apiClient.get('/auth/me'),
};

// api/radar.js
export const radar = {
  getTop4: () => 
    apiClient.get('/radar/top4'),
  
  getTop10: () => 
    apiClient.get('/radar/top10'),
  
  search: (keywords, months = 6) => 
    apiClient.get(`/radar/search?keywords=${keywords.join(',')}&months=${months}`),
  
  exportPng: (limit = 20) => 
    apiClient.get(`/radar/export-png?limit=${limit}`),
  
  exportCsv: (limit = 50) => 
    apiClient.get(`/radar/export-csv?limit=${limit}`),
};

// api/chat.js
export const chat = {
  getHealth: () => 
    apiClient.get('/chat'),
  
  createSession: () => 
    apiClient.post('/chat/session'),
  
  getSessions: () => 
    apiClient.get('/chat/sessions'),
  
  deleteSession: (sessionId) => 
    apiClient.delete(`/chat/session/${sessionId}`),
  
  getMessages: (sessionId) => 
    apiClient.get(`/chat/session/${sessionId}/messages`),
  
  sendMessage: (sessionId, query) => 
    apiClient.post(`/chat/session/${sessionId}/messages`, { query }),
};

// api/graph.js
export const graph = {
  explore: (keywords, depth = 2, location, minSalary) => {
    const params = new URLSearchParams({
      keywords: keywords.join(','),
      depth: depth.toString(),
    });
    if (location) params.append('location', location);
    if (minSalary) params.append('min_salary', minSalary.toString());
    return apiClient.get(`/graph/explore?${params.toString()}`);
  },
  
  roadAnalysis: (from, to) => 
    apiClient.get(`/graph/road_analysis?from=${from}&to=${to}`),
  
  filter: (filters) => 
    apiClient.post('/graph/filter', filters),
};
```

### 7.3 Using API in Components

```jsx
import { useState, useEffect } from 'react';
import { radar } from '../api/radar';

const TrendDashboard = () => {
  const [top4, setTop4] = useState([]);
  const [top10, setTop10] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [top4Data, top10Data] = await Promise.all([
          radar.getTop4(),
          radar.getTop10(),
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

### 8.1 CSS Modules

```jsx
// TrendDashboard.css
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

```jsx
// TrendDashboard.test.jsx
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import TrendDashboard from './TrendDashboard';
import * as radarApi from '../api/radar';

vi.mock('../api/radar');

describe('TrendDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('displays loading state initially', () => {
    radarApi.getTop4.mockResolvedValue({ data: [] });
    radarApi.getTop10.mockResolvedValue({ data: [] });
    
    render(
      <BrowserRouter>
        <TrendDashboard />
      </BrowserRouter>
    );
    
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('displays tech data after loading', async () => {
    const mockTop4 = [
      { name: 'React', growthRate: 42.1, jobCount: 1240 },
      { name: 'Vue', growthRate: 35.2, jobCount: 890 },
    ];
    
    radarApi.getTop4.mockResolvedValue({ data: mockTop4 });
    radarApi.getTop10.mockResolvedValue({ data: [] });
    
    render(
      <BrowserRouter>
        <TrendDashboard />
      </BrowserRouter>
    );
    
    await waitFor(() => {
      expect(screen.getByText('React')).toBeInTheDocument();
      expect(screen.getByText('Vue')).toBeInTheDocument();
    });
  });

  it('displays error message on API failure', async () => {
    radarApi.getTop4.mockRejectedValue(new Error('API Error'));
    radarApi.getTop10.mockRejectedValue(new Error('API Error'));
    
    render(
      <BrowserRouter>
        <TrendDashboard />
      </BrowserRouter>
    );
    
    await waitFor(() => {
      expect(screen.getByText(/error/i)).toBeInTheDocument();
    });
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

```jsx
// Good - Use PropTypes or TypeScript
import PropTypes from 'prop-types';

TechCard.propTypes = {
  tech: PropTypes.shape({
    name: PropTypes.string.isRequired,
    category: PropTypes.string.isRequired,
    growthRate: PropTypes.number.isRequired,
    jobCount: PropTypes.number.isRequired,
  }).isRequired,
  onClick: PropTypes.func.isRequired,
};

// Bad - No validation
const TechCard = ({ tech, onClick }) => {
  // No prop validation
};
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
