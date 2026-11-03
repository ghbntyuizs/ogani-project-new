# Update customer address design

User requested PUT /api/addresses/{id} with full input validation, existence/ownership checks and default handling. User chose explicitly to keep the current default when false is sent for an already-default address. All other material behavior follows the existing approved create-address contract: active CUSTOMER, required normalized recipient/phone/city/district/ward/address, Vietnam phone validation and same length limits. true replaces the default atomically. Foreign and missing addresses404; updates never reassign owners or edit old order snapshots.

Reuse the existing DTO and user-lock-first transaction. Exclude the target from bulk default clearing, then update fields and flush. Keep legacy country/state/postcode outside the supported request unchanged. Repair missing legacy default by promoting the updated address when none exists. No new migration/version requirement: serialized edits use last-successful-update-wins, matching the existing address writer architecture.

No delete/default-only API or frontend changes. Test validation/roles/ownership, exact limits, current-default no-op, cap10 updates, default races, update/create races, rollback and actual PUT after checkout preserving old order snapshots.
