#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Publish ML clustering runtime artifacts to MinIO as plain objects.

Required:
  --bucket <bucket>        MinIO bucket name, or set BUCKET / MLCLUSTER_MINIO_BUCKET

Optional:
  --tag <tag>              Snapshot tag. Defaults to params.yaml snapshot.tag
  --prefix <prefix>        Object key prefix. Defaults to ml-clustering
  --manifest-key <key>     Manifest key under prefix. Defaults to latest.json
  --no-latest              Upload artifacts only; do not update latest manifest
  --endpoint <url>         MinIO endpoint, or set ENDPOINT / MLCLUSTER_MINIO_ENDPOINT
                           (default: http://localhost:9000)
  --access-key <key>       Or set ACCESS_KEY / MLCLUSTER_MINIO_ACCESS_KEY (default: minioadmin)
  --secret-key <key>       Or set SECRET_KEY / MLCLUSTER_MINIO_SECRET_KEY (default: minioadmin123)
  --dry-run                Print what would be uploaded without uploading
  -h, --help               Show this help

Examples:
  scripts/publish_minio_artifacts.sh --bucket ml-clustering

  TAG=2026-05-11 BUCKET=ml-clustering scripts/publish_minio_artifacts.sh

Files uploaded:
  data/models/<tag>/best_labels.parquet
  data/labels/<tag>/cluster_labels.json
  data/raw/snapshot_<tag>/technologies.parquet
  latest.json              Updated last, unless --no-latest is set
EOF
}

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
module_dir="$(cd "$script_dir/.." && pwd)"

bucket="${BUCKET:-${MLCLUSTER_MINIO_BUCKET:-}}"
prefix="${PREFIX:-${MLCLUSTER_MINIO_PREFIX:-ml-clustering}}"
endpoint="${ENDPOINT:-${MLCLUSTER_MINIO_ENDPOINT:-http://localhost:9000}}"
access_key="${ACCESS_KEY:-${MLCLUSTER_MINIO_ACCESS_KEY:-minioadmin}}"
secret_key="${SECRET_KEY:-${MLCLUSTER_MINIO_SECRET_KEY:-minioadmin123}}"
tag="${TAG:-}"
dry_run="false"
publish_latest="${PUBLISH_LATEST:-true}"
manifest_key="${MANIFEST_KEY:-${MLCLUSTER_MINIO_MANIFEST_KEY:-latest.json}}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bucket) bucket="${2:-}"; shift 2 ;;
    --prefix) prefix="${2:-}"; shift 2 ;;
    --endpoint) endpoint="${2:-}"; shift 2 ;;
    --access-key) access_key="${2:-}"; shift 2 ;;
    --secret-key) secret_key="${2:-}"; shift 2 ;;
    --manifest-key) manifest_key="${2:-}"; shift 2 ;;
    --tag) tag="${2:-}"; shift 2 ;;
    --no-latest) publish_latest="false"; shift ;;
    --dry-run) dry_run="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "$bucket" ]]; then
  echo "Missing MinIO bucket. Use --bucket or set BUCKET / MLCLUSTER_MINIO_BUCKET." >&2
  exit 2
fi

if [[ -z "$tag" ]]; then
  tag="$(
    cd "$module_dir"
    python3 - <<'PY'
from pathlib import Path
import yaml

params = yaml.safe_load(Path("params.yaml").read_text(encoding="utf-8"))
print(params["snapshot"]["tag"])
PY
  )"
fi

if ! python3 -c "import boto3" >/dev/null 2>&1; then
  echo "Python package 'boto3' is required (pip install boto3)." >&2
  exit 127
fi

prefix="${prefix#/}"
prefix="${prefix%/}"
manifest_key="${manifest_key#/}"

labels_file="data/models/$tag/best_labels.parquet"
cluster_labels_file="data/labels/$tag/cluster_labels.json"
tech_file="data/raw/snapshot_$tag/technologies.parquet"

cd "$module_dir"

for file in "$labels_file" "$cluster_labels_file" "$tech_file"; do
  if [[ ! -f "$file" ]]; then
    echo "Missing required artifact: $module_dir/$file" >&2
    exit 1
  fi
done

manifest_file=""
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
fi

base_uri="s3://$bucket"
if [[ -n "$prefix" ]]; then
  base_uri="$base_uri/$prefix"
fi

echo "Uploading $labels_file -> $base_uri/models/$tag/best_labels.parquet"
echo "Uploading $cluster_labels_file -> $base_uri/labels/$tag/cluster_labels.json"
echo "Uploading $tech_file -> $base_uri/raw/snapshot_$tag/technologies.parquet"
if [[ -n "$manifest_file" ]]; then
  echo "Uploading latest manifest -> $base_uri/$manifest_key"
fi

if [[ "$dry_run" == "true" ]]; then
  echo "DRY RUN: no objects uploaded."
else
  ENDPOINT="$endpoint" ACCESS_KEY="$access_key" SECRET_KEY="$secret_key" \
  BUCKET="$bucket" PREFIX="$prefix" TAG="$tag" MANIFEST_KEY="$manifest_key" \
  LABELS_FILE="$labels_file" CLUSTER_LABELS_FILE="$cluster_labels_file" \
  TECH_FILE="$tech_file" MANIFEST_FILE="$manifest_file" \
  python3 - <<'PY'
import os
import boto3
from botocore.config import Config

client = boto3.client(
    "s3",
    endpoint_url=os.environ["ENDPOINT"],
    region_name="us-east-1",
    aws_access_key_id=os.environ["ACCESS_KEY"],
    aws_secret_access_key=os.environ["SECRET_KEY"],
    config=Config(s3={"addressing_style": "path"}),
)

bucket = os.environ["BUCKET"]
prefix = os.environ["PREFIX"]
tag = os.environ["TAG"]

if bucket not in [b["Name"] for b in client.list_buckets().get("Buckets", [])]:
    client.create_bucket(Bucket=bucket)
    print(f"Created MinIO bucket: {bucket}")


def key(rel_path: str) -> str:
    return f"{prefix}/{rel_path}" if prefix else rel_path


uploads = [
    (os.environ["LABELS_FILE"], key(f"models/{tag}/best_labels.parquet")),
    (os.environ["CLUSTER_LABELS_FILE"], key(f"labels/{tag}/cluster_labels.json")),
    (os.environ["TECH_FILE"], key(f"raw/snapshot_{tag}/technologies.parquet")),
]

manifest_file = os.environ.get("MANIFEST_FILE")
if manifest_file:
    uploads.append((manifest_file, key(os.environ["MANIFEST_KEY"])))

for src, dest_key in uploads:
    client.upload_file(src, bucket, dest_key)
    print(f"  -> s3://{bucket}/{dest_key}")
PY
fi

cat <<EOF

Done.

Backend/API environment:
  MLCLUSTER_MINIO_BUCKET=$bucket
  MLCLUSTER_MINIO_PREFIX=$prefix
  MLCLUSTER_MINIO_ENDPOINT=$endpoint
  MLCLUSTER_SNAPSHOT_TAG=latest
  MLCLUSTER_MINIO_MANIFEST_KEY=$manifest_key
  MLCLUSTER_RELOAD_TTL_SECONDS=300

Runtime artifact paths:
  $base_uri/models/$tag/best_labels.parquet
  $base_uri/labels/$tag/cluster_labels.json
  $base_uri/raw/snapshot_$tag/technologies.parquet
  $base_uri/$manifest_key
EOF
