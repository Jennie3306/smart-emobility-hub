package com.hcmut.emobility.m5charging.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record ScheduleMoveRequest(
        @NotNull OffsetDateTime startAt,
        @NotNull @Min(30) @Max(90) Integer durationMinutes,
        @NotBlank @Size(max = 500) String reason
) { }
