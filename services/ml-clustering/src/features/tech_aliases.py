from __future__ import annotations

import logging
import re
from dataclasses import dataclass

import pandas as pd

logger = logging.getLogger(__name__)


TECH_ALIAS_MAP: dict[str, str] = {
    "3ds max": "3ds Max",
    "adobe illustrator": "Adobe Illustrator",
    "adobe premiere": "Adobe Premiere",
    "ai agent": "AI Agent",
    "ai inference": "AI Inference",
    "angularjs": "AngularJS",
    "angular js": "AngularJS",
    "api": "API",
    "asp net": "ASP.NET",
    "aws": "AWS",
    "azure": "Azure",
    "bigquery": "BigQuery",
    "bitbucket": "Bitbucket",
    "capcut": "CapCut",
    "chatbot": "Chatbot",
    "chatgpt": "ChatGPT",
    "ci cd": "CI/CD",
    "circleci": "CircleCI",
    "cloudfront": "CloudFront",
    "computer vision": "Computer Vision",
    "copilot": "Copilot",
    "css": "CSS",
    "dart": "Dart",
    "data center": "Data Center",
    "dbt": "dbt",
    "deep learning": "Deep Learning",
    "django": "Django",
    "docker": "Docker",
    "dynamodb": "DynamoDB",
    "elasticsearch": "Elasticsearch",
    "express": "Express.js",
    "expressjs": "Express.js",
    "express js": "Express.js",
    "fastapi": "FastAPI",
    "figma": "Figma",
    "firebase": "Firebase",
    "firestore": "Firestore",
    "flask": "Flask",
    "gcp": "GCP",
    "genai": "GenAI",
    "generative ai": "Generative AI",
    "git": "Git",
    "github": "GitHub",
    "github actions": "GitHub Actions",
    "gitlab": "GitLab",
    "gitlab ci": "GitLab CI",
    "graphql": "GraphQL",
    "grpc": "gRPC",
    "html": "HTML",
    "hybrid cloud": "Hybrid Cloud",
    "ids": "IDS",
    "indesign": "InDesign",
    "iot": "IoT",
    "java": "Java",
    "javascript": "JavaScript",
    "jenkins": "Jenkins",
    "jquery": "jQuery",
    "json": "JSON",
    "k8s": "Kubernetes",
    "kubernetes": "Kubernetes",
    "kotlin": "Kotlin",
    "kubeflow": "Kubeflow",
    "less": "Less",
    "linux": "Linux",
    "llama": "LLaMA",
    "machine learning": "Machine Learning",
    "học máy": "Machine Learning",
    "matlab": "MATLAB",
    "matplotlib": "Matplotlib",
    "microservices": "Microservices",
    "mlflow": "MLflow",
    "mocha": "Mocha",
    "mongodb": "MongoDB",
    "mysql": "MySQL",
    "n8n": "n8n",
    "nestjs": "NestJS",
    "nest js": "NestJS",
    "net": ".NET",
    "nginx": "Nginx",
    "nextjs": "Next.js",
    "next js": "Next.js",
    "node js": "Node.js",
    "nodejs": "Node.js",
    "numpy": "NumPy",
    "pandas": "Pandas",
    "php": "PHP",
    "postgresql": "PostgreSQL",
    "power bi": "Power BI",
    "private cloud": "Private Cloud",
    "public cloud": "Public Cloud",
    "python": "Python",
    "pytorch": "PyTorch",
    "react": "React",
    "react js": "React",
    "reactjs": "React",
    "redis": "Redis",
    "rest": "REST",
    "restful": "RESTful",
    "ruby on rails": "Ruby on Rails",
    "serverless": "Serverless",
    "sketchup": "SketchUp",
    "snowflake": "Snowflake",
    "sql": "SQL",
    "sql server": "SQL Server",
    "sqlite": "SQLite",
    "tailwindcss": "Tailwind CSS",
    "tailwind css": "Tailwind CSS",
    "tensorflow": "TensorFlow",
    "trí tuệ nhân tạo": "Artificial Intelligence",
    "typescript": "TypeScript",
    "vuejs": "Vue.js",
    "vue js": "Vue.js",
}

_SEP_RE = re.compile(r"[\s._/\-]+")


@dataclass(frozen=True)
class CanonicalizationResult:
    technologies: pd.DataFrame
    edges_article_mentions_tech: pd.DataFrame
    edges_company_uses_tech: pd.DataFrame
    edges_job_requires_tech: pd.DataFrame
    edges_tech_related_tech: pd.DataFrame
    tech_id_map: dict[str, str]
    canonical_name_map: dict[str, str]
    n_merged: int


def normalize_tech_key(name: str) -> str:
    text = str(name or "").strip().casefold()
    text = _SEP_RE.sub(" ", text)
    return " ".join(text.split())


def canonical_tech_name(name: str) -> str:
    key = normalize_tech_key(name)
    return TECH_ALIAS_MAP.get(key, str(name or "").strip())


def _canonical_group_key(name: str) -> str:
    return normalize_tech_key(canonical_tech_name(name))


def _choose_representatives(
    df_tech: pd.DataFrame,
    df_edges_job_requires_tech: pd.DataFrame,
) -> tuple[dict[str, str], dict[str, str], dict[str, int]]:
    job_counts = (
        df_edges_job_requires_tech.groupby("tech_id")["job_id"].nunique().to_dict()
        if {"tech_id", "job_id"}.issubset(df_edges_job_requires_tech.columns)
        else {}
    )

    tech_id_map: dict[str, str] = {}
    canonical_name_map: dict[str, str] = {}
    group_sizes: dict[str, int] = {}

    df = df_tech[["tech_id", "name"]].copy()
    df["_canonical_name"] = df["name"].map(canonical_tech_name)
    df["_group_key"] = df["name"].map(_canonical_group_key)
    df["_job_count"] = df["tech_id"].map(job_counts).fillna(0).astype(int)
    df["_is_exact_canonical"] = df["name"].str.strip() == df["_canonical_name"]
    df["_name_len"] = df["name"].astype(str).str.len()

    for group_key, group in df.groupby("_group_key", sort=False):
        ordered = group.sort_values(
            by=["_is_exact_canonical", "_job_count", "_name_len", "name", "tech_id"],
            ascending=[False, False, True, True, True],
            kind="mergesort",
        )
        representative = ordered.iloc[0]
        canonical_id = str(representative["tech_id"])
        canonical_name = str(representative["_canonical_name"])
        group_sizes[group_key] = len(group)

        for tech_id in group["tech_id"].astype(str):
            tech_id_map[tech_id] = canonical_id
            canonical_name_map[tech_id] = canonical_name

    return tech_id_map, canonical_name_map, group_sizes


def _rewrite_edge_column(df: pd.DataFrame, column: str, tech_id_map: dict[str, str]) -> pd.DataFrame:
    if df.empty or column not in df.columns:
        return df
    out = df.copy()
    out[column] = out[column].map(lambda value: tech_id_map.get(str(value), value))
    return out.drop_duplicates().reset_index(drop=True)


def canonicalize_technology_snapshot(
    *,
    df_tech: pd.DataFrame,
    df_edges_mentions: pd.DataFrame,
    df_edges_company_uses_tech: pd.DataFrame,
    df_edges_job_requires_tech: pd.DataFrame,
    df_edges_tech_related: pd.DataFrame,
) -> CanonicalizationResult:
    if df_tech.empty or not {"tech_id", "name"}.issubset(df_tech.columns):
        return CanonicalizationResult(
            technologies=df_tech,
            edges_article_mentions_tech=df_edges_mentions,
            edges_company_uses_tech=df_edges_company_uses_tech,
            edges_job_requires_tech=df_edges_job_requires_tech,
            edges_tech_related_tech=df_edges_tech_related,
            tech_id_map={},
            canonical_name_map={},
            n_merged=0,
        )

    tech_id_map, canonical_name_map, group_sizes = _choose_representatives(
        df_tech, df_edges_job_requires_tech
    )
    canonical_ids = set(tech_id_map.values())
    n_merged = len(df_tech) - len(canonical_ids)

    df_canonical = df_tech[df_tech["tech_id"].astype(str).isin(canonical_ids)].copy()
    df_canonical["name"] = df_canonical["tech_id"].astype(str).map(canonical_name_map)
    df_canonical = df_canonical.drop_duplicates(subset=["tech_id"]).reset_index(drop=True)

    rewritten_mentions = _rewrite_edge_column(df_edges_mentions, "tech_id", tech_id_map)
    rewritten_company = _rewrite_edge_column(df_edges_company_uses_tech, "tech_id", tech_id_map)
    rewritten_job = _rewrite_edge_column(df_edges_job_requires_tech, "tech_id", tech_id_map)
    rewritten_related = _rewrite_edge_column(df_edges_tech_related, "tech_id_a", tech_id_map)
    rewritten_related = _rewrite_edge_column(rewritten_related, "tech_id_b", tech_id_map)
    if {"tech_id_a", "tech_id_b"}.issubset(rewritten_related.columns):
        rewritten_related = rewritten_related[
            rewritten_related["tech_id_a"] != rewritten_related["tech_id_b"]
        ].reset_index(drop=True)

    original_names = df_tech.set_index("tech_id")["name"].astype(str).to_dict()
    changed_names = sum(
        1
        for original_id, canonical_name in canonical_name_map.items()
        if original_names.get(original_id, "").strip() != canonical_name
    )
    merged_groups = sum(1 for size in group_sizes.values() if size > 1)
    logger.info(
        "canonicalize_technology_snapshot: %d -> %d techs, merged=%d, groups=%d, renamed=%d",
        len(df_tech),
        len(df_canonical),
        n_merged,
        merged_groups,
        changed_names,
    )

    return CanonicalizationResult(
        technologies=df_canonical,
        edges_article_mentions_tech=rewritten_mentions,
        edges_company_uses_tech=rewritten_company,
        edges_job_requires_tech=rewritten_job,
        edges_tech_related_tech=rewritten_related,
        tech_id_map=tech_id_map,
        canonical_name_map=canonical_name_map,
        n_merged=n_merged,
    )
