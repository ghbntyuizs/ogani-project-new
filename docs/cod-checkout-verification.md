# COD checkout API and verification

## Customer flow

1. `GET /api/cart` lists current items; add/update/remove APIs remain unchanged.
2. `GET /api/checkout/addresses` lists only the authenticated user's saved addresses. Creating/updating saved addresses remains a separate future feature.
3. `POST /api/checkout/preview` (no body) validates the entire cart and returns items, subtotal, discountAmount, shippingFee, total, currency and a cartToken. No inventory is reserved. Hidden/out-of-stock products, inactive categories and insufficient stock reject the checkout with 409. Missing products are rejected without creating an order.
4. Select COD and either a saved addressId or enter deliveryAddress. Copy cartToken from preview and confirm with `POST /api/checkout`:

```http
POST /api/checkout
Authorization: Bearer <access-token>
Idempotency-Key: 3c073b1a-33ad-47ae-8abe-482ad2c0efef
Content-Type: application/json
```

```json
{
  "cartToken": "<64-character token from preview>",
  "firstName": "An",
  "lastName": "Nguyen",
  "email": "an@example.com",
  "paymentMethod": "COD",
  "deliveryAddress": {
    "phone": "0901234567",
    "country": "Vietnam",
    "address": "12 Nguyen Hue",
    "city": "Ho Chi Minh",
    "state": "Ho Chi Minh",
    "postcode": "700000"
  },
  "orderNotes": "Call before delivery"
}
```

For saved delivery, replace the entire deliveryAddress object with `"addressId": 123`, taken from the owned address list. Supplying both/neither is 400; missing/foreign address IDs are 404. Saved recipient name/phone/address are snapshotted. Direct delivery uses firstName + lastName for the recipient and does not automatically create a saved address. Address IDs must be JSON integers; strings/fractions/overflow are rejected. Payment method must be the explicit string `"COD"`; numeric ordinals/online methods are rejected.

5. New orders return HTTP 201 with `ApiResponse<OrderResponse>`: id, orderCode, contact/delivery snapshot, item name/price/quantity snapshots, totals/currency, COD, UNPAID, PENDING and createdAt. `GET /api/orders/{id}` returns the same owned snapshot; missing/foreign IDs return 404. There is no order listing/cancellation/status API yet.
6. Purchased cart item rows are cleared on successful placement, while the cart record stays available as an empty cart. Stock is deducted and product optimistic version advances; exactly-zero inventory sets OUT_OF_STOCK. sold_count is not incremented until a future completion workflow defines sales. Initial PENDING history records the placing user.

## Prices, shipping and retry behavior

Prices are calculated on the backend from current valid salePrice/price. Client monetary fields cannot change totals. Shipping uses the existing `settings` keys: `shipping_fee`, `free_shipping_threshold`, `currency`. Seed values are 5 USD shipping below subtotal 99 USD, free shipping at/above 99 USD. Preview is the authoritative shipping quote; the existing cart read response continues to show zero shipping. Missing/invalid shipping configuration or monetary overflow produces 409 before any order/stock mutation. Amounts use BigDecimal/two decimals and must fit DECIMAL(12,2).

There is no coupon application yet. A supplied nonblank coupon code is rejected with 400; a pre-existing cart coupon reference is rejected with 409 rather than silently discarding an expected discount. Product sale prices are supported.

Changed cart contents/quantities, relevant product prices/names/categories or shipping configuration invalidate the preview token and require fresh preview/confirmation (409). Stock is checked again independently at placement; a preview does not guarantee availability. All items must pass before any purchase is applied.

Generate one new UUID Idempotency-Key per intended order and retain its request body. Retrying a successful identical normalized request with the same key returns HTTP 200 and the same persisted order, even if the old cart is empty or new items have since been added. It never consumes those new items. Reusing a key for different request data is 409. Different keys cannot duplicate the same consumed cart. Failed transactions persist no success record and can be retried. Account status is rechecked before replay/detail access.

## Transactions and schema

Every cart/checkout writer locks the user first. Checkout reads scalar cart item identifiers/quantities to avoid preloading stale products, then locks products in ascending ID order, categories in ascending ID order, and shipping settings in key order. Product versions preserve the existing admin edit contract. Order/items/history, stock changes and cart clearing commit or roll back together. No network payment work occurs inside the transaction.

Flyway V8 adds per-user checkout-key uniqueness, canonical request hash, currency and recipient snapshot, plus a dedicated sequence for collision-free COD order codes. Legacy orders are backfilled with USD (matching existing seed data) and their original first/last name; recipient width121 supports two legal60-character names plus a space. Existing migrations V1–V7 remain unchanged. Restart backend to apply V8 to the application database. No automatic payment collection, emails, frontend integration or deployment is included.

## Verification record

- Test-first RED: 19 API cases failed before endpoints existed; first GREEN: all19 passed.
- Committed concurrency cases observe PostgreSQL lock waits: same-key double-submit, different-key duplicate attempts, last-unit competition, inverse cart insertion order, checkout vs add/update/remove, stale admin edit, and blocked-account replay/detail.
- Independent review found a max-name migration defect and coercion concerns. Regression RED reproduced V7→V8 migration failure, rejected legal max names, fractional address IDs truncating to an owned ID and numeric payment ordinals. Fixed width121 and strict request parsing; follow-up review found no further important issue.
- Migration regression creates a unique schema inside the dedicated test database, migrates to V7, inserts a maximum-name legacy order, migrates to current version and verifies the preserved name; only that fixture schema is removed.
- New checkout tests also cover price/address snapshots, shipping boundaries, subtotal/total overflow, zero-priced items, partial multi-product failure, current price/cart/shipping changes, saved/foreign/direct addresses, unsupported payment/coupon, rollback, stock/version and preserved cart behavior.

Tests explicitly use `TEST_DB_URL=jdbc:postgresql://localhost:5432/ogani_store_test`; no committed fixtures are run against the application database. The newly written, unreleased V8 was revised during review; only this session's test schema/checksum was aligned to the revision, and no application database was repaired.

Final run on 2026-11-03: **11 unit +289 integration =300 tests passed**, zero failures/errors/skips, Maven BUILD SUCCESS. This includes34 new checkout cases (24 API,9 committed concurrency,1 migration). Independent follow-up review found no further important issue; `git diff --check` passed. No browser/storefront or Docker verification is claimed; this change delivers backend APIs. No commit/push performed.
