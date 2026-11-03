# Add Cart Item Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add products safely to the authenticated user's persisted cart.

**Architecture:** Serialize cart writes on the user row, validate the requested product under a database lock, then create/increment the cart item in one transaction. Reuse existing cart response pricing without reserving inventory.

**Tech Stack:** Java, Spring Boot, JPA, PostgreSQL, Flyway, MockMvc/JUnit.

**Spec:** `docs/superpowers/specs/2026-11-03-add-cart-item-design.md` (approved by user).

## Global Constraints

- No inventory reservation, product version changes, frontend changes or checkout implementation.
- Preserve current API envelope, authenticated-role access and Java import conventions.
- Work in the current checkout as requested; leave changes uncommitted.

## Review Focus

- First-cart concurrent creation must produce one cart and one item.
- Cumulative quantity must not overflow or exceed stock.
- Failed adds must leave no partially-created cart or changed existing items.
- A blocked account or a supplied userId must not bypass ownership.
- Fractional JSON numbers must not silently truncate to product IDs or quantities.

### Task 1: Transactional add endpoint and validation

Files: new `order/dto/request/cart/AddCartItemRequest.java`, existing CartController/CartService/CartServiceImpl, UserRepository, CartItemRepository, ErrorCode; new `order/CartAddItemIT.java`.

Produces: `CartResponse addItem(UserPrincipal principal, AddCartItemRequest request)` and POST `/api/cart/items`.

- [x] Write API tests: new cart, cumulative adds, exact stock, invalid JSON/fields/fractions, unavailable product/category, sale totals, owner isolation, rollback, auth and unchanged inventory/version.
- [x] Run `test-compile failsafe:integration-test failsafe:verify -Dit.test=CartAddItemIT`; confirm endpoint tests fail before implementation.
- [x] Implement request validation and transaction: user lock/status check, product lock/availability check, overflow-safe cumulative quantity, persist and flush, existing response mapping.
- [x] Run targeted API and existing cart tests; expect zero failures.

### Task 2: Database invariant and concurrency

Files: new Flyway `V7__cart_item_positive_quantity.sql`, new `order/CartAddConcurrencyIT.java`.

Consumes Task 1 service; produces proven atomic increments and database quantity protection.

- [x] Write independent committed-transaction tests for first cart race, increments, stock-limit race, rollback and positive-quantity constraint.
- [x] Observe constraint test fail before adding CHECK; add CHECK without rewriting legacy data.
- [x] Run concurrent tests with bounded timeouts and cleanup only test-owned fixtures.

### Task 3: Regression, review and API documentation

Files: README and `docs/cart-add-verification.md`.

- [x] Run all backend unit/integration tests, inspect counts/failures.
- [x] Review ownership, lock ordering and interactions with catalog/auth; fix important findings with regression tests.
- [x] Document request/response, error status, retry behavior and checkout revalidation requirement.
- [x] Run `git diff --check`; leave changes uncommitted.

## Execution ledger

- User approved implementation and the no-reservation business rule. Native execution in current checkout; no additional approval gates or automatic commits.
- Pre-flight: Task 2 consumes Task 1 service; no conflicting signatures. Task 3 verifies both.
- Task 1: RED confirmed missing endpoint (25 assertion failures); GREEN: 31 new API cases and existing CartApiIT pass against dedicated test DB.
- Environment ruling: inherited TEST_DB_URL pointed to dev. Initial transactional tests rolled back; subsequent runs explicitly set ogani_store_test, created separately. Committed tests assert database suffix before creating fixtures.
- Task 2: concurrency scenarios passed; RED confirmed missing positive-quantity constraint and missing cart timestamp advancement (2 assertion failures). Added V7 CHECK and dirty timestamp update.
- Task 3 review: independent reviewer found no important implementation defects. Added deterministic PostgreSQL lock-wait observations, committed stock/status change tests, and stale-principal account blocking tests to close minor coverage gaps. Corrected transaction-cached pg_stat_activity snapshots in test polling with pg_stat_clear_snapshot().
- First full regression: 11 unit + 190 integration tests passed, before the final three additional review-driven cases.
- Final regression: BUILD SUCCESS, 11 unit + 193 integration tests = 204 passed, no failures/errors/skips. All tasks complete, git diff --check passed. No commits or push.
