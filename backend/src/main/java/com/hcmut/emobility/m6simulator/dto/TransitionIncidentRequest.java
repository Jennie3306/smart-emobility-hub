package com.hcmut.emobility.m6simulator.dto;

import com.hcmut.emobility.m6simulator.domain.IncidentAction;

/** FR-M6-02. expectedVersion is the version the Operator read; assigneeUserId is required for ASSIGN. */
public record TransitionIncidentRequest(IncidentAction action, Long expectedVersion,
                                        String assigneeUserId, String note) {
}
