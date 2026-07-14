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

Stop public traffic before restoring, then run:

```sh
backup=$(ls -1t /opt/itam/backups/itam-*.dump | head -1)
CONFIRM_RESTORE=yes /opt/itam/app/deploy/restore-postgres.sh "$backup"
```
