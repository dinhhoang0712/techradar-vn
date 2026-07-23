// Domain types cho TrendDashboard/ComparePage — radar tổng quan, timeline theo tháng, so sánh công nghệ.
export interface MonthlyPoint {
    month: number;
    year: number;
    job_count?: number;
    article_count?: number;
}

export interface RadarTop4Item {
    industry: string;
    growth_rate: number;
    mom_rate?: number;
    job_count?: number;
    jobs_this_month?: number;
}

export interface RadarTop10Item {
    keyword: string;
    job_count?: number;
}

export interface RadarSearchResponse {
    data?: { month: number; year: number; keywords: Record<string, number> }[];
}

// Payload của GET /radar/stream (SSE) — bắn ra mỗi khi ETL rebuild xong, thay cho việc phải F5
// để thấy số liệu top4/top10 mới.
export interface RadarSnapshotEvent {
    top4: RadarTop4Item[];
    top10: RadarTop10Item[];
}

export interface CompareItem {
    keyword: string;
    monthly?: MonthlyPoint[];
    yoy_rate?: number;
    mom_rate?: number;
    growth_rate?: number;
}

export interface LlmSummaryResult {
    summary: string;
}
