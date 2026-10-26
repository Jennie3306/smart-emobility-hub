-- V10: mo hinh doc cua M1 (read model).
--
-- VI SAO M1 TU GIU BANG NAY, thay vi doc bang cua M4/M5:
--   M6 giu trang thai thiet bi trong bo nho (InMemoryDeviceStateStore), khong
--   ghi xuong database, va M4 thi chua viet bang parking_slot nao ca. Neu M1
--   doc database de tinh utilisation thi so lieu se khong bao gio doi, du M6
--   van phat su kien deu.
--   Nen M1 dung mot bang rieng, cap nhat bang su kien ResourceStateUpdatedEvent.
--   Day KHONG phai ban goc cua du lieu — ban goc thuoc M4/M5/M6 — ma la ban
--   chieu danh rieng cho man hinh giam sat, nen M1 so huu hoan toan.
--
-- Quy uoc danh so migration: module n dung dai V(n*10). M1 = V10, V11.

CREATE TABLE m1_resource_state (
    resource_type VARCHAR(20)  NOT NULL
                  CHECK (resource_type IN ('PARKING_SPACE', 'CHARGING_POINT')),
    resource_id   BIGINT       NOT NULL,
    hub_id        BIGINT       NOT NULL REFERENCES hub(id),
    state         VARCHAR(20)  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (resource_type, resource_id)
);

CREATE INDEX idx_m1_resource_hub ON m1_resource_state(hub_id, resource_type, state);

-- Trang thai ban dau: sinh tu so luong da khai o bang hub.
-- resource_id danh so duy nhat tren toan he thong, dung quy uoc cua M6
-- (DemoDeviceCatalog anh xa PARKING_SPACE 1 -> hub 1).
INSERT INTO m1_resource_state (resource_type, resource_id, hub_id, state)
SELECT 'PARKING_SPACE',
       ROW_NUMBER() OVER (ORDER BY h.id, g),
       h.id,
       'FREE'
FROM hub h, generate_series(1, h.parking_slots) AS g;

INSERT INTO m1_resource_state (resource_type, resource_id, hub_id, state)
SELECT 'CHARGING_POINT',
       ROW_NUMBER() OVER (ORDER BY h.id, g),
       h.id,
       'FREE'
FROM hub h, generate_series(1, h.charging_points) AS g;

-- Du lieu demo: Hub Ga Metro (id 1, 40 cho) dat o 31/40 = 77%, ngay DUOI nguong 80%.
-- Khi quay demo, cho M6 chuyen PARKING_SPACE 1 sang OCCUPIED la len 80% va
-- canh bao no ngay truoc mat nguoi xem.
UPDATE m1_resource_state SET state = 'OCCUPIED'
WHERE resource_type = 'PARKING_SPACE' AND hub_id = 1 AND resource_id BETWEEN 2 AND 32;

UPDATE m1_resource_state SET state = 'FAULTY'
WHERE resource_type = 'CHARGING_POINT' AND hub_id = 1 AND resource_id = 8;

UPDATE m1_resource_state SET state = 'IN_USE'
WHERE resource_type = 'CHARGING_POINT' AND hub_id = 1 AND resource_id BETWEEN 2 AND 4;
