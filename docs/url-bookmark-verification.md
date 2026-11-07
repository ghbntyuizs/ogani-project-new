# Login URL and bookmark audit

2026-11-07. Audited project references after physically deleting admin/page-login.html and admin/assets/js/auth.js. This verifies supported legacy bookmark destinations via server redirects; it does not edit a browser's saved bookmarks.

## Results

-25 HTML pages:61 unique local href/src destinations, zero broken responses.
-11 JavaScript module-reference destinations, zero broken responses.
-No active admin/storefront HTML or JS refers to the deleted login page or script. Historical documentation and compatibility tests intentionally retain the old URL names.
-All admin Logout anchors use /login.html and data-action=logout; the common logout handler still revokes sessions before navigation.
-Found /admin returned404. Added canonical redirects for /admin, /admin/ and /admin/index.html to /. Query parameters remain on the same origin.
-Found a doubled slash in legacy /shop//... could form an external redirect authority. Normalized leading slashes; regression verifies redirect origin stays local. No external request was made by that test.

| Bookmark / URL on localhost:5500 | Result |
|---|---|
| /login.html | Shared storefront login; session validation determines role navigation |
| /page-login.html |302→/login.html |
| /admin/page-login.html |302→/login.html; also handles legacy query strings by dropping them |
| /admin |302→/ |
| /admin/ |302→/ |
| /admin/index.html |302→/ |
| /shop/ |302→/index.html |
| /shop/login.html |302→/login.html |
| /shop/assets/api.js |302→/assets/api.js |

Protected destinations still pass through the existing session/role guards. Redirect URLs do not create or restore an account. Login is shared, and no deleted form remains.

Verification: observed new bookmark regressions RED→GREEN;62 Node test-runner scenarios +6 admin adapter +6 product editor =74 passed. Syntax/whitespace checks passed. Existing auth/history/logout regressions included. No backend/DB changes, no user browser bookmark mutations, no commit/push.

Restart the running frontend server to load changed redirect rules: Ctrl+C, then `.\start-web.cmd` from the repository root.

Suggested commit:

```text
fix(web): normalize legacy login and admin bookmark redirects

- Redirect admin entry bookmarks to the canonical dashboard
- Keep legacy login and storefront aliases on the shared routes
- Prevent external-host redirects from malformed legacy paths
- Verify internal links, modules and route regression tests
```
