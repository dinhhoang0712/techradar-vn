#!/usr/bin/env bash
# Backs up the live Postgres and Neo4j datastores from the running docker-compose stack into
# infrastructure/backup/dumps/<timestamp>/. Run ./restore-drill.sh afterwards to prove the backup
# is actually restorable - a backup nobody has ever restored isn't a backup, it's a hope.
#
# Postgres: pg_dump runs against the live container with no downtime (MVCC snapshot).
# Neo4j:    Community Edition's neo4j-admin dump only works offline, so this briefly stops
#           techradar-neo4j, runs a throwaway container against the same data volume to dump it,
#           then restarts techradar-neo4j. Expect ~10-20s of Neo4j unavailability.
#
# Usage: ./infrastructure/backup/backup.sh

set -euo pipefail

PG_CONTAINER="techradar-postgres"
PG_DB="techradar"
PG_USER="postgres"
NEO4J_CONTAINER="techradar-neo4j"
NEO4J_IMAGE="neo4j:5"
NEO4J_VOLUME="techradar-vn_neo4j_data"
NEO4J_DB="neo4j"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DUMP_DIR="$SCRIPT_DIR/dumps/$TIMESTAMP"
mkdir -p "$DUMP_DIR"

echo "==> Backing up to $DUMP_DIR"

echo "==> [1/2] Postgres: pg_dump (live, no downtime)"
docker exec "$PG_CONTAINER" pg_dump -U "$PG_USER" -d "$PG_DB" -F c -f /tmp/techradar.dump
docker cp "$PG_CONTAINER:/tmp/techradar.dump" "$DUMP_DIR/postgres.dump"
docker exec "$PG_CONTAINER" rm -f /tmp/techradar.dump
echo "    -> $DUMP_DIR/postgres.dump ($(du -h "$DUMP_DIR/postgres.dump" | cut -f1))"

echo "==> [2/2] Neo4j: neo4j-admin database dump (offline)"
mkdir -p "$DUMP_DIR/neo4j"
# The neo4j:5 image's neo4j-admin runs as a non-root user inside the container, which can't write
# into a bind-mounted host directory owned by the host user - world-writable sidesteps that.
chmod 777 "$DUMP_DIR/neo4j"

# techradar-neo4j MUST come back up even if the dump command below fails, or a failed backup
# attempt leaves the dev stack down - restore it unconditionally once we reach this point.
restart_neo4j() {
    docker start "$NEO4J_CONTAINER" >/dev/null 2>&1 || true
    echo "    -> waiting for Neo4j to come back up..."
    for _ in $(seq 1 30); do
        if docker exec "$NEO4J_CONTAINER" cypher-shell -u neo4j -p password "RETURN 1" >/dev/null 2>&1; then
            echo "    -> Neo4j back online"
            return
        fi
        sleep 2
    done
    echo "    -> WARNING: Neo4j did not report healthy within 60s - check 'docker logs $NEO4J_CONTAINER'" >&2
}
trap restart_neo4j EXIT

docker stop "$NEO4J_CONTAINER" >/dev/null
docker run --rm \
    -v "$NEO4J_VOLUME:/data" \
    -v "$DUMP_DIR/neo4j:/backups" \
    "$NEO4J_IMAGE" \
    neo4j-admin database dump "$NEO4J_DB" --to-path=/backups
echo "    -> $DUMP_DIR/neo4j/$NEO4J_DB.dump"

echo "==> Backup complete: $DUMP_DIR"
echo "==> Run ./restore-drill.sh to prove this backup actually restores."
