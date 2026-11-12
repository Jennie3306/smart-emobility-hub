package com.hcmut.emobility.m4parking.events;

import com.hcmut.emobility.m4parking.domain.ParkingBookingLog;
import com.hcmut.emobility.m4parking.service.ParkingService;
import com.hcmut.emobility.shared.events.ChargerFailedEvent;
import com.hcmut.emobility.shared.events.ProcessedEventStore;
import com.hcmut.emobility.shared.events.ReservationCancelledEvent;
import com.hcmut.emobility.shared.events.ReservationCheckedInEvent;
import com.hcmut.emobility.shared.events.ReservationExpiredEvent;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Su kien M4 nhan (muc 7.2 Template_M4, Phu luc A). M4 giu trang thai ParkingSpace nen chay o @Order(0),
 * truoc M1 (@Order(100)) - M1 nhan ResourceStateUpdatedEvent M4 phat ra trong luc xu ly.
 * Moi su kien chi xu ly mot lan (ProcessedEventStore); cac method cua ParkingService cung idempotent.
 */
@Component
public class ParkingEventHandler {

    private static final String CONSUMER = "M4";
    private static final String PARKING_SPACE = "PARKING_SPACE";
    private static final String CHARGING_POINT = "CHARGING_POINT";

    private final ParkingService parking;
    private final ProcessedEventStore processed;

    public ParkingEventHandler(ParkingService parking, ProcessedEventStore processed) {
        this.parking = parking;
        this.processed = processed;
    }

    /** UC-M4-04: M2 danh dau no-show (qua 15 phut khong check-in) -> giai phong cho. */
    @EventListener
    @Order(0)
    @Transactional
    public void onReservationExpired(ReservationExpiredEvent event) {
        if (!PARKING_SPACE.equals(event.getResourceType())) return;
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        parking.applyRelease(event.getReservationId(), ParkingBookingLog.Status.NO_SHOW);
    }

    /** Sinh vien huy (qua M4 hoac M2) hoac cho bi khoa -> giai phong cho / go reservation. */
    @EventListener
    @Order(0)
    @Transactional
    public void onReservationCancelled(ReservationCancelledEvent event) {
        if (!PARKING_SPACE.equals(event.getResourceType())) return;
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        parking.applyRelease(event.getReservationId(), ParkingBookingLog.Status.CANCELLED);
    }

    /** Check-in di qua API cua M2 -> RESERVED thanh OCCUPIED (BR-M4-05). */
    @EventListener
    @Order(0)
    @Transactional
    public void onReservationCheckedIn(ReservationCheckedInEvent event) {
        if (!PARKING_SPACE.equals(event.getResourceType())) return;
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        parking.applyCheckIn(event.getReservationId());
    }

    /** BR-M4-06: tru sac gan voi cho do bi hong -> khoa cho. */
    @EventListener
    @Order(0)
    @Transactional
    public void onChargerFailed(ChargerFailedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        parking.handleChargerFailed(event.getChargingPointId());
    }

    /**
     * MaintenanceResolved (bang chuyen trang thai muc 4.1): M6 bao tru sac "AVAILABLE"/"IDLE" tro lai.
     * Chi nhan tu vung cua M6; "FREE" cua M5 la trang thai phan cong, khong phai bao tri xong.
     * Bao cao PARKING_SPACE tu cam bien M6 bi bo qua: M4 la nguon su that cua trang thai cho do.
     */
    @EventListener
    @Order(0)
    @Transactional
    public void onResourceStateUpdated(ResourceStateUpdatedEvent event) {
        if (!event.isDeviceReading() || !CHARGING_POINT.equals(event.getResourceType())
                || event.getNewState() == null) return;
        String state = event.getNewState().trim().toUpperCase();
        if (!state.equals("AVAILABLE") && !state.equals("IDLE")) return;
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        parking.handleChargerRecovered(event.getResourceId());
    }
}
