ALTER TABLE coffee_orders ADD COLUMN fulfillment_type VARCHAR(20) NOT NULL DEFAULT 'PICKUP';
ALTER TABLE coffee_orders ADD COLUMN customer_phone VARCHAR(32);
ALTER TABLE coffee_orders ADD COLUMN delivery_address VARCHAR(500);

ALTER TABLE coffee_orders DROP CONSTRAINT ck_coffee_orders_status;
ALTER TABLE coffee_orders ADD CONSTRAINT ck_coffee_orders_status
    CHECK (order_status IN ('PLACED', 'READY', 'OUT_FOR_DELIVERY', 'COMPLETED'));
ALTER TABLE coffee_orders ADD CONSTRAINT ck_coffee_orders_fulfillment_type
    CHECK (fulfillment_type IN ('PICKUP', 'DELIVERY'));
ALTER TABLE coffee_orders ADD CONSTRAINT ck_coffee_orders_delivery_details
    CHECK (fulfillment_type <> 'DELIVERY' OR
           (customer_phone IS NOT NULL AND delivery_address IS NOT NULL));