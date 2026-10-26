package com.hcmut.emobility.m1monitoring.repository;

import com.hcmut.emobility.m1monitoring.domain.ResourceState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResourceStateRepository
        extends JpaRepository<ResourceState, ResourceState.Key> {

    /**
     * Dem theo trang thai cho tat ca hub trong mot truy van.
     * BR-M1-01: cho do dang dung = OCCUPIED + RESERVED.
     * BR-M1-09: tru sac FAULTY va UNDER_REPAIR bi loai khoi mau so.
     */
    @Query(value = """
           SELECT r.hub_id,
                  COUNT(*) FILTER (WHERE r.resource_type = 'PARKING_SPACE'),
                  COUNT(*) FILTER (WHERE r.resource_type = 'PARKING_SPACE'
                                     AND r.state IN ('OCCUPIED', 'RESERVED')),
                  COUNT(*) FILTER (WHERE r.resource_type = 'PARKING_SPACE'
                                     AND r.state = 'FREE'),
                  COUNT(*) FILTER (WHERE r.resource_type = 'CHARGING_POINT'
                                     AND r.state NOT IN ('FAULTY', 'UNDER_REPAIR')),
                  COUNT(*) FILTER (WHERE r.resource_type = 'CHARGING_POINT'
                                     AND r.state = 'IN_USE'),
                  COUNT(*) FILTER (WHERE r.resource_type = 'CHARGING_POINT'
                                     AND r.state = 'FREE')
           FROM m1_resource_state r
           GROUP BY r.hub_id
           ORDER BY r.hub_id
           """, nativeQuery = true)
    List<Object[]> countByHub();

    Optional<ResourceState> findByResourceTypeAndResourceId(String resourceType, Long resourceId);

    List<ResourceState> findByHubIdAndResourceTypeOrderByResourceId(Long hubId, String resourceType);

    @Query("""
           SELECT r FROM ResourceState r
           WHERE r.hubId = :hubId AND r.resourceType = :resourceType
           ORDER BY r.resourceId
           """)
    List<ResourceState> findOfHub(@Param("hubId") Long hubId,
                                  @Param("resourceType") String resourceType);
}
