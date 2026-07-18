#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Publish ML clustering runtime artifacts into published/<tag>/ so they can be
shared across machines via `git add` + `git commit` + `git push` — no MinIO/S3
needed. Artifacts here are tiny (tens of KB), so committing them to git is a
reasonable zero-infrastructure alternative to scripts/publish_minio_artifacts.sh.

Optional:
  --tag <tag>    Snapshot tag. Defaults to params.yaml snapshot.tag

Files copied into published/<tag>/:
  data/models/<tag>/best_labels.parquet     -> published/<tag>/best_labels.parquet
  data/labels/<tag>/cluster_labels.json     -> published/<tag>/cluster_labels.json
  data/raw/snapshot_<tag>/technologies.parquet -> published/<tag>/technologies.parquet

After running, on THIS machine or the receiving machine (after `git pull`):
  scripts/adopt_git_artifacts.sh --tag <tag>
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

labels_file="data/models/$tag/best_labels.parquet"
cluster_labels_file="data/labels/$tag/cluster_labels.json"
tech_file="data/raw/snapshot_$tag/technologies.parquet"

for file in "$labels_file" "$cluster_labels_file" "$tech_file"; do
  if [[ ! -f "$file" ]]; then
    echo "Missing required artifact: $module_dir/$file" >&2
    exit 1
  fi
done

out_dir="published/$tag"
mkdir -p "$out_dir"
cp "$labels_file" "$out_dir/best_labels.parquet"
cp "$cluster_labels_file" "$out_dir/cluster_labels.json"
cp "$tech_file" "$out_dir/technologies.parquet"

cat <<EOF
Copied into $module_dir/$out_dir:
  best_labels.parquet
  cluster_labels.json
  technologies.parquet

Next steps:
  git add $out_dir
  git commit -m "publish clustering artifacts for tag $tag"
  git push

On another machine, after 'git pull':
  scripts/adopt_git_artifacts.sh --tag $tag
EOF
