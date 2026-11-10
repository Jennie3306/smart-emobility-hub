package com.hcmut.emobility.shared.events;

/**
 * Phat boi: M2 (dat cho / gan xe thanh cong)
 * Ben nhan: M3 chuyen xe AVAILABLE -> RESERVED + reservedByUserId
 */
public class ReservationConfirmedEvent extends DomainEvent {

    private final Long reservationId;
    private final Long userId;
    private final String resourceType;
    private final Long resourceId;
    private final Long hubId;

    public ReservationConfirmedEvent(Long reservationId,
                                     Long userId,
                                     String resourceType,
                                     Long resourceId,
                                     Long hubId) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.hubId = hubId;
    }

    public Long getReservationId() { return reservationId; }
    public Long getUserId() { return userId; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public Long getHubId() { return hubId; }
}