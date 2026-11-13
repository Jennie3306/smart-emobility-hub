package com.hcmut.emobility.m6simulator.dto;

import com.hcmut.emobility.m6simulator.domain.*;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

public record IncidentView(Long id, ResourceType resourceType, Long resourceId, Long hubId, String reason,
                           IncidentSeverity severity, String description, IncidentStatus status,
                           IncidentSource source, String reportedBy, String assignee, String note,
                           OffsetDateTime reportedAt, OffsetDateTime assignedAt, OffsetDateTime startedAt,
                           OffsetDateTime resolvedAt, OffsetDateTime closedAt, Long version,
                           List<IncidentAction> allowedActions) {

    public static IncidentView of(Incident i) {
        return new IncidentView(i.getId(), i.getResourceType(), i.getResourceId(), i.getHubId(), i.getReason(),
                i.getSeverity(), i.getDescription(), i.getStatus(), i.getSource(), i.getReportedBy(),
                i.getAssignee(), i.getNote(), i.getReportedAt(), i.getAssignedAt(), i.getStartedAt(),
                i.getResolvedAt(), i.getClosedAt(), i.getVersion(), allowed(i.getStatus()));
    }

    /** Actions the BR-M6-04 graph accepts from this status, so the screen shows only valid buttons. */
    static List<IncidentAction> allowed(IncidentStatus status) {
        return Arrays.stream(IncidentAction.values()).filter(a -> {
            try { status.transition(a); return true; } catch (RuntimeException e) { return false; }
        }).toList();
    }
}
