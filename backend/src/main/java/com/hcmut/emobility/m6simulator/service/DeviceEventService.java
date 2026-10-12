package com.hcmut.emobility.m6simulator.service;

import com.hcmut.emobility.m6simulator.domain.DeviceReading;
import com.hcmut.emobility.m6simulator.domain.DeviceStateSnapshot;
import com.hcmut.emobility.m6simulator.domain.ProcessingResult;
import com.hcmut.emobility.m6simulator.domain.ResourceType;
import com.hcmut.emobility.shared.events.ChargerFailedEvent;
import com.hcmut.emobility.shared.events.DomainEvent;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
import com.hcmut.emobility.shared.events.VehicleFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeviceEventService {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventService.class);

    private final DemoDeviceCatalog catalog;
    private final InMemoryDeviceStateStore stateStore;
    private final InboundDeviceReadingStore inboundStore;
    private final EventPublisher eventPublisher;

    public DeviceEventService(DemoDeviceCatalog catalog,
                              InMemoryDeviceStateStore stateStore,
                              InboundDeviceReadingStore inboundStore,
                              EventPublisher eventPublisher) {
        this.catalog = catalog;
        this.stateStore = stateStore;
        this.inboundStore = inboundStore;
        this.eventPublisher = eventPublisher;
    }

    public ProcessingResult processReading(DeviceReading reading) {
        return processReading(reading, null);
    }

    /** Synchronous walking-skeleton path; it does not provide transaction/after-commit semantics. */
    public synchronized ProcessingResult processReading(DeviceReading reading, UUID runId) {
        String invalidReason = validate(reading);
        if (invalidReason != null) {
            return reject(reading == null ? null : reading.eventId(), invalidReason, runId);
        }

        Optional<Long> expectedHub = catalog.hubFor(reading.resourceType(), reading.resourceId());
        if (expectedHub.isEmpty() || !expectedHub.get().equals(reading.hubId())) {
            return reject(reading.eventId(), "RESOURCE_NOT_FOUND", runId);
        }

        Optional<ProcessingResult> previous = inboundStore.find(reading.eventId());
        if (previous.isPresent()) {
            log.info("M6_DEVICE_READING_DUPLICATE runId={} inboundEventId={} resourceType={} resourceId={}",
                    runId, reading.eventId(), reading.resourceType(), reading.resourceId());
            return ProcessingResult.duplicate(reading.eventId());
        }

        DeviceStateSnapshot snapshot = new DeviceStateSnapshot(reading.hubId(), reading.resourceType(),
                reading.resourceId(), reading.observedState(), reading.batteryLevel(),
                reading.failureReason(), reading.observedAt());
        stateStore.save(snapshot);

        List<DomainEvent> events = buildEvents(reading);
        List<ProcessingResult.OutboundEvent> outbound = new ArrayList<>(events.size());
        for (DomainEvent event : events) {
            eventPublisher.publish(event);
            outbound.add(new ProcessingResult.OutboundEvent(event.getEventId(),
                    event.getClass().getSimpleName(), reading.resourceType(), reading.resourceId()));
            log.info("M6_DEVICE_EVENT_PUBLISHED runId={} inboundEventId={} outboundEventId={} eventType={} resourceType={} resourceId={}",
                    runId, reading.eventId(), event.getEventId(), event.getClass().getSimpleName(),
                    reading.resourceType(), reading.resourceId());
        }

        ProcessingResult result = ProcessingResult.processed(reading.eventId(), outbound);
        inboundStore.markCompleted(result);
        log.info("M6_DEVICE_READING_PROCESSED runId={} inboundEventId={} result={} resourceType={} resourceId={} outboundCount={}",
                runId, reading.eventId(), result.status(), reading.resourceType(), reading.resourceId(), outbound.size());
        return result;
    }

    private List<DomainEvent> buildEvents(DeviceReading reading) {
        if (reading.failureReason() == null || reading.failureReason().isBlank()) {
            return List.of(new ResourceStateUpdatedEvent(reading.hubId(), reading.resourceType().name(),
                    reading.resourceId(), reading.observedState()));
        }

        if (reading.resourceType() == ResourceType.VEHICLE) {
            return List.of(new VehicleFailedEvent(reading.resourceId(), reading.hubId(), reading.failureReason()));
        }

        if (reading.resourceType() == ResourceType.CHARGING_POINT) {
            return List.of(
                    new ResourceStateUpdatedEvent(reading.hubId(), reading.resourceType().name(),
                            reading.resourceId(), "FAULTY"),
                    new ChargerFailedEvent(reading.resourceId(), reading.hubId(), reading.failureReason()));
        }

        return List.of(new ResourceStateUpdatedEvent(reading.hubId(), reading.resourceType().name(),
                reading.resourceId(), reading.observedState()));
    }

    private String validate(DeviceReading reading) {
        if (reading == null || reading.eventId() == null || reading.hubId() == null || reading.hubId() <= 0
                || reading.resourceType() == null || reading.resourceId() == null || reading.resourceId() <= 0
                || reading.observedState() == null || reading.observedState().isBlank()
                || reading.observedAt() == null) {
            return "INVALID_DEVICE_PAYLOAD";
        }
        if (reading.batteryLevel() != null
                && (reading.batteryLevel() < 0 || reading.batteryLevel() > 100)) {
            return "INVALID_DEVICE_PAYLOAD";
        }
        if (reading.batteryLevel() != null && reading.resourceType() != ResourceType.VEHICLE) {
            return "INVALID_DEVICE_PAYLOAD";
        }
        if (reading.failureReason() != null && reading.failureReason().isBlank()) {
            return "INVALID_DEVICE_PAYLOAD";
        }
        if (reading.failureReason() != null && reading.resourceType() == ResourceType.PARKING_SPACE) {
            return "INVALID_DEVICE_PAYLOAD";
        }
        return null;
    }

    private ProcessingResult reject(UUID eventId, String reason, UUID runId) {
        log.warn("M6_DEVICE_READING_REJECTED runId={} inboundEventId={} reasonCode={}", runId, eventId, reason);
        return ProcessingResult.rejected(eventId, reason);
    }
}
