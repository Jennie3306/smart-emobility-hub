package com.hcmut.emobility.shared.events;

/**
 * Phat boi: M2
 * Ben nhan: M3 nha xe ve Available; M4 giai phong cho do
 */
public class ReservationCancelledEvent extends DomainEvent {

    private final Long reservationId;
    private final String resourceType;
    private final Long resourceId;

    public ReservationCancelledEvent(Long reservationId, String resourceType, Long resourceId) {
        this.reservationId = reservationId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public Long getReservationId() { return reservationId; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
}
