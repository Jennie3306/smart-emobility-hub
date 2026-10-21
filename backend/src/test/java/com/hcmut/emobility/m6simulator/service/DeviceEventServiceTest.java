package com.hcmut.emobility.m6simulator.service;

import com.hcmut.emobility.m6simulator.domain.DeviceReading;
import com.hcmut.emobility.m6simulator.domain.DeviceStateSnapshot;
import com.hcmut.emobility.m6simulator.domain.ProcessingResult;
import com.hcmut.emobility.m6simulator.domain.ResourceType;
import com.hcmut.emobility.m6simulator.domain.SimulatorScenario;
import com.hcmut.emobility.shared.events.ChargerFailedEvent;
import com.hcmut.emobility.shared.events.DomainEvent;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
import com.hcmut.emobility.shared.events.VehicleFailedEvent;
import com.hcmut.emobility.shared.events.VehicleLowBatteryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DeviceEventServiceTest {

    private EventPublisher publisher;
    private InMemoryDeviceStateStore stateStore;
    private DeviceEventService service;

    @BeforeEach
    void setUp() {
        publisher = mock(EventPublisher.class);
        stateStore = new InMemoryDeviceStateStore();
        service = new DeviceEventService(new DemoDeviceCatalog(), stateStore,
                new InboundDeviceReadingStore(), publisher);
    }

    @Test
    void normalReadingPublishesResourceStateUpdatedAndStoresLatestState() {
        DeviceReading reading = reading(ResourceType.PARKING_SPACE, 1L, "OCCUPIED", null, null, 1);

        ProcessingResult result = service.processReading(reading);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publish(captor.capture());
        assertTrue(captor.getValue() instanceof ResourceStateUpdatedEvent);
        ResourceStateUpdatedEvent event = (ResourceStateUpdatedEvent) captor.getValue();
        assertEquals(1L, event.getHubId());
        assertEquals("PARKING_SPACE", event.getResourceType());
        assertEquals(1L, event.getResourceId());
        assertEquals("OCCUPIED", event.getNewState());
        assertEquals(ProcessingResult.Status.PROCESSED, result.status());
        assertEquals(1, stateStore.snapshot().size());
    }

    @Test
    void normalTickPipelinePublishesThreeContractStatesIncludingFreeParkingSpace() {
        List<DeviceReading> readings = new SyntheticReadingFactory()
                .createReadings(SimulatorScenario.NORMAL_TICK, 42);

        readings.forEach(service::processReading);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(3)).publish(captor.capture());
        List<ResourceStateUpdatedEvent> stateEvents = captor.getAllValues().stream()
                .map(ResourceStateUpdatedEvent.class::cast)
                .toList();
        assertEquals(List.of("AVAILABLE", "FREE", "AVAILABLE"),
                stateEvents.stream().map(ResourceStateUpdatedEvent::getNewState).toList());
        assertEquals(List.of("VEHICLE", "PARKING_SPACE", "CHARGING_POINT"),
                stateEvents.stream().map(ResourceStateUpdatedEvent::getResourceType).toList());
    }

    @Test
    void vehicleFailurePublishesVehicleFailedWithoutChangingConsumerState() {
        DeviceReading reading = reading(ResourceType.VEHICLE, 1L, "FAULTY", 0, "MECHANICAL", 2);

        ProcessingResult result = service.processReading(reading);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publish(captor.capture());
        assertTrue(captor.getValue() instanceof VehicleFailedEvent);
        VehicleFailedEvent event = (VehicleFailedEvent) captor.getValue();
        assertEquals(1L, event.getVehicleId());
        assertEquals(1L, event.getHubId());
        assertEquals("MECHANICAL", event.getReason());
        assertEquals(1, result.outboundEvents().size());
        assertNotEquals(reading.eventId(), result.outboundEvents().getFirst().eventId());
    }

    @Test
    void lowBatteryReadingUpdatesM6StateWithoutPublishingVehicleLowBattery() {
        DeviceReading reading = reading(ResourceType.VEHICLE, 1L, "AVAILABLE", 10, null, 7);

        ProcessingResult result = service.processReading(reading);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publish(captor.capture());
        assertTrue(captor.getValue() instanceof ResourceStateUpdatedEvent);
        assertFalse(captor.getValue() instanceof VehicleFailedEvent);
        assertFalse(captor.getValue() instanceof VehicleLowBatteryEvent);
        assertEquals(10, stateStore.snapshot().values().iterator().next().batteryLevel());
        assertEquals(ProcessingResult.Status.PROCESSED, result.status());
    }

    @Test
    void latestStateUpsertReplacesSnapshotWithoutKeepingHistory() {
        service.processReading(reading(ResourceType.VEHICLE, 1L, "AVAILABLE", 75, null, 8));
        service.processReading(reading(ResourceType.VEHICLE, 1L, "IN_USE", 60, null, 9));

        assertEquals(1, stateStore.snapshot().size());
        DeviceStateSnapshot latest = stateStore.snapshot().values().iterator().next();
        assertEquals("IN_USE", latest.state());
        assertEquals(60, latest.batteryLevel());
    }

    @Test
    void chargerFailurePublishesFaultyStateAndChargerFailed() {
        DeviceReading reading = reading(ResourceType.CHARGING_POINT, 1L, "FAULTY", null, "OFFLINE", 3);

        ProcessingResult result = service.processReading(reading);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(2)).publish(captor.capture());
        List<DomainEvent> events = captor.getAllValues();
        assertTrue(events.getFirst() instanceof ResourceStateUpdatedEvent);
        assertEquals("FAULTY", ((ResourceStateUpdatedEvent) events.getFirst()).getNewState());
        assertTrue(events.get(1) instanceof ChargerFailedEvent);
        assertEquals("OFFLINE", ((ChargerFailedEvent) events.get(1)).getReason());
        assertEquals(2, result.outboundEvents().size());
    }

    @Test
    void acceptsOnlyTheSelectedUppercaseReasonCodesForEachResourceType() {
        List<String> vehicleReasons = List.of("MECHANICAL", "SENSOR_OFFLINE", "MANUAL_REPORT");
        List<String> chargerReasons = List.of("OFFLINE", "OVERHEAT", "POWER_FAULT", "MANUAL_REPORT");
        long id = 10;

        for (String reason : vehicleReasons) {
            assertEquals(ProcessingResult.Status.PROCESSED,
                    service.processReading(reading(ResourceType.VEHICLE, 1L, "FAULTY", 0, reason, id++))
                            .status(), reason);
        }
        for (String reason : chargerReasons) {
            assertEquals(ProcessingResult.Status.PROCESSED,
                    service.processReading(reading(ResourceType.CHARGING_POINT, 1L, "FAULTY", null, reason, id++))
                            .status(), reason);
        }
    }

    @Test
    void rejectsLowercaseAndWrongResourceReasonCodes() {
        List<DeviceReading> invalidReadings = List.of(
                reading(ResourceType.VEHICLE, 1L, "FAULTY", 0, "mechanical", 20),
                reading(ResourceType.VEHICLE, 1L, "FAULTY", 0, "OFFLINE", 21),
                reading(ResourceType.CHARGING_POINT, 1L, "FAULTY", null, "MECHANICAL", 22),
                reading(ResourceType.PARKING_SPACE, 1L, "FAULTY", null, "MANUAL_REPORT", 23));

        for (DeviceReading invalid : invalidReadings) {
            ProcessingResult result = service.processReading(invalid);
            assertEquals(ProcessingResult.Status.REJECTED, result.status());
            assertEquals("INVALID_DEVICE_PAYLOAD", result.reasonCode());
        }
        assertTrue(stateStore.snapshot().isEmpty());
        verifyNoInteractions(publisher);
    }

    @Test
    void duplicateInboundIdIsNoOpAndDoesNotPublishAgain() {
        DeviceReading reading = reading(ResourceType.CHARGING_POINT, 1L, "AVAILABLE", null, null, 4);

        ProcessingResult first = service.processReading(reading);
        ProcessingResult second = service.processReading(reading);

        assertEquals(ProcessingResult.Status.PROCESSED, first.status());
        assertEquals(ProcessingResult.Status.DUPLICATE, second.status());
        assertTrue(second.outboundEvents().isEmpty());
        verify(publisher, times(1)).publish(org.mockito.ArgumentMatchers.any(DomainEvent.class));
    }

    @Test
    void malformedBatteryAndUnknownResourceAreRejectedWithoutPublishing() {
        DeviceReading badBattery = reading(ResourceType.VEHICLE, 1L, "AVAILABLE", 101, null, 5);
        DeviceReading unknown = reading(ResourceType.PARKING_SPACE, 9L, "AVAILABLE", null, null, 6);

        assertEquals("INVALID_DEVICE_PAYLOAD", service.processReading(badBattery).reasonCode());
        assertEquals("RESOURCE_NOT_FOUND", service.processReading(unknown).reasonCode());
        assertFalse(stateStore.snapshot().size() > 0);
        verifyNoInteractions(publisher);
    }

    private static DeviceReading reading(ResourceType type, Long id, String state, Integer battery,
                                         String reason, long eventIdSuffix) {
        return new DeviceReading(new UUID(0, eventIdSuffix), 1L, type, id, state, battery, reason,
                OffsetDateTime.parse("2026-10-02T10:00:00+07:00"));
    }
}
