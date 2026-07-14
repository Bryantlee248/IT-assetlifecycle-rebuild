# MVP-5 Production Hardening and Integrations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare the platform for pilot deployment with external API tokens, webhook foundation, sensitive field controls, attachment permissions, backup guidance, monitoring, and release documentation.

**Architecture:** This phase hardens the modular monolith for controlled pilot use. It does not change core domain behavior; it adds operational controls, secure integration boundaries, cache invalidation, observability, and deployment documentation.

**Tech Stack:** Spring Boot 3, PostgreSQL, Redis, Docker Compose, OpenAPI, Vue 3, TypeScript.

---

## Scope Check

This phase assumes MVP-4 exists: platform foundation, metadata assets, lifecycle, approval, permissions, notifications, import/export, reports, and lightweight assets.

This phase builds:

- External API token management.
- Webhook subscription foundation.
- Attachment permission enforcement.
- Sensitive field masking and export controls.
- Permission and metadata cache invalidation.
- Backup and restore scripts.
- Deployment documentation.
- Monitoring and operational logging.
- Pilot readiness checklist.

This phase does not build full SaaS billing, independent tenant databases, advanced SIEM integration, or cloud provider sync implementation.

## File Structure

```text
backend/src/main/java/com/company/itam/integration/
backend/src/main/java/com/company/itam/attachment/
backend/src/main/java/com/company/itam/common/cache/
backend/src/main/java/com/company/itam/common/observability/
backend/src/main/resources/db/migration/
  V008__production_hardening_integrations.sql
scripts/
  backup-postgres.ps1
  restore-postgres.ps1
docs/deployment/
  local-deployment.md
  pilot-deployment.md
  backup-restore.md
  operations-checklist.md
frontend/src/modules/integration/
frontend/src/modules/attachment/
```

---

## Task 1: Integration and Attachment Schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V008__production_hardening_integrations.sql`
- Test: `backend/src/test/java/com/company/itam/integration/IntegrationSchemaTest.java`

- [ ] **Step 1: Write schema test**

Verify:

```text
api_tokens
webhook_subscriptions
webhook_delivery_logs
attachments
system_settings
```

- [ ] **Step 2: Add migration**

Create:

```sql
CREATE TABLE api_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    token_name VARCHAR(128) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ
);

CREATE TABLE webhook_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(128) NOT NULL,
    target_url TEXT NOT NULL,
    events JSONB NOT NULL DEFAULT '[]'::jsonb,
    secret_hash VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE webhook_delivery_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    subscription_id UUID NOT NULL REFERENCES webhook_subscriptions(id),
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    response_code INTEGER,
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    business_type VARCHAR(64) NOT NULL,
    business_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(128),
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE system_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(id),
    setting_key VARCHAR(128) NOT NULL,
    setting_value JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, setting_key)
);
```

- [ ] **Step 3: Run schema test**

Run:

```powershell
cd backend
mvn -Dtest=IntegrationSchemaTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit schema**

Run:

```powershell
git add backend/src/main/resources/db/migration/V008__production_hardening_integrations.sql backend/src/test/java/com/company/itam/integration
git commit -m "feat: add integration attachment hardening schema"
```

---

## Task 2: API Token and Webhook Foundation

**Files:**
- Create: `backend/src/main/java/com/company/itam/integration/controller/ApiTokenController.java`
- Create: `backend/src/main/java/com/company/itam/integration/controller/WebhookController.java`
- Create: `backend/src/main/java/com/company/itam/integration/application/IntegrationApplicationService.java`
- Test: `backend/src/test/java/com/company/itam/integration/IntegrationApiTest.java`

- [ ] **Step 1: Write integration API tests**

Tests:

```text
POST /api/v1/integrations/api-tokens returns token once.
Stored token is hashed.
API token can authenticate scoped external request.
POST /api/v1/integrations/webhooks creates subscription.
Webhook delivery log is created for lifecycle transition event.
Disabled webhook does not receive delivery.
```

- [ ] **Step 2: Implement API token endpoints**

Endpoints:

```text
GET  /api/v1/integrations/api-tokens
POST /api/v1/integrations/api-tokens
PATCH /api/v1/integrations/api-tokens/{id}/revoke
```

Token response:

```json
{
  "tokenId": "uuid",
  "token": "itam_live_generated_token_visible_once"
}
```

- [ ] **Step 3: Implement webhook endpoints**

Endpoints:

```text
GET  /api/v1/integrations/webhooks
POST /api/v1/integrations/webhooks
PUT  /api/v1/integrations/webhooks/{id}
PATCH /api/v1/integrations/webhooks/{id}/status
GET  /api/v1/integrations/webhooks/{id}/deliveries
```

Supported MVP events:

```text
asset.created
asset.updated
lifecycle.transitioned
approval.completed
license.assigned
license.reclaimed
```

- [ ] **Step 4: Run integration tests**

Run:

```powershell
cd backend
mvn -Dtest=IntegrationApiTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit integration foundation**

Run:

```powershell
git add backend/src/main/java/com/company/itam/integration backend/src/test/java/com/company/itam/integration
git commit -m "feat: add API token and webhook foundation"
```

---

## Task 3: Attachment Permission and Sensitive Field Controls

**Files:**
- Create: `backend/src/main/java/com/company/itam/attachment/controller/AttachmentController.java`
- Create: `backend/src/main/java/com/company/itam/attachment/application/AttachmentApplicationService.java`
- Modify: `backend/src/main/java/com/company/itam/permission/domain/FieldPermissionResolver.java`
- Test: `backend/src/test/java/com/company/itam/attachment/AttachmentPermissionTest.java`
- Test: `backend/src/test/java/com/company/itam/permission/SensitiveFieldMaskingTest.java`

- [ ] **Step 1: Write tests**

Tests:

```text
User without business object access cannot download attachment.
Deleted attachment cannot be downloaded.
Sensitive field is masked in list response.
Sensitive field is masked in detail response unless role has visible permission.
Sensitive field is excluded from export when user lacks visibility.
```

- [ ] **Step 2: Implement attachment endpoints**

Endpoints:

```text
POST   /api/v1/attachments
GET    /api/v1/attachments/{id}
DELETE /api/v1/attachments/{id}
```

Rules:

- Attachment inherits permission from business object.
- Attachment path includes tenant directory.
- Download writes audit log for sensitive business types.

- [ ] **Step 3: Implement sensitive field masking**

Masking rules:

```text
license_key -> show last 4 characters only
contract_amount -> hidden when field permission invisible
contract_attachment -> hidden when field permission invisible
```

- [ ] **Step 4: Run tests**

Run:

```powershell
cd backend
mvn -Dtest=AttachmentPermissionTest,SensitiveFieldMaskingTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit attachment and masking**

Run:

```powershell
git add backend/src/main/java/com/company/itam/attachment backend/src/main/java/com/company/itam/permission backend/src/test/java/com/company/itam/attachment backend/src/test/java/com/company/itam/permission
git commit -m "feat: enforce attachment and sensitive field controls"
```

---

## Task 4: Cache Invalidation and Observability

**Files:**
- Create: `backend/src/main/java/com/company/itam/common/cache/CacheNames.java`
- Create: `backend/src/main/java/com/company/itam/common/cache/CacheInvalidationService.java`
- Create: `backend/src/main/java/com/company/itam/common/observability/RequestLoggingFilter.java`
- Test: `backend/src/test/java/com/company/itam/common/cache/CacheInvalidationTest.java`

- [ ] **Step 1: Write cache invalidation tests**

Tests:

```text
metadata update evicts metadata cache for tenant.
permission update evicts permission cache for tenant and role.
cache key includes tenant ID.
```

- [ ] **Step 2: Implement cache names**

Cache names:

```text
metadata:{tenantId}:{assetTypeId}
permission:{tenantId}:{userId}
field_permission:{tenantId}:{roleId}:{assetTypeId}
runtime_metadata:{tenantId}:{assetTypeId}
```

- [ ] **Step 3: Implement request logging filter**

Log fields:

```text
request_id
tenant_id
user_id
method
path
status
duration_ms
```

- [ ] **Step 4: Run tests**

Run:

```powershell
cd backend
mvn -Dtest=CacheInvalidationTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit cache and observability**

Run:

```powershell
git add backend/src/main/java/com/company/itam/common/cache backend/src/main/java/com/company/itam/common/observability backend/src/test/java/com/company/itam/common/cache
git commit -m "feat: add cache invalidation and request logging"
```

---

## Task 5: Deployment, Backup, and Operations Documentation

**Files:**
- Create: `scripts/backup-postgres.ps1`
- Create: `scripts/restore-postgres.ps1`
- Create: `docs/deployment/local-deployment.md`
- Create: `docs/deployment/pilot-deployment.md`
- Create: `docs/deployment/backup-restore.md`
- Create: `docs/deployment/operations-checklist.md`

- [ ] **Step 1: Create backup script**

`scripts/backup-postgres.ps1`:

```powershell
param(
  [string]$Container = "itam-postgres",
  [string]$Database = "itam",
  [string]$User = "itam",
  [string]$Output = "backup-itam.sql"
)

docker exec $Container pg_dump -U $User -d $Database | Out-File -Encoding utf8 $Output
Write-Output "Backup written to $Output"
```

- [ ] **Step 2: Create restore script**

`scripts/restore-postgres.ps1`:

```powershell
param(
  [string]$Container = "itam-postgres",
  [string]$Database = "itam",
  [string]$User = "itam",
  [Parameter(Mandatory=$true)][string]$InputFile
)

Get-Content $InputFile | docker exec -i $Container psql -U $User -d $Database
Write-Output "Restore completed from $InputFile"
```

- [ ] **Step 3: Create deployment docs**

Docs must include:

```text
required ports
required environment variables
database startup
backend startup
frontend startup
health check URL
OpenAPI URL
backup command
restore command
pilot acceptance checklist
rollback steps
```

- [ ] **Step 4: Commit operational docs**

Run:

```powershell
git add scripts docs/deployment
git commit -m "docs: add deployment backup operations guide"
```

---

## Task 6: Frontend Integration and Operations Pages

**Files:**
- Create: `frontend/src/modules/integration/ApiTokenPage.vue`
- Create: `frontend/src/modules/integration/WebhookPage.vue`
- Create: `frontend/src/modules/attachment/AttachmentList.vue`

- [ ] **Step 1: Build API token page**

Features:

- List tokens without showing token values.
- Create token and show generated token once.
- Revoke token.

- [ ] **Step 2: Build webhook page**

Features:

- List webhook subscriptions.
- Create and edit target URL and events.
- Enable or disable subscription.
- View delivery logs.

- [ ] **Step 3: Build attachment component**

Features:

- Upload attachment.
- List business object attachments.
- Download if permission allows.
- Delete if permission allows.

- [ ] **Step 4: Run frontend build**

Run:

```powershell
cd frontend
npm run build
```

Expected: build success.

- [ ] **Step 5: Commit frontend operations pages**

Run:

```powershell
git add frontend/src
git commit -m "feat: add integration and attachment frontend"
```

---

## Task 7: MVP-5 Final Verification and Release

**Files:**
- Create: `docs/qa/mvp-5-acceptance.md`
- Create: `docs/release/v1.0.0-pilot-release-notes.md`

- [ ] **Step 1: Create acceptance checklist**

`docs/qa/mvp-5-acceptance.md`:

```markdown
# MVP-5 Acceptance

- API tokens can be created, used with scopes, and revoked.
- Webhook subscriptions can be created and disabled.
- Webhook delivery logs are written for supported events.
- Attachments enforce business object permissions.
- Sensitive fields are masked or hidden in list, detail, and export responses.
- Metadata and permission cache keys include tenant ID.
- Metadata and permission updates invalidate relevant caches.
- Request logs include request ID, tenant ID, user ID, path, status, and duration.
- Backup script creates a PostgreSQL dump.
- Restore script can restore the dump to a clean local database.
- Deployment and operations documents are complete.
```

- [ ] **Step 2: Create pilot release notes**

`docs/release/v1.0.0-pilot-release-notes.md`:

```markdown
# v1.0.0 Pilot Release Notes

## Included

- Multi-tenant platform foundation.
- Metadata-driven asset model.
- Data center asset and software license lifecycle.
- Lightweight approval.
- Role, data, field, and state permissions.
- Import, export, reports, certificates, domains, cloud resource records.
- API token and webhook foundation.
- Attachment permissions and sensitive field controls.
- Deployment, backup, and operations documents.

## Known Limits

- No independent database per tenant.
- No full BPMN workflow designer.
- No cloud provider automatic sync.
- No complex CMDB topology graph.
- No full BI report designer.
```

- [ ] **Step 3: Run full verification**

Run:

```powershell
cd backend
mvn test
cd ../frontend
npm run build
```

Expected: all checks pass.

- [ ] **Step 4: Commit final docs**

Run:

```powershell
git add docs/qa/mvp-5-acceptance.md docs/release/v1.0.0-pilot-release-notes.md
git commit -m "docs: add MVP-5 acceptance and pilot release notes"
```

- [ ] **Step 5: Tag pilot release**

Run:

```powershell
git tag v1.0.0-pilot
```

Expected: no terminal output.

---

## Self-Review Checklist

Spec coverage:

- Webhook foundation: covered.
- API token foundation: covered.
- Attachment permission: covered.
- Sensitive field masking: covered.
- Cache invalidation: covered.
- Backup and restore: covered.
- Deployment and operations docs: covered.
- Pilot release notes: covered.

Deferred beyond pilot:

- Independent tenant database.
- Cloud provider sync implementation.
- Full workflow designer.
- Advanced observability stack.

