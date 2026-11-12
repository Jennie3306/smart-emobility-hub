-- V50: M5 ownership of charging-point assignment.
-- IDs are copied from M1's CHARGING_POINT projection so all modules use the same resource IDs.
-- Only equipment health (FAULTY, UNDER_REPAIR) is copied. RESERVED and IN_USE are assignment
-- states that M5 owns, and no charging_session exists yet to back them, so every other port
-- starts AVAILABLE. Copying them would leave those ports locked forever (review PR #11).
CREATE TABLE charging_point (
    id       BIGINT PRIMARY KEY,
    hub_id   BIGINT NOT NULL REFERENCES hub(id),
    state    VARCHAR(20) NOT NULL
             CHECK (state IN ('AVAILABLE','RESERVED','IN_USE','FAULTY','UNDER_REPAIR')),
    version  BIGINT NOT NULL DEFAULT 0
);

INSERT INTO charging_point (id, hub_id, state)
SELECT resource_id,
       hub_id,
       CASE state
           WHEN 'FAULTY' THEN 'FAULTY'
           WHEN 'UNDER_REPAIR' THEN 'UNDER_REPAIR'
           ELSE 'AVAILABLE'
       END
FROM m1_resource_state
WHERE resource_type = 'CHARGING_POINT';

CREATE INDEX idx_m5_charging_point_hub_state ON charging_point(hub_id, state);
