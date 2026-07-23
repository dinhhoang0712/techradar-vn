// Bảng màu/nhãn quan hệ dùng chung cho các đồ thị tri thức (GraphExplorer + các panel con) để
// mọi nơi hiển thị cùng 1 loại quan hệ luôn đồng nhất, tránh mỗi nơi một bản copy rồi lệch nhau.
export const PATH_HIGHLIGHT_COLOR = '#FFD700';

// Danh sách đầy đủ các loại quan hệ thực tế có trong dữ liệu Neo4j của backend (đối chiếu trực tiếp
// với code — không đoán): USES, REQUIRES, RELATED_TO, MENTIONS, POSTED_BY, HIRES_FOR là các loại đang
// được ghi/dùng; IS_TECHNOLOGY, LEADS_TO, IN_RING là loại cũ/khác module nhưng vẫn có thể xuất hiện
// khi truy vấn đồ thị chung nên vẫn cần màu + nhãn để không rơi về tiếng Anh mặc định.
export const LINK_TYPE_COLORS: Record<string, string> = {
    USES: '#6C63FF',
    REQUIRES: '#00D68F',
    RELATED_TO: '#FF6584',
    MENTIONS: '#54C5F8',
    POSTED_BY: '#FFC94D',
    HIRES_FOR: '#FF9800',
    IS_TECHNOLOGY: '#9b8cff',
    LEADS_TO: '#4f9dff',
    IN_RING: '#f6b93b',
};

// Nhãn quan hệ hiển thị cho người dùng — backend thường trả tên quan hệ bằng tiếng Anh (uses,
// requires...), nên luôn dịch theo `type` thay vì hiện thẳng label thô từ backend.
export const LINK_TYPE_LABELS: Record<string, string> = {
    USES: 'Sử dụng',
    REQUIRES: 'Yêu cầu',
    RELATED_TO: 'Liên quan',
    MENTIONS: 'Đề cập',
    POSTED_BY: 'Đăng bởi',
    HIRES_FOR: 'Tuyển cho',
    IS_TECHNOLOGY: 'Thuộc công nghệ',
    LEADS_TO: 'Dẫn đến',
    IN_RING: 'Cùng nhóm',
};

interface LinkLike {
    type?: string;
    label?: string;
}

export const linkTypeLabel = (link?: LinkLike | null): string =>
    (link?.type && LINK_TYPE_LABELS[link.type]) || link?.label || link?.type || '';

// Nhãn tiếng Việt cho các property backend gắn trực tiếp lên quan hệ (rel.asMap() ở
// Neo4jGraphRepository trả nguyên map, không cố định danh sách key) — khoá lạ chưa có nhãn thì
// hiện tên gốc đã tách dấu gạch dưới, không ẩn đi, để không mất thông tin khi có property mới.
export const EDGE_PROPERTY_LABELS: Record<string, string> = {
    evidence_count: 'Số lần ghi nhận',
    co_mention_count: 'Số lần cùng xuất hiện',
    first_seen: 'Lần đầu ghi nhận',
    last_updated: 'Cập nhật gần nhất',
    sentiment_score: 'Điểm cảm xúc',
    salary: 'Mức lương',
    location: 'Địa điểm',
};

export const edgePropertyLabel = (key: string): string => EDGE_PROPERTY_LABELS[key]
    || key.replace(/_/g, ' ').replace(/^./, c => c.toUpperCase());

export const formatEdgePropertyValue = (value: unknown): string => {
    if (typeof value === 'number') return Number.isInteger(value) ? value.toLocaleString() : value.toFixed(2);
    return String(value);
};
