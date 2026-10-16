ALTER TABLE coffee_orders ADD COLUMN order_status VARCHAR(20) NOT NULL DEFAULT 'PLACED';
ALTER TABLE coffee_orders ADD CONSTRAINT ck_coffee_orders_status
    CHECK (order_status IN ('PLACED', 'READY'));