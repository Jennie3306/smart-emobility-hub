package com.hcmut.emobility.m3vehicle.repository;

import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;
import com.hcmut.emobility.m3vehicle.domain.VehicleState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RentalVehicleRepository extends JpaRepository<RentalVehicle, Long> {
    List<RentalVehicle> findByCurrentHubIdAndState(Long hubId, VehicleState state);
}
