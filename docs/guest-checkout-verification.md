# Guest checkout and workflow regression — 2026-11-07

Anonymous visitors can add/update/remove basket items, including quantity 0 to remove, and place COD orders with manually entered contact and full shipping address. Customer login switches to the account basket without merging or removing the guest basket; logout restores the guest basket. Customers continue using their saved-address checkout.

## API and data

| API | Purpose |
| --- | --- |
| POST `/api/guest/session` | Initialize/reuse a random HttpOnly, SameSite=Strict guest cookie; return its hashed scope |
| POST `/api/guest/cart` | Resolve local product IDs/quantities against current catalog; flag unavailable rows so they can be removed |
| POST `/api/guest/checkout/preview` | Authoritative prices, shipping, total and scope-bound cartToken |
| POST `/api/guest/checkout` | COD placement with UUID Idempotency-Key, scope, quote token, items and manually entered contact/address |
| GET `/api/guest/orders/{id}` | Read confirmation only with the originating browser's guest cookie |

Migration `V16__guest_checkout_scope.sql` adds the private guest scope and a unique scoped checkout-key index. Orders have `user_id=NULL`, PENDING/UNPAID defaults and complete product/address snapshots. Guest checkout creates neither a user nor an address-book entry. Existing Admin/Staff order APIs process these orders normally. No guest order search by email, guest order list, or self-service guest cancellation is provided; the confirmation page explains contacting the store for changes.

The frontend stores at most 100 unique product IDs/quantities, never client prices, in `ogani.guest.cart`. Prices/availability come from the backend. Positive integer quantity, email, names, Vietnam phone, city, district, ward and detailed address are validated. Strict JSON parsing rejects fractional/string quantities and coerced contact fields. Checkout uses the same product/category/shipping-setting locks and pricing as customer checkout; adding to basket does not reserve stock.

Guest cookie scope serializes placement in a PostgreSQL transaction advisory lock. A matching key/body returns the original order; a changed body returns409. Rolled-back placement can retry, and a unique database constraint supplies a second duplicate guard. Quote fingerprints bind products, quantities, current prices/shipping and browser scope. Other/missing browser cookies cannot read the order.

Pending guest placement is persisted separately from customer checkout in localStorage before sending. Timeout/reload retries the exact key/body. Web Locks coordinate basket writes and initial cookie creation across tabs. Confirmed purchase quantities and consumed order IDs are saved atomically before clearing the pending attempt, so replay does not remove newly added items twice. A late replay clears only its own key and cannot erase a newer attempt. If the guest cookie changes while an attempt is uncertain, checkout stops and explains contacting the store.

## Actual browser verification

Final automated verification: **25 backend unit +709 integration tests**, zero failures/errors/skips, BUILD SUCCESS. Includes22 guest HTTP cases,2 committed concurrency cases and the full existing regression. Frontend: **72 Node test-runner cases +6 admin API adapter +6 product editor scenarios =84**, all passed. New/changed app and guest page modules pass Node syntax checks. Independent review's late-replay cleanup finding is fixed and regression-tested.

Tested in the in-app browser against isolated frontend15500/backend18080 and `ogani_store_test`, using disposable fixture product/customer. The user's frontend5500/backend8080 and development database were not used for these transactions.

- Guest: browse → add without login → quantity1→2 → checkout shows blank manual contact/address fields → enter full address → COD order COD-DQ, total23 (18+5), PENDING/UNPAID → basket empty after confirmation.
- Separation: add a new guest item → login fixture Customer → account basket empty → add account item → save full default address → COD order COD-DR, total14 (9+5) → cancel PENDING order → CANCELLED/UNPAID. Logout restores the separate guest item. Guest checkout fields remain blank despite the previously saved customer address.
- Admin: shared storefront login redirects to dashboard → Orders lists both new orders → guest detail shows no User ID and correct snapshots → PENDING→CONFIRMED→SHIPPING. While UNPAID, completion is unavailable. Confirm COD payment→PAID with actor/time → COMPLETED with status history. Product stock18 and sold_count2 reflect only the fulfilled two-unit guest order; customer cancellation restored its one unit.
- Navigation: admin logo opens storefront with the same admin account; browser Back retains the authorized admin session. Logout from admin opens shared login; Back does not reopen the admin session. Original guest confirmation remains readable in its browser and reflects COMPLETED/PAID.

Browser screenshots: [customer cancellation](screenshots/guest-regression-customer-cancelled.png), [admin completed guest order](screenshots/guest-regression-admin-completed.png).

After verification, the two fixture orders, fixture customer/address/cart and fixture product/category were deleted from `ogani_store_test`; isolated test servers were stopped. No development data was changed. Brave-specific browser behavior was not re-tested in this pass. Concurrent requests, lost response/retry and cross-tab late replay are covered by automated tests rather than browser fault injection.

## Suggested commit

```text
feat(checkout): support guest baskets and scoped COD checkout

- Allow anonymous cart operations and manually entered delivery details
- Keep guest and customer baskets separate across login and logout
- Reuse authoritative checkout pricing, inventory locks and order snapshots
- Protect guest orders with HttpOnly browser scope and scoped idempotency
- Preserve uncertain requests and prevent replay from clearing newer attempts
- Verify Guest, Customer cancellation and Admin COD fulfillment workflows
```
