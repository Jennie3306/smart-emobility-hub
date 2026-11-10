-- M8 integration branch: schema matching M2's existing Reservation entity.
-- Proposed handoff to TV4; no existing migrations are modified.
CREATE TABLE reservation (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    resource_type VARCHAR(20) NOT NULL CHECK (resource_type IN ('VEHICLE','PARKING_SPACE')),
    resource_id BIGINT NOT NULL,
    hub_id BIGINT REFERENCES hub(id),
    state VARCHAR(20) NOT NULL CHECK (state IN ('PENDING','CONFIRMED','CHECKED_IN','COMPLETED','CANCELLED','EXPIRED')),
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    checked_in_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    cancel_reason VARCHAR(30),
    active_resource_key VARCHAR(40) UNIQUE,
    active_user_key VARCHAR(40) UNIQUE,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_reservation_hub_created ON reservation(hub_id,created_at);
CREATE INDEX idx_reservation_expiry ON reservation(state,expires_at);
