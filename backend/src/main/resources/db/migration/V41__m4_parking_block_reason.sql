-- V41: ly do khoa cho do (M4). Chi cho bi khoa vi tru sac hong (CHARGER_FAILURE) moi tu mo lai
-- khi M6 bao tru sac hoat dong tro lai; cho do Operator khoa (OPERATOR) phai do Operator mo.
ALTER TABLE parking_space
    ADD COLUMN blocked_reason VARCHAR(20)
        CHECK (blocked_reason IN ('CHARGER_FAILURE', 'OPERATOR'));
