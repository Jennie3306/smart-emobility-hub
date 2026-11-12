package com.hcmut.emobility.shared.events;

/**
 * The owner of a resource changed its state. Published after the change is saved.
 *
 * Publishers (one owner per resource type):
 *   - M4 for PARKING_SPACE (FREE, RESERVED, OCCUPIED, BLOCKED)
 *   - M5 for CHARGING_POINT (FREE, RESERVED, IN_USE, FAULTY, UNDER_REPAIR)
 * Consumers: M1 updates the dashboard, M8 keeps it for the What-if snapshot, M4 unblocks a parking space
 * when M5 reports its charging point operational again.
 *
 * Raw sensor readings travel as {@link DeviceReadingReportedEvent}; they never use this event
 * (Appendix A, event contract v2).
 */
public class ResourceStateUpdatedEvent extends DomainEvent {

    private final Long hubId;
    private final String resourceType;
    private final Long resourceId;
    private final String newState;

    public ResourceStateUpdatedEvent(Long hubId, String resourceType, Long resourceId, String newState) {
        this.hubId = hubId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.newState = newState;
    }

    public Long getHubId() { return hubId; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public String getNewState() { return newState; }
}
