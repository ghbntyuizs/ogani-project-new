# Customer storefront implementation plan

Goal: User authorized connecting all customer product/cart/address/checkout/order APIs. Storefront source is absent, so add storefront/ using existing HTML/CSS/vanilla JS stack and Ogani visual language. Public catalog/detail, customer login/register/recovery, authenticated cart/address CRUD/default, COD checkout and own order list/detail/cancellation. No unsupported online payment/coupon UI.

Architecture: Dependency-free ES modules; common safe DOM/UI, isolated customer API/session client, tested checkout state machine, page modules. Serve statically from backend /shop via Maven resource mapping (same origin) or loopback Node/VS Code on5500 with API config. Reuse owned existing product imagery. Customer session key separate from admin, per-tab refresh single-flight/ownership and session-epoch protection. Data from APIs only; product/cart currency config defaults to existing USD, checkout uses BE currency.

Critical workflow: cart writes serialized; quantity0 removes. Address form reused on checkout to save/select full district/ward; selected saved addressId sent to BE. Preview provides authoritative cartToken/totals; FE generates one cryptographic UUID per logical placement and saves exact body/key per customer before POST. Timeout/5xx/invalid-success-response keep pending body/key and freeze edits; retry same request before reading potentially-cleared cart. Success200/201 redirects returned order id. Definitive400/404/409 clears pending and requires user review/fresh preview; auth expiry preserves attempt for same-user re-login. Explicit logout clears own local pending data. No silent new-key retry or silent price acceptance. Browser back cache reloads checkout.

- [x] Add RED client/checkout/contract tests; implement isolated auth/API client, pure flow helpers and pending checkout manager.
- [x] Build responsive catalog/detail and real cart integration; use safe text rendering and valid URL/image handling.
- [x] Implement address create/update/delete/default and checkout preview/contact/selection/COD placement/recovery.
- [x] Implement customer orders/filter/pagination/detail/cancel and prerequisite auth/recovery pages.
- [x] Add static serving/security/build resources and dependency-free local dev server.
- [x] Run unit/API/static checks, backend regression, independent review and browser E2E on isolated test data; fix observed issues.
- [x] Update README/checklist/verification and commit description; no commit/push.

Review focus: lost checkout response must not duplicate orders; no cart GET prerequisite on pending retry; copied-tab refresh isolation and logout response races; no other-customer pending attempt reused; canceled/unavailable cart rows removable; no client prices trusted; loading and validation errors usable; HTML/XSS-safe API data; resource path compatibility under /shop and local server.

