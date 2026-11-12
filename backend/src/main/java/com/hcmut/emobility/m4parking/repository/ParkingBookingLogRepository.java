package com.hcmut.emobility.m4parking.repository;

import com.hcmut.emobility.m4parking.domain.ParkingBookingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ParkingBookingLogRepository extends JpaRepository<ParkingBookingLog, Long> {

    Optional<ParkingBookingLog> findByReservationId(Long reservationId);

    List<ParkingBookingLog> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<ParkingBookingLog> findTop50ByHubIdOrderByCreatedAtDesc(Long hubId);

    /** BR-M4-01: so luot dat o trang thai status tai Hub giao voi khung gio [startAt, endAt). */
    @Query("""
            select count(b) from ParkingBookingLog b
             where b.hubId = :hubId and b.status = :status
               and b.slotStart < :endAt and b.slotEnd > :startAt
            """)
    long countOverlapping(@Param("hubId") Long hubId,
                          @Param("status") ParkingBookingLog.Status status,
                          @Param("startAt") OffsetDateTime startAt,
                          @Param("endAt") OffsetDateTime endAt);

    /** BR-M4-03: so luot dat cua mot sinh vien tai cung Hub, giao khung gio. */
    @Query("""
            select count(b) from ParkingBookingLog b
             where b.studentId = :studentId and b.hubId = :hubId and b.status = :status
               and b.slotStart < :endAt and b.slotEnd > :startAt
            """)
    long countStudentOverlapping(@Param("studentId") Long studentId, @Param("hubId") Long hubId,
                                 @Param("status") ParkingBookingLog.Status status,
                                 @Param("startAt") OffsetDateTime startAt,
                                 @Param("endAt") OffsetDateTime endAt);

    boolean existsByPersonalVehicleIdAndStatus(Long personalVehicleId, ParkingBookingLog.Status status);

    /** UC-M4-04 (Timer): luot dat con o trang thai status, chua check-in va da qua han truoc moc before. */
    @Query("""
            select b from ParkingBookingLog b
             where b.status = :status and b.checkInTime is null and b.checkInDeadline < :before
            """)
    List<ParkingBookingLog> findOverdueNoShows(@Param("status") ParkingBookingLog.Status status,
                                               @Param("before") OffsetDateTime before);
}
