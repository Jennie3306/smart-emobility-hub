package com.hcmut.emobility.m6simulator.dto;

import com.hcmut.emobility.m6simulator.domain.SimulationRunState;

public record SimulatorStatusView(
        boolean schedulerEnabled,
        Long intervalMs,
        SimulationRunState state,
        SimulationRunView latestRun) {
}
