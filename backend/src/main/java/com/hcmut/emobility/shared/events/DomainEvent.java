package com.hcmut.emobility.shared.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lop cha cua moi su kien giua cac module (Phu luc A - Hop dong su kien).
 * Quy uoc: ten lop ket thuc bang "Event", dong tu o thi qua khu.
 * eventId dung de ben nhan chong xu ly trung (idempotent).
 */
public abstract class DomainEvent {
    private final UUID eventId = UUID.randomUUID();
    private final OffsetDateTime occurredAt = OffsetDateTime.now();

    public UUID getEventId() { return eventId; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
}
