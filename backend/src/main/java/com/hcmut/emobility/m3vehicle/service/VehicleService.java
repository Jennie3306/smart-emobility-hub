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
                    "Xe khong o trang thai RESERVED", HttpStatus.BAD_REQUEST);
        }
        if (vehicle.getBatteryLevel() < lowBatteryThreshold) {
            throw new BusinessException("BATTERY_TOO_LOW",
                    "Pin khong du dieu kien cho thue (< " + lowBatteryThreshold + "%)",
                    HttpStatus.BAD_REQUEST);
        }
        if (vehicle.getReservedByUserId() == null
                || !vehicle.getReservedByUserId().equals(userId)) {
            throw new BusinessException("RESERVATION_MISMATCH",
                    "Xe nay khong thuoc ve ban", HttpStatus.FORBIDDEN);
        }

        vehicle.changeState(VehicleState.IN_USE);
        vehicle.clearReservation();

        RentalSession session = RentalSession.start(vehicleId, userId);
        sessionRepository.save(session);
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
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "Khong tim thay chuyen di", HttpStatus.NOT_FOUND));
        if (!activeSession.getUserId().equals(userId)) {
            throw new BusinessException("TRIP_OWNER_MISMATCH", "Chuyen di khong thuoc ve ban", HttpStatus.FORBIDDEN);
        }
        if (vehicle.getState() != VehicleState.IN_USE) {
            throw new BusinessException(
                    "INVALID_STATE",
                    "Xe chua duoc thue",
                    HttpStatus.BAD_REQUEST
            );
        }
        // BR-M3-03: Hub đích còn chỗ
        if (!hubServiceClient.hasAvailableSpace(hubId)) {
            throw new BusinessException(
                    "HUB_FULL",
                    "Hub dich da het cho do, vui long di chuyen den Hub lan can",
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
                new BusinessException("VEHICLE_NOT_FOUND", "Khong tim thay xe " + id, HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public CurrentTripResponse getCurrentTrip(Long userId) {
        RentalSession session = sessionRepository
                .findByUserIdAndStatus(userId, SessionStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        "NO_ACTIVE_TRIP",
                        "Ban khong co chuyen di dang dien ra",
                        HttpStatus.NOT_FOUND
                ));

        RentalVehicle vehicle = findOrThrow(session.getVehicleId());
        return CurrentTripResponse.from(session, vehicle);
    }
}
