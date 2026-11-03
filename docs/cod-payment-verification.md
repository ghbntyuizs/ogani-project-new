# COD payment confirmation

`PATCH /api/admin/orders/{id}/payment-status` requires an active ADMIN or STAFF account.

```json
{"paymentStatus":"PAID","version":2}
```

Read the current `version` from `GET /api/admin/orders/{id}`. Only COD orders with UNPAID payment and SHIPPING order status can be confirmed. Other payment targets are 400; non-COD, REFUNDED, invalid order state and stale versions are 409. Missing orders return 404; anonymous/customer access returns 401/403.

The response contains `id`, `orderCode`, `orderStatus`, `paymentStatus`, `version`, `paidAt`, `confirmedBy` and `updatedAt`. Admin detail exposes `payment.paidAt` and `payment.confirmedBy` as well.

A repeated PAID request for an already-paid COD order returns 200 without changing the original time, actor or version, including after completion and with an old version. Historical paid orders may have null audit fields; retry does not invent their payment history. Confirmation does not change inventory, order status or fulfillment history.

After confirmation, use the returned version to call the status API with COMPLETED. Completion records the sale exactly once. Refunds and online-payment verification remain separate future workflows.

Buyer lock precedes order lock, matching checkout/cancellation/status writers. Payment and audit fields are committed atomically with the order version. Migration V12 adds nullable audit columns and a user foreign key with ON DELETE SET NULL; existing orders remain readable. Restart backend to apply Flyway normally. This migration was exercised on ogani_store_test, not the development database.

Verification: initial 16 API cases failed against the missing route; 91 targeted new/existing cases then passed. Final suite: 11 unit + 530 integration = 541 passed, zero failures/errors/skips, including 19 payment API cases and 4 committed concurrency/rollback cases added by this feature. Access checks include blocked/demoted staff; other cases cover historical data, guest orders, concurrent confirmation/status/cancellation and injected-write-failure rollback. Independent read-only review found no critical/important findings. `git diff --check` passed; test fixtures and injected triggers were cleaned (zero remaining entries).
