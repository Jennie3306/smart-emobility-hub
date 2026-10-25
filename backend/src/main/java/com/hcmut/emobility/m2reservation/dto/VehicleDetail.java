package com.hcmut.emobility.m2reservation.dto;

import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;

public record VehicleDetail(Long id, String code, String type, int batteryLevel, Long hubId, String state,
                            int holdMinutes) {
    public static VehicleDetail from(RentalVehicle v, int holdMinutes) {
        return new VehicleDetail(v.getId(), v.getCode(), v.getType().name(), v.getBatteryLevel(),
                v.getCurrentHubId(), v.getState().name(), holdMinutes);
    }
}
