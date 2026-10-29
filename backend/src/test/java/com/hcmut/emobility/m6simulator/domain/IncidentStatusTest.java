package com.hcmut.emobility.m6simulator.domain;

import com.hcmut.emobility.shared.common.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentStatusTest {

    /** Independent decision table from D07: five allowed and twenty forbidden pairs. */
    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @CsvSource({
            "REPORTED,    ASSIGN,  ASSIGNED",
            "REPORTED,    START,   INVALID",
            "REPORTED,    RESOLVE, INVALID",
            "REPORTED,    REOPEN,  INVALID",
            "REPORTED,    CLOSE,   INVALID",
            "ASSIGNED,    ASSIGN,  INVALID",
            "ASSIGNED,    START,   IN_PROGRESS",
            "ASSIGNED,    RESOLVE, INVALID",
            "ASSIGNED,    REOPEN,  INVALID",
            "ASSIGNED,    CLOSE,   INVALID",
            "IN_PROGRESS, ASSIGN,  INVALID",
            "IN_PROGRESS, START,   INVALID",
            "IN_PROGRESS, RESOLVE, RESOLVED",
            "IN_PROGRESS, REOPEN,  INVALID",
            "IN_PROGRESS, CLOSE,   INVALID",
            "RESOLVED,    ASSIGN,  INVALID",
            "RESOLVED,    START,   INVALID",
            "RESOLVED,    RESOLVE, INVALID",
            "RESOLVED,    REOPEN,  IN_PROGRESS",
            "RESOLVED,    CLOSE,   CLOSED",
            "CLOSED,      ASSIGN,  INVALID",
            "CLOSED,      START,   INVALID",
            "CLOSED,      RESOLVE, INVALID",
            "CLOSED,      REOPEN,  INVALID",
            "CLOSED,      CLOSE,   INVALID"
    })
    void followsAcceptedD07DecisionTable(IncidentStatus source, IncidentAction action, String expected) {
        if ("INVALID".equals(expected)) {
            BusinessException error = assertThrows(BusinessException.class, () -> source.transition(action));
            assertEquals("INVALID_STATE_TRANSITION", error.getCode());
            assertEquals(HttpStatus.CONFLICT, error.getStatus());
        } else {
            assertEquals(IncidentStatus.valueOf(expected), source.transition(action));
        }
    }

    @ParameterizedTest
    @EnumSource(IncidentStatus.class)
    void nullActionCannotProduceATransition(IncidentStatus source) {
        assertThrows(NullPointerException.class, () -> source.transition(null));
    }
}
