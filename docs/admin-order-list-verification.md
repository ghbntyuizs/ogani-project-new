# Admin/Staff order listing

## API

```http
GET /api/admin/orders?page=0&size=20&keyword=Nguyen&status=PENDING&paymentStatus=UNPAID&dateFrom=2026-11-01&dateTo=2026-11-03&sort=createdAt,desc
Authorization: Bearer <access-token>
```

Only active ADMIN/STAFF accounts can list all orders. Anonymous/invalid-token accounts return401; Customer returns403. Current database role/status is revalidated, including a stale authenticated principal after account blocking/demotion. GET `/api/orders` remains the separate own-order customer endpoint.

| Parameter | Behavior |
| --- | --- |
| page | Zero-based integer, default0 |
| size | Integer1–100, default20 |
| keyword | Optional case-insensitive substring of orderCode, historical firstName + lastName, or order email; trimmed, blank ignored, max200 characters |
| status | PENDING, CONFIRMED, SHIPPING, COMPLETED, CANCELLED |
| paymentStatus | UNPAID, PAID, REFUNDED |
| dateFrom | Optional inclusive creation date, yyyy-MM-dd |
| dateTo | Optional inclusive creation date, yyyy-MM-dd |
| sort | createdAt,desc (default) or createdAt,asc |

All filters combine with AND; keyword's three searchable fields combine with OR. Percent, underscore, backslash and the explicit LIKE escape character are literal search input. Searching names/emails uses immutable order contact snapshots, not current User fields, so renamed/deleted accounts do not erase historical matches. Orders with user_id NULL remain visible.

Dates use the same LocalDateTime representation as response createdAt; no implicit timezone/offset conversion is introduced. A day range is implemented as >=dateFrom midnight and <the midnight after dateTo, preserving fractional late-night timestamps and excluding next-day midnight. One-sided/equal-day ranges are valid. Dates must be real ISO dates with year0001–9999; timestamps/offset strings are not accepted in these day parameters.

Order/payment status names are exact uppercase values. Blank status/date/page/size/sort, invalid/reversed dates, unsupported sorts, negative/noninteger/overflow page values, size outside1–100 or offsets above Integer.MAX_VALUE return400. Blank keyword is the exception and means no search. Omitted optional fields use defaults.

Sort includes id in the same direction as createdAt, giving deterministic order for tied timestamps. Offset pagination does not freeze a changing dataset between separate requests. No matches, no orders and pages beyond the last page return200 with empty content; counts reflect all active filters.

## Response and privacy

Existing ApiResponse<PageResponse<AdminOrderListResponse>> shape:

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "content": [{
      "id": 123,
      "orderCode": "COD-1",
      "customerName": "An Nguyen",
      "customerEmail": "an@example.com",
      "orderStatus": "PENDING",
      "paymentStatus": "UNPAID",
      "paymentMethod": "COD",
      "total": 35.00,
      "currency": "USD",
      "createdAt": "2026-11-03T12:00:00"
    }],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

No checkout key/hash, recipient address, notes, order items or history is returned in this listing. Admin order detail/status APIs remain separate unfinished features.

## Query design and limits

Read-only service uses a scalar closed projection through JpaSpecificationExecutor, applies all predicates and paging in SQL, and does not lock checkout rows or modify orders. SQL capture confirms a full page uses one order select plus one count, selects no sensitive/long detail fields and issues no item/history queries per order. Short/empty pages may use fewer statements through Spring Data's count optimization.

Flyway V10 adds `(created_at DESC,id DESC)` and `(payment_status,created_at DESC,id DESC)` indexes. Existing order-status/date index remains. Restart backend to apply V10.

These indexes support date/payment sorting/filtering. Leading-wildcard substring searches across lowercased text, deep offsets and exact counts can still scan many candidate rows. Bounded SQL statement count is not a throughput/latency benchmark; no percentage speedup or large-dataset timing is claimed. Consider workload-based EXPLAIN/trigram/keyset pagination later if actual data warrants it.

## Checklist and verification

- [x] All orders across owners, including deleted/NULL owners.
- [x] Pagination and stable creation-date sorting in both directions.
- [x] Code/customer-name/email historical search, case/trim and literal wildcards.
- [x] Every order/payment enum and combinations with dates/keyword.
- [x] Inclusive day boundaries, fractional timestamps, one-sided ranges and valid/invalid leap days.
- [x] Empty/no-match/past-last-page behavior and owner-independent filtered counts.
- [x] Input limits, blank/unknown/overflow values and supported date extremes.
- [x] Admin/Staff, anonymous/Customer, invalid-token, blocked and demoted stale principals.
- [x] Summary privacy, SQL query count/projection and unchanged persisted orders.
- [x] Independent code review: no actionable defects found; performance caveats documented.

TDD RED:50 cases with49 failures before endpoint existed (one access-control case already passed). Initial GREEN:all50 passed. Added3 further date/escape cases before full regression.

Final run on2026-11-03: **11 unit +422 integration =433 tests passed**, zero failures/errors/skips, BUILD SUCCESS. Includes53 new admin list cases; explicit ogani_store_test database. Independent review found no actionable issues; git diff --check passed. No frontend changes, browser/large-dataset benchmark, commit or push performed.
