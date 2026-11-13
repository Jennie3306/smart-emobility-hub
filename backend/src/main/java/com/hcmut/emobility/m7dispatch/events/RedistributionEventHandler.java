package com.hcmut.emobility.m7dispatch.events;

import com.hcmut.emobility.m7dispatch.service.RecommendationService;
import com.hcmut.emobility.shared.events.HubNearCapacityEvent;
import com.hcmut.emobility.shared.events.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * FR-M7-04 (non-interactive). M1 raises HubNearCapacityEvent once per new alert; M7 turns it into a
 * redistribution proposal that waits for the Operator (FR-M7-02).
 *
 * Runs after M1's transaction has committed (fallbackExecution: also when published outside a transaction), and
 * the proposal is written in its own transaction, so M7 can never roll back M1's alert.
 * resourceType is PARKING (value = utilisation %) or VEHICLE (value = available vehicles).
 */
@Component
public class RedistributionEventHandler {

    private static final Logger log = LoggerFactory.getLogger(RedistributionEventHandler.class);
    private static final String CONSUMER = "M7";

    private final ProcessedEventStore processed;
    private final RecommendationService recommendations;

    public RedistributionEventHandler(ProcessedEventStore processed, RecommendationService recommendations) {
        this.processed = processed;
        this.recommendations = recommendations;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onHubNearCapacity(HubNearCapacityEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        boolean vehicles = "VEHICLE".equals(event.getResourceType());
        log.info("[M7] Nhan canh bao tu M1: hub {} {} = {}{}", event.getHubId(), event.getResourceType(),
                event.getUsagePercent(), vehicles ? " xe kha dung" : "%");
        try {
            recommendations.proposeForAlert(event.getHubId(), event.getResourceType(), event.getEventId().toString());
        } catch (Exception ex) {
            log.error("[M7] Could not build a proposal for hub {}: {}", event.getHubId(), ex.getMessage(), ex);
        }
    }
}
