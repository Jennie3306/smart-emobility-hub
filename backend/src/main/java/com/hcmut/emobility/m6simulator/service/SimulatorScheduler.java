package com.hcmut.emobility.m6simulator.service;

import com.hcmut.emobility.m6simulator.domain.SimulatorScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.simulator", name = "scheduler-enabled", havingValue = "true")
public class SimulatorScheduler {

    private static final Logger log = LoggerFactory.getLogger(SimulatorScheduler.class);

    private final DeviceSimulatorService simulatorService;
    private final long seed;

    public SimulatorScheduler(DeviceSimulatorService simulatorService,
                              org.springframework.core.env.Environment environment) {
        this.simulatorService = simulatorService;
        this.seed = environment.getRequiredProperty("app.simulator.seed", Long.class);
        long intervalMs = environment.getRequiredProperty("app.simulator.interval-ms", Long.class);
        if (intervalMs <= 0) {
            throw new IllegalStateException("app.simulator.interval-ms must be positive when scheduler is enabled");
        }
    }

    @Scheduled(fixedDelayString = "${app.simulator.interval-ms}")
    public void scheduledTick() {
        try {
            simulatorService.runScenario(SimulatorScenario.NORMAL_TICK, seed);
        } catch (SimulationAlreadyRunningException ex) {
            log.info("M6_SCHEDULED_TICK_SKIPPED reason=SIMULATOR_ALREADY_RUNNING");
        } catch (RuntimeException ex) {
            log.error("M6_SCHEDULED_TICK_FAILED error={}", ex.getMessage(), ex);
        }
    }
}
