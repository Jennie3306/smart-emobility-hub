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
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Consumes only the shared event contract in shared/events; M5 does not call other module services. */
@Component
public class ChargingEventHandler {

    private static final String CONSUMER = "M5";
    private final ChargingService charging;
    private final ProcessedEventStore processed;

    public ChargingEventHandler(ChargingService charging, ProcessedEventStore processed) {
        this.charging = charging;
        this.processed = processed;
    }

    @EventListener
    @Order(0)
    @Transactional
    public void onChargingRequested(ChargingRequestedEvent event) {
        if (!processed.markIfNew(CONSUMER, event.getEventId())) return;
        charging.handleM4ChargingRequest(event.getUserId(), event.getHubId(), event.getParkingSpaceId(),
                VehicleKind.valueOf(event.getVehicleKind()), event.getVehicleId(), event.getBatteryLevel(),
                event.getLeaveAt());
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
        if ("CHARGING_POINT".equals(event.getResourceType())) {
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
