package com.hcmut.emobility.m7dispatch.domain;

/**
 * Vong doi RedistributionTask - bang chuyen trang thai o muc 4.1 Template_M7.
 *
 *   CREATED     -> IN_PROGRESS  (bat dau dieu chuyen)
 *   CREATED     -> FAILED       (tu choi / khong thuc hien)
 *   IN_PROGRESS -> DONE         (dieu chuyen hoan tat)
 *   IN_PROGRESS -> FAILED       (dieu chuyen that bai)
 *
 * Moi chuyen khac bi chan, gom CREATED -> DONE (chua qua IN_PROGRESS)
 * va moi chuyen ra khoi DONE / FAILED (trang thai ket thuc).
 */
public enum RedistributionTaskStatus {
    CREATED, IN_PROGRESS, DONE, FAILED;

    public boolean isActive() {
        return this == CREATED || this == IN_PROGRESS;
    }

    public boolean canMoveTo(RedistributionTaskStatus target) {
        return switch (this) {
            case CREATED     -> target == IN_PROGRESS || target == FAILED;
            case IN_PROGRESS -> target == DONE || target == FAILED;
            case DONE, FAILED -> false;
        };
    }
}
