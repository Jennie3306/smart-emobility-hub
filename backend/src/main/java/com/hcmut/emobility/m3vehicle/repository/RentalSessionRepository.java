package com.hcmut.emobility.m3vehicle.repository;

import com.hcmut.emobility.m3vehicle.domain.RentalSession;
import com.hcmut.emobility.m3vehicle.domain.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RentalSessionRepository extends JpaRepository<RentalSession, Long> {

    Optional<RentalSession> findByVehicleIdAndStatus(Long vehicleId, SessionStatus status);

    Optional<RentalSession> findByUserIdAndStatus(Long userId, SessionStatus status);
}