# Customer order list

## Contract

`GET /api/orders` returns orders owned by the current active authenticated account. This is plural, consistent with existing GET `/api/orders/{id}`. It is not an admin-wide listing API. Customer, Admin and Staff can list their own orders; no userId parameter is accepted for selecting an owner. Anonymous/invalid-token/blocked/deleted accounts are rejected with401, including stale authenticated principals.

```http
GET /api/orders?page=0&size=20&status=PENDING&sort=createdAt,desc
Authorization: Bearer <access-token>
```

| Parameter | Default | Valid values |
| --- | --- | --- |
| page | 0 | Non-negative integer within supported pagination offset |
| size | 20 | Integer1–100 |
| status | all owned statuses | PENDING, CONFIRMED, SHIPPING, COMPLETED, CANCELLED (exact uppercase) |
| sort | createdAt,desc | createdAt,desc or createdAt,asc |

Omit optional parameters to use defaults. Blank values, unknown status/sort, noninteger/negative values, integer overflow and offsets beyond JPA's Integer limit return400. Sorting always includes id in the same direction as createdAt, so equal creation timestamps have a deterministic order. Offset pagination does not freeze the dataset across separate requests while new orders are created.

Response uses existing ApiResponse/PageResponse:

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "content": [{
      "id": 123,
      "orderCode": "COD-1",
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

Empty owners, no-match status filters and pages past the end return200 with empty content. Total elements/pages remain scoped to the authenticated owner and filter. Use the summary id with GET `/api/orders/{id}` for full detail. Summary fields deliberately omit recipient/contact data, item collections, checkout key and request hash.

## Implementation and verification

Read-only OrderQueryService validates the active account without taking the checkout user write lock. Closed repository projections fetch only summary fields; owner/status constraints and pagination execute in the database. It neither loads item collections per order nor changes checkout/cart/order state. No migration required.

TDD RED: all33 new cases failed before list endpoint existed. GREEN: all33 passed, covering default/ascending order, equal-time pagination, all statuses, owner-scoped counts, null/foreign owners, spoofed userId, Admin/Staff own-only reads, empty/out-of-range pages, page-size boundary, invalid/blank/overflow inputs, stale blocked principal and preserved state.

Final full suite on2026-11-03: **11 unit +322 integration =333 tests passed**, zero failures/errors/skips, BUILD SUCCESS. Explicit test database ogani_store_test. Independent review found no actionable issues; git diff --check passed. No migration/frontend changes or commits/pushes.
