ALTER TABLE rental_vehicle
    ADD COLUMN IF NOT EXISTS reserved_by_user_id BIGINT;

-- Tạo bảng rental_session
CREATE TABLE IF NOT EXISTS rental_session (
                                              id          BIGSERIAL PRIMARY KEY,
                                              vehicle_id  BIGINT       NOT NULL,
                                              user_id     BIGINT       NOT NULL,
                                              start_time  TIMESTAMP    NOT NULL,
                                              end_time    TIMESTAMP,
                                              status      VARCHAR(20)  NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_rental_session_vehicle_status
    ON rental_session (vehicle_id, status);

CREATE INDEX IF NOT EXISTS idx_rental_session_user_status
    ON rental_session (user_id, status);