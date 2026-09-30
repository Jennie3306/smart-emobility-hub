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

@Service
public class VehicleService {

    private final RentalVehicleRepository repository;
    private final EventPublisher publisher;
    private final RentalSessionRepository sessionRepository;

    @Value("${app.charging.low-battery-threshold}")
    private int lowBatteryThreshold;

    public VehicleService(RentalVehicleRepository repository, RentalSessionRepository sessionRepository, EventPublisher publisher) {
        this.repository = repository;
        this.sessionRepository = sessionRepository;
        this.publisher = publisher;
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
    public void returnVehicle(Long vehicleId, Long hubId, int batteryLevel) {
        RentalVehicle vehicle = findOrThrow(vehicleId);
        if (vehicle.getState() != VehicleState.IN_USE) {
            throw new BusinessException(
                    "INVALID_STATE",
                    "Xe chua duoc thue",
                    HttpStatus.BAD_REQUEST
            );
        }

        /*
        // BR-M3-03: Hub đích còn chỗ
        if (!hubServiceClient.hasAvailableSpace(hubId)) {
            throw new BusinessException(
                    "HUB_FULL",
                    "Hub dich da het cho do, vui long di chuyen den Hub lan can",
                    HttpStatus.CONFLICT
            );
        }
        chưa làm logic kiểm tra còn chỗ trống ở HUB không
        */

        vehicle.updateBattery(batteryLevel);
        vehicle.setCurrentHubId(hubId);
        vehicle.changeState(batteryLevel < lowBatteryThreshold
                ? VehicleState.LOW_BATTERY
                : VehicleState.AVAILABLE);


        RentalSession activeSession = sessionRepository
                .findByVehicleIdAndStatus(vehicleId, SessionStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        "SESSION_NOT_FOUND",
                        "Khong tim thay chuyen di dang dien ra",
                        HttpStatus.NOT_FOUND
                ));
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
}
