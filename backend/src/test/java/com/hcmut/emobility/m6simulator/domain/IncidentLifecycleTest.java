package com.hcmut.emobility.m6simulator.domain;

import com.hcmut.emobility.shared.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentLifecycleTest {

    @Test
    void startsReported() {
        assertEquals(IncidentStatus.REPORTED, IncidentLifecycle.reported().status());
    }

    @Test
    void completesTheLifecycleWithoutChangingEarlierValues() {
        IncidentLifecycle reported = IncidentLifecycle.reported();
        IncidentLifecycle assigned = reported.transition(IncidentAction.ASSIGN);
        IncidentLifecycle inProgress = assigned.transition(IncidentAction.START);
        IncidentLifecycle resolved = inProgress.transition(IncidentAction.RESOLVE);
        IncidentLifecycle closed = resolved.transition(IncidentAction.CLOSE);

        assertEquals(IncidentStatus.REPORTED, reported.status());
        assertEquals(IncidentStatus.ASSIGNED, assigned.status());
        assertEquals(IncidentStatus.IN_PROGRESS, inProgress.status());
        assertEquals(IncidentStatus.RESOLVED, resolved.status());
        assertEquals(IncidentStatus.CLOSED, closed.status());
        assertNotSame(reported, assigned);
    }

    @Test
    void reopensResolvedAndCanResolveAndCloseAgain() {
        IncidentLifecycle resolved = IncidentLifecycle.reported()
                .transition(IncidentAction.ASSIGN)
                .transition(IncidentAction.START)
                .transition(IncidentAction.RESOLVE);

        IncidentLifecycle reopened = resolved.transition(IncidentAction.REOPEN);

        assertEquals(IncidentStatus.IN_PROGRESS, reopened.status());
        assertEquals(IncidentStatus.RESOLVED, resolved.status());
        assertEquals(IncidentStatus.CLOSED, reopened.transition(IncidentAction.RESOLVE)
                .transition(IncidentAction.CLOSE).status());
    }

    @Test
    void rejectingAnInvalidActionLeavesTheValueUsable() {
        IncidentLifecycle reported = IncidentLifecycle.reported();

        assertThrows(BusinessException.class, () -> reported.transition(IncidentAction.RESOLVE));

        assertEquals(IncidentStatus.REPORTED, reported.status());
        assertEquals(IncidentStatus.ASSIGNED, reported.transition(IncidentAction.ASSIGN).status());
    }

    @Test
    void nullActionCannotChangeTheValue() {
        IncidentLifecycle reported = IncidentLifecycle.reported();

        assertThrows(NullPointerException.class, () -> reported.transition(null));

        assertEquals(IncidentStatus.REPORTED, reported.status());
    }
}
