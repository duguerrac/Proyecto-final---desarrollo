-- Incoming packages: tracks packages received at dock being transported to shelf
CREATE TABLE IF NOT EXISTS incoming_package (
    id              BIGSERIAL PRIMARY KEY,
    sku             VARCHAR(50)    NOT NULL,
    item_id         BIGINT         NOT NULL,
    quantity        INTEGER        NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'RECEIVED',
    reception_spot_code VARCHAR(50) NOT NULL,
    target_spot_code    VARCHAR(50) NOT NULL,
    robot_id        VARCHAR(50),
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_incoming_package_status ON incoming_package(status);
CREATE INDEX idx_incoming_package_sku ON incoming_package(sku);