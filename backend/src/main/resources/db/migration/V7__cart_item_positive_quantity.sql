-- Refuse invalid legacy data rather than silently changing a customer's cart.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM cart_items WHERE quantity <= 0) THEN
        RAISE EXCEPTION 'Cannot enforce cart quantity: existing cart_items contain non-positive quantities; review and repair them before retrying migration';
    END IF;
END $$;

ALTER TABLE cart_items
    ADD CONSTRAINT chk_cart_item_quantity_positive CHECK (quantity > 0);
