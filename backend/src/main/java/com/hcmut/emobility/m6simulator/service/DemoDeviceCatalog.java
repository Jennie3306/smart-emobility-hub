package com.hcmut.emobility.m6simulator.service;

import com.hcmut.emobility.m6simulator.domain.ResourceKey;
import com.hcmut.emobility.m6simulator.domain.ResourceType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Synthetic IDs for local scenarios. Vehicle 1 matches the existing M3 demo
 * seed; parking and charging-point IDs are M6-only fixtures, not database rows.
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
