#!/bin/sh
# Starts as root only to make the uploads directory writable, then runs the app as the unprivileged "void"
# user. A Railway volume (or a new host bind mount) is mounted owned by root, which "void" cannot write to.
set -eu
dir="${UPLOADS_DIR:-/data/uploads}"
if [ "$(id -u)" = "0" ]; then
  mkdir -p "$dir"
  if [ "$(stat -c %u "$dir")" != "$(id -u void)" ]; then
    chown -R void:void "$dir"
  fi
  exec su-exec void "$@"
fi
exec "$@"
