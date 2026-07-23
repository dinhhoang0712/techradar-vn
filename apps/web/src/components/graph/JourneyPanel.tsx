import { NODE_TYPES } from '../../utils/graphNodeTypes';
import { LINK_TYPE_COLORS, linkTypeLabel } from '../../utils/graphLinkTypes';
import type { GraphNode, GraphLink } from '../../utils/graphNormalize';

interface JourneyEndpoint {
    id: string;
    label?: string;
}

interface JourneyPanelProps {
    pathStart: JourneyEndpoint | null;
    pathEnd: JourneyEndpoint | null;
    activePath: { nodes: GraphNode[]; links: GraphLink[] };
    onClose: () => void;
}

// Panel liệt kê từng bước của lộ trình kết nối đã tìm được (tab Phân tích lộ trình).
export default function JourneyPanel({ pathStart, pathEnd, activePath, onClose }: JourneyPanelProps) {
    return (
        <div className="journey-panel card">
            <div className="jp-header">
                <h3>Lộ trình kết nối</h3>
                <button className="btn btn-ghost" aria-label="Đóng lộ trình" onClick={onClose}>✕</button>
            </div>
            <div className="jp-body">
                <div className="jp-summary">
                    Từ <b>{pathStart?.label || pathStart?.id}</b> đến <b>{pathEnd?.label || pathEnd?.id}</b>
                </div>
                <div className="jp-steps">
                    {activePath.links.map((link, i) => {
                        const source = activePath.nodes[i];
                        const target = activePath.nodes[i + 1];
                        return (
                            <div key={i} className="jp-step">
                                <div className="jp-step-node">
                                    <span className="jp-step-dot" style={{ background: NODE_TYPES[source.type]?.color }} />
                                    {source.label || source.id}
                                </div>
                                <div className="jp-step-link" style={{ borderLeftColor: LINK_TYPE_COLORS[link.type] }}>
                                    <span className="jp-step-label">{linkTypeLabel(link)}</span>
                                </div>
                                {i === activePath.links.length - 1 && (
                                    <div className="jp-step-node">
                                        <span className="jp-step-dot" style={{ background: NODE_TYPES[target.type]?.color }} />
                                        {target.label || target.id}
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
}
