package com.hcmut.emobility.m1monitoring.repository;

import com.hcmut.emobility.m1monitoring.domain.Hub;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HubRepository extends JpaRepository<Hub, Long> {

    List<Hub> findAllByOrderByIdAsc();

    /**
     * BR-M1-08: chi xe o trang thai AVAILABLE moi tinh la kha dung.
     * Truy van thang bang rental_vehicle de M1 khong phu thuoc vao lop cua M3.
     */
    @Query(value = """
           SELECT h.id,
                  COUNT(v.id) FILTER (WHERE v.state = 'AVAILABLE')
           FROM hub h
           LEFT JOIN rental_vehicle v ON v.current_hub_id = h.id
           GROUP BY h.id
           """, nativeQuery = true)
    List<Object[]> countAvailableVehiclesPerHub();

    @Query(value = """
           SELECT v.id, v.code, v.type, v.battery_level, v.state
           FROM rental_vehicle v
           WHERE v.current_hub_id = :hubId
           ORDER BY v.code
           """, nativeQuery = true)
    List<Object[]> findVehiclesOfHub(@Param("hubId") Long hubId);

    @Query(value = """
           SELECT v.state, COUNT(*) FROM rental_vehicle v GROUP BY v.state ORDER BY v.state
           """, nativeQuery = true)
    List<Object[]> findVehicleCountByState();
}
