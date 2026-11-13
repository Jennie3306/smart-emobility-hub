package com.hcmut.emobility.m4parking.repository;

import com.hcmut.emobility.m4parking.domain.PrivateVehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrivateVehicleRepository extends JpaRepository<PrivateVehicle, Long> {

    List<PrivateVehicle> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    boolean existsByLicensePlate(String licensePlate);
}
