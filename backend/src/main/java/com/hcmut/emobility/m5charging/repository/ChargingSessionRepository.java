package com.hcmut.emobility.m5charging.repository;

import com.hcmut.emobility.m5charging.domain.ChargingSession;
import com.hcmut.emobility.m5charging.domain.ChargingSessionStatus;
import com.hcmut.emobility.m5charging.domain.VehicleKind;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChargingSessionRepository extends JpaRepository<ChargingSession, Long> {

    List<ChargingSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ChargingSession> findByHubIdOrderByCreatedAtDesc(Long hubId);

    List<ChargingSession> findByHubIdAndStatusInOrderByCreatedAtAsc(
            Long hubId, Collection<ChargingSessionStatus> statuses);

    List<ChargingSession> findByStatusIn(Collection<ChargingSessionStatus> statuses);

    List<ChargingSession> findByChargingPointIdAndStatusIn(
            Long chargingPointId, Collection<ChargingSessionStatus> statuses);

    List<ChargingSession> findByParkingSpaceIdAndStatusIn(
            Long parkingSpaceId, Collection<ChargingSessionStatus> statuses);

    boolean existsByVehicleKindAndVehicleIdAndStatusIn(
            VehicleKind vehicleKind, Long vehicleId, Collection<ChargingSessionStatus> statuses);

    List<ChargingSession> findByStatusAndHoldExpiresAtLessThanEqual(
            ChargingSessionStatus status, OffsetDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChargingSession s where s.id = :id")
    Optional<ChargingSession> lockById(@Param("id") Long id);
}
