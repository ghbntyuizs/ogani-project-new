# Admin Order Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** GET /api/admin/orders/{id} for active Admin/Staff with complete historical order snapshots and timeline.

**Spec:** User's explicit admin-detail checklist. Positive ID; missing404; anonymous401/Customer403; DB role/status revalidated. Include customer snapshot + nullable userId, item snapshot + nullable productId, shipping snapshot, stored amounts/currency, payment method/status/transactionRef, coupon reference + snapshot code, version, created/updated times and ordered history with nullable actor. No checkout keys/hashes/passwords/live product/coupon recalculation. Empty items/history200. Include deleted/ownerless references. Stable item ID order and timeline changedAt/id order.

**Architecture:** Scalar header/item/timeline projections, shared admin access guard, read-only REPEATABLE_READ transaction for coherent multi-query snapshot. No order writes/locks or customer API changes. V11 adds query indexes only.

**Tech Stack:** Spring Boot/JPA/PostgreSQL/MockMvc.

## Review Focus

- Preserve historical data after customer/product/coupon changes/deletion.
- Keep history rows with null/deleted actors; actor display name is current reference data, not a stored historical name.
- Prevent N+1 actor/product reads and avoid selecting checkout secrets.
- Concurrent status/history commits must not produce mixed-version response.
- Admin/Staff active access only, including stale blocked/demoted principal.

- [x] Write endpoint, snapshot, null-reference, security, SQL-count and coherent-read tests; observe missing-route failures.
- [x] Add explicit header/item/history projections and response DTOs; extend AdminOrderQueryService/Controller.
- [x] Run targeted/full regression and independent review; fix important issues with regressions.
- [x] Update feature checklist/Swagger/examples/results, diff check, provide commit description without committing.

## Ledger

- User supplied detailed acceptance checklist; native implementation in current checkout. No unrelated feature changes.
- Billing contact and delivery are order snapshots. Do not substitute account profile or current catalog/coupon values.
- Ruling: existing item/history tables have no order_id indexes; add V11 (order_id,id) and (order_id,changed_at,id) to support detail lookup and deterministic timeline sorting.
- RED:14 assertion failures against missing route, one permission case already passed. Fixed committed test fixture to use JDBC timestamps; coherent-read test independently failed404 as expected before implementation.
- GREEN:16 detail +53 existing admin list cases passed. First full regression11 unit +438 integration passed.
- Independent review found no actionable issue; added primary changedAt ordering regression and explicit item-ID ordering assertion. Final full suite in progress; documentation/checklist updated.
- Final:450 tests passed (11 unit +439 integration), zero failures/errors/skips;17 new cases. Review clear, docs/checklist and diff check complete. Leave code uncommitted and include commit description.
