# Customer order cancellation

## API

```http
POST /api/orders/{id}/cancel
Authorization: Bearer <access-token>
Content-Type: application/json
```

Optional body:

```json
{"reason":"Changed my mind"}
```

Use the order id returned by checkout, not orderCode or productId. No Idempotency-Key is needed: cancellation is naturally repeatable by order ID and current status. Reason is optional, a string of at most200 characters; missing/null/blank reason uses a default history note. A repeated cancellation never rewrites the original reason.

| Condition | Result |
| --- | --- |
| Own PENDING + COD + UNPAID order | 200, CANCELLED and restocked once |
| Own already-CANCELLED order | 200, unchanged; no new history/restock |
| Missing, foreign or ownerless order | 404 |
| Anonymous, invalid token or inactive/missing account | 401 |
| Invalid positive ID/body/reason | 400 |
| CONFIRMED/SHIPPING/COMPLETED or non-COD/paid/refunded order | 409 |
| Invalid legacy item quantities, negative inventory or restock overflow | 409, no changes |

Response is the existing ApiResponse<OrderResponse>, including id, orderCode, snapshots, totals and orderStatus=CANCELLED. Payment remains UNPAID for newly-cancelled COD orders. Admin/Staff using this customer endpoint may only cancel their own orders; admin cancellation of other buyers is a separate future feature.

## Business rules

- Restore existing purchased products' quantities; aggregate duplicate product IDs before applying stock changes. Exact Integer.MAX_VALUE inventory is valid; any overflow/invalid quantity fails the whole operation.
- HIDDEN products remain hidden. OUT_OF_STOCK becomes ACTIVE only if its inventory was0 and is restored to a positive value. Explicit OUT_OF_STOCK with already-positive inventory is preserved. No category is activated.
- Deleted products are not recreated; nullable product references are skipped and their count recorded in history. Invalid item quantities still reject cancellation. A newly-cancelled order must contain item rows.
- Preserve names/prices/address/currency/amount snapshots, order items, payment state, sold_count, checkout key/hash and cart contents. No refund, coupon-counter change or automatic re-add to cart occurs.
- Add one PENDING→CANCELLED event with authenticated actor, time and reason. Successful order and inventory updates advance optimistic versions; retries leave timestamp/version/history unchanged.
- Checkout retry with the original key returns the original now-CANCELLED order and leaves any newly populated cart intact. To purchase again, start a new checkout with a new key.

## Concurrency and schema

One transaction locks user -> owner-scoped order -> products in ascending ID order. Scalar restock projections avoid loading stale product entities before locking. All inventory bounds are checked before applying mutations. Status, history and stock commit/rollback together. Concurrent repeated cancellations produce one history event and one inventory restoration.

Flyway V9 adds nonnull orders.version with default0; existing orders are preserved. Order @Version prevents stale versioned ORM status updates from overwriting cancellation. Future admin writers must use compatible locks/version checks and validate state; unconditional external SQL writes bypass ORM protection.

Restart backend to apply V9. No frontend or browser behavior changed.

## Checklist

- [x] Existing order and ownership checks; unknown/foreign IDs indistinguishable.
- [x] PENDING-only COD/UNPAID policy and forbidden-state responses.
- [x] Atomic cancellation, restock and history.
- [x] Already-cancelled retries without repeated effects.
- [x] Anonymous/Customer/Admin access plus blocked stale principal.
- [x] Hidden/deleted products, duplicate lines, missing items and quantity boundaries.
- [x] Real concurrent cancellation, inverse item order and competing checkout.
- [x] Committed status changes rechecked after waiting; stale ORM writers rejected.
- [x] Injected late DB failure rolls back history/inventory/status and permits retry.
- [x] Checkout replay after cancellation preserves new cart contents.
- [x] Full checkout/cart/catalog regression and independent review.

## Verification record

RED:33 API cases,32 failures because endpoint was absent. GREEN: all33 API cases and24 existing checkout cases passed. Added8 committed concurrency/rollback/replay cases on explicit ogani_store_test; test fixtures and the unique error-injection trigger/function are removed after use.

Independent review found a note-length boundary: full200-character reason plus the old deleted-line suffix exceeded VARCHAR(255) at10000 deleted lines. An integration regression reproduced the409 database failure before the suffix was shortened. The complete reason and count now fit, including large counts. Added exact stock maximum, aggregate overflow, negative inventory and object/array reason cases.

Final run on2026-11-03: **11 unit +336 integration =347 tests passed**, zero failures/errors/skips, BUILD SUCCESS. New cancellation coverage:39 API +8 committed concurrency cases. `git diff --check` passed. The injected500 response in the late-failure test is intentional and asserts rollback; it is not a test failure. No frontend/browser or deployment validation is claimed.

Current checkout is main and does not include the previous order-list branch files. This change adds a separate cancellation controller and does not recreate/delete unrelated list work. Changes remain uncommitted.
