# Initial HTTP Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy the committed MVP-1 application to `125.77.25.229` as a four-container stack behind the existing host Nginx, with private data services, generated secrets, backups, and repeatable smoke tests.

**Architecture:** Host Nginx is the only public listener and proxies to a frontend Nginx container bound to `127.0.0.1:3000`. The frontend container serves the Vue SPA and proxies `/api` to an internal Spring Boot container; PostgreSQL and Redis remain private on the Compose network.

**Tech Stack:** Docker Engine 29, Docker Compose v5, Nginx, Vue 3/Vite, Spring Boot 3.3/Java 21, PostgreSQL 16, Redis 7, Python 3 unittest/urllib, POSIX shell.

---

### Task 1: Create an Isolated Deployment Worktree

**Files:**
- Reference: `docs/superpowers/specs/2026-07-14-initial-http-deployment-design.md`
- Worktree: `D:/Codex Project/IT-assetlifecycle-deployment`

- [ ] **Step 1: Invoke the required worktree skill**

Use `superpowers:using-git-worktrees` before creating the worktree. The source worktree contains uncommitted MVP-2 changes and must not be modified.

- [ ] **Step 2: Create the deployment branch from the approved design commit**

```powershell
git worktree add "D:/Codex Project/IT-assetlifecycle-deployment" -b deployment/http-mvp1 dae8c37
```

Expected: a new worktree on `deployment/http-mvp1`, based on `dae8c37`.

- [ ] **Step 3: Verify isolation**

```powershell
git -C "D:/Codex Project/IT-assetlifecycle-deployment" status --short --branch
git -C "D:/Codex Project/IT-assetlifecycle-rebuild" status --short
```

Expected: deployment worktree is clean; all pre-existing MVP-2 changes remain only in the original worktree.

### Task 2: Add the Frontend Container and SPA Proxy

**Files:**
- Create: `deploy/tests/test_deployment_contract.py`
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`
- Create: `frontend/.dockerignore`

- [ ] **Step 1: Write the failing frontend deployment contract test**

```python
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class DeploymentContractTest(unittest.TestCase):
    def test_frontend_container_files_exist(self):
        for relative in (
            "frontend/Dockerfile",
            "frontend/nginx.conf",
            "frontend/.dockerignore",
        ):
            self.assertTrue((ROOT / relative).is_file(), relative)

    def test_frontend_nginx_serves_spa_and_proxies_api(self):
        config = (ROOT / "frontend/nginx.conf").read_text(encoding="utf-8")
        self.assertIn("try_files $uri $uri/ /index.html", config)
        self.assertIn("location /api/", config)
        self.assertIn("proxy_pass http://backend:8080", config)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the test and verify it fails**

```powershell
python -m unittest deploy.tests.test_deployment_contract -v
```

Expected: FAIL because the three frontend deployment files do not exist.

- [ ] **Step 3: Add the frontend Dockerfile**

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
```

- [ ] **Step 4: Add the frontend Nginx configuration**

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 30s;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }

    location ~* ^/assets/.*\.(js|css|png|jpg|jpeg|gif|svg|ico|woff2?)$ {
        expires 7d;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }
}
```

- [ ] **Step 5: Add the frontend Docker ignore file**

```text
node_modules
dist
.env
.env.*
*.log
```

- [ ] **Step 6: Run the contract and frontend build tests**

```powershell
python -m unittest deploy.tests.test_deployment_contract -v
Set-Location frontend
npm ci
npm run build
```

Expected: contract tests PASS and Vite reports `built`.

- [ ] **Step 7: Commit**

```powershell
git add deploy/tests/test_deployment_contract.py frontend/Dockerfile frontend/nginx.conf frontend/.dockerignore
git commit -m "feat: containerize frontend deployment"
```

### Task 3: Harden the Backend Image and Complete Compose

**Files:**
- Modify: `deploy/tests/test_deployment_contract.py`
- Modify: `backend/Dockerfile`
- Modify: `docker-compose.yml`
- Create: `.env.example`

- [ ] **Step 1: Extend the contract test with Compose boundaries**

Add these methods to `DeploymentContractTest`:

```python
    def test_compose_keeps_data_services_private(self):
        compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
        self.assertIn("127.0.0.1:${ITAM_PUBLIC_PORT:?ITAM_PUBLIC_PORT is required}:80", compose)
        self.assertNotIn('"5432:5432"', compose)
        self.assertNotIn('"6379:6379"', compose)
        self.assertNotIn('"8080:8080"', compose)
        self.assertIn("SPRING_DATA_REDIS_HOST: redis", compose)

    def test_compose_requires_real_secrets(self):
        compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
        self.assertIn("${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}", compose)
        self.assertIn("${ITAM_JWT_SECRET:?ITAM_JWT_SECRET is required}", compose)
        self.assertNotIn("changeme-please", compose)

    def test_environment_template_has_no_real_values(self):
        template = (ROOT / ".env.example").read_text(encoding="utf-8")
        self.assertIn("POSTGRES_PASSWORD=replace-with-random-password", template)
        self.assertIn("ITAM_JWT_SECRET=replace-with-random-secret", template)
```

- [ ] **Step 2: Run the contract test and verify it fails**

```powershell
python -m unittest deploy.tests.test_deployment_contract -v
```

Expected: the new Compose and environment tests FAIL against the development Compose file.

- [ ] **Step 3: Replace the backend Dockerfile**

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/itam-backend-0.0.1.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --retries=10 \
  CMD curl -fsS http://127.0.0.1:8080/api/v1/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 4: Replace the Compose file**

```yaml
name: itam

x-logging: &default-logging
  driver: json-file
  options:
    max-size: "10m"
    max-file: "5"

services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: ${POSTGRES_DB:?POSTGRES_DB is required}
      POSTGRES_USER: ${POSTGRES_USER:?POSTGRES_USER is required}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}
    volumes:
      - itam-pg:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 10
    restart: unless-stopped
    logging: *default-logging
    networks: [app]

  redis:
    image: redis:7
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10
    restart: unless-stopped
    logging: *default-logging
    networks: [app]

  backend:
    image: itam-backend:${ITAM_IMAGE_TAG:-mvp1}
    build: ./backend
    environment:
      ITAM_DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:?POSTGRES_DB is required}
      ITAM_DB_USERNAME: ${POSTGRES_USER:?POSTGRES_USER is required}
      ITAM_DB_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: "6379"
      ITAM_JWT_SECRET: ${ITAM_JWT_SECRET:?ITAM_JWT_SECRET is required}
      LOGGING_LEVEL_COM_ITAM: INFO
      SPRINGDOC_SWAGGER_UI_ENABLED: "false"
      SPRINGDOC_API_DOCS_ENABLED: "false"
      MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS: never
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    restart: unless-stopped
    logging: *default-logging
    networks: [app]

  frontend:
    image: itam-frontend:${ITAM_IMAGE_TAG:-mvp1}
    build: ./frontend
    ports:
      - "127.0.0.1:${ITAM_PUBLIC_PORT:?ITAM_PUBLIC_PORT is required}:80"
    depends_on:
      backend:
        condition: service_healthy
    restart: unless-stopped
    logging: *default-logging
    networks: [app]

networks:
  app:

volumes:
  itam-pg:
```

- [ ] **Step 5: Add the environment template**

```dotenv
POSTGRES_DB=itam
POSTGRES_USER=itam
POSTGRES_PASSWORD=replace-with-random-password
ITAM_JWT_SECRET=replace-with-random-secret
ITAM_PUBLIC_PORT=3000
ITAM_IMAGE_TAG=mvp1
```

- [ ] **Step 6: Run tests**

```powershell
python -m unittest deploy.tests.test_deployment_contract -v
```

Expected: all deployment contract tests PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/Dockerfile docker-compose.yml .env.example deploy/tests/test_deployment_contract.py
git commit -m "feat: add private production compose stack"
```

### Task 4: Add Backup, Restore, Host Nginx, and Operations Docs

**Files:**
- Modify: `deploy/tests/test_deployment_contract.py`
- Create: `deploy/backup-postgres.sh`
- Create: `deploy/restore-postgres.sh`
- Create: `deploy/nginx-host.conf`
- Create: `deploy/itam-backup.cron`
- Create: `deploy/README.md`

- [ ] **Step 1: Extend the contract test for operations files**

Add this method to `DeploymentContractTest`:

```python
    def test_operations_files_include_safety_controls(self):
        backup = (ROOT / "deploy/backup-postgres.sh").read_text(encoding="utf-8")
        restore = (ROOT / "deploy/restore-postgres.sh").read_text(encoding="utf-8")
        host_nginx = (ROOT / "deploy/nginx-host.conf").read_text(encoding="utf-8")
        cron = (ROOT / "deploy/itam-backup.cron").read_text(encoding="utf-8")
        self.assertIn("-mtime +14 -delete", backup)
        self.assertIn('CONFIRM_RESTORE must be "yes"', restore)
        self.assertIn("proxy_pass http://127.0.0.1:3000", host_nginx)
        self.assertIn("30 2 * * * root", cron)
```

- [ ] **Step 2: Run the test and verify it fails**

```powershell
python -m unittest deploy.tests.test_deployment_contract -v
```

Expected: FAIL because operations files do not exist.

- [ ] **Step 3: Add the PostgreSQL backup script**

```sh
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
```

- [ ] **Step 4: Add the guarded restore script**

```sh
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
```

- [ ] **Step 5: Add the host Nginx template**

```nginx
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    access_log /var/log/nginx/itam-access.log;
    error_log /var/log/nginx/itam-error.log warn;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
    }
}
```

- [ ] **Step 6: Add the cron definition**

```cron
SHELL=/bin/sh
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
30 2 * * * root /opt/itam/app/deploy/backup-postgres.sh >> /var/log/itam-backup.log 2>&1
```

- [ ] **Step 7: Add deployment documentation**

Document these exact commands in `deploy/README.md`:

````markdown
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
````

- [ ] **Step 8: Validate scripts and contract tests**

```powershell
python -m unittest deploy.tests.test_deployment_contract -v
sh -n deploy/backup-postgres.sh
sh -n deploy/restore-postgres.sh
```

Expected: tests PASS and both `sh -n` commands exit 0.

- [ ] **Step 9: Commit**

```powershell
git add deploy
git update-index --chmod=+x deploy/backup-postgres.sh deploy/restore-postgres.sh
git commit -m "feat: add deployment operations tooling"
```

### Task 5: Add a Repeatable HTTP Smoke Test

**Files:**
- Create: `deploy/smoke_test.py`
- Create: `deploy/tests/test_smoke_test.py`

- [ ] **Step 1: Write failing unit tests for response and asset-type handling**

```python
import unittest

from deploy.smoke_test import expect_success, flatten_asset_types


class SmokeTestHelpersTest(unittest.TestCase):
    def test_expect_success_returns_data(self):
        self.assertEqual(expect_success({"code": 0, "data": {"id": "1"}}), {"id": "1"})

    def test_expect_success_rejects_error_envelope(self):
        with self.assertRaisesRegex(RuntimeError, "failed"):
            expect_success({"code": 40000, "message": "failed", "data": None})

    def test_flatten_asset_types_walks_children(self):
        nodes = [{"id": "a", "enabled": True, "children": [{"id": "b", "enabled": True, "children": []}]}]
        self.assertEqual([node["id"] for node in flatten_asset_types(nodes)], ["a", "b"])


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the tests and verify import failure**

```powershell
python -m unittest deploy.tests.test_smoke_test -v
```

Expected: FAIL because `deploy/smoke_test.py` does not exist.

- [ ] **Step 3: Add the smoke test implementation**

```python
#!/usr/bin/env python3
import argparse
import json
import time
import urllib.error
import urllib.request


def expect_success(envelope):
    if envelope.get("code") != 0:
        raise RuntimeError(envelope.get("message") or f"API code {envelope.get('code')}")
    return envelope.get("data")


def flatten_asset_types(nodes):
    result = []
    for node in nodes:
        result.append(node)
        result.extend(flatten_asset_types(node.get("children") or []))
    return result


class Api:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")

    def request(self, method, path, body=None, token=None):
        data = None if body is None else json.dumps(body).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = urllib.request.Request(self.base_url + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                return response.status, json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            payload = json.loads(error.read().decode("utf-8"))
            return error.code, payload


def login(api, username, password):
    _, response = api.request("POST", "/api/v1/auth/login", {"username": username, "password": password})
    return expect_success(response)


def login_with_current_password(api, username, old_password, new_password):
    try:
        return login(api, username, new_password), False
    except RuntimeError:
        return login(api, username, old_password), True


def rotate_password(api, username, old_password, new_password):
    session, using_old = login_with_current_password(api, username, old_password, new_password)
    if using_old:
        _, changed = api.request(
            "POST",
            "/api/v1/auth/change-password",
            {"oldPassword": old_password, "newPassword": new_password},
            session["accessToken"],
        )
        expect_success(changed)
        session = login(api, username, new_password)
    return session


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1")
    parser.add_argument("--platform-new-password", required=True)
    parser.add_argument("--tenant-new-password", required=True)
    args = parser.parse_args()
    api = Api(args.base_url)

    _, health_response = api.request("GET", "/api/v1/health")
    health = expect_success(health_response)
    if health.get("pg") != "UP" or health.get("redis") != "UP":
        raise RuntimeError(f"unhealthy dependencies: {health}")

    platform = rotate_password(api, "platform_admin", "Platform@123", args.platform_new_password)
    _, me_response = api.request("GET", "/api/v1/auth/me", token=platform["accessToken"])
    expect_success(me_response)

    tenant = rotate_password(api, "tenant_admin", "Tenant@123", args.tenant_new_password)
    token = tenant["accessToken"]
    _, tree_response = api.request("GET", "/api/v1/metadata/asset-types/tree", token=token)
    nodes = [node for node in flatten_asset_types(expect_success(tree_response)) if node.get("enabled")]
    if not nodes:
        raise RuntimeError("no enabled asset type available")

    asset_no = f"SMOKE-{int(time.time())}"
    _, create_response = api.request(
        "POST",
        "/api/v1/assets",
        {"assetTypeId": nodes[0]["id"], "assetName": "Smoke Test Asset", "assetNo": asset_no, "attributes": {}},
        token,
    )
    asset = expect_success(create_response)
    asset_id = asset["id"]

    _, get_response = api.request("GET", f"/api/v1/assets/{asset_id}", token=token)
    expect_success(get_response)
    _, list_response = api.request("GET", f"/api/v1/assets?keyword={asset_no}", token=token)
    expect_success(list_response)
    _, update_response = api.request(
        "PUT",
        f"/api/v1/assets/{asset_id}",
        {"assetName": "Smoke Test Asset Updated", "attributes": {}},
        token,
    )
    updated = expect_success(update_response)
    if updated["assetName"] != "Smoke Test Asset Updated":
        raise RuntimeError("asset update was not persisted")

    _, delete_response = api.request("DELETE", f"/api/v1/assets/{asset_id}", token=token)
    expect_success(delete_response)
    status, missing_response = api.request("GET", f"/api/v1/assets/{asset_id}", token=token)
    if status != 404 and missing_response.get("code") == 0:
        raise RuntimeError("deleted asset remains accessible")

    print("Smoke test passed: health, password rotation, login, and asset CRUD")


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: Run helper tests**

```powershell
python -m unittest deploy.tests.test_smoke_test -v
```

Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```powershell
git add deploy/smoke_test.py deploy/tests/test_smoke_test.py
git commit -m "test: add deployment smoke test"
```

### Task 6: Run Clean Local Verification

**Files:**
- Verify: `backend/pom.xml`
- Verify: `frontend/package.json`
- Verify: deployment files from Tasks 2-5

- [ ] **Step 1: Run deployment tests**

```powershell
python -m unittest discover -s deploy/tests -v
```

Expected: all deployment tests PASS.

- [ ] **Step 2: Run a clean backend build**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
& "$HOME\.cache\codex-tools\apache-maven-3.9.11\bin\mvn.cmd" -f backend/pom.xml -B clean verify
```

Expected: `BUILD SUCCESS`, 101 tests, 0 failures, 0 errors, 2 integration tests skipped.

- [ ] **Step 3: Run a clean frontend build and production audit**

```powershell
Set-Location frontend
npm ci
npm run build
npm audit --omit=dev
```

Expected: Vite build succeeds and production audit reports 0 vulnerabilities.

- [ ] **Step 4: Confirm no unexpected files are staged**

```powershell
git status --short
```

Expected: clean deployment worktree.

### Task 7: Package and Upload the Approved Deployment Release

**Files:**
- Source: deployment worktree HEAD
- Server release root: `/opt/itam/releases`
- Server symlink: `/opt/itam/app`
- Server secrets: `/opt/itam/.env`
- Server credentials: `/root/.config/itam/admin-credentials`

- [ ] **Step 1: Record the release commit and create an archive**

```powershell
$commit = git rev-parse --short=12 HEAD
$archive = Join-Path $env:TEMP "itam-$commit.tar"
git archive --format=tar -o $archive HEAD
```

Expected: archive contains only committed MVP-1 deployment files.

- [ ] **Step 2: Upload the release archive and create the release directory**

```powershell
scp $archive root@125.77.25.229:/tmp/
ssh root@125.77.25.229 "install -d -o root -g root -m 0755 /opt/itam/releases/$commit; tar -xf /tmp/itam-$commit.tar -C /opt/itam/releases/$commit; ln -sfn /opt/itam/releases/$commit /opt/itam/app; rm -f /tmp/itam-$commit.tar"
```

Expected: `/opt/itam/app/docker-compose.yml` resolves to the new release.

- [ ] **Step 3: Generate server-only secrets**

Run remotely without printing generated values:

```sh
set -eu
umask 077
install -d -o root -g root -m 0700 /root/.config/itam /opt/itam/backups
postgres_password=$(openssl rand -hex 24)
jwt_secret=$(openssl rand -hex 48)
platform_password=$(openssl rand -base64 24 | tr -d '\n')
tenant_password=$(openssl rand -base64 24 | tr -d '\n')
{
  printf 'POSTGRES_DB=itam\n'
  printf 'POSTGRES_USER=itam\n'
  printf 'POSTGRES_PASSWORD=%s\n' "$postgres_password"
  printf 'ITAM_JWT_SECRET=%s\n' "$jwt_secret"
  printf 'ITAM_PUBLIC_PORT=3000\n'
  printf 'ITAM_IMAGE_TAG=mvp1\n'
} > /opt/itam/.env
{
  printf 'PLATFORM_ADMIN_PASSWORD=%s\n' "$platform_password"
  printf 'TENANT_ADMIN_PASSWORD=%s\n' "$tenant_password"
} > /root/.config/itam/admin-credentials
chmod 600 /opt/itam/.env /root/.config/itam/admin-credentials
```

- [ ] **Step 4: Verify ownership and secrecy**

```sh
stat -c '%a %U:%G %n' /opt/itam/.env /root/.config/itam/admin-credentials
```

Expected: both files report `600 root:root`.

### Task 8: Validate, Build, and Start Compose

**Files:**
- Server: `/opt/itam/app/docker-compose.yml`
- Server: `/opt/itam/.env`

- [ ] **Step 1: Validate the resolved Compose model**

```sh
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml config --quiet
```

Expected: exit 0 with no validation errors.

- [ ] **Step 2: Pull and build images**

```sh
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml pull postgres redis
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml build --pull backend frontend
```

Expected: PostgreSQL, Redis, Maven, Temurin, Node and Nginx image layers resolve through the configured mirror; both application images build.

- [ ] **Step 3: Start the stack**

```sh
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml up -d
```

- [ ] **Step 4: Wait for all services to become healthy**

```sh
deadline=$(( $(date +%s) + 300 ))
while :; do
  all_healthy=true
  for service in postgres redis backend frontend; do
    container_id=$(docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml ps -q "$service")
    status=$(docker inspect -f '{{.State.Health.Status}}' "$container_id" 2>/dev/null || true)
    if [ "$status" != "healthy" ]; then
      all_healthy=false
    fi
  done
  "$all_healthy" && break
  if [ "$(date +%s)" -ge "$deadline" ]; then
    docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml ps
    exit 1
  fi
  sleep 5
done
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml ps
```

Expected: four services are running and healthy.

- [ ] **Step 5: Verify Flyway migrations**

```sh
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml logs backend | grep -E 'Successfully applied|Schema.*up to date|Migrating schema'
```

Expected: migrations through V5 are applied without Flyway errors.

### Task 9: Install the Host Nginx Gateway

**Files:**
- Source: `/opt/itam/app/deploy/nginx-host.conf`
- Install: `/etc/nginx/sites-available/itam`
- Enable: `/etc/nginx/sites-enabled/itam`
- Preserve: `/etc/nginx/sites-disabled/default.pre-itam`

- [ ] **Step 1: Back up the active default site**

```sh
install -d -m 0755 /etc/nginx/sites-disabled
if [ -L /etc/nginx/sites-enabled/default ]; then
  mv /etc/nginx/sites-enabled/default /etc/nginx/sites-disabled/default.pre-itam
fi
```

- [ ] **Step 2: Install and enable the ITAM site**

```sh
install -o root -g root -m 0644 /opt/itam/app/deploy/nginx-host.conf /etc/nginx/sites-available/itam
ln -sfn /etc/nginx/sites-available/itam /etc/nginx/sites-enabled/itam
```

- [ ] **Step 3: Validate before reloading**

```sh
nginx -t
curl -fsS http://127.0.0.1:3000/ >/dev/null
curl -fsS http://127.0.0.1:3000/api/v1/health >/dev/null
```

Expected: Nginx syntax succeeds and the frontend container serves both SPA and API paths.

- [ ] **Step 4: Reload and verify the public gateway**

```sh
systemctl reload nginx
curl -fsS http://127.0.0.1/ >/dev/null
curl -fsS http://127.0.0.1/api/v1/health >/dev/null
```

Expected: both requests succeed through host Nginx.

### Task 10: Rotate Seed Passwords and Run Asset CRUD Smoke Tests

**Files:**
- Script: `/opt/itam/app/deploy/smoke_test.py`
- Credentials: `/root/.config/itam/admin-credentials`

- [ ] **Step 1: Load credentials without printing them**

```sh
set -a
. /root/.config/itam/admin-credentials
set +a
```

- [ ] **Step 2: Run the repeatable smoke test**

```sh
python3 /opt/itam/app/deploy/smoke_test.py \
  --base-url http://127.0.0.1 \
  --platform-new-password "$PLATFORM_ADMIN_PASSWORD" \
  --tenant-new-password "$TENANT_ADMIN_PASSWORD"
```

Expected: `Smoke test passed: health, password rotation, login, and asset CRUD`.

- [ ] **Step 3: Confirm default credentials no longer work**

```sh
python3 - <<'PY'
import json, urllib.error, urllib.request
for username, password in (("platform_admin", "Platform@123"), ("tenant_admin", "Tenant@123")):
    request = urllib.request.Request(
        "http://127.0.0.1/api/v1/auth/login",
        data=json.dumps({"username": username, "password": password}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            payload = json.loads(response.read())
    except urllib.error.HTTPError as error:
        payload = json.loads(error.read())
    if payload.get("code") == 0:
        raise SystemExit(f"default password still works for {username}")
print("Default seed passwords rejected")
PY
```

Expected: `Default seed passwords rejected`.

### Task 11: Install and Verify Database Backups

**Files:**
- Install: `/etc/cron.d/itam-backup`
- Backup directory: `/opt/itam/backups`

- [ ] **Step 1: Set script permissions**

```sh
chmod 0755 /opt/itam/app/deploy/backup-postgres.sh /opt/itam/app/deploy/restore-postgres.sh
```

- [ ] **Step 2: Run the first backup**

```sh
backup=$(/opt/itam/app/deploy/backup-postgres.sh)
test -s "$backup"
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml \
  exec -T postgres pg_restore --list < "$backup" >/dev/null
```

Expected: a non-empty valid custom-format PostgreSQL backup.

- [ ] **Step 3: Install the daily cron job**

```sh
install -o root -g root -m 0644 /opt/itam/app/deploy/itam-backup.cron /etc/cron.d/itam-backup
systemctl is-active cron
```

Expected: cron is active and the definition is installed with mode 0644.

### Task 12: Verify Persistence, Network Exposure, and Logs

**Files:**
- Verify live deployment only

- [ ] **Step 1: Restart application containers without deleting volumes**

```sh
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml restart
timeout 180 sh -c 'until curl -fsS http://127.0.0.1/api/v1/health >/dev/null; do sleep 5; done'
```

- [ ] **Step 2: Re-run the smoke test with rotated credentials**

```sh
set -a
. /root/.config/itam/admin-credentials
set +a
python3 /opt/itam/app/deploy/smoke_test.py \
  --base-url http://127.0.0.1 \
  --platform-new-password "$PLATFORM_ADMIN_PASSWORD" \
  --tenant-new-password "$TENANT_ADMIN_PASSWORD"
```

Expected: smoke test passes after restart, proving database and password persistence.

- [ ] **Step 3: Verify local listeners**

```sh
ss -lnt '( sport = :80 or sport = :3000 or sport = :8080 or sport = :5432 or sport = :6379 )'
```

Expected: port 80 listens publicly; port 3000 listens only on `127.0.0.1`; no Docker listeners exist for 8080, 5432 or 6379. The pre-existing host PostgreSQL listener on `127.0.0.1:5432` may remain.

- [ ] **Step 4: Verify from the local workstation**

```powershell
curl.exe -fsS http://125.77.25.229/ -o NUL
curl.exe -fsS http://125.77.25.229/api/v1/health
Test-NetConnection 125.77.25.229 -Port 3000
Test-NetConnection 125.77.25.229 -Port 8080
Test-NetConnection 125.77.25.229 -Port 5432
Test-NetConnection 125.77.25.229 -Port 6379
```

Expected: HTTP requests succeed; all four non-public TCP tests report `TcpTestSucceeded: False`.

- [ ] **Step 5: Check logs and final status**

```sh
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml ps
docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml logs --since 10m
journalctl -u nginx --since '-10 minutes' --no-pager
```

Expected: four healthy services and no repeating startup, migration, proxy, or connection errors.

### Task 13: Publish the Deployment Branch and Record the HTTPS Follow-up

**Files:**
- Branch: `deployment/http-mvp1`
- External prerequisite: a domain resolving to `125.77.25.229`

- [ ] **Step 1: Verify branch history and worktree cleanliness**

```powershell
git status --short --branch
git log --oneline dae8c37..HEAD
```

Expected: clean worktree and only deployment-related commits.

- [ ] **Step 2: Push the deployment branch**

```powershell
git push -u origin deployment/http-mvp1
```

- [ ] **Step 3: Record the deferred HTTPS work**

Report that HTTP deployment is complete and HTTPS remains blocked only by the absence of a domain. The follow-up must add a trusted certificate, automatic renewal, port 443, and HTTP-to-HTTPS redirection after DNS is configured.
