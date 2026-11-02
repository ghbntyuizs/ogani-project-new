# Catalog optimization verification — 2026-11-02

## Implemented
- Product detail/create/update expose `version`; product PUT requires the version originally read and returns 409 for stale forms. Hibernate optimistic locking remains the final concurrent-write guard, and updates flush before returning the next version.
- All gallery mutations, including original endpoints, require the numeric `If-Match` version. A product row lock serializes gallery changes and every mutation advances its version, including secondary-image edits.
- New gallery state responses include images, thumbnail and version. The admin consumes these responses directly instead of fetching product detail after each gallery operation.
- Gallery images have **Set as thumbnail** controls. Choosing a file directly in Thumbnail on an existing product inserts it first in the gallery and saves immediately. Unsaved product text is preserved for Save.
- Product list DTOs omit long content; a closed JPA projection excludes those columns from SQL. Detail responses retain full content.
- New uploads receive aspect-preserving previews with longest edge 320 px, without upscaling. Generated media responses allow immutable browser caching. The client uses previews for managed images and preserves external/legacy image URLs.
- V5 registers new uploads. Successful product/category/gallery references claim uploads in the same transaction, using row locks shared with cleanup. Hourly cleanup removes at most 100 never-claimed uploads older than 24 hours and at most 1000 expired/revoked tokens. V6 adds token maintenance indexes. Automatic jobs are disabled in tests.
- Category sorting now runs in SQL with an ID tie-breaker. Removed the unused cart UserRepository dependency and obsolete comments/imports. Preserved the user's active local-profile setting.

## Verification evidence
- Final backend command: `mvn -o test failsafe:integration-test failsafe:verify` using installed Maven/JDK, real dedicated PostgreSQL database `ogani_store_test`.
- **11 unit tests and 153 integration tests passed**; zero failures, errors or skipped tests.
- Regression failures reproduced before changes: missing/stale version behavior, long list payload, missing gallery state routes, missing thumbnail URL variant and missing editor implementation.
- Review regressions reproduced legacy gallery mutation bypass and external-image URL misclassification. Fixes passed the complete suite, and independent follow-up inspection found no additional important issues.
- SQL capture verifies public/admin product-list SELECTs do not include `.description` or `.information`.
- Concurrent upload-claim test holds a row lock while cleanup runs; cleanup skips the in-flight claim and retains the file after claim commits.
- Frontend API suite: **6 scenarios passed**. Product-editor suite: **6 scenarios passed**, including current version propagation, gallery state consumption, no version adoption from a newer GET, conflict locking and concurrent-action rejection. Authored JavaScript parsed and the import-style scan found no inline qualified type references.

## Browser smoke checks
Isolated API/UI ports **18080/15500**, using copied compiled classes and a staged admin directory; no user server was stopped or reconfigured.

1. ADMIN logged in and created `OPT-UI-20261102` (product 168 in the test database).
2. Uploaded two gallery photos, selected a thumbnail through **Set as thumbnail**, then replaced it directly from the Thumbnail file input while editing stock.
3. Saved stock 13 and reloaded. Stock persisted, thumbnail URL matched the first gallery image, and preview URLs requested `?size=thumb`.
4. A separate STAFF session saved stock 14. The old ADMIN form tried to save 99 and displayed a conflict with Save/media actions disabled. Reload showed 14, proving the newer save was retained.
5. Current browser console contained no errors. Screenshots: `screenshots/catalog-thumbnail-optimized.png` and `screenshots/catalog-gallery-optimized.png`.

## Usage and limits
Restart the backend to apply additive Flyway V5/V6 migrations, then hard-refresh the frontend to load the new editor module. Product PUT and legacy gallery mutations now require version information; external API clients must update as documented in README.

Set `APP_UPLOAD_PUBLIC_ORIGIN` for an externally hosted API; relative managed URLs are preferred. Absolute URLs from other origins remain external and do not require local files. The cleanup registry deliberately does not backfill legacy files and never deletes an upload that has ever been claimed. It does not garbage-collect detached historical media.

Docker settings were updated but Docker was not built/run here. No timing/throughput benchmark was performed; the measured checks prove reduced SQL columns, smaller preview dimensions and fewer client requests, not a claimed percentage speedup. Browser fixture/media remain in the test database/ignored smoke upload directory for inspection. Test servers were stopped after verification; source changes are uncommitted.
