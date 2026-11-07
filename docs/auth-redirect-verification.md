# Admin login redirect verification

2026-11-07: user reported repeated / ↔ /login.html navigation after Admin login. Fresh isolated browser login did not reproduce the exact reported oscillation. Inspection found a concrete auth-startup flaw: storefront auth pages ignored current-user verification errors and then redirected based on cached role. Admin and storefront also loaded the common API module through different URL aliases. Live5500 copies initially had identical source hashes; an actual stale-cache/version mismatch on the user's original browser was not established.

## Changes

- Both entry points use `api.verifySession()` before deciding role navigation.401/403 or a mismatched/invalid current-user response clears the rejected cached session. Network/5xx failure preserves the session and shows an explicit retry state on auth pages, with no automatic redirect.
- Verification uses backend role, not just cached role. Epoch/user guards prevent an old403 response from clearing a newer login. A legitimate token refresh followed by403 clears the rejected session immediately.
- Admin now imports the same canonical `/assets/api.js` URL as the storefront. The Node server redirects legacy `/shop/` paths to canonical storefront/module URLs, preserving query parameters and no-store headers. This does not change backend-hosted /shop resource handling.
- No backend account, role, credential or business logic changed.

## Evidence

- Six new API verification tests and one canonical-route test, observed RED→GREEN. Existing suites:60 Node test-runner scenarios +6 admin adapter +6 editor =72 passed. JS syntax and Git whitespace checks passed.
- Independent reviewer caught stale403/new-login race; fixed with regression. Reviewer confirmed no deterministic redirect loop in reviewed startup paths; legitimate refresh403 case also tested and corrected.
- Browser web15500→backend18080→ogani_store_test: Admin login reached dashboard; opening login three separate times redirected to / and remained on dashboard. No console errors in that normal sequence.
- Stopped only the isolated backend18080, then opened login with cached Admin session: page stayed at /login.html with account-connection error and Retry, instead of automatically navigating to /. Restarted test backend and clicked Retry: dashboard restored with the same account. Logged out afterward. No business data mutated.
- [Stable dashboard after recovery](screenshots/admin-redirect-stable.png).

The user's existing server on5500 was not stopped. Restart it to load the server routing change: Ctrl+C, then from repository root `.\start-web.cmd`. Close old tabs and reopen http://localhost:5500/login.html so the browser loads the updated auth modules. Test servers were stopped after verification. No commit/push.

## Suggested commit

```text
fix(auth): validate sessions before role-based redirects

- Use verified backend identity and role in storefront and admin guards
- Clear rejected sessions and show retry for temporary verification failures
- Protect newer logins from stale auth responses
- Canonicalize shared client URLs and add redirect regression coverage
```
