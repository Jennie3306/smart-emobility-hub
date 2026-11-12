package com.hcmut.emobility.m1monitoring.events;

import com.hcmut.emobility.m1monitoring.domain.ResourceState;
import com.hcmut.emobility.m1monitoring.repository.ResourceStateRepository;
import com.hcmut.emobility.m1monitoring.service.DashboardPublisher;
import com.hcmut.emobility.m1monitoring.service.MonitoringService;
import com.hcmut.emobility.shared.events.ChargingCompletedEvent;
import com.hcmut.emobility.shared.events.ChargingStartedEvent;
import com.hcmut.emobility.shared.events.DispatchCompletedEvent;
import com.hcmut.emobility.shared.events.DispatchFailedEvent;
import com.hcmut.emobility.shared.events.DispatchStartedEvent;
import com.hcmut.emobility.shared.events.ProcessedEventStore;
import com.hcmut.emobility.shared.events.ReservationConfirmedEvent;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
import com.hcmut.emobility.shared.events.VehicleFailedEvent;
import com.hcmut.emobility.shared.events.VehicleLowBatteryEvent;
import com.hcmut.emobility.shared.events.VehiclePickedUpEvent;
import com.hcmut.emobility.shared.events.VehicleReturnedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * FR-M1-06. M1 tu xu ly su kien cua module khac trong package cua minh;
 * ben phat khong duoc goi thang vao day.
 *
 * Buoc dau tien luon la kiem tra eventId: hop dong su kien o Phu luc A khong
 * bao dam moi su kien chi den dung mot lan, ma ap dung hai lan thi so lieu sai.
 * Khoa chong trung la ("M1", eventId): M3 cung nghe mot so su kien nay, neu
 * dung chung mot khoa thi ben nao xu ly truoc se lam ben kia bo qua.
 *
 * Thu tu: moi listener o day mang @Order(100). Module so huu trang thai (M3 voi
 * xe) cap nhat o @Order(0) truoc, M1 dem lai sau, nen so day xuong khong bi cu.
 */
@Component
public class MonitoringEventHandler {

    private static final String CONSUMER = "M1";

    private static final Logger log = LoggerFactory.getLogger(MonitoringEventHandler.class);

    private final MonitoringService monitoring;
    private final ResourceStateRepository resources;
    private final DashboardPublisher publisher;
    private final ProcessedEventStore processed;

    public MonitoringEventHandler(MonitoringService monitoring,
                                  ResourceStateRepository resources,
                                  DashboardPublisher publisher,
                                  ProcessedEventStore processed) {
        this.monitoring = monitoring;
        this.resources = resources;
        this.publisher = publisher;
        this.processed = processed;
    }

    /**
     * The owner of a parking space (M4) or charging point (M5) changed its state -> update the read model,
     * recount and push. Appendix A v2: only owners publish this event, so M1 mirrors it as is; raw sensor
     * readings (DeviceReadingReportedEvent) go to the owners and never reach the dashboard directly.
     */
    @EventListener
    @Order(100)
    @Transactional
    public void onResourceStateUpdated(ResourceStateUpdatedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) {
            log.debug("[M1] Bo qua su kien da xu ly {}", event.getEventId());
            return;
        }

        String type = event.getResourceType();
        if (!MonitoringService.PARKING.equals(type) && !MonitoringService.CHARGING.equals(type)) {
            pushHub(event.getHubId());
            return;
        }

        try {
            Optional<ResourceState> existing =
                    resources.findByResourceTypeAndResourceId(type, event.getResourceId());
            String state = normalise(type, event.getNewState());

            if (existing.isPresent()) {
                existing.get().applyState(state);
            } else {
                // A resource the owner created after V10 seeded the read model: record it.
                resources.save(new ResourceState(type, event.getResourceId(),
                        event.getHubId(), state));
                log.info("[M1] Ghi nhan tai nguyen moi {} {} o hub {}",
                        type, event.getResourceId(), event.getHubId());
            }
        } catch (Exception ex) {
            log.error("[M1] Cap nhat mo hinh doc that bai: {}", ex.getMessage(), ex);
        }

        pushHub(event.getHubId());
    }

    /**
     * M3 bao co xe vua tra ve hub -> so xe kha dung tang, cap nhat ngay.
     * Khong co listener nay thi dashboard phai cho het chu ky 30 giay moi thay.
     */
    @EventListener
    @Order(100)
    @Transactional(readOnly = true)
    public void onVehicleReturned(VehicleReturnedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        pushHub(event.getHubId());
    }

    /** M3 bao xe hong -> xe chuyen sang MAINTENANCE nen khong con tinh la kha dung. */
    @EventListener
    @Order(100)
    @Transactional(readOnly = true)
    public void onVehicleFailed(VehicleFailedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        pushHub(event.getHubId());
    }

    /** M7 dieu chuyen xe xong -> ca hub di va hub den deu doi so xe kha dung. */
    @EventListener
    @Order(100)
    @Transactional(readOnly = true)
    public void onDispatchCompleted(DispatchCompletedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        pushHub(event.getFromHubId());
        pushHub(event.getToHubId());
    }

    /** M2 giu xe cho sinh vien -> hub mat mot xe kha dung. M3 doi trang thai xe o @Order(0). */
    @EventListener
    @Order(100)
    @Transactional(readOnly = true)
    public void onReservationConfirmed(ReservationConfirmedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        pushHub(event.getHubId());
    }

    /** M3 giao xe cho sinh vien -> hub xuat phat mat mot xe kha dung. */
    @EventListener
    @Order(100)
    @Transactional(readOnly = true)
    public void onVehiclePickedUp(VehiclePickedUpEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        pushHub(event.getHubId());
    }

    /** M7 started a redistribution task -> the source hub has one vehicle less. */
    @EventListener
    @Order(100)
    @Transactional(readOnly = true)
    public void onDispatchStarted(DispatchStartedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        pushHub(event.getFromHubId());
    }

    /** M7 marked a started task FAILED -> the vehicle is back at the source hub. */
    @EventListener
    @Order(100)
    @Transactional(readOnly = true)
    public void onDispatchFailed(DispatchFailedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        pushHub(event.getFromHubId());
    }

    /** M3 flagged a rental vehicle LOW_BATTERY -> the hub has one available vehicle less. */
    @EventListener
    @Order(100)
    @Transactional(readOnly = true)
    public void onVehicleLowBattery(VehicleLowBatteryEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        pushHub(event.getHubId());
    }

    /** M5 started a charging session -> refresh the hub (port and, for rental vehicles, vehicle counts). */
    @EventListener
    @Order(100)
    @Transactional(readOnly = true)
    public void onChargingStarted(ChargingStartedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        pushHub(event.getHubId());
    }

    /** M5 finished a charging session -> a charged rental vehicle is available again (Appendix A). */
    @EventListener
    @Order(100)
    @Transactional(readOnly = true)
    public void onChargingCompleted(ChargingCompletedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        pushHub(event.getHubId());
    }

    /**
     * M6 va M1 dung tu vung trang thai khac nhau cho cung mot thiet bi:
     * M6 phat "AVAILABLE" cho tru sac con rong, V10 cua M1 ghi "FREE".
     * Khong quy doi thi tru do khong duoc dem la con trong, nen so tru trong
     * tren dashboard giam sai sau moi NORMAL_TICK. M1 la ben nhan nen quy doi
     * tai day, khong bat M6 doi tu vung. Gia tri la thi giu nguyen.
     */
    public static String normalise(String type, String state) {
        if (state == null) return null;
        String s = state.trim().toUpperCase();
        if (MonitoringService.CHARGING.equals(type)) {
            return switch (s) {
                case "AVAILABLE", "IDLE" -> "FREE";
                case "OCCUPIED", "CHARGING" -> "IN_USE";
                default -> s;
            };
        }
        if (MonitoringService.PARKING.equals(type)) {
            return switch (s) {
                case "AVAILABLE", "EMPTY" -> "FREE";
                case "IN_USE" -> "OCCUPIED";
                default -> s;
            };
        }
        return s;
    }

    private void pushHub(Long hubId) {
        if (hubId == null) return;
        try {
            monitoring.buildHubStatus(hubId).ifPresent(publisher::publishHubStatus);
        } catch (Exception ex) {
            log.error("[M1] Cap nhat hub {} that bai: {}", hubId, ex.getMessage(), ex);
        }
    }
}
