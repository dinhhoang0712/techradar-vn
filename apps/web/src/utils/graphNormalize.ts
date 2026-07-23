// Chuẩn hoá payload node/edge thô từ Neo4jGraphRepository (nhãn/thuộc tính trả về không cố định
// theo từng loại truy vấn — explore vs lộ trình) về đúng shape {id,label,type} / {source,target,
// type,label} mà GraphExplorer/ForceGraph2D cần. Dùng chung cho mọi endpoint trả node/edge Neo4j
// để tránh mỗi nơi tự viết lại mapping rồi lệch nhau.
// pagerank_score/community_id/degree_centrality only ever appear on Technology nodes, written by
// the backend's graph analytics rebuild (POST /admin/graph-analytics/rebuild) — undefined until
// that rebuild has run at least once.
interface GraphAnalyticsProperties {
    pagerank_score?: number;
    community_id?: number;
    degree_centrality?: number;
}

export interface RawGraphNode {
    id?: string;
    keyword?: string;
    name?: string;
    label?: string;
    labels?: string[];
    type?: string;
    category?: string;
    properties?: { name?: string; title?: string } & GraphAnalyticsProperties & { [key: string]: unknown };
    [key: string]: unknown;
}

export interface GraphNode {
    id: string;
    label: string;
    type: string;
    properties?: GraphAnalyticsProperties & { [key: string]: unknown };
    [key: string]: unknown;
}

export interface RawGraphLink {
    source?: string;
    source_id?: string;
    from?: string;
    target?: string;
    target_id?: string;
    to?: string;
    type?: string;
    relation?: string;
    label?: string;
    [key: string]: unknown;
}

export interface GraphLink {
    source: string;
    target: string;
    type: string;
    label: string;
    [key: string]: unknown;
}

export function normalizeGraphNodes(rawNodes: RawGraphNode[] = []): GraphNode[] {
    return rawNodes.map(n => ({
        ...n,
        id: n.id || n.keyword || n.name || '',
        label: n.properties?.name || n.properties?.title || n.label || n.keyword || n.name || n.id || '',
        type: ((n.labels && n.labels[0]) || n.type || n.category || 'technology').toLowerCase(),
    }));
}

export function normalizeGraphLinks(rawLinks: RawGraphLink[] = []): GraphLink[] {
    return rawLinks.map(l => ({
        ...l,
        source: l.source || l.source_id || l.from || '',
        target: l.target || l.target_id || l.to || '',
        type: (l.type || l.relation || 'RELATED_TO').toUpperCase(),
        label: l.label || l.relation || l.type || '',
    }));
}
