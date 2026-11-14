package com.hcmut.emobility.m6simulator.domain;

import com.hcmut.emobility.shared.common.BusinessException;

import java.util.Objects;

/**
 * M6-D07 / BR-M6-04 transition graph only. Java names do not define a storage
 * or HTTP representation. Admission, assignee and device-health rules are separate.
 */
public enum IncidentStatus {
    REPORTED,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    public IncidentStatus transition(IncidentAction action) {
        Objects.requireNonNull(action, "action");
        IncidentStatus target = switch (this) {
            case REPORTED -> action == IncidentAction.ASSIGN ? ASSIGNED : null;
            case ASSIGNED -> action == IncidentAction.START ? IN_PROGRESS : null;
            case IN_PROGRESS -> action == IncidentAction.RESOLVE ? RESOLVED : null;
            case RESOLVED -> switch (action) {
                case REOPEN -> IN_PROGRESS;
                case CLOSE -> CLOSED;
                default -> null;
            };
            case CLOSED -> null;
        };
        if (target == null) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Action " + action + " is not allowed while the incident is " + this);
        }
        return target;
    }
}
