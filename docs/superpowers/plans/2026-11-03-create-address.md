# Create Customer Address Implementation Plan

Goal: Approved POST /api/addresses, active CUSTOMER only, maximum 10 addresses, first address automatically default, atomic replacement of default.

Architecture: User row lock serializes count/default changes with checkout. V13 adds nullable district/ward and matching immutable order snapshots, normalizes old default flags deterministically and adds a unique partial default index. Existing address/checkout JSON remains compatible with additive fields.

Spec: User approved design in chat: recipient max120, city/district/ward required max80 each, detail max255, Vietnamese phone validation and normalized spaces. Default country Vietnam. Country/state/postcode retained for compatibility; new API creates Vietnam addresses.

- [x] Write API validation/ownership/default/limit tests and observe RED.
- [x] Implement DTO/controller/service/repository and schema, propagate district/ward through checkout snapshots and responses.
- [x] Add committed concurrency/default/limit tests and checkout integration coverage.
- [x] Run full backend regression, independent review, update docs/checklist and prepare commit message. No commit/push.

Review focus: stale-role users, guest/foreign address ownership, default switching failure rollback, legacy nullable district/ward, concurrency at count9 and first creation, no modification of historical orders.

Ledger:15 RED missing-route failures;56 targeted GREEN cases. Clean build exposed applied V12 absent from current branch (HEAD d17910d); do not restore unrelated source or alter shared DB history. Created isolated ogani_address_test and ran fresh migrations through V13:11 unit +534 integration =545 passed, including27 new cases. Independent reviewer found no important issues; noted optional legacy-default migration fixtures, checkout-vs-create race and positive landline cases. Diff check passed; fixture/trigger cleanup zero. No commit/push or frontend changes.
