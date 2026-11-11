-- V52: audit trail for Operator priority overrides (BR-M5-09).
CREATE TABLE charging_priority_audit (
    id               BIGSERIAL PRIMARY KEY,
    session_id       BIGINT NOT NULL REFERENCES charging_session(id),
    operator_user_id BIGINT NOT NULL REFERENCES app_user(id),
    old_priority     VARCHAR(20) NOT NULL,
    new_priority     VARCHAR(20) NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    changed_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_m5_priority_audit_session ON charging_priority_audit(session_id, changed_at DESC);
