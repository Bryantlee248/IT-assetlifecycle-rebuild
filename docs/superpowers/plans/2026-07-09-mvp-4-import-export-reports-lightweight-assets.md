# MVP-4 Import, Export, Reports, and Lightweight Assets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add operational capabilities: Excel import/export, validation reports, dashboard reports, certificate/domain management, cloud resource management, and expiration reminders.

**Architecture:** This phase builds on metadata-driven assets and permission enforcement. Import/export uses metadata schemas and field permissions. Reports read business data without driving workflows. Certificates, domains, and cloud resources are lightweight asset modules using the common asset core.

**Tech Stack:** Spring Boot 3, PostgreSQL, MyBatis Plus, Apache POI or EasyExcel, Vue 3, TypeScript, Element Plus.

---

## Scope Check

This phase assumes MVP-3 exists: assets, metadata, lifecycle, approval, permissions, notifications.

This phase builds:

- Excel import templates.
- Excel import validation and async job records.
- Excel export with field permission filtering.
- Asset overview reports.
- Lifecycle summary.
- Software license usage report.
- Expiration alerts.
- Pending task report.
- Certificate/domain lightweight pages.
- Cloud resource lightweight pages.

This phase does not build cloud provider sync, complex BI, scheduled email delivery, or full CMDB topology.

## File Structure

```text
backend/src/main/java/com/company/itam/importexport/
backend/src/main/java/com/company/itam/report/
backend/src/main/java/com/company/itam/certificate/
backend/src/main/java/com/company/itam/cloudresource/
backend/src/main/resources/db/migration/
  V007__import_export_reports_lightweight_assets.sql
backend/src/test/java/com/company/itam/importexport/
backend/src/test/java/com/company/itam/report/
frontend/src/modules/importexport/
frontend/src/modules/report/
frontend/src/modules/certificate/
frontend/src/modules/cloudresource/
```

---

## Task 1: Import Job and Lightweight Asset Schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V007__import_export_reports_lightweight_assets.sql`
- Test: `backend/src/test/java/com/company/itam/importexport/ImportExportSchemaTest.java`

- [ ] **Step 1: Write schema test**

Verify:

```text
import_jobs
import_job_errors
certificate_records
domain_records
cloud_resource_records
```

- [ ] **Step 2: Add migration**

Create:

```sql
CREATE TABLE import_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    asset_type_id UUID REFERENCES asset_types(id),
    job_type VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    total_rows INTEGER NOT NULL DEFAULT 0,
    success_rows INTEGER NOT NULL DEFAULT 0,
    failed_rows INTEGER NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE import_job_errors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    job_id UUID NOT NULL REFERENCES import_jobs(id),
    row_no INTEGER NOT NULL,
    field_code VARCHAR(64),
    error_message TEXT NOT NULL
);

CREATE TABLE certificate_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    asset_id UUID NOT NULL REFERENCES assets(id),
    certificate_subject VARCHAR(255),
    domain_name VARCHAR(255),
    expire_date DATE NOT NULL,
    responsible_user_id UUID,
    business_system VARCHAR(128),
    renewal_record JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE TABLE domain_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    asset_id UUID NOT NULL REFERENCES assets(id),
    domain_name VARCHAR(255) NOT NULL,
    registrar VARCHAR(128),
    expire_date DATE NOT NULL,
    responsible_user_id UUID,
    business_system VARCHAR(128)
);

CREATE TABLE cloud_resource_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    asset_id UUID NOT NULL REFERENCES assets(id),
    provider VARCHAR(64) NOT NULL,
    region VARCHAR(64),
    resource_id VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    cost_center_id UUID,
    business_system VARCHAR(128),
    sync_source VARCHAR(64) NOT NULL DEFAULT 'manual',
    UNIQUE (tenant_id, provider, resource_id)
);
```

- [ ] **Step 3: Run schema test**

Run:

```powershell
cd backend
mvn -Dtest=ImportExportSchemaTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit schema**

Run:

```powershell
git add backend/src/main/resources/db/migration/V007__import_export_reports_lightweight_assets.sql backend/src/test/java/com/company/itam/importexport
git commit -m "feat: add import export report lightweight asset schema"
```

---

## Task 2: Import and Export Backend

**Files:**
- Create: `backend/src/main/java/com/company/itam/importexport/controller/ImportExportController.java`
- Create: `backend/src/main/java/com/company/itam/importexport/application/ImportExportApplicationService.java`
- Create: `backend/src/main/java/com/company/itam/importexport/domain/AssetExcelMapper.java`
- Test: `backend/src/test/java/com/company/itam/importexport/ImportExportApiTest.java`

- [ ] **Step 1: Write import/export tests**

Tests:

```text
GET /api/v1/assets/import-template returns Excel file for asset type.
POST /api/v1/assets/import creates import job and validates rows.
GET /api/v1/assets/import-jobs/{id} returns job result.
GET /api/v1/assets/export excludes unauthorized fields.
Import rejects unknown dynamic fields.
Import validates required fields and enum fields.
```

- [ ] **Step 2: Implement template generation**

Template columns:

```text
asset_no
asset_name
owner_org_id
responsible_user_id
location_id
all visible and editable metadata fields for asset type
```

- [ ] **Step 3: Implement import validation**

Use existing `AssetAttributeValidator`.

Import result:

```json
{
  "jobId": "uuid",
  "status": "completed",
  "totalRows": 20,
  "successRows": 18,
  "failedRows": 2
}
```

- [ ] **Step 4: Implement export**

Export must apply:

```text
tenant filter
data scope filter
field visibility filter
asset type filter
search filters
```

- [ ] **Step 5: Run import/export tests**

Run:

```powershell
cd backend
mvn -Dtest=ImportExportApiTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit import/export**

Run:

```powershell
git add backend/src/main/java/com/company/itam/importexport backend/src/test/java/com/company/itam/importexport
git commit -m "feat: add metadata-driven import export"
```

---

## Task 3: Reports Backend

**Files:**
- Create: `backend/src/main/java/com/company/itam/report/controller/ReportController.java`
- Create: `backend/src/main/java/com/company/itam/report/application/ReportApplicationService.java`
- Test: `backend/src/test/java/com/company/itam/report/ReportApiTest.java`

- [ ] **Step 1: Write report API tests**

Tests:

```text
GET /api/v1/reports/asset-summary returns count by kind and type.
GET /api/v1/reports/lifecycle-summary returns count by lifecycle state.
GET /api/v1/reports/license-usage returns total, used, available quantities.
GET /api/v1/reports/expiration-alerts returns assets expiring within configured days.
GET /api/v1/reports/pending-tasks returns pending approval and lifecycle tasks.
Reports respect tenant isolation and data scope.
```

- [ ] **Step 2: Implement report endpoints**

Endpoints:

```text
GET /api/v1/reports/asset-summary
GET /api/v1/reports/lifecycle-summary
GET /api/v1/reports/license-usage
GET /api/v1/reports/expiration-alerts
GET /api/v1/reports/pending-tasks
```

- [ ] **Step 3: Run report tests**

Run:

```powershell
cd backend
mvn -Dtest=ReportApiTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit reports**

Run:

```powershell
git add backend/src/main/java/com/company/itam/report backend/src/test/java/com/company/itam/report
git commit -m "feat: add MVP reports"
```

---

## Task 4: Certificate, Domain, and Cloud Resource Backend

**Files:**
- Create: `backend/src/main/java/com/company/itam/certificate/`
- Create: `backend/src/main/java/com/company/itam/cloudresource/`
- Test: `backend/src/test/java/com/company/itam/certificate/CertificateDomainApiTest.java`
- Test: `backend/src/test/java/com/company/itam/cloudresource/CloudResourceApiTest.java`

- [ ] **Step 1: Write API tests**

Certificate/domain tests:

```text
POST /api/v1/certificates creates asset and certificate record.
GET /api/v1/certificates/expiring returns near-expiration records.
POST /api/v1/domains creates asset and domain record.
```

Cloud resource tests:

```text
POST /api/v1/cloud-resources creates asset and cloud resource record.
GET /api/v1/cloud-resources/cost-summary groups by cost center.
Duplicate provider/resource_id is rejected within tenant.
```

- [ ] **Step 2: Implement endpoints**

Endpoints:

```text
GET  /api/v1/certificates
POST /api/v1/certificates
PUT  /api/v1/certificates/{id}
GET  /api/v1/certificates/expiring
GET  /api/v1/domains
POST /api/v1/domains
PUT  /api/v1/domains/{id}
GET  /api/v1/cloud-resources
POST /api/v1/cloud-resources
PUT  /api/v1/cloud-resources/{id}
GET  /api/v1/cloud-resources/cost-summary
```

- [ ] **Step 3: Run tests**

Run:

```powershell
cd backend
mvn -Dtest=CertificateDomainApiTest,CloudResourceApiTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit lightweight assets**

Run:

```powershell
git add backend/src/main/java/com/company/itam/certificate backend/src/main/java/com/company/itam/cloudresource backend/src/test/java/com/company/itam/certificate backend/src/test/java/com/company/itam/cloudresource
git commit -m "feat: add lightweight certificate domain cloud resources"
```

---

## Task 5: Frontend Pages

**Files:**
- Create: `frontend/src/modules/importexport/ImportExportPage.vue`
- Create: `frontend/src/modules/report/DashboardPage.vue`
- Create: `frontend/src/modules/certificate/CertificatePage.vue`
- Create: `frontend/src/modules/cloudresource/CloudResourcePage.vue`

- [ ] **Step 1: Build import/export page**

Features:

- Select asset type.
- Download template.
- Upload Excel.
- Show import job result and row errors.
- Export current filtered asset list.

- [ ] **Step 2: Build dashboard page**

Cards:

```text
asset total
assets by kind
assets by lifecycle state
license usage
expiration alerts
pending tasks
```

- [ ] **Step 3: Build lightweight asset pages**

Pages:

```text
/certificates
/domains
/cloud-resources
```

Features:

- List.
- Create/edit form.
- Expiration or cost summary.

- [ ] **Step 4: Run frontend build**

Run:

```powershell
cd frontend
npm run build
```

Expected: build success.

- [ ] **Step 5: Commit frontend pages**

Run:

```powershell
git add frontend/src
git commit -m "feat: add import export reports lightweight asset frontend"
```

---

## Task 6: MVP-4 Acceptance

**Files:**
- Create: `docs/qa/mvp-4-acceptance.md`

- [ ] **Step 1: Create acceptance checklist**

Content:

```markdown
# MVP-4 Acceptance

- Asset import template matches metadata fields.
- Import validates required, enum, unique, and unknown fields.
- Import job reports row-level errors.
- Export respects tenant, data scope, and field visibility permissions.
- Dashboard shows asset summary, lifecycle summary, license usage, expiration alerts, and pending tasks.
- Certificate and domain records can be created with expiration dates.
- Cloud resource records can be created with provider, region, resource ID, and cost center.
- Expiring certificates, domains, warranties, and licenses can be queried.
```

- [ ] **Step 2: Run full checks**

Run:

```powershell
cd backend
mvn test
cd ../frontend
npm run build
```

Expected: all checks pass.

- [ ] **Step 3: Commit acceptance**

Run:

```powershell
git add docs/qa/mvp-4-acceptance.md
git commit -m "docs: add MVP-4 acceptance checklist"
```

---

## Self-Review Checklist

Spec coverage:

- Import/export: covered.
- Report dashboard: covered.
- Certificate/domain lightweight management: covered.
- Cloud resource lightweight management: covered.
- Expiration alert querying: covered.

Deferred to MVP-5:

- Webhook.
- External API tokens.
- Production monitoring and backup.
- Cloud provider automatic sync.

