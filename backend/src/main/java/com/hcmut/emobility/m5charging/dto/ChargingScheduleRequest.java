package com.hcmut.emobility.m5charging.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record ChargingScheduleRequest(
        @NotNull Long hubId,
        @NotNull Long vehicleId,
        @NotNull @Min(0) @Max(100) Integer batteryLevel,
        @NotNull Long parkingSpaceId,
        @NotNull OffsetDateTime startAt,
        @NotNull @Min(30) @Max(90) Integer durationMinutes
) { }
