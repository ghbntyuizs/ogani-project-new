# Shared web and admin orders

Implemented2026-11-07 following the requested routes. Source stays organized in admin/ and storefront/, with one server, origin and session.

Latest follow-up: [single storefront login and cross-tab logout](logout-sync-verification.md). Logout now invalidates other same-account tabs; legacy admin login is redirect-only. Earlier verification records below describe the sequence of changes.
Login link and legacy bookmark audit: [verified URLs](url-bookmark-verification.md).

## Run

Start PostgreSQL and the existing backend on8080. Stop the previous frontend/Live Server/jwebserver on5500. In VS Code PowerShell:

```powershell
cd C:\Users\X\Desktop\ogani-project
.\start-web.cmd
```

Equivalent: `node storefront/dev-server.mjs`. Node18+ required; no npm install. If node is unrecognized, install Node.js LTS once (`winget install --id OpenJS.NodeJS.LTS -e`) and restart VS Code. This replaces serving admin/ with jwebserver.

| URL on localhost:5500 | Behavior |
|---|---|
| /login.html | One login form; CUSTOMER→/index.html, ADMIN/STAFF→/ |
| / | Admin dashboard; guests→login, Customer→storefront without revocation |
| /index.html | Public storefront; authenticated staff can browse without logout |
| /admin/orders.html | Real order list and filters, Admin/Staff only |
| /admin/order-detail.html?id=... | Snapshots, history, permitted status changes and COD collection |

Admin logo **Ogani Store** goes to /index.html; staff storefront nav **Quản trị** goes back to /. Same tab keeps the same account. Customer-only cart/address/checkout/order APIs remain Customer-only; staff sees an explanation instead of being logged out. Existing per-tab/Web Locks refresh isolation remains: cloned tabs must sign in independently. Old isolated admin/customer sessions are not migrated; sign in once after upgrading. Passwords are never stored.

The loopback server proxies /api/* to http://localhost:8080, preserving Authorization, Idempotency-Key and multipart bodies. It drops browser Origin/Referer for this server-to-server local hop. No CORS configuration is required for the unified UI. It is not a production hosting server. Override `$env:OGANI_API_TARGET='http://127.0.0.1:18080'` or `$env:STOREFRONT_PORT='15500'` for isolated testing. Target is restricted to local HTTP. Catalog display currency uses OGANI_CURRENCY (USD default); order currency is authoritative from backend.

## Admin order features

- GET list: keyword across order code/customer name/email; status/payment filters; inclusive created-date range; createdAt ascending/descending; page/size; empty/error/retry/reset states.
- GET detail: customer, item name/price/quantity snapshots, full delivery address, notes, coupon/discount/shipping/currency, status history with actor, payment method/status/paidAt/confirmedBy.
- PATCH status sends current version. Only payment-compatible transitions are offered; terminal orders have no mutation actions.
- COD collection is offered only UNPAID COD + SHIPPING; PAID before COMPLETED. Confirmation requires user action. Paid orders cannot be canceled; refunded orders cannot be fulfilled.
- After conflict/uncertainty the UI reloads authoritative detail and requires a new deliberate confirmation; it never silently retries a state mutation with a new version. Failure to reload removes stale action controls.
- No export endpoint, so no fake Export action. List DTO has no item-count field; the table does not invent a count. API content uses text nodes.
- Dashboard statistics and unconnected admin pages are still previews, visibly labeled. This task does not implement statistics APIs.

## Verification record

Browser used isolated web15500→backend18080→ogani_store_test. Disposable customer/product/two COD orders created via APIs; no dev store data changed.

- Admin login landed at exact /. Logo→/index.html kept Michael Scofield authenticated; Quản trị returned to admin.
- Admin Orders retrieved live seed and newly-created rows, keyword searched own fixture and opened the correct id. Snapshot/history/address/payment matched checkout.
- Admin changed one order PENDING→CONFIRMED→SHIPPING, confirmed COD PAID (actor/time recorded), then COMPLETED. Version progressed0→1→2→3→4, timeline rendered correctly, terminal actions removed.
- Staff login landed at /. Staff canceled the second pending order; history displayed Linda Burrows and the supplied note. Staff opening cart kept session and displayed Customer-only explanation.
- Customer login landed at /index.html. Navigating / redirected back without logout. Own orders displayed the admin COMPLETED/PAID and staff CANCELLED/UNPAID results. Logout cleared the visible session.
- Browser console had no errors at final check. Screenshots: [completed admin order](screenshots/admin-order-completed.png), [customer sees both results](screenshots/unified-customer-orders.png).
- Independent review caught paid/refunded/non-COD transition choices; fixed with regression tests and reviewer confirmed resolved.
- Automated client tests cover shared session, refresh/logout races, role landing, uploads, checkout retries, mounted routing/proxy, query filtering and payment-aware actions. Runtime/syntax/whitespace checks and backend regression results are recorded in the plan ledger after completion.

Fixture order/customer/product rows removed after browser verification. Test servers stopped when all verification finished. No commit or push.

## Commit description

### Follow-up: account switch and Back/Forward

2026-11-07: tested Admin login → Orders → logo storefront → logout → Customer login → Back three times → Forward. The original escalation did not reproduce on the unified browser: every history step stayed/redirected to /index.html with Eleanor Pena, never Michael Scofield. Direct /admin/orders.html also redirected to storefront and retained Customer.

Review did reveal that Admin DOMContentLoaded authorization alone did not handle a restored Back/Forward cache document (unlike storefront's existing pageshow handler). Regression tests reproduced the missing pagehide masking and persisted pageshow reload handlers (2 failed before fix). Admin now hides its document on pagehide, and keeps it hidden while reloading on persisted pageshow, forcing current-session authorization before old protected DOM can reappear. A normal pageshow does not reload. A separate shared-client regression rejects a late old Admin response after another document stores a Customer session.

Retested the browser sequence after the fix: Back1/2/3 and Forward all showed Customer with no old Admin identity; direct Admin Orders redirected. Live API checks on test backend: Customer me200, Admin list/detail403, anonymous Admin list401, logged-out Admin refresh401. Final checks:46 Node test-runner scenarios +6 admin adapter +6 product editor =58 passed; syntax/whitespace checks passed. No Java/API logic changed in this follow-up. Test servers stopped; no business data changed. Browser proof: [Customer after attempted Admin navigation](screenshots/history-role-guard.png). Cached pages from before this fix should be closed/reopened or reloaded to load the new guard.

Follow-up commit:

```text
fix(auth): revalidate cached admin pages after account switches

- Hide protected admin DOM when leaving the page and reload cached history restores
- Add history lifecycle and cross-document stale-session regressions
```

```text
feat(web): unify role-based sessions and integrate admin order management

- Host storefront and admin on one origin with shared login and role-aware landing
- Preserve staff sessions when navigating between admin and storefront
- Connect admin order filters, detail snapshots, status updates and COD collection
- Handle version conflicts and uncertain updates without silent mutation retries
- Add shared-client/routing regressions, launcher and verification documentation
```
