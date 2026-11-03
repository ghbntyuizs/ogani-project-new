# Customer Order List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** GET /api/orders lists only orders of the active authenticated principal.

**Architecture:** Read-only query service with owner-constrained, paginated closed projections. Summary DTO and existing ApiResponse/PageResponse. No changes to checkout writes or order detail.

**Tech Stack:** Spring Boot/JPA/PostgreSQL/MockMvc.

**Spec:** User's explicit order-list checklist in this conversation. Defaults: page0,size20,max100; status exact OrderStatus enum name; sort=createdAt,desc|createdAt,asc (default desc), ID tie-breaker in matching direction. Invalid/blank/overflow values400; empty/no-match/out-of-range pages200 empty content. Anonymous/disabled401. Customer/Admin/Staff own orders only, consistent with existing owned detail API.

## Review Focus

- Total counts and status filtering must also be owner scoped.
- Tie timestamps must not duplicate/skip entries across stable pages.
- Extra userId query parameters must not select a different owner.
- Size/offset overflow and unknown sort properties must fail safely.
- Summary payload must not expose recipient data, checkout key or request hash; no item query per order.

- [x] Write integration tests for explicit checklist and edge cases; observe missing endpoint failures.
- [x] Add OrderListView/repository queries, OrderListResponse, read-only OrderQueryService and OrderController.
- [x] Run targeted tests, full backend suite and independent review.
- [x] Create API feature tracking checklist requested earlier, document examples/results; diff check; leave changes uncommitted.

## Ledger

- Existing detail GET /api/orders/{id} accepts all authenticated roles with ownership enforcement. Preserve this policy for lists.
- Native execution in current checkout. No migration or frontend work required.
- RED:33 cases failed against absent list endpoint. GREEN: all33 integration cases passed after implementation.
- Added feature-level API checklist requested earlier; documented owned summaries and query contract. Full regression and review in progress.
- Final:333 tests passed (11 unit +322 integration), zero failures/errors/skips; independent review no actionable findings, diff check passed. All tasks complete, changes uncommitted.
