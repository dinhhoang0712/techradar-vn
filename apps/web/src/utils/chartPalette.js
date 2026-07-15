// Bảng màu dùng chung cho các biểu đồ/đồ thị phân loại (categorical) trong toàn app,
// để Radar/Compare/Cluster/Graph không mỗi trang một bảng màu khác nhau.
export const CHART_PALETTE = [
    '#6C63FF', '#00D68F', '#FF6584', '#FFC94D', '#54C5F8',
    '#FF8C00', '#7FBA00', '#E040FB', '#FF5252', '#00B4D8',
    '#9B59B6', '#1ABC9C', '#F1C40F', '#3498DB', '#E67E22',
];

export function colorForIndex(index) {
    return CHART_PALETTE[index % CHART_PALETTE.length];
}
