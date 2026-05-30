// Node types
export const NODE_TYPES = {
    technology: { color: '#6C63FF', size: 18 },
    company: { color: '#FF6584', size: 15 },
    skill: { color: '#00D68F', size: 13.5 },
    location: { color: '#FFC94D', size: 12 },
    job: { color: '#54C5F8', size: 10.5 },
};

export const ALL_NODES = [
    // Technologies
    { id: 'tech_golang', label: 'Golang', type: 'technology', jobs: 1680, avg_salary: 42, sentiment: 0.82, growth: 38 },
    { id: 'tech_react', label: 'React', type: 'technology', jobs: 3200, avg_salary: 35, sentiment: 0.73, growth: 12 },
    { id: 'tech_python', label: 'Python', type: 'technology', jobs: 2900, avg_salary: 38, sentiment: 0.80, growth: 44 },
    { id: 'tech_rust', label: 'Rust', type: 'technology', jobs: 420, avg_salary: 55, sentiment: 0.91, growth: 72 },
    { id: 'tech_nodejs', label: 'Node.js', type: 'technology', jobs: 2100, avg_salary: 32, sentiment: 0.70, growth: 18 },
    { id: 'tech_aiml', label: 'AI/ML', type: 'technology', jobs: 1800, avg_salary: 50, sentiment: 0.87, growth: 65 },
    { id: 'tech_k8s', label: 'Kubernetes', type: 'technology', jobs: 1100, avg_salary: 48, sentiment: 0.79, growth: 50 },
    { id: 'tech_typescript', label: 'TypeScript', type: 'technology', jobs: 2500, avg_salary: 36, sentiment: 0.81, growth: 30 },
    { id: 'tech_aws', label: 'AWS', type: 'technology', jobs: 2200, avg_salary: 45, sentiment: 0.78, growth: 28 },
    { id: 'tech_docker', label: 'Docker', type: 'technology', jobs: 1700, avg_salary: 40, sentiment: 0.77, growth: 22 },

    // Companies
    { id: 'co_vng', label: 'VNG', type: 'company', location: 'Hồ Chí Minh', size: 'large', avg_salary: 45 },
    { id: 'co_fpt', label: 'FPT Software', type: 'company', location: 'Hà Nội', size: 'large', avg_salary: 38 },
    { id: 'co_momo', label: 'MoMo', type: 'company', location: 'Hồ Chí Minh', size: 'medium', avg_salary: 50 },
    { id: 'co_tiki', label: 'Tiki', type: 'company', location: 'Hồ Chí Minh', size: 'large', avg_salary: 48 },
    { id: 'co_grab', label: 'Grab Vietnam', type: 'company', location: 'Hồ Chí Minh', size: 'large', avg_salary: 60 },
    { id: 'co_shopee', label: 'Shopee VN', type: 'company', location: 'Hồ Chí Minh', size: 'large', avg_salary: 55 },
    { id: 'co_zalo', label: 'Zalo', type: 'company', location: 'Hồ Chí Minh', size: 'medium', avg_salary: 46 },
    { id: 'co_techcom', label: 'Techcombank', type: 'company', location: 'Hà Nội', size: 'large', avg_salary: 42 },
    { id: 'co_sun', label: 'Sun Asterisk', type: 'company', location: 'Hà Nội', size: 'medium', avg_salary: 35 },
    { id: 'co_topdev', label: 'TopDev', type: 'company', location: 'Hồ Chí Minh', size: 'small', avg_salary: 33 },

    // Skills
    { id: 'sk_docker', label: 'Docker', type: 'skill', category: 'DevOps' },
    { id: 'sk_k8s', label: 'Kubernetes', type: 'skill', category: 'DevOps' },
    { id: 'sk_grpc', label: 'gRPC', type: 'skill', category: 'Backend' },
    { id: 'sk_microserv', label: 'Microservices', type: 'skill', category: 'Architecture' },
    { id: 'sk_kafka', label: 'Apache Kafka', type: 'skill', category: 'Distributed' },
    { id: 'sk_redis', label: 'Redis', type: 'skill', category: 'Database' },
    { id: 'sk_postgres', label: 'PostgreSQL', type: 'skill', category: 'Database' },
    { id: 'sk_tensorflow', label: 'TensorFlow', type: 'skill', category: 'AI' },
    { id: 'sk_pytorch', label: 'PyTorch', type: 'skill', category: 'AI' },
    { id: 'sk_langchain', label: 'LangChain', type: 'skill', category: 'AI' },
    { id: 'sk_react', label: 'React', type: 'skill', category: 'Frontend' },
    { id: 'sk_typescript', label: 'TypeScript', type: 'skill', category: 'Frontend' },
    { id: 'sk_aws', label: 'AWS', type: 'skill', category: 'Cloud' },
    { id: 'sk_ci', label: 'CI/CD', type: 'skill', category: 'DevOps' },
    { id: 'sk_git', label: 'Git', type: 'skill', category: 'General' },

    // Locations
    { id: 'loc_hcm', label: 'Hồ Chí Minh', type: 'location', jobs: 6800 },
    { id: 'loc_hn', label: 'Hà Nội', type: 'location', jobs: 4200 },
    { id: 'loc_dn', label: 'Đà Nẵng', type: 'location', jobs: 980 },
];

export const ALL_LINKS = [
    // Technologies (Thêm một số node mới)
    { id: 'tech_grpc', label: 'gRPC', type: 'technology', jobs: 520, avg_salary: 45, sentiment: 0.85, growth: 25 },
    { id: 'tech_postgresql', label: 'PostgreSQL', type: 'technology', jobs: 1900, avg_salary: 34, sentiment: 0.88, growth: 10 },

    // Company USES Technology
    { source: 'co_vng', target: 'tech_golang', type: 'USES', job_count: 45, period: '6 tháng', label: 'VNG vận hành hệ thống Zalo bằng Golang' },
    { source: 'co_vng', target: 'tech_aiml', type: 'USES', job_count: 31, period: '6 tháng', label: 'VNG ứng dụng AI trong xử lý hình ảnh' },
    { source: 'co_vng', target: 'tech_k8s', type: 'USES', job_count: 22, period: '6 tháng', label: 'VNG sử dụng K8s cho hạ tầng cloud' },
    { source: 'co_grab', target: 'tech_golang', type: 'USES', job_count: 62, period: '6 tháng', label: 'Grab Backend chủ chốt dùng Golang' },
    { source: 'co_grab', target: 'tech_aiml', type: 'USES', job_count: 48, period: '6 tháng', label: 'Grab dùng AI tối ưu hóa lộ trình' },
    { source: 'co_grab', target: 'tech_python', type: 'USES', job_count: 55, period: '6 tháng', label: 'Grab dùng Python cho Data Science' },
    { source: 'co_momo', target: 'tech_golang', type: 'USES', job_count: 38, period: '6 tháng', label: 'MoMo dùng Golang cho hệ thống thanh toán' },
    { source: 'co_momo', target: 'tech_react', type: 'USES', job_count: 25, period: '6 tháng', label: 'MoMo dùng React cho ứng dụng Web' },
    { source: 'co_tiki', target: 'tech_python', type: 'USES', job_count: 40, period: '6 tháng', label: 'Tiki triển khai Search Engine với Python' },
    { source: 'co_tiki', target: 'tech_react', type: 'USES', job_count: 33, period: '6 tháng', label: 'Tiki dùng React cho Storefront' },
    { source: 'co_shopee', target: 'tech_golang', type: 'USES', job_count: 70, period: '6 tháng', label: 'Shopee dùng Golang xử lý high-traffic' },
    { source: 'co_shopee', target: 'tech_typescript', type: 'USES', job_count: 41, period: '6 tháng', label: 'Shopee dùng TypeScript cho Frontend' },
    { source: 'co_fpt', target: 'tech_nodejs', type: 'USES', job_count: 58, period: '6 tháng', label: 'FPT Software cung cấp giải pháp Node.js' },
    { source: 'co_fpt', target: 'tech_react', type: 'USES', job_count: 72, period: '6 tháng', label: 'FPT F-Software chuyên về React projects' },
    { source: 'co_zalo', target: 'tech_golang', type: 'USES', job_count: 30, period: '6 tháng', label: 'Zalo Backend tối ưu với Golang' },
    { source: 'co_techcom', target: 'tech_aiml', type: 'USES', job_count: 20, period: '6 tháng', label: 'Techcombank dùng AI chấm điểm tín dụng' },
    { source: 'co_techcom', target: 'tech_docker', type: 'USES', job_count: 15, period: '6 tháng', label: 'Techcombank áp dụng Docker vào Microservices' },
    { source: 'co_sun', target: 'tech_react', type: 'USES', job_count: 44, period: '6 tháng', label: 'Sun* outsource nhiều dự án React' },

    // Technology REQUIRES/USES Technology or Skill
    { source: 'tech_golang', target: 'tech_grpc', type: 'REQUIRES', label: 'Golang là ngôn ngữ chính cho gRPC' },
    { source: 'tech_golang', target: 'sk_docker', type: 'REQUIRES', weight: 0.85, label: 'Golang thường Deploy với Docker' },
    { source: 'tech_golang', target: 'sk_k8s', type: 'REQUIRES', weight: 0.78, label: 'Golang chạy tốt trên Kubernetes' },
    { source: 'tech_golang', target: 'sk_grpc', type: 'REQUIRES', weight: 0.72, label: 'Golang cung cấp hiệu năng cao cho gRPC' },
    { source: 'tech_golang', target: 'sk_microserv', type: 'REQUIRES', weight: 0.80, label: 'Golang phù hợp nhất cho Microservices' },
    { source: 'tech_golang', target: 'tech_postgresql', type: 'USES', weight: 0.65, label: 'Golang thường đi kèm PostgreSQL' },
    { source: 'tech_python', target: 'sk_tensorflow', type: 'REQUIRES', weight: 0.70, label: 'Python là nền tảng của TensorFlow' },
    { source: 'tech_python', target: 'sk_pytorch', type: 'REQUIRES', weight: 0.75, label: 'Python hỗ trợ mạnh mẽ cho PyTorch' },
    { source: 'tech_python', target: 'sk_langchain', type: 'REQUIRES', weight: 0.60, label: 'Python dùng làm nền cho LangChain (LLM)' },
    { source: 'tech_python', target: 'sk_redis', type: 'REQUIRES', weight: 0.55, label: 'Python dễ dàng tích hợp với Redis' },
    { source: 'tech_react', target: 'sk_typescript', type: 'REQUIRES', weight: 0.88, label: 'React khuyến khích dùng TypeScript' },
    { source: 'tech_react', target: 'sk_git', type: 'REQUIRES', weight: 0.92, label: 'React projects cần quản lý qua Git' },
    { source: 'tech_aiml', target: 'sk_python', type: 'REQUIRES', weight: 0.95, label: 'AI/ML 90% các dự án dùng Python' },
    { source: 'tech_aiml', target: 'sk_tensorflow', type: 'REQUIRES', weight: 0.80, label: 'AI/ML sử dụng TensorFlow core' },
    { source: 'tech_aiml', target: 'sk_langchain', type: 'REQUIRES', weight: 0.70, label: 'AI/ML hiện đại tích hợp LangChain' },
    { source: 'tech_k8s', target: 'sk_docker', type: 'REQUIRES', weight: 0.95, label: 'Kubernetes dàn trận cho các Docker Container' },
    { source: 'tech_k8s', target: 'sk_ci', type: 'REQUIRES', weight: 0.82, label: 'Kubernetes là đích đến của CI/CD flow' },
    { source: 'tech_aws', target: 'sk_k8s', type: 'REQUIRES', weight: 0.65, label: 'AWS EKS là dịch vụ K8s phổ biến' },
    { source: 'tech_aws', target: 'sk_ci', type: 'REQUIRES', weight: 0.75, label: 'AWS CodePipeline cho CI/CD' },
    { source: 'tech_docker', target: 'sk_ci', type: 'REQUIRES', weight: 0.80, label: 'Docker giúp CI/CD nhất quán' },
    { source: 'tech_nodejs', target: 'sk_typescript', type: 'REQUIRES', weight: 0.78, label: 'Node.js Backend hiện đại dùng TS' },
    { source: 'tech_nodejs', target: 'sk_redis', type: 'REQUIRES', weight: 0.60, label: 'Node.js tích hợp Redis cho caching' },
    { source: 'tech_typescript', target: 'sk_react', type: 'REQUIRES', weight: 0.82, label: 'TypeScript mang lại type-safety cho React' },

    // Company LOCATED_IN Location
    { source: 'co_vng', target: 'loc_hcm', type: 'LOCATED_IN', label: 'VNG tại TP.HCM' },
    { source: 'co_grab', target: 'loc_hcm', type: 'LOCATED_IN', label: 'Grab tại TP.HCM' },
    { source: 'co_momo', target: 'loc_hcm', type: 'LOCATED_IN', label: 'MoMo tại TP.HCM' },
    { source: 'co_tiki', target: 'loc_hcm', type: 'LOCATED_IN', label: 'Tiki tại TP.HCM' },
    { source: 'co_shopee', target: 'loc_hcm', type: 'LOCATED_IN', label: 'Shopee tại TP.HCM' },
    { source: 'co_zalo', target: 'loc_hcm', type: 'LOCATED_IN', label: 'Zalo tại TP.HCM' },
    { source: 'co_topdev', target: 'loc_hcm', type: 'LOCATED_IN', label: 'TopDev tại TP.HCM' },
    { source: 'co_fpt', target: 'loc_hn', type: 'LOCATED_IN', label: 'FPT tại Hà Nội' },
    { source: 'co_techcom', target: 'loc_hn', type: 'LOCATED_IN', label: 'Techcombank tại Hà Nội' },
    { source: 'co_sun', target: 'loc_hn', type: 'LOCATED_IN', label: 'Sun Asterisk tại Hà Nội' },

    // Skill RELATED_TO Technology
    { source: 'sk_kafka', target: 'tech_golang', type: 'RELATED_TO', similarity: 0.65, label: 'Kafka liên quan Golang' },
    { source: 'sk_kafka', target: 'tech_python', type: 'RELATED_TO', similarity: 0.60, label: 'Kafka liên quan Python' },
    { source: 'sk_redis', target: 'tech_golang', type: 'RELATED_TO', similarity: 0.70, label: 'Redis liên quan Golang' },
];

// Index by id for fast lookup
export const nodeIndex = {};
ALL_NODES.forEach(n => { nodeIndex[n.id] = n; });

// Get 1-hop neighbors
export function getNeighbors(nodeId) {
    const neighborIds = new Set();
    const edgesOut = ALL_LINKS.filter(l => l.source === nodeId);
    const edgesIn = ALL_LINKS.filter(l => l.target === nodeId);
    edgesOut.forEach(l => neighborIds.add(l.target));
    edgesIn.forEach(l => neighborIds.add(l.source));
    return {
        nodes: [...neighborIds].map(id => nodeIndex[id]).filter(Boolean),
        links: [...edgesOut, ...edgesIn],
    };
}

// Get subgraph up to `depth` hops from startId
export function buildSubgraph(startId, depth = 1, maxNodes = 80) {
    const visitedNodes = new Set([startId]);
    const visitedLinks = new Set();
    let frontier = [startId];

    for (let d = 0; d < depth; d++) {
        const nextFrontier = [];
        for (const id of frontier) {
            if (visitedNodes.size >= maxNodes) break;
            const { nodes, links } = getNeighbors(id);
            nodes.forEach(n => {
                if (!visitedNodes.has(n.id)) {
                    visitedNodes.add(n.id);
                    nextFrontier.push(n.id);
                }
            });
            links.forEach(l => {
                const key = `${l.source}-${l.target}-${l.type}`;
                visitedLinks.add(key);
            });
        }
        frontier = nextFrontier;
    }

    const nodes = [...visitedNodes].map(id => nodeIndex[id]).filter(Boolean);
    const links = ALL_LINKS.filter(l => {
        const key = `${l.source}-${l.target}-${l.type}`;
        return visitedLinks.has(key) && visitedNodes.has(l.source) && visitedNodes.has(l.target);
    });

    return { nodes, links };
}

// BFS pathfinding to find shortest path between two nodes
export function findPath(startId, endId) {
    if (!nodeIndex[startId] || !nodeIndex[endId]) return null;
    if (startId === endId) return { nodes: [nodeIndex[startId]], links: [] };

    const queue = [[startId]];
    const visited = new Set([startId]);

    while (queue.length > 0) {
        const path = queue.shift();
        const lastNodeId = path[path.length - 1];

        if (lastNodeId === endId) {
            const nodes = path.map(id => nodeIndex[id]);
            const pathLinks = [];
            for (let i = 0; i < path.length - 1; i++) {
                const s = path[i];
                const t = path[i + 1];
                // Find link in either direction
                const link = ALL_LINKS.find(l =>
                    (l.source === s && l.target === t) || (l.source === t && l.target === s)
                );
                if (link) pathLinks.push(link);
            }
            return { nodes, links: pathLinks };
        }

        const { nodes: neighbors } = getNeighbors(lastNodeId);
        for (const neighbor of neighbors) {
            if (!visited.has(neighbor.id)) {
                visited.add(neighbor.id);
                queue.push([...path, neighbor.id]);
            }
        }
    }
    return null;
}

// Autocomplete search
export function searchNodes(query) {
    const q = query.toLowerCase();
    return ALL_NODES.filter(n => n.label.toLowerCase().includes(q)).slice(0, 10);
}
