CREATE INDEX idx_outbox_claim_order
    ON outbox_events (next_retry_at, created_at, event_id, status, locked_at);
