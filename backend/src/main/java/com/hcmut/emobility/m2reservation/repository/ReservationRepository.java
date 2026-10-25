package com.hcmut.emobility.m2reservation.repository;

import com.hcmut.emobility.m2reservation.domain.Reservation;
import com.hcmut.emobility.m2reservation.domain.ReservationState;
import com.hcmut.emobility.m2reservation.domain.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findFirstByUserIdAndResourceTypeAndStateIn(
            Long userId, ResourceType type, Collection<ReservationState> states);

    Optional<Reservation> findFirstByResourceTypeAndResourceIdAndStateIn(
            ResourceType type, Long resourceId, Collection<ReservationState> states);

    /** FR-M2-05: CONFIRMED reservations with expiresAt <= now (BR-M2-07). */
    @Query("select r.id from Reservation r"
            + " where r.state = com.hcmut.emobility.m2reservation.domain.ReservationState.CONFIRMED"
            + " and r.expiresAt <= :now")
    List<Long> findOverdueIds(@Param("now") OffsetDateTime now);
}
