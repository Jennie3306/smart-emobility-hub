package com.hcmut.emobility.m3vehicle.events;

import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;
import com.hcmut.emobility.m3vehicle.domain.SessionStatus;
import com.hcmut.emobility.m3vehicle.domain.VehicleState;
import com.hcmut.emobility.m3vehicle.repository.RentalSessionRepository;
import com.hcmut.emobility.m3vehicle.repository.RentalVehicleRepository;
import com.hcmut.emobility.shared.events.ChargingCompletedEvent;
import com.hcmut.emobility.shared.events.ChargingStartedEvent;
import com.hcmut.emobility.shared.events.DeviceReadingReportedEvent;
import com.hcmut.emobility.shared.events.DispatchCompletedEvent;
import com.hcmut.emobility.shared.events.DispatchFailedEvent;
import com.hcmut.emobility.shared.events.DispatchStartedEvent;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.ProcessedEventStore;
import com.hcmut.emobility.shared.events.ReservationExpiredEvent;
import com.hcmut.emobility.shared.events.VehicleFailedEvent;
import com.hcmut.emobility.shared.events.VehicleLowBatteryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class VehicleEventHandler {

    private static final Logger log = LoggerFactory.getLogger(VehicleEventHandler.class);
    private static final String RENTAL = "RENTAL";

    private final RentalVehicleRepository repository;
    private final RentalSessionRepository sessions;
    private final ProcessedEventStore processed;
    private final EventPublisher publisher;
    private final int lowBatteryThreshold;

    public VehicleEventHandler(RentalVehicleRepository repository,
                               RentalSessionRepository sessions,
                               ProcessedEventStore processed,
                               EventPublisher publisher,
                               @Value("${app.charging.low-battery-threshold}") int lowBatteryThreshold) {
        this.repository = repository;
        this.sessions = sessions;
        this.processed = processed;
        this.publisher = publisher;
        this.lowBatteryThreshold = lowBatteryThreshold;
    }

    @EventListener
    @Order(0)
    @Transactional
    public void onDeviceReadingReported(DeviceReadingReportedEvent event) {
        if (!"VEHICLE".equals(event.getResourceType()) || event.getBatteryLevel() == null) return;
        if (!processed.markIfNew("M3", event.getEventId())) return;
        repository.findById(event.getResourceId()).ifPresent(vehicle -> {
            if (vehicle.getState() == VehicleState.CHARGING) return;
            int level = event.getBatteryLevel();
            vehicle.updateBattery(level);
            if (vehicle.getState() == VehicleState.AVAILABLE && level < lowBatteryThreshold
                    && vehicle.getCurrentHubId() != null) {
                vehicle.changeState(VehicleState.LOW_BATTERY);
                repository.flush();
                publisher.publish(new VehicleLowBatteryEvent(vehicle.getId(), level, vehicle.getCurrentHubId()));
                log.info("[M3] Battery reading {}% for vehicle {} -> LOW_BATTERY", level, vehicle.getId());
            }
        });
    }

    /** M5 started charging a rental vehicle: LOW_BATTERY -> CHARGING */
    @EventListener
    @Order(0)
    @Transactional
    public void onChargingStarted(ChargingStartedEvent event) {
        if (!RENTAL.equals(event.getVehicleKind())) return;
        if (!processed.markIfNew("M3", event.getEventId())) return;
        repository.findById(event.getVehicleId()).ifPresent(vehicle -> {
            if (vehicle.getState() != VehicleState.LOW_BATTERY) {
                log.info("[M3] ChargingStarted for vehicle {} in state {}: state kept", vehicle.getId(), vehicle.getState());
                return;
            }
            vehicle.changeState(VehicleState.CHARGING);
            log.info("[M3] ChargingStarted: vehicle={} -> CHARGING", vehicle.getId());
        });
    }

    /** M5 finished charging a rental vehicle: CHARGING -> AVAILABLE with the final battery level (Appendix A). */
    @EventListener
    @Order(0)
    @Transactional
    public void onChargingCompleted(ChargingCompletedEvent event) {
        if (!RENTAL.equals(event.getVehicleKind())) return;
        if (!processed.markIfNew("M3", event.getEventId())) return;
        repository.findById(event.getVehicleId()).ifPresent(vehicle -> {
            vehicle.updateBattery(event.getFinalBatteryLevel());
            if (vehicle.getState() == VehicleState.CHARGING) {
                vehicle.changeState(VehicleState.AVAILABLE);
            }
            log.info("[M3] ChargingCompleted: vehicle={} battery {}% state {}", vehicle.getId(),
                    event.getFinalBatteryLevel(), vehicle.getState());
        });
    }

    /** M7: the vehicle left its Hub, AVAILABLE -> IN_TRANSIT. */
    @EventListener
    @Order(0)
    @Transactional
    public void onDispatchStarted(DispatchStartedEvent event) {
        if (!processed.markIfNew("M3", event.getEventId())) return;
        repository.findById(event.getVehicleId()).ifPresent(vehicle -> {
            if (vehicle.getState() != VehicleState.AVAILABLE) {
                log.info("[M3] DispatchStarted for vehicle {} in state {}: state kept", vehicle.getId(), vehicle.getState());
                return;
            }
            vehicle.changeState(VehicleState.IN_TRANSIT);
            vehicle.setCurrentHubId(null);
            log.info("[M3] DispatchStarted: vehicle={} leaves hub {} -> IN_TRANSIT", vehicle.getId(), event.getFromHubId());
        });
    }

    /** M7 marked a started task FAILED: the vehicle is back at the source Hub, IN_TRANSIT -> AVAILABLE. */
    @EventListener
    @Order(0)
    @Transactional
    public void onDispatchFailed(DispatchFailedEvent event) {
        if (!processed.markIfNew("M3", event.getEventId())) return;
        repository.findById(event.getVehicleId()).ifPresent(vehicle -> {
            vehicle.setCurrentHubId(event.getFromHubId());
            if (vehicle.getState() == VehicleState.IN_TRANSIT) {
                vehicle.changeState(VehicleState.AVAILABLE);
            }
            log.info("[M3] DispatchFailed: vehicle={} back at hub {}", vehicle.getId(), event.getFromHubId());
        });
    }

    @EventListener
    @Order(0) // M3 so huu trang thai xe
    @Transactional
    public void onVehicleFailed(VehicleFailedEvent event) {
        if (!processed.markIfNew("M3", event.getEventId())) return;
        try {
            repository.findById(event.getVehicleId()).ifPresent(vehicle -> {
                sessions.findByVehicleIdAndStatus(vehicle.getId(), SessionStatus.ACTIVE)
                        .ifPresent(session -> {
                            session.complete();
                            log.info("[M3] VehicleFailed: closed active session {} for vehicle {}",
                                    session.getId(), vehicle.getId());
                        });
                vehicle.changeState(VehicleState.MAINTENANCE);
                vehicle.clearReservation();
                log.info("[M3] VehicleFailed: vehicle={} -> MAINTENANCE", event.getVehicleId());
            });
        } catch (Exception ex) {
            log.error("[M3] Xu ly VehicleFailed that bai: {}", ex.getMessage(), ex);
        }
    }

    @EventListener
    @Order(0) // M3 so huu trang thai xe: cap nhat truoc, M1 dem lai o @Order(100)
    @Transactional
    public void onReservationExpired(ReservationExpiredEvent event) {
        if (!processed.markIfNew("M3", event.getEventId())) return;
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
    @Order(0)
    @Transactional
    public void onDispatchCompleted(DispatchCompletedEvent event) {
        if (!processed.markIfNew("M3", event.getEventId())) return;
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
}