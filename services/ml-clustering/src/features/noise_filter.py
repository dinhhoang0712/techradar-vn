"""
Lọc noise Technology nodes trước khi build feature matrix.

Áp dụng theo thứ tự:
  1. min_job_count  — loại tech xuất hiện trong quá ít job
  2. blocklist      — loại theo tên cụ thể (case-insensitive)
  3. heuristic_patterns — loại theo regex pattern
"""

from __future__ import annotations

import re
import logging

import pandas as pd

from conf.config import NoiseFilterParams

logger = logging.getLogger(__name__)

# Whitelist các tên kỹ thuật ngắn hợp lệ mà heuristic có thể nhầm
_SHORT_TECH_WHITELIST = {
    # Ngôn ngữ & runtime
    "c", "r", "go",
    # AI/ML
    "ai", "ml", "dl", "llm", "rag", "nlp", "ocr", "gpt",
    # Frontend
    "js", "ts", "css", "vue", "ios",
    # Backend / DB
    "sql", "api", "git", "net", "orm", "xml", "rpc", "gin",
    # Data
    "etl", "dwh", "dbt", "sas", "elk", "dvc",
    # DevOps / Cloud
    "aws", "gcp", "sdk", "ide", "svn",
    # Hardware
    "gpu", "cpu", "tpu", "npu", "iot", "nfc",
    # Networking / Security
    "vpc", "vpn", "dns", "ssh", "tcp", "udp", "http", "rest",
    "bgp", "nat", "sip", "sse", "rpc",
    "soc", "ips", "waf", "ids", "jwt", "otp",
    # Business / Other
    "ui", "ux", "qa", "it", "rpa", "erp", "crm",
    "stt", "qr", "kyc", "mes",
    # Vendor / Platform
    "sap", "php", "ios",
}


def filter_noise(
    df_tech: pd.DataFrame,
    df_edges_job_requires_tech: pd.DataFrame,
    params: NoiseFilterParams,
) -> pd.DataFrame:
    """
    Lọc noise từ df_tech, trả về df_tech đã làm sạch.

    Args:
        df_tech:                    DataFrame với cột tech_id, name, ...
        df_edges_job_requires_tech: DataFrame với cột tech_id, job_id
        params:                     NoiseFilterParams từ config

    Returns:
        df_tech đã bỏ các noise node
    """
    if not params.enabled:
        return df_tech

    original_count = len(df_tech)
    keep_mask = pd.Series(True, index=df_tech.index)

    # 1. min_job_count — loại tech quá hiếm
    if params.min_job_count > 1:
        job_counts = (
            df_edges_job_requires_tech.groupby("tech_id")["job_id"]
            .nunique()
            .rename("job_count")
        )
        df_with_count = df_tech.join(job_counts, on="tech_id")
        too_rare = df_with_count["job_count"].fillna(0) < params.min_job_count
        n_rare = too_rare.sum()
        if n_rare > 0:
            logger.info("noise_filter min_job_count<{}: loại {} tech", params.min_job_count, n_rare)
        keep_mask &= ~too_rare

    # 2. Blocklist — so sánh case-insensitive
    if params.blocklist:
        blocklist_lower = {b.lower() for b in params.blocklist}
        in_blocklist = df_tech["name"].str.lower().isin(blocklist_lower)
        n_blocked = in_blocklist.sum()
        if n_blocked > 0:
            blocked_names = df_tech.loc[in_blocklist, "name"].tolist()
            logger.info("noise_filter blocklist: loại {} tech: {}", n_blocked, blocked_names)
        keep_mask &= ~in_blocklist

    # 3. Heuristic patterns
    if params.heuristic_patterns:
        combined_pattern = "|".join(f"(?:{p})" for p in params.heuristic_patterns)
        regex = re.compile(combined_pattern, re.IGNORECASE | re.UNICODE)

        def is_noise_by_pattern(name: str) -> bool:
            if name.lower() in _SHORT_TECH_WHITELIST:
                return False
            return bool(regex.match(name))

        by_pattern = df_tech["name"].apply(is_noise_by_pattern)
        n_pattern = by_pattern.sum()
        if n_pattern > 0:
            pattern_names = df_tech.loc[by_pattern, "name"].tolist()
            logger.info("noise_filter heuristic: loại {} tech: {}", n_pattern, pattern_names)
        keep_mask &= ~by_pattern

    df_filtered = df_tech[keep_mask].reset_index(drop=True)
    removed = original_count - len(df_filtered)
    logger.info(
        "noise_filter hoàn tất: {} → {} tech (loại {} noise)",
        original_count, len(df_filtered), removed,
    )
    return df_filtered
