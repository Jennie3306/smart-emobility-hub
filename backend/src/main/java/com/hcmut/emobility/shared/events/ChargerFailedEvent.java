package com.hcmut.emobility.shared.events;

/**
 * Phat boi: M6
 * Ben nhan: M5 dung phien sac va phan lai cong; M4 xu ly cho do lien quan
 */
public class ChargerFailedEvent extends DomainEvent {

    private final Long chargingPointId;
    private final Long hubId;
    private final String reason;

    public ChargerFailedEvent(Long chargingPointId, Long hubId, String reason) {
        this.chargingPointId = chargingPointId;
        this.hubId = hubId;
        this.reason = reason;
    }

    public Long getChargingPointId() { return chargingPointId; }
    public Long getHubId() { return hubId; }
    public String getReason() { return reason; }
}
