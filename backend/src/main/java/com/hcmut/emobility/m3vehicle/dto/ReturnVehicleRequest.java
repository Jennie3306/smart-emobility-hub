package com.hcmut.emobility.m3vehicle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReturnVehicleRequest(
        @NotNull Long hubId,
        @Min(0) @Max(100) int batteryLevel
) { }
