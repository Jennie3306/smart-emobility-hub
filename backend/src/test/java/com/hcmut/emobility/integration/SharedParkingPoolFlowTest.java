package com.hcmut.emobility.integration;

import com.hcmut.emobility.m3vehicle.service.VehicleService;
import com.hcmut.emobility.m4parking.domain.ParkingBookingLog;
import com.hcmut.emobility.m4parking.domain.PrivateVehicle;
import com.hcmut.emobility.m4parking.dto.BookParkingRequest;
import com.hcmut.emobility.m4parking.dto.RegisterVehicleRequest;
import com.hcmut.emobility.m4parking.service.ParkingService;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTask;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTaskStatus;
import com.hcmut.emobility.m7dispatch.service.RedistributionService;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.events.ChargingCompletedEvent;
import com.hcmut.emobility.shared.events.ChargingStartedEvent;
import com.hcmut.emobility.shared.events.DeviceReadingReportedEvent;
import com.hcmut.emobility.shared.events.EventPublisher;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Appendix A v2 end to end on H2 (no mocked repositories):
 *  - BR-M4-08 / BR-M3-03 / BR-M7-03: one parking pool per Hub, shared by rental and personal vehicles (M3, M4, M7);
 *  - device readings go to the owner (M6 -> M3), charging lifecycle reaches the vehicle (M5 -> M3).
 * Hub A has 2 BIKE spaces (one with a charging point), Hub B has 1 BIKE space.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.reservation.expiry-scan-ms=3600000",
        "app.parking.no-show-scan-ms=3600000"
})
class SharedParkingPoolFlowTest {

    static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-20T08:00:00+07:00");
    static final long HUB_A = 9500L, HUB_B = 9600L;
    static final long SPACE_A_CHARGER = 9501L, SPACE_A_PLAIN = 9502L, SPACE_B = 9601L;
    static final long V1 = 9511L, V2 = 9512L, V3 = 9513L;
    static final long STUDENT_1 = 951L, STUDENT_2 = 952L, STUDENT_3 = 953L;

    @Autowired VehicleService vehicles;
    @Autowired ParkingService parking;
    @Autowired RedistributionService dispatch;
    @Autowired EventPublisher events;
    @Autowired JdbcTemplate jdbc;
    @MockBean Clock clock;

    @BeforeEach
    void seed() {
        when(clock.instant()).thenReturn(T0.toInstant());
        when(clock.getZone()).thenReturn(ZoneOffset.ofHours(7));
        jdbc.execute("ALTER TABLE hub ADD COLUMN IF NOT EXISTS parking_slots INT DEFAULT 40");
        jdbc.update("DELETE FROM redistribution_tasks WHERE vehicle_id IN (?, ?, ?)", V1, V2, V3);
        jdbc.update("DELETE FROM rental_session WHERE vehicle_id IN (?, ?, ?)", V1, V2, V3);
        jdbc.update("DELETE FROM parking_booking_log WHERE hub_id IN (?, ?)", HUB_A, HUB_B);
        jdbc.update("DELETE FROM parking_space WHERE hub_id IN (?, ?)", HUB_A, HUB_B);
        jdbc.update("DELETE FROM personal_vehicle WHERE student_id IN (?, ?, ?)", STUDENT_1, STUDENT_2, STUDENT_3);
        jdbc.update("DELETE FROM reservation WHERE user_id IN (?, ?, ?)", STUDENT_1, STUDENT_2, STUDENT_3);
        jdbc.update("DELETE FROM rental_vehicle WHERE id IN (?, ?, ?)", V1, V2, V3);
        jdbc.update("DELETE FROM hub WHERE id IN (?, ?)", HUB_A, HUB_B);
        jdbc.update("INSERT INTO hub(id, name, location, parking_slots) VALUES (?, 'Pool Hub A', 'Test', 2)", HUB_A);
        jdbc.update("INSERT INTO hub(id, name, location, parking_slots) VALUES (?, 'Pool Hub B', 'Test', 1)", HUB_B);
        jdbc.update("INSERT INTO parking_space(id, hub_id, space_code, type, status, charger_point_id, version) "
                + "VALUES (?, ?, 'P-01', 'BIKE', 'FREE', 9599, 0)", SPACE_A_CHARGER, HUB_A);
        jdbc.update("INSERT INTO parking_space(id, hub_id, space_code, type, status, version) "
                + "VALUES (?, ?, 'P-02', 'BIKE', 'FREE', 0)", SPACE_A_PLAIN, HUB_A);
        jdbc.update("INSERT INTO parking_space(id, hub_id, space_code, type, status, version) "
                + "VALUES (?, ?, 'P-01', 'BIKE', 'FREE', 0)", SPACE_B, HUB_B);
    }

    /** A vehicle reserved by the student, then picked up: IN_USE with an active trip. */
    void onTrip(long vehicleId, long studentId) {
        jdbc.update("INSERT INTO rental_vehicle(id, code, type, battery_level, state, current_hub_id, "
                + "reserved_by_user_id, version) VALUES (?, ?, 'E_BIKE', 80, 'RESERVED', ?, ?, 0)",
                vehicleId, "POOL-" + vehicleId, HUB_B, studentId);
        vehicles.pickUp(vehicleId, studentId);
    }

    void parkedAt(long vehicleId, long hubId, String state, int battery) {
        jdbc.update("INSERT INTO rental_vehicle(id, code, type, battery_level, state, current_hub_id, version) "
                + "VALUES (?, ?, 'E_BIKE', ?, ?, ?, 0)", vehicleId, "POOL-" + vehicleId, battery, state, hubId);
    }

    String spaceStatus(long spaceId) {
        return jdbc.queryForObject("SELECT status FROM parking_space WHERE id = ?", String.class, spaceId);
    }

    Long dockedOn(long spaceId) {
        return jdbc.queryForObject("SELECT docked_vehicle_id FROM parking_space WHERE id = ?", Long.class, spaceId);
    }

    String vehicleState(long vehicleId) {
        return jdbc.queryForObject("SELECT state FROM rental_vehicle WHERE id = ?", String.class, vehicleId);
    }

    @Test
    @DisplayName("TC-M3-11 (BR-M3-03, BR-M4-08): tra xe -> xe chiem mot cho trong quy chung, uu tien cho khong co tru sac")
    void returnedVehicleTakesASpace() {
        onTrip(V1, STUDENT_1);

        vehicles.returnVehicle(V1, HUB_A, 70, STUDENT_1);

        assertEquals("AVAILABLE", vehicleState(V1));
        assertEquals("OCCUPIED", spaceStatus(SPACE_A_PLAIN));
        assertEquals(V1, dockedOn(SPACE_A_PLAIN));
        assertEquals("FREE", spaceStatus(SPACE_A_CHARGER));
    }

    @Test
    @DisplayName("TC-M3-12 (UC-M3-02 ngoai le Hub day): xe thu hai tranh cho cuoi cung -> HUB_FULL kem Hub con cho")
    void secondReturnToAFullHubIsRejected() {
        jdbc.update("UPDATE parking_space SET status = 'BLOCKED', blocked_reason = 'OPERATOR' WHERE id = ?",
                SPACE_A_CHARGER);
        onTrip(V1, STUDENT_1);
        onTrip(V2, STUDENT_2);
        vehicles.returnVehicle(V1, HUB_A, 70, STUDENT_1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vehicles.returnVehicle(V2, HUB_A, 70, STUDENT_2));

        assertEquals("HUB_FULL", ex.getCode());
        assertTrue(ex.getMessage().contains("Pool Hub B"), ex.getMessage());
        assertEquals("IN_USE", vehicleState(V2));
    }

    @Test
    @DisplayName("TC-M3-13 (BR-M4-08): nhan xe -> cho cua xe tra lai quy chung")
    void pickUpFreesTheSpace() {
        onTrip(V1, STUDENT_1);
        vehicles.returnVehicle(V1, HUB_A, 70, STUDENT_1);
        jdbc.update("UPDATE rental_vehicle SET state = 'RESERVED', reserved_by_user_id = ? WHERE id = ?", STUDENT_2, V1);

        vehicles.pickUp(V1, STUDENT_2);

        assertEquals("FREE", spaceStatus(SPACE_A_PLAIN));
        assertNull(dockedOn(SPACE_A_PLAIN));
    }

    @Test
    @DisplayName("TC-M4-44 (BR-M4-01, BR-M4-08): xe thue dang dung o Hub chiem suc chua -> sinh vien xe ca nhan nhan HUB_FULL")
    void dockedRentalVehiclesCountAgainstBookingCapacity() {
        onTrip(V1, STUDENT_1);
        onTrip(V2, STUDENT_2);
        vehicles.returnVehicle(V1, HUB_A, 70, STUDENT_1);
        vehicles.returnVehicle(V2, HUB_A, 70, STUDENT_2);
        long personal = parking.registerVehicle(STUDENT_3,
                new RegisterVehicleRequest("59X1-95001", PrivateVehicle.VehicleType.E_MOTORBIKE, "Evo200", 40)).id();

        BusinessException ex = assertThrows(BusinessException.class, () -> parking.book(STUDENT_3,
                new BookParkingRequest(HUB_A, T0.plusHours(1), T0.plusHours(3),
                        ParkingBookingLog.VehicleKind.PERSONAL, personal, null, null, false, null)));

        assertEquals("HUB_FULL", ex.getCode());
        assertEquals(2, parking.availability(HUB_A, T0.plusHours(1), T0.plusHours(3)).dockedRentalVehicles());
    }

    @Test
    @DisplayName("TC-M7-13 (BR-M7-03): dieu chuyen -> roi cho o Hub nguon khi khoi hanh, chiem cho o Hub dich khi den")
    void redistributionMovesTheSpaceWithTheVehicle() {
        parkedAt(V1, HUB_A, "AVAILABLE", 80);
        jdbc.update("UPDATE parking_space SET status = 'OCCUPIED', docked_vehicle_id = ? WHERE id = ?", V1, SPACE_A_PLAIN);

        RedistributionTask task = dispatch.createRedistributionTask(V1, HUB_A, HUB_B);
        dispatch.updateTaskStatus(task.getTaskId(), RedistributionTaskStatus.IN_PROGRESS);
        assertEquals("FREE", spaceStatus(SPACE_A_PLAIN));
        assertEquals("IN_TRANSIT", vehicleState(V1));

        dispatch.updateTaskStatus(task.getTaskId(), RedistributionTaskStatus.DONE);
        assertEquals("OCCUPIED", spaceStatus(SPACE_B));
        assertEquals(V1, dockedOn(SPACE_B));
        assertEquals("AVAILABLE", vehicleState(V1));
        assertEquals(HUB_B, jdbc.queryForObject("SELECT current_hub_id FROM rental_vehicle WHERE id = ?", Long.class, V1));
    }

    @Test
    @DisplayName("TC-M7-14 (BR-M7-03): Hub dich chi con 1 cho va da co 1 xe dang toi -> DESTINATION_FULL")
    void secondTaskToTheLastSpaceIsRejected() {
        parkedAt(V1, HUB_A, "AVAILABLE", 80);
        parkedAt(V2, HUB_A, "AVAILABLE", 80);
        dispatch.createRedistributionTask(V1, HUB_A, HUB_B);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dispatch.createRedistributionTask(V2, HUB_A, HUB_B));

        assertEquals("DESTINATION_FULL", ex.getCode());
    }

    @Test
    @DisplayName("TC-M3-14 (FR non-interactive, Phu luc B): M6 bao pin 10% -> M3 chuyen xe AVAILABLE sang LOW_BATTERY")
    void lowBatteryReadingMovesVehicleToLowBattery() {
        parkedAt(V3, HUB_A, "AVAILABLE", 80);

        events.publish(new DeviceReadingReportedEvent(HUB_A, "VEHICLE", V3, "AVAILABLE", 10));

        assertEquals("LOW_BATTERY", vehicleState(V3));
        assertEquals(10, jdbc.queryForObject("SELECT battery_level FROM rental_vehicle WHERE id = ?", Integer.class, V3));
    }

    @Test
    @DisplayName("TC-M3-15 (state machine RentalVehicle): M5 bat dau sac -> CHARGING; sac xong -> AVAILABLE 90%")
    void chargingLifecycleReachesTheVehicle() {
        parkedAt(V3, HUB_A, "LOW_BATTERY", 12);

        events.publish(new ChargingStartedEvent(1L, HUB_A, 9599L, "RENTAL", V3));
        assertEquals("CHARGING", vehicleState(V3));

        events.publish(new ChargingCompletedEvent(1L, HUB_A, 9599L, "RENTAL", V3, 90));
        assertEquals("AVAILABLE", vehicleState(V3));
        assertEquals(List.of(90), jdbc.queryForList("SELECT battery_level FROM rental_vehicle WHERE id = ?", Integer.class, V3));
    }
}
