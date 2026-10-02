package com.hcmut.emobility.m6simulator.service;

import com.hcmut.emobility.m6simulator.domain.DeviceReading;
import com.hcmut.emobility.m6simulator.domain.ResourceType;
import com.hcmut.emobility.m6simulator.domain.SimulatorScenario;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Produces repeatable demo content with a fresh inbound ID per reading. */
@Component
public class SyntheticReadingFactory {

    public List<DeviceReading> createReadings(SimulatorScenario scenario, long seed) {
        Random random = new Random(seed);
        OffsetDateTime now = OffsetDateTime.now();
        return switch (scenario) {
            case NORMAL_TICK -> List.of(
                    reading(1L, ResourceType.VEHICLE, 1L, "AVAILABLE", random.nextInt(101), null, now),
                    reading(1L, ResourceType.PARKING_SPACE, 1L, "FREE", null, null, now),
                    reading(1L, ResourceType.CHARGING_POINT, 1L, "AVAILABLE", null, null, now));
            case VEHICLE_FAILURE -> List.of(
                    reading(1L, ResourceType.VEHICLE, 1L, "FAULTY", 0, "MECHANICAL", now));
            case CHARGER_FAILURE -> List.of(
                    reading(1L, ResourceType.CHARGING_POINT, 1L, "FAULTY", null, "OFFLINE", now));
        };
    }

    private static DeviceReading reading(Long hubId, ResourceType resourceType, Long resourceId,
                                         String state, Integer battery, String failureReason,
                                         OffsetDateTime observedAt) {
        return new DeviceReading(UUID.randomUUID(), hubId, resourceType, resourceId,
                state, battery, failureReason, observedAt);
    }
}
