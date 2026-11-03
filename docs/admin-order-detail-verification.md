# Admin/Staff order detail

## API

```http
GET /api/admin/orders/{id}
Authorization: Bearer <access-token>
```

Use `data.content[*].id` from GET `/api/admin/orders`, or `data.id` from checkout. The path accepts a positive numeric order ID, not orderCode/productId.

| Condition | HTTP status |
| --- | --- |
| Active ADMIN or STAFF; existing order | 200 |
| Missing order | 404 |
| Invalid/nonpositive/fractional/overflow ID | 400 |
| Anonymous, invalid token, blocked or missing actor account | 401 |
| Customer or demoted actor | 403 |

Admin/Staff can read orders of any customer, including accounts that no longer exist. Current database role/status is revalidated even for a stale authenticated principal. This does not change the own-order customer detail endpoint.

## Response

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 123,
    "orderCode": "COD-1",
    "version": 0,
    "customer": {
      "userId": 3,
      "firstName": "An",
      "lastName": "Nguyen",
      "email": "an@example.com",
      "phone": "0901234567"
    },
    "items": [{
      "itemId": 456,
      "productId": 1,
      "productName": "Product name at purchase",
      "unitPrice": 20.00,
      "quantity": 2,
      "lineTotal": 40.00
    }],
    "deliveryAddress": {
      "recipientName": "An Nguyen",
      "phone": "0901234567",
      "country": "Vietnam",
      "address": "12 Nguyen Hue",
      "city": "Ho Chi Minh",
      "state": null,
      "postcode": null
    },
    "payment": {
      "method": "COD",
      "status": "UNPAID",
      "transactionRef": null
    },
    "coupon": null,
    "subtotal": 40.00,
    "discountAmount": 0.00,
    "shippingFee": 5.00,
    "total": 45.00,
    "currency": "USD",
    "orderStatus": "PENDING",
    "orderNotes": "Delivery note",
    "statusHistory": [{
      "id": 789,
      "fromStatus": null,
      "toStatus": "PENDING",
      "note": "COD order placed",
      "changedBy": {"id": 3, "name": "An Nguyen"},
      "changedAt": "2026-11-03T12:00:00"
    }],
    "createdAt": "2026-11-03T12:00:00",
    "updatedAt": "2026-11-03T12:00:00"
  }
}
```

## Snapshot and legacy behavior

- `customer` contact fields and `deliveryAddress` come from the order, not the current User profile. userId is a nullable reference. The stored phone is the order/delivery contact captured at checkout, not a fresh account-profile phone.
- Items use order-item name, unitPrice, quantity and lineTotal snapshots, ordered by itemId. A changed/hidden/deleted catalog product does not rewrite or remove the purchased line; productId may be null.
- Payment method/status/reference, monetary amounts, currency and notes are read as stored. Historical PAYPAL/BANK_TRANSFER/PAID/REFUNDED records remain readable even though new checkout supports COD only. GET never recalculates totals, discounts or inventory.
- When a coupon reference or snapshot code exists, coupon is `{id,code}`. Code is the historical orders.coupon_code, and discountAmount is stored on the order. A renamed/inactive/deleted coupon does not replace the old code/discount. A deleted coupon may have null id with its old code retained. If both reference/code are absent, coupon is null. Current coupon type/value/validity are not presented as historical facts because those fields were not snapshotted.
- History is ordered by changedAt ascending, then event ID. Initial fromStatus may be null. A null/deleted actor does not remove the event: changedBy is null. It cannot reliably distinguish system events from deleted actors. For an existing actor, ID/name are current reference data in the read snapshot; actor names are not immutable historical snapshots in the current schema.
- Incomplete legacy orders with empty items/history return200 with empty arrays. They remain inspectable without changing their state or inventing data.
- Order version and updatedAt are included for future administrative state workflows. This endpoint does not implement transitions or payments.

## Consistency, query cost and schema

Detail uses a read-only PostgreSQL REPEATABLE_READ transaction. Header, items, timeline and actor display names come from one database snapshot even if status/history commit between those SELECTs. It does not take order/product write locks or block normal status/checkout writers. A later request can see the newly committed state.

Explicit scalar projections make3 domain queries (header/items/history) independent of row count; history uses LEFT JOIN for actor display fields. No per-product/coupon/actor entity fetch is needed. Order domain SELECTs exclude checkout key/hash, and history does not select actor password fields; DTOs expose no passwords/tokens/internal checkout keys. Authentication/access-check account reads are separate from these3 domain queries.

Flyway V11 adds `(order_id,id)` on order_items and `(order_id,changed_at,id)` on order_status_history. Restart backend to apply the query indexes. Response size and database work still grow with the order's items/history; fixed SQL statement count is not a constant-time or large-data benchmark claim.

## Checklist and verification

- [x] Existence/ID validation and Admin/Staff permission.
- [x] Customer/contact, item snapshots and delivery snapshot.
- [x] Stored payment, coupon/discount, totals and currency.
- [x] Null/deleted customer/product/coupon/actor links.
- [x] Empty legacy item/history data and initial/system history.
- [x] Stable chronological history plus ID tie-breaker and item ordering.
- [x] Stale blocked/demoted principal and forbidden customer access.
- [x] Coherent read while another transaction commits status/payment/history.
- [x] Bounded projected queries, secret omission and no state changes.
- [x] Independent review: no actionable findings.

Initial RED exposed the missing route. A committed-fixture setup issue was corrected to use JDBC timestamp setup, and the coherent-read case independently reproduced404 before implementation. GREEN:16 detail +53 existing list cases passed. Added one regression explicitly proving changedAt sorts before event ID and item IDs stay ordered.

Final run on2026-11-03: **11 unit +439 integration =450 tests passed**, zero failures/errors/skips, BUILD SUCCESS;17 new detail cases. Independent review found no actionable issue; git diff --check passed. V11 applied only through the explicit test-database runs; restart application backend for its migration.

Tests explicitly use ogani_store_test. Most fixtures roll back; the concurrent case commits only unique owned fixture rows and removes them in cleanup. No frontend/browser work, commits or pushes are included.
