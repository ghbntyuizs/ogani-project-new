-- Both index directions support the matching created_at/id ASC or DESC order.
CREATE INDEX idx_ord_created_id ON orders (created_at DESC, id DESC);
CREATE INDEX idx_ord_payment_created_id ON orders (payment_status, created_at DESC, id DESC);
