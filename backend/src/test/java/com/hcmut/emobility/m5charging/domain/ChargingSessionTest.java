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
}
