# Update cart item quantity

## Intent and approach

**User-approved amendment (2026-11-03):** The user superseded the original zero-rejection rule below: PUT quantity 0 now removes the owned item; add DELETE `/api/cart/items/{itemId}` for the remove button. Both removal paths validate active account and ownership, bypass product availability/stock checks and product locking, preserve the cart row, advance its timestamp and return the remaining cart (empty items/zero totals for last-item removal). Negative quantities still return 400; missing/foreign/already-removed item IDs return 404. Database quantities remain strictly positive because zero deletes the row. All mutations share the user-first lock; test removals against concurrent add/update and catalog writes. No migration or frontend change is needed. Earlier positive-only rules below describe the initial design and are superseded where they conflict with this amendment.

Add authenticated `PUT /api/cart/items/{itemId}` with body `{"quantity": 3}`. This replaces the quantity and returns HTTP 200 with the existing `ApiResponse<CartResponse>` shape. PUT fits an absolute replacement and repeated requests have the same quantity outcome. Delta updates are already served by POST add; introducing another delta API would duplicate that behavior.

Use the current Spring Boot/JPA/PostgreSQL implementation. No frontend, checkout, coupon or DELETE implementation is included. The user selected rejecting quantity zero and keeping deletion as a separate API.

## Rules

- Accept only a positive JSON integer quantity within Integer range. Reject zero, negatives, null, missing, fractional, boolean, string and overflow values with 400. Validate itemId as positive.
- Principal determines the cart owner. A missing cart/item or another user's item returns 404 with no change and no new cart creation. Revalidate active account under the user lock.
- Replace existing quantity, including reductions and no-op repeats, only for ACTIVE products in active categories and when requested quantity does not exceed current stock. Reject unavailable products/categories and insufficient stock with 409, leaving the old quantity intact. An over-stock existing item may be reduced to a valid available quantity.
- Keep unavailable items visible through GET. Updates to unavailable items are rejected; a future DELETE API will remove them independently of availability.
- No stock reservation/deduction and no change to product optimistic version/sold count. Prices and totals are calculated by the server from current prices, including salePrice, using the existing response mapping. Client productId/userId/cartId/price fields cannot reassign ownership or products.
- Advance cart updated_at on success. Quantity zero does not delete an item. Existing DB quantity CHECK remains unchanged.

## Transaction and concurrency

Use one transaction. Lock the user first, locate the item scoped to their cart, lock its product, validate and flush the item/cart change, then return the updated cart. Reuse the same lock order as POST add. An update must never lock an item belonging to another user. Product deletion outside the supported soft-delete API is outside the cart concurrency protocol.

Concurrent writes are serialized, with the last successful absolute update winning; there is no optimistic cart version contract in this change. Concurrent add and update apply in lock-acquisition order. Every accepted result must stay positive and within stock at validation time. Subsequent catalog changes can invalidate a cart; checkout must revalidate.

## Verification

Write failing endpoint tests before implementation. Cover replacement versus increment, reduction, exact stock, same-quantity repeat, over-stock recovery, invalid body/path values, missing and foreign items, unchanged state after failure, blocked/anonymous accounts, current sale/normal totals, inventory/version preservation and interaction with POST add.

Use committed fixtures only on ogani_store_test. Extend deterministic lock-wait integration tests for update/update and add/update ordering, product stock/status changes committed while an update waits, and stale authenticated principals after account block. Preserve all existing cart/auth/catalog tests and run the full backend suite. Document outcomes and keep changes uncommitted.

## Approval

Quantity-zero behavior and this design were approved by the user on 2026-11-03.
