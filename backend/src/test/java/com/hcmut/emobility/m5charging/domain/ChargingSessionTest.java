package com.hcmut.emobility.m5charging.domain;

import com.hcmut.emobility.shared.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ChargingSessionTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-10T10:00:00+07:00");

    @Test
    void followsRequestedQueuedScheduledChargingCompleted() {
        ChargingSession session = ChargingSession.request(7L, 1L, null, VehicleKind.RENTAL,
                22L, 18, null, NOW);
        assertEquals(ChargingSessionStatus.REQUESTED, session.getStatus());
        session.enqueue(NOW);
        assertEquals(ChargingSessionStatus.QUEUED, session.getStatus());
        session.assign(3L, NOW, null);
        assertEquals(ChargingSessionStatus.SCHEDULED, session.getStatus());
        session.start(NOW.plusMinutes(1));
        assertEquals(ChargingSessionStatus.CHARGING, session.getStatus());
        session.complete(NOW.plusMinutes(2));
        assertEquals(ChargingSessionStatus.COMPLETED, session.getStatus());
        assertNull(session.getActiveVehicleKey());
    }

    @Test
    void queuedCannotJumpDirectlyToCharging() {
        ChargingSession session = ChargingSession.request(7L, 1L, null, VehicleKind.RENTAL,
                22L, 18, null, NOW);
        session.enqueue(NOW);
        BusinessException error = assertThrows(BusinessException.class, () -> session.start(NOW));
        assertEquals("INVALID_STATE_TRANSITION", error.getCode());
    }

    @Test
    void chargingSessionCannotBeCancelled() {
        ChargingSession session = ChargingSession.request(7L, 1L, null, VehicleKind.RENTAL,
                22L, 18, null, NOW);
        session.enqueue(NOW);
        session.assign(3L, NOW, null);
        session.start(NOW.plusMinutes(1));
        assertThrows(BusinessException.class, () -> session.cancel(NOW.plusMinutes(2)));
        assertEquals(ChargingSessionStatus.CHARGING, session.getStatus());
    }

    @Test
    void failedPortRequeuesSessionAndAddsBonusWhenNoReplacementIsAssigned() {
        ChargingSession session = ChargingSession.request(7L, 1L, null, VehicleKind.RENTAL,
                22L, 18, null, NOW);
        session.enqueue(NOW);
        session.assign(3L, NOW.plusMinutes(5), null);
        session.start(NOW.plusMinutes(6));
        session.interrupt(NOW.plusMinutes(8));
        session.requeueAfterFailure(NOW.plusMinutes(8));
        assertEquals(ChargingSessionStatus.QUEUED, session.getStatus());
        assertEquals(NOW, session.getWaitingSince());
        assertEquals(0, session.getFailureBonus());
        session.applyFailureBonus(); // service applies this only if no replacement point was assigned.
        assertEquals(20, session.getFailureBonus());
        assertEquals(18, session.getBatteryLevel());
    }


    @Test
    void failureBonusIsRejectedAfterReplacementPortHasBeenAssigned() {
        ChargingSession session = ChargingSession.request(7L, 1L, null, VehicleKind.RENTAL,
                23L, 18, null, NOW);
        session.enqueue(NOW);
        session.assign(4L, NOW.plusMinutes(1), null);

        BusinessException error = assertThrows(BusinessException.class, session::applyFailureBonus);
        assertEquals("INVALID_STATE_TRANSITION", error.getCode());
        assertEquals(0, session.getFailureBonus());
        assertEquals(ChargingSessionStatus.SCHEDULED, session.getStatus());
    }

    @Test
    void startIsRejectedAfterTenMinutePortHoldExpires() {
        ChargingSession beforeExpiry = ChargingSession.request(7L, 1L, null, VehicleKind.RENTAL,
                21L, 18, null, NOW);
        beforeExpiry.enqueue(NOW);
        beforeExpiry.assign(2L, NOW, null);
        assertDoesNotThrow(() -> beforeExpiry.start(NOW.plusMinutes(9).plusSeconds(59)));

        ChargingSession atExpiry = ChargingSession.request(7L, 1L, null, VehicleKind.RENTAL,
                22L, 18, null, NOW);
        atExpiry.enqueue(NOW);
        atExpiry.assign(3L, NOW, null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> atExpiry.start(NOW.plusMinutes(10)));
        assertEquals("CHARGING_HOLD_EXPIRED", error.getCode());
    }

    // --- Review PR #11 ---

    @Test
    void chargerFailureBeforeBookingKeepsItsTimeWindow() {
        OffsetDateTime start = NOW.plusHours(20);
        ChargingSession booking = ChargingSession.schedule(7L, 1L, 5L, VehicleKind.PERSONAL,
                30L, 40, start, 60, 3L, NOW);

        booking.interrupt(NOW.plusMinutes(1));
        booking.requeueAfterFailure(NOW.plusMinutes(1));

        assertEquals(ChargingSessionStatus.QUEUED, booking.getStatus());
        assertEquals(start, booking.getScheduledStartAt());
        assertEquals(start.plusMinutes(60), booking.getScheduledEndAt());
        assertNull(booking.getChargingPointId());
        assertTrue(booking.waitsForWindow(NOW.plusMinutes(1)));
    }

    @Test
    void chargerFailureDuringChargingClearsTheWindowForAnImmediateRequeue() {
        ChargingSession booking = ChargingSession.schedule(7L, 1L, 5L, VehicleKind.PERSONAL,
                31L, 40, NOW, 60, 3L, NOW);
        booking.start(NOW.plusMinutes(2));

        booking.interrupt(NOW.plusMinutes(20));

        assertNull(booking.getScheduledStartAt());
        assertNull(booking.getScheduledEndAt());
    }

    @Test
    void bookingCanMoveToReplacementPortWithoutChangingItsWindow() {
        OffsetDateTime start = NOW.plusHours(3);
        ChargingSession booking = ChargingSession.schedule(7L, 1L, 5L, VehicleKind.PERSONAL,
                32L, 40, start, 30, 3L, NOW);

        booking.moveToPort(4L);

        assertEquals(4L, booking.getChargingPointId());
        assertEquals(start, booking.getScheduledStartAt());
        assertEquals(ChargingSessionStatus.SCHEDULED, booking.getStatus());
    }

    @Test
    void lateAssignmentOfAStartedWindowGetsAFreshHold() {
        ChargingSession booking = ChargingSession.schedule(7L, 1L, 5L, VehicleKind.PERSONAL,
                33L, 40, NOW.plusHours(2), 60, 3L, NOW);
        booking.interrupt(NOW.plusMinutes(5));
        booking.requeueAfterFailure(NOW.plusMinutes(5));

        OffsetDateTime late = NOW.plusHours(2).plusMinutes(15);
        booking.assign(4L, late, null);

        assertEquals(late.plusMinutes(10), booking.getHoldExpiresAt());
        assertDoesNotThrow(() -> booking.start(late.plusMinutes(1)));
    }

    @Test
    void automaticSessionReturnsToQueueWhenItsHoldExpires() {
        ChargingSession automatic = ChargingSession.request(null, 1L, null, VehicleKind.RENTAL,
                24L, 12, null, NOW);
        automatic.enqueue(NOW);
        automatic.assign(2L, NOW.plusMinutes(1), null);
        assertTrue(automatic.isAutomatic());

        automatic.returnToQueue(NOW.plusMinutes(11));

        assertEquals(ChargingSessionStatus.QUEUED, automatic.getStatus());
        assertNull(automatic.getChargingPointId());
        assertNull(automatic.getHoldExpiresAt());
        assertEquals(NOW, automatic.getWaitingSince());
        assertNotNull(automatic.getActiveVehicleKey());
    }
}
