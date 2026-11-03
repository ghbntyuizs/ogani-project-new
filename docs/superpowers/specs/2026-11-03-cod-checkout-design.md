# Authenticated COD checkout

## Intent and scope

Implement backend checkout for the entire authenticated user's cart, using the existing Spring Boot/JPA/PostgreSQL architecture. User selected COD first. Persist an order with immutable purchased-item and shipping/contact snapshots, deduct stock atomically, and clear purchased cart items only after all validation succeeds. Preserve the cart record.

No storefront work, guest checkout, online payments, coupon application, saved-address create/update/delete, order cancellation/status transitions or stock restoration are included. User approved implementation and added selecting an owned saved address OR entering a new delivery address; expose an owned address list for selection. New entered addresses are snapshotted on the order without automatically saving them. Provide owned order detail so a caller can retrieve the created order. Account roles retain the existing authenticated cart access policy; ownership is always derived from the principal.

## Approaches

Recommended and selected payment direction: COD checkout in one database transaction. Reuse shared user-first cart locking, lock products in ascending ID order, and retain product optimistic versions for catalog conflicts. This fits the current persisted cart and requires no external payment provider.

Online payment would require pending payment sessions, callbacks, verification, expiry and inventory release rules. Stock reservation on add would change the user's previously selected cart behavior. Neither is needed for COD.

## Proposed API and confirmation

- `POST /api/checkout/preview`: read the current cart and server-side prices/shipping settings; return purchasable items, subtotal, shippingFee, discountAmount=0, total, currency and cartToken. Preview does not create an order or reserve inventory. Empty/unavailable/insufficient-stock carts are rejected with actionable item details.
- `POST /api/checkout` with required UUID `Idempotency-Key` header and JSON containing cartToken, firstName, lastName, phone, email, shipCountry, shipAddress, shipCity, optional shipState/shipPostcode/orderNotes. COD is the only supported payment method; reject a supplied unsupported method instead of silently changing it. Return 201 with the created order; identical successful retries return 200 with the same persisted order.
- `GET /api/orders/{orderId}`: return the authenticated owner's order detail. Foreign and missing IDs return 404. This does not add order listing or admin order management.

cartToken is an opaque server-generated fingerprint of the checkout-relevant cart snapshot (item IDs, quantities, product IDs/names/effective prices/availability, currency and shipping inputs). Recompute it under the placement locks. Changed cart, price, product availability or shipping configuration returns 409 and requires a fresh preview. Never accept client prices or totals as authoritative. The token is not a payment credential or stock reservation; its integrity is verified by recomputation rather than trusting client fields.

## Business rules

- Require an active authenticated user at preview, placement, replay and detail. Validate and normalize contact/address field lengths against existing schema; require nonblank names/phone/country/address/city and valid email. Reject invalid body/header/path values with 400.
- Use current effective salePrice when valid, otherwise price; reject invalid negative monetary state rather than creating a negative order. Validate requested item quantities, ACTIVE product status, active direct category and available stock again during placement. If any item fails, reject the entire checkout and preserve all cart/stock/order state.
- Read shipping_fee, free_shipping_threshold and currency from the existing settings table. Current seed is fee 5, free shipping when subtotal >=99, currency USD. Compare the threshold against subtotal before shipping. Fail explicitly for missing/invalid settings instead of silently charging an invented fee. Snapshot the actual shipping amount and currency on the order.
- Coupons remain unimplemented: reject a supplied coupon or an existing cart coupon reference explicitly rather than silently ignoring an expected discount. This does not affect product sale prices.
- Use BigDecimal with exact currency amounts at two decimal places; guard line totals, subtotal and total against existing DECIMAL(12,2) overflow before writing. Guard quantity and sold-count arithmetic where used.
- Create unique order code, PENDING order, UNPAID payment, COD method and initial PENDING history record. Snapshot purchased product name, unitPrice, quantity/lineTotal and all contact/address fields. Keep historical data stable after product/user/address changes. sold_count remains unchanged until a future completion workflow defines successful sales.
- Decrement stock by purchased quantity; when stock reaches zero set OUT_OF_STOCK. Persist through versioned Product entities so admin forms loaded before checkout cannot overwrite deducted inventory with an old version. Do not mark paid or completed for COD placement.
- Clear purchased cart items, update cart timestamp and preserve the cart. Failures roll everything back. Cart totals shown outside checkout continue their current behavior; preview is authoritative for shipping.

## Concurrency, retries and schema

Placement transaction: lock/revalidate user; check existing order by user + idempotency key; then load cart item identifiers/quantities without eagerly loading mutable product state; lock each product in ascending ID order; obtain and validate current shipping configuration/category state; verify token; calculate and persist order/items/history; decrement versioned stock; clear purchased items; flush and commit. Follow the existing user -> product lock order. Preview should read a coherent snapshot (using the same locking protocol where needed) but release locks immediately and reserve nothing.

Store idempotency key and canonical request fingerprint on orders with a unique (user_id, checkout_key) constraint; historical seeded orders have nullable key/hash. A committed retry is returned before checking the now-empty cart. Same key with different normalized request data returns 409. A rolled-back request has no successful record and can be retried. A different key after success sees an empty cart, not a second order. Replaying after new cart additions must return the old order and leave new cart contents untouched. Keys stay with the order; no separate cleanup timer.

Use a new Flyway migration for nullable checkout key/request hash and nonnull currency with a documented USD backfill matching legacy seed data. Preserve original migrations. Order codes must be generated collision-safely using a DB sequence/identity-derived value within VARCHAR(20), not row counts. Keep existing Java import conventions.

## Verification and boundaries

Test happy path, sale/normal/zero prices, shipping threshold boundaries, normalized address snapshots, owned order detail, stock/version/status updates, unchanged sold_count, cart clearing and initial history. Test empty/missing carts, invalid inputs, unavailable products/categories, insufficient stock in one item of a multi-item cart, missing/invalid settings, unsupported coupons/payment method and monetary overflow; assert no partial writes.

Use committed isolated fixtures on ogani_store_test for concurrent same-key double submit, changed-key duplicate attempts, different users competing for last units, inverse cart product ordering, checkout vs add/update/remove and checkout vs admin edits. Observe actual database contention with bounded waits. Test identical retry after response loss, changed-body/key conflicts, token mismatch after cart/price/shipping changes, and replay with a newly populated cart. Run all existing backend tests and independent code review; do not claim frontend integration or payment collection.

## Approval

Approved for implementation by user, including the ten checkout business steps and saved/new address selection. Clear purchased cart contents on success while retaining the cart record, consistent with existing cart API behavior. Explicit COD selection is supported; online payment remains out of scope.
