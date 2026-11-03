# Delete a delivery address and select its default

Both endpoints require an active CUSTOMER and operate only on that customer's saved addresses. Positive Long IDs are required; malformed/nonpositive/overflow IDs400, missing/foreign404, anonymous/inactive401, Admin/Staff403. Foreign IDs do not reveal ownership. Current account role/status are checked even with a stale principal.

## Delete

```http
DELETE /api/addresses/123
Authorization: Bearer <customer-token>
```

No request body. Success200: standard ApiResponse, message `Address deleted`, null data. Deleting a nondefault preserves the current default. Deleting a default automatically promotes the oldest remaining address (smallest ID), as chosen by the user. Deleting the last address leaves an empty list and no default; the next created address automatically becomes default. Repeating deletion of a missing address404. Legacy nonempty data with no default is repaired by selecting its oldest remaining address after deletion.

Orders contain independent shipping snapshots, not a foreign key to the saved address. Deletion is allowed even if an address was used before; old customer/admin order responses remain unchanged. Checkout retry with the original key/body still returns the existing order after address deletion. A new checkout using a deleted address404 without changing cart or inventory.

## Select default

```http
PATCH /api/addresses/123/default
Authorization: Bearer <customer-token>
```

No request body. Success200 with the saved address and `defaultAddress:true`. Other addresses of that customer become nondefault. Selecting the same target again is safe and does not edit address fields. Legacy addresses missing district/ward can be selected; this operation does not require replacing or revalidating their historical data. Read the current address list via `GET /api/checkout/addresses`, which lists defaults first.

## Transactions and verification

All address writers and checkout lock the owning user first. A partial unique database index enforces at most one default. DELETE explicitly flushes removal before promoting a new default to avoid Hibernate's update-before-delete ordering. Default selection clears only other addresses, excluding the managed target. Late write failure restores deleted rows, address fields and old default flags atomically. Concurrent commands serialize; the last successful selection wins and no deleted row is recreated.

Tests:14 initial API cases produced13 missing-route failures (one access case already passed);96 related cases passed after implementation. Eight new committed cases cover competing/same-target default selection, default deletion vs another/same selection, deleting all/duplicate addresses, create/delete at cap10 and two rollback failures. A ninth committed case races checkout with deletion and checks complete snapshot/stock consistency or clean404. The checkout snapshot test now deletes an address after an order and retries the original placement. Final clean suite:11 unit +606 integration =617 passed, zero failures/errors/skips, including23 new cases. Independent read-only review found no important issue. Diff check passed and unique address/checkout fixtures and injected triggers were cleaned (zero remaining entries).

No migration, frontend integration, commit or push is included. Restart backend to load these routes.
