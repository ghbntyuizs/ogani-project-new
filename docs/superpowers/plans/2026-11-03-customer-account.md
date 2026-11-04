# Customer account implementation plan

Goal: PATCH /api/users/me updates only the logged-in active CUSTOMER's firstName/lastName/phone. PUT /api/users/me/password verifies current password, validates/compares new/confirmation, hashes it and revokes every access/refresh session, per user choice.

Architecture: Reuse user-first locks. V14 adds token_version default0; JWT ver claim compared with current user on every authenticated request. Legacy signed tokens lacking ver work only at version0. Password change increments version and revokes refresh rows atomically. Login locks user before verifying credentials; refresh discovers owner scalar, locks user then token to serialize against password change. Profile returns safe DTO; existing /auth/me receives actual fullName.

Contract decisions from existing model: first/last max60, normalized whitespace, phone same Vietnam format as addresses and nonunique (schema has no uniqueness). PATCH omission unchanged, explicit phone:null clears; names null/blank invalid, empty patch/unknown or protected fields400. Password policy stays compatible with register: >=6 characters, <=72 UTF-8 bytes, exact strings (no trim), confirmation exact, new must differ. Wrong current400; no password/hash in response. Security routes CUSTOMER only; account lock/role/version rechecked from DB. In-flight requests already authorized are not retroactively undone.

- [x] API tests for profile validation, ownership/protected fields, password rules and immediate token revocation; observe RED.
- [x] Implement V14, strict DTOs/controller/service and safe response.
- [x] Update JWT and auth lock ordering; prove old tokens fail and new credentials work.
- [x] Committed password-vs-refresh/login/change/profile races and rollback tests; full backend/FE regression.
- [x] Independent review, docs/checklist and commit description; no commit/push.

Review focus: no token resurrection after rotation, no reverse user/token locks, stale JWT cannot reauthenticate after change, UTF-8 bcrypt limit, null vs omitted PATCH, no mutation to order/address snapshots, no protected fields accepted.

Ledger:29 API RED missing-route failures, GREEN29 API +1 refresh concurrency +11 unit. First full659 passed. Review found registration's existing UTF-8 byte limit defect: added two failing regressions (registration500/login401), then shared guard for register/login/password change; preserve AuthenticationManager's missing-user dummy checking while issuing sessions only from locked user. Final clean18 unit +645 integration =663 passed (46 new cases), zero failures/errors/skips. FE12 scenarios passed; follow-up review no concrete findings; diff clean and account fixture/trigger cleanup zero. V14 test-only application verified. No commit/push or frontend screen changes.
