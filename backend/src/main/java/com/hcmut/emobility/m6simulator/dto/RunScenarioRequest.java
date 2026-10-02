package com.hcmut.emobility.m6simulator.dto;

import jakarta.validation.constraints.NotNull;

public record RunScenarioRequest(@NotNull Long seed) {
}
