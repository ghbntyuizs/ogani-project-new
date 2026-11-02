CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens (expires_at);
CREATE INDEX idx_refresh_tokens_revoked ON refresh_tokens (id) WHERE revoked = TRUE;
