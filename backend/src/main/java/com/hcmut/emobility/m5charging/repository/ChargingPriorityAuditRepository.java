package com.hcmut.emobility.m5charging.repository;

import com.hcmut.emobility.m5charging.domain.ChargingPriorityAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChargingPriorityAuditRepository extends JpaRepository<ChargingPriorityAudit, Long> {
    List<ChargingPriorityAudit> findBySessionIdOrderByChangedAtDesc(Long sessionId);
}
