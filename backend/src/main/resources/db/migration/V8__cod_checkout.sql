ALTER TABLE orders ADD COLUMN checkout_key VARCHAR(36);
ALTER TABLE orders ADD COLUMN checkout_request_hash VARCHAR(64);
-- Historical seed prices are USD; snapshot currency for all future orders.
ALTER TABLE orders ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE orders ADD COLUMN ship_recipient_name VARCHAR(121);
UPDATE orders SET ship_recipient_name = first_name || ' ' || last_name;
ALTER TABLE orders ALTER COLUMN ship_recipient_name SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT uq_order_checkout_key UNIQUE (user_id, checkout_key);
CREATE SEQUENCE checkout_order_code_seq;
