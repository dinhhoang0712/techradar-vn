import { NODE_TYPES, DEFAULT_NODE_TYPE } from '../../utils/graphNodeTypes';
import type { RawGraphNode } from '../../utils/graphNormalize';

interface BrowseResultsGridProps {
    results: RawGraphNode[];
    error: string;
    searched: boolean;
    loading: boolean;
    onResultClick: (node: RawGraphNode) => void;
}

// Lưới kết quả của tab "Duyệt bộ lọc" — bấm 1 thẻ để nhảy sang tab Khám phá tại node đó.
export default function BrowseResultsGrid({ results, error, searched, loading, onResultClick }: BrowseResultsGridProps) {
    return (
        <div className="graph-canvas-wrapper browse-results-wrapper">
            {error && <p style={{ color: 'var(--danger-light)', padding: 16 }}>{error}</p>}
            {!error && searched && !loading && results.length === 0 && (
                <p style={{ color: 'var(--text-3)', padding: 16 }}>Không tìm thấy node nào phù hợp.</p>
            )}
            {!searched && !loading && (
                <p style={{ color: 'var(--text-3)', padding: 16 }}>Chọn bộ lọc bên trái rồi bấm "Áp dụng bộ lọc".</p>
            )}
            <div className="browse-results-grid">
                {results.map(node => {
                    const nt = NODE_TYPES[String(node.type || '').toLowerCase()] || DEFAULT_NODE_TYPE;
                    return (
                        <button
                            type="button"
                            key={node.id}
                            className="browse-result-card"
                            style={{ borderLeftColor: nt.color }}
                            onClick={() => onResultClick(node)}
                        >
                            <span className="browse-result-type" style={{ background: nt.color + '22', color: nt.color }}>{node.type}</span>
                            <strong className="browse-result-name">{node.name || node.properties?.title || '(không tên)'}</strong>
                            {node.properties?.salary != null && <span className="browse-result-meta">💰 {String(node.properties.salary)}</span>}
                            {node.properties?.location != null && <span className="browse-result-meta">📍 {String(node.properties.location)}</span>}
                        </button>
                    );
                })}
            </div>
        </div>
    );
}
