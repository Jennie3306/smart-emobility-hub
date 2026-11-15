package com.hcmut.emobility.m3vehicle.events;

import com.hcmut.emobility.m3vehicle.domain.SessionStatus;
import com.hcmut.emobility.m3vehicle.domain.VehicleState;
import com.hcmut.emobility.m3vehicle.repository.RentalSessionRepository;
import com.hcmut.emobility.m3vehicle.repository.RentalVehicleRepository;
import com.hcmut.emobility.m3vehicle.service.VehicleService;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.events.ReservationCancelledEvent;
import com.hcmut.emobility.shared.events.ReservationCheckedInEvent;
import com.hcmut.emobility.shared.events.ReservationConfirmedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReservationVehicleHandler {
    private final RentalVehicleRepository vehicles;
    private final RentalSessionRepository sessions;
    private final VehicleService service;
    private final int lowBatteryThreshold;

    public ReservationVehicleHandler(
            RentalVehicleRepository vehicles,
            RentalSessionRepository sessions,
            VehicleService service,
            @Value("${app.charging.low-battery-threshold}") int lowBatteryThreshold) {
        this.vehicles = vehicles;
        this.sessions = sessions;
        this.service = service;
        this.lowBatteryThreshold = lowBatteryThreshold;
    }

    @EventListener @Order(0) @Transactional
    public void confirmed(ReservationConfirmedEvent e) {
        if (!"VEHICLE".equals(e.getResourceType())) return;
        var vehicle = vehicles.findById(e.getResourceId()).orElseThrow();
        if (vehicle.getState() == VehicleState.RESERVED && e.getUserId().equals(vehicle.getReservedByUserId())) return;
        if (vehicle.getBatteryLevel() < lowBatteryThreshold) {
            throw new BusinessException(
                    "BATTERY_TOO_LOW",
                    "Battery is below the rental threshold (< " + lowBatteryThreshold + "%)",
                    HttpStatus.BAD_REQUEST);
        }
        vehicle.reserve(e.getUserId());
        vehicles.flush();
    }

    @EventListener @Order(0) @Transactional
    public void checkedIn(ReservationCheckedInEvent e) {
        if (!"VEHICLE".equals(e.getResourceType())) return;
        var session = sessions.findByVehicleIdAndStatus(e.getResourceId(), SessionStatus.ACTIVE);
        if (session.isPresent() && e.getUserId().equals(session.get().getUserId())) return;
        service.pickUp(e.getResourceId(), e.getUserId());
    }

    @EventListener @Order(0) @Transactional
    public void cancelled(ReservationCancelledEvent e) {
        if (!"VEHICLE".equals(e.getResourceType())) return;
        vehicles.findById(e.getResourceId()).ifPresent(v -> {
            if (v.getState() == VehicleState.RESERVED) {
                v.changeState(VehicleState.AVAILABLE);
                v.clearReservation();
                vehicles.flush();
            }
        });
    }
}