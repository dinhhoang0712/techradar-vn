#!/bin/sh
# Runs as root (the container's default user) so it can fix ownership of the crawler_data named
# volume mounted at /app/data — which keeps whatever ownership it had from the last container that
# wrote to it (root, before this image switched to a non-root user) regardless of what the image
# itself was built with. Then drops to the unprivileged appuser for the actual process.
set -e
chown -R appuser:appuser /app/data
exec gosu appuser "$@"
