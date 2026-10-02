package com.hcmut.emobility.m6simulator.dto;

import com.hcmut.emobility.m6simulator.domain.SimulatorScenario;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SimulationRunView(
        UUID runId,
        SimulatorScenario scenario,
        long seed,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        int readingCount,
        int processedCount,
        int duplicateCount,
        int rejectedCount,
        List<OutboundEventView> outboundEvents,
        String error) {

    public SimulationRunView {
        outboundEvents = List.copyOf(outboundEvents);
    }

    public record OutboundEventView(UUID eventId, String eventType, String resourceType, Long resourceId) {
    }
}
