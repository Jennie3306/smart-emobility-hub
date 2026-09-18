package com.hcmut.emobility.shared.events;

/**
 * Phat boi: M6
 * Ben nhan: M1 cap nhat dashboard; M8 dung lam du lieu snapshot
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
