# Address deletion and default selection design

User requested DELETE /api/addresses/{id} and PATCH /api/addresses/{id}/default with ownership, transaction/concurrency protection and preservation of old order snapshots. The user chose automatic promotion of the oldest remaining address when deleting the default; deleting the final address leaves no default.

Reuse existing active CUSTOMER validation and user-first lock. Missing and foreign404, invalid IDs400. DELETE returns200 standard envelope and null data, repeated missing deletion404. PATCH needs no body, returns saved address200, repeat selection is safe. Retain at most one default via existing index, and one default whenever any addresses remain. Repair legacy missing defaults after deletion. Legacy nullable address fields do not prevent selection/removal.

No new migration or version contract. Flush DELETE before promoting its replacement; use existing target-excluding bulk clear for PATCH. Stock/cart/orders are not modified by these endpoints. Checkout has its own user lock and immutable snapshots; races either complete checkout before deletion or reject a deleted source address cleanly. Retry of a successful checkout uses stored order data.

Test role/status/ownership/path, deletion of nondefault/default/final address, repeated selection/deletion, concurrency with create/default/deletion/checkout and rollback after a deleted row or cleared default. Existing create/update/default invariants must continue passing. Frontend work is excluded.
