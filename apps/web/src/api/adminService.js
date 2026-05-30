import { apiClient } from '../utils/apiClient';

// --- Dashboard Stats ---

export const fetchMonthlyVisits = async () => {
    return await apiClient('/admin/dashboard/monthly-visits');
};

export const fetchSearchesToday = async () => {
    return await apiClient('/admin/dashboard/searches-today');
};

export const fetchTopKeywords = async () => {
    return await apiClient('/admin/dashboard/top-keywords');
};

export const fetchUserCount = async () => {
    return await apiClient('/admin/dashboard/user-count');
};

export const fetchVisitsToday = async () => {
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
                revenueMock: getData(monthlyVisitsRes) || []
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
