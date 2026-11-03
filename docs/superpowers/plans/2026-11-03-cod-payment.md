# COD Payment Implementation Plan

Goal: Implement the user-approved payment-status design, COD UNPAID to PAID only while SHIPPING, with immutable confirmation audit on retry.

Architecture: Reuse active Admin/Staff validation and buyer-before-order locks. Add V12 nullable paid_at and payment_confirmed_by columns. Expose audit in PATCH and admin detail. No payment reversal or automatic completion.

- [x] Write tests and observe missing-route failures.
- [x] Implement strict request, migration, transactional service, response and controller.
- [x] Verify access, states, retries, concurrency and existing backend regressions.
- [x] Review and document results; prepare commit description without committing.

Review focus: non-COD paid requests rejected; retry after completion unchanged; stale version rejected; lock ordering compatible; historical paid records may have null audit.

Ledger: 16 missing-route RED failures; 91 targeted GREEN cases. Final 11 unit +530 integration =541 tests passed, including 23 new cases. Independent reviewer found no critical/important issues; suggested optional additional parser input coverage. Diff check passed and fixture/trigger cleanup returned zero. No commit/push or frontend changes. Migration applied to test database only.
