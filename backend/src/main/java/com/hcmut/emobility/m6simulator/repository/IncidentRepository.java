package com.hcmut.emobility.m6simulator.repository;

import com.hcmut.emobility.m6simulator.domain.Incident;
import com.hcmut.emobility.m6simulator.domain.IncidentStatus;
import com.hcmut.emobility.m6simulator.domain.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    /** BR-M6-06: at most one active Incident (REPORTED, ASSIGNED, IN_PROGRESS) per resource. */
    Optional<Incident> findFirstByResourceTypeAndResourceIdAndStatusIn(
            ResourceType resourceType, Long resourceId, Collection<IncidentStatus> statuses);

    List<Incident> findAllByOrderByReportedAtDescIdDesc();
}
