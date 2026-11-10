package com.hcmut.emobility.shared.events;

import java.time.OffsetDateTime;

/**
 * Phat boi: M4 (sinh vien dat cho do va chon "sac trong luc do")
 * Ben nhan: M5 tao ChargingSession o trang thai REQUESTED va dua vao hang doi uu tien.
 *
 * Chot boi nhom truong 10/11/2026: M4 khong goi thang service cua M5, ma phat su kien nay.
 * Neu cho do bi huy hoac het han, M5 tu nghe ReservationCancelledEvent /
 * ReservationExpiredEvent (resourceType = PARKING_SPACE, resourceId = parkingSpaceId)
 * de huy phien sac gan voi cho do do.
 */
public class ChargingRequestedEvent extends DomainEvent {

    private final Long userId;
    private final Long hubId;
    private final Long parkingSpaceId;
    /** "PERSONAL" (xe ca nhan, id trong personal_vehicle cua M4) hoac "RENTAL" (xe thue, id rental_vehicle cua M3). */
    private final String vehicleKind;
    private final Long vehicleId;
    /** Muc pin nguoi dung khai bao khi dat, 0..100; null neu khong biet. */
    private final Integer batteryLevel;
    private final OffsetDateTime arriveAt;
    private final OffsetDateTime leaveAt;

    public ChargingRequestedEvent(Long userId, Long hubId, Long parkingSpaceId,
                                  String vehicleKind, Long vehicleId, Integer batteryLevel,
                                  OffsetDateTime arriveAt, OffsetDateTime leaveAt) {
        this.userId = userId;
        this.hubId = hubId;
        this.parkingSpaceId = parkingSpaceId;
        this.vehicleKind = vehicleKind;
        this.vehicleId = vehicleId;
        this.batteryLevel = batteryLevel;
        this.arriveAt = arriveAt;
        this.leaveAt = leaveAt;
    }

    public Long getUserId() { return userId; }
    public Long getHubId() { return hubId; }
    public Long getParkingSpaceId() { return parkingSpaceId; }
    public String getVehicleKind() { return vehicleKind; }
    public Long getVehicleId() { return vehicleId; }
    public Integer getBatteryLevel() { return batteryLevel; }
    public OffsetDateTime getArriveAt() { return arriveAt; }
    public OffsetDateTime getLeaveAt() { return leaveAt; }
}
