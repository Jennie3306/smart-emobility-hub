package com.hcmut.emobility.m2reservation.domain;

import java.util.EnumSet;
import java.util.Set;

/** Reservation state machine - must match the m2_state_reservation diagram (section 4.1). */
public enum ReservationState {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    COMPLETED,
    CANCELLED,
    EXPIRED;

    /** States that still hold the resource (BR-M2-01). */
    public static final Set<ReservationState> ACTIVE = EnumSet.of(PENDING, CONFIRMED, CHECKED_IN);

    /**
     * Allowed transitions. Anything not listed here is rejected (BR-M2-06).
     * Swapping the vehicle after a failure (CONFIRMED -> CONFIRMED) does not go through here
     * because the state does not change.
     */
    public boolean canMoveTo(ReservationState target) {
        return switch (this) {
            case PENDING    -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED  -> target == CHECKED_IN || target == CANCELLED || target == EXPIRED;
            case CHECKED_IN -> target == COMPLETED;
            case COMPLETED, CANCELLED, EXPIRED -> false;
        };
    }

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isTerminal() {
        return !isActive();
    }
}
