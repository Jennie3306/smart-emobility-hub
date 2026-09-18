package com.hcmut.emobility.shared.events;

/**
 * Phat boi: M3
 * Ben nhan: M5 dua xe vao hang doi sac
 */
public class VehicleLowBatteryEvent extends DomainEvent {

    private final Long vehicleId;
    private final int batteryLevel;

    public VehicleLowBatteryEvent(Long vehicleId, int batteryLevel) {
        this.vehicleId = vehicleId;
        this.batteryLevel = batteryLevel;
    }

    public Long getVehicleId() { return vehicleId; }
    public int getBatteryLevel() { return batteryLevel; }
}
