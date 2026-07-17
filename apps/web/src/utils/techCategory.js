// Phân loại tên công nghệ thành nhóm để vẽ radar "Tech DNA" của công ty.
// Khớp theo từ khóa con (case-insensitive), tech rơi vào nhóm đầu tiên khớp theo thứ tự ưu tiên bên dưới.
const CATEGORY_KEYWORDS = [
    ['AI/ML', ['tensorflow', 'pytorch', 'machine learning', 'artificial intelligence', ' ai', 'ai ', 'ml', 'llm', 'nlp', 'opencv', 'keras', 'scikit', 'gpt', 'langchain', 'hugging face', 'computer vision', 'deep learning']],
    ['Data', ['sql', 'postgres', 'mysql', 'mongodb', 'redis', 'elasticsearch', 'kafka', 'spark', 'hadoop', 'airflow', 'data warehouse', 'etl', 'bigquery', 'snowflake', 'neo4j', 'cassandra', 'data']],
    ['Mobile', ['android', 'ios', 'flutter', 'react native', 'swift', 'kotlin', 'xamarin', 'mobile']],
    ['Frontend', ['react', 'vue', 'angular', 'javascript', 'typescript', 'html', 'css', 'next.js', 'nuxt', 'svelte', 'redux', 'tailwind', 'sass', 'webpack', 'frontend']],
    ['Infra/DevOps', ['docker', 'kubernetes', 'aws', 'azure', 'gcp', 'terraform', 'jenkins', 'ci/cd', 'devops', 'linux', 'nginx', 'ansible', 'cloud']],
    ['Backend', ['java', 'spring', 'node', 'python', 'django', 'flask', 'php', 'laravel', '.net', 'c#', 'golang', 'go', 'ruby', 'rails', 'express', 'nestjs', 'microservice', 'api', 'backend']],
];

const OTHER_CATEGORY = 'Khác';

export const CATEGORY_ORDER = [...CATEGORY_KEYWORDS.map(([name]) => name), OTHER_CATEGORY];

export function categorizeTech(techName) {
    const lower = ` ${(techName || '').toLowerCase()} `;
    for (const [category, keywords] of CATEGORY_KEYWORDS) {
        if (keywords.some(kw => lower.includes(kw))) return category;
    }
    return OTHER_CATEGORY;
}

// Trả về mảng [{ category, value }] theo đúng thứ tự CATEGORY_ORDER (kể cả 0) — để nhiều công ty
// overlay lên cùng 1 radar chart luôn dùng chung 1 bộ trục, so sánh được với nhau.
export function buildTechRadarData(techStack = []) {
    const counts = Object.fromEntries(CATEGORY_ORDER.map(c => [c, 0]));
    techStack.forEach(t => { counts[categorizeTech(t)] += 1; });
    return CATEGORY_ORDER.map(category => ({ category, value: counts[category] }));
}
