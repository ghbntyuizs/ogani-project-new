# Single login and synchronized logout

2026-11-07. User reported logging out in admin left the storefront logged in, and requested removing separate admin login. Both UIs already used one API client but tokens intentionally remained per-tab; no cross-tab logout signal existed. Two regression tests failed before the fix: another tab retained its session, and a cached tab with a missed logout notification remained valid.

## Result

- `/login.html` is the only login form, in the storefront. Admin/Staff sign in there and land at `/`; Customer lands at `/index.html`.
- The old admin HTML page and its auth script are physically deleted. `/admin/page-login.html` and `/page-login.html` are server-only redirects to `/login.html`; there is no fallback admin login page. Admin Logout anchors use `/login.html` with an explicit logout action rather than the deleted page URL.
- Explicit logout publishes an account-scoped random marker through localStorage. No token or password is stored there. Same-account tabs clear their own session, revoke their own refresh token with a keepalive request, and update/redirect their UI. A different account in another tab is unaffected.
- Session reads compare a durable logout stamp, so frozen/cached tabs also invalidate if they missed the storage event. Late refresh responses cannot restore a logged-out account. A fresh deliberate login captures the current stamp and survives delayed notifications.
- Back/Forward or visiting login without logout preserves a valid session and redirects according to role. Merely opening login never publishes logout.
- Synchronization applies to the same browser profile and exact origin. Use `http://localhost:5500` consistently; do not mix it with `127.0.0.1`, another port, or the legacy backend-hosted storefront. Per-tab refresh ownership remains isolated; this does not copy tokens between tabs or browsers.

## Browser verification

Isolated web15500 → backend18080 → ogani_store_test. No business records modified.

1. Old admin login URL opened the storefront login.
2. Signed in Admin in two separate tabs using the shared login; second tab navigated to storefront and displayed Michael Scofield.
3. Clicked real dashboard profile-menu Logout in first tab: it reached storefront login; second tab immediately showed guest storefront, login link visible, old admin identity absent.
4. Signed both tabs in again; visiting login while authenticated redirected to `/`; Back/Forward kept Admin signed in.
5. Logged out from the storefront tab: the dashboard tab immediately reached login with no protected content.
6. Customer login → visiting login → Back/Forward kept Eleanor Pena at `/index.html`; explicit logout then cleared it.
7. Both tabs' final browser console checks contained no errors. [Shared storefront login after synchronized logout](screenshots/shared-login-logout.png).

Automated checks:53 Node test-runner scenarios +6 admin adapter +6 product editor =65 passing. This includes six new logout transport/account/race/cache regressions and the legacy login redirect test. Syntax and whitespace checks pass. Independent read-only review found no important issues. Targeted backend static-resource integration tests:3 passed, zero failures/errors/skips, BUILD SUCCESS. No backend Java/business/auth policy changed. No commit/push. Isolated test servers were stopped after verification.

To load the fix: stop/restart `start-web.cmd`, close old tabs (they hold older scripts), and reopen `/login.html` on the same origin.

Further user report: old “Ogani Admin / Sign In to Dashboard” screenshot. Inspected live port5500: exact / returned Dashboard HTML, /index.html returned storefront and /login.html returned the shared login; none contained the old form text. Removed the previously retained redirect-only HTML file entirely and replaced all internal admin Logout hrefs with the shared login plus data-action=logout. Browser verification on the user's running5500: opening /admin/page-login.html landed at /login.html and displayed the storefront form. The screenshot's original tab/origin/cache state could not be established, so its precise cause is unconfirmed. Back into storefront retaining a logged-in Admin session is expected. Existing65 FE scenarios passed again; syntax/whitespace checks passed. [Verified live shared login](screenshots/removed-admin-login.png). No user development account was signed into or modified during this check.

## Commit description

```text
fix(auth): synchronize logout and use storefront login for all roles

- Remove the separate admin login form and redirect legacy URLs
- Propagate same-account logout across tabs without sharing tokens
- Invalidate cached sessions and reject late refresh responses after logout
- Preserve valid sessions across login-page and Back/Forward navigation
- Add logout, history and redirect regression coverage
```
