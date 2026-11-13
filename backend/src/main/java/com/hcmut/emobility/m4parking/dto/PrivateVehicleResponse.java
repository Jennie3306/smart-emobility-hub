package com.hcmut.emobility.m4parking.dto;

import com.hcmut.emobility.m4parking.domain.PrivateVehicle;

import java.time.OffsetDateTime;

public record PrivateVehicleResponse(Long id, String licensePlate, PrivateVehicle.VehicleType vehicleType,
                                     String model, Integer batteryLevel, PrivateVehicle.Status status,
                                     OffsetDateTime createdAt) {

    public static PrivateVehicleResponse from(PrivateVehicle v) {
        return new PrivateVehicleResponse(v.getId(), v.getLicensePlate(), v.getVehicleType(), v.getModel(),
                v.getBatteryLevel(), v.getStatus(), v.getCreatedAt());
    }
}
