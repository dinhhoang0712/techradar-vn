"""
Gold — Tech Dedup
Gộp các :Technology node trùng lặp do khác cách viết (Go/Golang,
ML/Machine Learning, K8s/Kubernetes...).

Vì sao job này vẫn cần dù TechAliasCache.java + tech_alias_cache.py (Python
Silver) đã chặn phần lớn duplicate NGAY LÚC GHI:
  - Case CHƯA từng biết trước (không có trong dp_tech_alias_map) — cần LLM
    phán đoán, việc mà 2 nơi ghi realtime không làm (tốn phí/độ trễ nếu gọi
    LLM trên từng message).
  - Node ĐÃ TỒN TẠI SẴN trong Neo4j từ TRƯỚC KHI 1 alias được biết đến —
    Cypher MERGE không tự tìm và xoá node cũ, chỉ tạo/dùng lại đúng tên đang
    tra. Job này áp lại toàn bộ alias map (cũ + mới phát hiện) vào node hiện
    có trong graph.

2 giai đoạn, chạy nối tiếp trong 1 lần:
  A. Áp dp_tech_alias_map đã biết trực tiếp vào Neo4j hiện có (rẻ, không cần LLM)
  B. Với tên còn lại chưa có trong alias map — gửi LLM 1 lần, nhóm nào tự tin
     cao thì merge thẳng + ghi vào dp_tech_alias_map (lần sau rẻ hơn); nhóm
     không chắc thì đưa vào dp_tech_alias_review_queue cho người duyệt.

Chạy: định kỳ (mặc định 5:30 sáng, ngay sau neo4j_enricher).
"""

from __future__ import annotations

import json

from common.db import get_neo4j_driver, get_pg_conn, log_pipeline_run
from config import Settings
from loguru import logger

_LLM_PROMPT_TEMPLATE = """Bạn là chuyên gia phân loại công nghệ IT. Dưới đây là danh sách tên công nghệ \
trích xuất tự động từ nhiều nguồn khác nhau (bài viết, job posting), có thể \
có TÊN KHÁC NHAU CHO CÙNG 1 CÔNG NGHỆ do khác nguồn/cách viết (ví dụ viết \
tắt, viết hoa/thường khác, đồng nghĩa).

Danh sách:
{names}

Tìm các NHÓM tên mà bạn CHẮC CHẮN là cùng 1 công nghệ (ví dụ "K8s" và \
"Kubernetes" là CÙNG 1 thứ). Chỉ nhóm khi chắc chắn tuyệt đối — KHÔNG nhóm \
các công nghệ chỉ đơn thuần liên quan/cùng lĩnh vực (ví dụ "React" và "Vue" \
KHÔNG được nhóm, dù cùng là frontend framework — đó là 2 công nghệ khác nhau).

Trả về CHỈ JSON, đúng format:
{{
  "groups": [
    {{"names": ["K8s", "Kubernetes"], "canonical": "Kubernetes", "confidence": "high", "reasoning": "..."}},
    {{"names": ["Postgres", "PostgreSQL"], "canonical": "PostgreSQL", "confidence": "low", "reasoning": "..."}}
  ]
}}
confidence "high" nếu chắc chắn tuyệt đối, "low" nếu có khả năng nhưng không chắc 100%.
Nếu không có nhóm nào, trả {{"groups": []}}.
"""


def _load_alias_map(conn) -> dict[str, str]:
    with conn.cursor() as cur:
        cur.execute("SELECT alias_normalized, canonical_name FROM dp_tech_alias_map")
        rows = cur.fetchall()
    return {r["alias_normalized"]: r["canonical_name"] for r in rows}


def _save_new_aliases(conn, new_aliases: dict[str, str]) -> None:
    if not new_aliases:
        return
    with conn.cursor() as cur:
        for alias_normalized, canonical in new_aliases.items():
            cur.execute(
                """INSERT INTO dp_tech_alias_map (alias_normalized, canonical_name, source)
                   VALUES (%s, %s, 'llm_auto')
                   ON CONFLICT (alias_normalized) DO NOTHING""",
                (alias_normalized, canonical),
            )
    conn.commit()


def _save_review_queue(conn, entries: list[dict]) -> None:
    if not entries:
        return
    with conn.cursor() as cur:
        for e in entries:
            cur.execute(
                """INSERT INTO dp_tech_alias_review_queue (name_a, name_b, llm_reasoning)
                   VALUES (%s, %s, %s)""",
                (e["name_a"], e["name_b"], e.get("reasoning", "")),
            )
    conn.commit()


def _fetch_technology_names(driver) -> list[str]:
    with driver.session() as session:
        result = session.run("MATCH (t:Technology) RETURN DISTINCT t.name AS name")
        return sorted({r["name"] for r in result if r["name"]})


# Quan hệ TRỎ VÀO Technology đã biết trong schema (xem docs/data-platform) —
# không dùng APOC (apoc.refactor.mergeNodes) vì plugin APOC không có sẵn trên
# Neo4j Docker local (chỉ AuraDB cloud có) — viết Cypher thuần, liệt kê rõ
# từng loại quan hệ thay vì merge "mọi loại" chung chung.
_INCOMING_REL_TYPES = ["MENTIONS", "REQUIRES", "USES", "IS_TECHNOLOGY"]


def _merge_duplicate_node(driver, duplicate_name: str, canonical_name: str) -> bool:
    """
    Merge node 'duplicate_name' vào 'canonical_name': chuyển hướng từng loại
    quan hệ đã biết (xem _INCOMING_REL_TYPES + RELATED_TO 2 chiều) sang
    canonical, sau đó DETACH DELETE node phụ (dọn luôn quan hệ loại lạ nếu
    có, dù không redirect được — tradeoff chấp nhận được vì schema đã biết
    trước, không đổi thường xuyên).

    Trả False nếu 1 trong 2 tên không tồn tại hoặc trùng nhau — không raise,
    an toàn để gọi lặp lại (idempotent, dùng MERGE khi tạo lại cạnh).
    """
    with driver.session() as session:
        exists = list(
            session.run(
                """
            MATCH (canonical:Technology {name: $canonical_name})
            MATCH (dup:Technology {name: $dup_name})
            WHERE elementId(canonical) <> elementId(dup)
            RETURN count(*) AS c
            """,
                {"canonical_name": canonical_name, "dup_name": duplicate_name},
            )
        )
        if not exists or exists[0]["c"] == 0:
            return False

        for rel_type in _INCOMING_REL_TYPES:
            session.run(
                f"""
                MATCH (other)-[r:{rel_type}]->(dup:Technology {{name: $dup_name}})
                MATCH (canonical:Technology {{name: $canonical_name}})
                WHERE elementId(canonical) <> elementId(dup)
                MERGE (other)-[:{rel_type}]->(canonical)
                DELETE r
                """,
                {"canonical_name": canonical_name, "dup_name": duplicate_name},
            )

        # RELATED_TO — Technology có thể là nguồn HOẶC đích, xử lý cả 2 chiều
        session.run(
            """
            MATCH (dup:Technology {name: $dup_name})-[r:RELATED_TO]->(other)
            MATCH (canonical:Technology {name: $canonical_name})
            WHERE elementId(canonical) <> elementId(dup) AND elementId(other) <> elementId(canonical)
            MERGE (canonical)-[:RELATED_TO]->(other)
            DELETE r
            """,
            {"canonical_name": canonical_name, "dup_name": duplicate_name},
        )
        session.run(
            """
            MATCH (other)-[r:RELATED_TO]->(dup:Technology {name: $dup_name})
            MATCH (canonical:Technology {name: $canonical_name})
            WHERE elementId(canonical) <> elementId(dup) AND elementId(other) <> elementId(canonical)
            MERGE (other)-[:RELATED_TO]->(canonical)
            DELETE r
            """,
            {"canonical_name": canonical_name, "dup_name": duplicate_name},
        )

        session.run(
            "MATCH (dup:Technology {name: $dup_name}) DETACH DELETE dup",
            {"dup_name": duplicate_name},
        )
    return True


def _parse_llm_response(raw: str) -> list[dict]:
    """Bóc markdown fence nếu có + parse JSON → list group. Tách riêng khỏi
    _call_llm để test được mà không cần gọi API thật."""
    raw = raw.strip()
    if raw.startswith("```"):
        lines = raw.splitlines()
        end = len(lines) - 1 if lines[-1].strip() == "```" else len(lines)
        raw = "\n".join(lines[1:end])

    data = json.loads(raw)
    return data.get("groups", [])


def _call_llm(names: list[str], settings: Settings) -> list[dict]:
    provider = settings.tech_dedup_llm_provider
    prompt = _LLM_PROMPT_TEMPLATE.format(names="\n".join(f"- {n}" for n in names))

    if provider == "openai":
        from openai import OpenAI

        client = OpenAI(api_key=settings.openai_api_key)
        response = client.chat.completions.create(
            model=settings.tech_dedup_openai_model,
            temperature=0.0,
            response_format={"type": "json_object"},
            messages=[{"role": "user", "content": prompt}],
        )
        raw = response.choices[0].message.content
    elif provider == "groq":
        from groq import Groq

        client = Groq(api_key=settings.groq_api_key)
        response = client.chat.completions.create(
            model=settings.tech_dedup_groq_model,
            temperature=0.0,
            response_format={"type": "json_object"},
            messages=[{"role": "user", "content": prompt}],
        )
        raw = response.choices[0].message.content
    else:
        import google.generativeai as genai

        genai.configure(api_key=settings.gemini_api_key)
        model = genai.GenerativeModel(
            model_name=settings.tech_dedup_gemini_model,
            generation_config=genai.GenerationConfig(temperature=0.0, response_mime_type="application/json"),
        )
        raw = model.generate_content(prompt).text

    return _parse_llm_response(raw)


def run(settings: Settings) -> dict:
    logger.info("Tech Dedup: starting...")
    pg_conn = get_pg_conn(settings)
    run_id = log_pipeline_run(pg_conn, "tech_dedup", "running")
    results = {"merged_known_alias": 0, "merged_llm_high_confidence": 0, "sent_to_review": 0}

    try:
        driver = get_neo4j_driver(settings)
        alias_map = _load_alias_map(pg_conn)
        names = _fetch_technology_names(driver)
        logger.info("Tech Dedup: {} Technology name(s) hiện có, {} alias đã biết", len(names), len(alias_map))

        # Giai đoạn A — áp alias map ĐÃ BIẾT trực tiếp vào graph hiện có
        for name in names:
            canonical = alias_map.get(name.strip().lower())
            if canonical and canonical != name:
                if _merge_duplicate_node(driver, name, canonical):
                    results["merged_known_alias"] += 1
                    logger.info("Tech Dedup: merged '{}' -> '{}' (alias đã biết)", name, canonical)

        # Giai đoạn B — tên còn lại chưa có trong alias map, hỏi LLM
        remaining_names = _fetch_technology_names(driver)  # đọc lại sau Giai đoạn A
        known_keys = set(alias_map.keys())
        unresolved = [n for n in remaining_names if n.strip().lower() not in known_keys]

        if len(unresolved) >= 2:
            logger.info("Tech Dedup: {} tên chưa có alias, hỏi LLM...", len(unresolved))
            groups = _call_llm(unresolved, settings)
            new_aliases: dict[str, str] = {}
            review_entries: list[dict] = []

            for group in groups:
                group_names = group.get("names", [])
                canonical = group.get("canonical")
                confidence = group.get("confidence", "low")
                if not canonical or len(group_names) < 2:
                    continue

                if confidence == "high":
                    for n in group_names:
                        if n == canonical:
                            continue
                        new_aliases[n.strip().lower()] = canonical
                        if _merge_duplicate_node(driver, n, canonical):
                            results["merged_llm_high_confidence"] += 1
                            logger.info("Tech Dedup: merged '{}' -> '{}' (LLM tự tin cao)", n, canonical)
                else:
                    for n in group_names:
                        if n == canonical:
                            continue
                        review_entries.append(
                            {
                                "name_a": n,
                                "name_b": canonical,
                                "reasoning": group.get("reasoning", ""),
                            }
                        )
                        results["sent_to_review"] += 1

            _save_new_aliases(pg_conn, new_aliases)
            _save_review_queue(pg_conn, review_entries)
        else:
            logger.info("Tech Dedup: không còn tên nào cần hỏi LLM.")

        driver.close()
        total = sum(results.values())
        logger.info("Tech Dedup: done — {}", results)
        log_pipeline_run(pg_conn, "tech_dedup", "success", rows_affected=total, run_id=run_id)
        return results

    except Exception as exc:
        logger.exception("Tech Dedup failed")
        try:
            log_pipeline_run(pg_conn, "tech_dedup", "failed", error_msg=str(exc), run_id=run_id)
        except Exception:
            pass
        raise
    finally:
        pg_conn.close()
