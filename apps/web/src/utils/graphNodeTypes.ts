// Bảng màu/kích cỡ node dùng chung cho các đồ thị quan hệ (GraphExplorer, CompanyNeighborhoodGraph, ...)
// để mọi nơi vẽ node cùng loại (technology/company/skill/...) luôn đồng nhất, tránh mỗi nơi một bản
// copy rồi lệch nhau theo thời gian.
export interface NodeTypeStyle {
    color: string;
    size: number;
}

export const NODE_TYPES: Record<string, NodeTypeStyle> = {
    technology: { color: '#6C63FF', size: 10 },
    company: { color: '#FF6584', size: 14 },
    skill: { color: '#00D68F', size: 8 },
    location: { color: '#FFC94D', size: 12 },
    industry: { color: '#54C5F8', size: 12 },
    job: { color: '#FF9800', size: 12 },
};

// Style fallback cho type không nằm trong NODE_TYPES ở trên.
export const DEFAULT_NODE_TYPE: NodeTypeStyle = { color: '#9FA8C7', size: 8 };

// Bảng màu "cộng đồng công nghệ" (Louvain community, xem Neo4jGraphAnalyticsAdapter phía backend)
// dùng cho chế độ Phân tích đồ thị trên GraphExplorer — chỉ áp dụng cho node loại `technology`.
// 6 màu này đã chạy qua validator OKLCH/CVD của bộ dataviz skill (all-pairs, nền tối #060810,
// PASS ở mọi check trừ CVD nằm trong dải floor 6-8 — hợp lệ khi có kênh phụ, tức luôn kèm nhãn
// chữ ở legend/tooltip, không chỉ dựa vào màu) và được chọn để tách biệt rõ với 6 màu NODE_TYPES
// ở trên (không trùng vùng hue với technology/company/skill/location/industry/job).
// index = Technology.community_id (0..5); community_id ngoài khoảng này (long-tail, "khác") dùng
// OTHER_COMMUNITY_COLOR.
export const COMMUNITY_PALETTE: string[] = [
    '#ca4b23', // 0 — cam đất
    '#9e9800', // 1 — vàng ô liu
    '#008a6b', // 2 — xanh ngọc
    '#3c8ff4', // 3 — xanh dương
    '#7a4aba', // 4 — tím
    '#df539f', // 5 — hồng magenta
];

// community_id backend gán cho nhóm "khác" (long-tail, ngoài 6 cộng đồng lớn nhất) — xem
// Neo4jGraphAnalyticsAdapter.OTHER_COMMUNITY.
export const OTHER_COMMUNITY_ID = 99;
export const OTHER_COMMUNITY_COLOR = '#5b6472';
