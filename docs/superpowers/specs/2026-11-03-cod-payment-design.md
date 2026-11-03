# COD payment confirmation design

Approved by the user in chat on 2026-11-03: PATCH /api/admin/orders/{id}/payment-status, active Admin/Staff only, body paymentStatus=PAID and current nonnegative integer version. Only COD UNPAID orders in SHIPPING can be confirmed. Persist original payment time and confirming actor. Repeated PAID requests for already-paid COD orders return unchanged, including old-version retries after COMPLETED. No reversal or automatic completion. Existing fulfillment endpoint still requires PAID to complete.

Approach: extend the existing order transaction and version mechanism with nullable audit fields. This fits the single COD confirmation event; a general payment gateway ledger is outside this feature. Implementation uses existing buyer-before-order locking, atomic status/audit update, and exposes audit in admin detail. Historical paid orders keep unknown audit as null. Deleted actor references become null without deleting the order or timestamp. Inventory and fulfillment history remain unaffected by confirmation.

Verification covers access, strict inputs, method/status conflicts, stale versions, retries, concurrent confirmation and fulfillment/cancellation, and failed-write rollback. No payment provider integration or refunds are included.
