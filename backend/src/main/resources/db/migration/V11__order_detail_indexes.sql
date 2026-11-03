CREATE INDEX idx_order_items_order_id ON order_items (order_id, id);
CREATE INDEX idx_order_history_order_time_id ON order_status_history (order_id, changed_at, id);
