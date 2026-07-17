import { useState, useEffect, useLayoutEffect, useCallback, useRef } from 'react';
import ForceGraph2D from 'react-force-graph-2d';
import { exploreGraph } from '../../api/graphService';

// Cùng bảng màu node với GraphExplorer.jsx để nhất quán trong toàn app.
const NODE_TYPES = {
    technology: { color: '#6C63FF', size: 9 },
    company: { color: '#FF6584', size: 13 },
    skill: { color: '#00D68F', size: 7 },
    location: { color: '#FFC94D', size: 11 },
    industry: { color: '#54C5F8', size: 11 },
    job: { color: '#FF9800', size: 11 },
};

const CANVAS_BG = '#060810';

// Mini "bản đồ liên kết" quanh 1 công ty — tái dùng endpoint GET /graph/explore đã có (tìm theo tên),
// không cần thêm API backend mới. Giới hạn đã biết: match theo company.name, không theo company.id,
// nên nếu tên không khớp tuyệt đối trong Neo4j, graph có thể rỗng.
export default function CompanyNeighborhoodGraph({ companyName, height = 320 }) {
    const fgRef = useRef();
    const wrapperRef = useRef(null);
    const [size, setSize] = useState({ width: 0, height: 0 });
    const [graphData, setGraphData] = useState({ nodes: [], links: [] });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);

    useLayoutEffect(() => {
        const el = wrapperRef.current;
        if (!el) return undefined;
        const update = () => setSize({ width: el.clientWidth, height: el.clientHeight });
        update();
        const ro = new ResizeObserver(update);
        ro.observe(el);
        return () => ro.disconnect();
    }, []);

    useEffect(() => {
        if (!companyName) return undefined;
        let cancelled = false;

        const fetchNeighborhood = () => {
            setLoading(true);
            setError(false);
            exploreGraph([companyName], 1)
                .then(res => {
                    if (cancelled) return;
                    const rawNodes = res?.data?.nodes || [];
                    const rawLinks = res?.data?.edges || res?.data?.links || [];
                    const nodes = rawNodes.map(n => ({
                        ...n,
                        id: n.id || n.keyword || n.name,
                        label: n.properties?.name || n.properties?.title || n.label || n.keyword || n.name || n.id,
                        type: ((n.labels && n.labels[0]) || n.type || n.category || 'technology').toLowerCase(),
                    }));
                    const links = rawLinks.map(l => ({
                        ...l,
                        source: l.source || l.source_id || l.from,
                        target: l.target || l.target_id || l.to,
                        type: (l.type || l.relation || 'RELATED_TO').toUpperCase(),
                    }));
                    setGraphData({ nodes, links });
                    setTimeout(() => { if (fgRef.current) fgRef.current.zoomToFit(400, 40); }, 300);
                })
                .catch(() => { if (!cancelled) setError(true); })
                .finally(() => { if (!cancelled) setLoading(false); });
        };
        fetchNeighborhood();
        return () => { cancelled = true; };
    }, [companyName]);

    useEffect(() => {
        if (fgRef.current) {
            fgRef.current.d3Force('link').distance(90);
            fgRef.current.d3Force('charge').strength(-180);
            fgRef.current.d3ReheatSimulation();
        }
    }, [graphData]);

    const paintNode = useCallback((node, ctx, globalScale) => {
        const nt = NODE_TYPES[node.type] || { color: '#9FA8C7', size: 7 };
        ctx.beginPath();
        ctx.arc(node.x, node.y, nt.size, 0, 2 * Math.PI);
        ctx.fillStyle = nt.color;
        ctx.fill();
        if (globalScale >= 0.8) {
            ctx.font = `600 ${Math.min(12 / globalScale, 11)}px Inter, sans-serif`;
            ctx.fillStyle = '#E8EAF6';
            ctx.textAlign = 'center';
            ctx.fillText(node.label || node.id, node.x, node.y + nt.size + 9);
        }
    }, []);

    return (
        <div className="company-neighborhood-graph" ref={wrapperRef} style={{ height }}>
            {loading && <div className="detail-loading"><div className="loading-spinner" /></div>}
            {!loading && error && <p className="company-empty-hint">Không thể tải bản đồ liên kết.</p>}
            {!loading && !error && graphData.nodes.length === 0 && (
                <p className="company-empty-hint">Không tìm thấy dữ liệu đồ thị cho công ty này.</p>
            )}
            {!loading && !error && graphData.nodes.length > 0 && size.width > 0 && (
                <ForceGraph2D
                    ref={fgRef}
                    width={size.width}
                    height={size.height}
                    graphData={graphData}
                    autoPauseRedraw={false}
                    nodeCanvasObject={paintNode}
                    nodeCanvasObjectMode={() => 'replace'}
                    linkColor={() => '#5c6494'}
                    linkWidth={1}
                    backgroundColor={CANVAS_BG}
                />
            )}
        </div>
    );
}
