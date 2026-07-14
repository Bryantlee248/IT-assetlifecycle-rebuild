# Initial HTTP Deployment

The stack runs from `/opt/itam/app` with secrets in `/opt/itam/.env`.

## Validate and start

```sh
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml config --quiet
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml up -d --build
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml ps
```

## Logs

```sh
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml logs --tail=200
```

## Backup

```sh
/opt/itam/app/deploy/backup-postgres.sh
```

## Restore

Select the backup to restore, stop public traffic, take a pre-restore backup, restore, and
restart the application:

```sh
backup=$(ls -1t /opt/itam/backups/itam-*.dump | head -1)
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml stop frontend backend
pre_restore_backup=$(/opt/itam/app/deploy/backup-postgres.sh)
test -s "$pre_restore_backup"
CONFIRM_RESTORE=yes /opt/itam/app/deploy/restore-postgres.sh "$backup"
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml up -d backend frontend
```
