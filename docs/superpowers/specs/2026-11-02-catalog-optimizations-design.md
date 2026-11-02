# Catalog correctness and payload/image optimization

User authorized implementation of the six prioritized improvements after reviewing the existing code. Keep the current Spring Boot/PostgreSQL/Bootstrap stack and reuse this checkout without overwriting unrelated edits (including the user's active local profile setting).

## Approach
Extend the current catalog API and admin client together. Use the existing Product @Version field, targeted DTOs, generated local image variants and small scheduled maintenance jobs. No cache service, framework migration or broad entity restructuring.

## Contract
- Product detail/create/update responses include version. PUT requires the version the form originally read; missing version is 400, stale version is 409. Flush before mapping the saved response. Existing integration helpers explicitly read the current version to represent read-then-edit; dedicated regression tests send stale/missing values directly.
- Keep first-gallery-image thumbnail semantics. Add a gallery thumbnail-selection operation that moves the selected image first. Direct thumbnail upload on an existing product creates a gallery entry and selects it first, using the same gallery concurrency gate. The form must not silently accept a new product version after an image operation if another admin changed text fields.
- Return gallery images, thumbnail and product version in one admin gallery state response. Preserve existing gallery endpoint response shapes for compatibility; expose a dedicated state endpoint and have the admin use it to remove its extra product-detail request.
- Product list responses omit long descriptions/information/shipping content. Detail responses retain them. Use a JPA projection so the list SQL does not load these columns. Preserve list fields used by existing admin/public tests.
- New local uploads produce original PNG plus an aspect-preserving variant with longest edge 320 pixels. List/gallery preview rendering requests the variant, while legacy/external images keep their URLs.
- Register new uploads in a new migration/table. Claim managed uploads transactionally under row lock when product/category/gallery writes reference them. Cleanup may remove only registered uploads older than 24 hours that have never been claimed, holding the same row lock so attachment cannot race deletion. Legacy and ever-claimed uploads are deliberately retained. This is conservative cleanup, not full media-library garbage collection.
- A configurable scheduled maintenance job removes expired/revoked refresh tokens and invokes bounded unused-upload cleanup; tests disable automatic scheduling and call operations explicitly.
- Sort categories in the database with an ID tie-breaker. Remove only dependencies/imports/comments made obsolete by these changes. Use imports at the top of Java classes per user preference.

## Verification
Regression tests for stale writes, missing version, response version progression, list payload/projection, thumbnail selection ownership, image resizing/validation, cleanup retention/reference/race behavior and token pruning. Run the existing suite on ogani_store_test. Test frontend version handling and gallery operations, and exercise the browser where available. Never stop or reconfigure the user's running application to free a port; use dedicated test ports.
