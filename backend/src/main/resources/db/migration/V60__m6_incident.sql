-- V60: Incident cua M6 (FR-M6-01, FR-M6-02, FR-M6-05). Quy uoc: module n dung dai V(n*10); M6 = V60..V69.
-- M6 chi bao hong (VehicleFailed / ChargerFailed); chu so huu tu khoa tai nguyen (BR-M6-08).

CREATE TABLE incident (
    id            BIGSERIAL    PRIMARY KEY,
    resource_type VARCHAR(20)  NOT NULL CHECK (resource_type IN ('VEHICLE', 'CHARGING_POINT')),
    resource_id   BIGINT       NOT NULL,
    hub_id        BIGINT       NOT NULL REFERENCES hub(id),
    reason        VARCHAR(30)  NOT NULL,
    severity      VARCHAR(10)  NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    description   VARCHAR(500),
    status        VARCHAR(20)  NOT NULL
                  CHECK (status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    source        VARCHAR(10)  NOT NULL CHECK (source IN ('OPERATOR', 'DEVICE')),
    reported_by   VARCHAR(50)  NOT NULL,
    assignee      VARCHAR(50),
    note          VARCHAR(500),
    reported_at   TIMESTAMPTZ  NOT NULL,
    assigned_at   TIMESTAMPTZ,
    started_at    TIMESTAMPTZ,
    resolved_at   TIMESTAMPTZ,
    closed_at     TIMESTAMPTZ,
    version       BIGINT       NOT NULL DEFAULT 0      -- FR-M6-02: kiem tra phien ban Operator da doc
);

-- BR-M6-06 o muc database: moi tai nguyen co toi da mot Incident dang hoat dong,
-- ke ca khi hai bao cao den cung luc (TC-M6-59).
CREATE UNIQUE INDEX uq_incident_active_resource
    ON incident (resource_type, resource_id)
    WHERE status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS');

CREATE INDEX idx_incident_status ON incident (status, reported_at);
