CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    point_balance BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_users_point_balance CHECK (point_balance >= 0)
) ENGINE = InnoDB;

CREATE TABLE menus (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    price BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_menus_price CHECK (price > 0)
) ENGINE = InnoDB;

CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    menu_name_snapshot VARCHAR(100) NOT NULL,
    paid_amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    paid_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_orders_menu FOREIGN KEY (menu_id)
        REFERENCES menus (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_orders_paid_amount CHECK (paid_amount > 0),
    CONSTRAINT chk_orders_status CHECK (status IN ('PAID')),
    INDEX idx_orders_popular (status, paid_at, menu_id)
) ENGINE = InnoDB;

CREATE TABLE idempotency_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    result_code VARCHAR(50) NULL,
    http_status INT NULL,
    response_body JSON NULL,
    status VARCHAR(20) NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uk_idempotency_request
        UNIQUE (user_id, operation_type, idempotency_key),
    CONSTRAINT chk_idempotency_operation_type
        CHECK (operation_type IN ('CHARGE', 'ORDER')),
    CONSTRAINT chk_idempotency_status
        CHECK (status IN ('PROCESSING', 'COMPLETED')),
    CONSTRAINT chk_idempotency_result_fields CHECK (
        (
            status = 'PROCESSING'
            AND result_code IS NULL
            AND http_status IS NULL
            AND response_body IS NULL
            AND completed_at IS NULL
        )
        OR
        (
            status = 'COMPLETED'
            AND result_code IS NOT NULL
            AND http_status IS NOT NULL
            AND response_body IS NOT NULL
            AND completed_at IS NOT NULL
        )
    )
) ENGINE = InnoDB;

CREATE TABLE outbox_events (
    event_id CHAR(36) NOT NULL,
    order_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL,
    locked_at DATETIME(6) NULL,
    claim_token CHAR(36) NULL,
    published_at DATETIME(6) NULL,
    failed_at DATETIME(6) NULL,
    last_http_status INT NULL,
    last_error_type VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id),
    CONSTRAINT fk_outbox_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uk_outbox_order UNIQUE (order_id),
    CONSTRAINT chk_outbox_event_type CHECK (event_type = 'ORDER_PAID'),
    CONSTRAINT chk_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_outbox_retry_count CHECK (retry_count BETWEEN 0 AND 4),
    CONSTRAINT chk_outbox_error_type CHECK (
        last_error_type IS NULL
        OR last_error_type IN (
            'TIMEOUT',
            'NETWORK',
            'HTTP_3XX',
            'HTTP_4XX',
            'HTTP_5XX'
        )
    ),
    CONSTRAINT chk_outbox_error_status CHECK (
        (last_error_type IS NULL AND last_http_status IS NULL)
        OR (
            last_error_type IN ('TIMEOUT', 'NETWORK')
            AND last_http_status IS NULL
        )
        OR (
            last_error_type IN ('HTTP_3XX', 'HTTP_4XX', 'HTTP_5XX')
            AND last_http_status IS NOT NULL
        )
    ),
    CONSTRAINT chk_outbox_state_fields CHECK (
        (
            status = 'PENDING'
            AND retry_count BETWEEN 0 AND 3
            AND next_retry_at IS NOT NULL
            AND locked_at IS NULL
            AND claim_token IS NULL
            AND published_at IS NULL
            AND failed_at IS NULL
            AND (
                (retry_count = 0 AND last_error_type IS NULL)
                OR (retry_count BETWEEN 1 AND 3 AND last_error_type IS NOT NULL)
            )
        )
        OR
        (
            status = 'PROCESSING'
            AND retry_count BETWEEN 0 AND 3
            AND next_retry_at IS NOT NULL
            AND locked_at IS NOT NULL
            AND claim_token IS NOT NULL
            AND published_at IS NULL
            AND failed_at IS NULL
            AND (
                (retry_count = 0 AND last_error_type IS NULL)
                OR (retry_count BETWEEN 1 AND 3 AND last_error_type IS NOT NULL)
            )
        )
        OR
        (
            status = 'PUBLISHED'
            AND retry_count BETWEEN 0 AND 3
            AND next_retry_at IS NULL
            AND locked_at IS NULL
            AND claim_token IS NULL
            AND published_at IS NOT NULL
            AND failed_at IS NULL
            AND last_http_status IS NULL
            AND last_error_type IS NULL
        )
        OR
        (
            status = 'FAILED'
            AND retry_count BETWEEN 1 AND 4
            AND next_retry_at IS NULL
            AND locked_at IS NULL
            AND claim_token IS NULL
            AND published_at IS NULL
            AND failed_at IS NOT NULL
            AND last_error_type IS NOT NULL
        )
    ),
    INDEX idx_outbox_due (status, next_retry_at)
) ENGINE = InnoDB;

INSERT INTO users (id, point_balance, created_at, updated_at)
VALUES (1, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO menus (id, name, price, created_at)
VALUES
    (1, '아메리카노', 4000, UTC_TIMESTAMP(6)),
    (2, '카페라떼', 5000, UTC_TIMESTAMP(6)),
    (3, '카푸치노', 5500, UTC_TIMESTAMP(6));
