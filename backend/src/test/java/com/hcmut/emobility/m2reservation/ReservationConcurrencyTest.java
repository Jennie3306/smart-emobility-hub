package com.hcmut.emobility.m2reservation;

import com.hcmut.emobility.m2reservation.repository.ReservationRepository;
import com.hcmut.emobility.m2reservation.service.ReservationService;
import com.hcmut.emobility.shared.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Technique: concurrency testing. Related: FR-M2-06, BR-M2-01, NFR-CONS-01, TC-M2-02.
 * Runs on H2 in CI (UNIQUE generated from the entity); also run manually on PostgreSQL as evidence for section 5.4.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.reservation.expiry-scan-ms=3600000")
class ReservationConcurrencyTest {

    @Autowired ReservationService service;
    @Autowired ReservationRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("TC-M2-02: Two students book the same vehicle at once -> exactly 1 CONFIRMED, the other rejected")
    void twoStudentsSameVehicle() throws Exception {
        jdbc.update("DELETE FROM reservation");
        jdbc.update("DELETE FROM rental_vehicle");
        jdbc.update("INSERT INTO rental_vehicle(code, type, battery_level, state, current_hub_id, version)"
                + " VALUES ('EB-C01', 'E_BIKE', 80, 'AVAILABLE', 4, 0)");
        long vehicleId = jdbc.queryForObject("SELECT id FROM rental_vehicle WHERE code = 'EB-C01'", Long.class);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = pool.submit(attempt(201L, vehicleId, start));
            Future<String> b = pool.submit(attempt(202L, vehicleId, start));
            start.countDown();   // release both threads at the same time

            List<String> results = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            // Bat bien cua BR-M2-01: dung mot nguoi dat duoc, nguoi con lai bi tu choi.
            // Ma tu choi phu thuoc thoi diem: chen trung khoa (RESOURCE_CONFLICT) hoac
            // den sau khi M3 da giu xe (VEHICLE_NOT_AVAILABLE).
            assertThat(results).filteredOn("OK"::equals).hasSize(1);
            assertThat(results).filteredOn(r -> !"OK".equals(r))
                    .allMatch(r -> r.equals("RESOURCE_CONFLICT") || r.equals("VEHICLE_NOT_AVAILABLE")
                            || r.equals("CONCURRENT_UPDATE") || r.equals("LOCK_CONFLICT"));
            assertThat(repository.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<String> attempt(long userId, long vehicleId, CountDownLatch start) {
        return () -> {
            start.await();
            try {
                service.reserveVehicle(userId, vehicleId);
                return "OK";
            } catch (BusinessException e) {
                return e.getCode();
            } catch (ConcurrencyFailureException e) {
                // khoa bi giu khi M3 flush trang thai xe trong cung giao dich
                return "LOCK_CONFLICT";
            }
        };
    }
}
