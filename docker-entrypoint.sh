#!/bin/sh
set -eu

DATA_DIR="${DATA_DIR:-/app/data}"
INCUS_CONF="${INCUS_CONF:-${INCUS_CONFIG:-$DATA_DIR/incus-client}}"
PUID="${PUID:-1000}"
PGID="${PGID:-1000}"
APP_USER="${APP_USER:-incusmobile}"
APP_GROUP="${APP_GROUP:-incusmobile}"

mkdir -p "$DATA_DIR"
mkdir -p "$DATA_DIR/ssh"
mkdir -p "$INCUS_CONF"

if [ "$(id -u)" = "0" ]; then
  if ! getent group "$PGID" >/dev/null 2>&1; then
    groupadd -g "$PGID" "$APP_GROUP"
  fi

  if ! getent passwd "$PUID" >/dev/null 2>&1; then
    useradd \
      -u "$PUID" \
      -g "$PGID" \
      -d "$DATA_DIR" \
      -s /usr/sbin/nologin \
      "$APP_USER"
  fi

  chown -R "$PUID:$PGID" "$DATA_DIR"
  chmod 755 "$DATA_DIR" 2>/dev/null || true
  chmod 700 "$DATA_DIR/ssh" 2>/dev/null || true
  chmod 700 "$INCUS_CONF" 2>/dev/null || true

  exec gosu "$PUID:$PGID" "$0" "$@"
fi

chmod 700 "$DATA_DIR/ssh" 2>/dev/null || true
chmod 700 "$INCUS_CONF" 2>/dev/null || true

exec "$@"
