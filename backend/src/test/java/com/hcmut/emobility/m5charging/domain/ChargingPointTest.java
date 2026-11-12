package com.hcmut.emobility.m5charging.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Review PR #11: recovery transition and which outside states M5 accepts. */
class ChargingPointTest {

    @Test
    void repairedChargerComesBackWhenM6ReportsAvailable() {
        ChargingPoint faulty = new ChargingPoint(1L, 1L, ChargingPointState.FAULTY);
        assertTrue(faulty.applyExternalState("AVAILABLE"));
        assertEquals(ChargingPointState.AVAILABLE, faulty.getState());

        ChargingPoint underRepair = new ChargingPoint(2L, 1L, ChargingPointState.UNDER_REPAIR);
        assertTrue(underRepair.applyExternalState("idle"));
        assertEquals(ChargingPointState.AVAILABLE, underRepair.getState());
    }

    @Test
    void releaseStillKeepsABrokenChargerBroken() {
        ChargingPoint faulty = new ChargingPoint(1L, 1L, ChargingPointState.FAULTY);
        faulty.release();
        assertEquals(ChargingPointState.FAULTY, faulty.getState());
    }

    @Test
    void outsideReportsNeverOverwriteM5Assignments() {
        ChargingPoint reserved = new ChargingPoint(3L, 1L, ChargingPointState.RESERVED);
        assertFalse(reserved.applyExternalState("FREE"));        // M5's own FREE event coming back
        assertFalse(reserved.applyExternalState("IN_USE"));
        assertEquals(ChargingPointState.RESERVED, reserved.getState());

        ChargingPoint inUse = new ChargingPoint(4L, 1L, ChargingPointState.IN_USE);
        assertFalse(inUse.applyExternalState("AVAILABLE"));
        assertEquals(ChargingPointState.IN_USE, inUse.getState());
    }

    @Test
    void equipmentFailureFromOutsideIsStillApplied() {
        ChargingPoint point = new ChargingPoint(5L, 1L, ChargingPointState.IN_USE);
        assertFalse(point.applyExternalState("FAULTY"));
        assertEquals(ChargingPointState.FAULTY, point.getState());
        assertFalse(point.applyExternalState(null));
        assertFalse(point.applyExternalState("SOMETHING_NEW"));
        assertEquals(ChargingPointState.FAULTY, point.getState());
    }
}
