package com.hcmut.emobility.m5charging;

import com.hcmut.emobility.m5charging.domain.ChargingSessionStatus;
import com.hcmut.emobility.m5charging.domain.VehicleKind;
import com.hcmut.emobility.m5charging.dto.ChargingRequest;
import com.hcmut.emobility.m5charging.dto.ChargingSessionResponse;
import com.hcmut.emobility.m5charging.service.ChargingService;
import com.hcmut.emobility.shared.common.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Charging allocation through ChargingService on H2 (FR-M5-01, 04, 05, 06, 08; BR-M5-02..04, 10).
 * Hub 77 is private to this class; ports 771 and 772 are its charging points.
 * Related: TC-M5-09..15.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChargingFlowIntegrationTest {

    private static final long HUB = 77L, P1 = 771L, P2 = 772L;
    private static final long STUDENT = 7701L, OTHER = 7702L;
    private static final long CAR_A = 7711L, CAR_B = 7712L;

    @Autowired ChargingService charging;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM charging_session WHERE hub_id = ?", HUB);
        jdbc.update("DELETE FROM charging_point WHERE hub_id = ?", HUB);
        jdbc.update("INSERT INTO hub(id, name, location) SELECT ?, 'M5 test hub', 'Test' "
                + "WHERE NOT EXISTS (SELECT 1 FROM hub WHERE id = ?)", HUB, HUB);
        jdbc.update("DELETE FROM personal_vehicle WHERE id IN (?, ?)", CAR_A, CAR_B);
        jdbc.update("INSERT INTO personal_vehicle(id, student_id, license_plate, vehicle_type, model, battery_level, status, created_at) "
                + "VALUES (?, ?, 'M5-TEST-A', 'E_MOTORBIKE', 'Test', 30, 'ACTIVE', CURRENT_TIMESTAMP)", CAR_A, STUDENT);
        jdbc.update("INSERT INTO personal_vehicle(id, student_id, license_plate, vehicle_type, model, battery_level, status, created_at) "
                + "VALUES (?, ?, 'M5-TEST-B', 'E_MOTORBIKE', 'Test', 30, 'ACTIVE', CURRENT_TIMESTAMP)", CAR_B, OTHER);
    }

    private void ports(long... ids) {
        for (long id : ids) {
            jdbc.update("INSERT INTO charging_point(id, hub_id, state, version) VALUES (?, ?, 'AVAILABLE', 0)", id, HUB);
        }
    }

    /** JPA changes of the service are flushed before the JDBC checks read the tables. */
    private <T> T read(Class<T> type, String sql, Object... args) {
        em.flush();
        return jdbc.queryForObject(sql, type, args);
    }

    private String portState(long id) {
        em.flush();
        return read(String.class, "SELECT state FROM charging_point WHERE id = ?", id);
    }

    private ChargingSessionResponse request(long user, long vehicle, int battery) {
        return charging.request(user, new ChargingRequest(HUB, VehicleKind.PERSONAL, vehicle, battery, null, null));
    }

    private ChargingSessionStatus rentalSessionStatus(long vehicleId) {
        return ChargingSessionStatus.valueOf(read(String.class,
                "SELECT status FROM charging_session WHERE vehicle_kind = 'RENTAL' AND vehicle_id = ? AND hub_id = ?",
                vehicleId, HUB));
    }

    @Test
    @DisplayName("TC-M5-09 (FR-M5-01, FR-M5-04): a request at a hub with a free port is assigned at once, the port is RESERVED")
    void requestGetsAFreePort() {
        ports(P1);

        ChargingSessionResponse s = request(STUDENT, CAR_A, 30);

        assertEquals(ChargingSessionStatus.SCHEDULED, s.status());
        assertEquals(P1, s.chargingPointId());
        assertNotNull(s.holdExpiresAt());
        assertEquals("RESERVED", portState(P1));
    }

    @Test
    @DisplayName("TC-M5-10 (BR-M5-03, BR-M5-04): 90% battery and a second open session are refused")
    void fullBatteryAndDuplicateSessionAreRefused() {
        ports(P1);

        BusinessException full = assertThrows(BusinessException.class, () -> request(STUDENT, CAR_A, 90));
        assertEquals("BATTERY_ALREADY_FULL", full.getCode());

        request(STUDENT, CAR_A, 30);
        BusinessException twice = assertThrows(BusinessException.class, () -> request(STUDENT, CAR_A, 30));
        assertEquals("ACTIVE_SESSION_EXISTS", twice.getCode());
    }

    @Test
    @DisplayName("TC-M5-11 (FR-M5-08, BR-M5-03): a rental vehicle returned below 20% is queued automatically, once")
    void lowBatteryReturnQueuesTheVehicleOnce() {
        ports(P1);

        charging.handleVehicleReturned(7790L, HUB, 25);
        assertEquals(0, read(Integer.class, "SELECT COUNT(*) FROM charging_session WHERE vehicle_id = 7790 AND hub_id = ?", HUB));

        charging.handleVehicleReturned(7791L, HUB, 15);
        charging.handleVehicleReturned(7791L, HUB, 15);
        assertEquals(1, read(Integer.class, "SELECT COUNT(*) FROM charging_session WHERE vehicle_id = 7791 AND hub_id = ?", HUB));
        assertEquals(ChargingSessionStatus.SCHEDULED, rentalSessionStatus(7791L));
    }

    @Test
    @DisplayName("TC-M5-12 (FR-M5-04, BR-M5-02): when the only port is freed, the waiting session with the lower battery gets it")
    void freedPortGoesToTheHighestPriority() {
        ports(P1);
        ChargingSessionResponse holder = request(STUDENT, CAR_A, 60);
        charging.handleVehicleReturned(7792L, HUB, 18);   // P = 0.5 * 82 = 41
        charging.handleVehicleReturned(7793L, HUB, 5);    // P = 0.5 * 95 = 47.5
        assertEquals(ChargingSessionStatus.QUEUED, rentalSessionStatus(7792L));
        assertEquals(ChargingSessionStatus.QUEUED, rentalSessionStatus(7793L));

        charging.cancel(holder.id(), STUDENT, false);   // FR-M5-06: releases the port and re-runs the queue

        assertEquals(ChargingSessionStatus.SCHEDULED, rentalSessionStatus(7793L));
        assertEquals(ChargingSessionStatus.QUEUED, rentalSessionStatus(7792L));
    }

    @Test
    @DisplayName("TC-M5-13 (FR-M5-06, BR-M5-12): another student cannot cancel; a CHARGING session cannot be cancelled")
    void cancelRules() {
        ports(P1);
        ChargingSessionResponse s = request(STUDENT, CAR_A, 30);

        assertThrows(BusinessException.class, () -> charging.cancel(s.id(), OTHER, false));

        charging.start(s.id(), STUDENT, false);
        assertEquals("IN_USE", portState(P1));
        BusinessException e = assertThrows(BusinessException.class, () -> charging.cancel(s.id(), STUDENT, false));
        assertEquals("INVALID_STATE_TRANSITION", e.getCode());
    }

    @Test
    @DisplayName("TC-M5-14 (FR-M5-05, BR-M5-10): a charger fails during charging -> FAULTY, session moved to the other free port")
    void failedChargerMovesTheSessionToAnotherPort() {
        ports(P1, P2);
        ChargingSessionResponse s = request(STUDENT, CAR_A, 30);
        assertEquals(P1, s.chargingPointId());
        charging.start(s.id(), STUDENT, false);

        charging.handleChargerFailed(P1, HUB);

        assertEquals("FAULTY", portState(P1));
        assertEquals(P2, read(Long.class, "SELECT charging_point_id FROM charging_session WHERE id = ?", s.id()));
        assertEquals("SCHEDULED", read(String.class, "SELECT status FROM charging_session WHERE id = ?", s.id()));
    }

    @Test
    @DisplayName("TC-M5-15 (FR-M5-05, BR-M5-10): no replacement port -> back to the queue with +20 compensation")
    void failedChargerWithoutReplacementRequeuesWithBonus() {
        ports(P1);
        ChargingSessionResponse s = request(STUDENT, CAR_A, 30);
        charging.start(s.id(), STUDENT, false);

        charging.handleChargerFailed(P1, HUB);

        assertEquals("FAULTY", portState(P1));
        assertEquals("QUEUED", read(String.class, "SELECT status FROM charging_session WHERE id = ?", s.id()));
        assertEquals(20, read(Integer.class, "SELECT failure_bonus FROM charging_session WHERE id = ?", s.id()));
    }
}
