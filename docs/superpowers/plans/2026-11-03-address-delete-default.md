# Delete addresses and select default implementation plan

Goal: Active CUSTOMER can DELETE owned address and PATCH its default. Missing/foreign404, invalid ID400. User chose oldest remaining ID as replacement when deleting a default; last address deletion leaves empty list. Existing orders retain independent snapshots.

Architecture: Reuse user-lock-first transactions and existing partial unique default index. Flush deletion before promoting a replacement so SQL update-before-delete ordering cannot violate uniqueness. PATCH excludes the managed target from bulk default clearing. DELETE returns200 standard envelope with null data; repeated missing DELETE404. PATCH requires no body and returns saved address; repeat same target200 unchanged. No migration/version contract; last successful default command wins.

- [x] Add API/security/ownership/default/id regressions; observe RED.
- [x] Implement delete/default services, controller mappings and customer-only route guards.
- [x] Test default races, delete/default races, create/delete cap boundary, rollback and order snapshot preservation.
- [x] Full regression and review, docs/checklist, commit description; no commit/push.

Review focus: delete flush before promotion, exactly one default when nonempty, legacy nullable address fields allowed for default selection, no body on PATCH, old order and checkout retry survive removal of source address.

Ledger:14 API cases RED with13 missing-route failures and1 existing access pass; GREEN96 related cases. Added9 committed concurrency/rollback cases including checkout deletion race; strengthened saved-address order snapshot/retry regression. Final clean suite11 unit +606 integration =617 passed (23 new cases), zero failures/errors/skips. Independent review no important findings; optional explicit null-response assertion deferred. Diff check passed and address/checkout fixture/trigger cleanup zero. No migration, frontend changes, commit or push.
