package com.hcmut.emobility.m5charging.dto;

import com.hcmut.emobility.m5charging.domain.PriorityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PriorityChangeRequest(
        @NotNull PriorityLevel priority,
        @NotBlank String reason
) { }
