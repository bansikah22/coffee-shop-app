ALTER TABLE coffee_orders DROP CONSTRAINT ck_coffee_orders_status;

-- Orders created before payment integration were already valid purchases.
UPDATE coffee_orders SET order_status = 'PAID' WHERE order_status = 'PLACED';

ALTER TABLE coffee_orders ADD COLUMN payment_attempt_id VARCHAR(36);
ALTER TABLE coffee_orders ADD CONSTRAINT uk_coffee_orders_payment_attempt UNIQUE (payment_attempt_id);
ALTER TABLE coffee_orders ADD CONSTRAINT ck_coffee_orders_status
    CHECK (order_status IN ('AWAITING_PAYMENT', 'PAID', 'PAYMENT_EXPIRED', 'READY', 'OUT_FOR_DELIVERY', 'COMPLETED'));

CREATE TABLE processed_payment_events (
    event_id VARCHAR(36) PRIMARY KEY,
    payload_fingerprint CHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);