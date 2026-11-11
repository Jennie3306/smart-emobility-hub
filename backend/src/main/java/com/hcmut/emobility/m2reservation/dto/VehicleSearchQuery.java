package com.hcmut.emobility.m2reservation.dto;

import com.hcmut.emobility.m3vehicle.domain.VehicleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Vehicle search parameters (FR-M2-01). Wrong type or outside 0..100 -> 400 VALIDATION_FAILED. */
public record VehicleSearchQuery(
        Long hubId,
        VehicleType type,
        @Min(value = 0, message = "Minimum battery must be between 0 and 100")
        @Max(value = 100, message = "Minimum battery must be between 0 and 100") Integer minBattery
) {
    public int minBatteryOrZero() {
        return minBattery == null ? 0 : minBattery;
    }
}
