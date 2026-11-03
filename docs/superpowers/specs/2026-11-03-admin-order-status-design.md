# Admin/Staff order status transitions

## Scope and choices

Implement PATCH `/api/admin/orders/{id}/status` for active Admin/Staff. User confirmed cancellation from PENDING or CONFIRMED only; SHIPPING cancellation requires a separate failed-delivery/return workflow and is excluded. User selected completion only after payment is already PAID; payment confirmation is a separate future API.

Synchronous transaction and explicit state machine fit the existing COD checkout, cancellation and Order @Version. Arbitrary status assignment would bypass inventory/history safeguards. A background workflow engine is unnecessary for these immediate transitions.

## State graph

| Current | New allowed states |
| --- | --- |
| PENDING | CONFIRMED, CANCELLED |
| CONFIRMED | SHIPPING, CANCELLED |
| SHIPPING | COMPLETED |
| COMPLETED | none |
| CANCELLED | none |

The same target as current state is an unchanged200 response with no new history, timestamps, payment changes or counters. Different transitions must satisfy both state and version checks. No backward/skipped transition or reopening terminal orders.

## Proposed request/response

Body: `{"status":"CONFIRMED","version":0,"note":"Order checked"}`. Require exact enum-name string and a nonnegative integer Long version from admin order detail. Reject null/missing/unknown/numeric status, malformed/fractional/string/overflow version, invalid IDs or nonstring/over200-character note with400. Normalize blank optional note. No client actor ID or client prices are accepted as authoritative.

Return a compact ApiResponse summary with id, orderCode, orderStatus, paymentStatus, version and updatedAt. New successful changes increment version once; mismatched version for a different target returns409 without mutation. A repeated same-target command returns the current summary even with a prior version, without reapplying effects or replacing the original note/actor. Missing order404; Anonymous/inactive actor401; non-Admin/Staff403.

## Approved payment direction

1. Recommended: SHIPPING→COMPLETED requires already-PAID payment. This endpoint never changes payment status; confirm cash collection through a separate future payment API. Until that API exists, an UNPAID COD order cannot be completed here.
2. Alternative: explicitly confirm COD cash collection within the completion request, requiring a dedicated confirmation flag for COD/UNPAID. Mark PAID and COMPLETED atomically with audit information. It must not imply that an online payment provider was charged.

Other proposed policies: COD UNPAID/PAID may be confirmed/shipped; online methods require PAID before forward processing. REFUNDED orders cannot advance fulfillment. Cancellation may release UNPAID or already-REFUNDED orders from allowed source states, without issuing refunds; PAID orders require a refund workflow first. The existing customer cancellation endpoint remains PENDING/COD/UNPAID only. These administrative payment guards require final design approval.

## Inventory and audit effects

- Checkout already deducts stock. Confirmation/shipping must not deduct it again or recheck current catalog availability/prices as if making a new purchase. Validate purchased item quantities; preserve all financial/contact/item snapshots, cart state and checkout key/hash.
- Cancellation restores existing product quantities exactly once using the same rules as customer cancellation: aggregate duplicate lines, guard int overflow/negative inventory, preserve HIDDEN, reactivate OUT_OF_STOCK only when previous stock was0, skip deleted product references and audit the skipped count. No coupon usage adjustment or sold_count decrement.
- Completion increments sold_count by purchased quantities exactly once, aggregating duplicate products and skipping deleted references without recreating them. Guard negative/overflow counters and validate all changes before applying. Do not modify remaining stock, product price or visibility during completion.
- New transitions append one history event with actual from/to states, authenticated actor, server time and bounded note. Terminal no-ops/retries append nothing. Order/history/counter/payment effects must commit or roll back together.
- Extract shared inventory effects rather than duplicating the existing customer restock policy; retain all customer-cancellation regression behavior.

## Locking and consistency

Do not take an actor write lock before a different buyer lock: two staff processing each other's orders could deadlock. Revalidate actor role/status, discover owner using a scalar projection (not a managed Order), lock buyer if present, then lock the order and compare its current owner. Reject changed ownership with409 rather than acquiring a new buyer lock after the order. Ownerless/deleted-account historical orders remain administrable. Products are locked in ascending ID order for inventory/counter effects.

This preserves existing user→order→product ordering for customer cancellation and user→product ordering for checkout. Use existing Order/Product optimistic versions and flush before returning the summary. Future state/payment writers must share locking/version rules; unconditional external SQL cannot be made safe by an ORM endpoint.

## Verification

Test the full state matrix, same-target no-ops, terminal states, expected versions, strict input, both roles, anonymous/customer and blocked/demoted stale actors. Test ownerless/deleted-customer orders, deleted/hidden products, duplicate lines, empty/invalid items, stock/sold-counter boundaries, note length and unchanged snapshots.

Committed test fixtures on *_test only: two staff competing, same/different targets, admin confirm/cancel versus customer cancellation, completion counters once, stale product/admin edits, checkout competition, inverse product order and injected late failure rollback. Verify actor/history/version effects and existing checkout replay after terminal transitions. Full backend regression and independent review; update tracking/docs and include commit description without committing.

## Approval

User instructed implementation of this design and confirmed PENDING/CONFIRMED cancellation and PAID-only completion. Implement payment option1 only; no confirmation flag or payment-state mutation in this endpoint.
