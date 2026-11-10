package com.hcmut.emobility.m7dispatch.repository;

import com.hcmut.emobility.m7dispatch.domain.RedistributionTask;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RedistributionTaskRepository extends JpaRepository<RedistributionTask, Long> {

    /** BR-M7-02: nhiem vu dang hoat dong (CREATED hoac IN_PROGRESS) cua mot xe. */
    @Query("""
           SELECT t FROM RedistributionTask t
           WHERE t.vehicleId = :vehicleId
             AND t.status IN (com.hcmut.emobility.m7dispatch.domain.RedistributionTaskStatus.CREATED,
                              com.hcmut.emobility.m7dispatch.domain.RedistributionTaskStatus.IN_PROGRESS)
           """)
    Optional<RedistributionTask> findActiveByVehicleId(@Param("vehicleId") Long vehicleId);

    List<RedistributionTask> findByStatusOrderByCreatedAtDesc(RedistributionTaskStatus status);

    List<RedistributionTask> findAllByOrderByCreatedAtDesc();
}
