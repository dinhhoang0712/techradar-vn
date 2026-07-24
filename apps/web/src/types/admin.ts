// Domain types cho AdminDashboard/AdminSettings và các tab con (SocialTab, JobsTab, MessagingTab, ...).
import type { PipelineStats } from '../utils/adminDashboardFormat';

export interface AdminDashboardStats {
    totalUsers: number;
    activeSessions: number;
    searchesToday: number;
    topKeywords: (string | { name?: string; keyword?: string })[];
    monthlyVisits: { month: string; count: number }[];
}

export interface TopPoster {
    full_name: string;
    post_count: number;
}

export interface SocialDashboard {
    total_posts: number;
    posts_today: number;
    total_comments: number;
    total_likes: number;
    total_follows: number;
    top_posters?: TopPoster[];
    pending_reports?: number;
}

export interface TopTechnology {
    name: string;
    job_count: number;
}

export interface JobMarketDashboard {
    total_jobs_indexed: number;
    job_match_alerts_sent: number;
    top_technologies?: TopTechnology[];
}

export type PipelineDashboard = PipelineStats;

export interface NotificationTypeCount {
    type: string;
    count: number;
}

export interface MessagingDashboard {
    total_conversations: number;
    total_messages: number;
    messages_today: number;
    notifications_by_type?: NotificationTypeCount[];
}

export interface ClusteringPipelineStatus {
    status: 'running' | 'success' | 'failed' | string;
    started_at?: string;
    finished_at?: string;
    duration_s?: number;
    error?: string;
    current_stage?: string | null;
    snapshot_tag?: string;
}

export interface ClusteringPipelineRunMetrics {
    dbcv?: number;
    silhouette?: number;
    davies_bouldin?: number;
    calinski_harabasz?: number;
    [key: string]: number | undefined;
}

export interface ClusteringPipelineRun {
    started_at: string;
    snapshot_tag?: string;
    algorithm?: string;
    metrics?: ClusteringPipelineRunMetrics;
}

export interface DataPlatformJob {
    job_name: string;
    status: 'running' | 'success' | 'failed' | 'never_run';
    rows_affected?: number;
    error_msg?: string;
    started_at?: string;
    finished_at?: string;
}

// One row from GET /admin/data-platform/jobs/{jobId}/history — same fields as DataPlatformJob
// plus a stable row id (needed since history returns multiple rows per job) and computed duration.
export interface DataPlatformJobRun extends DataPlatformJob {
    id: number;
    duration_s: number | null;
}

export interface CrawlerStatus {
    state: 'idle' | 'running' | 'unknown';
    started_at?: string;
    finished_at?: string;
    results?: unknown;
    success_count?: number;
    total?: number;
}

export interface RadarStatus {
    state: 'idle' | 'running';
    started_at?: string | null;
    finished_at?: string | null;
    rows_upserted?: number | null;
}

// GET /admin/dashboard/live-metrics/stream (SSE) payload — the headline real-time numbers.
export interface AdminLiveMetrics {
    crawler: CrawlerStatus;
    radar: RadarStatus;
    new_technologies_this_month: number;
    ai_requests_today: number;
    pending_reports: number;
    pipeline_health: PipelineDashboard;
}

export interface AdminUser {
    id: string;
    full_name?: string;
    name?: string;
    email?: string;
    role?: string;
    status?: string;
    avatar_url?: string;
    [key: string]: unknown;
}

export interface AdminSettingsMap {
    isWebMaintenance?: boolean;
    isAppMaintenance?: boolean;
    isGraphEnabled?: boolean;
    isChatEnabled?: boolean;
    isRagEnabled?: boolean;
}

export interface AdminSettingRow {
    key: string;
    value: string | boolean;
}

// Admin moderation trả về post/comment dạng phẳng (author_name/author_avatar_url), khác với
// Post/Comment ở types/social.ts (author lồng thành object) dùng cho FeedPage/PostCard.
export interface AdminModerationPost {
    id: string;
    author_name?: string;
    author_avatar_url?: string;
    content: string;
    like_count: number;
    comment_count: number;
    created_at?: string;
}

export interface AdminModerationComment {
    id: string;
    author_name?: string;
    author_avatar_url?: string;
    content: string;
    created_at?: string;
}

// GET /admin/audit-log — one row of the admin mutation trail (user CRUD, moderation, cluster
// label overrides, pipeline triggers, admin notifications).
export interface AuditLogEntry {
    id: string;
    actor_id: string;
    actor_email?: string;
    action: string;
    target_type?: string;
    target_id?: string;
    details?: string;
    created_at: string;
}
