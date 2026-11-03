ALTER TABLE customer_addresses ADD COLUMN district VARCHAR(80);
ALTER TABLE customer_addresses ADD COLUMN ward VARCHAR(80);
ALTER TABLE orders ADD COLUMN ship_district VARCHAR(80);
ALTER TABLE orders ADD COLUMN ship_ward VARCHAR(80);

-- Preserve earliest existing default, or select earliest address if none exists.
WITH ranked AS (
 SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY is_default DESC,id) AS position
 FROM customer_addresses
)
UPDATE customer_addresses a SET is_default=(r.position=1) FROM ranked r WHERE a.id=r.id;
CREATE UNIQUE INDEX uk_customer_address_default ON customer_addresses(user_id) WHERE is_default;
CREATE INDEX idx_customer_address_user ON customer_addresses(user_id);
