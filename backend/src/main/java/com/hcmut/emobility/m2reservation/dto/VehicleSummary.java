package com.hcmut.emobility.m2reservation.dto;

import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;

public record VehicleSummary(Long id, String code, String type, int batteryLevel, Long hubId) {
    public static VehicleSummary from(RentalVehicle v) {
        return new VehicleSummary(v.getId(), v.getCode(), v.getType().name(), v.getBatteryLevel(), v.getCurrentHubId());
    }
}
