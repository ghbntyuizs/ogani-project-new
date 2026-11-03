# Create a customer delivery address

`POST /api/addresses` requires an active CUSTOMER account. Success: HTTP 201 and the standard ApiResponse envelope containing the saved address and its `id`.

```json
{
  "recipientName": "Nguyễn Văn An",
  "phone": "+84901234567",
  "city": "TP. Hồ Chí Minh",
  "district": "Quận 1",
  "ward": "Phường Bến Nghé",
  "address": "12 Nguyễn Huệ",
  "defaultAddress": true
}
```

Recipient, city, district, ward and address are required strings. Maximum lengths after whitespace normalization: recipient120, city/district/ward80 each, detailed address255. Whitespace collapses to single spaces; unsupported control characters and non-string input are rejected. Province/district/ward names are validated for presence and length, not against an administrative directory.

Vietnam phones accept domestic mobile format `0[35789]` plus eight digits, or domestic landline `02` plus nine digits. Equivalent `+84` prefixes normalize to domestic `0`. This verifies format, not ownership or real-world availability. New addresses use country Vietnam. `defaultAddress` is optional (false by default) and must be a JSON boolean if present.

Owner comes exclusively from the authenticated account; client user IDs cannot select another owner. Anonymous401, inactive401, Admin/Staff403. Validation400; maximum10 saved addresses per customer, including defaults, otherwise409. Existing legacy customers over the cap keep their data but cannot create further addresses.

The first address automatically becomes default even if the flag is false. Selecting a new default clears the previous default in the same transaction. A customer user-row lock serializes count/default changes and uses the same lock order as checkout. The database also enforces at most one default per customer. Request failure restores the old default. Repeated successful POSTs create separate addresses and count toward the cap; this endpoint is not idempotent.

Use the returned `id` as `addressId` in checkout. `GET /api/checkout/addresses` includes district/ward and keeps the default first. Checkout copies district/ward into the Order; customer order responses and admin order detail expose those snapshots even after the saved address changes. Legacy addresses and orders without district/ward remain readable with null fields. The existing ad-hoc checkout deliveryAddress contract is unchanged.

Migration V13 adds nullable saved-address and order district/ward fields, an owner lookup index and a partial unique default index. Before adding uniqueness, it retains each customer's earliest existing default, or selects their earliest address if none was default. It does not invent historical district/ward or change old order snapshots. Restart backend to apply Flyway normally; do not edit prior migrations.

Verification:15 initial API cases failed against the missing route;56 targeted cases passed after implementation. Final clean full suite:11 unit +534 integration =545 passed, zero failures/errors/skips. This feature adds27 cases (22 address API,4 committed concurrency/rollback,1 checkout snapshot). Independent review found no important issues. Diff check passed; unique address fixtures and injected triggers were cleaned (zero remaining entries). All migrations in this checkout applied successfully from an empty database through V13.

Environment note: the initial address clean run exposed payment migration V12 present in ogani_store_test but absent from that checkout, so the first address suite used isolated ogani_address_test. V12 was restored with its original SQL to match existing Flyway history. The payment Java sources are now present too; the later project audit runs the combined feature suite on ogani_store_test. No migration repair or database history changes are needed.
