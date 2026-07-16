ALTER TABLE orders
    MODIFY status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

ALTER TABLE idempotency_records
    MODIFY operation_type VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY idempotency_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY result_code VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NULL,
    MODIFY status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

ALTER TABLE outbox_events
    MODIFY event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY event_type VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY claim_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    MODIFY last_error_type VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NULL;
