#!/usr/bin/env bash
# The actual disaster-recovery drill: restores the most recent backup from ./dumps/ into
# brand-new, fully isolated scratch containers (never the live techradar-postgres/techradar-neo4j)
# and runs a sanity query against each. A backup nobody has ever restored isn't proven to work -
# this is what proves it. Scratch containers/volumes are always torn down on exit, pass or fail.
#
# Usage: ./infrastructure/backup/restore-drill.sh [dump-timestamp]
#   With no argument, uses the most recent backup under ./dumps/.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DUMPS_DIR="$SCRIPT_DIR/dumps"

TIMESTAMP="${1:-}"
if [ -z "$TIMESTAMP" ]; then
    TIMESTAMP="$(ls -1 "$DUMPS_DIR" | sort | tail -1)"
fi
DUMP_DIR="$DUMPS_DIR/$TIMESTAMP"
if [ ! -d "$DUMP_DIR" ]; then
    echo "No backup found at $DUMP_DIR. Run ./backup.sh first." >&2
    exit 1
fi
echo "==> Drilling against backup: $DUMP_DIR"

PG_SCRATCH="techradar-restore-drill-pg"
NEO4J_SCRATCH="techradar-restore-drill-neo4j"
NEO4J_SCRATCH_VOLUME="techradar-restore-drill-neo4j-data"
NEO4J_IMAGE="neo4j:5"

PASS=true

cleanup() {
    echo "==> Tearing down scratch containers/volumes"
    docker rm -f "$PG_SCRATCH" >/dev/null 2>&1 || true
    docker rm -f "$NEO4J_SCRATCH" >/dev/null 2>&1 || true
    docker volume rm "$NEO4J_SCRATCH_VOLUME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# ---- Postgres restore drill --------------------------------------------------------------
echo "==> [1/2] Postgres: restoring into a fresh scratch container"
docker run -d --name "$PG_SCRATCH" \
    -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=techradar \
    postgres:16-alpine >/dev/null

echo "    -> waiting for scratch Postgres to be ready..."
# The official postgres image restarts once internally after running init scripts on a fresh
# data dir - pg_isready can catch the narrow window between that shutdown and the real startup
# and report ready right before the connection dies. Require two consecutive successes.
READY_STREAK=0
for _ in $(seq 1 60); do
    if docker exec "$PG_SCRATCH" pg_isready -U postgres -d techradar >/dev/null 2>&1; then
        READY_STREAK=$((READY_STREAK + 1))
        [ "$READY_STREAK" -ge 2 ] && break
    else
        READY_STREAK=0
    fi
    sleep 1
done

docker cp "$DUMP_DIR/postgres.dump" "$PG_SCRATCH:/tmp/postgres.dump"
RESTORE_OK=false
for attempt in 1 2 3; do
    if docker exec "$PG_SCRATCH" pg_restore -U postgres -d techradar --no-owner --no-privileges --clean --if-exists /tmp/postgres.dump; then
        RESTORE_OK=true
        break
    fi
    echo "    -> pg_restore attempt $attempt failed (see above) - retrying in 3s..."
    sleep 3
done
[ "$RESTORE_OK" = true ] || echo "    -> pg_restore did not succeed cleanly after retries; verifying data anyway"

USER_COUNT="$(docker exec "$PG_SCRATCH" psql -U postgres -d techradar -tAc 'SELECT COUNT(*) FROM users;' 2>/dev/null | tr -d '[:space:]')"
TABLE_COUNT="$(docker exec "$PG_SCRATCH" psql -U postgres -d techradar -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';" 2>/dev/null | tr -d '[:space:]')"

# Deliberately schema-version-agnostic (row count + table count, not any specific column) - this
# drill's job is to prove the dump mechanically restores, not to assert today's exact schema.
if [ "${USER_COUNT:-0}" -ge 1 ] 2>/dev/null && [ "${TABLE_COUNT:-0}" -ge 1 ] 2>/dev/null; then
    echo "    -> PASS: restored DB has $TABLE_COUNT table(s), 'users' has $USER_COUNT row(s)"
else
    echo "    -> FAIL: users=$USER_COUNT tables=$TABLE_COUNT"
    PASS=false
fi

# ---- Neo4j restore drill -----------------------------------------------------------------
echo "==> [2/2] Neo4j: loading dump into a fresh scratch volume + container"
docker volume create "$NEO4J_SCRATCH_VOLUME" >/dev/null

docker run --rm \
    -v "$NEO4J_SCRATCH_VOLUME:/data" \
    -v "$DUMP_DIR/neo4j:/backups" \
    "$NEO4J_IMAGE" \
    neo4j-admin database load neo4j --from-path=/backups --overwrite-destination=true

docker run -d --name "$NEO4J_SCRATCH" \
    -e NEO4J_AUTH=neo4j/password \
    -v "$NEO4J_SCRATCH_VOLUME:/data" \
    "$NEO4J_IMAGE" >/dev/null

echo "    -> waiting for scratch Neo4j to be ready..."
NEO4J_UP=false
for _ in $(seq 1 45); do
    if docker exec "$NEO4J_SCRATCH" cypher-shell -u neo4j -p password "RETURN 1" >/dev/null 2>&1; then
        NEO4J_UP=true
        break
    fi
    sleep 2
done

if [ "$NEO4J_UP" = true ]; then
    NODE_COUNT="$(docker exec "$NEO4J_SCRATCH" cypher-shell -u neo4j -p password --format plain "MATCH (n) RETURN count(n);" 2>/dev/null | tail -1 | tr -d '[:space:]')"
    if [ "${NODE_COUNT:-0}" -ge 1 ] 2>/dev/null; then
        echo "    -> PASS: restored graph has $NODE_COUNT node(s)"
    else
        echo "    -> FAIL: restored graph reports $NODE_COUNT node(s)"
        PASS=false
    fi
else
    echo "    -> FAIL: scratch Neo4j never became ready"
    PASS=false
fi

echo "=================================================="
if [ "$PASS" = true ]; then
    echo "RESTORE DRILL: PASS - backup $TIMESTAMP is proven restorable."
    exit 0
else
    echo "RESTORE DRILL: FAIL - see above. Investigate before trusting this backup."
    exit 1
fi
