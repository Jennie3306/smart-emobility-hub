package com.hcmut.emobility.m6simulator.domain;

import java.util.List;
import java.util.UUID;

public record ProcessingResult(
        Status status,
        UUID inboundEventId,
        List<OutboundEvent> outboundEvents,
        String reasonCode) {

    public ProcessingResult {
        outboundEvents = List.copyOf(outboundEvents);
    }

    public static ProcessingResult processed(UUID inboundEventId, List<OutboundEvent> events) {
        return new ProcessingResult(Status.PROCESSED, inboundEventId, events, null);
    }

    /** Accepted and recorded, but nothing to announce (e.g. ACTIVE_INCIDENT_EXISTS, BR-M6-06). */
    public static ProcessingResult processedWithoutEvents(UUID inboundEventId, String reasonCode) {
        return new ProcessingResult(Status.PROCESSED, inboundEventId, List.of(), reasonCode);
    }

    public static ProcessingResult duplicate(UUID inboundEventId) {
        return new ProcessingResult(Status.DUPLICATE, inboundEventId, List.of(), null);
    }

    public static ProcessingResult rejected(UUID inboundEventId, String reasonCode) {
        return new ProcessingResult(Status.REJECTED, inboundEventId, List.of(), reasonCode);
    }

    public enum Status {
        PROCESSED,
        DUPLICATE,
        REJECTED
    }

    public record OutboundEvent(UUID eventId, String eventType, ResourceType resourceType,
                                Long resourceId) {
    }
}
