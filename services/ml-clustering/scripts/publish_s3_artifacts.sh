#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Publish ML clustering runtime artifacts to S3 as plain files.

Required:
  --bucket <bucket>        S3 bucket name, or set BUCKET / MLCLUSTER_S3_BUCKET

Optional:
  --tag <tag>              Snapshot tag. Defaults to params.yaml snapshot.tag
  --prefix <prefix>        S3 prefix. Defaults to ml-clustering
  --manifest-key <key>     S3 manifest key under prefix. Defaults to latest.json
  --no-latest              Upload artifacts only; do not update latest manifest
  --region <region>        AWS region, or set AWS_DEFAULT_REGION / MLCLUSTER_S3_REGION
  --dry-run                Print upload commands without uploading
  -h, --help               Show this help

Examples:
  scripts/publish_s3_artifacts.sh --bucket my-bucket --region ap-southeast-1

  TAG=2026-05-11 BUCKET=my-bucket PREFIX=ml-clustering \
    scripts/publish_s3_artifacts.sh

Files uploaded:
  data/models/<tag>/best_labels.parquet
  data/labels/<tag>/cluster_labels.json
  data/raw/snapshot_<tag>/technologies.parquet
  latest.json              Updated last, unless --no-latest is set
EOF
}

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
module_dir="$(cd "$script_dir/.." && pwd)"

bucket="${BUCKET:-${MLCLUSTER_S3_BUCKET:-}}"
prefix="${PREFIX:-${MLCLUSTER_S3_PREFIX:-ml-clustering}}"
region="${REGION:-${MLCLUSTER_S3_REGION:-${AWS_DEFAULT_REGION:-}}}"
tag="${TAG:-}"
dry_run="false"
publish_latest="${PUBLISH_LATEST:-true}"
manifest_key="${MANIFEST_KEY:-${MLCLUSTER_S3_MANIFEST_KEY:-latest.json}}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bucket)
      bucket="${2:-}"
      shift 2
      ;;
    --prefix)
      prefix="${2:-}"
      shift 2
      ;;
    --region)
      region="${2:-}"
      shift 2
      ;;
    --manifest-key)
      manifest_key="${2:-}"
      shift 2
      ;;
    --tag)
      tag="${2:-}"
      shift 2
      ;;
    --no-latest)
      publish_latest="false"
      shift
      ;;
    --dry-run)
      dry_run="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$bucket" ]]; then
  echo "Missing S3 bucket. Use --bucket or set BUCKET / MLCLUSTER_S3_BUCKET." >&2
  exit 2
fi

if [[ -z "$tag" ]]; then
  tag="$(
    cd "$module_dir"
    python - <<'PY'
from pathlib import Path
import yaml

params = yaml.safe_load(Path("params.yaml").read_text(encoding="utf-8"))
print(params["snapshot"]["tag"])
PY
  )"
fi

if [[ "$dry_run" != "true" ]] && ! command -v aws >/dev/null 2>&1; then
  echo "aws CLI is not installed or not in PATH." >&2
  exit 127
fi

prefix="${prefix#/}"
prefix="${prefix%/}"
manifest_key="${manifest_key#/}"

labels_file="data/models/$tag/best_labels.parquet"
cluster_labels_file="data/labels/$tag/cluster_labels.json"
tech_file="data/raw/snapshot_$tag/technologies.parquet"

required_files=(
  "$labels_file"
  "$cluster_labels_file"
  "$tech_file"
)

cd "$module_dir"

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "Missing required artifact: $module_dir/$file" >&2
    exit 1
  fi
done

aws_args=()
if [[ -n "$region" ]]; then
  aws_args+=(--region "$region")
fi

upload() {
  local src="$1"
  local dest="$2"
  echo "Uploading $src -> $dest"
  if [[ "$dry_run" == "true" ]]; then
    local dry_cmd="aws"
    if ((${#aws_args[@]})); then
      dry_cmd="$dry_cmd ${aws_args[*]}"
    fi
    echo "DRY RUN: $dry_cmd s3 cp $src $dest"
    return 0
  fi
  if ((${#aws_args[@]})); then
    aws "${aws_args[@]}" s3 cp "$src" "$dest"
  else
    aws s3 cp "$src" "$dest"
  fi
}

base_uri="s3://$bucket"
if [[ -n "$prefix" ]]; then
  base_uri="$base_uri/$prefix"
fi

upload "$labels_file" "$base_uri/models/$tag/best_labels.parquet"
upload "$cluster_labels_file" "$base_uri/labels/$tag/cluster_labels.json"
upload "$tech_file" "$base_uri/raw/snapshot_$tag/technologies.parquet"

if [[ "$publish_latest" == "true" ]]; then
  manifest_file="$(mktemp)"
  trap 'rm -f "$manifest_file"' EXIT
  cat >"$manifest_file" <<EOF
{
  "tag": "$tag",
  "created_at": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "artifacts": {
    "best_labels": "models/$tag/best_labels.parquet",
    "cluster_labels": "labels/$tag/cluster_labels.json",
    "technologies": "raw/snapshot_$tag/technologies.parquet"
  }
}
EOF
  upload "$manifest_file" "$base_uri/$manifest_key"
fi

cat <<EOF

Done.

Backend/API environment:
  MLCLUSTER_S3_BUCKET=$bucket
  MLCLUSTER_S3_PREFIX=$prefix
  MLCLUSTER_S3_REGION=$region
  MLCLUSTER_SNAPSHOT_TAG=latest
  MLCLUSTER_S3_MANIFEST_KEY=$manifest_key
  MLCLUSTER_RELOAD_TTL_SECONDS=300

Runtime artifact paths:
  $base_uri/models/$tag/best_labels.parquet
  $base_uri/labels/$tag/cluster_labels.json
  $base_uri/raw/snapshot_$tag/technologies.parquet
  $base_uri/$manifest_key
EOF
