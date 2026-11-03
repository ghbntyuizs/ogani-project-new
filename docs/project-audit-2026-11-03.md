# Project audit — 2026-11-03

Scope: clean compile and all backend tests; source review of authentication/security/catalog/order/user service paths, DTOs, repositories, current migrations and cross-feature mappings; custom frontend JavaScript syntax and API/editor regression scenarios; local HTML/CSS reference checks. Binary images and vendored libraries are inventoried/reference-checked, not manually reviewed as application logic. No browser interaction, dependency vulnerability scan or deployment/load test is claimed.

## Confirmed issues and changes

- AdminOrderQueryService had a duplicated constructor fragment after combining payment audit and address snapshots. Fixed immediately before this audit; both payment audit and district/ward are preserved. Its targeted83-case regression passed.
- Product create/update accepted a normalized empty slug, e.g. `slug="!!!"`, making the public product URL unusable. Three new regressions reproduced201/200 instead of400; both service paths now reject it before persistence, preserving original products on failure.
- README and checklist still described implemented order/address functionality as pending. Updated to match current source/test coverage. Address documentation now reflects restored V12 and the payment implementation currently present.

## Verification

- Initial combined backend clean suite:11 unit +557 integration passed.
- Slug regressions RED:3 failures before fix.
- Frontend:API client6 scenarios and product editor6 scenarios passed via their exported run functions (plain node --test does not execute these exported scenarios).
- Syntax:all7 custom JS files checked with node --check.
- Static references:78 admin files inventoried,16 HTML pages plus CSS scanned; zero missing local asset/link targets.
- Independent read-only backend reviews found the slug issue; no additional confirmed order/user mapping or locking issues.
- Final clean combined suite:11 unit +560 integration =571 passed, zero failures/errors/skips. All13 migrations validated; git diff --check passed. Unique address/status fixtures and injected triggers were cleaned (zero remaining entries).

## Remaining scope and local configuration

- Frontend auth/catalog are connected. Order and other admin pages remain explicitly marked previews. Customer storefront source is absent; a complete end-to-end customer browser flow cannot be verified from this checkout.
- Local profile activation is an explicitly preserved user setting recorded in the earlier catalog plan. It enables a development JWT secret; deployment must use a non-local profile and a configured JWT_SECRET as described in README. This audit does not overwrite the local preference.
- No commits, pushes, DB repair/history edits or development-data mutations were performed. Backend tests use ogani_store_test. The prior isolated address test database remains available.
