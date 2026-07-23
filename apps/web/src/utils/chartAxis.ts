// Các hàm tính toán domain trục Y cho biểu đồ tăng trưởng theo % (growth chart),
// dùng chung giữa TrendDashboard và ComparePage để tránh lệch logic giữa 2 nơi.
export function roundAxisLimit(value: number): number {
    if (value <= 100) return 100;
    if (value <= 250) return Math.ceil(value / 50) * 50;
    return Math.ceil(value / 100) * 100;
}

export function getAxisLimit(value: number): number {
    if (value < 99) return 100;
    return roundAxisLimit(value * 1.15);
}

export function getPercentAxisDomain(
    data: Record<string, unknown>[],
    keys: string[],
): [number, number] {
    const values = data.flatMap(row => keys.map(key => Number(row[key] || 0)));
    const maxGrowth = Math.max(0, ...values);
    const minGrowth = Math.min(0, ...values);
    const maxValue = getAxisLimit(maxGrowth);
    const minValue = -getAxisLimit(Math.abs(minGrowth));

    return [minValue, maxValue];
}
