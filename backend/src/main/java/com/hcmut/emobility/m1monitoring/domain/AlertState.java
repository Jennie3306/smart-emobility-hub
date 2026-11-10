package com.hcmut.emobility.m1monitoring.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Vong doi canh bao - state machine o muc 4.1 cua Template_M1.
 * Bon chuyen trang thai bi tu choi:
 *   RESOLVED -> bat ky      : canh bao da dong khong bao gio mo lai
 *   DISMISSED -> bat ky     : operator da chu dong bo qua
 *   ACKNOWLEDGED -> OPEN    : khong co y nghia nghiep vu
 *   (tao moi khi da co canh bao cung loai dang mo) : vi pham BR-M1-06
 */
public enum AlertState {

    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    DISMISSED;

    private static final Set<AlertState> CLOSED = EnumSet.of(RESOLVED, DISMISSED);

    /** Canh bao con dang duoc theo doi (OPEN hoac ACKNOWLEDGED). */
    public boolean isOpen() {
        return !CLOSED.contains(this);
    }

    /** true neu chuyen tu trang thai nay sang {@code target} la hop le. */
    public boolean canMoveTo(AlertState target) {
        return switch (this) {
            case OPEN         -> target == ACKNOWLEDGED || target == RESOLVED || target == DISMISSED;
            case ACKNOWLEDGED -> target == RESOLVED || target == DISMISSED;
            case RESOLVED, DISMISSED -> false;
        };
    }
}
