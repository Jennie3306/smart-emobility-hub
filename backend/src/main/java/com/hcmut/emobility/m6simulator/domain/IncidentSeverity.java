package com.hcmut.emobility.m6simulator.domain;

/**
 * BR-M6-10: four severity levels. An Operator report must name one (no default); an Incident recorded
 * automatically from a device failure takes its level from the device type and the reason code.
 * Severity is for display, filtering and prioritising only: it never assigns a person or changes the lifecycle.
 */
public enum IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** Mapping table of BR-M6-10 for automatic Incidents; null when the pair is not a device failure. */
    public static IncidentSeverity forDeviceFailure(ResourceType type, String reasonCode) {
        if (type == null || reasonCode == null) return null;
        return switch (type) {
            case VEHICLE -> switch (reasonCode) {
                case "MECHANICAL" -> HIGH;
                case "SENSOR_OFFLINE" -> MEDIUM;
                default -> null;
            };
            case CHARGING_POINT -> switch (reasonCode) {
                case "OFFLINE" -> MEDIUM;
                case "POWER_FAULT" -> HIGH;
                case "OVERHEAT" -> CRITICAL;
                default -> null;
            };
            case PARKING_SPACE -> null;
        };
    }
}
