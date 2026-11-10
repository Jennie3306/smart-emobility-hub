package com.hcmut.emobility.m6simulator.domain;

import java.util.Set;

/** Source-specific M6-D04 reasons, per TV2's decision dated 2026-10-28. */
public final class FailureReasonCodes {

    private static final Set<String> VEHICLE_DEVICE = Set.of(
            "MECHANICAL", "SENSOR_OFFLINE");
    private static final Set<String> CHARGING_POINT_DEVICE = Set.of(
            "OFFLINE", "OVERHEAT", "POWER_FAULT");

    private FailureReasonCodes() {
    }

    public static boolean allowsDeviceReading(ResourceType resourceType, String reasonCode) {
        if (resourceType == null || reasonCode == null) {
            return false;
        }
        return switch (resourceType) {
            case VEHICLE -> VEHICLE_DEVICE.contains(reasonCode);
            case CHARGING_POINT -> CHARGING_POINT_DEVICE.contains(reasonCode);
            case PARKING_SPACE -> false;
        };
    }

    /** Reason validation only; does not create an Incident or grant Operator access. */
    public static boolean allowsOperatorReport(ResourceType resourceType, String reasonCode) {
        return allowsDeviceReading(resourceType, reasonCode)
                || ("MANUAL_REPORT".equals(reasonCode)
                && (resourceType == ResourceType.VEHICLE || resourceType == ResourceType.CHARGING_POINT));
    }
}
