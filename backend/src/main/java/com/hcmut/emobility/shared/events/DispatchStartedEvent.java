package com.hcmut.emobility.shared.events;

/**
 * M7: a redistribution task moved to IN_PROGRESS - the vehicle has left the source Hub.
 * Consumers: M3 moves the vehicle AVAILABLE -> IN_TRANSIT; M1 refreshes the source Hub.
 * M7 releases the vehicle's parking space through ParkingDock before publishing.
 */
public class DispatchStartedEvent extends DomainEvent {
    private final Long taskId;
    private final Long vehicleId;
    private final Long fromHubId;
    private final Long toHubId;

    public DispatchStartedEvent(Long taskId, Long vehicleId, Long fromHubId, Long toHubId) {
        this.taskId = taskId;
        this.vehicleId = vehicleId;
        this.fromHubId = fromHubId;
        this.toHubId = toHubId;
    }

    public Long getTaskId() { return taskId; }
    public Long getVehicleId() { return vehicleId; }
    public Long getFromHubId() { return fromHubId; }
    public Long getToHubId() { return toHubId; }
}
