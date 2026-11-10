package com.hcmut.emobility.m7dispatch.repository;

import com.hcmut.emobility.m7dispatch.domain.RedistributionTask;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface RedistributionTaskRepository extends JpaRepository<RedistributionTask, String> {
    
    // Câu query đơn giản dễ giải thích lúc bảo vệ đồ án
    @Query("SELECT t FROM RedistributionTask t WHERE t.vehicleId = :vehicleId AND t.status IN ('CREATED', 'IN_PROGRESS')")
    Optional<RedistributionTask> findActiveByVehicleId(@Param("vehicleId") String vehicleId);
    
    List<RedistributionTask> findByStatus(RedistributionTaskStatus status);
}