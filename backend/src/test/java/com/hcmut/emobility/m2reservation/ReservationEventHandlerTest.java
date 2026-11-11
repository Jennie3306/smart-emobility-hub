package com.hcmut.emobility.m2reservation;

import com.hcmut.emobility.m2reservation.domain.Reservation;
import com.hcmut.emobility.m2reservation.domain.ReservationState;
import com.hcmut.emobility.m2reservation.repository.ReservationRepository;
import com.hcmut.emobility.m2reservation.repository.VehicleSearchRepository;
import com.hcmut.emobility.m2reservation.service.ReservationService;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.VehicleFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Technique: fault injection. Related: FR-M2-08, NFR-REL-02 (a failing handler must not break the publisher).
 * A failure inside M2's handling is simulated by making the replacement lookup throw. Before the fix, the
 * exception marked the shared transaction rollback-only and came back to the publisher as
 * UnexpectedRollbackException even though the listener caught it.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.reservation.expiry-scan-ms=3600000")
class ReservationEventHandlerTest {

    static final long STUDENT = 401L;

    @Autowired ReservationService service;
    @Autowired ReservationRepository repository;
    @Autowired EventPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @SpyBean VehicleSearchRepository vehicles;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM reservation");
        jdbc.update("DELETE FROM rental_vehicle");
    }

    long vehicle(String code, long hubId) {
        jdbc.update("INSERT INTO rental_vehicle(code, type, battery_level, state, current_hub_id, version)"
                + " VALUES (?, 'E_BIKE', 80, 'AVAILABLE', ?, 0)", code, hubId);
        return jdbc.queryForObject("SELECT id FROM rental_vehicle WHERE code = ?", Long.class, code);
    }

    void failInsideM2() {
        doThrow(new IllegalStateException("simulated M2 failure"))
                .when(vehicles).findReplacements(any(), any(), any());
    }

    @Test
    @DisplayName("TC-M2-21: Publisher without a transaction (M6) - an M2 failure is logged, not thrown back")
    void failureIsNotThrownBackToM6() {
        long v = vehicle("EB-H01", 1);
        Reservation r = service.reserveVehicle(STUDENT, v);
        failInsideM2();

        assertDoesNotThrow(() -> publisher.publish(new VehicleFailedEvent(v, 1L, "TEST")));
        // Only M2's own transaction was rolled back
        assertEquals(ReservationState.CONFIRMED, repository.findById(r.getId()).orElseThrow().getState());
    }

    @Test
    @DisplayName("TC-M2-22: Publisher inside a transaction (M3 style) - the publisher still commits when M2 fails")
    void publisherTransactionStillCommits() {
        long v = vehicle("EB-H02", 1);
        long marker = vehicle("EB-H03", 2);
        Reservation r = service.reserveVehicle(STUDENT, v);
        failInsideM2();

        TransactionTemplate publisherTx = new TransactionTemplate(transactionManager);
        assertDoesNotThrow(() -> publisherTx.executeWithoutResult(status -> {
            jdbc.update("UPDATE rental_vehicle SET battery_level = 55 WHERE id = ?", marker);
            publisher.publish(new VehicleFailedEvent(v, 1L, "TEST"));
        }));

        // The publisher's own write was committed...
        assertEquals(55, jdbc.queryForObject(
                "SELECT battery_level FROM rental_vehicle WHERE id = ?", Integer.class, marker));
        // ...and M2's failed attempt left the reservation untouched
        assertEquals(ReservationState.CONFIRMED, repository.findById(r.getId()).orElseThrow().getState());
    }
}
