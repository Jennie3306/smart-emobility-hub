package com.hcmut.emobility.m2reservation.events;

import com.hcmut.emobility.m2reservation.domain.ResourceType;
import com.hcmut.emobility.m2reservation.service.ReservationService;
import com.hcmut.emobility.shared.events.VehicleFailedEvent;
import com.hcmut.emobility.shared.events.VehicleReturnedEvent;
import com.hcmut.emobility.shared.events.VehiclePickedUpEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * M2 handles other modules' events inside its own package (same pattern as M3's VehicleEventHandler).
 *
 * Does NOT use ProcessedEventStore: the shared store keys only on eventId, so when M3 also listens to
 * VehicleFailedEvent and marks it first, this handler would be skipped (VD-20).
 * Duplicates are handled by state checks in the service instead: only CONFIRMED / CHECKED_IN reservations
 * are processed, so receiving the same event again finds nothing to change.
 */
@Component
public class ReservationEventHandler {
    @EventListener @Transactional
    public void onVehiclePickedUp(VehiclePickedUpEvent e) { service.pickedUp(e.getVehicleId(),e.getUserId()); }

    private static final Logger log = LoggerFactory.getLogger(ReservationEventHandler.class);

    private final ReservationService service;

    public ReservationEventHandler(ReservationService service) {
        this.service = service;
    }

    @EventListener
    @Transactional
    public void onVehicleFailed(VehicleFailedEvent event) {          // FR-M2-08
        try {
            service.handleVehicleFailed(event.getVehicleId());
        } catch (Exception ex) {
            log.error("[M2] Failed to handle VehicleFailed: {}", ex.getMessage(), ex);
        }
    }

    @EventListener
    @Transactional
    public void onVehicleReturned(VehicleReturnedEvent event) {      // FR-M2-09
        try {
            service.completeByResource(ResourceType.VEHICLE, event.getVehicleId());
        } catch (Exception ex) {
            log.error("[M2] Failed to handle VehicleReturned: {}", ex.getMessage(), ex);
        }
    }
}
