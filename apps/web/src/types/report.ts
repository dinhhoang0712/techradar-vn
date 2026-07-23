// Domain types cho ReportPage: báo cáo tổng hợp xu hướng công nghệ theo quý/năm (GET /report).
export interface ReportTechRow {
    name: string;
    cluster_label?: string;
    job_count?: number;
    growth_rate?: number;
}

export interface ReportResult {
    period?: string;
    top_techs?: ReportTechRow[];
    report?: string;
    generated_at?: string;
}
