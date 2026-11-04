# Customer profile and password APIs

Both endpoints are for the authenticated active CUSTOMER, deriving owner exclusively from Security Context. Admin/Staff403, anonymous/invalid/revoked/stale access token401, inactive401. Current DB role/status/session version are rechecked under the user lock. Neither endpoint accepts a target user ID.

## Partial profile update

```http
PATCH /api/users/me
Authorization: Bearer <customer-token>
Content-Type: application/json
```

```json
{"firstName":"Nguyễn Văn","lastName":"An","phone":"+84901234567"}
```

Success200: safe DTO containing id, email, firstName, lastName, fullName and phone. Email is read-only. Names follow the existing split DB model (max60 each), normalize repeated whitespace, and cannot be null/blank if supplied. Phone uses the same Vietnam mobile/landline format as addresses; +84 normalizes to domestic0. Phone is not unique, matching existing schema. No duplicate-phone constraint is introduced.

Omitted fields remain unchanged; explicit `phone:null` removes the phone. Empty patches, malformed JSON values and unknown/protected fields such as role, status, id, userId, email and passwordHash return400. Even mixed allowed/protected payloads fail without applying changes. Profile edits do not change password, role, status, email, addresses or historical order snapshots. Existing `GET /api/auth/me` now returns the current fullName instead of null; PATCH returns the detailed profile, including phone.

## Change password

```http
PUT /api/users/me/password
Authorization: Bearer <customer-token>
Content-Type: application/json
```

```json
{"currentPassword":"oldPassword123","newPassword":"newPassword456","confirmPassword":"newPassword456"}
```

Required strings only; no extra fields. New password minimum6 characters, matching registration policy, and maximum72 UTF-8 bytes for BCrypt. The byte limit also applies to registration/login through a shared guard (overlong input400). It must differ from the current password, match confirmPassword exactly and not be blank. Passwords are not trimmed or normalized. Wrong current password, mismatched confirmation and invalid lengths return400 without changing credentials or sessions. Success200 has null data and asks the client to sign in again; no raw password/hash is returned, and password DTO toString is redacted.

Per the user's choice, all existing access and refresh tokens, including the caller's session and other devices, are invalid after success. FE must clear the session and go to login; do not automatically retry this request with old credentials. If a success response is lost, the original access token is revoked; login with the new password to determine the outcome. The API does not return replacement tokens.

## Session and transaction design

V14 adds users.token_version default0 with a nonnegative constraint. Issued JWTs include ver; authentication compares it with the current user row. Legacy signed tokens without ver are accepted only while the user version is0, so they too stop working after the first password change. Malformed/negative/fractional/non-numeric/overflow claims are rejected. Existing non-password profile changes do not increment the token version.

Password hash, version increment and all refresh-token revocations commit atomically. Login takes the user lock before credential validation; refresh discovers a scalar user ID before locking user then token, matching password-change order. Concurrent refresh/login cannot leave a surviving old session. A second concurrent password command using the old session fails401. Partial profile updates serialize and preserve independently updated fields. Requests already authorized and executing before revocation are not retroactively undone.

Restart backend to apply V14. The development database is not modified by tests. Existing local profile preferences are preserved. Forgot/reset password, phone ownership verification, email change and frontend screens remain outside this feature.

Verification:29 API regressions failed before routes existed;29 API + existing refresh-concurrency case and11 unit tests passed afterward. First clean full suite18 unit +641 integration =659 passed. Independent review identified the existing registration UTF-8 byte-limit gap; two regressions reproduced registration500/login401 instead of validation400, then shared byte validation fixed both. Added exact72-byte and real legacy-token revocation checks. Final clean suite18 unit +645 integration =663 passed, zero failures/errors/skips, including46 new cases (33 account API,6 committed concurrency/rollback,7 JWT unit). Both existing FE suites passed12 scenarios. Follow-up independent review found no additional concrete issue. Diff check passed; account fixtures and injected triggers cleaned (zero remaining entries). V14 applied successfully on ogani_store_test; no development-data changes, commit or push.
