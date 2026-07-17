"""
Shared technology keyword dictionary — dùng để trích tech entity bằng
keyword-matching khi chưa có NLP service (hoặc NLP service bỏ sót).

Danh sách này song song với TECH_KEYWORDS trong
apps/backend .../features/kafka/EntityExtractionService.java — mở rộng
một bên thì nên mở rộng bên kia để Technology node không bị phân mảnh
tên (vd "Golang" ở Python và "Go" ở Java tạo hai node khác nhau).
"""
import re

TECH_KEYWORDS = [
    # Ngôn ngữ
    "Python", "Java", "JavaScript", "TypeScript", "Golang", "Go", "Rust",
    "C++", "C#", "PHP", "Ruby", "Swift", "Kotlin", "Scala", "Dart", "Perl",
    "Elixir", "Haskell",
    # Frontend
    "React", "Vue", "Angular", "Svelte", "Next.js", "Nuxt", "jQuery",
    "Tailwind", "Bootstrap", "Webpack", "Vite", "HTML", "CSS", "Sass",
    # Backend / framework
    "Spring Boot", "Spring", "Django", "Flask", "FastAPI", "Node.js",
    "Express", "Laravel", "Rails", ".NET", "ASP.NET", "NestJS",
    # Mobile
    "Flutter", "React Native", "Android", "iOS", "Xamarin",
    # Data / AI
    "AI", "ML", "NLP", "RPA", "Machine Learning", "Deep Learning",
    "Computer Vision", "Big Data", "Data Science", "LLM", "GPT", "ChatGPT",
    "Gemini", "Claude", "TensorFlow", "PyTorch", "Keras", "Hadoop", "Spark",
    "Flink", "Airflow", "dbt", "Databricks", "Snowflake", "Power BI",
    "Tableau", "BigQuery",
    # Database
    "SQL", "NoSQL", "PostgreSQL", "MySQL", "MongoDB", "Redis", "Neo4j",
    "Qdrant", "Elasticsearch", "Cassandra", "SQLite", "Oracle", "MariaDB",
    "GraphQL",
    # Cloud / DevOps
    "AWS", "GCP", "Azure", "Docker", "Kubernetes", "CI/CD", "DevOps",
    "Terraform", "Ansible", "Jenkins", "GitLab", "GitHub Actions",
    "Prometheus", "Grafana",
    # Messaging / infra
    "Kafka", "RabbitMQ", "gRPC", "Microservices", "WebSocket",
    # Bảo mật / công nghệ mới
    "Blockchain", "Web3", "Solidity", "IoT", "AR", "VR", "Cybersecurity",
    "5G", "Semiconductor",
]

# Cụm từ tiếng Việt phổ biến trong tin tức công nghệ VN -> tên canonical.
VN_TECH_ALIASES = {
    "trí tuệ nhân tạo": "AI",
    "học máy": "Machine Learning",
    "học sâu": "Deep Learning",
    "dữ liệu lớn": "Big Data",
    "an ninh mạng": "Cybersecurity",
    "bảo mật mạng": "Cybersecurity",
    "chuyển đổi số": "Digital Transformation",
    "bán dẫn": "Semiconductor",
    "chip bán dẫn": "Semiconductor",
    "vi mạch": "Semiconductor",
    "điện toán đám mây": "Cloud",
    "chuỗi khối": "Blockchain",
    "thực tế ảo": "VR",
    "thực tế tăng cường": "AR",
    "vạn vật kết nối": "IoT",
    "internet vạn vật": "IoT",
}

_CANONICAL_BY_LOWER = {kw.lower(): kw for kw in TECH_KEYWORDS}
_CANONICAL_BY_LOWER.update({alias.lower(): name for alias, name in VN_TECH_ALIASES.items()})

_ALL_TERMS_BY_LENGTH = sorted(_CANONICAL_BY_LOWER.keys(), key=len, reverse=True)
# (?<!\w) / (?!\w) thay cho \b: \b không match đúng ở các term kết thúc bằng
# ký tự không phải chữ/số (C++, C#, .NET, CI/CD) vì \b đòi hỏi ranh giới
# \w<->\W, còn ".NET " (theo sau là khoảng trắng) là \W<->\W nên \b sẽ fail.
_PATTERN = re.compile(
    r"(?<!\w)(" + "|".join(re.escape(term) for term in _ALL_TERMS_BY_LENGTH) + r")(?!\w)",
    re.IGNORECASE | re.UNICODE,
)


def extract_tech(text: str) -> list[str]:
    """Trích tên công nghệ từ text bằng keyword matching, trả về tên canonical (dedup)."""
    if not text:
        return []
    found = set()
    for match in _PATTERN.finditer(text):
        canonical = _CANONICAL_BY_LOWER.get(match.group(0).lower())
        if canonical:
            found.add(canonical)
    return sorted(found)
