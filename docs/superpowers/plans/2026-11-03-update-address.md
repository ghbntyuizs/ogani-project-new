# Update customer address implementation plan

Goal: PUT /api/addresses/{id} fully updates supported Vietnam address fields for the active owning CUSTOMER. Same validation/body as create. User explicitly chose to preserve the default when false is sent for the current default. true switches defaults atomically. Missing and foreign addresses both404. Existing order snapshots unchanged.

Architecture: Reuse create request validation and user lock before loading the owned address. Clear default flags on other addresses only, excluding the updated row, to avoid stale managed-entity flags. Reuse response mapping. No migration or new version requirement. Concurrent valid edits serialize; the last committed update wins.

- [x] Add API/default/ownership/validation regressions, observe missing-route RED.
- [x] Implement update service/controller/customer security and default-excluding-target query.
- [x] Verify create/update concurrency, rollback, saved-address reads and checkout snapshots.
- [x] Run regression/review, update checklist/docs and prepare commit description without committing.

Review focus: unchanged default updates must not clear their own flag, false cannot remove the sole default, updates allowed at count10, missing/foreign IDs indistinguishable, checkout sees coherent old/new address and old orders never change.

Ledger:17 initial API cases,16 RED missing-route failures,1 existing access check passed. GREEN68 related cases. First full suite11 unit +581 integration =592 passed. Independent read-only review found no important issue; added explicit legacy no-default repair and preserved country/state/postcode regressions. Final suite11 unit +583 integration =594 passed, zero failures/errors/skips;23 new tests. Diff check passed and fixture/trigger cleanup zero. No migration, commit or push.
