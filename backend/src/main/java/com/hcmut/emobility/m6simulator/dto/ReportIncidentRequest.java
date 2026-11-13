package com.hcmut.emobility.m6simulator.dto;

import com.hcmut.emobility.m6simulator.domain.IncidentSeverity;
import com.hcmut.emobility.m6simulator.domain.ResourceType;

/** FR-M6-01. Severity is required; there is no default (BR-M6-10). */
public record ReportIncidentRequest(Long hubId, ResourceType resourceType, Long resourceId,
                                    String reason, IncidentSeverity severity, String description) {
}
