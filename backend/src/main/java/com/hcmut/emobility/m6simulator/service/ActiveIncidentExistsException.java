package com.hcmut.emobility.m6simulator.service;

import com.hcmut.emobility.m6simulator.domain.Incident;
import com.hcmut.emobility.m6simulator.domain.IncidentStatus;
import com.hcmut.emobility.shared.common.BusinessException;

/** BR-M6-06: the resource already has an active Incident. HTTP 409 with the existing Incident in details. */
public class ActiveIncidentExistsException extends BusinessException {

    private final Long existingIncidentId;
    private final IncidentStatus existingStatus;

    public ActiveIncidentExistsException(Incident existing) {
        super("ACTIVE_INCIDENT_EXISTS", "Resource " + existing.getResourceType() + " " + existing.getResourceId()
                + " already has active Incident #" + existing.getId() + " (" + existing.getStatus() + ")");
        this.existingIncidentId = existing.getId();
        this.existingStatus = existing.getStatus();
    }

    public Long getExistingIncidentId() { return existingIncidentId; }
    public IncidentStatus getExistingStatus() { return existingStatus; }
}
