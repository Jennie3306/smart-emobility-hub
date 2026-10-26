package com.hcmut.emobility.m1monitoring.repository;

import com.hcmut.emobility.m1monitoring.domain.Alert;
import com.hcmut.emobility.m1monitoring.domain.AlertState;
import com.hcmut.emobility.m1monitoring.domain.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    /** BR-M1-06: moi hub chi co toi da mot canh bao dang mo cho moi loai. */
    @Query("""
           SELECT a FROM Alert a
           WHERE a.hubId = :hubId AND a.type = :type
             AND a.state IN (com.hcmut.emobility.m1monitoring.domain.AlertState.OPEN,
                             com.hcmut.emobility.m1monitoring.domain.AlertState.ACKNOWLEDGED)
           """)
    Optional<Alert> findOpenByHubAndType(@Param("hubId") Long hubId, @Param("type") AlertType type);

    @Query("""
           SELECT a FROM Alert a
           WHERE a.state IN (com.hcmut.emobility.m1monitoring.domain.AlertState.OPEN,
                             com.hcmut.emobility.m1monitoring.domain.AlertState.ACKNOWLEDGED)
           ORDER BY a.raisedAt DESC
           """)
    List<Alert> findOpen();

    List<Alert> findByRaisedAtBetweenOrderByRaisedAtDesc(OffsetDateTime from, OffsetDateTime to);

    List<Alert> findByHubIdAndRaisedAtBetweenOrderByRaisedAtDesc(
            Long hubId, OffsetDateTime from, OffsetDateTime to);

    long countByState(AlertState state);
}
