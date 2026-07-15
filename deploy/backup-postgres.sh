#!/bin/sh
set -eu
umask 077

APP_DIR=${ITAM_APP_DIR:-/opt/itam/app}
ENV_FILE=${ITAM_ENV_FILE:-/opt/itam/.env}
BACKUP_DIR=${ITAM_BACKUP_DIR:-/opt/itam/backups}

test -r "$ENV_FILE"
. "$ENV_FILE"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

lock_file="$BACKUP_DIR/.backup.lock"
temporary=
exec 9>"$lock_file"
if ! flock -n 9; then
  echo "Backup already in progress" >&2
  exit 1
fi

cleanup() {
  if [ -n "$temporary" ]; then
    rm -f "$temporary" || true
  fi
}
trap cleanup 0
trap 'exit 1' HUP INT TERM

stamp=$(date +%Y%m%d-%H%M%S)
final="$BACKUP_DIR/itam-$stamp.dump"
temporary="$final.$$.tmp"

docker compose --env-file "$ENV_FILE" -f "$APP_DIR/docker-compose.yml" \
  exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$temporary"
test -s "$temporary"
mv "$temporary" "$final"
temporary=
find "$BACKUP_DIR" -type f -name 'itam-*.dump' -mtime +14 -delete
printf '%s\n' "$final"
