# Update Cart Item Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Replace an owned cart item's quantity safely.

**Architecture:** Principal-owned lookup after the same user lock used by POST; product lock and current stock/availability validation; one transaction returns the existing cart response.

**Tech Stack:** Spring Boot, JPA, PostgreSQL, MockMvc, JUnit.

**Spec:** `docs/superpowers/specs/2026-11-03-update-cart-item-design.md`, approved by user.

## Global Constraints

- Positive quantities replace; user-approved amendment: PUT zero removes, DELETE removes separately, negatives remain errors.
- No inventory reservation, product version mutation, new migration or frontend change.
- Retain the current checkout/branch and leave changes uncommitted.

## Review Focus

- Another user's item must return 404 and remain unchanged.
- Over-stock carts must recover by reducing to an available quantity.
- Request JSON must reject fractional/coerced numbers and overflow.
- Same-quantity requests must still validate current product availability.
- Concurrent add/update must use the same user-first lock protocol.

### Task 1: Endpoint and business rules

Files: new UpdateCartItemRequest, CartUpdateItemIT; modify CartController, CartService, CartServiceImpl, CartItemRepository.

Interface: `CartResponse updateItem(UserPrincipal principal, Long itemId, UpdateCartItemRequest request)`; PUT `/api/cart/items/{itemId}` returns ApiResponse<CartResponse>.

- [x] Write failing API tests for replacement/reduction/no-op/exact stock, invalid bodies and IDs, missing/foreign item, disabled account, unavailable product/category, rollback, current prices and unchanged inventory/version.
- [x] Run targeted tests; confirm missing-endpoint failures.
- [x] Implement strict request, owner-scoped lookup, shared user/product validation helpers and transactional update.
- [x] Run API tests and existing cart regressions; expect no failures.

### Task 2: Cross-feature concurrency and regression

Files: extend CartAddConcurrencyIT using its committed isolated fixtures, update README and cart verification report.

- [x] Add tests for update/update and add/update with observed user lock waits, product stock/status changes while waiting, stale-principal blocking, and update timestamp.
- [x] Run full backend unit/integration suite explicitly on ogani_store_test.
- [x] Independent review and resolve important findings with regression tests.
- [x] Document API and last-successful-update semantics; run git diff --check.

## Execution ledger

- User approved design and implementation. Native execution; no further approval gate or auto-commit.
- Pre-flight: Task 2 consumes Task 1 endpoint and existing user-first lock protocol; no conflicting contracts.
- Task 1 RED: 30 new API cases, 27 missing-endpoint assertion failures. GREEN: 30 new API cases plus 41 existing cart integration cases passed.
- Task 1 implementation: strict UpdateCartItemRequest, PUT controller/service, owner-scoped query, shared user/product lock helpers. No schema change.
- Task 2: added seven deterministic concurrency/timestamp/security cases. Full backend suite and independent review in progress.
- Final: 241 tests passed (11 unit, 230 integration), zero failures/errors/skips. Reviewer found no actionable issues. All tasks complete, docs updated, diff check passed. No commits/pushes.

## User-requested removal extension

- [x] Write removal API tests first; RED: 17 failures across 19 cases.
- [x] Allow DTO quantity zero; route PUT zero and DELETE through shared owned-item deletion, before product validation.
- [x] Preserve positive-only POST add validation and database quantity CHECK.
- [x] Add seven lock/concurrency/catalog/account regression cases and remove obsolete zero-invalid PUT case.
- [x] Verify full backend suite, independent review and update current API documentation.

Extension completed: 266 tests passed (11 unit + 255 integration), no failures/errors/skips. Reviewer found no important issues. README/current API report updated; diff check passed. No migration/frontend changes or commits/pushes.
