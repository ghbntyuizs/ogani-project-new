-- Only new uploads are registered; legacy media is deliberately not eligible for cleanup.
CREATE TABLE uploaded_images (
    filename VARCHAR(40) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_uploaded_images_unclaimed ON uploaded_images (created_at, filename) WHERE claimed = FALSE;
