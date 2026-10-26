package com.hcmut.emobility.m1monitoring.events;

import com.hcmut.emobility.m1monitoring.domain.ResourceState;
import com.hcmut.emobility.m1monitoring.repository.ResourceStateRepository;
import com.hcmut.emobility.m1monitoring.service.DashboardPublisher;
import com.hcmut.emobility.m1monitoring.service.MonitoringService;
import com.hcmut.emobility.shared.events.DispatchCompletedEvent;
import com.hcmut.emobility.shared.events.ProcessedEventStore;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
import com.hcmut.emobility.shared.events.VehicleFailedEvent;
import com.hcmut.emobility.shared.events.VehicleReturnedEvent;
import com.hcmut.emobility.shared.events.ReservationConfirmedEvent;
import com.hcmut.emobility.shared.events.VehiclePickedUpEvent;
import org.springframework.core.annotation.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * FR-M1-06. M1 tu xu ly su kien cua module khac trong package cua minh;
 * ben phat khong duoc goi thang vao day.
 *
 * Buoc dau tien luon la kiem tra eventId: hop dong su kien o Phu luc A khong
 * bao dam moi su kien chi den dung mot lan, ma ap dung hai lan thi so lieu sai.
 */
@Component
public class MonitoringEventHandler {
    @EventListener @Order(100) @Transactional(readOnly=true)
    public void onReservationConfirmed(ReservationConfirmedEvent e) { pushHub(e.getHubId()); }
    @EventListener @Order(100) @Transactional(readOnly=true)
    public void onVehiclePickedUp(VehiclePickedUpEvent e) { pushHub(e.getHubId()); }

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
     * M6 bao tai nguyen doi trang thai -> cap nhat mo hinh doc, tinh lai, day xuong.
     * M6 giu trang thai trong bo nho va khong ghi database, nen neu M1 khong
     * cap nhat bang cua minh o day thi so lieu tren dashboard se dung yen.
     */
    @EventListener
    @Transactional
    public void onResourceStateUpdated(ResourceStateUpdatedEvent event) {
        if (!processed.markIfNew("M1",event.getEventId())) {
            log.debug("[M1] Dismiss su kien da xu ly {}", event.getEventId());
            return;
        }

        String type = event.getResourceType();
        if (!MonitoringService.PARKING.equals(type) && !MonitoringService.CHARGING.equals(type)) {
            // VEHICLE do M3 giu; M1 chi dem lai so xe kha dung.
            pushHub(event.getHubId());
            return;
        }

        try {
            Optional<ResourceState> existing =
                    resources.findByResourceTypeAndResourceId(type, event.getResourceId());

            if (existing.isPresent()) {
                existing.get().applyState(event.getNewState());
            } else {
                // Tai nguyen moi do M6 sinh them: ghi nhan de lan sau dem dung.
                resources.save(new ResourceState(type, event.getResourceId(),
                        event.getHubId(), event.getNewState()));
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
    @Transactional(readOnly = true)
    public void onVehicleReturned(VehicleReturnedEvent event) {
        if (!processed.markIfNew("M1",event.getEventId())) return;
        pushHub(event.getHubId());
    }

    /** M3 bao xe hong -> xe chuyen sang MAINTENANCE nen khong con tinh la kha dung. */
    @EventListener
    @Transactional(readOnly = true)
    public void onVehicleFailed(VehicleFailedEvent event) {
        if (!processed.markIfNew("M1",event.getEventId())) return;
        pushHub(event.getHubId());
    }

    /** M7 dieu chuyen xe xong -> ca hub di va hub den deu doi so xe kha dung. */
    @EventListener
    @Transactional(readOnly = true)
    public void onDispatchCompleted(DispatchCompletedEvent event) {
        if (!processed.markIfNew("M1",event.getEventId())) return;
        pushHub(event.getFromHubId());
        pushHub(event.getToHubId());
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
