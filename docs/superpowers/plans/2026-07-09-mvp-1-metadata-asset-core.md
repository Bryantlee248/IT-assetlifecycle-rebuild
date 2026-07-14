# MVP-1 Metadata and Asset Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build metadata-driven asset type, field, form, list, search, asset CRUD, and asset relation foundations.

**Architecture:** This phase extends the MVP-0 modular monolith with `metadata` and `asset-core` modules. Asset common fields are stored in relational columns, dynamic asset fields are stored in PostgreSQL JSONB, and runtime metadata drives frontend forms, lists, and search panels.

**Tech Stack:** Spring Boot 3, MyBatis Plus, PostgreSQL JSONB, Flyway, Vue 3, TypeScript, Element Plus, Pinia, Vitest.

---

## Scope Check

This plan assumes MVP-0 exists: tenant context, identity, RBAC skeleton, audit table, backend/frontend scaffolds, Flyway, Docker runtime, OpenAPI.

This phase builds:

- Asset type tree.
- Field definitions.
- Form schema.
- List view schema.
- Search schema.
- Asset core table and APIs.
- JSONB dynamic attributes.
- Runtime metadata endpoint.
- Asset relation table and APIs.
- Preset metadata for server, network device, security device, software license, certificate, domain, and cloud resource.

This phase does not build lifecycle transitions, approval flow, license assignment, import/export, reporting, or production integration.

## File Structure

Backend files:

```text
backend/src/main/java/com/company/itam/metadata/
  controller/
  application/
  domain/
  repository/
  dto/
backend/src/main/java/com/company/itam/asset/
  controller/
  application/
  domain/
  repository/
  dto/
backend/src/main/resources/db/migration/
  V002__metadata_asset_core.sql
backend/src/test/java/com/company/itam/metadata/
backend/src/test/java/com/company/itam/asset/
```

Frontend files:

```text
frontend/src/modules/metadata/
frontend/src/modules/asset/
frontend/src/api/metadata.ts
frontend/src/api/assets.ts
frontend/src/types/metadata.ts
frontend/src/types/asset.ts
```

---

## Task 1: Metadata and Asset Database Schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V002__metadata_asset_core.sql`
- Test: `backend/src/test/java/com/company/itam/metadata/MetadataSchemaMigrationTest.java`

- [ ] **Step 1: Write migration verification test**

Create a Spring Boot integration test that starts the application and queries these tables:

```sql
select to_regclass('asset_types');
select to_regclass('field_definitions');
select to_regclass('form_schemas');
select to_regclass('list_view_schemas');
select to_regclass('search_schemas');
select to_regclass('assets');
select to_regclass('asset_relations');
```

Expected before implementation: test fails because tables do not exist.

- [ ] **Step 2: Add migration**

Create tables:

```sql
CREATE TABLE asset_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    parent_id UUID REFERENCES asset_types(id),
    type_code VARCHAR(64) NOT NULL,
    type_name VARCHAR(128) NOT NULL,
    asset_kind VARCHAR(32) NOT NULL,
    lifecycle_template_id UUID,
    icon VARCHAR(64),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, type_code)
);

CREATE TABLE field_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    asset_type_id UUID NOT NULL REFERENCES asset_types(id),
    field_code VARCHAR(64) NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    field_type VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    unique_scope VARCHAR(32) NOT NULL DEFAULT 'none',
    default_value TEXT,
    validation_rule JSONB,
    data_source JSONB,
    searchable BOOLEAN NOT NULL DEFAULT FALSE,
    sortable BOOLEAN NOT NULL DEFAULT FALSE,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    editable BOOLEAN NOT NULL DEFAULT TRUE,
    sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    encrypted BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, asset_type_id, field_code)
);

CREATE TABLE form_schemas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    asset_type_id UUID NOT NULL REFERENCES asset_types(id),
    form_code VARCHAR(64) NOT NULL,
    form_name VARCHAR(128) NOT NULL,
    schema_json JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, asset_type_id, form_code)
);

CREATE TABLE list_view_schemas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    asset_type_id UUID NOT NULL REFERENCES asset_types(id),
    view_code VARCHAR(64) NOT NULL,
    view_name VARCHAR(128) NOT NULL,
    schema_json JSONB NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, asset_type_id, view_code)
);

CREATE TABLE search_schemas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    asset_type_id UUID NOT NULL REFERENCES asset_types(id),
    schema_json JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, asset_type_id)
);

CREATE TABLE assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    asset_no VARCHAR(64) NOT NULL,
    asset_name VARCHAR(128) NOT NULL,
    asset_kind VARCHAR(32) NOT NULL,
    asset_type_id UUID NOT NULL REFERENCES asset_types(id),
    lifecycle_status VARCHAR(64) NOT NULL DEFAULT 'planned',
    owner_user_id UUID,
    owner_org_id UUID,
    location_id UUID,
    cost_center_id UUID,
    responsible_user_id UUID,
    source_type VARCHAR(32) NOT NULL DEFAULT 'manual',
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, asset_no)
);

CREATE TABLE asset_relations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    source_asset_id UUID NOT NULL REFERENCES assets(id),
    target_asset_id UUID NOT NULL REFERENCES assets(id),
    relation_type VARCHAR(64) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_assets_tenant_type ON assets(tenant_id, asset_type_id) WHERE deleted = FALSE;
CREATE INDEX idx_assets_tenant_status ON assets(tenant_id, lifecycle_status) WHERE deleted = FALSE;
CREATE INDEX idx_assets_attributes_gin ON assets USING gin(attributes);
```

- [ ] **Step 3: Run migration test**

Run:

```powershell
cd backend
mvn -Dtest=MetadataSchemaMigrationTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit schema**

Run:

```powershell
git add backend/src/main/resources/db/migration/V002__metadata_asset_core.sql backend/src/test/java/com/company/itam/metadata
git commit -m "feat: add metadata and asset core schema"
```

---

## Task 2: Metadata Backend APIs

**Files:**
- Create: `backend/src/main/java/com/company/itam/metadata/controller/AssetTypeController.java`
- Create: `backend/src/main/java/com/company/itam/metadata/controller/FieldDefinitionController.java`
- Create: `backend/src/main/java/com/company/itam/metadata/controller/RuntimeMetadataController.java`
- Create: `backend/src/main/java/com/company/itam/metadata/application/MetadataApplicationService.java`
- Test: `backend/src/test/java/com/company/itam/metadata/MetadataApiTest.java`

- [ ] **Step 1: Write API tests**

Test these endpoints:

```text
POST /api/v1/metadata/asset-types
GET  /api/v1/metadata/asset-types/tree
POST /api/v1/metadata/asset-types/{typeId}/fields
GET  /api/v1/metadata/asset-types/{typeId}/fields
PUT  /api/v1/metadata/asset-types/{typeId}/form-schema
PUT  /api/v1/metadata/asset-types/{typeId}/list-view
PUT  /api/v1/metadata/asset-types/{typeId}/search-schema
GET  /api/v1/metadata/runtime/asset-types/{typeId}
```

Expected before implementation: 404.

- [ ] **Step 2: Implement metadata DTOs**

DTO names:

```text
AssetTypeCreateRequest
AssetTypeResponse
FieldDefinitionCreateRequest
FieldDefinitionResponse
FormSchemaRequest
ListViewSchemaRequest
SearchSchemaRequest
RuntimeMetadataResponse
```

Required validations:

```text
typeCode: non-empty, lowercase snake or kebab code
assetKind: tangible or intangible
fieldCode: non-empty, lowercase snake code
fieldType: one of text, textarea, number, decimal, date, datetime, enum, multi_enum, bool, user, org, location, asset_relation, file, url, json
schemaJson: valid JSON object
```

- [ ] **Step 3: Implement metadata services and repositories**

Rules:

- All reads and writes use current `tenant_id`.
- Field code cannot be changed after creation.
- Disabled fields are hidden from runtime metadata.
- Runtime metadata merges field definitions, form schema, list view schema, search schema, and field permission stubs.

- [ ] **Step 4: Run tests**

Run:

```powershell
cd backend
mvn -Dtest=MetadataApiTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit metadata APIs**

Run:

```powershell
git add backend/src/main/java/com/company/itam/metadata backend/src/test/java/com/company/itam/metadata
git commit -m "feat: add metadata management APIs"
```

---

## Task 3: Asset Core Backend APIs

**Files:**
- Create: `backend/src/main/java/com/company/itam/asset/controller/AssetController.java`
- Create: `backend/src/main/java/com/company/itam/asset/controller/AssetRelationController.java`
- Create: `backend/src/main/java/com/company/itam/asset/application/AssetApplicationService.java`
- Create: `backend/src/main/java/com/company/itam/asset/domain/AssetAttributeValidator.java`
- Test: `backend/src/test/java/com/company/itam/asset/AssetApiTest.java`

- [ ] **Step 1: Write asset API tests**

Tests must verify:

```text
POST /api/v1/assets creates an asset with JSONB attributes.
GET /api/v1/assets returns only current tenant assets.
GET /api/v1/assets/{assetId} returns dynamic attributes.
PUT /api/v1/assets/{assetId} updates allowed dynamic attributes.
POST /api/v1/assets/{assetId}/relations creates a relation.
GET /api/v1/assets/{assetId}/relations lists relations.
```

Expected before implementation: 404.

- [ ] **Step 2: Implement attribute validation**

Validation rules:

- Required configured fields must exist.
- Enum fields must match configured values.
- Number fields must parse as numbers.
- Date fields must parse as ISO dates.
- Unique fields are checked within their configured scope.
- Unknown attribute fields are rejected unless `field_type=json` is explicitly configured.

- [ ] **Step 3: Implement asset APIs**

Endpoints:

```text
GET  /api/v1/assets
POST /api/v1/assets
GET  /api/v1/assets/{assetId}
PUT  /api/v1/assets/{assetId}
DELETE /api/v1/assets/{assetId}
GET  /api/v1/assets/{assetId}/relations
POST /api/v1/assets/{assetId}/relations
DELETE /api/v1/assets/{assetId}/relations/{relationId}
```

Query supports:

```text
assetTypeId
assetKind
lifecycleStatus
keyword
ownerOrgId
responsibleUserId
locationId
page
pageSize
```

- [ ] **Step 4: Run asset API tests**

Run:

```powershell
cd backend
mvn -Dtest=AssetApiTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit asset core APIs**

Run:

```powershell
git add backend/src/main/java/com/company/itam/asset backend/src/test/java/com/company/itam/asset
git commit -m "feat: add asset core APIs"
```

---

## Task 4: Preset MVP Metadata

**Files:**
- Create: `backend/src/main/resources/db/migration/V003__seed_mvp_asset_metadata.sql`
- Test: `backend/src/test/java/com/company/itam/metadata/PresetMetadataTest.java`

- [ ] **Step 1: Write preset metadata test**

Test that a default tenant can receive these asset types:

```text
server
network_device
security_device
software_license
certificate
domain
cloud_resource
```

Expected before seed: missing rows.

- [ ] **Step 2: Add seed migration**

Seed templates for:

Server fields:

```text
brand, model, sn, cpu, memory_gb, warranty_end_date, rack, u_position
```

Software license fields:

```text
vendor, license_model, total_quantity, start_date, end_date, license_key, contract_no
```

Certificate/domain fields:

```text
domain_name, certificate_subject, expire_date, responsible_user_id, business_system
```

Cloud resource fields:

```text
provider, region, resource_id, resource_type, cost_center, business_system
```

- [ ] **Step 3: Run preset metadata test**

Run:

```powershell
cd backend
mvn -Dtest=PresetMetadataTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit preset metadata**

Run:

```powershell
git add backend/src/main/resources/db/migration/V003__seed_mvp_asset_metadata.sql backend/src/test/java/com/company/itam/metadata/PresetMetadataTest.java
git commit -m "feat: seed MVP asset metadata"
```

---

## Task 5: Frontend Metadata and Asset Pages

**Files:**
- Create: `frontend/src/api/metadata.ts`
- Create: `frontend/src/api/assets.ts`
- Create: `frontend/src/types/metadata.ts`
- Create: `frontend/src/types/asset.ts`
- Create: `frontend/src/modules/metadata/AssetTypePage.vue`
- Create: `frontend/src/modules/asset/AssetListPage.vue`
- Create: `frontend/src/modules/asset/AssetFormDrawer.vue`
- Modify: `frontend/src/router/index.ts`

- [ ] **Step 1: Add API clients and types**

Add typed API clients matching backend endpoints:

```ts
export interface FieldDefinition {
  id: string
  fieldCode: string
  fieldName: string
  fieldType: string
  required: boolean
  visible: boolean
  editable: boolean
}
```

Asset request shape:

```ts
export interface AssetSaveRequest {
  assetTypeId: string
  assetName: string
  ownerOrgId?: string
  responsibleUserId?: string
  locationId?: string
  attributes: Record<string, unknown>
}
```

- [ ] **Step 2: Build metadata pages**

Pages:

```text
/metadata/asset-types
```

Features:

- Asset type tree.
- Field list.
- Add/edit field drawer.
- Form schema editor using structured JSON text area.
- List/search schema editors using structured JSON text area.

- [ ] **Step 3: Build asset pages**

Pages:

```text
/assets
```

Features:

- Asset type selector.
- Runtime metadata load.
- Dynamic table columns.
- Dynamic search controls.
- Dynamic create/edit form.
- JSONB attributes submit.

- [ ] **Step 4: Run frontend checks**

Run:

```powershell
cd frontend
npm run typecheck
npm run build
```

Expected: both commands complete successfully.

- [ ] **Step 5: Commit frontend MVP-1 pages**

Run:

```powershell
git add frontend/src
git commit -m "feat: add metadata-driven asset frontend"
```

---

## Task 6: MVP-1 End-to-End Verification

**Files:**
- Create: `docs/qa/mvp-1-acceptance.md`

- [ ] **Step 1: Create acceptance checklist**

Create `docs/qa/mvp-1-acceptance.md` with:

```markdown
# MVP-1 Acceptance

- Tenant admin can create an asset type.
- Tenant admin can add fields to an asset type.
- Tenant admin can configure form/list/search schemas.
- Runtime metadata endpoint returns field, form, list, and search metadata.
- User can create a server asset with dynamic attributes.
- User can create a software license asset with different dynamic attributes.
- Asset list renders configured columns.
- Asset details preserve JSONB attributes.
- Asset relation can bind one asset to another.
- Tenant isolation prevents cross-tenant asset access.
```

- [ ] **Step 2: Run backend tests**

Run:

```powershell
cd backend
mvn test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run frontend build**

Run:

```powershell
cd frontend
npm run build
```

Expected: build success.

- [ ] **Step 4: Commit acceptance documentation**

Run:

```powershell
git add docs/qa/mvp-1-acceptance.md
git commit -m "docs: add MVP-1 acceptance checklist"
```

---

## Self-Review Checklist

Spec coverage:

- Metadata layers: covered.
- Asset type and dynamic fields: covered.
- JSONB asset attributes: covered.
- Runtime metadata: covered.
- Asset CRUD and relations: covered.
- Preset server and software license metadata: covered.

Deferred to later MVPs:

- Lifecycle actions.
- Approval.
- License assignment.
- Import/export.
- Reports and production hardening.

