package com.hcmut.emobility.m6simulator.service;

import com.hcmut.emobility.m6simulator.domain.DeviceReading;
import com.hcmut.emobility.m6simulator.domain.ResourceType;
import com.hcmut.emobility.m6simulator.domain.SimulatorScenario;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticReadingFactoryTest {

    private final SyntheticReadingFactory factory = new SyntheticReadingFactory();

    @Test
    void sameSeedProducesSameScenarioContentButFreshIds() {
        List<DeviceReading> first = factory.createReadings(SimulatorScenario.NORMAL_TICK, 42);
        List<DeviceReading> second = factory.createReadings(SimulatorScenario.NORMAL_TICK, 42);

        assertEquals(first.stream().map(SyntheticReadingFactoryTest::content).toList(),
                second.stream().map(SyntheticReadingFactoryTest::content).toList());
        assertNotEquals(first.getFirst().eventId(), second.getFirst().eventId());
        assertNotNull(first.getFirst().observedAt());
        assertNotNull(second.getFirst().observedAt());
    }

    @Test
    void normalTickContainsAllThreeResourceTypesAndVehicleBattery() {
        List<DeviceReading> readings = factory.createReadings(SimulatorScenario.NORMAL_TICK, 42);

        assertEquals(3, readings.size());
        assertEquals(List.of(ResourceType.VEHICLE, ResourceType.PARKING_SPACE, ResourceType.CHARGING_POINT),
                readings.stream().map(DeviceReading::resourceType).toList());
        assertEquals(List.of("AVAILABLE", "FREE", "AVAILABLE"),
                readings.stream().map(DeviceReading::observedState).toList());
        assertTrue(readings.getFirst().batteryLevel() >= 0 && readings.getFirst().batteryLevel() <= 100);
        assertEquals(1, readings.stream().filter(reading -> reading.batteryLevel() != null).count());
    }

    private static String content(DeviceReading reading) {
        return reading.hubId() + ":" + reading.resourceType() + ":" + reading.resourceId() + ":"
                + reading.observedState() + ":" + reading.batteryLevel() + ":" + reading.failureReason();
    }
}
