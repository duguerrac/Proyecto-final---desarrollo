CREATE TABLE IF NOT EXISTS inventory_item (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    fragile BOOLEAN NOT NULL DEFAULT FALSE,
    default_speed_limit DECIMAL(5,2) NOT NULL DEFAULT 1.0,
    weight_kg DECIMAL(8,2) NOT NULL DEFAULT 0,
    dimensions VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS root_point (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    x DECIMAL(8,2) NOT NULL,
    y DECIMAL(8,2) NOT NULL,
    z_level INT NOT NULL DEFAULT 1,
    type VARCHAR(30) NOT NULL DEFAULT 'INTERNAL',
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    max_speed DECIMAL(5,2) NOT NULL DEFAULT 1.0
);

CREATE TABLE IF NOT EXISTS spot (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    aisle VARCHAR(20) NOT NULL,
    section VARCHAR(20) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    root_point_id BIGINT REFERENCES root_point(id),
    x DECIMAL(8,2),
    y DECIMAL(8,2),
    z DECIMAL(8,2)
);

CREATE TABLE IF NOT EXISTS spot_item (
    id BIGSERIAL PRIMARY KEY,
    spot_id BIGINT NOT NULL REFERENCES spot(id),
    item_id BIGINT NOT NULL REFERENCES inventory_item(id),
    quantity_available INT NOT NULL DEFAULT 0,
    quantity_reserved INT NOT NULL DEFAULT 0,
    UNIQUE(spot_id, item_id)
);

CREATE TABLE IF NOT EXISTS dispatch_order (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    assigned_robot_id VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS order_item (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES dispatch_order(id),
    item_id BIGINT NOT NULL REFERENCES inventory_item(id),
    requested_quantity INT NOT NULL
);

CREATE TABLE IF NOT EXISTS route_edge (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES root_point(id),
    target_id BIGINT NOT NULL REFERENCES root_point(id),
    distance DECIMAL(8,2) NOT NULL,
    bidirectional BOOLEAN NOT NULL DEFAULT TRUE,
    weight DECIMAL(8,2) NOT NULL DEFAULT 1.0
);

CREATE TABLE IF NOT EXISTS route_plan (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES dispatch_order(id),
    robot_id VARCHAR(50),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_distance DECIMAL(10,2),
    speed_factor DECIMAL(5,2) NOT NULL DEFAULT 1.0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS route_step (
    id BIGSERIAL PRIMARY KEY,
    route_plan_id BIGINT NOT NULL REFERENCES route_plan(id),
    sequence INT NOT NULL,
    root_point_id BIGINT NOT NULL REFERENCES root_point(id),
    action VARCHAR(50) NOT NULL DEFAULT 'NAVIGATE',
    UNIQUE(route_plan_id, sequence)
);

CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMP
);

ALTER TABLE spot_item ADD COLUMN IF NOT EXISTS quantity_reserved INT NOT NULL DEFAULT 0;
ALTER TABLE route_plan ADD COLUMN IF NOT EXISTS speed_factor DECIMAL(5,2) NOT NULL DEFAULT 1.0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_route_edge_source_target'
    ) THEN
        ALTER TABLE route_edge ADD CONSTRAINT uk_route_edge_source_target UNIQUE (source_id, target_id);
    END IF;
END $$;
