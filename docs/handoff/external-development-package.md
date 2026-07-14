# UI Handoff Requirement

External developers must read and follow `docs/mvp/16-UI设计规范与前端交互范式.md`. The frontend must use an enterprise B2B SaaS admin style, not a marketing page or decorative low-density interface.

# External Development Package

## Purpose

This package is the handoff material for implementing the redesigned IT asset management platform on another development platform.

The authoritative development entry is `docs/mvp/README.md`. The `docs/mvp` set has been updated after architectural and QA review; use it before older process notes under `docs/superpowers`.

## Document Set

Read in this order:

1. `docs/mvp/README.md`
2. `docs/mvp/01-产品需求说明书_PRD.md`
3. `docs/mvp/02-MVP功能清单与用户故事.md`
4. `docs/mvp/03-系统模块设计说明.md`
5. `docs/mvp/04-页面清单与交互说明.md`
6. `docs/mvp/05-数据模型设计说明.md`
7. `docs/mvp/06-数据库表结构设计.md`
8. `docs/mvp/07-API接口规格说明.md`
9. `docs/mvp/08-模板体系设计.md`
10. `docs/mvp/09-报告生成规则说明.md`
11. `docs/mvp/10-权限与安全设计.md`
12. `docs/mvp/11-状态流转设计.md`
13. `docs/mvp/12-测试用例与验收标准.md`
14. `docs/mvp/13-种子数据与演示数据.md`
15. `docs/mvp/14-二期三期扩展预留设计.md`
16. `docs/mvp/15-开发总提示词.md`
17. `docs/mvp/16-UI设计规范与前端交互范式.md`

## Confirmed Delivery Strategy

The system must be delivered in stages:

```text
MVP-0 Platform Foundation
MVP-1 Metadata and Asset Core
MVP-2 Lifecycle State Machine and Actions (no approval)
MVP-3 Approval, Permission Enhancement, Notification
MVP-4 Import, Export, Reports, Lightweight Assets
MVP-5 Production Hardening and Integrations
```

The external development platform should not skip stages. Each MVP must be independently verifiable before the next stage begins.

## Review-Fixed Non-Negotiable Rules

- `data_scope_rules`, `field_permission_rules`, and `state_permission_rules` must be implemented with the fields defined in `06`.
- Lifecycle actions that require approval create approval instances from MVP-3 onward; reject/cancel keeps asset state unchanged; resubmit creates a new approval instance.
- All APIs must follow the response envelope, pagination, auth header, filters, and error codes in `07`.
- Dynamic fields default to JSONB, but hot/unique fields must use physical columns or database indexes.
- Soft-delete unique constraints must use partial unique indexes.
- `assets` and key write tables must include `created_by` and `updated_by`.
- Platform users have no tenant context. API Tokens are tenant-bound and scope-bound.
- Role-permission defaults in `10` are the baseline for seed data and tests.
- MVP-2 does not build approval. MVP-3 overlays approval on lifecycle actions.
- Acceptance must use executable cases from `12`, including security, concurrency, consistency, and performance tests.

## Recommended External Platform Workflow

For each MVP:

1. Read the full `docs/mvp` set.
2. Implement only the current MVP scope.
3. Write failing tests first from `12-测试用例与验收标准.md`.
4. Implement minimal code to pass.
5. Run backend tests, frontend build, database migration, and API contract checks.
6. Produce the MVP acceptance evidence.
7. Checkpoint the completed MVP before starting the next one.

## Current Status

Completed materials:

- MVP development document set under `docs/mvp`.
- UI and frontend interaction specification.
- External development handoff index.
- Review remediation for P0 blocking issues.

No application code has been implemented in this workspace.
