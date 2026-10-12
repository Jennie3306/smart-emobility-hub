package com.hcmut.emobility.m6simulator.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Inbound synthetic reading. It is runtime data, not a persisted entity. */
public record DeviceReading(
        UUID eventId,
        Long hubId,
        ResourceType resourceType,
        Long resourceId,
        String observedState,
        Integer batteryLevel,
        String failureReason,
        OffsetDateTime observedAt) {
}
