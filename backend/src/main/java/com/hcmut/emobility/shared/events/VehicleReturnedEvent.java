package com.hcmut.emobility.shared.events;

/**
 * Phat boi: M3
 * Ben nhan: M5 xet dua xe vao hang doi sac; M2 dong dat cho
 */
public class VehicleReturnedEvent extends DomainEvent {

    private final Long vehicleId;
    private final Long hubId;
    private final int batteryLevel;

    public VehicleReturnedEvent(Long vehicleId, Long hubId, int batteryLevel) {
        this.vehicleId = vehicleId;
        this.hubId = hubId;
        this.batteryLevel = batteryLevel;
    }

    public Long getVehicleId() { return vehicleId; }
    public Long getHubId() { return hubId; }
    public int getBatteryLevel() { return batteryLevel; }
}
