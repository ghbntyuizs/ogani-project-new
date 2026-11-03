# Add-to-cart API

## Contract

`POST /api/cart/items`, bearer token required. Available to the same authenticated roles as `GET /api/cart`.

```json
{"productId": 1, "quantity": 2}
```

HTTP 200 returns the existing `ApiResponse<CartResponse>` envelope, including cartId, items (itemId, productId, productName, thumbnailUrl, unitPrice, quantity, lineTotal), subtotal, couponCode, discountAmount, shippingFee and total. A subsequent add increments the existing item. Owner and prices come from the server; client userId/cartId/price fields cannot select another cart or change pricing.

Both fields must be positive JSON integers within Long/Integer ranges. Fractional values, numeric strings, booleans, missing fields and malformed JSON return 400. Missing products return 404. HIDDEN/OUT_OF_STOCK products, inactive categories and insufficient cumulative stock return 409. Unauthenticated or blocked accounts return 401.

No inventory is reserved or deducted. Product stock, sold count and optimistic version remain unchanged. Checkout must revalidate current price, availability and stock in its own transaction. Current product prices, including valid salePrice, are used; there is no price snapshot in a cart. Coupon discount and shipping remain zero, as before.

Existing unavailable items remain visible through GET; adding more is rejected. This endpoint does not replace quantities, remove items or apply coupons. Every POST is a separate addition: retrying after an uncertain network result can add twice. Clients should read the cart before retrying; no idempotency-key support is promised.

## Transaction and database rules

Each mutation locks the authenticated user's row before finding/creating their cart, then locks the requested product before validating stock/status. This serializes first-cart creation and increments across application instances using PostgreSQL, while preserving existing unique constraints. Failure rolls the transaction back. Cart updated_at advances on successful mutations. GET remains read-only.

All future cart writers must follow the user-first locking convention. Stock validation is a point-in-time check; subsequent catalog changes can make an existing cart unavailable or over stock. Category activity is checked when processing the add, without reserving category state afterward.

Flyway V7 adds `CHECK (quantity > 0)`. Migration stops with an explicit message if legacy non-positive quantities exist; it does not silently rewrite customer carts. Restart backend to apply the migration.

## Verification

New `CartAddItemIT` covers API rules, integer overflow, strict JSON parsing, ownership, sale/normal/current prices, rollback, authentication, and preserved inventory/version. `CartAddConcurrencyIT` creates committed test-owned fixtures and uses separate request transactions for first-cart creation, existing-item increments, competing stock checks, failure rollback, timestamp advancement, and database quantity protection. It holds a user-row lock until two requests are visibly blocked in PostgreSQL; product-row contention cases verify an addition waits for committed stock/status changes. A stale authenticated principal is rejected after the account is blocked. The polling helper clears PostgreSQL's transaction-cached activity snapshot between samples. Cleanup only removes test-owned fixtures; tests refuse to run committed fixture writes unless current_database ends in `_test`.

Set TEST_DB_URL explicitly before running tests, since inherited environment variables can override the default test database:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/ogani_store_test'
mvn -f backend/pom.xml test failsafe:integration-test failsafe:verify
```

Initial RED: missing endpoint caused 25 assertion failures. After endpoint implementation, 31 new API cases and existing CartApiIT passed. A second RED exposed the absent CHECK and unchanged cart timestamp; both were implemented afterward.

Final run on 2026-11-03: **11 unit tests + 193 integration tests = 204 passed; zero failures, errors or skips**, including 40 new cart cases. Maven reported BUILD SUCCESS. Independent review found no important implementation defects; its three minor coverage suggestions were implemented. `git diff --check` passed. No frontend files changed; no commit or push performed.

Environment note: the inherited TEST_DB_URL initially pointed at the development database. The initial new API tests ran in rollback transactions, without a new migration. Subsequent runs explicitly used the separately-created ogani_store_test database; V7 and all committed concurrency fixtures were applied only there.
