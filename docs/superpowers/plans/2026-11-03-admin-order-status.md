# Admin Order Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PATCH admin order status with explicit transitions, actor/history/version tracking and exactly-once inventory effects.

**Spec:** docs/superpowers/specs/2026-11-03-admin-order-status-design.md; user approved implementation and chose PAID-only completion.

**Architecture:** Shared active admin guard; scalar owner discovery -> buyer lock -> order lock -> sorted product locks. Reuse/extract customer restock effects. No automatic payment-state changes or new migration.

**Tech Stack:** Spring Boot/JPA/PostgreSQL/MockMvc.

## Constraints and review focus

- PENDING→CONFIRMED/CANCELLED; CONFIRMED→SHIPPING/CANCELLED; SHIPPING→COMPLETED; no outgoing terminal transitions.
- Same target returns200 unchanged even with a prior version. Different target needs current nonnegative Long version.
- COD UNPAID/PAID may confirm/ship; online requiresPAID; refunded cannot move forward. Cancellation acceptsUNPAID/REFUNDED, neverPAID; completion requiresPAID.
- Cancellation restores stock once; completion increments sold_count once. Do not deduct inventory twice, change payment/snapshots/cart/checkout keys or recreate deleted products.
- No actor write lock before buyer lock; reject changed ownership rather than acquiring a new buyer lock after order locking.
- History/note/counter failures roll back the whole transition.

- [x] Write full transition-matrix/API/security/payment/input/counter tests; observe missing-route failures.
- [x] Implement request/response, enum transition policy, shared guard/inventory effects, scoped locking and service/controller.
- [x] Add committed staff/customer cancellation races, shared-product counter races and rollback tests.
- [x] Run full regression and independent review; fix important findings test-first.
- [x] Update checklist/docs/Swagger and prepare commit message; no commit/push.

## Ledger

- User chose payment option1 explicitly. Until the separate payment API exists, unpaid COD shipping orders correctly return409 on completion.
- Work in current checkout. Preserve customer cancellation policy and existing read APIs.
- RED:58 API cases,57 absent-route failures; GREEN:58 new API +117 related regression cases passed.
- Added10 committed concurrency cases; first full suite11 unit +507 integration passed.
- Review no blocking findings; minor compatibility concern reproduced via old customer overflow-message assertion (RED), then restored original cancellation ErrorCode/messages in shared effects.
- Final full suite: 11 unit +507 integration =518 passed, zero failures/errors/skips. Diff check passed and test fixture/trigger cleanup checks returned zero. Documentation/checklist and commit description prepared; no migration, commit or push.
