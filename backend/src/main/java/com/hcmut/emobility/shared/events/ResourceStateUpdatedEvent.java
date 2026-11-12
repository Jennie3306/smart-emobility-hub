package com.hcmut.emobility.shared.events;

/**
 * Trang thai moi cua mot tai nguyen (cho do, tru sac, xe).
 *
 * Phat boi:
 *   - chu so huu trang thai: M4 (PARKING_SPACE), M5 (CHARGING_POINT) - source = null
 *   - M6 khi doc so lieu thiet bi - source = SOURCE_DEVICE ("M6")
 * Ben nhan: M1 cap nhat dashboard; M8 dung lam du lieu snapshot; M4/M5 nhan tin hieu suc khoe tu M6.
 *
 * Chot boi nhom truong 12/11/2026: moi tai nguyen co mot chu so huu duy nhat. So doc cua M6 chi la
 * tin hieu dau vao cho chu so huu (M5 xu ly hong / phuc hoi tru sac, M4 khoa / mo cho); M1 chi phan
 * chieu trang thai do chu so huu phat, nen bo qua so doc M6 cho PARKING_SPACE va CHARGING_POINT.
 * Nho vay dashboard, M4, M5 va M8 luon cung mot so lieu.
 */
public class ResourceStateUpdatedEvent extends DomainEvent {

    /** source cua so doc thiet bi tu M6. */
    public static final String SOURCE_DEVICE = "M6";

    private final Long hubId;
    private final String resourceType;
    private final Long resourceId;
    private final String newState;
    private final String source;

    /** Do chu so huu trang thai phat (M4, M5). */
    public ResourceStateUpdatedEvent(Long hubId, String resourceType, Long resourceId, String newState) {
        this(hubId, resourceType, resourceId, newState, null);
    }

    public ResourceStateUpdatedEvent(Long hubId, String resourceType, Long resourceId, String newState,
                                     String source) {
        this.hubId = hubId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.newState = newState;
        this.source = source;
    }

    public Long getHubId() { return hubId; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public String getNewState() { return newState; }
    public String getSource() { return source; }

    /** true khi day la so doc thiet bi cua M6, khong phai trang thai do chu so huu quyet dinh. */
    public boolean isDeviceReading() { return SOURCE_DEVICE.equals(source); }
}
