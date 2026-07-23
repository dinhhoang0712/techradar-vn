import { LINK_TYPE_COLORS, LINK_TYPE_LABELS, linkTypeLabel, edgePropertyLabel, formatEdgePropertyValue } from '../../utils/graphLinkTypes';

interface ResolvedEndpoint {
    id: string;
    label?: string;
}

// Sau khi ForceGraph2D xử lý, source/target của link được thay bằng chính node object (không còn
// là id thô nữa) — khác với GraphLink gốc (utils/graphNormalize.ts) chỉ có id dạng string.
export interface ResolvedGraphLink {
    source: string | ResolvedEndpoint;
    target: string | ResolvedEndpoint;
    type: string;
    label?: string;
    properties?: Record<string, unknown>;
}

const edgeEndpointLabel = (endpoint: string | ResolvedEndpoint): string =>
    (typeof endpoint === 'object' ? (endpoint?.label || endpoint?.id) : endpoint);

// Panel chi tiết 1 quan hệ (cạnh nối) khi bấm vào cạnh trên canvas — hiện câu mô tả + toàn bộ
// property backend gắn trên quan hệ đó (rel.asMap()).
export default function EdgeDetailPanel({ edge, onClose }: { edge: ResolvedGraphLink; onClose: () => void }) {
    const sourceLabel = edgeEndpointLabel(edge.source);
    const targetLabel = edgeEndpointLabel(edge.target);

    return (
        <div className="edge-panel card">
            <div className="ep-header">
                <h3>Chi tiết mối quan hệ</h3>
                <button className="btn btn-ghost" aria-label="Đóng chi tiết quan hệ" onClick={onClose}>✕</button>
            </div>
            <div className="ep-body">
                {sourceLabel && targetLabel && (
                    <p className="ep-sentence">
                        <b>{sourceLabel}</b> <span style={{ color: LINK_TYPE_COLORS[edge.type] }}>{linkTypeLabel(edge).toLowerCase()}</span> <b>{targetLabel}</b>
                    </p>
                )}
                <div className="ep-row">
                    <span className="ep-label">Loại quan hệ</span>
                    <span className="ep-type" style={{ color: LINK_TYPE_COLORS[edge.type] }}>{LINK_TYPE_LABELS[edge.type] || edge.type}</span>
                </div>
                {Object.entries(edge.properties || {}).map(([key, value]) => (
                    value != null && (
                        <div className="ep-row" key={key}>
                            <span className="ep-label">{edgePropertyLabel(key)}</span>
                            <span className="ep-value">{formatEdgePropertyValue(value)}</span>
                        </div>
                    )
                ))}
            </div>
        </div>
    );
}
