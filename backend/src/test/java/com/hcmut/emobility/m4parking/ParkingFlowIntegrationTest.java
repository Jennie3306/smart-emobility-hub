package com.hcmut.emobility.m4parking;

import com.hcmut.emobility.m2reservation.service.ReservationService;
import com.hcmut.emobility.m4parking.domain.ParkingBookingLog;
import com.hcmut.emobility.m4parking.domain.PrivateVehicle;
import com.hcmut.emobility.m4parking.dto.BookParkingRequest;
import com.hcmut.emobility.m4parking.dto.ParkingBookingResponse;
import com.hcmut.emobility.m4parking.dto.RegisterVehicleRequest;
import com.hcmut.emobility.m4parking.service.ParkingService;
import com.hcmut.emobility.shared.events.ChargerFailedEvent;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Tich hop M4 <-> M2 qua Reservation core va event bus (H2, khong mock repository):
 * kiem tra query JPQL, mapping entity va listener cua M4 chay dung khi M2 phat su kien.
 * Lien quan: FR-M4-02..04, BR-M4-04, BR-M4-06; TC-M4-36..38.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.reservation.expiry-scan-ms=3600000",
        "app.parking.no-show-scan-ms=3600000"
})
class ParkingFlowIntegrationTest {

    static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-20T08:00:00+07:00");
    static final long STUDENT = 401L;
    static final long HUB = 9400L;
    static final long PLAIN_SPACE = 9401L;
    static final long CHARGER_SPACE = 9402L;
    static final long CHARGER = 9499L;

    @Autowired ParkingService parking;
    @Autowired ReservationService reservations;
    @Autowired EventPublisher events;
    @Autowired JdbcTemplate jdbc;
    @MockBean Clock clock;

    @BeforeEach
    void seed() {
        setNow(T0);
        // Schema H2 sinh tu entity: bang hub (entity cua M1) khong co cot parking_slots cua V1.
        jdbc.execute("ALTER TABLE hub ADD COLUMN IF NOT EXISTS parking_slots INT DEFAULT 40");
        jdbc.update("DELETE FROM parking_booking_log");
        jdbc.update("DELETE FROM parking_space WHERE hub_id = ?", HUB);
        jdbc.update("DELETE FROM personal_vehicle WHERE student_id = ?", STUDENT);
        jdbc.update("DELETE FROM reservation WHERE user_id = ?", STUDENT);
        jdbc.update("DELETE FROM hub WHERE id = ?", HUB);
        jdbc.update("INSERT INTO hub(id, name, location, parking_slots) VALUES (?, 'M4 Test Hub', 'Test', 2)", HUB);
        jdbc.update("INSERT INTO parking_space(id, hub_id, space_code, type, status, version) "
                + "VALUES (?, ?, 'P-01', 'BIKE', 'FREE', 0)", PLAIN_SPACE, HUB);
        jdbc.update("INSERT INTO parking_space(id, hub_id, space_code, type, status, charger_point_id, version) "
                + "VALUES (?, ?, 'P-02', 'BIKE', 'FREE', ?, 0)", CHARGER_SPACE, HUB, CHARGER);
    }

    void setNow(OffsetDateTime now) {
        when(clock.instant()).thenReturn(now.toInstant());
        when(clock.getZone()).thenReturn(ZoneOffset.ofHours(7));
    }

    long registerVehicle(String plate) {
        return parking.registerVehicle(STUDENT,
                new RegisterVehicleRequest(plate, PrivateVehicle.VehicleType.E_MOTORBIKE, "Evo200", 40)).id();
    }

    ParkingBookingResponse book(long vehicleId, Long spaceId) {
        return parking.book(STUDENT, new BookParkingRequest(HUB, T0.plusHours(1), T0.plusHours(3),
                ParkingBookingLog.VehicleKind.PERSONAL, vehicleId, null, spaceId, false, null));
    }

    String spaceStatus(long spaceId) {
        return jdbc.queryForObject("SELECT status FROM parking_space WHERE id = ?", String.class, spaceId);
    }

    String reservationState(long reservationId) {
        return jdbc.queryForObject("SELECT state FROM reservation WHERE id = ?", String.class, reservationId);
    }

    String bookingStatus(long bookingId) {
        return jdbc.queryForObject("SELECT status FROM parking_booking_log WHERE id = ?", String.class, bookingId);
    }

    @Test
    @DisplayName("TC-M4-36 (UC-M4-04): khong check-in trong 15 phut -> Timer M2 EXPIRED -> M4 tra cho ve FREE, NO_SHOW")
    void noShowThroughM2Timer() {
        ParkingBookingResponse booking = book(registerVehicle("59X1-90001"), null);
        assertEquals("RESERVED", spaceStatus(booking.spaceId()));
        assertEquals("CONFIRMED", reservationState(booking.reservationId()));
        OffsetDateTime expiresAt = jdbc.queryForObject("SELECT expires_at FROM reservation WHERE id = ?",
                OffsetDateTime.class, booking.reservationId());
        assertTrue(T0.plusHours(1).plusMinutes(15).isEqual(expiresAt), "M2 giu cho den gio bat dau + 15 phut");

        setNow(T0.plusHours(1).plusMinutes(16));
        assertTrue(reservations.expire(booking.reservationId()));

        assertEquals("EXPIRED", reservationState(booking.reservationId()));
        assertEquals("FREE", spaceStatus(booking.spaceId()));
        assertEquals("NO_SHOW", bookingStatus(booking.id()));
    }

    @Test
    @DisplayName("TC-M4-37 (FR-M4-03): check-in dung han roi check-out -> reservation COMPLETED, cho FREE")
    void checkInThenCheckOut() {
        ParkingBookingResponse booking = book(registerVehicle("59X1-90002"), PLAIN_SPACE);

        setNow(T0.plusHours(1).plusMinutes(5));
        parking.checkIn(STUDENT, booking.id());
        assertEquals("OCCUPIED", spaceStatus(PLAIN_SPACE));
        assertEquals("CHECKED_IN", reservationState(booking.reservationId()));

        setNow(T0.plusHours(2));
        parking.checkOut(STUDENT, booking.id());
        assertEquals("FREE", spaceStatus(PLAIN_SPACE));
        assertEquals("COMPLETED", reservationState(booking.reservationId()));
        assertEquals("COMPLETED", bookingStatus(booking.id()));
    }

    @Test
    @DisplayName("TC-M4-38 (BR-M4-06): tru sac hong -> cho BLOCKED, reservation CANCELLED; tru sac tot lai -> FREE")
    void chargerFailureAndRecovery() {
        ParkingBookingResponse booking = book(registerVehicle("59X1-90003"), CHARGER_SPACE);

        events.publish(new ChargerFailedEvent(CHARGER, HUB, "OFFLINE"));
        assertEquals("BLOCKED", spaceStatus(CHARGER_SPACE));
        assertEquals("CANCELLED", reservationState(booking.reservationId()));
        assertEquals("CANCELLED", bookingStatus(booking.id()));
        assertEquals("RESOURCE_BLOCKED",
                jdbc.queryForObject("SELECT cancel_reason FROM reservation WHERE id = ?", String.class,
                        booking.reservationId()));

        // So doc thiet bi cua M6 (source = "M6"): M4 chi mo cho khi tin hieu den tu thiet bi.
        events.publish(new ResourceStateUpdatedEvent(HUB, "CHARGING_POINT", CHARGER, "AVAILABLE",
                ResourceStateUpdatedEvent.SOURCE_DEVICE));
        assertEquals("FREE", spaceStatus(CHARGER_SPACE));
    }

    @Test
    @DisplayName("TC-M4-40: qua gio roi bai 15 phut ma chua check-out -> Timer tu check-out, reservation COMPLETED, cho FREE")
    void automaticCheckOutAtWindowEnd() {
        ParkingBookingResponse booking = book(registerVehicle("59X1-90004"), PLAIN_SPACE);
        setNow(T0.plusHours(1));
        parking.checkIn(STUDENT, booking.id());

        setNow(T0.plusHours(3).plusMinutes(16)); // gio roi bai T0+3h, them 15 phut cho phep
        assertTrue(parking.overstayBookingIds().contains(booking.id()));
        assertTrue(parking.autoCheckOut(booking.id()));

        assertEquals("FREE", spaceStatus(PLAIN_SPACE));
        assertEquals("COMPLETED", reservationState(booking.reservationId()));
        assertEquals("COMPLETED", bookingStatus(booking.id()));
    }
}
