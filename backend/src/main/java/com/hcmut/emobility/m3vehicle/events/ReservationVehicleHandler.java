package com.hcmut.emobility.m3vehicle.events;
import com.hcmut.emobility.m3vehicle.domain.VehicleState;
import com.hcmut.emobility.m3vehicle.repository.*;
import com.hcmut.emobility.m3vehicle.domain.SessionStatus;
import com.hcmut.emobility.m3vehicle.service.VehicleService;
import com.hcmut.emobility.shared.events.*;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Owned M3 reactions. Failures propagate so the M2 operation rolls back rather than partially succeeding. */
@Component
public class ReservationVehicleHandler {
    private final RentalVehicleRepository vehicles;
    private final RentalSessionRepository sessions;
    private final VehicleService service;
    public ReservationVehicleHandler(RentalVehicleRepository vehicles,RentalSessionRepository sessions,VehicleService service) {
        this.vehicles=vehicles;this.sessions=sessions;this.service=service;
    }
    @EventListener @Order(0) @Transactional
    public void confirmed(ReservationConfirmedEvent e) {
        if(!"VEHICLE".equals(e.getResourceType())) return;
        var vehicle=vehicles.findById(e.getResourceId()).orElseThrow();
        if(vehicle.getState()==VehicleState.RESERVED && e.getUserId().equals(vehicle.getReservedByUserId())) return;
        vehicle.reserve(e.getUserId()); vehicles.flush();
    }
    @EventListener @Order(0) @Transactional
    public void checkedIn(ReservationCheckedInEvent e) {
        if(!"VEHICLE".equals(e.getResourceType())) return;
        var session=sessions.findByVehicleIdAndStatus(e.getResourceId(),SessionStatus.ACTIVE);
        if(session.isPresent() && e.getUserId().equals(session.get().getUserId())) return;
        service.pickUp(e.getResourceId(),e.getUserId());
    }
    @EventListener @Order(0) @Transactional
    public void cancelled(ReservationCancelledEvent e) {
        if(!"VEHICLE".equals(e.getResourceType())) return;
        vehicles.findById(e.getResourceId()).ifPresent(v -> {
            if(v.getState()==VehicleState.RESERVED) {v.changeState(VehicleState.AVAILABLE);v.clearReservation();vehicles.flush();}
        });
    }
}
