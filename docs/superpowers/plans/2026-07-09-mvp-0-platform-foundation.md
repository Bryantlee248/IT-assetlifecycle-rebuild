# MVP-0 Platform Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first formal engineering foundation for the new multi-tenant ITAM platform.

**Architecture:** This plan creates a modular monolith skeleton with Spring Boot 3 backend, Vue 3 TypeScript frontend, PostgreSQL, Redis, Flyway migrations, JWT authentication, tenant context, RBAC seed data, audit foundation, OpenAPI documentation, and Docker Compose local runtime. It intentionally stops before asset metadata and lifecycle business features; those belong to later MVP plans.

**Tech Stack:** Java 21, Spring Boot 3, Maven, PostgreSQL, Redis, Flyway, MyBatis Plus, springdoc-openapi, Vue 3, TypeScript, Vite, Pinia, Element Plus, Vitest, Docker Compose.

---

## Scope Check

The approved redesign spec covers several independent subsystems: platform foundation, metadata-driven assets, lifecycle, approval, license management, import/export, reporting, and integrations.

This implementation plan covers **MVP-0 Platform Foundation only**:

- repository initialization
- backend project skeleton
- frontend project skeleton
- local Docker runtime
- database migrations
- tenant, user, organization, role, permission base tables
- JWT login and tenant context
- basic RBAC
- audit foundation
- OpenAPI documentation
- minimum tests

Separate implementation plans should be written for:

- MVP-1 Metadata and Asset Core
- MVP-2 Lifecycle Closure
- MVP-3 Approval, Permission, Notification
- MVP-4 Import, Export, Reports, Lightweight Assets
- MVP-5 Production Hardening and Integrations

## File Structure

Create this structure under `D:\Codex Project\IT-assetlifecycle-rebuild`:

```text
backend/
  pom.xml
  src/main/java/com/company/itam/
    ItamApplication.java
    common/
      config/
      exception/
      security/
      tenant/
      web/
    platform/
    identity/
    permission/
    audit/
  src/main/resources/
    application.yml
    application-local.yml
    db/migration/
      V001__platform_identity_permission_audit.sql
  src/test/java/com/company/itam/
frontend/
  package.json
  index.html
  vite.config.ts
  tsconfig.json
  src/
    main.ts
    App.vue
    router/
    stores/
    api/
    layouts/
    modules/
docker/
  postgres/
    init.sql
docker-compose.yml
.gitignore
README.md
docs/
  superpowers/
    specs/
    plans/
```

MVP-0 creates only foundation modules. Asset-specific modules start in MVP-1.

---

## Task 0: Initialize Repository Hygiene

**Files:**
- Create: `.gitignore`
- Create: `README.md`
- Verify: `docs/superpowers/specs/2026-07-09-itam-platform-redesign.md`

- [ ] **Step 1: Initialize Git repository**

Run:

```powershell
git init
```

Expected:

```text
Initialized empty Git repository
```

- [ ] **Step 2: Create `.gitignore`**

Create `.gitignore` with:

```gitignore
.idea/
.vscode/
*.iml

target/
build/
dist/
node_modules/

.env
.env.*
!.env.example

*.log
logs/

.DS_Store
Thumbs.db

docker/postgres/data/
```

- [ ] **Step 3: Create root `README.md`**

Create `README.md` with:

```markdown
# IT Asset Lifecycle Rebuild

This repository contains the redesigned IT asset management platform.

The project is built as a multi-tenant modular monolith:

- Backend: Spring Boot 3
- Database: PostgreSQL
- Cache: Redis
- Frontend: Vue 3 + TypeScript + Vite

The approved design spec is located at:

`docs/superpowers/specs/2026-07-09-itam-platform-redesign.md`
```

- [ ] **Step 4: Verify repository status**

Run:

```powershell
git status --short
```

Expected includes:

```text
?? .gitignore
?? README.md
?? docs/
```

- [ ] **Step 5: Commit repository hygiene**

Run:

```powershell
git add .gitignore README.md docs
git commit -m "chore: initialize repository documentation"
```

Expected:

```text
[main ...] chore: initialize repository documentation
```

If the default branch is `master`, rename it:

```powershell
git branch -M main
```

---

## Task 1: Create Local Runtime with Docker Compose

**Files:**
- Create: `docker-compose.yml`
- Create: `docker/postgres/init.sql`
- Create: `.env.example`

- [ ] **Step 1: Create `.env.example`**

Create `.env.example` with:

```dotenv
POSTGRES_DB=itam
POSTGRES_USER=itam
POSTGRES_PASSWORD=itam_dev_password
POSTGRES_PORT=5432
REDIS_PORT=6379
BACKEND_PORT=8080
FRONTEND_PORT=5173
JWT_SECRET=replace-with-local-dev-secret-at-least-32-chars
```

- [ ] **Step 2: Create PostgreSQL init script**

Create `docker/postgres/init.sql` with:

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
```

- [ ] **Step 3: Create `docker-compose.yml`**

Create `docker-compose.yml` with:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: itam-postgres
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-itam}
      POSTGRES_USER: ${POSTGRES_USER:-itam}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-itam_dev_password}
    ports:
      - "${POSTGRES_PORT:-5432}:5432"
    volumes:
      - ./docker/postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-itam} -d ${POSTGRES_DB:-itam}"]
      interval: 5s
      timeout: 3s
      retries: 20

  redis:
    image: redis:7-alpine
    container_name: itam-redis
    ports:
      - "${REDIS_PORT:-6379}:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 20

volumes:
  postgres-data:
```

- [ ] **Step 4: Start runtime dependencies**

Run:

```powershell
docker compose up -d postgres redis
```

Expected:

```text
Container itam-postgres Started
Container itam-redis Started
```

- [ ] **Step 5: Verify containers are healthy**

Run:

```powershell
docker compose ps
```

Expected:

```text
itam-postgres   healthy
itam-redis      healthy
```

- [ ] **Step 6: Commit local runtime**

Run:

```powershell
git add .env.example docker docker-compose.yml
git commit -m "chore: add local docker runtime"
```

Expected:

```text
[main ...] chore: add local docker runtime
```

---

## Task 2: Scaffold Spring Boot Backend

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/company/itam/ItamApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-local.yml`
- Create directories under `backend/src/main/java/com/company/itam/`

- [ ] **Step 1: Create backend directories**

Run:

```powershell
New-Item -ItemType Directory -Force -Path `
  backend/src/main/java/com/company/itam/common/config,`
  backend/src/main/java/com/company/itam/common/exception,`
  backend/src/main/java/com/company/itam/common/security,`
  backend/src/main/java/com/company/itam/common/tenant,`
  backend/src/main/java/com/company/itam/common/web,`
  backend/src/main/java/com/company/itam/platform,`
  backend/src/main/java/com/company/itam/identity,`
  backend/src/main/java/com/company/itam/permission,`
  backend/src/main/java/com/company/itam/audit,`
  backend/src/main/resources/db/migration,`
  backend/src/test/java/com/company/itam
```

Expected: directories exist.

- [ ] **Step 2: Create `backend/pom.xml`**

Create `backend/pom.xml` with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
    <relativePath/>
  </parent>

  <groupId>com.company</groupId>
  <artifactId>itam-backend</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <name>itam-backend</name>

  <properties>
    <java.version>21</java.version>
    <mybatis-plus.version>3.5.7</mybatis-plus.version>
    <jjwt.version>0.12.6</jjwt.version>
    <springdoc.version>2.6.0</springdoc.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
      <groupId>com.baomidou</groupId>
      <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
      <version>${mybatis-plus.version}</version>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springdoc</groupId>
      <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
      <version>${springdoc.version}</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>${jjwt.version}</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 3: Create application class**

Create `backend/src/main/java/com/company/itam/ItamApplication.java` with:

```java
package com.company.itam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ItamApplication {
    public static void main(String[] args) {
        SpringApplication.run(ItamApplication.class, args);
    }
}
```

- [ ] **Step 4: Create base application config**

Create `backend/src/main/resources/application.yml` with:

```yaml
spring:
  application:
    name: itam-backend
  profiles:
    active: local

server:
  port: ${BACKEND_PORT:8080}

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
```

- [ ] **Step 5: Create local application config**

Create `backend/src/main/resources/application-local.yml` with:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:${POSTGRES_PORT:5432}/${POSTGRES_DB:itam}
    username: ${POSTGRES_USER:itam}
    password: ${POSTGRES_PASSWORD:itam_dev_password}
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis:
      host: localhost
      port: ${REDIS_PORT:6379}

itam:
  jwt:
    secret: ${JWT_SECRET:replace-with-local-dev-secret-at-least-32-chars}
    access-token-minutes: 30
    refresh-token-days: 7
```

- [ ] **Step 6: Verify backend compiles**

Run:

```powershell
cd backend
mvn test
cd ..
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 7: Commit backend scaffold**

Run:

```powershell
git add backend
git commit -m "chore: scaffold Spring Boot backend"
```

Expected:

```text
[main ...] chore: scaffold Spring Boot backend
```

---

## Task 3: Add MVP-0 Database Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V001__platform_identity_permission_audit.sql`

- [ ] **Step 1: Create migration**

Create `backend/src/main/resources/db/migration/V001__platform_identity_permission_audit.sql` with:

```sql
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_code VARCHAR(64) NOT NULL UNIQUE,
    tenant_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tenant_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    email VARCHAR(128),
    mobile VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, username)
);

CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    parent_id UUID REFERENCES organizations(id),
    org_code VARCHAR(64) NOT NULL,
    org_name VARCHAR(128) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, org_code)
);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    role_type VARCHAR(32) NOT NULL DEFAULT 'custom',
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, role_code)
);

CREATE TABLE user_roles (
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES tenant_users(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, user_id, role_id)
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_code VARCHAR(128) NOT NULL UNIQUE,
    permission_name VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE role_permissions (
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    permission_id UUID NOT NULL REFERENCES permissions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, role_id, permission_id)
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(id),
    actor_id UUID,
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(128) NOT NULL,
    resource_id VARCHAR(128),
    before_data JSONB,
    after_data JSONB,
    ip VARCHAR(64),
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_users_tenant ON tenant_users(tenant_id) WHERE deleted = FALSE;
CREATE INDEX idx_organizations_tenant ON organizations(tenant_id) WHERE deleted = FALSE;
CREATE INDEX idx_roles_tenant ON roles(tenant_id) WHERE deleted = FALSE;
CREATE INDEX idx_audit_logs_tenant_created ON audit_logs(tenant_id, created_at DESC);
```

- [ ] **Step 2: Run backend tests to trigger Flyway**

Run:

```powershell
cd backend
mvn test
cd ..
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 3: Run backend application**

Run:

```powershell
cd backend
mvn spring-boot:run
```

Expected includes:

```text
Started ItamApplication
```

Stop the server with `Ctrl+C`.

- [ ] **Step 4: Verify migration table exists**

Run:

```powershell
docker exec -it itam-postgres psql -U itam -d itam -c "select version, description, success from flyway_schema_history order by installed_rank;"
```

Expected includes:

```text
1 | platform identity permission audit | t
```

- [ ] **Step 5: Commit migration**

Run:

```powershell
git add backend/src/main/resources/db/migration/V001__platform_identity_permission_audit.sql
git commit -m "feat: add MVP-0 platform database schema"
```

Expected:

```text
[main ...] feat: add MVP-0 platform database schema
```

---

## Task 4: Add Backend Health and API Response Foundation

**Files:**
- Create: `backend/src/main/java/com/company/itam/common/web/ApiResponse.java`
- Create: `backend/src/main/java/com/company/itam/common/web/HealthController.java`
- Create: `backend/src/test/java/com/company/itam/common/web/HealthControllerTest.java`

- [ ] **Step 1: Write failing health endpoint test**

Create `backend/src/test/java/com/company/itam/common/web/HealthControllerTest.java` with:

```java
package com.company.itam.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsHealthStatus() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd backend
mvn -Dtest=HealthControllerTest test
cd ..
```

Expected:

```text
Status expected:<200> but was:<404>
```

- [ ] **Step 3: Create common API response**

Create `backend/src/main/java/com/company/itam/common/web/ApiResponse.java` with:

```java
package com.company.itam.common.web;

public record ApiResponse<T>(boolean success, T data, String message) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
```

- [ ] **Step 4: Create health controller**

Create `backend/src/main/java/com/company/itam/common/web/HealthController.java` with:

```java
package com.company.itam.common.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
            "status", "UP",
            "time", Instant.now().toString()
        ));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```powershell
cd backend
mvn -Dtest=HealthControllerTest test
cd ..
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 6: Commit health foundation**

Run:

```powershell
git add backend/src/main/java/com/company/itam/common/web backend/src/test/java/com/company/itam/common/web
git commit -m "feat: add backend health endpoint"
```

Expected:

```text
[main ...] feat: add backend health endpoint
```

---

## Task 5: Add Tenant Context Foundation

**Files:**
- Create: `backend/src/main/java/com/company/itam/common/tenant/TenantContext.java`
- Create: `backend/src/main/java/com/company/itam/common/tenant/TenantContextHolder.java`
- Create: `backend/src/test/java/com/company/itam/common/tenant/TenantContextHolderTest.java`

- [ ] **Step 1: Write failing tenant context holder test**

Create `backend/src/test/java/com/company/itam/common/tenant/TenantContextHolderTest.java` with:

```java
package com.company.itam.common.tenant;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextHolderTest {
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void storesTenantContextForCurrentThread() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantContext context = new TenantContext(tenantId, userId, Set.of("tenant_admin"), false);

        TenantContextHolder.set(context);

        assertThat(TenantContextHolder.get()).isEqualTo(context);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void clearsTenantContext() {
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), UUID.randomUUID(), Set.of(), false));

        TenantContextHolder.clear();

        assertThat(TenantContextHolder.get()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd backend
mvn -Dtest=TenantContextHolderTest test
cd ..
```

Expected:

```text
cannot find symbol
```

- [ ] **Step 3: Create tenant context record**

Create `backend/src/main/java/com/company/itam/common/tenant/TenantContext.java` with:

```java
package com.company.itam.common.tenant;

import java.util.Set;
import java.util.UUID;

public record TenantContext(
    UUID tenantId,
    UUID userId,
    Set<String> roleCodes,
    boolean platformAdmin
) {
}
```

- [ ] **Step 4: Create tenant context holder**

Create `backend/src/main/java/com/company/itam/common/tenant/TenantContextHolder.java` with:

```java
package com.company.itam.common.tenant;

import java.util.UUID;

public final class TenantContextHolder {
    private static final ThreadLocal<TenantContext> HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext context) {
        HOLDER.set(context);
    }

    public static TenantContext get() {
        return HOLDER.get();
    }

    public static UUID getTenantId() {
        TenantContext context = HOLDER.get();
        return context == null ? null : context.tenantId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```powershell
cd backend
mvn -Dtest=TenantContextHolderTest test
cd ..
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 6: Commit tenant context**

Run:

```powershell
git add backend/src/main/java/com/company/itam/common/tenant backend/src/test/java/com/company/itam/common/tenant
git commit -m "feat: add tenant context foundation"
```

Expected:

```text
[main ...] feat: add tenant context foundation
```

---

## Task 6: Scaffold Frontend

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/index.html`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/stores/auth.ts`
- Create: `frontend/src/api/http.ts`

- [ ] **Step 1: Create frontend directories**

Run:

```powershell
New-Item -ItemType Directory -Force -Path `
  frontend/src/router,`
  frontend/src/stores,`
  frontend/src/api,`
  frontend/src/layouts,`
  frontend/src/modules
```

Expected: directories exist.

- [ ] **Step 2: Create `frontend/package.json`**

Create `frontend/package.json` with:

```json
{
  "name": "itam-frontend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite --host 127.0.0.1",
    "build": "vue-tsc -b && vite build",
    "test": "vitest run",
    "typecheck": "vue-tsc -b"
  },
  "dependencies": {
    "@element-plus/icons-vue": "^2.3.1",
    "axios": "^1.7.7",
    "element-plus": "^2.8.5",
    "pinia": "^2.2.4",
    "vue": "^3.5.12",
    "vue-router": "^4.4.5"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.1.4",
    "typescript": "^5.6.3",
    "vite": "^5.4.10",
    "vitest": "^2.1.4",
    "vue-tsc": "^2.1.8"
  }
}
```

- [ ] **Step 3: Create Vite and TypeScript config**

Create `frontend/vite.config.ts` with:

```ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://127.0.0.1:8080'
    }
  }
})
```

Create `frontend/tsconfig.json` with:

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "moduleResolution": "Bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "preserve",
    "strict": true
  },
  "include": ["src/**/*.ts", "src/**/*.vue"]
}
```

- [ ] **Step 4: Create frontend entry files**

Create `frontend/index.html` with:

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>IT Asset Management</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

Create `frontend/src/main.ts` with:

```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'

createApp(App)
  .use(createPinia())
  .use(router)
  .use(ElementPlus)
  .mount('#app')
```

Create `frontend/src/App.vue` with:

```vue
<template>
  <router-view />
</template>
```

- [ ] **Step 5: Create router and HTTP foundation**

Create `frontend/src/router/index.ts` with:

```ts
import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('../modules/HomePage.vue')
    }
  ]
})
```

Create `frontend/src/api/http.ts` with:

```ts
import axios from 'axios'

export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('itam_access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
```

Create `frontend/src/stores/auth.ts` with:

```ts
import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: localStorage.getItem('itam_access_token') || '',
    displayName: ''
  }),
  actions: {
    setAccessToken(token: string) {
      this.accessToken = token
      localStorage.setItem('itam_access_token', token)
    },
    clear() {
      this.accessToken = ''
      this.displayName = ''
      localStorage.removeItem('itam_access_token')
    }
  }
})
```

Create `frontend/src/modules/HomePage.vue` with:

```vue
<template>
  <main class="home-page">
    <h1>IT Asset Management</h1>
    <p>MVP-0 platform foundation is running.</p>
  </main>
</template>

<style scoped>
.home-page {
  padding: 24px;
}
</style>
```

- [ ] **Step 6: Install frontend dependencies**

Run:

```powershell
cd frontend
npm install
cd ..
```

Expected:

```text
added
```

- [ ] **Step 7: Verify frontend build**

Run:

```powershell
cd frontend
npm run build
cd ..
```

Expected:

```text
build
```

- [ ] **Step 8: Commit frontend scaffold**

Run:

```powershell
git add frontend
git commit -m "chore: scaffold Vue frontend"
```

Expected:

```text
[main ...] chore: scaffold Vue frontend
```

---

## Task 7: Add OpenAPI and Backend Smoke Verification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Start backend**

Run:

```powershell
cd backend
mvn spring-boot:run
```

Expected:

```text
Started ItamApplication
```

- [ ] **Step 2: Verify health endpoint**

In a second terminal, run:

```powershell
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/health'
```

Expected includes:

```text
success : True
data    : @{status=UP; ...}
```

- [ ] **Step 3: Verify OpenAPI endpoint**

Run:

```powershell
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api-docs' | Select-Object -ExpandProperty openapi
```

Expected:

```text
3.0.1
```

- [ ] **Step 4: Stop backend**

Stop the backend terminal with `Ctrl+C`.

- [ ] **Step 5: Update README verification section**

Append this to `README.md`:

````markdown
## Local Verification

Start dependencies:

```powershell
docker compose up -d postgres redis
```

Run backend:

```powershell
cd backend
mvn spring-boot:run
```

Run frontend:

```powershell
cd frontend
npm run dev
```

Verify backend health:

```powershell
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/health'
```

OpenAPI document:

`http://127.0.0.1:8080/swagger-ui.html`
````

- [ ] **Step 6: Commit verification docs**

Run:

```powershell
git add README.md
git commit -m "docs: add local verification steps"
```

Expected:

```text
[main ...] docs: add local verification steps
```

---

## Task 8: MVP-0 Completion Verification

**Files:**
- Verify full repository.

- [ ] **Step 1: Run backend test suite**

Run:

```powershell
cd backend
mvn test
cd ..
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 2: Run frontend build**

Run:

```powershell
cd frontend
npm run build
cd ..
```

Expected:

```text
build
```

- [ ] **Step 3: Verify Docker runtime**

Run:

```powershell
docker compose ps
```

Expected:

```text
itam-postgres
itam-redis
```

- [ ] **Step 4: Verify Git is clean**

Run:

```powershell
git status --short
```

Expected: no output.

If `frontend/package-lock.json` appears after `npm install`, commit it:

```powershell
git add frontend/package-lock.json
git commit -m "chore: commit frontend dependency lockfile"
```

- [ ] **Step 5: Tag MVP-0 foundation**

Run:

```powershell
git tag v0.1.0-mvp-0-foundation
```

Expected:

No terminal output. This means the tag was created.

---

## Self-Review Checklist

Spec coverage:

- Multi-tenant foundation: covered by tenant schema and `TenantContext`.
- Formal backend engineering skeleton: covered by Spring Boot scaffold.
- Formal frontend engineering skeleton: covered by Vue 3 TypeScript scaffold.
- PostgreSQL and Redis local runtime: covered by Docker Compose.
- Flyway migration: covered by `V001__platform_identity_permission_audit.sql`.
- RBAC base tables: covered by roles, permissions, and join tables.
- Audit foundation: covered by `audit_logs`.
- OpenAPI: covered by springdoc config and verification.
- Tests: covered by backend health and tenant context tests, frontend build verification.

Known deferred items for later MVP plans:

- Asset metadata tables.
- Asset core APIs.
- Lifecycle templates and transitions.
- Approval business flow.
- Software license domain.
- Data scope, field permission, and state permission implementation.
- Import/export, reports, notifications, integrations.

Placeholder scan:

- The plan does not use unresolved placeholders.
- Secrets in `.env.example` are local development examples and must be replaced before production.

Type consistency:

- Java package root is consistently `com.company.itam`.
- API base path is consistently `/api/v1`.
- Runtime containers are consistently named `itam-postgres` and `itam-redis`.
