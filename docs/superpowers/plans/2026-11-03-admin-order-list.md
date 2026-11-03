# Admin Order List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** GET /api/admin/orders for active Admin/Staff, with paginated search/filter/sort across all orders.

**Architecture:** Read-only admin query service; scalar closed projection via Specification fluent query. Existing ApiResponse/PageResponse; customer API unchanged.

**Tech Stack:** Spring Boot/JPA/PostgreSQL/MockMvc/Flyway.

**Spec:** User's explicit feature checklist. Defaults: page0,size20,max100,sort=createdAt,desc|createdAt,asc plus same-direction id tie. keyword is case-insensitive literal substring over orderCode, firstName+' '+lastName and email snapshots; trim, blank ignored, max200. status and paymentStatus exact enum values. dateFrom/dateTo ISO yyyy-MM-dd, inclusive days in existing createdAt wall-clock representation, implemented >=start and <next-day. One-sided ranges allowed, reversed/invalid dates400. All filters AND. Include deleted/ownerless users' historical orders. Admin/Staff only; active DB role/status revalidated. Summary includes order/payment/date/total/currency and customer name/email, never address/key/hash/items.

## Review Focus

- Search SQL wildcard characters must be literals, not broaden access/matches.
- Include the end date without excluding late-night fractional timestamps or including next midnight.
- Counts must apply all filters; stable equal-time ordering.
- No per-order item/customer fetches or sensitive checkout payload.
- Revalidate blocked/demoted stale principals; Customer403/Anonymous401.

- [x] Write API/security/boundary/SQL capture tests and observe missing endpoint failures.
- [x] Add filter DTO/specification/admin summary projection/service/controller and date/payment indexes.
- [x] Run targeted test, full regression and independent review; fix important findings test-first.
- [x] Update API feature checklist/Swagger/examples/results, diff check and commit description (no commit).

## Ledger

- Reuse current branch/workspace; no frontend or unrelated order state changes.
- Keyword uses historical order contacts rather than joining mutable User; this preserves search after account changes/deletion.
- Date filters use LocalDateTime in the same representation as API createdAt; no implicit offset conversion. Boundary fixtures use Hibernate temporal binding too.
- RED:50 cases,49 absent-endpoint failures. GREEN:all50 passed after implementation.
- Added3 more escape/leap/extreme-date cases. SQL capture verified one page SELECT +count and no item/history loads or sensitive detail columns.
- Reviewer found no actionable defect; documented limits of substring/deep-offset/count performance. Full suite running; docs/checklist updated.
- Final:433 tests passed (11 unit +422 integration), zero failures/errors/skips;53 new cases. Review clear, docs/checklist/diff check complete. Leave changes uncommitted and include commit message in final.
