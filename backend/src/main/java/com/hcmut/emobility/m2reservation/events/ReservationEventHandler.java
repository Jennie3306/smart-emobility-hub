package com.hcmut.emobility.m2reservation.events;

import com.hcmut.emobility.m2reservation.domain.ResourceType;
import com.hcmut.emobility.m2reservation.service.ReservationService;
import com.hcmut.emobility.shared.events.VehicleFailedEvent;
import com.hcmut.emobility.shared.events.VehiclePickedUpEvent;
import com.hcmut.emobility.shared.events.VehicleReturnedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * M2 handles other modules' events inside its own package (same pattern as M3's VehicleEventHandler).
 *
 * Transactions:
 * - VehicleFailed: M2 reacts AFTER the publisher's transaction has committed (or immediately when the publisher
 *   has no transaction, e.g. M6's simulator), and the service runs in its own transaction (REQUIRES_NEW).
 *   A failure in M2 is logged and rolls back only M2's work; it can no longer mark the publisher's transaction
 *   rollback-only and surface as UnexpectedRollbackException in M6. Waiting for the commit also means M2 sees
 *   the failed vehicle already in MAINTENANCE, so the follow-up ReservationCancelledEvent never makes M3 update
 *   a row the publisher still holds locked.
 * - VehicleReturned and VehiclePickedUp: deliberately stay inside M3's transaction (fail-fast, no try/catch),
 *   so the vehicle state and the reservation never disagree; an error rolls back both.
 *
 * Does NOT use ProcessedEventStore: duplicates are handled by state checks in the service - only CONFIRMED /
 * CHECKED_IN reservations are processed, so receiving the same event again finds nothing to change (TC-M2-12).
 */
@Component
public class ReservationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventHandler.class);

    private final ReservationService service;

    public ReservationEventHandler(ReservationService service) {
        this.service = service;
    }

    /** FR-M2-08 (actor IoT Simulator via M6). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onVehicleFailed(VehicleFailedEvent event) {
        try {
            service.handleVehicleFailed(event.getVehicleId());
        } catch (Exception ex) {
            log.error("[M2] Failed to handle VehicleFailed for vehicle {}: {}", event.getVehicleId(), ex.getMessage(), ex);
        }
    }

    /**
     * FR-M2-09 (actor M3): closes the reservation inside M3's return transaction, so the vehicle becoming
     * AVAILABLE and the reservation becoming COMPLETED commit or roll back together. No try/catch: catching
     * here could not prevent the rollback anyway, so the real error reaches M3 instead of UnexpectedRollbackException.
     */
    @EventListener
    public void onVehicleReturned(VehicleReturnedEvent event) {
        service.completeByResource(ResourceType.VEHICLE, event.getVehicleId());
    }

    /** Pick-up made directly through M3: check the reservation in, inside M3's transaction (fail-fast). */
    @EventListener
    public void onVehiclePickedUp(VehiclePickedUpEvent event) {
        service.pickedUp(event.getVehicleId(), event.getUserId());
    }
}
