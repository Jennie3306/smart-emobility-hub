package com.hcmut.emobility.m1monitoring.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Mo hinh doc cua M1: trang thai moi nhat cua tung cho do va tung tru sac,
 * cap nhat tu su kien ResourceStateUpdatedEvent do M6 phat.
 *
 * Day khong phai ban goc cua du lieu (ban goc thuoc M4/M5/M6) ma la ban chieu
 * phuc vu man hinh giam sat, nen M1 so huu va duoc phep ghi.
 */
@Entity
@Table(name = "m1_resource_state")
@IdClass(ResourceState.Key.class)
public class ResourceState {

    @Id
    @Column(name = "resource_type", nullable = false, length = 20)
    private String resourceType;      // PARKING_SPACE | CHARGING_POINT (tu vung cua M6)

    @Id
    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    @Column(nullable = false, length = 20)
    private String state;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ResourceState() { }

    public ResourceState(String resourceType, Long resourceId, Long hubId, String state) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.hubId = hubId;
        this.state = state;
        this.updatedAt = OffsetDateTime.now();
    }

    public void applyState(String newState) {
        this.state = newState;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public Long getHubId() { return hubId; }
    public String getState() { return state; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    /** Khoa chinh ghep: M6 danh so resourceId duy nhat trong pham vi moi loai. */
    public static class Key implements Serializable {
        private String resourceType;
        private Long resourceId;

        public Key() { }
        public Key(String resourceType, Long resourceId) {
            this.resourceType = resourceType;
            this.resourceId = resourceId;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(resourceType, k.resourceType)
                && Objects.equals(resourceId, k.resourceId);
        }
        @Override public int hashCode() { return Objects.hash(resourceType, resourceId); }
    }
}
