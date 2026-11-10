package com.hcmut.emobility.m6simulator.service;

import com.hcmut.emobility.m6simulator.domain.ProcessingResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * M6 producer-side, process-local dedupe aid. It is intentionally separate from
 * shared.events.ProcessedEventStore, which belongs to event consumers. The
 * bounded FIFO is only a memory guard for this demo; it is not a durable
 * retention or idempotency guarantee.
 */
@Component
public class InboundDeviceReadingStore {

    static final int MAX_ENTRIES = 10_000;
    private final Map<UUID, ProcessingResult> completed = new LinkedHashMap<>();

    public synchronized Optional<ProcessingResult> find(UUID eventId) {
        return Optional.ofNullable(completed.get(eventId));
    }

    public synchronized void markCompleted(ProcessingResult result) {
        completed.put(result.inboundEventId(), result);
        while (completed.size() > MAX_ENTRIES) {
            UUID oldest = completed.keySet().iterator().next();
            completed.remove(oldest);
        }
    }

    public synchronized int size() {
        return completed.size();
    }
}
