# Forgot/reset password implementation plan

Goal: Public POST /api/auth/forgot-password and /reset-password with SMTP email, hashed single-use expiring tokens, non-enumerating forgot responses, throttling and immediate session revocation. User chose SMTP environment config and Mailpit local.

Architecture: V15 reset-token rows (SHA256 only,32 random bytes,15-minute expiry, issuing user version, used/revoked state) and hashed rate buckets. Active CUSTOMER recovery only, matching this feature area; blocked/missing/noncustomer forgot returns same200. New accepted request revokes older links. Reset uses user-before-token locks, matching login/refresh/password changes, increments token version and revokes all refresh tokens atomically. Password-change version invalidates pending reset links automatically.

Defaults: email3 and IP20 per15 minutes,60-second email cooldown; reset IP50 per15 minutes. Rate consumption commits independently so failed reset attempts still count. Forgot throttling returns same200; reset throttle429 independent of token validity. Trust request remote address, not unverified forwarded headers. Reset invalid/expired/used/revoked/wrong-user-version/blocked account all generic400. Shared existing6-character/72-UTF8-byte password policy, confirmation and different-new-password rule.

SMTP: Spring mail with bounded timeouts. After-commit bounded in-memory asynchronous dispatch keeps SMTP outside DB locks and request timing; raw token only in memory/email, never DB/log/API. SMTP failure is generic/logged without recipient/link, no durable retry guarantee; user requests a new link after cooldown. Queue bounded50,2 workers. Reset URL from trusted config, no request Host; HTTPS except loopback HTTP, fragment token. Backend public reset form makes local link usable while storefront is absent; configurable external FE URL remains supported. Mailpit compose optional loopback-only ports1025/8025. No automatic login after reset.

- [x] API/mail-capture tests for validation, privacy and single-use behavior; behavioral RED UTC/order regressions observed.
- [x] V15, request DTOs, clock/config, independent DB limiter and commit-before-SMTP dispatcher.
- [x] Implement forgot/reset and cleanup; ensure existing password-change/reset mutual invalidation.
- [x] Minimal backend reset form and SMTP/Mailpit docs/config.
- [x] Test expiry/rate boundaries, concurrent reset/forgot/login/refresh/change and rollback; real SMTP transport test.
- [x] Full backend/FE checks and independent review; update checklist, prepare commit description. No commit/push.

Review focus: invalid attempts count despite rollback, no stale reset after password changes, no token raw values in persistent state/logs, queue/SMTP failures do not reveal account presence, row locks user-before-token, no conditional response timing due SMTP, cleanup bounded and leaves active tokens.

Ledger:Initial test scaffold had a var declaration compile error, then SMTP mock health-check bootstrap error; corrected both, without claiming missing-route RED. First meaningful suite20/21 exposed a7-hour UTC JDBC discrepancy; explicit LOCAL_DATE_TIME bindings fixed it. Account lookup/issuance moved entirely off request timing. Facade NOT_SUPPORTED avoids outer/REQUIRES_NEW connection pool exhaustion; quota commits before atomic consumer transaction. Review found accepted-order ambiguity: forced two blocked issuance jobs reproduced2 emails vs expected1, then Permit(bucket,window,sequence) guard fixed stale requests without schema changes. Follow-up review no concrete regression. First full701 passed; final25 unit +682 integration =707 passed (44 new cases), zero failures/errors/skips. JS7 reset-page +12 existing scenarios passed. Real SMTP socket transport verified; Mailpit configured but not launched (Docker unavailable); no external inbox delivery claimed. Diff clean, fixture/trigger cleanup zero, V15 test-only application verified. No commit/push.
