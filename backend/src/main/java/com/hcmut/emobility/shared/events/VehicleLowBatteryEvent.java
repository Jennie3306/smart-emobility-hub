package com.hcmut.emobility.shared.events;

/**
 * Published by M3 when a rental vehicle at a hub drops below the rental battery threshold
 * (on return, or from an M6 battery reading).
 * Consumers: M5 puts the vehicle in the charging queue; M1 refreshes the hub's available-vehicle count.
 */
public class VehicleLowBatteryEvent extends DomainEvent {

    private final Long vehicleId;
    private final int batteryLevel;
    /** Hub the vehicle is parked at; null only for events built by older callers. */
    private final Long hubId;

    public VehicleLowBatteryEvent(Long vehicleId, int batteryLevel) {
        this(vehicleId, batteryLevel, null);
    }

    public VehicleLowBatteryEvent(Long vehicleId, int batteryLevel, Long hubId) {
        this.vehicleId = vehicleId;
        this.batteryLevel = batteryLevel;
        this.hubId = hubId;
    }

    public Long getVehicleId() { return vehicleId; }
    public int getBatteryLevel() { return batteryLevel; }
    public Long getHubId() { return hubId; }
}
