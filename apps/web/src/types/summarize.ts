// Domain type cho SummaryPanel (tóm tắt tin tức gần đây theo công nghệ).
export interface TechSummary {
    period?: string;
    sources_used?: number;
    summary?: string;
    key_points?: string[];
}
