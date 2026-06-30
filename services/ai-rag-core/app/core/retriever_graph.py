import asyncio

from app.core.entity_extractor import extract_query_entities
from app.db.neo4j_client import run_query
from app.db.graph_queries import (
    JOBS_BY_TECH_AND_TITLE,
    JOBS_BY_TECH,
    JOBS_BY_TITLE,
    JOBS_BY_COMPANY,
    JOBS_BY_LOCATION,
    COMPANIES_USING_TECH,
    TECH_RELATED,
)

# ---------------------------------------------------------------------------
# Query-time alias normalization
# Maps tech names as extracted by entity_extractor to canonical graph forms.
# Must mirror knowledge-graph/entity_resolution/aliases.json for variants
# that actually appear in TECH_KEYWORDS / TECH_ABBREVS.
# ---------------------------------------------------------------------------
_QUERY_TECH_ALIASES: dict[str, str] = {
    "k8s": "Kubernetes",
    "reactjs": "React",
    "react.js": "React",
    "vuejs": "Vue",
    "vue.js": "Vue",
    "angularjs": "Angular",
    "angular.js": "Angular",
    "nodejs": "Node.js",
    "node js": "Node.js",
    "springboot": "Spring Boot",
    "spring-boot": "Spring Boot",
    "elasticsearch": "Elasticsearch",
    "huggingface": "Hugging Face",
    "scikit-learn": "Scikit-learn",
    "sklearn": "Scikit-learn",
    "langchain": "LangChain",
    "llamaindex": "LlamaIndex",
    "llama-index": "LlamaIndex",
    "tensorflow": "TensorFlow",
    "pytorch": "PyTorch",
    "postgres": "PostgreSQL",
    "nestjs": "NestJS",
    "nest.js": "NestJS",
    "nextjs": "Next.js",
    "next.js": "Next.js",
    "expressjs": "Express.js",
    "express.js": "Express.js",
    "tailwindcss": "Tailwind CSS",
    "tailwind": "Tailwind CSS",
    "golang": "Go",
    "dotnet": ".NET",
    "cicd": "CI/CD",
    "genai": "Generative AI",
    "github actions": "GitHub Actions",
    "gitlab ci": "GitLab CI",
    "apache kafka": "Apache Kafka",
    "apache spark": "Apache Spark",
    "apache airflow": "Apache Airflow",
}


def _normalize_tech_entities(entities: list[str]) -> list[str]:
    """Resolve query-time tech aliases to canonical graph names and deduplicate."""
    seen: dict[str, bool] = {}
    result: list[str] = []
    for name in entities:
        canonical = _QUERY_TECH_ALIASES.get(name.lower(), name)
        key = canonical.lower()
        if key not in seen:
            seen[key] = True
            result.append(canonical)
    return result


async def graph_search(query: str) -> dict:
    """
    Trích entity từ query (dictionary + NER model, không dùng LLM) → graph traversal trên Job / Company / Technology.

    Trả về dict:
    {
        "entities":     list[str],   # tech/skill trích được
        "job_titles":   list[str],   # job title keywords trích được
        "companies":    list[dict],  # công ty liên quan
        "jobs":         list[dict],  # job tìm được (theo tech + theo title + theo công ty/địa điểm)
        "related_tech": list[dict],  # tech liên quan qua RELATED_TO
    }
    """
    loop = asyncio.get_event_loop()
    extracted = await loop.run_in_executor(None, extract_query_entities, query)

    tech_entities = _normalize_tech_entities(extracted["technologies"])
    job_title_kws = extracted["job_titles"]
    company_names = extracted["companies"]
    locations     = extracted["locations"]

    if not tech_entities and not job_title_kws and not company_names and not locations:
        return {"entities": [], "job_titles": [], "jobs": [], "companies": [], "related_tech": []}

    names_lower    = [e.lower() for e in tech_entities]
    titles_lower   = [t.lower() for t in job_title_kws]
    companies_lower = [c.lower() for c in company_names]
    locations_lower = [l.lower() for l in locations]

    # --- Job khớp CẢ title lẫn tech (ưu tiên cao nhất) ---
    jobs_by_tech_and_title = await run_query(
        JOBS_BY_TECH_AND_TITLE,
        {"keywords": titles_lower, "names": names_lower},
    ) if names_lower and titles_lower else []

    # --- Job theo tech/skill (REQUIRES relationship) ---
    jobs_by_tech = await run_query(
        JOBS_BY_TECH,
        {"names": names_lower},
    ) if names_lower else []

    # --- Job theo title keyword (CONTAINS matching) ---
    jobs_by_title = await run_query(
        JOBS_BY_TITLE,
        {"keywords": titles_lower},
    ) if titles_lower else []

    # --- Job theo tên công ty (NER ORG) ---
    jobs_by_company = await run_query(
        JOBS_BY_COMPANY,
        {"company_names": companies_lower},
    ) if companies_lower else []

    # --- Job theo địa điểm (NER LOC) ---
    jobs_by_location = await run_query(
        JOBS_BY_LOCATION,
        {"locations": locations_lower},
    ) if locations_lower else []

    # Gộp tất cả nguồn: ưu tiên kết quả khớp cả title lẫn tech
    seen = set()
    jobs = []
    for j in jobs_by_tech_and_title + jobs_by_title + jobs_by_tech + jobs_by_company + jobs_by_location:
        key = j.get("title", "")
        if key not in seen:
            seen.add(key)
            jobs.append(j)

    # Công ty đang dùng các tech này
    companies = await run_query(
        COMPANIES_USING_TECH,
        {"names": names_lower},
    )

    # Tech liên quan (RELATED_TO — 2 chiều)
    related = await run_query(
        TECH_RELATED,
        {"names": names_lower},
    )

    return {
        "entities":     tech_entities,
        "job_titles":   job_title_kws,
        "ner_companies": company_names,
        "ner_locations": locations,
        "jobs":          jobs,
        "companies":     companies,
        "related_tech":  related,
    }
