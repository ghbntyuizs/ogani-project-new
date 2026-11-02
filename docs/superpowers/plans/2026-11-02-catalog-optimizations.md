# Catalog optimization implementation plan

> Execute inline using executing-plans and test-driven-development. Implementation already authorized in this chat.

**Goal:** Prevent stale product edits, improve thumbnail controls and image payloads, reduce list/gallery costs and bound unused credentials/uploads.
**Spec:** ../specs/2026-11-02-catalog-optimizations-design.md
**Architecture:** Existing Java 17/Spring Boot, PostgreSQL, MapStruct and vanilla JS. Top-of-file imports only; preserve user edits.

## Tasks and verification
- [x] Product version: add stale/missing-version integration regressions; require read version, flush updates and use a version-aware editor.
- [x] Product list: lean DTO and closed JPA projection; SQL-capture test confirms long columns are not selected.
- [x] Images: selected-image-first operation, gallery state APIs, direct thumbnail upload and serialized/versioned FE operations.
- [x] Image storage: 320px variants, V5 registry, transactional claims, retention cleanup, claim/cleanup race test, bounded token maintenance with V6 indexes.
- [x] Category/cart cleanup: database sorting and removal of obsolete cart dependency/comment/imports.
- [x] Final: backend full suite, frontend regression/syntax checks and browser smoke. Independent review and fixes; update README and record evidence.

## Ledger
- Existing source/application.yml has user-added profiles.active=local; preserve it.
- User authorized scope before this plan; implement continuously without another approval gate.
- RED/GREEN: stale/missing product version and list payload regressions reproduced; 42 targeted catalog tests passed after implementation. Gallery state routes initially returned 404; new state/thumbnail tests then passed.
- RED/GREEN: small-image URL regression and missing product editor module reproduced; API and editor suites now pass six scenarios each.
- Review found legacy gallery writes bypassed the version gate and external image URLs were classified as local. Three focused regressions failed, then fixes added mandatory If-Match to all legacy writes and configured-origin URL classification. Follow-up review found no additional important issues.
- Ruling: keep legacy gallery response bodies, but require a version header for every mutation. Existing tests explicitly simulate reading the version before writes; missing/stale-header regressions bypass helpers.
- Ruling: flush pending writes in transactional integration helpers before reading the next gallery version; production HTTP requests already commit separately.
- Final full backend run: 11 unit + 153 integration tests passed. Browser uses isolated ports 18080/15500 and ogani_store_test, with compiled classes copied to a separate directory to avoid Maven build interference.
- Browser verified thumbnail selection/direct replacement and concurrent ADMIN/STAFF saves: newer stock 14 survived stale stock 99 attempt; reload confirmed 14. See ../../catalog-optimization-verification.md.
