package com.hcmut.emobility.m3vehicle.dto;

import com.hcmut.emobility.m3vehicle.domain.RentalSession;
import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;

import java.time.LocalDateTime;

public record CurrentTripResponse(
        Long sessionId,
        Long vehicleId,
        String vehicleCode,
        String vehicleState,
        int batteryLevel,
        Long hubId,
        LocalDateTime startTime
) {
    public static CurrentTripResponse from(RentalSession session, RentalVehicle vehicle) {
        return new CurrentTripResponse(
                session.getId(),
                vehicle.getId(),
                vehicle.getCode(),
                vehicle.getState().name(),
                vehicle.getBatteryLevel(),
                vehicle.getCurrentHubId(),
                session.getStartTime()
        );
    }
}