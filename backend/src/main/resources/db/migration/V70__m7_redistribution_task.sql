-- V70: bang nhiem vu dieu chuyen cua M7.
-- Quy uoc so hieu migration: module n dung dai V(n*10). M7 = V70..V79.
-- Thieu bang nay thi ddl-auto: validate chan app khoi dong.

CREATE TABLE redistribution_tasks (
    task_id            BIGSERIAL    PRIMARY KEY,
    vehicle_id         BIGINT       NOT NULL REFERENCES rental_vehicle(id),
    source_hub_id      BIGINT       NOT NULL REFERENCES hub(id),
    destination_hub_id BIGINT       NOT NULL REFERENCES hub(id),
    status             VARCHAR(20)  NOT NULL
                       CHECK (status IN ('CREATED', 'IN_PROGRESS', 'DONE', 'FAILED')),
    recommendation_id  VARCHAR(64),
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP,
    CHECK (source_hub_id <> destination_hub_id)
);

-- BR-M7-02 o muc database: moi xe co toi da mot nhiem vu dang hoat dong,
-- ke ca khi hai Operator bam tao cung luc (tranh chap tai nguyen).
CREATE UNIQUE INDEX uq_redistribution_active_vehicle
    ON redistribution_tasks (vehicle_id)
    WHERE status IN ('CREATED', 'IN_PROGRESS');

CREATE INDEX idx_redistribution_status ON redistribution_tasks (status, created_at);
