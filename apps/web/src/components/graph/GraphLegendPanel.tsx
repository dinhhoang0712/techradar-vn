import { NODE_TYPES, COMMUNITY_PALETTE, OTHER_COMMUNITY_COLOR, OTHER_COMMUNITY_ID } from '../../utils/graphNodeTypes';
import { LINK_TYPE_COLORS, LINK_TYPE_LABELS } from '../../utils/graphLinkTypes';

interface GraphLegendPanelProps {
    hiddenTypes: Set<string>;
    onToggleNodeType: (type: string) => void;
    presentLinkTypes: string[];
    analyticsView?: boolean;
    communityCounts?: Map<number, number>;
}

// Chú giải thu gọn của canvas đồ thị: hướng dẫn dùng + toggle ẩn/hiện loại node + bảng màu quan hệ
// đang thực sự xuất hiện trong đồ thị hiện tại (không liệt kê hết mọi loại quan hệ có thể có), và
// (khi bật chế độ Phân tích đồ thị) bảng màu cộng đồng công nghệ đang thực sự xuất hiện.
export default function GraphLegendPanel({
    hiddenTypes, onToggleNodeType, presentLinkTypes, analyticsView, communityCounts,
}: GraphLegendPanelProps) {
    // Chỉ liệt kê cộng đồng thực sự có mặt trong đồ thị con đang xem, theo đúng thứ tự palette
    // (0..5) rồi tới "Khác" — không liệt kê cộng đồng rỗng cho đỡ rối.
    const presentCommunities = communityCounts
        ? COMMUNITY_PALETTE
            .map((color, id) => ({ id, color, count: communityCounts.get(id) || 0 }))
            .filter(c => c.count > 0)
        : [];
    const otherCount = communityCounts?.get(OTHER_COMMUNITY_ID) || 0;
    return (
        <div className="graph-legend-panel">
            <div className="legend-section">
                <span className="legend-section-title">Cách dùng</span>
                <ul className="legend-tips">
                    <li>Màu của node và cạnh nối thể hiện loại node/quan hệ — xem bảng màu bên dưới</li>
                    <li>Node có viền sáng nhấp nháy là node bạn đang xem hiện tại</li>
                    <li>Bấm vào 1 node để khám phá tiếp từ node đó</li>
                    <li>Kéo để di chuyển khung nhìn, cuộn chuột hoặc bấm +/− để phóng to/thu nhỏ</li>
                    <li>Bấm vào 1 cạnh nối để xem chi tiết mối quan hệ</li>
                    <li>Đường dẫn ở góc trên-trái (nếu có) để quay lại node đã xem trước đó</li>
                </ul>
            </div>
            <div className="legend-sep" />
            <div className="legend-section">
                <span className="legend-section-title">Loại node <em>(bấm để ẩn/hiện)</em></span>
                <div className="legend-grid">
                    {Object.entries(NODE_TYPES).map(([type, cfg]) => {
                        const isHidden = hiddenTypes.has(type);
                        return (
                            <button
                                type="button" key={type}
                                className={`legend-item legend-item-toggle${isHidden ? ' off' : ''}`}
                                aria-pressed={!isHidden}
                                onClick={() => onToggleNodeType(type)}
                            >
                                <span className="legend-dot" style={{ background: cfg.color }} />
                                {type.charAt(0).toUpperCase() + type.slice(1)}
                            </button>
                        );
                    })}
                </div>
            </div>
            {presentLinkTypes.length > 0 && (
                <>
                    <div className="legend-sep" />
                    <div className="legend-section">
                        <span className="legend-section-title">Loại quan hệ <em>(trong đồ thị đang xem)</em></span>
                        <div className="legend-grid">
                            {presentLinkTypes.map(type => (
                                <div key={type} className="legend-item">
                                    <span className="legend-line" style={{ background: LINK_TYPE_COLORS[type] }} />
                                    {LINK_TYPE_LABELS[type] || type}
                                </div>
                            ))}
                        </div>
                    </div>
                </>
            )}
            {analyticsView && (presentCommunities.length > 0 || otherCount > 0) && (
                <>
                    <div className="legend-sep" />
                    <div className="legend-section">
                        <span className="legend-section-title">Cộng đồng công nghệ <em>(Phân tích đồ thị)</em></span>
                        <p className="legend-hint">Kích cỡ node = mức độ trung tâm (PageRank) · màu = cộng đồng (Louvain)</p>
                        <div className="legend-grid">
                            {presentCommunities.map(c => (
                                <div key={c.id} className="legend-item">
                                    <span className="legend-dot" style={{ background: c.color }} />
                                    Nhóm {c.id + 1} ({c.count})
                                </div>
                            ))}
                            {otherCount > 0 && (
                                <div className="legend-item">
                                    <span className="legend-dot" style={{ background: OTHER_COMMUNITY_COLOR }} />
                                    Khác ({otherCount})
                                </div>
                            )}
                        </div>
                    </div>
                </>
            )}
        </div>
    );
}
