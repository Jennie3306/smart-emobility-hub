package com.hcmut.emobility.shared.events;

/**
 * M7: a redistribution task that had started was marked FAILED - the vehicle goes back to the source Hub.
 * Consumers: M3 moves the vehicle IN_TRANSIT -> AVAILABLE at the source Hub; M1 refreshes the source Hub.
 * A task that fails before it starts publishes nothing (the vehicle never left).
 */
public class DispatchFailedEvent extends DomainEvent {
    private final Long taskId;
    private final Long vehicleId;
    private final Long fromHubId;

    public DispatchFailedEvent(Long taskId, Long vehicleId, Long fromHubId) {
        this.taskId = taskId;
        this.vehicleId = vehicleId;
        this.fromHubId = fromHubId;
    }

    public Long getTaskId() { return taskId; }
    public Long getVehicleId() { return vehicleId; }
    public Long getFromHubId() { return fromHubId; }
}
