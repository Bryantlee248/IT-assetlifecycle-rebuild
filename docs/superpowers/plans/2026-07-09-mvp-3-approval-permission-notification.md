# MVP-3 Approval, Permission, and Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement lightweight approval, data scope permissions, field permissions, state permissions, and in-app notifications.

**Architecture:** This phase connects lifecycle actions with approval instances and adds permission enforcement beyond basic RBAC. The backend remains authoritative for data filtering, field visibility, field editability, and approval task ownership.

**Tech Stack:** Spring Boot 3, MyBatis Plus, PostgreSQL JSONB, Redis cache, Vue 3, TypeScript, Element Plus.

---

## Scope Check

This phase assumes MVP-2 exists: assets, metadata, lifecycle states, lifecycle actions, and lifecycle events.

This phase builds:

- Approval templates, nodes, instances, tasks.
- Purchase, retirement, license assignment, license renewal approval hooks.
- Data scope rules.
- Field permission rules.
- State permission rules.
- Permission filtering for list/detail/export-ready data.
- In-app notifications.

This phase does not build full BPMN, countersign, transfer, full automation rules, email delivery, or webhook delivery.

## File Structure

```text
backend/src/main/java/com/company/itam/approval/
backend/src/main/java/com/company/itam/permission/
backend/src/main/java/com/company/itam/notification/
backend/src/main/resources/db/migration/
  V006__approval_permission_notification.sql
backend/src/test/java/com/company/itam/approval/
backend/src/test/java/com/company/itam/permission/
backend/src/test/java/com/company/itam/notification/
frontend/src/modules/approval/
frontend/src/modules/permission/
frontend/src/modules/notification/
frontend/src/api/approval.ts
frontend/src/api/permission.ts
frontend/src/api/notification.ts
```

---

## Task 1: Approval and Permission Schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V006__approval_permission_notification.sql`
- Test: `backend/src/test/java/com/company/itam/approval/ApprovalPermissionSchemaTest.java`

- [ ] **Step 1: Write migration test**

Verify tables:

```text
approval_templates
approval_nodes
approval_instances
approval_tasks
data_scope_rules
field_permission_rules
state_permission_rules
notifications
```

- [ ] **Step 2: Add migration**

Create:

```sql
CREATE TABLE approval_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, template_code)
);

CREATE TABLE approval_nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    template_id UUID NOT NULL REFERENCES approval_templates(id),
    node_order INTEGER NOT NULL,
    node_name VARCHAR(128) NOT NULL,
    approver_type VARCHAR(32) NOT NULL,
    approver_value VARCHAR(128),
    condition_rule JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE approval_instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    business_type VARCHAR(64) NOT NULL,
    business_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    applicant_id UUID NOT NULL,
    current_node_id UUID,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE approval_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    instance_id UUID NOT NULL REFERENCES approval_instances(id),
    node_id UUID NOT NULL REFERENCES approval_nodes(id),
    approver_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    comment TEXT,
    acted_at TIMESTAMPTZ
);

CREATE TABLE data_scope_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    resource_type VARCHAR(64) NOT NULL,
    scope_type VARCHAR(64) NOT NULL,
    scope_value JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE field_permission_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    asset_type_id UUID NOT NULL REFERENCES asset_types(id),
    field_code VARCHAR(64) NOT NULL,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    editable BOOLEAN NOT NULL DEFAULT TRUE,
    condition_rule JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE state_permission_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    asset_type_id UUID NOT NULL REFERENCES asset_types(id),
    lifecycle_state VARCHAR(64) NOT NULL,
    allowed_actions JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    receiver_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    content TEXT,
    notification_type VARCHAR(64) NOT NULL,
    business_type VARCHAR(64),
    business_id UUID,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_approval_tasks_approver_status ON approval_tasks(tenant_id, approver_id, status);
CREATE INDEX idx_notifications_receiver_created ON notifications(tenant_id, receiver_id, created_at DESC);
```

- [ ] **Step 3: Run migration test**

Run:

```powershell
cd backend
mvn -Dtest=ApprovalPermissionSchemaTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit schema**

Run:

```powershell
git add backend/src/main/resources/db/migration/V006__approval_permission_notification.sql backend/src/test/java/com/company/itam/approval
git commit -m "feat: add approval permission notification schema"
```

---

## Task 2: Permission Enforcement

**Files:**
- Create: `backend/src/main/java/com/company/itam/permission/domain/DataScopeResolver.java`
- Create: `backend/src/main/java/com/company/itam/permission/domain/FieldPermissionResolver.java`
- Create: `backend/src/main/java/com/company/itam/permission/domain/StatePermissionResolver.java`
- Test: `backend/src/test/java/com/company/itam/permission/PermissionResolverTest.java`

- [ ] **Step 1: Write permission resolver tests**

Tests:

```text
self scope returns only owner_user_id current user.
own_org_tree returns current org and descendants.
specified_asset_type returns configured asset type.
field rule hides sensitive field.
field rule makes retired asset field read-only.
state rule blocks retire action for asset_user.
```

- [ ] **Step 2: Implement data scope resolver**

Supported scopes:

```text
self
own_org
own_org_tree
specified_org
specified_location
specified_asset_type
responsible
all_tenant
```

Resolver output:

```java
public record DataScopeFilter(String sqlFragment, Map<String, Object> parameters) {}
```

- [ ] **Step 3: Implement field permission resolver**

Return:

```java
public record FieldAccess(String fieldCode, boolean visible, boolean editable) {}
```

Apply to:

- Asset detail response.
- Asset list response.
- Runtime metadata response.
- Asset update validation.

- [ ] **Step 4: Implement state permission resolver**

Return allowed actions for current asset:

```java
Set<String> allowedActions(UUID userId, UUID assetTypeId, String lifecycleState)
```

- [ ] **Step 5: Run permission tests**

Run:

```powershell
cd backend
mvn -Dtest=PermissionResolverTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit permission enforcement**

Run:

```powershell
git add backend/src/main/java/com/company/itam/permission backend/src/test/java/com/company/itam/permission
git commit -m "feat: add permission resolvers"
```

---

## Task 3: Approval Backend Flow

**Files:**
- Create: `backend/src/main/java/com/company/itam/approval/controller/ApprovalController.java`
- Create: `backend/src/main/java/com/company/itam/approval/application/ApprovalApplicationService.java`
- Create: `backend/src/main/java/com/company/itam/approval/domain/ApproverResolver.java`
- Test: `backend/src/test/java/com/company/itam/approval/ApprovalFlowTest.java`

- [ ] **Step 1: Write approval flow tests**

Tests:

```text
lifecycle action requiring approval creates approval instance and task.
my pending tasks returns only current approver tasks.
approve final task triggers lifecycle transition.
reject keeps asset state unchanged.
non-approver cannot approve task.
```

- [ ] **Step 2: Implement approver resolver**

Supported approver types:

```text
role
user
org_manager
asset_owner
```

- [ ] **Step 3: Implement approval endpoints**

Endpoints:

```text
GET  /api/v1/approvals/tasks/my
GET  /api/v1/approvals/instances
GET  /api/v1/approvals/instances/{id}
POST /api/v1/approvals/instances/{id}/approve
POST /api/v1/approvals/instances/{id}/reject
POST /api/v1/approvals/instances/{id}/cancel
GET  /api/v1/approvals/templates
POST /api/v1/approvals/templates
PUT  /api/v1/approvals/templates/{templateId}
```

- [ ] **Step 4: Connect lifecycle to approval**

When transition requires approval:

```text
create approval_instance
create first approval_task
create notification for approver
return approval_required with approvalInstanceId
```

When approval completes:

```text
execute stored lifecycle action
write lifecycle_event
write audit_log
notify applicant
```

- [ ] **Step 5: Run approval tests**

Run:

```powershell
cd backend
mvn -Dtest=ApprovalFlowTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit approval flow**

Run:

```powershell
git add backend/src/main/java/com/company/itam/approval backend/src/test/java/com/company/itam/approval
git commit -m "feat: add lightweight approval flow"
```

---

## Task 4: Notification Backend and Frontend

**Files:**
- Create: `backend/src/main/java/com/company/itam/notification/controller/NotificationController.java`
- Create: `backend/src/main/java/com/company/itam/notification/application/NotificationApplicationService.java`
- Create: `frontend/src/modules/notification/NotificationListPage.vue`
- Test: `backend/src/test/java/com/company/itam/notification/NotificationApiTest.java`

- [ ] **Step 1: Write notification API tests**

Tests:

```text
GET /api/v1/notifications returns current user's notifications.
PATCH /api/v1/notifications/{id}/read marks one notification read.
PATCH /api/v1/notifications/read-all marks all current user's notifications read.
Approval task creation creates pending approval notification.
```

- [ ] **Step 2: Implement notification endpoints**

Endpoints:

```text
GET   /api/v1/notifications
PATCH /api/v1/notifications/{id}/read
PATCH /api/v1/notifications/read-all
GET   /api/v1/notifications/unread-count
```

- [ ] **Step 3: Implement frontend notification page**

Features:

- Notification list.
- Unread badge.
- Mark one as read.
- Mark all as read.

- [ ] **Step 4: Run checks**

Run:

```powershell
cd backend
mvn -Dtest=NotificationApiTest test
cd ../frontend
npm run build
```

Expected: backend and frontend checks pass.

- [ ] **Step 5: Commit notifications**

Run:

```powershell
git add backend/src/main/java/com/company/itam/notification backend/src/test/java/com/company/itam/notification frontend/src
git commit -m "feat: add in-app notifications"
```

---

## Task 5: Frontend Approval and Permission Pages

**Files:**
- Create: `frontend/src/modules/approval/ApprovalTaskPage.vue`
- Create: `frontend/src/modules/approval/ApprovalDetailPage.vue`
- Create: `frontend/src/modules/permission/RolePermissionPage.vue`
- Create: `frontend/src/api/approval.ts`
- Create: `frontend/src/api/permission.ts`

- [ ] **Step 1: Add approval API client**

Methods:

```ts
getMyApprovalTasks()
getApprovalInstance(id: string)
approveApprovalInstance(id: string, comment: string)
rejectApprovalInstance(id: string, comment: string)
```

- [ ] **Step 2: Add permission API client**

Methods:

```ts
getRoles()
getRolePermissions(roleId: string)
saveRolePermissions(roleId: string, permissionCodes: string[])
getDataScopeRules(roleId: string)
saveDataScopeRules(roleId: string, rules: DataScopeRule[])
getFieldRules(roleId: string)
saveFieldRules(roleId: string, rules: FieldRule[])
getStateRules(roleId: string)
saveStateRules(roleId: string, rules: StateRule[])
```

- [ ] **Step 3: Build approval task page**

Features:

- My pending tasks.
- Approval detail drawer.
- Approve action.
- Reject action with required comment.

- [ ] **Step 4: Build role permission page**

Features:

- Role list.
- Functional permissions checkbox tree.
- Data scope rule table.
- Field permission rule table.
- State permission rule table.

- [ ] **Step 5: Run frontend build**

Run:

```powershell
cd frontend
npm run build
```

Expected: build success.

- [ ] **Step 6: Commit frontend approval and permission**

Run:

```powershell
git add frontend/src
git commit -m "feat: add approval and permission frontend"
```

---

## Task 6: MVP-3 Acceptance

**Files:**
- Create: `docs/qa/mvp-3-acceptance.md`

- [ ] **Step 1: Create acceptance checklist**

Content:

```markdown
# MVP-3 Acceptance

- Purchase lifecycle action creates approval task.
- Retirement lifecycle action creates approval task.
- Software license assignment action can require approval.
- Approval passes trigger lifecycle transition.
- Approval rejection keeps asset state unchanged.
- Non-approver cannot approve task.
- Ordinary user sees only own assets.
- Department manager sees department tree assets.
- Sensitive fields are hidden from unauthorized users.
- Retired state blocks edit actions by state permission.
- Pending approval notifications are created and can be read.
```

- [ ] **Step 2: Run backend tests and frontend build**

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
git add docs/qa/mvp-3-acceptance.md
git commit -m "docs: add MVP-3 acceptance checklist"
```

---

## Self-Review Checklist

Spec coverage:

- Lightweight approval: covered.
- Approval-task ownership: covered.
- Data scope permissions: covered.
- Field permissions: covered.
- State permissions: covered.
- In-app notifications: covered.

Deferred to MVP-4 and MVP-5:

- Expiration scheduled reminders.
- Export field permission enforcement.
- Webhook and email delivery.

