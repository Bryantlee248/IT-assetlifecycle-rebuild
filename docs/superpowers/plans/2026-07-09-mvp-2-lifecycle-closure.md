# MVP-2 Lifecycle Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement lifecycle state machines and action APIs for data center assets and software licenses.

**Architecture:** This phase adds the `lifecycle` module and introduces lifecycle templates, states, transitions, guard rules, lifecycle events, and action execution. Asset state changes are only allowed through lifecycle action endpoints.

**Tech Stack:** Spring Boot 3, MyBatis Plus, PostgreSQL, Flyway, Vue 3, TypeScript, Element Plus.

---

## Scope Check

This phase assumes MVP-1 exists: asset types, assets, dynamic attributes, runtime metadata, frontend asset pages.

This phase builds:

- Lifecycle templates.
- Lifecycle states.
- Lifecycle transitions.
- Guard rule evaluation.
- Lifecycle event logs.
- Action APIs.
- Frontend lifecycle action panel and event timeline.

This phase does not build human approval. Actions marked as approval-required return `approval_required` but approval execution is implemented in MVP-3.

## File Structure

```text
backend/src/main/java/com/company/itam/lifecycle/
  controller/
  application/
  domain/
  repository/
  dto/
backend/src/main/resources/db/migration/
  V004__lifecycle.sql
  V005__seed_mvp_lifecycle_templates.sql
backend/src/test/java/com/company/itam/lifecycle/
frontend/src/modules/lifecycle/
frontend/src/api/lifecycle.ts
frontend/src/types/lifecycle.ts
```

---

## Task 1: Lifecycle Database Schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V004__lifecycle.sql`
- Test: `backend/src/test/java/com/company/itam/lifecycle/LifecycleSchemaMigrationTest.java`

- [ ] **Step 1: Write migration test**

Verify these tables exist:

```text
lifecycle_templates
lifecycle_states
lifecycle_transitions
lifecycle_events
```

Expected before migration: missing tables.

- [ ] **Step 2: Add migration**

Create:

```sql
CREATE TABLE lifecycle_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    asset_kind VARCHAR(32) NOT NULL,
    asset_type_id UUID REFERENCES asset_types(id),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, template_code)
);

CREATE TABLE lifecycle_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    template_id UUID NOT NULL REFERENCES lifecycle_templates(id),
    state_code VARCHAR(64) NOT NULL,
    state_name VARCHAR(128) NOT NULL,
    state_category VARCHAR(64) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_initial BOOLEAN NOT NULL DEFAULT FALSE,
    is_terminal BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, template_id, state_code)
);

CREATE TABLE lifecycle_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    template_id UUID NOT NULL REFERENCES lifecycle_templates(id),
    from_state VARCHAR(64) NOT NULL,
    to_state VARCHAR(64) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    action_name VARCHAR(128) NOT NULL,
    require_approval BOOLEAN NOT NULL DEFAULT FALSE,
    require_attachment BOOLEAN NOT NULL DEFAULT FALSE,
    guard_rule JSONB NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE (tenant_id, template_id, from_state, action_code)
);

CREATE TABLE lifecycle_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    asset_id UUID NOT NULL REFERENCES assets(id),
    from_state VARCHAR(64) NOT NULL,
    to_state VARCHAR(64) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    operator_id UUID NOT NULL,
    reason TEXT,
    related_process_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_lifecycle_events_asset_created ON lifecycle_events(asset_id, created_at DESC);
```

- [ ] **Step 3: Run migration test**

Run:

```powershell
cd backend
mvn -Dtest=LifecycleSchemaMigrationTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit schema**

Run:

```powershell
git add backend/src/main/resources/db/migration/V004__lifecycle.sql backend/src/test/java/com/company/itam/lifecycle
git commit -m "feat: add lifecycle schema"
```

---

## Task 2: Seed MVP Lifecycle Templates

**Files:**
- Create: `backend/src/main/resources/db/migration/V005__seed_mvp_lifecycle_templates.sql`
- Test: `backend/src/test/java/com/company/itam/lifecycle/LifecycleTemplateSeedTest.java`

- [ ] **Step 1: Write seed test**

Verify these templates and states exist:

```text
data_center_asset_lifecycle:
planned, purchasing, inbound, deployed, running, maintenance, retired, disposed

software_license_lifecycle:
planned, purchasing, available, assigned, renewal_pending, expired, reclaimed, retired
```

- [ ] **Step 2: Add seed migration**

Seed transitions:

```text
planned -> purchasing      submit_purchase
purchasing -> inbound      confirm_inbound
inbound -> deployed        deploy
deployed -> running        start_operation
running -> maintenance     report_fault
maintenance -> running     finish_repair
running -> retired         retire
retired -> disposed        dispose
```

Software license transitions:

```text
planned -> purchasing          submit_purchase
purchasing -> available        register_license
available -> assigned          assign_license
assigned -> reclaimed          reclaim_license
assigned -> renewal_pending    mark_renewal
renewal_pending -> assigned    renew_license
renewal_pending -> expired     expire
expired -> assigned            renew_after_expired
available -> retired           retire
reclaimed -> available         release_to_pool
```

- [ ] **Step 3: Run seed test**

Run:

```powershell
cd backend
mvn -Dtest=LifecycleTemplateSeedTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit seed**

Run:

```powershell
git add backend/src/main/resources/db/migration/V005__seed_mvp_lifecycle_templates.sql backend/src/test/java/com/company/itam/lifecycle/LifecycleTemplateSeedTest.java
git commit -m "feat: seed MVP lifecycle templates"
```

---

## Task 3: Lifecycle Action Backend

**Files:**
- Create: `backend/src/main/java/com/company/itam/lifecycle/controller/LifecycleController.java`
- Create: `backend/src/main/java/com/company/itam/lifecycle/application/LifecycleApplicationService.java`
- Create: `backend/src/main/java/com/company/itam/lifecycle/domain/LifecycleGuardEvaluator.java`
- Test: `backend/src/test/java/com/company/itam/lifecycle/LifecycleActionApiTest.java`

- [ ] **Step 1: Write lifecycle action tests**

Tests:

```text
GET /api/v1/assets/{assetId}/lifecycle/actions returns valid actions for current state.
POST /api/v1/assets/{assetId}/lifecycle/actions/confirm_inbound moves purchasing to inbound.
Direct asset lifecycle_status update is not exposed.
Guard rule blocks deploy without required location and responsible user.
Lifecycle event is written after successful action.
Action requiring approval returns approval_required and keeps asset state unchanged.
```

- [ ] **Step 2: Implement guard evaluator**

Guard JSON format:

```json
{
  "requireFields": ["location_id", "responsible_user_id"],
  "requireAttributeFields": ["sn"],
  "requireAttachment": false
}
```

Evaluation result:

```java
public record GuardResult(boolean passed, List<String> errors) {}
```

- [ ] **Step 3: Implement lifecycle action endpoints**

Endpoints:

```text
GET  /api/v1/assets/{assetId}/lifecycle
GET  /api/v1/assets/{assetId}/lifecycle/events
GET  /api/v1/assets/{assetId}/lifecycle/actions
POST /api/v1/assets/{assetId}/lifecycle/actions/{actionCode}
```

Action request:

```json
{
  "reason": "confirmed inbound",
  "formData": {},
  "attachmentIds": []
}
```

Response when transitioned:

```json
{
  "result": "transitioned",
  "fromState": "purchasing",
  "toState": "inbound"
}
```

Response when approval is required:

```json
{
  "result": "approval_required",
  "approvalInstanceId": null
}
```

- [ ] **Step 4: Run lifecycle tests**

Run:

```powershell
cd backend
mvn -Dtest=LifecycleActionApiTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit backend lifecycle actions**

Run:

```powershell
git add backend/src/main/java/com/company/itam/lifecycle backend/src/test/java/com/company/itam/lifecycle
git commit -m "feat: add lifecycle action APIs"
```

---

## Task 4: Frontend Lifecycle Panel

**Files:**
- Create: `frontend/src/api/lifecycle.ts`
- Create: `frontend/src/types/lifecycle.ts`
- Create: `frontend/src/modules/lifecycle/LifecycleActionPanel.vue`
- Create: `frontend/src/modules/lifecycle/LifecycleTimeline.vue`
- Modify: `frontend/src/modules/asset/AssetListPage.vue`

- [ ] **Step 1: Add lifecycle API client**

Methods:

```ts
getLifecycle(assetId: string)
getLifecycleEvents(assetId: string)
getLifecycleActions(assetId: string)
executeLifecycleAction(assetId: string, actionCode: string, payload: LifecycleActionRequest)
```

- [ ] **Step 2: Add action panel**

UI behavior:

- Load available actions for selected asset.
- Render action buttons.
- Open reason dialog before execution.
- Show `approval_required` message if approval is needed.
- Refresh asset detail after successful transition.

- [ ] **Step 3: Add lifecycle timeline**

Render:

```text
action name
from state
to state
operator
created time
reason
```

- [ ] **Step 4: Run frontend build**

Run:

```powershell
cd frontend
npm run build
```

Expected: build success.

- [ ] **Step 5: Commit frontend lifecycle UI**

Run:

```powershell
git add frontend/src
git commit -m "feat: add lifecycle action frontend"
```

---

## Task 5: MVP-2 Acceptance

**Files:**
- Create: `docs/qa/mvp-2-acceptance.md`

- [ ] **Step 1: Create acceptance checklist**

Content:

```markdown
# MVP-2 Acceptance

- Server asset can move from planned to purchasing.
- Server asset can move from purchasing to inbound.
- Server asset cannot deploy without required location and responsible user.
- Server asset can move through deployed, running, maintenance, retired, disposed.
- Software license can move through planned, purchasing, available, assigned, reclaimed, renewal_pending, expired, retired.
- State cannot be directly modified through asset update API.
- Lifecycle event is written for each transition.
- Approval-required transition does not change state before approval module exists.
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

- [ ] **Step 4: Commit acceptance**

Run:

```powershell
git add docs/qa/mvp-2-acceptance.md
git commit -m "docs: add MVP-2 acceptance checklist"
```

---

## Self-Review Checklist

Spec coverage:

- Lifecycle templates: covered.
- State transitions: covered.
- Guard rules: covered.
- Action-only state changes: covered.
- Lifecycle event log: covered.
- Frontend action panel and timeline: covered.

Deferred to MVP-3:

- Approval instance creation.
- Approval tasks.
- Notification.
- Data scope and field permission completion.

