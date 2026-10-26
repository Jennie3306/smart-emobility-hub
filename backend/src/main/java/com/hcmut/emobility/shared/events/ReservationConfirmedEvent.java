package com.hcmut.emobility.shared.events;

/** Integration contract: M2 confirms a hold; M3 reserves its own vehicle atomically. */
public class ReservationConfirmedEvent extends DomainEvent {
    private final Long reservationId,userId,resourceId,hubId;
    private final String resourceType;
    public ReservationConfirmedEvent(Long reservationId,Long userId,String resourceType,Long resourceId,Long hubId) {
        this.reservationId=reservationId;this.userId=userId;this.resourceType=resourceType;this.resourceId=resourceId;this.hubId=hubId;
    }
    public Long getReservationId(){return reservationId;}
    public Long getUserId(){return userId;}
    public String getResourceType(){return resourceType;}
    public Long getResourceId(){return resourceId;}
    public Long getHubId(){return hubId;}
}
