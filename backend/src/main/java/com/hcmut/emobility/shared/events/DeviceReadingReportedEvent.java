package com.hcmut.emobility.shared.events;

/**
 * A device (sensor / IoT simulator) reported a reading. Published by M6 only.
 *
 * A reading is an observation, not a business state. The module that owns the resource decides what the
 * reading means and, if its state changes, publishes {@link ResourceStateUpdatedEvent} itself:
 *   - VEHICLE        -> M3 (battery level; below the threshold an AVAILABLE vehicle becomes LOW_BATTERY)
 *   - CHARGING_POINT -> M5 (port back online after a failure)
 *   - PARKING_SPACE  -> M4 (occupancy sensor; informational only)
 * Device failures are not readings: M6 publishes VehicleFailedEvent / ChargerFailedEvent for those.
 *
 * Appendix A (event contract v2), decided 12/11/2026: every resource has exactly one owner, so the
 * dashboard (M1), the booking modules (M4, M5) and the What-if snapshot (M8) always see the same numbers.
 */
public class DeviceReadingReportedEvent extends DomainEvent {

    private final Long hubId;
    private final String resourceType;
    private final Long resourceId;
    private final String observedState;
    /** 0..100 for VEHICLE readings, null for other resource types. */
    private final Integer batteryLevel;

    public DeviceReadingReportedEvent(Long hubId, String resourceType, Long resourceId,
                                      String observedState, Integer batteryLevel) {
        this.hubId = hubId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.observedState = observedState;
        this.batteryLevel = batteryLevel;
    }

    public Long getHubId() { return hubId; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public String getObservedState() { return observedState; }
    public Integer getBatteryLevel() { return batteryLevel; }
}
