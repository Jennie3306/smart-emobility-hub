-- V51: M5 charging requests, schedules, queue and session lifecycle.
CREATE TABLE charging_session (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT REFERENCES app_user(id),
    hub_id                BIGINT NOT NULL REFERENCES hub(id),
    parking_space_id      BIGINT,
    vehicle_kind          VARCHAR(20) NOT NULL CHECK (vehicle_kind IN ('PERSONAL','RENTAL')),
    vehicle_id            BIGINT NOT NULL,
    battery_level         INT NOT NULL CHECK (battery_level BETWEEN 0 AND 100),
    start_battery_level   INT NOT NULL CHECK (start_battery_level BETWEEN 0 AND 100),
    target_battery_level  INT NOT NULL DEFAULT 90 CHECK (target_battery_level BETWEEN 1 AND 100),
    status                VARCHAR(20) NOT NULL CHECK (status IN
                          ('REQUESTED','QUEUED','SCHEDULED','CHARGING','COMPLETED','INTERRUPTED','CANCELLED')),
    priority_level        VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (priority_level IN ('NORMAL','HIGH','URGENT')),
    priority_score        DOUBLE PRECISION NOT NULL DEFAULT 0,
    failure_bonus         INT NOT NULL DEFAULT 0,
    active_vehicle_key    VARCHAR(80) UNIQUE,
    charging_point_id    BIGINT REFERENCES charging_point(id),
    expected_use_at       TIMESTAMPTZ,
    scheduled_start_at    TIMESTAMPTZ,
    scheduled_end_at      TIMESTAMPTZ,
    waiting_since         TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    assigned_at           TIMESTAMPTZ,
    hold_expires_at       TIMESTAMPTZ,
    charging_started_at   TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    version               BIGINT NOT NULL DEFAULT 0,
    CHECK ((scheduled_start_at IS NULL AND scheduled_end_at IS NULL)
        OR (scheduled_start_at IS NOT NULL AND scheduled_end_at > scheduled_start_at))
);

CREATE INDEX idx_m5_session_hub_state ON charging_session(hub_id, status, created_at);
CREATE INDEX idx_m5_session_point_state ON charging_session(charging_point_id, status);
CREATE INDEX idx_m5_session_user_state ON charging_session(user_id, status);
CREATE INDEX idx_m5_session_parking ON charging_session(parking_space_id, status);
