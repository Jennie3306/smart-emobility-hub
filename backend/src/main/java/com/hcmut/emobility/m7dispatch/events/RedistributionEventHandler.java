package com.hcmut.emobility.m7dispatch.events;

import com.hcmut.emobility.shared.events.HubNearCapacityEvent;
import com.hcmut.emobility.shared.events.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * FR-M7-04. Nhan canh bao cua M1 qua su kien dung chung o Phu luc A.
 * Ban hien tai chi ghi nhan; viec sinh de xuat dieu chuyen tu dong chua lam,
 * ghi vao muc 4.4 Sai lech cua M7.
 */
@Component
public class RedistributionEventHandler {

    private static final Logger log = LoggerFactory.getLogger(RedistributionEventHandler.class);
    private static final String CONSUMER = "M7";

    private final ProcessedEventStore processed;

    public RedistributionEventHandler(ProcessedEventStore processed) {
        this.processed = processed;
    }

    @EventListener
    public void onHubNearCapacity(HubNearCapacityEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        // M1 gui ty le % cho PARKING, nhung so xe kha dung (khong phai %) cho VEHICLE.
        boolean vehicles = "VEHICLE".equals(event.getResourceType());
        log.info("[M7] Nhan canh bao tu M1: hub {} {} = {}{}", event.getHubId(), event.getResourceType(),
                event.getUsagePercent(), vehicles ? " xe kha dung" : "%");
    }
}
