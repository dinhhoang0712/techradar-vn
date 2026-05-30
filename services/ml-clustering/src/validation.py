from __future__ import annotations

from dataclasses import dataclass, field
from typing import Iterable

import pandas as pd

from conf.config import FeatureParams


@dataclass(frozen=True)
class ValidationIssue:
    check: str
    message: str


@dataclass
class SnapshotValidationReport:
    errors: list[ValidationIssue] = field(default_factory=list)
    warnings: list[ValidationIssue] = field(default_factory=list)
    checks_run: int = 0

    @property
    def ok(self) -> bool:
        return not self.errors

    def add_error(self, check: str, message: str) -> None:
        self.errors.append(ValidationIssue(check, message))

    def add_warning(self, check: str, message: str) -> None:
        self.warnings.append(ValidationIssue(check, message))

    def fail_message(self) -> str:
        lines = ["Snapshot validation failed:"]
        for issue in self.errors:
            lines.append(f"- [{issue.check}] {issue.message}")
        return "\n".join(lines)


class SnapshotValidationError(ValueError):
    def __init__(self, report: SnapshotValidationReport) -> None:
        super().__init__(report.fail_message())
        self.report = report


def _missing_columns(df: pd.DataFrame, required: Iterable[str]) -> list[str]:
    return [col for col in required if col not in df.columns]


def _require_columns(
    report: SnapshotValidationReport,
    name: str,
    df: pd.DataFrame,
    required: Iterable[str],
) -> None:
    report.checks_run += 1
    missing = _missing_columns(df, required)
    if missing:
        report.add_error(f"{name}.schema", f"Missing required columns: {missing}")


def _require_non_empty(report: SnapshotValidationReport, name: str, df: pd.DataFrame) -> None:
    report.checks_run += 1
    if df.empty:
        report.add_error(f"{name}.rows", "DataFrame is empty")


def _require_no_nulls(
    report: SnapshotValidationReport,
    name: str,
    df: pd.DataFrame,
    columns: Iterable[str],
) -> None:
    for col in columns:
        if col not in df.columns:
            continue
        report.checks_run += 1
        n_null = int(df[col].isna().sum())
        if n_null:
            report.add_error(f"{name}.{col}.null", f"{n_null} null values")


def _warn_duplicate_values(
    report: SnapshotValidationReport,
    name: str,
    df: pd.DataFrame,
    column: str,
) -> None:
    if column not in df.columns:
        return
    report.checks_run += 1
    n_dup = int(df[column].duplicated().sum())
    if n_dup:
        report.add_warning(f"{name}.{column}.duplicate", f"{n_dup} duplicate values")


def _require_unique_values(
    report: SnapshotValidationReport,
    name: str,
    df: pd.DataFrame,
    column: str,
) -> None:
    if column not in df.columns:
        return
    report.checks_run += 1
    n_dup = int(df[column].duplicated().sum())
    if n_dup:
        report.add_error(f"{name}.{column}.unique", f"{n_dup} duplicate values")


def _require_non_blank_strings(
    report: SnapshotValidationReport,
    name: str,
    df: pd.DataFrame,
    column: str,
) -> None:
    if column not in df.columns:
        return
    report.checks_run += 1
    values = df[column].fillna("").astype(str).str.strip()
    n_blank = int((values == "").sum())
    if n_blank:
        report.add_error(f"{name}.{column}.blank", f"{n_blank} blank values")


def _check_foreign_key(
    report: SnapshotValidationReport,
    edge_name: str,
    edge_df: pd.DataFrame,
    edge_col: str,
    target_name: str,
    target_ids: set,
    *,
    error: bool,
) -> None:
    if edge_col not in edge_df.columns:
        return

    report.checks_run += 1
    edge_ids = set(edge_df[edge_col].dropna().unique())
    missing = edge_ids - target_ids
    if not missing:
        return

    sample = sorted(map(str, missing))[:5]
    message = (
        f"{len(missing)} distinct {edge_col} values do not exist in {target_name}; "
        f"sample={sample}"
    )
    if error:
        report.add_error(f"{edge_name}.{edge_col}.foreign_key", message)
    else:
        report.add_warning(f"{edge_name}.{edge_col}.foreign_key", message)


def _warn_duplicate_edges(
    report: SnapshotValidationReport,
    edge_name: str,
    edge_df: pd.DataFrame,
    columns: list[str],
) -> None:
    if any(col not in edge_df.columns for col in columns):
        return
    report.checks_run += 1
    n_dup = int(edge_df.duplicated(subset=columns).sum())
    if n_dup:
        report.add_warning(f"{edge_name}.duplicate_edges", f"{n_dup} duplicate edge rows")


def _validate_article_embeddings(
    report: SnapshotValidationReport,
    df_article: pd.DataFrame,
    *,
    required: bool,
    expected_dim: int = 768,
) -> None:
    report.checks_run += 1
    if "embedding" not in df_article.columns:
        if required:
            report.add_error("articles.embedding.schema", "Missing required column: embedding")
        return

    non_null = df_article["embedding"].dropna()
    if non_null.empty:
        if required:
            report.add_error("articles.embedding.coverage", "No article embeddings available")
        else:
            report.add_warning("articles.embedding.coverage", "No article embeddings available")
        return

    bad_dim = 0
    for emb in non_null:
        try:
            if len(emb) != expected_dim:
                bad_dim += 1
        except TypeError:
            bad_dim += 1

    report.checks_run += 1
    if bad_dim:
        report.add_error(
            "articles.embedding.dimension",
            f"{bad_dim} embeddings do not have dimension {expected_dim}",
        )


def validate_stage2_snapshot(
    *,
    df_tech: pd.DataFrame,
    df_company: pd.DataFrame,
    df_article: pd.DataFrame,
    df_job: pd.DataFrame,
    df_edges_mentions: pd.DataFrame,
    df_edges_company_uses_tech: pd.DataFrame,
    df_edges_job_requires_tech: pd.DataFrame,
    df_edges_job_requires_skill: pd.DataFrame,
    df_edges_tech_related: pd.DataFrame,
    feature_params: FeatureParams,
) -> SnapshotValidationReport:
    report = SnapshotValidationReport()

    _require_non_empty(report, "technologies", df_tech)
    if feature_params.use_job_tfidf:
        _require_non_empty(report, "jobs", df_job)
        _require_non_empty(report, "edges_job_requires_tech", df_edges_job_requires_tech)
    if feature_params.use_company_tfidf:
        _require_non_empty(report, "companies", df_company)

    _require_columns(report, "technologies", df_tech, ["tech_id", "name"])
    _require_columns(report, "companies", df_company, ["company_id", "name"])
    _require_columns(report, "articles", df_article, ["article_id"])
    _require_columns(report, "jobs", df_job, ["job_id"])
    _require_columns(report, "edges_article_mentions_tech", df_edges_mentions, ["article_id", "tech_id"])
    _require_columns(report, "edges_company_uses_tech", df_edges_company_uses_tech, ["company_id", "tech_id"])
    _require_columns(report, "edges_job_requires_tech", df_edges_job_requires_tech, ["job_id", "tech_id"])
    _require_columns(report, "edges_job_requires_skill", df_edges_job_requires_skill, ["job_id", "skill_id"])
    _require_columns(report, "edges_tech_related_tech", df_edges_tech_related, ["tech_id_a", "tech_id_b"])

    if feature_params.use_job_tfidf:
        _require_columns(report, "jobs", df_job, ["title", "level"])
    if not feature_params.use_name_embedding and feature_params.article_embedding_aggregation.enabled:
        _require_columns(report, "articles", df_article, ["published_date"])

    _require_no_nulls(report, "technologies", df_tech, ["tech_id", "name"])
    _require_no_nulls(report, "companies", df_company, ["company_id"])
    _require_no_nulls(report, "articles", df_article, ["article_id"])
    _require_no_nulls(report, "jobs", df_job, ["job_id"])
    _require_no_nulls(report, "edges_article_mentions_tech", df_edges_mentions, ["article_id", "tech_id"])
    _require_no_nulls(report, "edges_company_uses_tech", df_edges_company_uses_tech, ["company_id", "tech_id"])
    _require_no_nulls(report, "edges_job_requires_tech", df_edges_job_requires_tech, ["job_id", "tech_id"])
    _require_no_nulls(report, "edges_job_requires_skill", df_edges_job_requires_skill, ["job_id", "skill_id"])
    _require_no_nulls(report, "edges_tech_related_tech", df_edges_tech_related, ["tech_id_a", "tech_id_b"])

    _require_unique_values(report, "technologies", df_tech, "tech_id")
    _require_unique_values(report, "companies", df_company, "company_id")
    _require_unique_values(report, "articles", df_article, "article_id")
    _require_unique_values(report, "jobs", df_job, "job_id")
    _warn_duplicate_values(report, "technologies", df_tech, "name")
    _require_non_blank_strings(report, "technologies", df_tech, "name")

    tech_ids = set(df_tech["tech_id"].dropna().unique()) if "tech_id" in df_tech.columns else set()
    company_ids = set(df_company["company_id"].dropna().unique()) if "company_id" in df_company.columns else set()
    article_ids = set(df_article["article_id"].dropna().unique()) if "article_id" in df_article.columns else set()
    job_ids = set(df_job["job_id"].dropna().unique()) if "job_id" in df_job.columns else set()

    _check_foreign_key(
        report, "edges_article_mentions_tech", df_edges_mentions, "article_id", "articles", article_ids, error=True
    )
    _check_foreign_key(
        report, "edges_article_mentions_tech", df_edges_mentions, "tech_id", "technologies", tech_ids, error=False
    )
    _check_foreign_key(
        report, "edges_company_uses_tech", df_edges_company_uses_tech, "company_id", "companies", company_ids, error=True
    )
    _check_foreign_key(
        report, "edges_company_uses_tech", df_edges_company_uses_tech, "tech_id", "technologies", tech_ids, error=False
    )
    _check_foreign_key(
        report, "edges_job_requires_tech", df_edges_job_requires_tech, "job_id", "jobs", job_ids, error=True
    )
    _check_foreign_key(
        report, "edges_job_requires_tech", df_edges_job_requires_tech, "tech_id", "technologies", tech_ids, error=False
    )
    _check_foreign_key(
        report, "edges_job_requires_skill", df_edges_job_requires_skill, "job_id", "jobs", job_ids, error=True
    )
    _check_foreign_key(
        report, "edges_tech_related_tech", df_edges_tech_related, "tech_id_a", "technologies", tech_ids, error=False
    )
    _check_foreign_key(
        report, "edges_tech_related_tech", df_edges_tech_related, "tech_id_b", "technologies", tech_ids, error=False
    )

    _warn_duplicate_edges(report, "edges_article_mentions_tech", df_edges_mentions, ["article_id", "tech_id"])
    _warn_duplicate_edges(report, "edges_company_uses_tech", df_edges_company_uses_tech, ["company_id", "tech_id"])
    _warn_duplicate_edges(report, "edges_job_requires_tech", df_edges_job_requires_tech, ["job_id", "tech_id"])
    _warn_duplicate_edges(report, "edges_job_requires_skill", df_edges_job_requires_skill, ["job_id", "skill_id"])
    _warn_duplicate_edges(report, "edges_tech_related_tech", df_edges_tech_related, ["tech_id_a", "tech_id_b"])

    require_article_embeddings = (
        not feature_params.use_name_embedding
        and feature_params.article_embedding_aggregation.enabled
    )
    _validate_article_embeddings(report, df_article, required=require_article_embeddings)

    if not report.ok:
        raise SnapshotValidationError(report)
    return report
