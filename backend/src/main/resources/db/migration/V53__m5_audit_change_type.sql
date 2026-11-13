-- V53: phan biet doi uu tien voi doi lich trong nhat ky Operator cua M5 (review PR #11).
-- Doi lich giu nguyen muc uu tien nen old_priority = new_priority; cot nay cho biet do la SCHEDULE_MOVE.
ALTER TABLE charging_priority_audit
    ADD COLUMN change_type VARCHAR(20) NOT NULL DEFAULT 'PRIORITY_CHANGE'
        CHECK (change_type IN ('PRIORITY_CHANGE', 'SCHEDULE_MOVE'));
