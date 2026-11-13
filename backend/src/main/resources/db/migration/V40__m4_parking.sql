-- V40: cac bang cua M4 - dat cho do (xe thue va xe ca nhan), theo ERD o Template_M4 muc 6.1.
-- Quy uoc so hieu migration: module n dung dai V(n*10). M4 = V40..V49.
-- Chay sau V20 vi co khoa ngoai toi bang reservation cua M2.
-- Thieu bang parking_space thi ddl-auto: validate chan app khoi dong (entity ParkingSpace).
--
-- personal_vehicle va parking_booking_log la bang cua entity PrivateVehicle va ParkingBookingLog
-- (se hien thuc tiep); tao san o day de M4 chi can mot migration.

CREATE TABLE personal_vehicle (
    id            BIGSERIAL   PRIMARY KEY,
    student_id    BIGINT      NOT NULL REFERENCES app_user(id),
    license_plate VARCHAR(20) NOT NULL UNIQUE,
    vehicle_type  VARCHAR(20) NOT NULL CHECK (vehicle_type IN ('E_MOTORBIKE','E_BIKE','E_SCOOTER','E_CAR')),
    model         VARCHAR(60) NOT NULL,
    battery_level INT         CHECK (battery_level BETWEEN 0 AND 100),
    status        VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_personal_vehicle_student ON personal_vehicle(student_id, status);

CREATE TABLE parking_space (
    id                     BIGSERIAL   PRIMARY KEY,
    hub_id                 BIGINT      NOT NULL REFERENCES hub(id),
    space_code             VARCHAR(30) NOT NULL,
    type                   VARCHAR(20) NOT NULL CHECK (type IN ('CAR','BIKE')),
    status                 VARCHAR(20) NOT NULL CHECK (status IN ('FREE','RESERVED','OCCUPIED','BLOCKED')),
    current_reservation_id BIGINT      REFERENCES reservation(id),
    charger_point_id       BIGINT      UNIQUE,
    version                BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_parking_space_code UNIQUE (hub_id, space_code),
    -- BR-M4-05, BR-M4-06: FREE thi khong co reservation; RESERVED/OCCUPIED bat buoc co.
    CONSTRAINT ck_parking_space_reservation CHECK (
        (status = 'FREE' AND current_reservation_id IS NULL)
        OR (status IN ('RESERVED','OCCUPIED') AND current_reservation_id IS NOT NULL)
        OR status = 'BLOCKED')
);
CREATE INDEX idx_parking_space_hub_status ON parking_space(hub_id, type, status);

CREATE TABLE parking_booking_log (
    id                  BIGSERIAL   PRIMARY KEY,
    reservation_id      BIGINT      NOT NULL UNIQUE REFERENCES reservation(id),
    space_id            BIGINT      NOT NULL REFERENCES parking_space(id),
    hub_id              BIGINT      NOT NULL REFERENCES hub(id),
    student_id          BIGINT      NOT NULL REFERENCES app_user(id),
    vehicle_kind        VARCHAR(10) NOT NULL CHECK (vehicle_kind IN ('PERSONAL','RENTAL')),
    personal_vehicle_id BIGINT      REFERENCES personal_vehicle(id),
    slot_start          TIMESTAMPTZ NOT NULL,
    slot_end            TIMESTAMPTZ NOT NULL,
    check_in_deadline   TIMESTAMPTZ NOT NULL,
    charging_requested  BOOLEAN     NOT NULL DEFAULT FALSE,
    check_in_time       TIMESTAMPTZ,
    check_out_time      TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','COMPLETED','NO_SHOW','CANCELLED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_booking_slot CHECK (slot_end > slot_start),
    CONSTRAINT ck_booking_vehicle CHECK ((vehicle_kind = 'PERSONAL') = (personal_vehicle_id IS NOT NULL))
);
CREATE INDEX idx_booking_hub_slot ON parking_booking_log(hub_id, slot_start, status);
CREATE INDEX idx_booking_student_status ON parking_booking_log(student_id, status);

-- Seed: id cho do trung resource_id PARKING_SPACE cua m1_resource_state (V10),
-- cung bieu thuc ROW_NUMBER() OVER (ORDER BY h.id, g): Hub 1 co id 1-40, Hub 2 co 41-70, ...
INSERT INTO parking_space (id, hub_id, space_code, type, status)
SELECT ROW_NUMBER() OVER (ORDER BY h.id, g), h.id, 'P-' || LPAD(g::text, 2, '0'), 'BIKE', 'FREE'
FROM hub h, generate_series(1, h.parking_slots) AS g;
SELECT setval('parking_space_id_seq', (SELECT MAX(id) FROM parking_space));

-- Gan tru sac cho cac cho co so thu tu g <= charging_points, dung cach V10 danh so CHARGING_POINT
-- (Hub 1: 1-8, Hub 2: 9-14, ...). Cho P-01 cua Hub Ga Metro nhan tru 1, tru ma kich ban
-- CHARGER_FAILURE cua M6 lam hong.
UPDATE parking_space ps
SET charger_point_id = x.cp_offset + CAST(SUBSTRING(ps.space_code FROM 3) AS INT)
FROM (SELECT id, charging_points,
             SUM(charging_points) OVER (ORDER BY id) - charging_points AS cp_offset
      FROM hub) x
WHERE ps.hub_id = x.id
  AND CAST(SUBSTRING(ps.space_code FROM 3) AS INT) <= x.charging_points;

-- Lop cho CAR cho test phan hoach tuong duong: 4 cho cuoi cua Hub Ga Metro.
UPDATE parking_space SET type = 'CAR' WHERE hub_id = 1 AND space_code IN ('P-37','P-38','P-39','P-40');
