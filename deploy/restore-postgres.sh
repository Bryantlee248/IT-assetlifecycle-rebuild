#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: CONFIRM_RESTORE=yes $0 /path/to/backup.dump" >&2
  exit 2
fi
if [ "${CONFIRM_RESTORE:-}" != "yes" ]; then
  echo 'CONFIRM_RESTORE must be "yes"' >&2
  exit 2
fi

APP_DIR=${ITAM_APP_DIR:-/opt/itam/app}
ENV_FILE=${ITAM_ENV_FILE:-/opt/itam/.env}
backup=$1

test -r "$ENV_FILE"
test -s "$backup"
. "$ENV_FILE"

docker compose --env-file "$ENV_FILE" -f "$APP_DIR/docker-compose.yml" \
  exec -T postgres pg_restore --clean --if-exists --no-owner \
  -U "$POSTGRES_USER" -d "$POSTGRES_DB" < "$backup"
