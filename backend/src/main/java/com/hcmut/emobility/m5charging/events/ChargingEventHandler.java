package com.hcmut.emobility.m5charging.events;

import com.hcmut.emobility.m5charging.domain.VehicleKind;
import com.hcmut.emobility.m5charging.service.ChargingService;
import com.hcmut.emobility.shared.events.ChargerFailedEvent;
import com.hcmut.emobility.shared.events.ChargingRequestedEvent;
import com.hcmut.emobility.shared.events.ProcessedEventStore;
import com.hcmut.emobility.shared.events.RecommendationApprovedEvent;
import com.hcmut.emobility.shared.events.ReservationCancelledEvent;
import com.hcmut.emobility.shared.events.ReservationExpiredEvent;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
import com.hcmut.emobility.shared.events.VehicleLowBatteryEvent;
import com.hcmut.emobility.shared.events.VehicleReturnedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Consumes only the shared event contract in shared/events; M5 does not call other module services. */
@Component
public class ChargingEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ChargingEventHandler.class);
    private static final String CONSUMER = "M5";
    private final ChargingService charging;
    private final ProcessedEventStore processed;

    public ChargingEventHandler(ChargingService charging, ProcessedEventStore processed) {
        this.charging = charging;
        this.processed = processed;
    }

    /**
     * M4 -> M5. Runs only after M4's parking booking has committed, and the service call has its own
     * transaction, so a charging request M5 rejects is logged and never rolls back or fails M4
     * (review PR #11). Not transactional itself: an exception must not mark any outer work rollback-only.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Order(0)
    public void onChargingRequested(ChargingRequestedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        VehicleKind kind = parseVehicleKind(event.getVehicleKind());
        if (kind == null) {
            log.warn("[M5] ChargingRequested {} ignored: unknown vehicleKind '{}' (expected PERSONAL or RENTAL)",
                    event.getEventId(), event.getVehicleKind());
            return;
        }
        try {
            charging.handleM4ChargingRequest(event.getUserId(), event.getHubId(), event.getParkingSpaceId(),
                    kind, event.getVehicleId(), event.getBatteryLevel(), event.getArriveAt(), event.getLeaveAt());
        } catch (RuntimeException ex) {
            log.error("[M5] ChargingRequested {} for parking space {} failed: {}", event.getEventId(),
                    event.getParkingSpaceId(), ex.getMessage(), ex);
        }
    }

    private static VehicleKind parseVehicleKind(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return VehicleKind.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @EventListener
    @Order(0)
    @Transactional
    public void onChargerFailed(ChargerFailedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        charging.handleChargerFailed(event.getChargingPointId(), event.getHubId());
    }

    @EventListener
    @Order(0)
    @Transactional
    public void onResourceStateUpdated(ResourceStateUpdatedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        // Only device readings from M6 carry new information for M5; M5's own events (and M4's) are ignored.
        if (event.isDeviceReading() && "CHARGING_POINT".equals(event.getResourceType())) {
            charging.synchroniseChargingPoint(event.getResourceId(), event.getHubId(), event.getNewState());
        }
    }

    @EventListener
    @Order(0)
    @Transactional
    public void onVehicleReturned(VehicleReturnedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        charging.handleVehicleReturned(event.getVehicleId(), event.getHubId(), event.getBatteryLevel());
    }

    @EventListener
    @Order(0)
    @Transactional
    public void onVehicleLowBattery(VehicleLowBatteryEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        charging.handleVehicleLowBattery(event.getVehicleId(), event.getBatteryLevel());
    }

    @EventListener
    @Order(0)
    @Transactional
    public void onReservationCancelled(ReservationCancelledEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        if ("PARKING_SPACE".equals(event.getResourceType())) {
            charging.cancelParkingLinkedSessions(event.getResourceId());
        }
    }

    @EventListener
    @Order(0)
    @Transactional
    public void onReservationExpired(ReservationExpiredEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        if ("PARKING_SPACE".equals(event.getResourceType())) {
            charging.cancelParkingLinkedSessions(event.getResourceId());
        }
    }

    @EventListener
    @Order(0)
    @Transactional
    public void onRecommendationApproved(RecommendationApprovedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        charging.applyRecommendation(event.getRecommendationId(), event.getActionType());
    }
}
