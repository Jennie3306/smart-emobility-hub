package com.hcmut.emobility.m2reservation;

import com.hcmut.emobility.m2reservation.domain.CancelReason;
import com.hcmut.emobility.m2reservation.domain.Reservation;
import com.hcmut.emobility.m2reservation.domain.ReservationState;
import com.hcmut.emobility.m2reservation.events.ReservationEventHandler;
import com.hcmut.emobility.m2reservation.repository.ReservationRepository;
import com.hcmut.emobility.m2reservation.service.ReservationService;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.events.VehicleFailedEvent;
import com.hcmut.emobility.shared.events.VehicleReturnedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Business tests for the Reservation core on H2 (test profile) with a fake clock.
 * Related: FR-M2-03..09, BR-M2-01..08.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.reservation.expiry-scan-ms=3600000")   // keep the real Timer quiet during tests
class ReservationServiceTest {

    static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-24T14:00:00+07:00");
    static final long STUDENT_A = 101L;
    static final long STUDENT_B = 102L;

    @Autowired ReservationService service;
    @Autowired ReservationEventHandler handler;
    @Autowired ReservationRepository repository;
    @Autowired JdbcTemplate jdbc;
    @MockBean Clock clock;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM reservation");
        jdbc.update("DELETE FROM rental_vehicle");
        setNow(T0);
    }

    void setNow(OffsetDateTime now) {
        when(clock.instant()).thenReturn(now.toInstant());
        when(clock.getZone()).thenReturn(ZoneOffset.ofHours(7));
    }

    long vehicle(String code, String type, int battery, String state, long hubId) {
        jdbc.update("INSERT INTO rental_vehicle(code, type, battery_level, state, current_hub_id, version)"
                + " VALUES (?, ?, ?, ?, ?, 0)", code, type, battery, state, hubId);
        return jdbc.queryForObject("SELECT id FROM rental_vehicle WHERE code = ?", Long.class, code);
    }

    ReservationState stateOf(Long reservationId) {
        return repository.findById(reservationId).orElseThrow().getState();
    }

    @Test
    @DisplayName("TC-M2-01: Booking an AVAILABLE vehicle with no active reservation -> CONFIRMED, held 15 minutes")
    void reserveAvailableVehicle() {
        long v = vehicle("EB-T01", "E_BIKE", 80, "AVAILABLE", 1);
        Reservation r = service.reserveVehicle(STUDENT_A, v);
        assertEquals(ReservationState.CONFIRMED, r.getState());
        assertEquals(T0.plusMinutes(15).toInstant(), r.getExpiresAt().toInstant());
        assertEquals("VEHICLE:" + v, r.getActiveResourceKey());
    }

    @Test
    @DisplayName("TC-M2-03: Hold boundary - still CONFIRMED at 14:59, EXPIRED at 15:00 (BR-M2-02, BR-M2-07)")
    void holdBoundary() {
        long v = vehicle("EB-T03", "E_BIKE", 80, "AVAILABLE", 1);
        Reservation r = service.reserveVehicle(STUDENT_A, v);

        setNow(T0.plusMinutes(14).plusSeconds(59));
        assertTrue(service.findOverdueIds().isEmpty());
        assertFalse(service.expire(r.getId()));

        setNow(T0.plusMinutes(15));
        assertEquals(1, service.findOverdueIds().size());
        assertTrue(service.expire(r.getId()));
        assertEquals(ReservationState.EXPIRED, stateOf(r.getId()));
    }

    @Test
    @DisplayName("TC-M2-04: Student already has 1 active reservation -> RESERVATION_LIMIT_EXCEEDED (BR-M2-03)")
    void oneActiveReservationPerStudent() {
        long v1 = vehicle("EB-T04", "E_BIKE", 80, "AVAILABLE", 1);
        long v2 = vehicle("EB-T05", "E_BIKE", 80, "AVAILABLE", 1);
        service.reserveVehicle(STUDENT_A, v1);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reserveVehicle(STUDENT_A, v2));
        assertEquals("RESERVATION_LIMIT_EXCEEDED", ex.getCode());
    }

    @Test
    @DisplayName("TC-M2-05: MAINTENANCE / LOW_BATTERY / IN_TRANSIT vehicles cannot be booked (BR-M2-04)")
    void notAvailableVehicleRejected() {
        for (String state : new String[]{"MAINTENANCE", "LOW_BATTERY", "IN_TRANSIT"}) {
            long v = vehicle("X-" + state, "E_BIKE", 50, state, 1);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.reserveVehicle(STUDENT_A, v));
            assertEquals("VEHICLE_NOT_AVAILABLE", ex.getCode(), state);
        }
    }

    @Test
    @DisplayName("TC-M2-09: Check-in at expiresAt - 1s succeeds, at expiresAt is rejected (BR-M2-07)")
    void checkInBoundary() {
        long v1 = vehicle("EB-T09", "E_BIKE", 80, "AVAILABLE", 1);
        Reservation ok = service.reserveVehicle(STUDENT_A, v1);
        setNow(T0.plusMinutes(15).minusSeconds(1));
        assertEquals(ReservationState.CHECKED_IN, service.checkIn(ok.getId(), STUDENT_A).getState());

        setNow(T0);
        long v2 = vehicle("EB-T10", "E_BIKE", 80, "AVAILABLE", 1);
        Reservation late = service.reserveVehicle(STUDENT_B, v2);
        setNow(T0.plusMinutes(15));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.checkIn(late.getId(), STUDENT_B));
        assertEquals("RESERVATION_EXPIRED", ex.getCode());
    }

    @Test
    @DisplayName("TC-M2-10: Vehicle fails, replacement of same hub and type exists -> vehicle swapped, expiresAt kept (BR-M2-08)")
    void vehicleFailedWithReplacement() {
        long failed = vehicle("EB-T11", "E_BIKE", 80, "AVAILABLE", 2);
        long spare = vehicle("EB-T12", "E_BIKE", 60, "AVAILABLE", 2);
        Reservation r = service.reserveVehicle(STUDENT_A, failed);

        handler.onVehicleFailed(new VehicleFailedEvent(failed, 2L, "TEST"));

        Reservation after = repository.findById(r.getId()).orElseThrow();
        assertEquals(ReservationState.CONFIRMED, after.getState());
        assertEquals(spare, after.getResourceId());
        assertEquals(r.getExpiresAt().toInstant(), after.getExpiresAt().toInstant());
    }

    @Test
    @DisplayName("TC-M2-11: Vehicle fails, no replacement -> CANCELLED, cancelReason = VEHICLE_FAILED")
    void vehicleFailedWithoutReplacement() {
        long failed = vehicle("EB-T13", "E_BIKE", 80, "AVAILABLE", 3);
        vehicle("ES-T13", "E_SCOOTER", 80, "AVAILABLE", 3);   // different type, must not be picked
        Reservation r = service.reserveVehicle(STUDENT_A, failed);

        handler.onVehicleFailed(new VehicleFailedEvent(failed, 3L, "TEST"));

        Reservation after = repository.findById(r.getId()).orElseThrow();
        assertEquals(ReservationState.CANCELLED, after.getState());
        assertEquals(CancelReason.VEHICLE_FAILED, after.getCancelReason());
        assertNull(after.getActiveResourceKey());
    }

    @Test
    @DisplayName("TC-M2-12: Receiving the same VehicleFailed event twice -> same result as once")
    void duplicateEventIsIdempotent() {
        long failed = vehicle("EB-T14", "E_BIKE", 80, "AVAILABLE", 3);
        Reservation r = service.reserveVehicle(STUDENT_A, failed);
        VehicleFailedEvent event = new VehicleFailedEvent(failed, 3L, "TEST");

        handler.onVehicleFailed(event);
        handler.onVehicleFailed(event);

        assertEquals(ReservationState.CANCELLED, stateOf(r.getId()));
        assertEquals(1, repository.count());
    }

    @Test
    @DisplayName("TC-M2-13: VehicleReturnedEvent closes the CHECKED_IN reservation -> COMPLETED (FR-M2-09)")
    void returnCompletesReservation() {
        long v = vehicle("EB-T15", "E_BIKE", 80, "AVAILABLE", 1);
        Reservation r = service.reserveVehicle(STUDENT_A, v);
        service.checkIn(r.getId(), STUDENT_A);

        handler.onVehicleReturned(new VehicleReturnedEvent(v, 1L, 70));

        Reservation after = repository.findById(r.getId()).orElseThrow();
        assertEquals(ReservationState.COMPLETED, after.getState());
        assertNull(after.getActiveResourceKey());
    }

    @Test
    @DisplayName("TC-M2-14: Cancel by someone other than the owner -> ACCESS_DENIED, state unchanged")
    void cancelByOtherUserDenied() {
        long v = vehicle("EB-T16", "E_BIKE", 80, "AVAILABLE", 1);
        Reservation r = service.reserveVehicle(STUDENT_A, v);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(r.getId(), STUDENT_B));
        assertEquals("ACCESS_DENIED", ex.getCode());
        assertEquals(ReservationState.CONFIRMED, stateOf(r.getId()));
    }

    @Test
    @DisplayName("FR-M2-04: Owner cancels -> CANCELLED, cancelReason = USER_REQUEST, vehicle can be booked again")
    void ownerCancelReleasesVehicle() {
        long v = vehicle("EB-T17", "E_BIKE", 80, "AVAILABLE", 1);
        Reservation r = service.reserveVehicle(STUDENT_A, v);
        Reservation cancelled = service.cancel(r.getId(), STUDENT_A);
        assertEquals(CancelReason.USER_REQUEST, cancelled.getCancelReason());
        assertEquals(ReservationState.CONFIRMED, service.reserveVehicle(STUDENT_B, v).getState());
    }
}
