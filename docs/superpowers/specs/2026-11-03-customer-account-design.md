# Customer profile and password design

User requested PATCH /api/users/me and PUT /api/users/me/password with owner isolation, validation, immutable role/status, current-password verification, different new password, confirmation and hashing. User explicitly chose immediate revocation of all access/refresh sessions and login again after password change.

Use existing firstName/lastName model for display name, max60 each, normalized whitespace, return fullName. Existing phone is nonunique; preserve that policy and use address-compatible Vietnam format. PATCH omission keeps values, phone:null clears, null/blank names invalid, empty/unknown/protected fields rejected. Response is a safe DTO. Current active CUSTOMER role is mandatory.

Use BCrypt policy compatible with registration (min6 characters and72 UTF-8 byte cap), no password trimming, confirmation exact, reject same password and wrong current password. V14 user token version makes every old JWT invalid; revoke every refresh row in the same transaction. Legacy versionless JWT maps to version0 only. Login/refresh/password writers must share user-first lock ordering, with token lock after user for refresh. Login must verify credentials while holding the user lock. Token-version overflow returns conflict rather than wrapping. Unrelated account/order/address data must not change.

No frontend, email change, phone verification or reset-password feature. Test input/security/byte boundaries, protected fields, real token revocation, legacy JWT, concurrent login/refresh/password/profile, and rollback after pending password/version writes. Independent review and full regression before completion.
