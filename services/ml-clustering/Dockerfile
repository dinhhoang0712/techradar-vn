FROM python:3.11-slim

# Mirror host layout so conf/config.py's Path(__file__).resolve().parents[3]
# resolves to /app (acting as project root). Layout inside container:
#   /app/src/ml-clustering/conf/config.py
#   parents[0]=conf, parents[1]=ml-clustering, parents[2]=src, parents[3]=/app
WORKDIR /app/src/ml-clustering

# gosu: drops root -> appuser in the entrypoint after fixing volume ownership (see docker-entrypoint.sh).
# git: mlflow (via GitPython) and take_snapshot() shell out to `git rev-parse HEAD` to tag runs
# with the source commit — python:3.11-slim doesn't ship it.
RUN apt-get update && apt-get install -y --no-install-recommends gosu git && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
# CPU-only torch FIRST — sentence-transformers/transformers below pull torch transitively, and
# without this index pip defaults to the CUDA wheel (~5x larger) even though nothing here uses a
# GPU. Same pattern as ai-rag-core/embedding-service's Dockerfiles.
RUN pip install --no-cache-dir --timeout 600 torch==2.4.1 --index-url https://download.pytorch.org/whl/cpu
RUN pip install --no-cache-dir --timeout 600 -r requirements.txt

COPY . .

# Seed data/ from the committed published/<tag>/ snapshots so a fresh `git clone` +
# `docker compose up` serves real clusters immediately — no MinIO config, no running the ML
# pipeline first. published/ is a flat export; store.py (via conf/config.py's DATA_DIR) expects
# the nested models/<tag>/, labels/<tag>/, raw/snapshot_<tag>/ layout, so re-derive it here.
RUN for tag_dir in published/*/; do \
        tag=$(basename "$tag_dir"); \
        mkdir -p "data/models/$tag" "data/labels/$tag" "data/raw/snapshot_$tag"; \
        [ -f "${tag_dir}best_labels.parquet" ] && cp "${tag_dir}best_labels.parquet" "data/models/$tag/"; \
        [ -f "${tag_dir}cluster_labels.json" ] && cp "${tag_dir}cluster_labels.json" "data/labels/$tag/"; \
        [ -f "${tag_dir}technologies.parquet" ] && cp "${tag_dir}technologies.parquet" "data/raw/snapshot_$tag/"; \
    done

RUN useradd --create-home --uid 1000 appuser \
    && mkdir -p /tmp/ml-clustering-cache \
    && chown -R appuser:appuser /app/src/ml-clustering /tmp/ml-clustering-cache \
    && chmod +x docker-entrypoint.sh

EXPOSE 8001

# Stays root here on purpose: the ml_clustering_cache volume (docker-compose.yml) keeps whatever
# ownership it had from earlier root-run containers, so entrypoint.sh chowns it fresh on every
# start before dropping to appuser to actually run the server.
ENTRYPOINT ["./docker-entrypoint.sh"]

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
