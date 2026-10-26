package com.hcmut.emobility.m3vehicle.service;

import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;
import com.hcmut.emobility.m3vehicle.domain.VehicleState;
import com.hcmut.emobility.m3vehicle.repository.RentalVehicleRepository;
import com.hcmut.emobility.m3vehicle.domain.RentalSession;
import com.hcmut.emobility.m3vehicle.domain.SessionStatus;
import com.hcmut.emobility.m3vehicle.repository.RentalSessionRepository;

import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.VehicleLowBatteryEvent;
import com.hcmut.emobility.shared.events.VehicleReturnedEvent;
import com.hcmut.emobility.shared.events.VehiclePickedUpEvent;
/*import org.aspectj.weaver.patterns.IVerificationRequired;*/
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hcmut.emobility.m3vehicle.dto.CurrentTripResponse;

@Service
public class VehicleService {

    private final RentalVehicleRepository repository;
    private final EventPublisher publisher;
    private final RentalSessionRepository sessionRepository;

    @Value("${app.charging.low-battery-threshold}")
    private int lowBatteryThreshold;
    private final HubServiceClient hubServiceClient;

    public VehicleService(RentalVehicleRepository repository,
                          RentalSessionRepository sessionRepository,
                          EventPublisher publisher,
                          HubServiceClient hubServiceClient) {
        this.repository = repository;
        this.sessionRepository = sessionRepository;
        this.publisher = publisher;
        this.hubServiceClient = hubServiceClient;
    }

    /**
     * Nhan xe da dat: RESERVED -> IN_USE.
     * Tien dieu kien: xe ton tai va dang o trang thai RESERVED.
     * Hau dieu kien: xe chuyen sang IN_USE.
     * Ngoai le: VEHICLE_NOT_FOUND, INVALID_STATE_TRANSITION, VEHICLE_NOT_AVAIL, BATTERY_TOO_LOW, RESERVATION_MISMATCH
     */
    @Transactional
    public void pickUp(Long vehicleId, Long userId) {
        RentalVehicle vehicle = findOrThrow(vehicleId);
        if (vehicle.getState() != VehicleState.RESERVED) {
            throw new BusinessException("VEHICLE_NOT_AVAIL",
                    "Vehicle is not reserved", HttpStatus.BAD_REQUEST);
        }
        if (vehicle.getBatteryLevel() < lowBatteryThreshold) {
            throw new BusinessException("BATTERY_TOO_LOW",
                    "Battery is below the rental threshold (< " + lowBatteryThreshold + "%)",
                    HttpStatus.BAD_REQUEST);
        }
        if (vehicle.getReservedByUserId() == null
                || !vehicle.getReservedByUserId().equals(userId)) {
            throw new BusinessException("RESERVATION_MISMATCH",
                    "This reservation does not belong to you", HttpStatus.FORBIDDEN);
        }

        Long originHubId=vehicle.getCurrentHubId();
        vehicle.changeState(VehicleState.IN_USE);
        vehicle.setCurrentHubId(null);
        vehicle.clearReservation();

        RentalSession session = RentalSession.start(vehicleId, userId);
        sessionRepository.save(session);
        repository.flush();
        publisher.publish(new VehiclePickedUpEvent(vehicleId,userId,originHubId));
    }

    /**
     * Tra xe tai mot Hub: IN_USE -> AVAILABLE (hoac LOW_BATTERY neu pin duoi nguong).
     * Phat VehicleReturnedEvent de M5 xet dua vao hang doi sac va M2 dong dat cho.
     */
    @Transactional
    public void returnVehicle(Long vehicleId, Long hubId, int batteryLevel, Long userId) {
        RentalVehicle vehicle = findOrThrow(vehicleId);
        RentalSession activeSession = sessionRepository
                .findByVehicleIdAndStatus(vehicleId, SessionStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "Trip not found", HttpStatus.NOT_FOUND));
        if (!activeSession.getUserId().equals(userId)) {
            throw new BusinessException("TRIP_OWNER_MISMATCH", "This trip does not belong to you", HttpStatus.FORBIDDEN);
        }
        if (vehicle.getState() != VehicleState.IN_USE) {
            throw new BusinessException(
                    "INVALID_STATE",
                    "Vehicle is not currently rented",
                    HttpStatus.BAD_REQUEST
            );
        }
        // BR-M3-03: Hub đích còn chỗ
        if (!hubServiceClient.hasAvailableSpace(hubId)) {
            throw new BusinessException(
                    "HUB_FULL",
                    "Destination hub is full. Choose another hub.",
                    HttpStatus.CONFLICT
            );
        }

        vehicle.updateBattery(batteryLevel);
        vehicle.setCurrentHubId(hubId);
        vehicle.changeState(batteryLevel < lowBatteryThreshold
                ? VehicleState.LOW_BATTERY
                : VehicleState.AVAILABLE);


        activeSession.complete();

        publisher.publish(new VehicleReturnedEvent(vehicleId, hubId, batteryLevel));
        if (batteryLevel < lowBatteryThreshold) {
            publisher.publish(new VehicleLowBatteryEvent(vehicleId, batteryLevel));
        }
    }

    private RentalVehicle findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new BusinessException("VEHICLE_NOT_FOUND", "Vehicle not found: " + id, HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public CurrentTripResponse getCurrentTrip(Long userId) {
        RentalSession session = sessionRepository
                .findByUserIdAndStatus(userId, SessionStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        "NO_ACTIVE_TRIP",
                        "You have no active trip",
                        HttpStatus.NOT_FOUND
                ));

        RentalVehicle vehicle = findOrThrow(session.getVehicleId());
        return CurrentTripResponse.from(session, vehicle);
    }
}
