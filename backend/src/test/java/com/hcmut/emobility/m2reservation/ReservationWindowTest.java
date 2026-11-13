package com.hcmut.emobility.m2reservation;

import com.hcmut.emobility.m2reservation.domain.Reservation;
import com.hcmut.emobility.m2reservation.domain.ReservationState;
import com.hcmut.emobility.m2reservation.domain.ResourceType;
import com.hcmut.emobility.shared.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reservation core mo rong cho M4 (reserveWindow): giu cho theo khung gio, het han tai han check-in
 * do M4 chon (BR-M4-04) thay vi now + hold-minutes.
 */
class ReservationWindowTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-20T08:00:00+07:00");

    @Test
    @DisplayName("confirmWindow luu khung gio va giu cho den han check-in, khong theo hold-minutes")
    void windowHoldLastsUntilCheckInDeadline() {
        Reservation reservation = Reservation.create(7L, ResourceType.PARKING_SPACE, 9L, 1L, NOW);

        reservation.confirmWindow(NOW, NOW.plusHours(3), NOW.plusHours(5), NOW.plusHours(3).plusMinutes(15));

        assertEquals(ReservationState.CONFIRMED, reservation.getState());
        assertEquals(NOW.plusHours(3), reservation.getStartAt());
        assertEquals(NOW.plusHours(5), reservation.getEndAt());
        assertFalse(reservation.isOverdue(NOW.plusHours(3).plusMinutes(14)));
        assertTrue(reservation.isOverdue(NOW.plusHours(3).plusMinutes(15)));
    }

    @Test
    @DisplayName("confirmWindow tu choi khung gio nguoc hoac han check-in da qua (400)")
    void invalidWindowIsRejected() {
        Reservation backwards = Reservation.create(7L, ResourceType.PARKING_SPACE, 9L, 1L, NOW);
        BusinessException error = assertThrows(BusinessException.class,
                () -> backwards.confirmWindow(NOW, NOW.plusHours(2), NOW.plusHours(1), NOW.plusHours(2)));
        assertEquals("INVALID_TIME_SLOT", error.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals(ReservationState.PENDING, backwards.getState());

        Reservation past = Reservation.create(7L, ResourceType.PARKING_SPACE, 9L, 1L, NOW);
        assertThrows(BusinessException.class,
                () -> past.confirmWindow(NOW, NOW.minusHours(1), NOW.plusHours(1), NOW.minusMinutes(45)));
    }
}
