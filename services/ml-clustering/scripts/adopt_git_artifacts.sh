#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Adopt ML clustering artifacts published via git (see scripts/publish_git_artifacts.sh)
into the local data/ layout that app/store.py reads when NO MinIO env vars are set
(MLCLUSTER_MINIO_BUCKET unset -> store.py falls back to local disk).

Run this on the RECEIVING machine after `git pull`.

Optional:
  --tag <tag>    Snapshot tag. Defaults to params.yaml snapshot.tag

After running, start the app with:
  MLCLUSTER_SNAPSHOT_TAG=<tag> uvicorn app.main:app --port 8001
EOF
}

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
module_dir="$(cd "$script_dir/.." && pwd)"
tag=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag) tag="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

cd "$module_dir"

if [[ -z "$tag" ]]; then
  tag="$(python3 -c 'import yaml; print(yaml.safe_load(open("params.yaml", encoding="utf-8"))["snapshot"]["tag"])')"
fi

src_dir="published/$tag"
if [[ ! -d "$src_dir" ]]; then
  echo "Missing $module_dir/$src_dir — did you 'git pull' after publish_git_artifacts.sh ran?" >&2
  exit 1
fi

mkdir -p "data/models/$tag" "data/labels/$tag" "data/raw/snapshot_$tag"
cp "$src_dir/best_labels.parquet"  "data/models/$tag/best_labels.parquet"
cp "$src_dir/cluster_labels.json" "data/labels/$tag/cluster_labels.json"
cp "$src_dir/technologies.parquet" "data/raw/snapshot_$tag/technologies.parquet"

cat <<EOF
Adopted tag '$tag' into local data/ layout:
  data/models/$tag/best_labels.parquet
  data/labels/$tag/cluster_labels.json
  data/raw/snapshot_$tag/technologies.parquet

Start serving with:
  MLCLUSTER_SNAPSHOT_TAG=$tag uvicorn app.main:app --port 8001
EOF
