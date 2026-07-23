import { useMemo } from 'react';

interface NodeLike {
    id: string;
    label?: string;
}

/**
 * Gợi ý tối đa 5 node khớp với `query` trong danh sách `nodes` — dùng chung cho ô search
 * "Khám phá" và 2 ô "Xuất phát/Điểm đến" ở tab Phân tích lộ trình.
 * `activeLabel` là nhãn của node đang được chọn hiện tại: nếu query khớp y hệt (đã chọn xong),
 * ẩn gợi ý thay vì hiện lại chính node vừa chọn.
 */
export function useNodeSuggest<T extends NodeLike>(query: string, nodes: T[], activeLabel?: string | null): T[] {
    return useMemo(() => {
        if (query.length === 0) return [];
        if (activeLabel && query.trim().toLowerCase() === activeLabel.toLowerCase()) return [];
        const lower = query.toLowerCase();
        return nodes.filter(n => (n.label || n.id).toLowerCase().includes(lower)).slice(0, 5);
    }, [query, nodes, activeLabel]);
}
