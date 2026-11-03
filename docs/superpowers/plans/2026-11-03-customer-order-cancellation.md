# Customer Order Cancellation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Cancel an owned PENDING/UNPAID/COD order and restore stock exactly once.

**Spec:** docs/superpowers/specs/2026-11-03-customer-order-cancellation-design.md, approved by user.

**Architecture:** User -> owner-scoped order write lock -> ascending product write locks, scalar restock projections and one transaction. Order optimistic version protects future stale writers. Share response mapping with checkout.

**Tech Stack:** Spring Boot/JPA/PostgreSQL/Flyway/MockMvc.

## Review Focus

- Repeated/concurrent cancellation must not restore inventory or append history twice.
- Paid/non-PENDING orders require409; missing/foreign orders404; inactive/anonymous401.
- Deleted/hidden products, duplicate order lines and integer overflow must be handled without partial changes.
- Stock, order transition and history must roll back together.
- Checkout replay and snapshots must remain intact after cancellation.

- [x] Write API tests first and observe missing endpoint failures.
- [x] Add V9 Order version, scoped locking query/restock projection, shared mapper, cancellation service/controller/request.
- [x] Add committed isolated concurrency/rollback/stale-status tests.
- [x] Run full backend regression, independent review, documentation/checklist and diff check.
- [x] Provide commit description; leave code uncommitted.

## Ledger

- Current checkout is main and includes COD checkout, but not the previous GET order list files. Do not recreate unrelated branch work; cancellation uses a separate controller.
- User confirmed PENDING-only and approved the written design.
- RED:33 API cases,32 assertion failures against absent endpoint. GREEN:33 API +24 checkout tests passed.
- Added8 committed concurrency/rollback/replay tests. First full suite:11 unit +330 integration passed.
- Review found a history-note length boundary; RED regression reproduced it with200-char reason and10000 deleted lines. Shortened suffix without truncating reason/count; added exactmax/overflow/negative/type boundaries.
- Final full regression in progress; current API documentation/checklist updated. Commit message to be provided without committing.
- Final:347 tests passed (11 unit +336 integration), zero failures/errors/skips,47 new cancellation cases. Review finding fixed with RED→GREEN regression. Docs/checklist and diff check complete. No commit/push.
