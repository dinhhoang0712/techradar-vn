#!/bin/sh
# Runs as root (the container's default user) so it can fix ownership of the ml_clustering_cache
# named volume mounted at /tmp/ml-clustering-cache — which keeps whatever ownership it had from
# the last container that wrote to it (root, before this image switched to a non-root user)
# regardless of what the image itself was built with. Then drops to appuser for the actual server.
set -e
chown -R appuser:appuser /tmp/ml-clustering-cache
exec gosu appuser "$@"
