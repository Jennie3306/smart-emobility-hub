-- V71: de xuat dieu chuyen cua M7 (FR-M7-02, FR-M7-04). M7 = V70..V79.
-- Nguon: M1_ALERT (M7 tu de xuat khi M1 canh bao) hoac M8_SIMULATION (qua RedistributionRecommendations).

CREATE TABLE redistribution_recommendation (
    id                 BIGSERIAL    PRIMARY KEY,
    source             VARCHAR(20)  NOT NULL,
    external_ref       VARCHAR(64),
    source_hub_id      BIGINT       NOT NULL REFERENCES hub(id),
    destination_hub_id BIGINT       NOT NULL REFERENCES hub(id),
    amount             INT          NOT NULL CHECK (amount >= 1),
    task_count         INT          NOT NULL DEFAULT 0,
    reason             VARCHAR(500),
    status             VARCHAR(20)  NOT NULL
                       CHECK (status IN ('PENDING', 'APPROVED', 'PARTIAL', 'REJECTED')),
    note               VARCHAR(500),
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    resolved_at        TIMESTAMP,
    resolved_by        VARCHAR(50),
    CHECK (source_hub_id <> destination_hub_id)
);

-- propose() cua M8 idempotent theo (source, external_ref).
CREATE UNIQUE INDEX uq_recommendation_source_ref
    ON redistribution_recommendation (source, external_ref)
    WHERE external_ref IS NOT NULL;

CREATE INDEX idx_recommendation_status ON redistribution_recommendation (status, created_at);
