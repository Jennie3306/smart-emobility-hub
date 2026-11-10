-- V11: bang canh bao, do module M1 so huu.
-- Khong module nao khac duoc ghi vao bang nay.

CREATE TABLE alert (
    id              BIGSERIAL PRIMARY KEY,
    hub_id          BIGINT      NOT NULL REFERENCES hub(id),
    type            VARCHAR(20) NOT NULL CHECK (type IN ('CAPACITY', 'LOW_VEHICLE')),
    state           VARCHAR(20) NOT NULL
                    CHECK (state IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED')),
    trigger_value   INT         NOT NULL,
    raised_at       TIMESTAMPTZ NOT NULL,
    acknowledged_by VARCHAR(50),
    acknowledged_at TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,
    dismiss_reason  VARCHAR(200)
);

-- Tac vu 30 giay tra cuu canh bao dang mo cua tung hub theo bo ba nay.
CREATE INDEX idx_alert_hub_type_state ON alert(hub_id, type, state);
CREATE INDEX idx_alert_raised_at      ON alert(raised_at);
