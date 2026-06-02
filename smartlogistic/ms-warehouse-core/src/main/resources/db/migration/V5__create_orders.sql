-- Orders and order lines for warehouse picking operations
CREATE TABLE warehouse_order (
    id              BIGSERIAL PRIMARY KEY,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    pickup_spot_code VARCHAR(30),
    delivery_point   VARCHAR(30),
    robot_id         VARCHAR(50),
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE order_line (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES warehouse_order(id),
    sku             VARCHAR(50) NOT NULL,
    quantity        INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_warehouse_order_status ON warehouse_order(status);
CREATE INDEX idx_order_line_order_id ON order_line(order_id);