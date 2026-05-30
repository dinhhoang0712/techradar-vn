"""
Tự động đặt tên cụm bằng Gemini 2.5 Flash.

Chỉ gọi Gemini sau khi đã chốt clustering tốt nhất (ở Stage 4) — KHÔNG gọi
trong vòng grid search vì sẽ tốn rất nhiều token.

Ý tưởng:
  - Với mỗi cụm, sample top-K thành viên gần tâm cụm nhất (theo cosine với
    centroid) + top công ty hay dùng + top job hay yêu cầu.
  - Đẩy vào prompt template (`prompts/cluster_label.txt`).
  - Parse JSON response.
  - Cache theo hash của input prompt → re-run cùng cụm không tốn token.
"""

from __future__ import annotations

import dataclasses
import hashlib
import json
import time
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pandas as pd
from loguru import logger

from conf.config import LabelingParams, get_settings

_REQUIRED_KEYS = {"label", "label_en", "description", "domain", "confidence", "outliers"}

# Lazy template cache: path → content
_template_cache: dict[str, str] = {}


@dataclass
class ClusterLabel:
    """
    Output cuối cùng cho 1 cụm.

    Fields:
        cluster_id:      id cụm (int).
        label:           tên ngắn (Tiếng Việt).
        label_en:        tên ngắn (English).
        description:     mô tả 1-2 câu.
        domain:          domain chuẩn hoá (xem prompt).
        confidence:      0..1.
        is_coherent:     False nếu cụm không có chủ đề rõ ràng (cần review).
        coherence_reason: lý do nếu is_coherent=False.
        outliers:        list tech không khớp chủ đề.
        member_count:    số thành viên cụm.
        sample_techs:    tech đại diện đã đưa vào prompt.
    """
    cluster_id: int
    label: str
    label_en: str
    description: str
    domain: str
    confidence: float
    is_coherent: bool
    coherence_reason: str
    outliers: list[str]
    member_count: int
    sample_techs: list[str]


def map_cluster_to_members(
    labels: np.ndarray,
    tech_ids: list[str],
) -> dict[int, list[str]]:
    """
    Build `{cluster_id: [tech_id, ...]}` từ mảng labels đã căn thứ tự với tech_ids.
    Bao gồm cả cluster -1 (noise) để caller có thể xử lý riêng.
    """
    result: dict[int, list[str]] = {}
    for tech_id, cluster_id in zip(tech_ids, labels.tolist()):
        cid = int(cluster_id)
        result.setdefault(cid, []).append(tech_id)
    return result


def select_top_members_per_cluster(
    cluster_to_members: dict[int, list[str]],
    X: np.ndarray,
    tech_ids: list[str],
    df_technologies: pd.DataFrame,
    top_k: int = 25,
) -> dict[int, list[str]]:
    """
    Chọn top-K tech gần tâm cụm nhất → đại diện cho cụm khi đẩy vào prompt.

    Yêu cầu logic:
      - Với mỗi cluster: tính centroid = mean của X[member_indices].
      - Sort member theo cosine_similarity(X[member], centroid) DESC, lấy top-K.
      - Map index → name từ `df_technologies`.
      - Bỏ qua cluster -1 (noise).
      - Trả `{cluster_id: [tech_name, ...]}`.
    """
    id_to_idx = {tid: i for i, tid in enumerate(tech_ids)}
    id_to_name: dict[str, str] = df_technologies.set_index("tech_id")["name"].to_dict()

    result: dict[int, list[str]] = {}
    for cluster_id, members in cluster_to_members.items():
        if cluster_id == -1:
            continue

        valid = [(mid, id_to_idx[mid]) for mid in members if mid in id_to_idx]
        if not valid:
            result[cluster_id] = []
            continue

        member_ids, indices = zip(*valid)
        member_X = X[list(indices)]
        centroid: np.ndarray = member_X.mean(axis=0)
        centroid_norm = float(np.linalg.norm(centroid))

        if centroid_norm == 0.0:
            top_ids = list(member_ids)[:top_k]
        else:
            sims: list[tuple[str, float]] = []
            for mid, idx in valid:
                row = X[idx]
                row_norm = float(np.linalg.norm(row))
                sim = float(np.dot(row, centroid) / (row_norm * centroid_norm)) if row_norm > 0 else 0.0
                sims.append((mid, sim))
            sims.sort(key=lambda x: x[1], reverse=True)
            top_ids = [mid for mid, _ in sims[:top_k]]

        result[cluster_id] = [id_to_name.get(mid, mid) for mid in top_ids]

    return result


def collect_cluster_context(
    cluster_to_member_techs: dict[int, list[str]],
    df_edges_company_uses_tech: pd.DataFrame,
    df_companies: pd.DataFrame,
    df_edges_job_requires_tech: pd.DataFrame,
    df_jobs: pd.DataFrame,
    top_n_companies: int = 8,
    top_n_jobs: int = 8,
) -> dict[int, dict]:
    """
    Với mỗi cụm, tổng hợp:
      - top công ty USES nhiều tech của cụm nhất.
      - top job title yêu cầu nhiều tech của cụm nhất.

    Trả về `{cluster_id: {"top_companies": [name…], "top_jobs": [title…]}}`.
    """
    company_name: dict[str, str] = df_companies.set_index("company_id")["name"].to_dict()
    job_title: dict[str, str] = df_jobs.set_index("job_id")["title"].to_dict()

    result: dict[int, dict] = {}
    for cluster_id, tech_ids in cluster_to_member_techs.items():
        tech_set = set(tech_ids)

        if not df_edges_company_uses_tech.empty and "tech_id" in df_edges_company_uses_tech.columns:
            comp_edges = df_edges_company_uses_tech[
                df_edges_company_uses_tech["tech_id"].isin(tech_set)
            ]
            top_companies = (
                comp_edges["company_id"]
                .value_counts()
                .head(top_n_companies)
                .index
                .map(lambda cid: company_name.get(cid, cid))
                .tolist()
            )
        else:
            top_companies = []

        if not df_edges_job_requires_tech.empty and "tech_id" in df_edges_job_requires_tech.columns:
            job_edges = df_edges_job_requires_tech[
                df_edges_job_requires_tech["tech_id"].isin(tech_set)
            ]
            top_job_ids = (
                job_edges["job_id"]
                .value_counts()
                .head(top_n_jobs)
                .index
                .tolist()
            )
            top_jobs = [job_title.get(jid, jid) for jid in top_job_ids]
        else:
            top_jobs = []

        result[cluster_id] = {"top_companies": top_companies, "top_jobs": top_jobs}

    return result


def render_prompt(
    template_path: str | Path,
    cluster_id: int,
    n_members: int,
    top_members: list[str],
) -> str:
    """
    Đọc template `prompts/cluster_label.txt` rồi `.format(...)` an toàn.

    Yêu cầu:
      - Lazy đọc file 1 lần, cache nội dung.
      - Format các list thành chuỗi bullet `- item`.
      - Cắt nếu danh sách quá dài (giữ tối đa params.max_members_in_prompt).
      - KHÔNG dùng f-string với `.format()` đồng thời để tránh xung đột `{}`.
    """
    path_str = str(Path(template_path).resolve())
    if path_str not in _template_cache:
        _template_cache[path_str] = Path(template_path).read_text(encoding="utf-8")
    template = _template_cache[path_str]

    def to_bullets(items: list[str]) -> str:
        return "\n".join("- " + item for item in items) if items else "(không có dữ liệu)"

    return template.format(
        cluster_id=cluster_id,
        n_members=n_members,
        top_members=to_bullets(top_members),
    )


def _call_llm_raw(prompt: str, params: LabelingParams) -> str:
    """Gọi LLM (OpenAI hoặc Gemini) và trả về raw text."""
    provider = getattr(params, "provider", "gemini")

    if provider == "openai":
        from openai import OpenAI
        client = OpenAI(api_key=get_settings().openai_api_key)
        response = client.chat.completions.create(
            model=params.openai_model,
            temperature=params.temperature,
            response_format={"type": "json_object"},
            messages=[{"role": "user", "content": prompt}],
        )
        return response.choices[0].message.content.strip()
    else:
        import google.generativeai as genai
        api_key = get_settings().gemini_api_key
        genai.configure(api_key=api_key)
        model = genai.GenerativeModel(
            model_name=params.gemini_model,
            generation_config=genai.GenerationConfig(
                temperature=params.temperature,
                response_mime_type="application/json",
            ),
        )
        response = model.generate_content(prompt)
        return response.text.strip()


def call_gemini(
    prompt: str,
    params: LabelingParams,
    cache_dir: str | Path | None = None,
) -> dict:
    """
    Gọi LLM (OpenAI GPT hoặc Gemini), parse output thành dict.

    - Cache key = sha256(prompt) → file JSON trong `cache_dir`.
    - Retry 4 lần với backoff khi lỗi.
    - Validate JSON: keys ["label","label_en","description","domain","confidence","outliers"].
    - Strip markdown fence nếu có.
    """
    cache_key = hashlib.sha256(prompt.encode("utf-8")).hexdigest()

    if cache_dir is not None:
        cache_path = Path(cache_dir) / f"{cache_key}.json"
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        if cache_path.exists():
            logger.debug("Cache hit: {}", cache_key[:12])
            return json.loads(cache_path.read_text(encoding="utf-8"))
    else:
        cache_path = None

    provider = getattr(params, "provider", "gemini")
    last_exc: Exception | None = None
    for attempt in range(4):
        if attempt > 0:
            wait = 2 ** attempt  # 2s, 4s, 8s
            logger.warning("{} retry {}/3 sau {}s (lỗi: {})", provider, attempt, wait, last_exc)
            time.sleep(wait)
        try:
            text = _call_llm_raw(prompt, params)

            # Strip markdown fence nếu có
            if text.startswith("```"):
                lines = text.splitlines()
                start = 1
                end = len(lines) - 1 if lines[-1].strip() == "```" else len(lines)
                text = "\n".join(lines[start:end]).strip()

            data: dict = json.loads(text)

            missing = _REQUIRED_KEYS - set(data.keys())
            if missing:
                raise ValueError("Thiếu keys trong response: " + str(missing))

            if cache_path is not None:
                cache_path.write_text(
                    json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
                )
            return data

        except Exception as exc:
            last_exc = exc
            continue

    raise RuntimeError(f"{provider} thất bại sau 4 lần thử: {last_exc}") from last_exc


def label_all_clusters(
    cluster_to_members: dict[int, list[str]],
    X: np.ndarray,
    tech_ids: list[str],
    df_technologies: pd.DataFrame,
    df_edges_company_uses_tech: pd.DataFrame,
    df_companies: pd.DataFrame,
    df_edges_job_requires_tech: pd.DataFrame,
    df_jobs: pd.DataFrame,
    params: LabelingParams,
) -> dict[int, ClusterLabel]:
    """
    Orchestrator của Stage 4: gán nhãn cho mọi cụm.

    Yêu cầu logic:
      1. `select_top_members_per_cluster` → top members.
      2. Với mỗi cluster_id (≠ -1):
           render_prompt → call_gemini → parse → tạo `ClusterLabel`.
      3. Bỏ cluster -1 (noise) — không gọi LLM.
      4. Log progress (loguru) sau mỗi cụm; KHÔNG dừng cả batch nếu 1 cụm fail
         (đặt label="UNLABELED" + ghi failure_reason).
      5. Trả về dict {cluster_id: ClusterLabel}.
    """
    prompt_template_path = Path(__file__).parent / "prompts" / "cluster_label.txt"

    top_members_by_name = select_top_members_per_cluster(
        cluster_to_members, X, tech_ids, df_technologies,
        top_k=params.max_members_in_prompt,
    )
    cluster_ids = sorted(cid for cid in cluster_to_members if cid != -1)
    logger.info("Bắt đầu gán nhãn {} cụm với {} ...", len(cluster_ids), params.provider.upper())

    result: dict[int, ClusterLabel] = {}
    for cluster_id in cluster_ids:
        members = cluster_to_members[cluster_id]
        top_names = top_members_by_name.get(cluster_id, [])

        try:
            prompt = render_prompt(
                prompt_template_path,
                cluster_id=cluster_id,
                n_members=len(members),
                top_members=top_names,
            )
            data = call_gemini(prompt, params, cache_dir=params.cache_dir)
            time.sleep(5)  # tránh rate limit Gemini (free tier ~10 RPM)
            is_coherent = bool(data.get("is_coherent", True))
            allowed_outliers = set(top_names)
            outliers = [
                item for item in list(data.get("outliers", []))
                if item in allowed_outliers
            ]
            label = ClusterLabel(
                cluster_id=cluster_id,
                label=data["label"],
                label_en=data["label_en"],
                description=data["description"],
                domain=data["domain"],
                confidence=float(data["confidence"]),
                is_coherent=is_coherent,
                coherence_reason=data.get("coherence_reason", ""),
                outliers=outliers,
                member_count=len(members),
                sample_techs=top_names,
            )
            coherence_tag = "" if is_coherent else " ⚠️ INCOHERENT"
            logger.info(
                "[{}/{}] Cluster {} → '{}' (domain={}, conf={:.2f}){}",
                cluster_ids.index(cluster_id) + 1,
                len(cluster_ids),
                cluster_id,
                label.label,
                label.domain,
                label.confidence,
                coherence_tag,
            )
        except Exception as exc:
            logger.error("Cluster {} thất bại: {}", cluster_id, exc)
            label = ClusterLabel(
                cluster_id=cluster_id,
                label="UNLABELED",
                label_en="UNLABELED",
                description=str(exc),
                domain="Other",
                confidence=0.0,
                is_coherent=False,
                coherence_reason="Labeling failed",
                outliers=[],
                member_count=len(members),
                sample_techs=top_names,
            )

        result[cluster_id] = label

    n_ok = sum(1 for lb in result.values() if lb.label != "UNLABELED")
    logger.info("Hoàn tất: {}/{} cụm gán nhãn thành công.", n_ok, len(cluster_ids))
    return result


def save_cluster_labels(
    labels: dict[int, ClusterLabel],
    out_path: str | Path,
) -> None:
    """
    Ghi labels ra `cluster_labels.json` (indent=2, ensure_ascii=False).

    Yêu cầu: cluster_id là string trong JSON (json không cho key int).
    """
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    data = {str(k): dataclasses.asdict(v) for k, v in sorted(labels.items())}
    out.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    logger.info("cluster_labels.json → {}", out)
