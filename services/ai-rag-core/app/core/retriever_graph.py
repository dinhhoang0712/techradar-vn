import asyncio

from app.core.entity_extractor import extract_query_entities
from app.db.neo4j_client import run_query


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

    tech_entities = extracted["technologies"]
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
        """
        UNWIND $keywords AS kw
        MATCH (j:Job)-[:REQUIRES]->(t)
        WHERE toLower(j.title) CONTAINS kw
          AND (t:Technology OR t:Skill) AND toLower(t.name) IN $names
        OPTIONAL MATCH (j)-[:HIRES_FOR]->(c:Company)
        WITH DISTINCT j, c, collect(DISTINCT t.name)[..5] AS techs
        RETURN
            j.title       AS title,
            j.salary      AS salary,
            j.description AS description,
            j.benefit     AS benefit,
            j.requirement AS requirement,
            techs         AS technology,
            c.name        AS company,
            c.location    AS location
        LIMIT 20
        """,
        {"keywords": titles_lower, "names": names_lower},
    ) if names_lower and titles_lower else []

    # --- Job theo tech/skill (REQUIRES relationship) ---
    jobs_by_tech = await run_query(
        """
        MATCH (j:Job)-[:REQUIRES]->(t)
        WHERE (t:Technology OR t:Skill) AND toLower(t.name) IN $names
        OPTIONAL MATCH (j)-[:HIRES_FOR]->(c:Company)
        WITH DISTINCT j, t, c
        RETURN
            j.title       AS title,
            j.salary      AS salary,
            j.description AS description,
            j.benefit     AS benefit,
            j.requirement AS requirement,
            t.name        AS technology,
            c.name        AS company,
            c.location    AS location
        LIMIT 20
        """,
        {"names": names_lower},
    ) if names_lower else []

    # --- Job theo title keyword (CONTAINS matching) ---
    jobs_by_title = await run_query(
        """
        UNWIND $keywords AS kw
        MATCH (j:Job)
        WHERE toLower(j.title) CONTAINS kw
        OPTIONAL MATCH (j)-[:HIRES_FOR]->(c:Company)
        OPTIONAL MATCH (j)-[:REQUIRES]->(t:Technology)
        WITH DISTINCT j, c, collect(DISTINCT t.name)[..3] AS techs
        RETURN
            j.title       AS title,
            j.salary      AS salary,
            j.description AS description,
            j.benefit     AS benefit,
            j.requirement AS requirement,
            techs         AS technology,
            c.name        AS company,
            c.location    AS location
        LIMIT 20
        """,
        {"keywords": titles_lower},
    ) if titles_lower else []

    # --- Job theo tên công ty (NER ORG) ---
    jobs_by_company = await run_query(
        """
        UNWIND $company_names AS cname
        MATCH (j:Job)-[:HIRES_FOR]->(c:Company)
        WHERE toLower(c.name) CONTAINS cname
        OPTIONAL MATCH (j)-[:REQUIRES]->(t:Technology)
        WITH DISTINCT j, c, collect(DISTINCT t.name)[..3] AS techs
        RETURN
            j.title       AS title,
            j.salary      AS salary,
            j.description AS description,
            j.benefit     AS benefit,
            j.requirement AS requirement,
            techs         AS technology,
            c.name        AS company,
            c.location    AS location
        LIMIT 15
        """,
        {"company_names": companies_lower},
    ) if companies_lower else []

    # --- Job theo địa điểm (NER LOC) ---
    jobs_by_location = await run_query(
        """
        UNWIND $locations AS loc
        MATCH (j:Job)-[:HIRES_FOR]->(c:Company)
        WHERE toLower(c.location) CONTAINS loc
        OPTIONAL MATCH (j)-[:REQUIRES]->(t:Technology)
        WITH DISTINCT j, c, collect(DISTINCT t.name)[..3] AS techs
        RETURN
            j.title       AS title,
            j.salary      AS salary,
            j.description AS description,
            j.benefit     AS benefit,
            j.requirement AS requirement,
            techs         AS technology,
            c.name        AS company,
            c.location    AS location
        LIMIT 15
        """,
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
        """
        MATCH (c:Company)-[:USES]->(t:Technology)
        WHERE toLower(t.name) IN $names
        RETURN DISTINCT
            c.name      AS name,
            c.industry  AS industry,
            c.location  AS location,
            c.size      AS size,
            c.rating    AS rating,
            t.name      AS technology
        LIMIT 15
        """,
        {"names": names_lower},
    )

    # Tech liên quan (RELATED_TO — 2 chiều)
    related = await run_query(
        """
        MATCH (t:Technology)-[:RELATED_TO]-(t2:Technology)
        WHERE toLower(t.name) IN $names
        RETURN DISTINCT t.name AS from_tech, t2.name AS related_tech
        LIMIT 20
        """,
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
