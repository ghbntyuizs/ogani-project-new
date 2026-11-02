# Backend fixes and admin API integration

## Intent
Fix the confirmed backend defects first, then make the existing admin catalog screens work with the existing REST API. Keep Spring Boot, PostgreSQL, Flyway and the current Bootstrap HTML/JavaScript UI.

## Approach proposed for approval
Incremental repair and integration is recommended because auth and catalog already exist. Rebuilding the frontend adds migration work without resolving backend correctness. Implementing every missing commerce/content API at once would mix new business requirements with this repair effort.

## First delivery
- Establish an executable Maven/JDK test workflow using existing installations where possible. Never run tests against the development database or reset existing data.
- Make refresh-token consumption atomic, reject refresh for inactive accounts, and test concurrent consumption and account state.
- Require a configured JWT secret outside an explicit local development setup; keep test configuration self-contained and document startup changes.
- Sort and filter products consistently by effective selling price, with deterministic pagination ordering.
- Return an empty cart without creating a database record on GET; use the common success envelope and avoid per-item lazy product queries.
- Add appropriate handling for optimistic-lock conflicts and fix confirmed validation/contract inconsistencies encountered in these paths.
- Preserve existing migrations; add new migrations only if required. Correct the README merge markers and inaccurate setup instructions.
- Build a shared admin API client and session handling, then connect login, category CRUD, product CRUD and gallery operations with role-aware controls, validation, loading, empty and error states.
- Match form payloads to API names and enum values. Replace unsafe user-input HTML interpolation with text nodes.
- Resolve the file/URL mismatch for product images through a documented, validated local upload endpoint if no existing upload mechanism is found. Store uploaded content outside source/static code directories and expose only generated image identifiers.

## Boundaries
No framework migration, storefront reconstruction, payment integration or new order/checkout/content/customer-management APIs in this delivery. Existing pages without APIs must clearly indicate unavailable functionality rather than simulate successful persistence. The storefront source is absent from this checkout.

## Verification
Backend regression tests must cover the changed behaviors; integration tests use a dedicated test database. Exercise admin authentication and catalog operations against the running API where the environment permits. Report any verification blocked by missing dependencies or services explicitly. Do not claim production readiness based on static inspection.

## Implementation choices
Use the current checkout and preserve unrelated edits. Implement sequentially in this chat. Keep token persistence minimal and avoid rendering backend or user strings through innerHTML. Upload limits, permitted image types and storage configuration will be documented and tested during the upload task.
