-- V1: bang loi dung chung. CHI TV4 duoc them file migration moi.
-- Cac module tu bo sung bang cua minh o cac file V2, V3... theo ERD.

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('STUDENT', 'OPERATOR'))
);

CREATE TABLE hub (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    location      VARCHAR(200) NOT NULL,
    parking_slots INT NOT NULL CHECK (parking_slots >= 0),
    charging_points INT NOT NULL CHECK (charging_points >= 0)
);

CREATE TABLE rental_vehicle (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(20) NOT NULL UNIQUE,
    type           VARCHAR(20) NOT NULL CHECK (type IN ('E_BIKE', 'E_SCOOTER')),
    battery_level  INT NOT NULL CHECK (battery_level BETWEEN 0 AND 100),
    state          VARCHAR(20) NOT NULL,
    current_hub_id BIGINT REFERENCES hub(id),
    version        BIGINT NOT NULL DEFAULT 0   -- optimistic lock
);

CREATE INDEX idx_vehicle_hub_state ON rental_vehicle(current_hub_id, state);
