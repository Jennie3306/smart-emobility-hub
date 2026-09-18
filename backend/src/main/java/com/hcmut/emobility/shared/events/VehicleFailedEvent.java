package com.hcmut.emobility.shared.events;

/**
 * Phat boi: M6
 * Ben nhan: M3 chuyen xe sang Maintenance; M2 xu ly dat cho lien quan
 */
public class VehicleFailedEvent extends DomainEvent {

    private final Long vehicleId;
    private final Long hubId;
    private final String reason;

    public VehicleFailedEvent(Long vehicleId, Long hubId, String reason) {
        this.vehicleId = vehicleId;
        this.hubId = hubId;
        this.reason = reason;
    }

    public Long getVehicleId() { return vehicleId; }
    public Long getHubId() { return hubId; }
    public String getReason() { return reason; }
}
