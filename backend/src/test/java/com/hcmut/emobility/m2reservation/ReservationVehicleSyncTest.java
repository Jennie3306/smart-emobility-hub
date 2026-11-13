package com.hcmut.emobility.m2reservation;

import com.hcmut.emobility.m2reservation.domain.Reservation;
import com.hcmut.emobility.m2reservation.events.ReservationEventHandler;
import com.hcmut.emobility.m2reservation.service.ReservationService;
import com.hcmut.emobility.shared.events.VehicleFailedEvent;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Integration tests M2 -> M3 through the event bus: the vehicle state in M3 follows the reservation.
 * Related: FR-M2-03, FR-M2-04, FR-M2-05, FR-M2-08, DEF-M2-01.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.reservation.expiry-scan-ms=3600000")
class ReservationVehicleSyncTest {

    static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-07T09:00:00+07:00");
    static final long STUDENT = 301L;

    @Autowired ReservationService service;
    @Autowired ReservationEventHandler handler;
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

    long vehicle(String code, long hubId) {
        jdbc.update("INSERT INTO rental_vehicle(code, type, battery_level, state, current_hub_id, version)"
                + " VALUES (?, 'E_BIKE', 80, 'AVAILABLE', ?, 0)", code, hubId);
        return jdbc.queryForObject("SELECT id FROM rental_vehicle WHERE code = ?", Long.class, code);
    }

    String vehicleState(long vehicleId) {
        return jdbc.queryForObject("SELECT state FROM rental_vehicle WHERE id = ?", String.class, vehicleId);
    }

    @Test
    @DisplayName("TC-M2-17: Reserve -> vehicle RESERVED in M3; cancel -> AVAILABLE again")
    void reserveAndCancelSyncVehicle() {
        long v = vehicle("EB-S01", 1);
        Reservation r = service.reserveVehicle(STUDENT, v);
        assertEquals("RESERVED", vehicleState(v));

        service.cancel(r.getId(), STUDENT);
        assertEquals("AVAILABLE", vehicleState(v));
    }

    @Test
    @DisplayName("TC-M2-18: Timer expires the reservation -> vehicle AVAILABLE again in M3")
    void expirySyncsVehicle() {
        long v = vehicle("EB-S02", 1);
        Reservation r = service.reserveVehicle(STUDENT, v);

        setNow(T0.plusMinutes(15));
        service.expire(r.getId());
        assertEquals("AVAILABLE", vehicleState(v));
    }

    @Test
    @DisplayName("TC-M2-19: Vehicle fails with a replacement -> replacement RESERVED in M3")
    void replacementIsReserved() {
        long failed = vehicle("EB-S03", 2);
        long spare = vehicle("EB-S04", 2);
        service.reserveVehicle(STUDENT, failed);

        handler.onVehicleFailed(new VehicleFailedEvent(failed, 2L, "TEST"));
        assertEquals("RESERVED", vehicleState(spare));
    }
}
