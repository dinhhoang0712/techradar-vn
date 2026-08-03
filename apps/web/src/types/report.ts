// Domain types cho ReportPage: báo cáo tổng hợp xu hướng công nghệ theo quý/năm (GET /report).
export interface ReportTechRow {
    name: string;
    cluster_label?: string;
    job_count?: number;
    growth_rate?: number;
    // BE gộp 2 nguồn: 'analytics' (tech_analytics/PostgreSQL, có job_count+growth_rate) và
    // 'articles' (Neo4j mentions, chỉ có mention_count) — xem report_service.py::handle().
    source?: 'analytics' | 'articles';
    mention_count?: number;
}

export interface ReportResult {
    period?: string;
    top_techs?: ReportTechRow[];
    report?: string;
    generated_at?: string;
}

// Một báo cáo đã tạo, lưu lại phía client (localStorage) để xem lại mà không cần gọi LLM lần nữa.
export interface ReportHistoryEntry {
    id: string;
    period: string;
    topN: number;
    savedAt: string;
    result: ReportResult;
}
