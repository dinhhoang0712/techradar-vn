"""
==========================================================
  SCRIPT TRÍCH XUẤT THỰC THỂ (NER) - PhoBERT / ELECTRA
  Chiến lược: Dùng trực tiếp entities từ model NER (không map schema)
==========================================================

PIPELINE:
  1. Đọc các file `filtered_data_*.json` (hoặc danh sách file được truyền vào)
     Cấu trúc file: { "source_platform", "source_url", "scraped_at",
                      "post_detail": [ { "title", "content", "is_relevant" } ] }
  2. Chỉ giữ lại các bài có `is_relevant = true`
  3. Gộp `title + content`, đưa qua model NER tiếng Việt (ORG / PER / LOC)
  4. Lưu TRỰC TIẾP danh sách thực thể mà model trả ra vào field `entities`
     cho từng bài, với mỗi phần tử có dạng:
       { "entity": <span>, "label": <entity_group>, "score": <float 0-1> }
  5. Bổ sung thêm 4 loại thực thể bằng dictionary + rule-based:
       - DATE     : ngày tháng năm (regex patterns tiếng Việt)
       - TECH     : công nghệ / ngôn ngữ lập trình / framework / tool (dictionary)
       - JOB_ROLE : vị trí / chức danh nghề nghiệp (dictionary + regex)
       - SALARY   : thông tin lương / mức lương (regex patterns)

Lưu ý:
- Script này KHÔNG gọi LLM, không cần API key.
- Thư viện bắt buộc: `transformers`, `torch`.
"""

import argparse
import json
import os
import re
import sys
from typing import List

import torch
from transformers import AutoModelForTokenClassification, AutoTokenizer, pipeline


if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")


# ==========================================
# CẤU HÌNH
# ==========================================

# Thư mục gốc project (dựa trên vị trí file hiện tại)
BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# Thư mục chứa file filtered_data_*.json (output sau bước lọc IT)
FILTERED_DATA_DIR = os.path.join(BASE_DIR, "filtered_data")

# Thư mục chứa file extracted_data_phobert_*.json (output sau bước NER PhoBERT)
EXTRACTED_DATA_DIR = os.path.join(BASE_DIR, "extracted_data")

# Thư mục mặc định để scan input (có thể override bằng --dir)
DIRECTORY = FILTERED_DATA_DIR

# Model NER tiếng Việt (PhoBERT / ELECTRA)
# File test hiện dùng model: NlpHust/ner-vietnamese-electra-base
NER_MODEL_NAME = "NlpHust/ner-vietnamese-electra-base"


# ==========================================
# KHỞI TẠO PIPELINE NER
# ==========================================

print(f"[INFO] Đang load model NER: {NER_MODEL_NAME} ...")
_tokenizer = AutoTokenizer.from_pretrained(NER_MODEL_NAME)
_model = AutoModelForTokenClassification.from_pretrained(NER_MODEL_NAME)

# Sử dụng aggregation_strategy="simple" để gộp token thành span hoàn chỉnh
ner_pipeline = pipeline(
    "ner",
    model=_model,
    tokenizer=_tokenizer,
    aggregation_strategy="simple",
    device=0 if torch.cuda.is_available() else -1,
)


# ==========================================
# DICTIONARY & PATTERNS: DATE và TECH
# ==========================================

# ----- DATE: regex nhận diện ngày tháng (thứ tự: dài → ngắn) -----
DATE_PATTERNS = [
    # ngày dd tháng mm năm YYYY (tiếng Việt đầy đủ)
    r"\b(ng[aà]y\s+\d{1,2}\s+th[aá]ng\s+\d{1,2}(?:\s+n[aă]m\s+\d{4})?)\b",
    # tháng X năm YYYY  /  tháng X
    r"\b(th[aá]ng\s+\d{1,2}(?:\s+n[aă]m\s+\d{4})?)\b",
    # Quý I/II/III/IV hoặc Q1–4 + năm tuỳ chọn
    r"\b(Qu[yý]\s+(?:I{1,3}|IV|[1-4])(?:\s*/\s*\d{4})?)\b",
    r"\b(Q[1-4]\s*/\s*\d{4})\b",
    # dd/mm/yyyy hoặc dd-mm-yyyy (phải có phần năm 4 chữ số)
    r"\b(\d{1,2}[/\-]\d{1,2}[/\-]\d{4})\b",
    # mm/yyyy hoặc mm-yyyy (đầu ghi chắc chắn không phải phần của dd/mm/yyyy)
    r"(?<!\d[/\-])(\d{1,2}[/\-]\d{4})(?!\d)",
    # Tên tháng tiếng Anh + YYYY  (e.g. March 2026, Jan 2025)
    r"\b((?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|"
    r"Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+\d{4})\b",
    # năm YYYY đứng theo sau từ 'năm' hoặc 'year'
    r"\b(?:n[aă]m|year)\s+((?:19|20)\d{2})\b",
    # Thứ trong tuần (thứ 2 → thứ 7, thứ Hai → thứ Bảy)
    r"\b(Th[ứu]\s+(?:[2-7]|Hai|Ba|Tư|Năm|Sáu|B[aả]y))\b",
    # Ngày lễ cụ thể: 30/4, 2/9, 1/5, 1/1
    r"\b(\d{1,2}/\d{1,2}(?:\s*[,và&]\s*\d{1,2}/\d{1,2})*)\b",
]

# Biên dịch sẵn, flag IGNORECASE để bắt cả viết hoa/thường
_DATE_REGEXES = [re.compile(p, re.IGNORECASE) for p in DATE_PATTERNS]


# ----- TECH: các cụm từ công nghệ (match case-insensitive) -----
# ----- TECH: các cụm từ công nghệ -----
# Từ viết tắt ngắn (match riêng bằng \b...\b)
# Có phân biệt HOA/thường để tránh trùng lặp tiếng Việt (VD: "AI" vs "ai" = ai đó)
TECH_ABBREVS_CASE_SENSITIVE = {
    "AI", "IT", "RPA", "AGI", "NPU", "GPU", "TPU"
}

# Không phân biệt HOA/thường
TECH_ABBREVS = {
    "AWS", "GCP", "CSS", "PHP", "SQL", "VBA", "SAS", "ELK", "SVN",
    "API", "SDK", "IDE", "RPC", "ORM", "JWT", "SSH", "HTTP", "HTTPS",
    "REST", "SOAP", "HTML", "XML", "JSON", "YAML", "gRPC", "LLM", "GPT",
    "SIP", "RTP", "VPN", "ETL", "ERP", "CRM", "MES", "IoT", "WAF",
    "SAP", "BGP", "OSPF", "VLAN", "NAT", "DNS", "DHCP", "TCP", "UDP",
    "OCR", "STT", "TTS", "RAG", "NLP", "MLOps", "CI/CD", "OLAP", "DWH",
    "SIEM", "EDR", "HSM", "IDS", "IPS", "SSE",
    # Cybersecurity & networking (từ DT)
    "DDoS", "MPC", "KYC", "SOC", "PAD", "SoC", "NVR", "VMS",
    "OTP", "2FA", "APK", "NFC", "ADB", "QR",
    "IPv4", "IPv6", "5G", "4G", "eSIM",
    "ROM", "CCCD", "VNeID",
    # Từ VN-EP
    "MoE", "LPU", "ICI", "SRAM", "HBM", "EEG",
    "C2PA", "GDPR", "FCC", "CISO", "FAIR",
    "MAU", "DAU"
}

# Từ / cụm dài hơn – dùng lookbehind/lookahead (không phân biệt HOA/thường)
TECH_KEYWORDS: List[str] = [
    # AI & ML & Language Models (Đồng bộ với filter_data.py)
    "ChatGPT", "Claude", "Gemini", "Copilot", "Grok", "Perplexity", "DeepSeek",
    "OpenAI", "Anthropic", "Mistral", "Llama", "VinAI", "xAI",
    "Trí tuệ nhân tạo", "Machine Learning", "Deep Learning", "Generative AI",
    "GenAI", "AI tạo sinh", "Mô hình ngôn ngữ", "Học máy", "Chatbot",
    "AI Agent", "Tác nhân AI", "Computer Vision",
    # AI sản phẩm thêm từ DT
    "Apple Intelligence", "Siri", "Sora", "Claude Code", "Claude Mythos",
    "Cursor AI", "Odoo", "AI Workplace Service",
    "Deepfake", "GhostChat", "Anatsa", "Stealerium",
    "Ransomware", "Infostealer", "Spyware",
    # Bảo mật & nền tảng
    "Zero Trust", "iVerify", "Lookout", "Kaspersky", "Malwarebytes",
    "Zscaler", "Proofpoint", "BreachForums", "nTrust",
    "iCallme", "Verichains", "IDMerit",
    "Watering hole", "SIM swap", "Pig Butchering",
    # Chip & phần cứng
    "Snapdragon", "Tensor", "Apple Silicon", "Qualcomm",
    "chip quang tử", "qubit",
    # Mạng & hạ tầng
    "Data Center", "AI Data Center", "Trung tâm dữ liệu",
    "Public Cloud", "Private Cloud", "Hybrid Cloud",
    "AI Readiness", "AI-First",
    "Python", "Java", "JavaScript", "TypeScript",
    "Golang", "Rust", "Kotlin", "Swift", "Scala", "Ruby", "PHP",
    "Perl", "Dart", "Lua", "Groovy", "Elixir", "Erlang", "Haskell",
    "MATLAB", "Julia", "Visual Basic", "Objective-C",
    "C++", "C#",  # giữ lại vì có ký hiệu riêng
    # Web Front-end
    "React", "ReactJS", "React.js", "Vue", "VueJS", "Vue.js",
    "Angular", "AngularJS", "Next.js", "Nuxt.js", "Svelte", "jQuery",
    "Bootstrap", "Tailwind", "TailwindCSS", "Sass", "SCSS", "Less",
    "Webpack", "Vite", "Babel",
    # Web Back-end
    "Node.js", "NodeJS", "Express", "ExpressJS", "NestJS", "Django",
    "Flask", "FastAPI", "Spring Boot", "Spring", "SpringBoot",
    "Laravel", "Symfony", "Rails", "Ruby on Rails", "ASP.NET", ".NET",
    "Gin", "Echo", "Fiber",
    # Database / Datastore
    "MySQL", "PostgreSQL", "MariaDB", "SQLite", "MSSQL", "SQL Server",
    "Oracle", "MongoDB", "Redis", "Cassandra", "DynamoDB", "Elasticsearch",
    "HBase", "CouchDB", "Neo4j", "InfluxDB", "Firebase", "Firestore",
    "Supabase", "PlanetScale",
    # Cloud & Infra (bỏ S3, EC2, Lambda – dễ trùng tên thường)
    "Azure", "Google Cloud", "Google Cloud Platform",
    "Alibaba Cloud", "DigitalOcean", "CloudFront",
    "Cloudflare", "Vercel", "Netlify", "Heroku",
    # DevOps / CI/CD / Container
    "Docker", "Kubernetes", "K8s", "Jenkins", "GitLab CI", "GitHub Actions",
    "CircleCI", "Travis CI", "Ansible", "Terraform", "Puppet", "Chef",
    "Helm", "ArgoCD", "Prometheus", "Grafana", "Logstash", "Kibana",
    # Version control
    "Git", "GitHub", "GitLab", "Bitbucket",
    # AI / ML / Data Science
    "TensorFlow", "PyTorch", "Keras", "Scikit-learn",
    "Pandas", "NumPy", "Matplotlib", "Seaborn", "Plotly", "Scipy",
    "XGBoost", "LightGBM", "CatBoost", "OpenCV", "NLTK", "SpaCy",
    "Transformers", "HuggingFace", "Hugging Face", "BERT", "GPT",
    "LangChain", "LlamaIndex",
    # Big Data
    "Hadoop", "Apache Spark", "Spark", "Apache Kafka", "Kafka",
    "Hive", "Apache Flink", "Flink", "Apache Airflow", "Airflow",
    "dbt", "Dask", "Presto", "Trino", "Snowflake", "Databricks",
    "BigQuery", "Redshift",
    # Testing
    "JUnit", "pytest", "Selenium", "Cypress", "Playwright", "Jest",
    "Mocha", "Jasmine", "Postman", "JMeter", "Appium", "RestAssured",
    # Protocols / tools
    "RESTful", "GraphQL", "WebSocket", "WebRTC", "VoIP",
    "RabbitMQ", "ActiveMQ", "ZeroMQ", "NATS", "Nginx", "Apache", "Tomcat",
    "Microservices", "Serverless", "Linux", "Unix", "Windows Server",
    "Agile", "Scrum", "Jira", "Confluence", "Trello", "Asana",
    "FreeSWITCH", "Asterisk", "Genesys",
    # MLOps / AI infra
    "MLflow", "Kubeflow", "Airflow", "DVC", "Feast",
    "FAISS", "Qdrant", "Milvus", "pgvector", "Pinecone",
    "AutoML", "Vertex AI", "SageMaker",
    # BI / Data tools
    "Power BI", "Tableau", "Looker", "Metabase", "Grafana",
    "Mixpanel", "Google Analytics", "Amplitude",
    "Pentaho", "Talend", "Informatica", "dbt",
    # Frontend extras
    "Redux", "Zustand", "Recoil", "MobX", "Pinia", "Vuex",
    "Styled Components", "Emotion", "Ant Design", "Element Plus", "Vuetify",
    "D3.js", "Recharts", "TradingView", "Chart.js",
    # Security tools
    "Splunk", "QRadar", "Trellix", "Symantec", "Trend Micro",
    "Fortinet", "PaloAlto", "CheckPoint", "Imperva", "F5",
    "SonicWALL", "Barracuda", "Cisco", "Juniper", "Aruba",
    # 3D / Design tools
    "Blender", "Cinema4D", "Unity", "Unreal", "SketchUp",
    "Revit", "Rhino", "3ds Max", "V-Ray", "Lumion", "Enscape",
    "Substance 3D", "Figma", "Adobe Photoshop", "Adobe Illustrator",
    "Adobe Premiere", "After Effects", "InDesign", "CapCut",
    # Enterprise systems
    "SAP ERP", "Oracle ERP", "Salesforce", "ServiceNow", "n8n",
    "Power Automate", "Zapier",
    # AI Models & Products (từ GenK)
    "Muse Spark", "TurboQuant", "Gemma 4", "DLSS 5", "OpenClaw",
    "Atlas 350", "Agents SDK", "Project Glasswing", "Constitutional AI",
    "Trinity Large Thinking", "Claude Opus",
    "Gemini CLI", "Google AI Edge Eloquent", "WisperFlow", "SuperWhisper",
    # Khái niệm AI/Tech (từ GenK)
    "agentic AI", "Physical AI", "vibe-coding", "vibe coding",
    "KV cache", "frame generation", "ray tracing",
    "sideloading", "AI Factory", "AI inference", "AI reasoning",
    "Sovereign Cloud",
    "reCAPTCHA", "AI streamer",
    # Mô hình AI mới (từ VN-EP)
    "GPT-5.4", "GPT-5.3", "GPT-5.2", "GPT-4o",
    "Gemini 3", "Gemini 3 Flash", "Gemini 3.1 Flash", "Gemini 2.5",
    "Nano Banana", "Nano Banana Pro", "Personal Intelligence",
    "Images 2.0", "TurboDiffusion", "NeMoClaw",
    "Mixture of Experts", "Mô hình hỗn hợp chuyên gia",
    # Khái niệm & kỹ thuật (từ VN-EP)
    "AI washing", "C2PA", "Content Credentials", "peer preservation",
    "adversarial poetry", "Predictive AI", "Foundation Models",
    "GDPR", "Model Extraction", "Grokipedia",
    "Iceberg Index", "exaflop", "petaflop",
    "Cursor", "Claude Code",
]

# Regex cho viết tắt ngắn: dùng \b thật sự
_ABBREV_PATTERN = re.compile(
    r"\b(" + "|".join(re.escape(a) for a in sorted(TECH_ABBREVS, key=len, reverse=True)) + r")\b",
    re.IGNORECASE,
)

_ABBREV_CASE_PATTERN = re.compile(
    r"\b(" + "|".join(re.escape(a) for a in sorted(TECH_ABBREVS_CASE_SENSITIVE, key=len, reverse=True)) + r")\b",
)

# Regex cho cụm dài: dùng negative lookbehind/lookahead (unicode safe)
_TECH_SORTED = sorted(TECH_KEYWORDS, key=len, reverse=True)
_TECH_PATTERN = re.compile(
    r"(?<![\w.])" +
    "(" + "|".join(re.escape(t) for t in _TECH_SORTED) + ")" +
    r"(?![\w.])",
    re.IGNORECASE,
)


# ==========================================
# DICTIONARY & PATTERNS: JOB_ROLE và SALARY
# ==========================================

# ----- JOB_ROLE: chức danh / vị trí nghề nghiệp -----
# Ưu tiên cụm dài hơn (sorted by length desc trong regex)
JOB_ROLE_KEYWORDS: List[str] = [
    # Quản lý / lãnh đạo cấp cao
    "Chief Executive Officer", "Chief Technology Officer", "Chief Information Officer",
    "Chief Product Officer", "Chief Data Officer", "Chief Operating Officer",
    "Vice President", "General Manager", "Managing Director",
    "Giám đốc điều hành", "Giám đốc công nghệ", "Giám đốc kỹ thuật",
    "Giám đốc sản phẩm", "Giám đốc dữ liệu", "Giám đốc vận hành",
    "Tổng giám đốc", "Phó giám đốc", "Trưởng phòng", "Phó phòng",
    "Trưởng nhóm", "Trưởng bộ phận", "Quản lý dự án",
    # IT / Tech roles
    "Software Engineer", "Software Developer", "Senior Software Engineer",
    "Junior Software Engineer", "Full Stack Developer", "Full-Stack Developer",
    "Frontend Developer", "Front-end Developer", "Backend Developer", "Back-end Developer",
    "Mobile Developer", "iOS Developer", "Android Developer",
    "DevOps Engineer", "Cloud Engineer", "Data Engineer", "Data Scientist",
    "Data Analyst", "Business Analyst", "System Analyst", "System Administrator",
    "Database Administrator", "Network Engineer", "Security Engineer",
    "QA Engineer", "QA Tester", "Test Engineer", "Automation Engineer",
    "AI Engineer", "ML Engineer", "Machine Learning Engineer",
    "Product Manager", "Project Manager", "Scrum Master", "Tech Lead",
    "Solution Architect", "Technical Architect", "Enterprise Architect",
    "UI/UX Designer", "UX Designer", "UI Designer", "UX Researcher",
    "IT Manager", "IT Director", "IT Specialist", "IT Support",
    # Tiếng Việt
    "Kỹ sư phần mềm", "Lập trình viên", "Nhà phát triển phần mềm",
    "Kỹ sư dữ liệu", "Kỹ sư AI", "Kỹ sư học máy",
    "Kỹ sư hệ thống", "Kỹ sư mạng", "Kỹ sư bảo mật",
    "Kỹ sư DevOps", "Kỹ sư cloud", "Kỹ sư kiểm thử",
    "Chuyên viên phân tích dữ liệu", "Chuyên viên phân tích nghiệp vụ",
    "Chuyên viên phát triển phần mềm", "Chuyên viên IT",
    "Nhà khoa học dữ liệu", "Kiến trúc sư phần mềm",
    "Quản lý sản phẩm", "Quản lý dự án IT",
    "Nhà thiết kế UI", "Nhà thiết kế UX", "Nhà thiết kế giao diện",
    # Roles phổ biến trong data TopCV
    "Penetration Tester", "Pentest Engineer",
    "VoIP Engineer", "Telephony Engineer", "Network Security Engineer",
    "Pre-Sale Engineer", "Pre-Sales Engineer",
    "Creative Designer", "Graphic Designer", "Art Designer",
    "UI/UX Researcher", "Motion Designer", "3D Designer",
    "IT Helpdesk", "IT Coordinator", "IT Specialist",
    "Senior AI Engineer", "Junior AI Engineer",
    "Manual Tester", "Automation Tester",
    "BrSE", "Fresher Developer", "Fresher Engineer",
    "DevOps/Cloud Engineer", "Cloud DevOps Engineer",
    "SAP Consultant", "ERP Consultant",
    "Data Lake Engineer", "MLOps Engineer",
    "Lập Trình Viên", "Kỹ Thuật Viên", "Chuyên Viên IT",
    "Nhân Viên IT", "Nhân Viên Triển Khai",
    "Trưởng Phòng", "Giám Đốc Công Nghệ",
    # Viết tắt phổ biến
    "CEO", "CTO", "CIO", "CPO", "CDO", "COO", "CFO",
    "VP", "GM", "PM", "PO", "BA", "SA", "DBA",
    "SDE", "SWE", "QA", "QC",
]

_JOB_ROLE_SORTED = sorted(JOB_ROLE_KEYWORDS, key=len, reverse=True)
_JOB_ROLE_PATTERN = re.compile(
    r"(?<![\w.])(" + "|".join(re.escape(j) for j in _JOB_ROLE_SORTED) + r")(?![\w.])",
    re.IGNORECASE,
)


# ----- SALARY: regex nhận diện mức lương -----
SALARY_PATTERNS = [
    # Dạng "X - Y triệu", "X – Y triệu"
    r"\b(\d+(?:[,.]\d+)?\s*[-–]\s*\d+(?:[,.]\d+)?\s*tri[eệ]u(?:\s*VNĐ|\s*VND|\s*đồng)?)\b",
    # Dạng "X triệu", "X,X triệu"
    r"\b(\d+(?:[,.]\d+)?\s*tri[eệ]u(?:\s*VNĐ|\s*VND|\s*đồng)?)\b",
    # Dạng "upto/up to X triệu" hoặc "upto X VNĐ" (viết liền hoặc cách)
    r"\b(upto\s+\d+(?:[,.]\d+)?(?:M|\s*tri[eệ]u|\s*VNĐ|\s*VND)?)\b",
    r"\b(up\s+to\s+\$?\d+(?:[,.]\d+)?(?:[,.]\d{3})*(?:\s*USD|\s*VND|\s*VNĐ|\s*tri[eệ]u)?)\b",
    # Dạng "từ X triệu", "từ X đến Y triệu"
    r"\b(t[ừu]\s+\d+(?:[,.]\d+)?(?:\s*đến\s+\d+(?:[,.]\d+)?)?\s*tri[eệ]u(?:\s*VNĐ|\s*VND)?)\b",
    # Dạng "$X,XXX - $Y,XXX" hoặc "$X,XXX+"
    r"(\$\d{1,3}(?:,\d{3})*(?:\s*[-–]\s*\$\d{1,3}(?:,\d{3})*)?(?:\+)?(?:\s*USD|\s*VND|\s*VNĐ)?)",
    # Dạng số lớn: "10,000,000 VNĐ" hoặc "10.000.000đ"
    r"\b(\d{1,3}(?:[,.]\d{3})+(?:\s*VNĐ|\s*VND|\s*đồng|đ)?)\b",
    # Dạng "XY$ / tháng", "X$ / tháng" (lương USD nhỏ lẻ)
    r"\b(\d+(?:[,.]\d+)?\s*\$\s*/\s*tháng)\b",
    # Dạng "$X,XXX - $Y,XXX" và "$X - Y"
    r"(\$\s*\d{1,4}(?:\s*[-–]\s*\$?\s*\d{1,4})?(?:\s*USD|\s*VND)?)",
    # Dạng "X tháng lương/năm" (gói thu nhập)
    r"\b(\d{1,2}\s*(?:–|-)?\s*\d{0,2}\s*tháng\s+lương(?:/năm)?)\b",
    # Dạng "lương X triệu", "thu nhập X triệu", "mức lương X triệu"
    r"\b(?:lương|thu\s+nhập|mức\s+lương|salary|income)\s+(\d+(?:[,.]\d+)?(?:\s*[-–]\s*\d+(?:[,.]\d+)?)?\s*(?:tri[eệ]u|nghìn|USD|VNĐ|VND)?)\b",
    # Dạng cụm "lương thương lượng", "lương cạnh tranh", "lương hấp dẫn"
    r"\b(lương\s+(?:thương\s+lượng|cạnh\s+tranh|hấp\s+dẫn|theo\s+năng\s+lực|thoả\s+thuận|thỏa\s+thuận))\b",
    r"\b(thu\s+nhập\s+(?:cạnh\s+tranh|hấp\s+dẫn|theo\s+năng\s+lực))\b",
    r"\b(salary\s+(?:negotiable|competitive|attractive))\b",
    r"\b(competitive\s+salary(?:\s+range)?)\b",
]

_SALARY_REGEXES = [re.compile(p, re.IGNORECASE) for p in SALARY_PATTERNS]


def extract_date_entities(text: str) -> List[dict]:
    """
    Trích xuất các thực thể DATE từ văn bản bằng regex.
    Trả về list [{ "entity": <span>, "label": "DATE", "score": 1.0 }].
    Tránh trùng lặp dựa trên vị trí ký tự.
    """
    found: List[dict] = []
    covered_spans = []  # danh sách (start, end) đã được match

    for regex in _DATE_REGEXES:
        for m in regex.finditer(text):
            s, e = m.start(), m.end()
            # Bỏ qua nếu đã bị overlap với span trước đó
            if any(s < ce and e > cs for cs, ce in covered_spans):
                continue
            covered_spans.append((s, e))
            found.append({"entity": m.group().strip(), "label": "DATE", "score": 1.0})

    return found


def extract_tech_entities(text: str) -> List[dict]:
    """
    Trích xuất TECH: dùng 3 regex riêng biệt:
      - _ABBREV_CASE_PATTERN: viết tắt ngắn phải đúng case (VD: AI, IT)
      - _ABBREV_PATTERN: viết tắt ngắn case-insensitive (AWS, API...)
      - _TECH_PATTERN  : cụm từ dài hơn với lookaround
    Mỗi keyword chỉ xuất hiện một lần (case-insensitive dedup).
    """
    seen: set = set()
    found: List[dict] = []

    for pattern in (_ABBREV_CASE_PATTERN, _ABBREV_PATTERN, _TECH_PATTERN):
        for m in pattern.finditer(text):
            kw = m.group()
            key = kw.lower()
            if key not in seen:
                seen.add(key)
                found.append({"entity": kw, "label": "TECH", "score": 1.0})

    return found


def extract_job_role_entities(text: str) -> List[dict]:
    """
    Trích xuất JOB_ROLE từ văn bản bằng dictionary + regex.
    Mỗi chức danh chỉ xuất hiện một lần (case-insensitive dedup).
    Trả về list [{ "entity": <span>, "label": "JOB_ROLE", "score": 1.0 }].
    """
    seen: set = set()
    found: List[dict] = []

    for m in _JOB_ROLE_PATTERN.finditer(text):
        kw = m.group()
        key = kw.lower()
        if key not in seen:
            seen.add(key)
            found.append({"entity": kw, "label": "JOB_ROLE", "score": 1.0})

    return found


def extract_salary_entities(text: str) -> List[dict]:
    """
    Trích xuất SALARY từ văn bản bằng regex patterns.
    Tránh trùng lặp dựa trên vị trí ký tự (overlap check).
    Trả về list [{ "entity": <span>, "label": "SALARY", "score": 1.0 }].
    """
    found: List[dict] = []
    covered_spans = []

    for regex in _SALARY_REGEXES:
        for m in regex.finditer(text):
            # Lấy group(1) nếu có (bắt phần giá trị), không thì dùng group()
            span = (m.group(1) if m.lastindex and m.group(1) else m.group()).strip()
            if not span:
                continue
            s, e = m.start(), m.end()
            if any(s < ce and e > cs for cs, ce in covered_spans):
                continue
            covered_spans.append((s, e))
            found.append({"entity": span, "label": "SALARY", "score": 1.0})

    return found


def _chunk_text_by_tokens(text: str, max_tokens: int = 480, overlap: int = 50) -> List[str]:
    """
    Chia text thành các chunk không vượt quá `max_tokens` token (không tính special tokens).
    Các chunk liên tiếp overlap nhau `overlap` token để tránh bỏ sót entity tại biên.
    Dùng tokenizer của model NER để đảm bảo đúng số lượng token.
    """
    token_ids = _tokenizer.encode(text, add_special_tokens=False)
    if len(token_ids) <= max_tokens:
        return [text]

    chunks: List[str] = []
    start = 0
    while start < len(token_ids):
        end = min(start + max_tokens, len(token_ids))
        chunk_text = _tokenizer.decode(
            token_ids[start:end],
            skip_special_tokens=True,
            clean_up_tokenization_spaces=True,
        )
        chunks.append(chunk_text)
        if end >= len(token_ids):
            break
        start += max_tokens - overlap   # bước nhảy có overlap
    return chunks


def extract_entities_ner(text: str) -> List[dict]:
    """
    Chạy NER model trên text, đồng thời bổ sung DATE/TECH/JOB_ROLE/SALARY bằng rule-based.

    Định dạng mỗi phần tử:
      {
        "entity": <chuỗi span>,
        "label": <entity_group: ORG / PER / LOC / DATE / TECH / JOB_ROLE / SALARY>,
        "score": <độ tin cậy 0-1, làm tròn 2 chữ số>
      }

    Xử lý text dài hơn giới hạn 512 tokens của ELECTRA bằng sliding-window chunking:
      - Chunk tối đa 480 token (để lại buffer cho special tokens)
      - Overlap 50 token giữa các chunk liên tiếp
      - Dedup NER theo (word.lower(), label) qua tất cả các chunk
      - Rule-based extractors (DATE/TECH/JOB_ROLE/SALARY) chạy trên text gốc đầy đủ
    """
    if not text.strip():
        return []

    # --- 1. Chia text thành chunks nếu vượt giới hạn 512 tokens ---
    chunks = _chunk_text_by_tokens(text, max_tokens=480, overlap=50)

    # --- 2. Chạy NER trên từng chunk, dedup theo (word.lower(), label) ---
    final_entities: List[dict] = []
    _ner_seen: set = set()

    for chunk in chunks:
        if not chunk.strip():
            continue
        try:
            ner_results = ner_pipeline(chunk)
        except Exception as e:
            print(f"    [WARN] NER pipeline lỗi trên chunk ({len(chunk)} ký tự): {e}")
            continue

        for ent in ner_results:
            label = (ent.get("entity_group") or ent.get("entity") or "").upper()
            if not label or label in ("O", ""):
                continue
            word  = ent.get("word", "").strip()
            score = ent.get("score")
            if not word or score is None:
                continue
            dedup_key = (word.lower(), label)
            if dedup_key not in _ner_seen:
                _ner_seen.add(dedup_key)
                final_entities.append({
                    "entity": word,
                    "label":  label,
                    "score":  round(float(score), 2),
                })

    # --- 3. DATE bằng regex rule-based (chạy trên text gốc đầy đủ) ---
    final_entities.extend(extract_date_entities(text))

    # --- 4. TECH bằng dictionary rule-based ---
    final_entities.extend(extract_tech_entities(text))

    # --- 5. JOB_ROLE bằng dictionary rule-based ---
    final_entities.extend(extract_job_role_entities(text))

    # --- 6. SALARY bằng regex rule-based ---
    final_entities.extend(extract_salary_entities(text))

    return final_entities


# Ánh xạ label model / rule-based → key trong dict entities
# Hỗ trợ cả: label ngắn (PER/ORG/LOC), label đầy đủ của NlpHust (PERSON/ORGANIZATION/LOCATION),
# và bất kỳ tiền tố B-/I- nào.
_LABEL_TO_KEY = {
    # Dạng ngắn
    "PER":          "PER",
    "ORG":          "ORG",
    "LOC":          "LOC",
    # Dạng đầy đủ (NlpHust electra)
    "PERSON":       "PER",
    "ORGANIZATION": "ORG",
    "LOCATION":     "LOC",
    # Rule-based entities
    "DATE":         "DATE",
    "TECH":         "TECH",
    "JOB_ROLE":     "JOB_ROLE",
    "SALARY":       "SALARY",
    # Tiền tố B-/I- + dạng ngắn
    "B-PER": "PER",  "I-PER": "PER",
    "B-ORG": "ORG",  "I-ORG": "ORG",
    "B-LOC": "LOC",  "I-LOC": "LOC",
    # Tiền tố B-/I- + dạng đầy đủ
    "B-PERSON":       "PER",  "I-PERSON":       "PER",
    "B-ORGANIZATION": "ORG",  "I-ORGANIZATION": "ORG",
    "B-LOCATION":     "LOC",  "I-LOCATION":     "LOC",
}


def normalize_entity(text: str, label: str = "") -> str:
    """
    Chuẩn hóa chuỗi entity trước khi lưu:
      - Bỏ khoảng trắng thừa đầu/cuối và thu gọn khoảng trắng bên trong.
      - Xóa ký tự ▁ (artifact subword của PhoBERT/SentencePiece).
      - Xóa các ký tự đặc biệt lẻ ở đầu/cuối (dấu chấm, dấu phẩy, dấu ngoặc).
      - Với PER: title-case nếu toàn bộ chuỗi là chữ thường.
    Trả về chuỗi đã chuẩn hóa, hoặc chuỗi rỗng nếu không hợp lệ.
    """
    if not text:
        return ""

    # Xóa ký tự subword ▁ của SentencePiece / PhoBERT
    text = text.replace("▁", "").replace("\u2581", "")

    # Thu gọn khoảng trắng
    text = " ".join(text.split())

    # Bỏ dấu câu lẻ ở đầu/cuối
    text = text.strip(".,;:\"'()[]{}")

    # Thu gọn lại sau khi strip
    text = text.strip()

    # Title-case cho PER nếu toàn chữ thường (tránh phá vỡ viết tắt như NPU, CEO)
    if label.upper() == "PER" and text and text == text.lower():
        text = text.title()

    return text


def group_entities(flat_entities: List[dict]) -> dict:
    """
    Chuyển danh sách entities phẳng sang dict có cấu trúc:
      {
        "PER":      [...],
        "ORG":      [...],
        "LOC":      [...],
        "DATE":     [...],
        "TECH":     [...],
        "JOB_ROLE": [...],
        "SALARY":   [...]
      }
    - Mỗi entity được chuẩn hóa qua normalize_entity() trước khi lưu.
    - Mỗi entity chỉ xuất hiện một lần trong mỗi nhóm (dedup case-sensitive sau normalize).
    - Các label không nằm trong ánh xạ sẽ bị bỏ qua.
    """
    result: dict = {
        "PER":      [],
        "ORG":      [],
        "LOC":      [],
        "DATE":     [],
        "TECH":     [],
        "JOB_ROLE": [],
        "SALARY":   [],
    }
    seen: dict = {k: set() for k in result}

    for ent in flat_entities:
        label = (ent.get("entity_group") or ent.get("label") or "").upper()
        key = _LABEL_TO_KEY.get(label)
        if key is None:
            continue
        raw = ent.get("entity", "")
        value = normalize_entity(raw, label=key)
        if value and value not in seen[key]:
            seen[key].add(value)
            result[key].append(value)

    return result



# ==========================================
# HÀM XỬ LÝ FILE JSON
# ==========================================

def ner_json_file_phobert(input_path: str) -> str:
    print(f"\n{'=' * 55}")
    print(f"[PhoBERT NER] Đang xử lý: {os.path.basename(input_path)}")
    print(f"{'=' * 55}")

    with open(input_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    # --- Nhận diện định dạng ---
    if isinstance(data, list):
        # Format mới: array trực tiếp (job_descriptions)
        fmt = "array"
        posts = data
    elif isinstance(data, dict):
        # Format cũ: object với key "post_detail" (VN-EP, DanTri...)
        fmt = "object"
        posts = data.get("post_detail", [])
    else:
        print("  Định dạng JSON không hợp lệ.")
        return ""

    print(f"Định dạng phát hiện: {'array trực tiếp' if fmt == 'array' else 'object (post_detail)'}")

    total = len(posts)
    relevant_indices = [i for i, p in enumerate(posts) if p.get("is_relevant") is True]

    print(
        f"Tổng: {total} bài | Chỉ xử lý & ghi ra: {len(relevant_indices)} | "
        f"Bỏ qua (is_relevant=false): {total - len(relevant_indices)}"
    )

    relevant_posts = [posts[i] for i in relevant_indices]

    print("\n[Bước] Chạy NER model tiếng Việt (trả về list entities thô)...")
    all_entities: List[List[dict]] = []
    for i, post in enumerate(relevant_posts):
        if fmt == "array":
            # Format mới: title = job_title, content = job_description + benefits + requirements
            title_raw = post.get("job_title", "")
            content_raw = " ".join(filter(None, [
                post.get("job_description", "") or "",
                post.get("benefits", "") or "",
                post.get("requirements", "") or "",
            ]))
        else:
            # Format cũ: title = title, content = content (fallback sang job fields nếu trống)
            title_raw = post.get("title", "")
            content_raw = post.get("content", "")
            # if not title_raw and not content_raw:
            #     title_raw = post.get("job_title", "")
            #     content_raw = " ".join(filter(None, [
            #         post.get("job_description", "") or "",
            #         post.get("benefits", "") or "",
            #         post.get("requirements", "") or "",
            #     ]))

        text = f"{title_raw}. {content_raw}".strip(". ").strip()
        ents = extract_entities_ner(text)
        all_entities.append(ents)

        title = title_raw[:45]

        # Tóm tắt nhanh số lượng từng nhóm thực thể cho log
        grouped = group_entities(ents)
        summary = " | ".join(
            f"{k[:3].upper()}:{len(v)}" for k, v in grouped.items() if v
        ) or "—"
        print(f"  [{relevant_indices[i] + 1}] {title}")
        print(f"       ENTITIES: {summary}")

    # Gán entities (dạng dict có cấu trúc) vào từng bài relevant
    for i, idx in enumerate(relevant_indices):
        posts[idx]["entities"] = group_entities(all_entities[i])

    # Output chỉ giữ lại bài có is_relevant=true (đã có entities)
    output_posts = [posts[i] for i in relevant_indices]

    if fmt == "array":
        # Format mới: giữ nguyên cấu trúc array
        data_out = output_posts
    else:
        # Format cũ: giữ nguyên wrapper object, thay post_detail
        data_out = {k: v for k, v in data.items() if k != "post_detail"}
        data_out["post_detail"] = output_posts

    # Lưu output vào thư mục extracted_data_phobert với tên dạng extracted_data_phobert_XXX.json
    os.makedirs(EXTRACTED_DATA_DIR, exist_ok=True)
    base_name = os.path.basename(input_path)

    if base_name.startswith("filtered_data_"):
        suffix = base_name[len("filtered_data_"):]
    else:
        suffix = base_name

    output_filename = "extracted_data_phobert_" + suffix
    output_path = os.path.join(EXTRACTED_DATA_DIR, output_filename)

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(data_out, f, ensure_ascii=False, indent=2)

    print(f"\n  → Kết quả: {output_path} (chỉ {len(output_posts)} bài is_relevant=true)")
    return output_path


# ==========================================
# HÀM CHÍNH
# ==========================================

def main():
    global DIRECTORY

    parser = argparse.ArgumentParser(
        description=(
            "NER trích xuất entity cho bài IT bằng PhoBERT/ELECTRA "
            "(model HuggingFace, không dùng LLM; map sang schema 12 trường)."
        )
    )
    parser.add_argument(
        "files",
        nargs="*",
        help=(
            "Đường dẫn file filtered_data_*.json cần xử lý. "
            "Nếu bỏ trống, script sẽ tự scan thư mục mặc định hoặc --dir."
        ),
    )
    parser.add_argument(
        "--dir",
        dest="directory",
        default=DIRECTORY,
        help=f"Thư mục chứa file filtered_data_*.json (mặc định: {DIRECTORY}).",
    )
    args = parser.parse_args()

    DIRECTORY = args.directory

    if args.files:
        labeled_files = [os.path.abspath(f) for f in args.files]
        missing = [f for f in labeled_files if not os.path.exists(f)]
        if missing:
            for f in missing:
                print(f"[LỖI] Không tìm thấy file: {f}")
            sys.exit(1)
    else:
        if not os.path.isdir(DIRECTORY):
            print(f"[LỖI] Thư mục không tồn tại: {DIRECTORY}")
            sys.exit(1)

        labeled_files = [
            os.path.join(DIRECTORY, f)
            for f in os.listdir(DIRECTORY)
            if f.startswith("filtered_data_") and f.endswith(".json")
        ]

    if not labeled_files:
        print("Không tìm thấy file đầu vào.")
        return

    print(f"Tìm thấy {len(labeled_files)} file:")
    for f in labeled_files:
        print(f"  - {os.path.basename(f)}")

    for file_path in labeled_files:
        ner_json_file_phobert(file_path)

    print(f"\n{'=' * 55}")
    print(
        "Hoàn thành! Kiểm tra các file extracted_data_phobert_*.json "
        "trong thư mục extracted_data_phobert"
    )
    print(f"{'=' * 55}")


if __name__ == "__main__":
    main()

