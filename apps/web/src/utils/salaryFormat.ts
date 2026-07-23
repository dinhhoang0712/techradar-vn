// Màu/định dạng dùng chung giữa SalaryPage và SalaryDetailPanel — tách ra để 2 nơi luôn hiển thị
// đồng nhất (cùng ngưỡng màu theo tỉ lệ lương, cùng cách viết tắt "triệu").
export const SALARY_COLORS: string[] = ['#00d68f', '#54C5F8', '#6C63FF', '#ffc94d', '#FF6584'];

// Chia 3 mức lương (cao/vừa/thấp) theo tam phân vị của chính tập giá trị đang hiển thị,
// thay vì theo tỉ lệ cố định so với max — vì chart chỉ vẽ top-N (đã lọc sẵn nhóm lương cao),
// nên ngưỡng cố định theo % của max khiến mức "thấp" gần như không bao giờ xuất hiện.
export function salaryColor(value: number | undefined | null, sortedDesc: number[]): string {
    if (!value || sortedDesc.length === 0) return 'var(--text-3)';
    const n = sortedDesc.length;
    const highCut = sortedDesc[Math.max(0, Math.ceil(n / 3) - 1)];
    const midCut = sortedDesc[Math.max(0, Math.ceil((2 * n) / 3) - 1)];
    if (value >= highCut) return 'var(--green)';
    if (value >= midCut) return '#54C5F8';
    return 'var(--yellow)';
}

export function formatM(val: number | undefined | null): string {
    if (!val) return '—';
    return `${val.toFixed(1)}M`;
}
