# Add products to the authenticated user's cart

## Intent and scope

Implement backend `POST /api/cart/items` using the existing Spring Boot/JPA/PostgreSQL stack. Request: `{"productId": 123, "quantity": 2}`. Return the existing `ApiResponse<CartResponse>` envelope with HTTP 200 and the updated cart. The authenticated principal determines ownership; no client-supplied user/cart identifier is accepted. Preserve existing authenticated-role access to carts.

The user selected stock validation without reservation. Adding to a cart does not change product stock, sold count, price, or version. Future checkout must revalidate availability, prices and stock atomically.

## Approaches

Recommended: retain persistent carts and serialize mutations for one user by locking the existing user row before finding/creating their cart. This covers the first-cart race as well as subsequent increments. Retain existing unique constraints for one cart per user and one item per product/cart.

Alternative: use PostgreSQL upserts for cart/item creation and increments. This is compact but makes business validation, response mapping and future cart mutation rules less natural in the existing JPA service layer. Redis or session-only carts introduce unnecessary storage and persistence changes.

The user approved this design and implementation on 2026-11-03.

## Business rules

- Require login and an enabled account. Isolate carts between users.
- Require positive integer productId and quantity; reject missing/null values, zero, negatives, integer overflow and malformed input.
- Repeated additions increment the existing quantity; validate the combined quantity using overflow-safe arithmetic before writing.
- Reject nonexistent products, HIDDEN/OUT_OF_STOCK products, inactive categories, zero stock and quantities exceeding current stock. Reject the whole request without partial writes or an empty newly-created cart on failure.
- Use server-side current effective prices, including valid sale prices, through the existing response calculation. Never accept client prices or totals.
- Coupon discounts and shipping remain unimplemented and zero, matching the existing API. This endpoint does not implement checkout or change coupon behavior.
- Preserve existing items when a product later becomes unavailable; reject additional quantity for that product. GET remains read-only and does not silently remove items.
- Independent POST requests each represent an addition; automatic retry after an uncertain network outcome can add twice. Idempotency keys are outside this change.

## Concurrency and integration

Lock order for writes: user, then requested product. Read the requested product under a lock compatible with stock/status update serialization. The validation is a point-in-time check, not a stock guarantee after commit. Use a transaction for the entire operation and flush before returning. Do not increment the product's optimistic version merely for a cart addition. Avoid changing admin catalog update contracts and refresh-token locking.

Add a database CHECK for positive cart item quantities after checking existing data; do not silently delete or rewrite invalid legacy data. Preserve foreign keys and unique constraints. Keep Java type imports at file top.

## Verification

Cover initial add, repeated add, exact stock limit, excessive cumulative quantity, sale/normal prices and totals, multiple products, per-user isolation, authentication and disabled accounts, invalid input, unavailable products/categories, rollback after failure, and unchanged product stock/version. Exercise concurrent first-cart creation, same-product increments and additions exceeding stock using committed integration fixtures and separate transactions. Run existing cart, auth, catalog and full backend regression tests; document results and the API contract.

## Non-goals

Frontend, guest carts, quantity replacement, removal, coupon application, stock reservation and checkout are not part of this endpoint.
