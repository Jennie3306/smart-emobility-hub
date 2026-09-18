package com.hcmut.emobility.shared.events;

/**
 * Phat boi: M7
 * Ben nhan: M3 cap nhat vi tri xe; M1 cap nhat dashboard
 */
public class DispatchCompletedEvent extends DomainEvent {

    private final Long taskId;
    private final Long vehicleId;
    private final Long fromHubId;
    private final Long toHubId;

    public DispatchCompletedEvent(Long taskId, Long vehicleId, Long fromHubId, Long toHubId) {
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
