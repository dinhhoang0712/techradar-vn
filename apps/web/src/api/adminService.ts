import { apiClient } from '../utils/apiClient';
import { openSseStream } from '../utils/sseStream';
import type { ApiResponse } from '../types/api';
import type {
    AdminDashboardStats, SocialDashboard, JobMarketDashboard, PipelineDashboard, MessagingDashboard,
    ClusteringPipelineStatus, ClusteringPipelineRun, DataPlatformJob, DataPlatformJobRun, CrawlerStatus, AdminUser, AdminSettingRow,
    AdminModerationPost, AdminModerationComment, AdminLiveMetrics, AuditLogEntry,
} from '../types/admin';
import type { ClusterSummary, ClusterDetail } from '../types/cluster';

// --- Dashboard Stats ---

const fetchMonthlyVisits = async (): Promise<unknown> => {
    return await apiClient('/admin/dashboard/monthly-visits');
};

const fetchSearchesToday = async (): Promise<unknown> => {
    return await apiClient('/admin/dashboard/searches-today');
};

const fetchTopKeywords = async (): Promise<unknown> => {
    return await apiClient('/admin/dashboard/top-keywords');
};

const fetchUserCount = async (): Promise<unknown> => {
    return await apiClient('/admin/dashboard/user-count');
};

const fetchVisitsToday = async (): Promise<unknown> => {
    return await apiClient('/admin/dashboard/visits-today');
};

interface AggregatedStatsResult {
    status: 'success';
    data: AdminDashboardStats;
}

/**
 * @deprecated Use granular fetch functions instead.
 * Maintaining this for backward compatibility during transition.
 */
export const fetchAdminDashboardStats = async (): Promise<AggregatedStatsResult> => {
    try {
        const [userCountRes, visitsTodayRes, searchesTodayRes, monthlyVisitsRes, topKeywordsRes] = await Promise.allSettled([
            fetchUserCount(),
            fetchVisitsToday(),
            fetchSearchesToday(),
            fetchMonthlyVisits(),
            fetchTopKeywords()
        ]);

        // Helper to extract data safely from settled promises
        const getData = (res: PromiseSettledResult<unknown>): unknown => {
            if (res.status === 'fulfilled' && res.value) {
                // Handle both { status, data } and raw data shapes
                const value = res.value as { data?: unknown };
                return value.data !== undefined ? value.data : res.value;
            }
            return null;
        };

        return {
            status: 'success',
            data: {
                totalUsers: (getData(userCountRes) as number) || 0,
                activeSessions: (getData(visitsTodayRes) as number) || 0,
                searchesToday: (getData(searchesTodayRes) as number) || 0,
                topKeywords: (getData(topKeywordsRes) as AdminDashboardStats['topKeywords']) || [],
                monthlyVisits: (getData(monthlyVisitsRes) as AdminDashboardStats['monthlyVisits']) || []
            }
        };
    } catch (error) {
        console.error('Failed to aggregate admin stats:', error);
        throw error;
    }
};

// --- Settings Management ---

export const fetchAdminSettings = async (): Promise<ApiResponse<AdminSettingRow[]>> => {
    return await apiClient('/admin/settings');
};

export const updateAdminSetting = async (key: string, value: string): Promise<unknown> => {
    return await apiClient(`/admin/settings/${key}`, {
        method: 'PUT',
        body: JSON.stringify({ value })
    });
};

export const deleteAdminSetting = async (key: string): Promise<unknown> => {
    return await apiClient(`/admin/settings/${key}`, { method: 'DELETE' });
};

// --- Cache eviction ---
// Company/job/roadmap reads are cached (Redis, 30 min TTL) with no automatic invalidation when
// their source data changes — these let an admin force stale data to refresh immediately.

export const evictCompanyCache = async (): Promise<unknown> => {
    return await apiClient('/admin/cache/companies/evict', { method: 'POST' });
};

export const evictJobMatchCache = async (): Promise<unknown> => {
    return await apiClient('/admin/cache/jobs/evict', { method: 'POST' });
};

export const evictRoadmapCache = async (): Promise<unknown> => {
    return await apiClient('/admin/cache/roadmap/evict', { method: 'POST' });
};

// --- Audit log ---

export const fetchAuditLog = async (page = 0, size = 50): Promise<ApiResponse<AuditLogEntry[]>> => {
    return await apiClient(`/admin/audit-log?page=${page}&size=${size}`);
};

// POST /admin/analytics/rebuild — rebuilds tech_analytics from the knowledge graph on demand
// instead of waiting for the scheduled ETL cron.
export const triggerAnalyticsRebuild = async (): Promise<ApiResponse<{ rows_upserted?: number }>> => {
    return await apiClient('/admin/analytics/rebuild', { method: 'POST' });
};

// POST /admin/graph-analytics/rebuild — recomputes PageRank/Louvain community/degree centrality
// (Neo4j GDS) for Technology nodes, powering the Knowledge Graph Explorer's "Phân tích đồ thị" view.
export const triggerGraphAnalyticsRebuild = async (): Promise<ApiResponse<{ technologies_scored?: number; communities_found?: number }>> => {
    return await apiClient('/admin/graph-analytics/rebuild', { method: 'POST' });
};

// POST /admin/crawler/trigger — "Kích hoạt Radar": wakes the crawler container to run now
// instead of waiting for its own CRAWL_INTERVAL_HOURS schedule.
// Response data: { delivered: boolean } — false means no crawler container is currently listening.
// May reject with status 409 (a run is already in progress) or 429 (debounced, clicked too fast).
export const triggerCrawlerRun = async (): Promise<ApiResponse<{ delivered: boolean }> & { message?: string }> => {
    return await apiClient('/admin/crawler/trigger', { method: 'POST' });
};

// GET /admin/crawler/status — last known crawl run status.
export const fetchCrawlerStatus = async (): Promise<ApiResponse<CrawlerStatus>> => {
    return await apiClient('/admin/crawler/status');
};

// --- User Management ---

export const fetchAdminUsers = async (): Promise<ApiResponse<AdminUser[]>> => {
    return await apiClient('/admin/users');
};

export const createAdminUser = async (userData: Partial<AdminUser>): Promise<ApiResponse<AdminUser>> => {
    return await apiClient('/admin/users', {
        method: 'POST',
        body: JSON.stringify(userData)
    });
};

export const updateAdminUser = async (id: string, userData: Partial<AdminUser>): Promise<ApiResponse<AdminUser>> => {
    return await apiClient(`/admin/users/${id}`, {
        method: 'PUT',
        body: JSON.stringify(userData)
    });
};

export const deleteAdminUser = async (id: string): Promise<unknown> => {
    return await apiClient(`/admin/users/${id}`, {
        method: 'DELETE'
    });
};

// --- Social Moderation ---

export const fetchAdminPosts = async (page = 0, size = 20): Promise<ApiResponse<AdminModerationPost[]>> => {
    return await apiClient(`/admin/posts?page=${page}&size=${size}`);
};

export const deleteAdminPost = async (id: string): Promise<unknown> => {
    return await apiClient(`/admin/posts/${id}`, { method: 'DELETE' });
};

export const fetchAdminPostComments = async (postId: string, page = 0, size = 20): Promise<ApiResponse<AdminModerationComment[]>> => {
    return await apiClient(`/admin/posts/${postId}/comments?page=${page}&size=${size}`);
};

export const deleteAdminComment = async (id: string): Promise<unknown> => {
    return await apiClient(`/admin/comments/${id}`, { method: 'DELETE' });
};

// --- Content Reports (moderation queue) ---

// Dispatched after a report is dismissed or its content deleted, so AdminSidebar can refresh its
// pending-count badge immediately instead of waiting for the next poll.
export const ADMIN_REPORTS_CHANGED_EVENT = 'admin-reports-changed';

export interface AdminReport {
    id: string;
    reporter_name?: string;
    target_type?: 'POST' | 'COMMENT' | string;
    target_content?: string;
    target_author_name?: string;
    post_id?: string;
    comment_id?: string;
    reason?: string;
    created_at?: string;
    ai_suggested_action?: string;
    ai_suggested_reason?: string;
    ai_confidence?: number;
    ai_suggested_at?: string;
    [key: string]: unknown;
}

export const fetchAdminReports = async (page = 0, size = 20): Promise<ApiResponse<AdminReport[]>> => {
    return await apiClient(`/admin/reports?page=${page}&size=${size}`);
};

export const dismissAdminReport = async (id: string): Promise<unknown> => {
    return await apiClient(`/admin/reports/${id}/dismiss`, { method: 'POST' });
};

// POST /admin/reports/{id}/ai-suggestion — get (or, with force=true, recompute) the LLM's
// suggested moderation action for a report. Response is a ReportView with
// ai_suggested_action, ai_suggested_reason, ai_confidence, ai_suggested_at filled in.
export const fetchReportAiSuggestion = async (id: string, force = false): Promise<ApiResponse<AdminReport>> => {
    return await apiClient(`/admin/reports/${id}/ai-suggestion${force ? '?force=true' : ''}`, { method: 'POST' });
};

// --- Report Dashboards ---

export const fetchSocialDashboard = async (): Promise<ApiResponse<SocialDashboard>> => {
    return await apiClient('/admin/dashboard/social');
};

export const fetchJobMarketDashboard = async (): Promise<ApiResponse<JobMarketDashboard>> => {
    return await apiClient('/admin/dashboard/jobs');
};

export const fetchPipelineDashboard = async (): Promise<ApiResponse<PipelineDashboard>> => {
    return await apiClient('/admin/dashboard/pipeline');
};

export const fetchMessagingDashboard = async (): Promise<ApiResponse<MessagingDashboard>> => {
    return await apiClient('/admin/dashboard/messaging');
};

// GET /admin/dashboard/live-metrics/stream — SSE, backend re-polls and pushes a fresh snapshot
// every 5s: articles crawled (last run), technologies first tracked this month, whether a radar
// rebuild is running, and today's AI-proxy request count.
// Trả về AbortController; gọi .abort() khi unmount để đóng stream.
export const streamAdminLiveMetrics = (
    onSnapshot: (metrics: AdminLiveMetrics) => void,
    onError?: (err: Error) => void,
): AbortController => {
    return openSseStream('/admin/dashboard/live-metrics/stream', (data) => onSnapshot(data as AdminLiveMetrics), onError);
};

// --- Clustering pipeline ops + cluster label review ---

// GET /admin/clustering/pipeline/status — live retrain state, poll while status === 'running'.
export const fetchClusteringPipelineStatus = async (): Promise<ApiResponse<ClusteringPipelineStatus>> => {
    return await apiClient('/admin/clustering/pipeline/status');
};

// POST /admin/clustering/pipeline/trigger — starts a full retrain (5 DVC stages) in the background.
// May reject with status 409 if a run is already in progress.
export const triggerClusteringPipeline = async (): Promise<unknown> => {
    return await apiClient('/admin/clustering/pipeline/trigger', { method: 'POST' });
};

// GET /admin/clustering/pipeline/runs — history of past "best" training runs (model quality
// metrics: silhouette/dbcv/... over time), newest first.
export const fetchClusteringPipelineRuns = async (): Promise<ApiResponse<ClusteringPipelineRun[]>> => {
    return await apiClient('/admin/clustering/pipeline/runs');
};

export const fetchClusters = async (isCoherent?: boolean): Promise<ApiResponse<ClusterSummary[]>> => {
    const qs = isCoherent === undefined ? '' : `?is_coherent=${isCoherent}`;
    return await apiClient(`/clustering/clusters${qs}`);
};

export const fetchClusterDetail = async (clusterId: string | number): Promise<ApiResponse<ClusterDetail>> => {
    return await apiClient(`/clustering/clusters/${clusterId}`);
};

export interface BatchPredictTechResult {
    tech_name: string;
    cluster_id: number | null;
    label: string | null;
    label_en?: string | null;
    domain?: string | null;
    found: boolean;
    provisional?: boolean;
    matched_via?: string | null;
}

export interface BatchPredictResult {
    results: BatchPredictTechResult[];
    n_found: number;
    n_provisional: number;
    n_not_found: number;
    snapshot_tag?: string;
}

// POST /clustering/predict/batch — look up the cluster for several technologies in one call
// instead of one request per technology.
export const batchPredictClusters = async (techNames: string[]): Promise<ApiResponse<BatchPredictResult>> => {
    return await apiClient('/clustering/predict/batch', {
        method: 'POST',
        body: JSON.stringify({ tech_names: techNames }),
    });
};

export interface UpdateClusterLabelFields {
    label?: string;
    label_en?: string;
    description?: string;
    domain?: string;
}

// PUT /admin/clustering/clusters/{id}/label — override an AI-generated cluster label.
export const updateClusterLabel = async (clusterId: string | number, fields: UpdateClusterLabelFields): Promise<ApiResponse<ClusterDetail>> => {
    return await apiClient(`/admin/clustering/clusters/${clusterId}/label`, {
        method: 'PUT',
        body: JSON.stringify(fields)
    });
};

// --- Data platform gold jobs (manual trigger) ---

// GET /admin/data-platform/jobs — latest dp_pipeline_runs status for each of the 5 jobs that have
// no dedicated trigger of their own (gold_pg_etl and retrain_clustering already have theirs).
// Response data: always 5 entries, in a fixed order.
export const fetchDataPlatformJobs = async (): Promise<ApiResponse<DataPlatformJob[]>> => {
    return await apiClient('/admin/data-platform/jobs');
};

// POST /admin/data-platform/jobs/{jobId}/trigger — asks data-platform to run the job now instead
// of waiting for its cron schedule. Response data: { delivered: boolean } — false means no
// data-platform container is currently listening on Redis.
// May reject with status 409 if that job's last logged run is still 'running'.
export const triggerDataPlatformJob = async (jobId: string): Promise<ApiResponse<{ delivered: boolean }> & { message?: string }> => {
    return await apiClient(`/admin/data-platform/jobs/${jobId}/trigger`, { method: 'POST' });
};

// GET /admin/data-platform/jobs/{jobId}/history — full run history for one job, newest first,
// paginated (page/size, same convention as fetchAdminPosts/fetchAdminReports).
export const fetchDataPlatformJobHistory = async (jobId: string, page = 0, size = 20): Promise<ApiResponse<DataPlatformJobRun[]>> => {
    return await apiClient(`/admin/data-platform/jobs/${jobId}/history?page=${page}&size=${size}`);
};

// --- Admin notifications ---

interface SendAdminNotificationOptions {
    title: string;
    body?: string;
    link?: string;
    userId?: string;
}

// POST /admin/notifications — gửi thông báo tới 1 user (kèm userId) hoặc broadcast tới toàn bộ
// user đang active (bỏ trống userId). Response data: { recipients: number }
export const sendAdminNotification = async ({ title, body, link, userId }: SendAdminNotificationOptions): Promise<ApiResponse<{ recipients: number }>> => {
    return await apiClient('/admin/notifications', {
        method: 'POST',
        body: JSON.stringify({ title, body, link, user_id: userId })
    });
};

// --- CMS Content Management ---

export interface CmsContent {
    id: string;
    title?: string;
    type?: string;
    status?: string;
    content_date?: string;
    date?: string;
    [key: string]: unknown;
}

export const fetchCmsContent = async (): Promise<ApiResponse<CmsContent[]>> => {
    return await apiClient('/admin/cms');
};

export const createCmsContent = async (content: Partial<CmsContent>): Promise<ApiResponse<CmsContent>> => {
    return await apiClient('/admin/cms', {
        method: 'POST',
        body: JSON.stringify(content)
    });
};

export const updateCmsContent = async (id: string, content: Partial<CmsContent>): Promise<ApiResponse<CmsContent>> => {
    return await apiClient(`/admin/cms/${id}`, {
        method: 'PUT',
        body: JSON.stringify(content)
    });
};

export const deleteCmsContent = async (id: string): Promise<unknown> => {
    return await apiClient(`/admin/cms/${id}`, {
        method: 'DELETE'
    });
};

// --- Knowledge Graph review queue ---
// dp_tech_alias_review_queue (Technology alias pairs the LLM in tech_dedup.py wasn't confident
// enough to auto-merge) + Company near-duplicate groups (detected live from Neo4j, never
// persisted — see KgReviewAdminController).

export const KG_REVIEW_CHANGED_EVENT = 'admin-kg-review-changed';

export interface TechAliasReviewItem {
    id: number;
    name_a: string;
    name_b: string;
    llm_reasoning?: string;
    status: string;
    created_at: string;
}

export interface CompanyDuplicateCandidate {
    id: string;
    name: string;
}

export interface CompanyDuplicateGroup {
    normalized_core: string;
    companies: CompanyDuplicateCandidate[];
}

export const fetchTechAliasReviewQueue = async (page = 0, size = 20): Promise<ApiResponse<TechAliasReviewItem[]>> => {
    return await apiClient(`/admin/kg-review/tech-aliases?page=${page}&size=${size}`);
};

export const fetchTechAliasReviewCount = async (): Promise<ApiResponse<{ pending: number }>> => {
    return await apiClient('/admin/kg-review/tech-aliases/count');
};

export const approveTechAlias = async (id: number, canonicalName?: string): Promise<unknown> => {
    return await apiClient(`/admin/kg-review/tech-aliases/${id}/approve`, {
        method: 'POST',
        body: JSON.stringify({ canonical_name: canonicalName || null }),
    });
};

export const rejectTechAlias = async (id: number): Promise<unknown> => {
    return await apiClient(`/admin/kg-review/tech-aliases/${id}/reject`, { method: 'POST' });
};

export const fetchCompanyDuplicates = async (): Promise<ApiResponse<CompanyDuplicateGroup[]>> => {
    return await apiClient('/admin/kg-review/company-duplicates');
};

export const mergeCompanyDuplicate = async (duplicateId: string, canonicalId: string): Promise<unknown> => {
    return await apiClient('/admin/kg-review/company-duplicates/merge', {
        method: 'POST',
        body: JSON.stringify({ duplicate_id: duplicateId, canonical_id: canonicalId }),
    });
};
