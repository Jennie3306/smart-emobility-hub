package com.hcmut.emobility.m6simulator.domain;

import java.util.Set;

/** M6-D04 failure reason strings selected as the M6 implementation baseline. */
public final class FailureReasonCodes {

    private static final Set<String> VEHICLE = Set.of(
            "MECHANICAL", "SENSOR_OFFLINE", "MANUAL_REPORT");
    private static final Set<String> CHARGING_POINT = Set.of(
            "OFFLINE", "OVERHEAT", "POWER_FAULT", "MANUAL_REPORT");

    private FailureReasonCodes() {
    }

    public static boolean allows(ResourceType resourceType, String reasonCode) {
        if (resourceType == null || reasonCode == null) {
            return false;
        }
        return switch (resourceType) {
            case VEHICLE -> VEHICLE.contains(reasonCode);
            case CHARGING_POINT -> CHARGING_POINT.contains(reasonCode);
            case PARKING_SPACE -> false;
        };
    }
}
