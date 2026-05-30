// Mock API for Admin Dashboard

const MOCK_ADMIN_STATS = {
    totalUsers: 1450,
    activeSessions: 342,
    searchesToday: 890,
    topKeywords: ['Golang', 'React', 'AI Engineer', 'Data Analyst'],
    revenueMock: [
        { name: 'Tháng 1', uv: 4000 },
        { name: 'Tháng 2', uv: 3000 },
        { name: 'Tháng 3', uv: 5000 },
        { name: 'Tháng 4', uv: 4500 },
    ]
};

export const fetchAdminDashboardStats = async () => {
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve({
                status: 'success',
                data: MOCK_ADMIN_STATS
            });
        }, 500);
    });
};
