# Backend and admin integration verification — 2026-11-02

## Delivered
- Refresh-token rotation uses row locking; blocked accounts cannot refresh. Concurrent refresh/replay is covered against PostgreSQL.
- JWT secret required by default; explicit local profile provides development defaults.
- Product filtering and sorting use effective selling price, with ID tie-break ordering. Category fetching avoids per-product category lazy queries.
- GET cart is read-only, returns the common success envelope, and fetches item products together. Optimistic-lock conflicts return 409.
- Validated PNG/JPEG upload and public image retrieval; generated filenames, pixel/size limits, raster rewriting, durable Docker upload volume.
- Shared admin API client, tab-scoped auth, single refresh per request group, copied-tab ownership protection using Web Locks.
- Real login/logout, category CRUD, product CRUD/search/filter/pagination, file uploads and gallery reordering/removal, with role restrictions and error/loading states.
- Unsupported admin pages are labeled previews and their persistence controls disabled. Unsafe demo innerHTML mutation removed.
- README merge markers and obsolete setup/testing claims corrected.

## Executed checks
- Baseline Maven unit suite: 2 passed.
- Regression failures observed before auth/cart and effective-price fixes; optimistic-conflict test observed HTTP 500 before handler addition.
- `mvn -o clean verify`: BUILD SUCCESS, 8 unit tests and 137 integration tests at that point.
- Final `mvn -o test failsafe:integration-test failsafe:verify`: BUILD SUCCESS, **10 unit tests and 138 integration tests**, zero failures/errors/skips. The final two unit tests and cart API integration test were added after the clean build.
- Frontend `admin/tests/api.test.mjs`: **6 scenarios passed**. Covered bearer headers, concurrent refresh, field errors, invalid sessions, image URL handling and copied-tab ownership rejection.
- All authored JavaScript parsed successfully. `git diff --check` passed (only Windows line-ending notices).
- Independent code review: category-upload identity race, thumbnail/gallery mismatch and copied-tab token conflict addressed; follow-up review found no additional important findings.

## Browser checks against the test database
- ADMIN login and API-loaded products/categories.
- Create product `UI-CHECK-20261102`, edit quantity to 9, reload and confirm persistence.
- Create category `ui-category-check-20261102`, upload sample JPEG and save.
- Upload two product gallery images, reorder, verify thumbnail URL equals first gallery URL; standalone thumbnail picker disabled.
- Logout, log in as STAFF; category Add disabled and Delete controls absent, product Hide controls absent.
- Server pagination moves from page 1 to page 2; selling-price sort and keyword search (`banana`) return expected products.
- Current product-page browser console reports no errors. Screenshot: `screenshots/admin-api-connected.png`.

## Boundaries and environment
Backend/browser checks used **ogani_store_test**, never the development database. Browser fixtures above remain in the test database for inspection, with their uploaded sample images in ignored local uploads storage. Tests themselves roll back changes except their explicitly cleaned concurrency/upload fixtures.

Existing Maven 3.10.0 and Microsoft JDK 21 were used, compiling for Java 17. No dependency upgrade or global tool installation was needed. Test server processes are stopped after verification.

Docker configuration was reviewed but not built/run here. No payment, checkout/order/content APIs or missing customer storefront were implemented in this delivery. Existing V2 migration still seeds demo accounts; production seed/credential preparation remains separate. Uploaded images without DB references currently require manual cleanup. Copied-tab protection requires Web Locks (current Chromium on localhost/HTTPS).
