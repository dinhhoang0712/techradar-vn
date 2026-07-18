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

// POST /admin/crawler/trigger — "Kích hoạt Radar": wakes the crawler container to run now
// instead of waiting for its own CRAWL_INTERVAL_HOURS schedule.
// Response data: { delivered: boolean } — false means no crawler container is currently listening.
// May reject with status 409 (a run is already in progress) or 429 (debounced, clicked too fast).
export const triggerCrawlerRun = async () => {
    return await apiClient('/admin/crawler/trigger', { method: 'POST' });
};

// GET /admin/crawler/status — last known crawl run status.
// Response data: { state: 'idle'|'running'|'unknown', started_at, finished_at, results, success_count, total }
export const fetchCrawlerStatus = async () => {
    return await apiClient('/admin/crawler/status');
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

// POST /admin/reports/{id}/ai-suggestion — get (or, with force=true, recompute) the LLM's
// suggested moderation action for a report. Response is a ReportView with
// ai_suggested_action, ai_suggested_reason, ai_confidence, ai_suggested_at filled in.
export const fetchReportAiSuggestion = async (id, force = false) => {
    return await apiClient(`/admin/reports/${id}/ai-suggestion${force ? '?force=true' : ''}`, { method: 'POST' });
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

// --- Clustering pipeline ops + cluster label review ---

// GET /admin/clustering/pipeline/status — live retrain state, poll while status === 'running'.
// Response data: { status, started_at, finished_at, duration_s, error, current_stage, snapshot_tag }
export const fetchClusteringPipelineStatus = async () => {
    return await apiClient('/admin/clustering/pipeline/status');
};

// POST /admin/clustering/pipeline/trigger — starts a full retrain (5 DVC stages) in the background.
// May reject with status 409 if a run is already in progress.
export const triggerClusteringPipeline = async () => {
    return await apiClient('/admin/clustering/pipeline/trigger', { method: 'POST' });
};

// GET /admin/clustering/pipeline/runs — history of past "best" training runs (model quality
// metrics: silhouette/dbcv/... over time), newest first.
export const fetchClusteringPipelineRuns = async () => {
    return await apiClient('/admin/clustering/pipeline/runs');
};

export const fetchClusters = async (isCoherent) => {
    const qs = isCoherent === undefined ? '' : `?is_coherent=${isCoherent}`;
    return await apiClient(`/clustering/clusters${qs}`);
};

export const fetchClusterDetail = async (clusterId) => {
    return await apiClient(`/clustering/clusters/${clusterId}`);
};

// PUT /admin/clustering/clusters/{id}/label — override an AI-generated cluster label.
// body: { label?, labelEn?, description?, domain? } — at least 1 field required.
export const updateClusterLabel = async (clusterId, fields) => {
    return await apiClient(`/admin/clustering/clusters/${clusterId}/label`, {
        method: 'PUT',
        body: JSON.stringify(fields)
    });
};

// --- Data platform gold jobs (manual trigger) ---

// GET /admin/data-platform/jobs — latest dp_pipeline_runs status for each of the 5 jobs that have
// no dedicated trigger of their own (gold_pg_etl and retrain_clustering already have theirs).
// Response data: [{ job_name, status: 'running'|'success'|'failed'|'never_run', rows_affected,
//                    error_msg, started_at, finished_at }, ...] — always 5 entries, in a fixed order.
export const fetchDataPlatformJobs = async () => {
    return await apiClient('/admin/data-platform/jobs');
};

// POST /admin/data-platform/jobs/{jobId}/trigger — asks data-platform to run the job now instead
// of waiting for its cron schedule. Response data: { delivered: boolean } — false means no
// data-platform container is currently listening on Redis.
// May reject with status 409 if that job's last logged run is still 'running'.
export const triggerDataPlatformJob = async (jobId) => {
    return await apiClient(`/admin/data-platform/jobs/${jobId}/trigger`, { method: 'POST' });
};

// --- Admin notifications ---

// POST /admin/notifications — gửi thông báo tới 1 user (kèm userId) hoặc broadcast tới toàn bộ
// user đang active (bỏ trống userId). Response data: { recipients: number }
export const sendAdminNotification = async ({ title, body, link, userId }) => {
    return await apiClient('/admin/notifications', {
        method: 'POST',
        body: JSON.stringify({ title, body, link, userId })
    });
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
