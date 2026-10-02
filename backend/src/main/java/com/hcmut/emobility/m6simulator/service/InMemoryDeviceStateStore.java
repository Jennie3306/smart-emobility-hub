package com.hcmut.emobility.m6simulator.service;

import com.hcmut.emobility.m6simulator.domain.DeviceStateSnapshot;
import com.hcmut.emobility.m6simulator.domain.ResourceKey;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** M6-owned, process-local latest state for the walking skeleton. */
@Component
public class InMemoryDeviceStateStore {

    private final Map<ResourceKey, DeviceStateSnapshot> latest = new ConcurrentHashMap<>();

    public void save(DeviceStateSnapshot snapshot) {
        latest.put(new ResourceKey(snapshot.resourceType(), snapshot.resourceId()), snapshot);
    }

    public Optional<DeviceStateSnapshot> find(ResourceKey key) {
        return Optional.ofNullable(latest.get(key));
    }

    public Map<ResourceKey, DeviceStateSnapshot> snapshot() {
        return Map.copyOf(latest);
    }
}
