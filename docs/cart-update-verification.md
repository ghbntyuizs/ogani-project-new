# Update cart quantity

## API

`PUT /api/cart/items/{itemId}` requires an authenticated active account. Use the **itemId** from GET `/api/cart`, not the productId.

```json
{"quantity": 3}
```

Positive quantity replaces the old value; it is not an increment. Repeating the same positive request leaves the same quantity, subject to current availability/stock validation. Quantity 0 deletes the owned item. `DELETE /api/cart/items/{itemId}` performs the same removal without a body. HTTP 200 returns the existing `ApiResponse<CartResponse>` with recalculated current prices, sale prices and totals. Other items are preserved; product stock, sold_count and version are unchanged. Coupon discounts/shipping remain zero.

| Condition | HTTP status |
| --- | --- |
| Positive integer quantity within stock; owned available item | 200 |
| PUT quantity 0 or DELETE; owned item regardless of product/category availability | 200 |
| Negative/missing/null quantity, strings, booleans, fractions or overflow; invalid itemId | 400 |
| Unauthenticated or blocked account | 401 |
| Missing cart/item, previously removed item or an item owned by someone else | 404 |
| Positive update on hidden/out-of-stock product, inactive category or requested quantity above stock | 409 |

Zero-delete and DELETE were requested by the user after the original positive-only implementation. Negative quantities remain invalid. Existing over-stock quantities can be reduced to an allowed available quantity. An unavailable item remains visible in GET and can be removed through either removal path. Extra productId/userId/cartId/price fields cannot change product, ownership or pricing. Removing the last item preserves the cart record and returns an empty items array with zero totals. Repeating removal returns 404 and cannot remove a newly-added replacement item with a different itemId.

## Concurrency and integration

All add/update/removal operations use the shared user-first lock. Positive add/update then lock the product; removal deliberately skips product locking/availability validation. Item lookup is scoped to the authenticated owner. Each change is one transaction; a failure leaves the quantity/timestamp unchanged and does not create a cart. Successful operations update the cart timestamp, including same-quantity requests. Only positive quantities are persisted; zero deletes the row and does not change the database CHECK constraint.

Concurrent absolute updates are serialized: the last successful update determines quantity. Concurrent add/update can yield different valid outcomes depending on lock acquisition order; there is no cart-version/If-Match contract. For example, from quantity 4, add 2 and update to 3 can finish at either 3 or 5. A failed stock validation has no partial effect. Requests validate catalog changes committed before the product lock is obtained.

There is no stock reservation or price guarantee; checkout must revalidate. No new migration is required for this update endpoint. Restart backend to load the new endpoint.

## Verification record

TDD RED: 27 assertions failed because PUT endpoint was absent (30 API cases total). GREEN: all 30 new API cases and 41 existing cart cases passed. Coverage includes absolute replacement, reduction/no-op/exact stock, over-stock recovery, strict body/path validation, missing/foreign items, spoofed fields, rollback, current pricing, preserved other items/inventory/version, inactive products/categories/accounts and POST accumulation after PUT.

Seven additional committed concurrency cases cover update/update, add/update, competing add/reduction, stock/status changes while waiting, stale principal rejection and timestamp advancement. They extend the existing isolated fixture class, require a dedicated *_test database and observe real PostgreSQL lock waits with refreshed activity snapshots. Fixtures are removed after each case.

Initial positive-update full-suite run on 2026-11-03: **11 unit + 230 integration = 241 tests passed**, zero failures/errors/skips; Maven BUILD SUCCESS. Independent review found no actionable implementation issues. Test execution explicitly sets `TEST_DB_URL=jdbc:postgresql://localhost:5432/ogani_store_test`; application development continues using ogani_store. No frontend changes or automatic commits/pushes.

Removal extension: 19 API cases cover both removal paths, unavailable products, remaining/empty totals, unchanged catalog, authorization, invalid/missing/foreign IDs and stale deletion after re-add. RED confirmed 17 failures before implementation. Added seven concurrency cases: zero/DELETE race, removal/add, removal/positive update, both removal paths during a held catalog update, and both paths with stale principals after account blocking. Final extension run: **11 unit + 255 integration = 266 tests passed**, zero failures/errors/skips, BUILD SUCCESS. Independent review found no important issues; mixed add/update removal races exercise DELETE while zero PUT uses the same deletion helper and is covered in the zero/DELETE race. `git diff --check` passed. No new migration required.
