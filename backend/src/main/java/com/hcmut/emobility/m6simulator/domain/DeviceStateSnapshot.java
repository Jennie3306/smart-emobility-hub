package com.hcmut.emobility.m6simulator.domain;

import java.time.OffsetDateTime;

public record DeviceStateSnapshot(
        Long hubId,
        ResourceType resourceType,
        Long resourceId,
        String state,
        Integer batteryLevel,
        String failureReason,
        OffsetDateTime observedAt) {
}
