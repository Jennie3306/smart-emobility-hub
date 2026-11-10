package com.hcmut.emobility.shared.events;

/**
 * Phat boi: M5 khi mot ChargingSession sang COMPLETED
 * Ben nhan: M3 cap nhat muc pin xe thue (vehicleKind = "RENTAL"); M1 cap nhat dashboard hub.
 *
 * Trang thai cong sac (IN_USE -> FREE) van di qua ResourceStateUpdatedEvent nhu cac module khac.
 */
public class ChargingCompletedEvent extends DomainEvent {

    private final Long sessionId;
    private final Long hubId;
    private final Long chargingPointId;
    /** "PERSONAL" hoac "RENTAL", cung quy uoc voi ChargingRequestedEvent. */
    private final String vehicleKind;
    private final Long vehicleId;
    private final int finalBatteryLevel;

    public ChargingCompletedEvent(Long sessionId, Long hubId, Long chargingPointId,
                                  String vehicleKind, Long vehicleId, int finalBatteryLevel) {
        this.sessionId = sessionId;
        this.hubId = hubId;
        this.chargingPointId = chargingPointId;
        this.vehicleKind = vehicleKind;
        this.vehicleId = vehicleId;
        this.finalBatteryLevel = finalBatteryLevel;
    }

    public Long getSessionId() { return sessionId; }
    public Long getHubId() { return hubId; }
    public Long getChargingPointId() { return chargingPointId; }
    public String getVehicleKind() { return vehicleKind; }
    public Long getVehicleId() { return vehicleId; }
    public int getFinalBatteryLevel() { return finalBatteryLevel; }
}
