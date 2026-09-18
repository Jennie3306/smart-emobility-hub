package com.hcmut.emobility.m3vehicle.events;

import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;
import com.hcmut.emobility.m3vehicle.domain.VehicleState;
import com.hcmut.emobility.m3vehicle.repository.RentalVehicleRepository;
import com.hcmut.emobility.shared.events.ProcessedEventStore;
import com.hcmut.emobility.shared.events.ReservationExpiredEvent;
import com.hcmut.emobility.shared.events.VehicleFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * MAU cho moi module: module tu xu ly su kien cua nguoi khac TRONG package cua minh.
 * Ben phat (M6, M2) khong duoc dong vao code cua M3.
 */
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
        if (!processed.markIfNew(event.getEventId())) return;   // chong xu ly trung
        try {
            repository.findById(event.getVehicleId()).ifPresent(v ->
                    v.changeState(VehicleState.MAINTENANCE));
        } catch (Exception ex) {
            log.error("[M3] Xu ly VehicleFailed that bai: {}", ex.getMessage(), ex);
        }
    }

    @EventListener
    @Transactional
    public void onReservationExpired(ReservationExpiredEvent event) {
        if (!processed.markIfNew(event.getEventId())) return;
        if (!"VEHICLE".equals(event.getResourceType())) return;
        try {
            RentalVehicle vehicle = repository.findById(event.getResourceId()).orElse(null);
            if (vehicle != null && vehicle.getState() == VehicleState.RESERVED) {
                vehicle.changeState(VehicleState.AVAILABLE);
            }
        } catch (Exception ex) {
            log.error("[M3] Xu ly ReservationExpired that bai: {}", ex.getMessage(), ex);
        }
    }
}
