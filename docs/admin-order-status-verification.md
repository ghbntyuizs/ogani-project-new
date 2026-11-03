# Admin/Staff order status transitions

## API and usage

```http
PATCH /api/admin/orders/{id}/status
Authorization: Bearer <access-token>
Content-Type: application/json
```

```json
{"status":"CONFIRMED","version":0,"note":"Order checked"}
```

Get id and current `data.version` from GET `/api/admin/orders/{id}`. After a successful PATCH, use the returned version for the next change. Do not reuse an old version for a different target; reload detail after409.

Response uses ApiResponse with a compact summary:

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 123,
    "orderCode": "COD-1",
    "orderStatus": "CONFIRMED",
    "paymentStatus": "UNPAID",
    "version": 1,
    "updatedAt": "2026-11-03T12:00:00"
  }
}
```

## State machine

| Current | Allowed new states |
| --- | --- |
| PENDING | CONFIRMED, CANCELLED |
| CONFIRMED | SHIPPING, CANCELLED |
| SHIPPING | COMPLETED |
| COMPLETED | none |
| CANCELLED | none |

No backwards/skipped/reopened transitions. Same current target returns200 unchanged, even for a prior version: no history, timestamps, payment mutations or repeated inventory/counter effects. A different target requires an exact version match and valid transition. Same-target behavior does not repair inconsistent legacy data or revise the original note/actor.

Request status must be an exact enum-name string. Version is a required nonnegative Long JSON integer; strings, fractions, booleans, overflow/missing values are invalid. Optional note is a string of at most200 characters; blank uses a default. Actor is always taken from authenticated DB user, never client fields.

| Condition | Result |
| --- | --- |
| Active ADMIN/STAFF; valid target/version/payment/items | 200 |
| Same target as current | 200 unchanged |
| Missing order | 404 |
| Invalid ID/body/status/version/note | 400 |
| Anonymous, missing/blocked actor | 401 |
| Customer or demoted actor | 403 |
| Stale different-target version, invalid state/payment, changed owner or inventory bounds | 409, no partial effects |

## Payment rules selected by user

- COD UNPAID/PAID orders can be confirmed and shipped; online methods requirePAID before forward fulfillment.
- SHIPPING→COMPLETED requires already-PAID payment, including COD. This endpoint never marks paymentPAID or collects money. A separate payment-confirmation API is still to be implemented. Therefore an ordinary newly placed UNPAID COD order can reachSHIPPING but correctly receives409 on completion until payment is confirmed separately.
- REFUNDED orders cannot advance fulfillment. Cancellation is allowed fromPENDING/CONFIRMED forUNPAID or already-REFUNDED orders of supported stored methods; paid orders require a refund workflow first. This endpoint does not issue refunds or change payment status.
- Customer self-cancellation remains PENDING/COD/UNPAID only. Admin cancellation is a distinct permission/policy while using the same inventory-restoration implementation.

## Inventory, audit and retries

Checkout already deducted stock. Confirmation/shipping validate purchased quantities but do not recheck current product price/visibility/availability or deduct stock again. A purchased last unit may legitimately leave the catalog product OUT_OF_STOCK.

Cancellation restores existing products exactly once. Shared OrderInventoryEffects aggregates duplicate purchased product IDs, preservesHIDDEN and explicitOUT_OF_STOCK with positive stock, reactivates depletedOUT_OF_STOCK after restoration, skips deleted references and checks quantity/stock overflow. Customer cancellation keeps its previous error category/messages and behavior.

Completion increments sold_count by purchased quantities once, aggregates duplicate lines, skips deleted products and checks negative/overflow counters. It does not deduct stock, alter product visibility or update prices. Order/Product versions protect stale writers. Existing item/contact/address/amount/currency snapshots, cart contents, coupon usage and checkout keys/hashes remain untouched.

Every new transition records actual source/target, authenticated actor, time and bounded note in one transaction. Inventory/counter/history/version changes roll back together on failure. No Idempotency-Key is needed for status commands: a repeated current-target command has no effects. An earlier request sent after further progress is not a retry of current state and can return409.

## Locking and schema

Shared active-role guard does not take an actor write lock. The service reads a scalar owner reference, locks that buyer when present, then locks the order and rechecks its owner. Changed ownership returns409 instead of acquiring a new buyer lock after the order. Historical ownerless/deleted-account orders remain administrable. Inventory products are locked in ascending ID order. This matches existing customer-cancellation and checkout locking and avoids preloading stale products.

No migration is required: Order/Product versions and sold_count already exist. Restart backend to load the new endpoint. Future order/payment writers must use compatible locking/version checks; unconditional external SQL is outside these guarantees.

## Verification

- Test-first RED:58 API cases,57 missing-route failures (one access case already passed). GREEN: all58 API cases plus117 existing related admin/customer cancellation cases passed.
- Full25-pair status matrix, terminal/no-op behavior, stale versions, payment policies, both staff roles, blocked/demoted principals, strict input and maximum note.
- Stock/sale counters, exact integer limits/overflow/negative values, hidden/deleted/duplicate items, empty items, guest orders and immutable financial/contact snapshots.
- Ten committed test cases observe PostgreSQL lock waits: identical/different staff commands; admin/customer cancellation/confirmation races; completion once; shared products in inverse order; staff handling each other's orders; cancel vs complete counters; changed ownership; injected late DB failure followed by retry. Only uniquely owned fixtures and a unique test trigger/function are created on ogani_store_test and cleaned afterward.
- Independent review found no blocking issue. Its compatibility concern was reproduced with a regression asserting the original customer restock-overflow message, then fixed by preserving the original cancellation error category/text in shared effects. An injected500 in the late-failure test is intentional and verifies rollback.

Final verification: 11 unit tests + 507 integration tests = 518 passed, with zero failures, errors or skipped tests. This includes 58 new API cases and 10 new concurrency cases. `git diff --check` passed; test fixture and trigger cleanup checks returned zero remaining entries. No frontend, payment collection, commit or push is included.
