package com.hcmut.emobility.m3vehicle.service;

import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;
import com.hcmut.emobility.m3vehicle.domain.VehicleState;
import com.hcmut.emobility.m3vehicle.repository.RentalVehicleRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.VehicleLowBatteryEvent;
import com.hcmut.emobility.shared.events.VehicleReturnedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

    private final RentalVehicleRepository repository;
    private final EventPublisher publisher;

    @Value("${app.charging.low-battery-threshold}")
    private int lowBatteryThreshold;

    public VehicleService(RentalVehicleRepository repository, EventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    /**
     * Nhan xe da dat: RESERVED -> IN_USE.
     * Tien dieu kien: xe ton tai va dang o trang thai RESERVED.
     * Hau dieu kien: xe chuyen sang IN_USE.
     * Ngoai le: VEHICLE_NOT_FOUND, INVALID_STATE_TRANSITION.
     */
    @Transactional
    public void pickUp(Long vehicleId) {
        RentalVehicle vehicle = findOrThrow(vehicleId);
        vehicle.changeState(VehicleState.IN_USE);
    }

    /**
     * Tra xe tai mot Hub: IN_USE -> AVAILABLE (hoac LOW_BATTERY neu pin duoi nguong).
     * Phat VehicleReturnedEvent de M5 xet dua vao hang doi sac va M2 dong dat cho.
     */
    @Transactional
    public void returnVehicle(Long vehicleId, Long hubId, int batteryLevel) {
        RentalVehicle vehicle = findOrThrow(vehicleId);
        vehicle.updateBattery(batteryLevel);
        vehicle.setCurrentHubId(hubId);
        vehicle.changeState(batteryLevel < lowBatteryThreshold
                ? VehicleState.LOW_BATTERY : VehicleState.AVAILABLE);

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
