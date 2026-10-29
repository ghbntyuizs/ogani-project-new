-- Xóa unique constraint cũ của cột slug.
ALTER TABLE categories
DROP CONSTRAINT IF EXISTS categories_slug_key;

-- Slug chỉ cần duy nhất trong nhóm category đang active.
CREATE UNIQUE INDEX uq_categories_active_slug
    ON categories (slug)
    WHERE is_active = TRUE;