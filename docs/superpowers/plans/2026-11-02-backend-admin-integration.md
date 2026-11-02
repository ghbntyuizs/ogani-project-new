# Backend fixes and admin integration implementation plan

> Execute inline with superpowers:executing-plans. User approved the linked design and requested implementation.

**Goal:** Repair backend correctness and connect the existing admin catalog UI.
**Architecture:** Keep Spring Boot/PostgreSQL and Bootstrap/vanilla JavaScript. Use a shared authenticated API client and focused page modules.
**Spec:** ../specs/2026-11-02-backend-admin-integration-design.md

## Constraints and review focus
Preserve existing data and migrations. Tests use a dedicated database. Cover token reuse/concurrency, inactive users, effective price ordering, empty cart reads, API failures, role restrictions and invalid uploads. Do not represent unsupported pages as working features.

## Tasks
- [x] Establish baseline with installed Maven/JDK; record environment limitations.
- [x] Auth: add regression tests for inactive refresh and replay; atomically consume tokens in RefreshTokenRepository/AuthServiceImpl. Isolate local JWT defaults.
- [x] Catalog/cart: add effective-price sorting and empty-cart regression tests; implement deterministic sorting, fetch plans, consistent response envelope and optimistic-lock error handling.
- [x] Images: test allowed/invalid uploads, add generated-name image storage and public read endpoint with explicit limits.
- [x] Admin: add shared API/session client, real login/logout, role guards and safe DOM helpers. Test refresh retry and failures.
- [x] Catalog pages: connect server search/pagination and CRUD; map product DTOs, categories and gallery uploads, with loading/error states.
- [x] Mark unsupported pages, remove demo mutations, repair README and document environment setup.
- [x] Run available backend and frontend checks and review final diff. Record blocked checks explicitly.

## Progress / rulings
- Existing checkout reused per approved spec; no unrelated tracked changes at start. No automatic commits or pushes.
- Baseline sandbox Maven cannot create its default repository; requesting normal installed-cache execution.
- Baseline: 2 unit tests passed. First auth/cart regressions reproduced blocked-user refresh and writes on empty-cart reads, then passed after fixes.
- Effective-price regression reproduced regular-price sorting. Existing sort assertion was updated to check effective selling prices to match the approved contract.
- Full suite before upload endpoint tests: 8 unit tests and 135 integration tests passed. New upload endpoint tests awaiting final clean verify.
- Frontend: 5 API-client scenarios pass via Node VM; all authored JavaScript parses. Browser confirmed real login, product creation and persisted product quantity after reload against ogani_store_test.
- Independent reviewer found category-upload identity race and thumbnail/gallery mismatch; both addressed. Final follow-up review requested.
- Ruling: keep existing Flyway seed migrations unchanged; document demo credentials/production seed limitation rather than silently modifying applied migrations.
- Ruling: use dedicated database profile for browser tests. Windows JAR locking interrupted overlapping packaging/UI work; stopped owned process and reran clean verify sequentially.
- Final checks: clean verify passed (8 unit + 137 integration at that point); final full test invocation passed 10 unit + 138 integration. Frontend API tests: 6 passed. Independent follow-up review clean for important issues.
- Browser confirmed category image upload, product gallery upload/reorder/thumbnail authority, logout/staff role controls, pagination and keyword search. See ../..//backend-admin-verification.md for evidence and limitations.
