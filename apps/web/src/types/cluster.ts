// Domain types cho phân cụm công nghệ (ClusterDashboard/ClusterGrid/ClusterDetailView, AdminClusters).
export interface ClusterSummary {
    cluster_id: number;
    label: string;
    label_en?: string;
    domain: string;
    n_members?: number;
    confidence?: number;
    overridden?: boolean;
    is_coherent?: boolean;
}

export interface ClusterDetail extends ClusterSummary {
    coherence_reason?: string;
    description?: string;
    members?: string[];
    outliers?: string[];
    overridden_by?: string;
    overridden_at?: string;
}

export interface ClusterByTechResult {
    cluster_id: number;
    [key: string]: unknown;
}

// Shape của node/link dựng riêng cho ForceGraph2D ở ClusterDetailView (khác GraphNode/GraphLink của
// GraphExplorer — đây chỉ vẽ 1 cụm + các công nghệ thành viên, không phải toàn bộ Neo4j graph).
export interface ClusterGraphNode {
    id: string;
    name: string;
    isCenter: boolean;
    color: string;
    val: number;
}

// Chỉ khai báo `color` — `source`/`target` do LinkObject<N, L> (react-force-graph-2d) tự thêm dưới
// dạng `string | number | NodeObject<N>`: string lúc khởi tạo, rồi bị thư viện mutate thành tham
// chiếu node ngay khi simulation chạy. Khai báo lại 2 field này ở đây sẽ giao (intersect) với union
// đó và thu hẹp về đúng mỗi `string`, khiến paintLink không bao giờ narrow được sang NodeObject.
export interface ClusterGraphLink {
    color: string;
}
