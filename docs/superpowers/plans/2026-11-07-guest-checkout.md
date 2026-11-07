# Guest checkout and final workflow verification

Goal: user authorizes final regression plus actual Customer/Admin workflows, and anonymous cart/checkout with manually entered contact/address. User explicitly selected separate guest/customer carts: no merge on login; guest cart stays available after logout.

Architecture: guest basket stores only product IDs/quantities locally; server resolves current catalog data and authoritative quote. Dedicated public /api/guest endpoints use a cryptographic HttpOnly SameSite cookie for order scope, never email-based access. Orders have no user owner, retain full snapshots and existing COD/PENDING/UNPAID processing. Scoped idempotency unique constraint and transaction lock serialize guest placements; product/category/settings locking/pricing reuse existing checkout logic. Guest read-only order confirmation is accessible only with the guest cookie. No guest address-book API or implicit account creation. Guest private confirmation and pending request state are distinct from customer order pages.

- [x] Baseline full BE suite before Java edits; RED tests for guest basket, APIs/authorization/validation/replay/inventory/snapshots.
- [x] Guest DTO/controller/service, order scope migration and shared pricing adapter. Validate max100 unique product IDs, positive bounded quantity, email/name/Vietnam phone/full city/district/ward/address. No client prices accepted. Customer/Admin APIs remain protected.
- [x] FE guest cart adapter and guest checkout form. No calls to saved-address APIs when anonymous. Shared pending-checkout manager supports guest endpoint/scope and same-key/body retry. Successful order clears only purchased guest basket after confirmed response.
- [x] Guest confirmation page, Admin visibility/processing of ownerless orders; maintain separate carts on login/logout.
- [x] Full JS and BE regression, independent review, isolated browser Guest/Customer/Admin workflows including COD completion/cancellation.
- [x] Document actual evidence, limitations, commands and commit description. No commit/push; preserve existing edits.

Rulings: COD remains selected payment method. Guest receives private order confirmation using browser cookie; no public order lookup by email/code, no guest account/address persistence. Cart maximum100 types prevents unbounded quote work. Guest scope cookie is initialized under a browser lock and quote fingerprint includes scope to reject scope changes. Pending checkout blocks edits until resolved. Need stable scope returned by server for retry validation.

Evidence: docs/guest-checkout-verification.md. Final BE25 unit +709 IT; FE84 scenarios. Independent review issue fixed with matching-key cleanup test. Browser Guest/Customer/Admin verified on isolated15500/18080 test database and fixtures cleaned.

