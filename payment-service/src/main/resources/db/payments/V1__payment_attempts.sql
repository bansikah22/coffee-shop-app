CREATE TABLE payment_attempts (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(100) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    order_reference VARCHAR(100) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor BETWEEN 50 AND 100000),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'USD'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'EXPIRED')),
    session_id VARCHAR(255) UNIQUE,
    checkout_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_id, request_key),
    UNIQUE (owner_id, order_reference)
);

CREATE TABLE webhook_inbox (
    event_id VARCHAR(255) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payment_outbox (
    event_id VARCHAR(36) PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL REFERENCES payment_attempts(id),
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX pending_outbox ON payment_outbox(published, created_at);
CREATE INDEX pending_inbox ON webhook_inbox(state, received_at);