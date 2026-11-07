ALTER TABLE orders ADD COLUMN guest_scope varchar(64);
ALTER TABLE orders ADD CONSTRAINT ck_guest_order_owner CHECK (guest_scope IS NULL OR user_id IS NULL);
CREATE UNIQUE INDEX uk_guest_checkout_key ON orders(guest_scope, checkout_key) WHERE guest_scope IS NOT NULL;
