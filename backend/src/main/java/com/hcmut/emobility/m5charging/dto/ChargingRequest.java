package com.hcmut.emobility.m5charging.dto;

import com.hcmut.emobility.m5charging.domain.VehicleKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record ChargingRequest(
        @NotNull Long hubId,
        @NotNull VehicleKind vehicleKind,
        @NotNull Long vehicleId,
        @NotNull @Min(0) @Max(100) Integer batteryLevel,
        Long parkingSpaceId,
        OffsetDateTime expectedUseAt
) { }
