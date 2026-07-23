// Màu/định dạng dùng chung giữa SalaryPage và SalaryDetailPanel — tách ra để 2 nơi luôn hiển thị
// đồng nhất (cùng ngưỡng màu theo tỉ lệ lương, cùng cách viết tắt "triệu").
export const SALARY_COLORS: string[] = ['#00d68f', '#54C5F8', '#6C63FF', '#ffc94d', '#FF6584'];

export function salaryColor(value: number | undefined | null, max: number | undefined | null): string {
    if (!value || !max) return 'var(--text-3)';
    const ratio = value / max;
    if (ratio > 0.75) return 'var(--green)';
    if (ratio > 0.45) return '#54C5F8';
    return 'var(--yellow)';
}

export function formatM(val: number | undefined | null): string {
    if (!val) return '—';
    return `${val.toFixed(1)}M`;
}
