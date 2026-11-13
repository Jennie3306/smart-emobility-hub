package com.hcmut.emobility.shared.events;

/**
 * A charging session started drawing power at a charging point. Published by M5.
 * Consumers: M3 moves a rental vehicle LOW_BATTERY -> CHARGING (vehicleKind = "RENTAL");
 * M1 refreshes the hub on the dashboard.
 * The port itself (RESERVED -> IN_USE) still travels as ResourceStateUpdatedEvent.
 */
public class ChargingStartedEvent extends DomainEvent {
    private final Long sessionId;
    private final Long hubId;
    private final Long chargingPointId;
    /** "PERSONAL" or "RENTAL", same convention as ChargingCompletedEvent. */
    private final String vehicleKind;
    private final Long vehicleId;

    public ChargingStartedEvent(Long sessionId, Long hubId, Long chargingPointId,
                                String vehicleKind, Long vehicleId) {
        this.sessionId = sessionId;
        this.hubId = hubId;
        this.chargingPointId = chargingPointId;
        this.vehicleKind = vehicleKind;
        this.vehicleId = vehicleId;
    }

    public Long getSessionId() { return sessionId; }
    public Long getHubId() { return hubId; }
    public Long getChargingPointId() { return chargingPointId; }
    public String getVehicleKind() { return vehicleKind; }
    public Long getVehicleId() { return vehicleId; }
}
