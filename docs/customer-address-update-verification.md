# Update a saved customer address

`PUT /api/addresses/{id}`: active owning CUSTOMER only, HTTP200 and standard ApiResponse with saved address fields.

```json
{
  "recipientName": "Nguyễn Văn An",
  "phone": "0901234567",
  "city": "TP. Hồ Chí Minh",
  "district": "Quận 1",
  "ward": "Phường Bến Nghé",
  "address": "25 Nguyễn Huệ",
  "defaultAddress": true
}
```

All six supported text fields must be supplied and valid, following the same normalization, maximum lengths and Vietnam phone rules as POST /api/addresses. Names are checked for presence/length, not against a geographic directory. Wrong JSON types, invalid phones, missing/blank/oversized fields and invalid IDs return400. Unsupported legacy country/state/postcode fields are preserved; owner/id cannot be changed via JSON.

Missing or foreign addresses both return404 without revealing ownership. Anonymous/inactive401, Admin/Staff403. Customer status and role are checked from the database even with a stale principal. No address is inserted by PUT; update remains allowed when the customer already has10 saved addresses.

Default behavior approved by the user: true switches the default to this address; false or omitted preserves an existing default. If this address already is the default, it stays default even with false. For legacy data with no default, the updated address becomes default, matching create's repair rule. Repeating the same PUT keeps the same address/default. Switching defaults and updating fields commit or roll back together.

The customer row is locked before looking up the owned address, matching create/checkout lock ordering. Bulk default clearing excludes the managed target to avoid clearing unchanged defaults. Concurrent valid PUTs serialize; the last successful update wins. No optimistic-version or stale-form rejection contract is introduced. Other future address writers must follow the same locking rule.

Existing order snapshots are unchanged. The checkout regression now updates the saved address through this PUT API after placing an order and verifies that customer/admin order responses still contain the original district/ward.

No migration, frontend integration, commit or push is included. Read addresses via GET /api/checkout/addresses. DELETE and default-only PATCH remain separate pending features.

Verification: initial17 API cases produced16 missing-route failures (one access case already passed);68 related cases passed after implementation. Final suite:11 unit +583 integration =594 passed, zero failures/errors/skips. This feature adds23 cases:19 update API and4 committed concurrency/rollback. The checkout snapshot regression now uses the actual PUT endpoint. Independent review found no important defect. Diff check passed; unique address fixtures and injected triggers were cleaned (zero remaining entries).
