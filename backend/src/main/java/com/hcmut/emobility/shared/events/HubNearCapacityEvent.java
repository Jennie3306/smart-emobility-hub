package com.hcmut.emobility.shared.events;

/**
 * Phat boi: M1
 * Ben nhan: M7 de xuat dieu chuyen xe
 */
public class HubNearCapacityEvent extends DomainEvent {

    private final Long hubId;
    private final String resourceType;
    private final int usagePercent;

    public HubNearCapacityEvent(Long hubId, String resourceType, int usagePercent) {
        this.hubId = hubId;
        this.resourceType = resourceType;
        this.usagePercent = usagePercent;
    }

    public Long getHubId() { return hubId; }
    public String getResourceType() { return resourceType; }
    public int getUsagePercent() { return usagePercent; }
}
