# Customer order cancellation

## Scope and chosen approach

Implement POST `/api/orders/{id}/cancel`. The user selected customer self-cancellation only while PENDING. Use a synchronous transactional cancellation with stock restoration; an asynchronous cancellation request/approval flow is unnecessary for a PENDING COD order. No admin status API, refunds or cart re-add behavior is included.

Optional body: `{"reason":"Changed my mind"}`. Accept no body or an empty object, normalize blank reason to a default note, enforce a string reason up to200 characters. Return HTTP200 using the existing ApiResponse<OrderResponse> shape. Keep endpoint in a separate cancellation controller, compatible with existing order detail and future list controllers.

## Business rules

- Require an authenticated active account; validate positive order ID. Unknown or foreign-owned orders return404 without revealing their status. Existing authenticated roles may cancel only their own orders, consistent with the current owned-order APIs.
- Only PENDING + COD + UNPAID orders may newly transition to CANCELLED. Other order states or paid/refunded/unsupported payment methods return409; this endpoint never claims to refund money.
- A previously CANCELLED owned order returns200 unchanged. Do not restore stock, change timestamp, or add another history record on retries. A repeated request cannot revise the original cancellation reason.
- On first cancellation, restore purchased quantities exactly once; preserve order item/name/price/address snapshots, monetary totals, payment status, sold_count, checkout key and request hash. Never reinsert items into the cart. Checkout replay for the old key must return the original now-cancelled order and must not purchase again.
- Aggregate repeated order-item product IDs using overflow-safe arithmetic before restoring inventory. Reject invalid quantities, negative current stock or Integer stock overflow with409 and no partial effects. A PENDING order without item rows is inconsistent and returns409.
- Deleted products (nullable product_id due existing FK) are not recreated; skip those restocks and record their count in cancellation history. Restore hidden products' quantities while preserving HIDDEN. An OUT_OF_STOCK product transitions to ACTIVE only when its stock was zero and becomes positive through restoration; preserve explicit OUT_OF_STOCK with already-positive stock. No category is activated or modified.
- Record one PENDING→CANCELLED history event with authenticated actor, server time and reason; update the order timestamp. Product versions advance on inventory changes. No coupon usage or refund adjustments are implemented.

## Concurrency

One transaction locks user first, then the owner-scoped order row, then products in ascending ID order. Read item product IDs/quantities as scalars before product locks to avoid stale eager-loaded state. This fits the existing checkout/cart user-first protocol. Flush state/history/stock together; rollback all changes on any failure.

Add a new V9 migration and @Version on Order so stale versioned status writers cannot overwrite a cancellation. Future admin transitions must use order locking/version checks and revalidate state; unconditional external SQL updates are not protected by ORM rules. Concurrent repeat cancellation produces a single transition, one history event and one restock.

## Verification and review

Test first cancellation, optional/invalid reason/body/IDs, nonexistent/foreign/null-owner orders, Anonymous/Customer/Admin/Staff own-only access, blocked/stale principals, all order/payment states, already-cancelled retry, restored stock/status/version, HIDDEN preservation, deleted products, duplicate product lines, maximum/overflow stock, unchanged snapshots/cart and checkout replay after cancellation.

Use committed isolated fixtures only on *_test databases for overlapping cancellations, cancellation versus another order/checkout on shared products, and stale versioned status writers. Validate complete rollback on a later product failure and no deadlocks with inverse item insertion order. Run full backend regression and independent review; update verification/checklist and provide a commit description without committing automatically.

## Approval

PENDING-only cancellation and this design were approved for implementation by the user on2026-11-03.
