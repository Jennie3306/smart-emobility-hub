package com.hcmut.emobility.m6simulator.service;

import com.hcmut.emobility.m6simulator.domain.ResourceKey;
import com.hcmut.emobility.m6simulator.domain.ResourceType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * The resources the simulator reports on, with their Hub: vehicle 1 (EB-001), parking space 1 (P-01) and
 * charging point 1, all at Hub 1. They are rows of the demo seed; under contract v2 their owners decide what a
 * reading changes (M3 the vehicle, M5 the charging point; M4 takes space occupancy from bookings, not sensors).
 * A reading for any other resource is rejected (TC-M6-07).
 */
@Component
public class DemoDeviceCatalog {

    private final Map<ResourceKey, Long> hubs = Map.of(
            new ResourceKey(ResourceType.VEHICLE, 1L), 1L,
            new ResourceKey(ResourceType.PARKING_SPACE, 1L), 1L,
            new ResourceKey(ResourceType.CHARGING_POINT, 1L), 1L);

    public Optional<Long> hubFor(ResourceType resourceType, Long resourceId) {
        if (resourceType == null || resourceId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(hubs.get(new ResourceKey(resourceType, resourceId)));
    }
}
