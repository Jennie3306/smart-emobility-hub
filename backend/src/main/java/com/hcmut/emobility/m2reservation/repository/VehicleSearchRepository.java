package com.hcmut.emobility.m2reservation.repository;

import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;
import com.hcmut.emobility.m3vehicle.domain.VehicleType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Read-only access to M3 rental vehicles - does not import any M3 service or repository (VD-16).
 * Extends Repository (not JpaRepository), so M2 has no save/delete on vehicles.
 * Vehicles that already have an active reservation (active_resource_key not NULL) are excluded,
 * so results stay correct even before M3 moves the vehicle to RESERVED.
 */
public interface VehicleSearchRepository extends Repository<RentalVehicle, Long> {

    String AVAILABLE = "com.hcmut.emobility.m3vehicle.domain.VehicleState.AVAILABLE";
    String NOT_RESERVED = " and not exists (select r.id from Reservation r"
            + " where r.resourceType = com.hcmut.emobility.m2reservation.domain.ResourceType.VEHICLE"
            + " and r.resourceId = v.id and r.activeResourceKey is not null)";

    Optional<RentalVehicle> findById(Long id);

    /** FR-M2-01: AVAILABLE vehicles filtered by hub, type and minimum battery; highest battery first. */
    @Query("select v from RentalVehicle v where v.state = " + AVAILABLE
            + " and (:hubId is null or v.currentHubId = :hubId)"
            + " and (:type is null or v.type = :type)"
            + " and v.batteryLevel >= :minBattery"
            + NOT_RESERVED
            + " order by v.batteryLevel desc")
    List<RentalVehicle> searchAvailable(@Param("hubId") Long hubId,
                                        @Param("type") VehicleType type,
                                        @Param("minBattery") int minBattery);

    /** FR-M2-10: bookable vehicle count per hub, busiest hub first. */
    @Query("select v.currentHubId as hubId, count(v) as availableCount from RentalVehicle v"
            + " where v.state = " + AVAILABLE
            + " and v.currentHubId is not null"
            + " and (:type is null or v.type = :type)"
            + " and v.batteryLevel >= :minBattery"
            + NOT_RESERVED
            + " group by v.currentHubId order by count(v) desc")
    List<HubAvailability> countAvailableByHub(@Param("type") VehicleType type,
                                              @Param("minBattery") int minBattery);

    /** FR-M2-08, BR-M2-08: replacement vehicles at the same hub, same type, currently AVAILABLE. */
    @Query("select v from RentalVehicle v where v.state = " + AVAILABLE
            + " and v.currentHubId = :hubId and v.type = :type and v.id <> :excludeId"
            + NOT_RESERVED
            + " order by v.batteryLevel desc")
    List<RentalVehicle> findReplacements(@Param("hubId") Long hubId,
                                         @Param("type") VehicleType type,
                                         @Param("excludeId") Long excludeId);
}
