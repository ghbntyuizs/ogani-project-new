# Unified web and admin orders implementation plan

Goal: implement the user's specified shared origin/session, role-based landing, admin logo storefront navigation, and live order management. Preserve all existing customer/catalog operations and backend authorization.

Architecture: one dependency-free Node server mounts storefront at root, admin under /admin/, and serves the admin dashboard for exact /. Dashboard includes a base URL so its relative links work. /index.html always storefront. /shop/ remains an alias for backend compatibility. A common ES-module API client owns one per-tab session, refresh lock and session epoch. Admin facade reuses that client (including multipart uploads). Login accepts all existing roles and routes ADMIN/STAFF to /, CUSTOMER to storefront. Staff storefront access is browse-only; customer APIs remain CUSTOMER-only without logging staff out. Backend /shop remains available; admin pages remain Node-served.

- [x] RED: shared role/login/session/refresh/upload, mounted server and order transition/query tests.
- [x] Implement shared API roles/session and admin adapter; role-aware storefront guard/nav/login, admin root/logo/guards.
- [x] Implement unified server with containment and MIME types (fonts included), no directory listing or source exposure.
- [x] Replace admin order samples with safe API list/filter/pagination and detail/snapshots/history/status/COD payment UI; version conflict reload, no silent mutation retry.
- [x] Run JS/BE checks, isolated browser role navigation/order workflow, one independent review; fix observed issues.
- [x] Update README/run docs/results/commit text. No commit or push.

Ruling: no approval pause — user specified concrete routes, roles and same-session navigation and authorized implementation. Keep existing uncommitted storefront work. No worktree switch because current work depends on those files.

Ledger: 2026-11-07 — all tasks implemented inline. RED→GREEN observed for missing roles module, shared session/multipart, mounted root routes/proxy and payment transition regressions. Final frontend checks:35 storefront/shared/server +7 password reset +6 admin adapter +6 editor =54 passed. Backend:25 unit +685 integration =710 passed, zero skips/failures/errors, BUILD SUCCESS. JS syntax and Git whitespace checks passed. Browser evidence and limitations recorded in docs/unified-web-verification.md. No commit/push.
