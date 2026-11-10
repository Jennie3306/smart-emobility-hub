package com.hcmut.emobility.m5charging.repository;

import com.hcmut.emobility.m5charging.domain.ChargingPoint;
import com.hcmut.emobility.m5charging.domain.ChargingPointState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChargingPointRepository extends JpaRepository<ChargingPoint, Long> {

    List<ChargingPoint> findByHubIdOrderById(Long hubId);

    List<ChargingPoint> findByHubIdAndStateInOrderById(Long hubId, Collection<ChargingPointState> states);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ChargingPoint p where p.hubId = :hubId and p.state = :state order by p.id")
    List<ChargingPoint> lockByHubAndState(@Param("hubId") Long hubId,
                                          @Param("state") ChargingPointState state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ChargingPoint p where p.hubId = :hubId and p.state in :states order by p.id")
    List<ChargingPoint> lockByHubAndStates(@Param("hubId") Long hubId,
                                           @Param("states") Collection<ChargingPointState> states);
}
