import { apiClient } from '../utils/apiClient';

// --- Dashboard Stats ---

const fetchMonthlyVisits = async () => {
    return await apiClient('/admin/dashboard/monthly-visits');
};

const fetchSearchesToday = async () => {
    return await apiClient('/admin/dashboard/searches-today');
};

const fetchTopKeywords = async () => {
    return await apiClient('/admin/dashboard/top-keywords');
};

const fetchUserCount = async () => {
    return await apiClient('/admin/dashboard/user-count');
};

const fetchVisitsToday = async () => {
    return await apiClient('/admin/dashboard/visits-today');
};

/**
 * @deprecated Use granular fetch functions instead.
 * Maintaining this for backward compatibility during transition.
 */
export const fetchAdminDashboardStats = async () => {
    try {
        const [userCountRes, visitsTodayRes, searchesTodayRes, monthlyVisitsRes, topKeywordsRes] = await Promise.allSettled([
            fetchUserCount(),
            fetchVisitsToday(),
            fetchSearchesToday(),
            fetchMonthlyVisits(),
            fetchTopKeywords()
        ]);

        // Helper to extract data safely from settled promises
        const getData = (res) => {
            if (res.status === 'fulfilled' && res.value) {
                // Handle both { status, data } and raw data shapes
                return res.value.data !== undefined ? res.value.data : res.value;
            }
            return null;
        };

        return {
            status: 'success',
            data: {
                totalUsers: getData(userCountRes) || 0,
                activeSessions: getData(visitsTodayRes) || 0,
                searchesToday: getData(searchesTodayRes) || 0,
                topKeywords: getData(topKeywordsRes) || [],
                monthlyVisits: getData(monthlyVisitsRes) || []
            }
        };
    } catch (error) {
        console.error('Failed to aggregate admin stats:', error);
        throw error;
    }
};

// --- Settings Management ---

export const fetchAdminSettings = async () => {
    return await apiClient('/admin/settings');
};

export const updateAdminSetting = async (key, value) => {
    return await apiClient(`/admin/settings/${key}`, {
        method: 'PUT',
        body: JSON.stringify({ value })
    });
};

// POST /admin/analytics/rebuild — rebuilds tech_analytics from the knowledge graph on demand
// instead of waiting for the scheduled ETL cron.
export const triggerAnalyticsRebuild = async () => {
    return await apiClient('/admin/analytics/rebuild', { method: 'POST' });
};

// --- User Management ---

export const fetchAdminUsers = async () => {
    return await apiClient('/admin/users');
};

export const createAdminUser = async (userData) => {
    return await apiClient('/admin/users', {
        method: 'POST',
        body: JSON.stringify(userData)
    });
};

export const updateAdminUser = async (id, userData) => {
    return await apiClient(`/admin/users/${id}`, {
        method: 'PUT',
        body: JSON.stringify(userData)
    });
};

export const deleteAdminUser = async (id) => {
    return await apiClient(`/admin/users/${id}`, {
        method: 'DELETE'
    });
};

// --- Social Moderation ---

export const fetchAdminPosts = async (page = 0, size = 20) => {
    return await apiClient(`/admin/posts?page=${page}&size=${size}`);
};

export const deleteAdminPost = async (id) => {
    return await apiClient(`/admin/posts/${id}`, { method: 'DELETE' });
};

export const fetchAdminPostComments = async (postId, page = 0, size = 20) => {
    return await apiClient(`/admin/posts/${postId}/comments?page=${page}&size=${size}`);
};

export const deleteAdminComment = async (id) => {
    return await apiClient(`/admin/comments/${id}`, { method: 'DELETE' });
};

// --- Content Reports (moderation queue) ---

// Dispatched after a report is dismissed or its content deleted, so AdminSidebar can refresh its
// pending-count badge immediately instead of waiting for the next poll.
export const ADMIN_REPORTS_CHANGED_EVENT = 'admin-reports-changed';

export const fetchAdminReports = async (page = 0, size = 20) => {
    return await apiClient(`/admin/reports?page=${page}&size=${size}`);
};

export const dismissAdminReport = async (id) => {
    return await apiClient(`/admin/reports/${id}/dismiss`, { method: 'POST' });
};

// --- Report Dashboards ---

export const fetchSocialDashboard = async () => {
    return await apiClient('/admin/dashboard/social');
};

export const fetchJobMarketDashboard = async () => {
    return await apiClient('/admin/dashboard/jobs');
};

export const fetchPipelineDashboard = async () => {
    return await apiClient('/admin/dashboard/pipeline');
};

export const fetchMessagingDashboard = async () => {
    return await apiClient('/admin/dashboard/messaging');
};

// --- CMS Content Management ---

export const fetchCmsContent = async () => {
    return await apiClient('/admin/cms');
};

export const createCmsContent = async (content) => {
    return await apiClient('/admin/cms', {
        method: 'POST',
        body: JSON.stringify(content)
    });
};

export const updateCmsContent = async (id, content) => {
    return await apiClient(`/admin/cms/${id}`, {
        method: 'PUT',
        body: JSON.stringify(content)
    });
};

export const deleteCmsContent = async (id) => {
    return await apiClient(`/admin/cms/${id}`, {
        method: 'DELETE'
    });
};
