# COD Checkout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Preview and atomically place COD orders from the user's whole cart with saved/new delivery address selection.

**Architecture:** User-first locking shared with cart, products locked in ID order without eager preloading, server-side pricing/settings, immutable order snapshots and per-user request-key deduplication.

**Tech Stack:** Java/Spring Boot/JPA/PostgreSQL/Flyway/MockMvc.

**Spec:** docs/superpowers/specs/2026-11-03-cod-checkout-design.md (approved).

## Global Constraints

- COD only, no payment collection, coupons rejected explicitly.
- Owner-only address selection/order reads; direct delivery snapshot does not save a customer address.
- Preserve cart record; remove purchased item rows only on transaction success.
- Use ogani_store_test explicitly for tests; keep current checkout and leave changes uncommitted.

## Review Focus

- Retry after successful placement must not consume new cart additions.
- Cart item reads must not prefetch stale product entities before their locks.
- Multiple product locks must be ordered for competing buyers.
- Invalid shipping settings/DECIMAL overflow must fail before any stock change.
- Foreign address/order IDs must not expose another user's data.

### Task 1: Test-first public checkout contract

- [x] Add CheckoutIT tests for preview, COD placement, snapshots, saved/direct address, stock/version/cart/history, retry, invalid input, shipping thresholds, failures and ownership.
- [x] Run targeted test; verify missing-endpoint failures.
- [x] Add V8 checkout order metadata, repositories, request/response DTOs, checkout pricing/snapshot logic and controller/service.
- [x] Run targeted API and cart regression tests.

Interfaces: POST /api/checkout/preview, POST /api/checkout (Idempotency-Key UUID), GET /api/checkout/addresses, GET /api/orders/{id}. Placement request has cartToken, firstName, lastName, email, paymentMethod COD, addressId OR deliveryAddress, optional orderNotes/couponCode. Return existing ApiResponse.

### Task 2: Concurrency and full regression

- [x] Add committed isolated tests for same/different checkout keys, competing stock, inverse item ordering, concurrent cart edits, catalog edits and replay with new cart contents.
- [x] Run full backend suite; independent review; fix important findings with regression cases.
- [x] Document request examples, status/retry behavior and test evidence; git diff --check.

## Execution ledger

- User approved implementation and expanded saved/new address selection. Native execution, no automatic commit.
- Pre-flight: Task 2 consumes Task 1 APIs and user -> ascending product lock ordering; no conflicting interfaces.
- Task 1 RED: 19 missing-endpoint cases. GREEN: all19 API cases passed. Added address list/selection, direct shipping snapshots, V8 order metadata, pricing/fingerprint and transactional service.
- Task 2: added nine committed concurrency cases; first full run 11 unit +283 integration passed.
- Review RED: three API assertion failures and one real V7→V8 migration failure. Fixed recipient width121 and strict addressId/payment JSON parsing. Only isolated test DB aligned to revised unreleased V8. Follow-up review: no further important issue.
- Added explicit zero-price and multi-item all-or-nothing regression cases, strengthened historical product-name snapshot assertion. Final full suite in progress.
- Final: BUILD SUCCESS, 11 unit +289 integration =300 passed, zero failures/errors/skips.34 new checkout tests, follow-up review clear, docs and diff check complete. No commit/push or frontend change.
