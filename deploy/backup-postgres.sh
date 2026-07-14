#!/bin/sh
set -eu

APP_DIR=${ITAM_APP_DIR:-/opt/itam/app}
ENV_FILE=${ITAM_ENV_FILE:-/opt/itam/.env}
BACKUP_DIR=${ITAM_BACKUP_DIR:-/opt/itam/backups}

test -r "$ENV_FILE"
. "$ENV_FILE"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

stamp=$(date +%Y%m%d-%H%M%S)
final="$BACKUP_DIR/itam-$stamp.dump"
temporary="$final.tmp"
trap 'rm -f "$temporary"' EXIT INT TERM

docker compose --env-file "$ENV_FILE" -f "$APP_DIR/docker-compose.yml" \
  exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$temporary"
test -s "$temporary"
mv "$temporary" "$final"
trap - EXIT INT TERM
find "$BACKUP_DIR" -type f -name 'itam-*.dump' -mtime +14 -delete
printf '%s\n' "$final"
