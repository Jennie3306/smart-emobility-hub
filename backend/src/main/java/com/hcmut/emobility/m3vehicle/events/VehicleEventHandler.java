package com.hcmut.emobility.m3vehicle.events;

import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;
import com.hcmut.emobility.m3vehicle.domain.VehicleState;
import com.hcmut.emobility.m3vehicle.repository.RentalVehicleRepository;
import com.hcmut.emobility.shared.events.DispatchCompletedEvent;
import com.hcmut.emobility.shared.events.ProcessedEventStore;
import com.hcmut.emobility.shared.events.ReservationExpiredEvent;
import com.hcmut.emobility.shared.events.VehicleFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class VehicleEventHandler {

    private static final Logger log = LoggerFactory.getLogger(VehicleEventHandler.class);

    private final RentalVehicleRepository repository;
    private final ProcessedEventStore processed;

    public VehicleEventHandler(RentalVehicleRepository repository, ProcessedEventStore processed) {
        this.repository = repository;
        this.processed = processed;
    }

    @EventListener
    @Transactional
    public void onVehicleFailed(VehicleFailedEvent event) {
        if (!processed.markIfNew("M3",event.getEventId())) return;
        try {
            repository.findById(event.getVehicleId()).ifPresent(vehicle -> {
                vehicle.changeState(VehicleState.MAINTENANCE);
                vehicle.clearReservation();
                log.info("[M3] VehicleFailed: vehicle={} -> MAINTENANCE", event.getVehicleId());
            });
        } catch (Exception ex) {
            log.error("[M3] Xu ly VehicleFailed that bai: {}", ex.getMessage(), ex);
        }
    }

    @EventListener
    @Transactional
    public void onReservationExpired(ReservationExpiredEvent event) {
        if (!processed.markIfNew("M3",event.getEventId())) return;
        if (!"VEHICLE".equals(event.getResourceType())) return;
        try {
            RentalVehicle vehicle = repository.findById(event.getResourceId()).orElse(null);
            if (vehicle != null && vehicle.getState() == VehicleState.RESERVED) {
                vehicle.changeState(VehicleState.AVAILABLE);
                vehicle.clearReservation();
                log.info("[M3] ReservationExpired: vehicle={} -> AVAILABLE", event.getResourceId());
            }
        } catch (Exception ex) {
            log.error("[M3] Xu ly ReservationExpired that bai: {}", ex.getMessage(), ex);
        }
    }

    @EventListener
    @Transactional
    public void onDispatchCompleted(DispatchCompletedEvent event) {
        if (!processed.markIfNew("M3",event.getEventId())) return;
        try {
            repository.findById(event.getVehicleId()).ifPresent(vehicle -> {
                vehicle.setCurrentHubId(event.getToHubId());
                if (vehicle.getState() == VehicleState.IN_TRANSIT) {
                    vehicle.changeState(VehicleState.AVAILABLE);
                }
                log.info("[M3] DispatchCompleted: vehicle={} hub {} -> {}",
                        event.getVehicleId(), event.getFromHubId(), event.getToHubId());
            });
        } catch (Exception ex) {
            log.error("[M3] Xu ly DispatchCompleted that bai: {}", ex.getMessage(), ex);
        }
    }

    // ReservationConfirmed / ReservationCancelled / ReservationCheckedIn: xu ly tai
    // ReservationVehicleHandler (fail-fast de M2 rollback khi khong giu duoc xe).
    // Tranh hai listener cung doi trang thai mot xe.
}
