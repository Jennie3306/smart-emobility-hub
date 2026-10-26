package com.hcmut.emobility.m2reservation;

import com.hcmut.emobility.m2reservation.domain.ReservationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.hcmut.emobility.m2reservation.domain.ReservationState.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Technique: state transition testing.
 * Related: BR-M2-05, BR-M2-06, TC-M2-07, TC-M2-08.
 */
class ReservationStateTest {

    @Test
    @DisplayName("TC-M2-07: Valid lifecycle PENDING -> CONFIRMED -> CHECKED_IN -> COMPLETED")
    void validLifecycle() {
        assertTrue(PENDING.canMoveTo(CONFIRMED));
        assertTrue(CONFIRMED.canMoveTo(CHECKED_IN));
        assertTrue(CHECKED_IN.canMoveTo(COMPLETED));
    }

    @Test
    @DisplayName("TC-M2-08: Invalid transitions are rejected")
    void invalidTransitions() {
        assertFalse(EXPIRED.canMoveTo(CHECKED_IN));
        assertFalse(CANCELLED.canMoveTo(CONFIRMED));
        assertFalse(CHECKED_IN.canMoveTo(CANCELLED));
        assertFalse(CHECKED_IN.canMoveTo(EXPIRED));
        assertFalse(PENDING.canMoveTo(CHECKED_IN));
        for (ReservationState target : ReservationState.values()) {
            assertFalse(COMPLETED.canMoveTo(target), "COMPLETED -> " + target);
        }
    }

    @Test
    @DisplayName("TC-M2-08: Terminal states are no longer active")
    void terminalStatesAreNotActive() {
        assertTrue(CONFIRMED.isActive());
        assertTrue(COMPLETED.isTerminal());
        assertTrue(CANCELLED.isTerminal());
        assertTrue(EXPIRED.isTerminal());
    }
}
