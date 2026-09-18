package com.hcmut.emobility.m3vehicle.dto;

import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;

public record VehicleResponse(Long id, String code, String type, int batteryLevel, String state, Long hubId) {
    public static VehicleResponse from(RentalVehicle v) {
        return new VehicleResponse(v.getId(), v.getCode(), v.getType().name(),
                v.getBatteryLevel(), v.getState().name(), v.getCurrentHubId());
    }
}
