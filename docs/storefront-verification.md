# Customer storefront

Updated2026-11-07: the local Node server now hosts both UIs. Use `/index.html` for storefront, `/` for admin and `/login.html` for shared role-aware login. Sessions are now shared between both interfaces. Admin order UI is connected. See [current unified web instructions](unified-web-verification.md); the following records the original customer storefront implementation and verification.

Implemented 2026-11-04. Entry: `http://localhost:8080/shop/` after starting/rebuilding the backend. Alternative: `cd storefront` then `node dev-server.mjs`, open `http://localhost:5500/`, keep API on 8080. No frontend dependencies or install step. API base and catalog/cart display currency are in `storefront/assets/config.js`; default USD matches current backend settings. Preview/order currency comes from the API. Do not use file://.

## API coverage

| Page | Connected operations |
|---|---|
| Catalog/detail | GET products with search/category/price/sale/sort/pagination, categories, product by slug, gallery |
| Cart | GET cart, POST items, PUT absolute quantity (0 removes), DELETE item |
| Addresses | GET checkout/addresses, POST, PUT, DELETE addresses, PATCH default |
| Checkout | POST preview, save/select full address, POST checkout with COD/contact/cartToken/addressId and Idempotency-Key |
| Orders | GET own orders with pagination/status/sort, GET detail snapshots, POST cancel with reason for pending unpaid COD |
| Authentication | Register/login/me/refresh/logout; forgot-password request; existing backend reset-link form |

All data is fetched from the backend. API text is rendered as text, including product descriptions. Cart DTO does not expose product slug/availability flags, so cart does not invent detail links or stock labels. Cart shows subtotal; shipping and final totals appear at checkout.

## Checkout and session rules

- A cryptographic UUID and exact request body are persisted in per-customer sessionStorage before placement. Timeout/network/5xx/malformed success retains that attempt; reload/retry sends the same body/key before reading the potentially cleared cart.
- Uncertain checkout blocks cart/address edits. Catalog add attempts route to checkout recovery. A definitive validation/conflict response requires review and confirmation of a fresh preview.
- Customer session is separate from admin. Refresh is single-flight; cloned tabs with Web Locks must log in independently. Logout/session epoch prevents stale responses restoring a session. Explicit logout clears that customer's pending local attempt; users can still inspect their orders after logging back in.
- COD only. No online payment, coupon application, customer profile editor, or admin order UI was added in this task.
- Address names are validated text, not geographic catalog codes, matching the current backend. New checkout addresses are saved before using addressId, preserving district/ward snapshots.

## Verification

- Backend: 25 unit + 685 integration tests, zero failures/errors/skips, BUILD SUCCESS. Includes three new static routing/security/resource-exclusion tests. Database explicitly `ogani_store_test`.
- Storefront: 27 Node tests for session refresh/logout/roles, safe returns, serialized cart writes, checkout uncertainty/idempotency/reload/double click, disabled states and static server containment.
- Existing frontend regressions also passed: 6 admin API + 6 product editor + 7 password reset scenarios. All storefront JavaScript passed syntax checks; Git diff whitespace check passed.
- Independent code review found catalog writes bypassed pending-checkout freeze; fixed centrally and in catalog routing.
- Actual browser against isolated backend port 18080 and test DB: registered disposable customer; opened product detail; added own fixture product; quantity 1→2; created full address; preview subtotal18/shipping5/total23; placed one COD order; displayed returned order id with PENDING/UNPAID; canceled and displayed CANCELLED; filtered PENDING to empty list; updated address; created second address, made default, deleted it and observed original promoted; quantity1→0 emptied cart; logout then private cart redirected to login.
- Inspected mobile layout at390px; corrected filter wrapping and observed corrected layout. Corrected literal null text from native append/replaceChildren optional children. Browser console had no warnings/errors at final order view.
- Lost-response, retry and double-click paths are automated client tests; they were not simulated through browser networking. COD paid/completed display uses backend status fields; staff payment/status flow remains covered by backend tests, not this browser pass.

Browser proof: [canceled order](screenshots/storefront-order.png). All browser transaction data used test fixtures, not the development store.
After verification, the disposable customer/order/product fixtures were removed from `ogani_store_test` and the isolated backend on18080 was stopped. The fixture product's stock returned to20 after cancellation, before cleanup.

## Suggested commit

```text
feat(storefront): integrate customer shopping and COD checkout APIs

- Add responsive product, cart, address, checkout and customer order pages
- Connect customer authentication, token refresh and protected navigation
- Preserve checkout request/key across uncertain responses and serialize cart writes
- Serve storefront at /shop and provide a dependency-free local dev server
- Add client/static integration tests and document browser verification
```
