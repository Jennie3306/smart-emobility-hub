package com.hcmut.emobility.shared.events;
/** M3 pickup fact: M2 reconciles direct pickup; M1 refreshes the origin hub. */
public class VehiclePickedUpEvent extends DomainEvent {
    private final Long vehicleId,userId,hubId;
    public VehiclePickedUpEvent(Long vehicleId,Long userId,Long hubId) {this.vehicleId=vehicleId;this.userId=userId;this.hubId=hubId;}
    public Long getVehicleId(){return vehicleId;}
    public Long getUserId(){return userId;}
    public Long getHubId(){return hubId;}
}
