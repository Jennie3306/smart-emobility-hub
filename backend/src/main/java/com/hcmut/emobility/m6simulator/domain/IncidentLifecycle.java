package com.hcmut.emobility.m6simulator.domain;

/**
 * Immutable, unpersisted D07 lifecycle value. Creating this value is not creating
 * an Incident record. A future application service must validate permissions,
 * assignee, version and active-Incident policy before accepting a transition.
 * This value never changes timestamps, severity, resource health or emits events.
 */
public final class IncidentLifecycle {

    private final IncidentStatus status;

    private IncidentLifecycle(IncidentStatus status) {
        this.status = status;
    }

    public static IncidentLifecycle reported() {
        return new IncidentLifecycle(IncidentStatus.REPORTED);
    }

    public IncidentStatus status() {
        return status;
    }

    /** Returns a new value for an allowed transition; rejection leaves this value intact. */
    public IncidentLifecycle transition(IncidentAction action) {
        return new IncidentLifecycle(status.transition(action));
    }
}
