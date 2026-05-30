"""
Bảng mở rộng tên viết tắt (acronym) → tên đầy đủ.

Mục đích: Khi encode tên tech bằng embedding model, các acronym như SSH, JWT, K8s
bị encode theo mặt chữ (vô nghĩa) → cluster sai. Mở rộng giúp model hiểu ngữ nghĩa.

Sử dụng: expand_tech_name("SSH") → "SSH (Secure Shell)"
"""

ACRONYM_MAP: dict[str, str] = {
    # --- Network / Security ---
    "SSH": "Secure Shell",
    "VPN": "Virtual Private Network",
    "DNS": "Domain Name System",
    "DHCP": "Dynamic Host Configuration Protocol",
    "TCP": "Transmission Control Protocol",
    "UDP": "User Datagram Protocol",
    "HTTP": "HyperText Transfer Protocol",
    "HTTPS": "HyperText Transfer Protocol Secure",
    "BGP": "Border Gateway Protocol",
    "OSPF": "Open Shortest Path First",
    "NAT": "Network Address Translation",
    "VLAN": "Virtual Local Area Network",
    "VoIP": "Voice over Internet Protocol",
    "IPv4": "Internet Protocol version 4",
    "IPv6": "Internet Protocol version 6",
    "DDoS": "Distributed Denial of Service",
    "NFC": "Near Field Communication",
    "JWT": "JSON Web Token",
    "FCC": "Federal Communications Commission",
    "WAF": "Web Application Firewall",
    "IDS": "Intrusion Detection System",
    "IPS": "Intrusion Prevention System",
    "SIEM": "Security Information and Event Management",
    "SOC": "Security Operations Center",
    "HSM": "Hardware Security Module",
    "KYC": "Know Your Customer",
    "OTP": "One-Time Password",
    "SSE": "Server-Sent Events",
    "GDPR": "General Data Protection Regulation",
    "SIP": "Session Initiation Protocol",

    # --- Web / Frontend ---
    "CSS": "Cascading Style Sheets",
    "HTML": "HyperText Markup Language",
    "SASS": "Syntactically Awesome Style Sheets",
    "SCSS": "Sassy Cascading Style Sheets",
    "REST": "Representational State Transfer",
    "RESTful": "Representational State Transfer API",
    "GraphQL": "Graph Query Language",
    "gRPC": "Google Remote Procedure Call",
    "RPC": "Remote Procedure Call",
    "SOAP": "Simple Object Access Protocol",

    # --- AI / ML ---
    "LLM": "Large Language Model",
    "RAG": "Retrieval Augmented Generation",
    "AGI": "Artificial General Intelligence",
    "NLP": "Natural Language Processing",
    "GPT": "Generative Pre-trained Transformer",
    "BERT": "Bidirectional Encoder Representations from Transformers",
    "TPU": "Tensor Processing Unit",
    "NPU": "Neural Processing Unit",
    "GPU": "Graphics Processing Unit",
    "STT": "Speech to Text",
    "TTS": "Text to Speech",
    "FAISS": "Facebook AI Similarity Search",
    "NLTK": "Natural Language Toolkit",
    "GenAI": "Generative Artificial Intelligence",

    # --- DevOps / Cloud ---
    "K8s": "Kubernetes container orchestration",
    "CI": "Continuous Integration",
    "CD": "Continuous Deployment",
    "ETL": "Extract Transform Load",
    "GCP": "Google Cloud Platform",
    "ELK": "Elasticsearch Logstash Kibana",
    "MPC": "Multi-Party Computation",

    # --- Data ---
    "SQL": "Structured Query Language",
    "DWH": "Data Warehouse",
    "DVC": "Data Version Control",
    "dbt": "Data Build Tool",

    # --- Telecom ---
    "5G": "5th Generation Mobile Network",
    "4G": "4th Generation Mobile Network",
    "eSIM": "Embedded SIM Card",
    "IoT": "Internet of Things",

    # --- Business / Other ---
    "RPA": "Robotic Process Automation",
    "ERP": "Enterprise Resource Planning",
    "MES": "Manufacturing Execution System",
    "SAS": "Statistical Analysis System",
    "IT": "Information Technology",
    "NET": ".NET Framework",
    "JIRA": "Jira Project Management",
    "SVN": "Subversion Version Control",
}


def expand_tech_name(name: str) -> str:
    """
    Mở rộng tên tech nếu có trong bảng acronym.

    Ví dụ:
        "SSH"  → "SSH (Secure Shell)"
        "React" → "React" (không đổi)
    """
    expanded = ACRONYM_MAP.get(name)
    if expanded:
        return f"{name} ({expanded})"
    return name


def expand_tech_names(names: list[str]) -> list[str]:
    """Mở rộng danh sách tên tech."""
    return [expand_tech_name(n) for n in names]
