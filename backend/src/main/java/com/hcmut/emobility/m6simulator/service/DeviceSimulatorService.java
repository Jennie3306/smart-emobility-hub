package com.hcmut.emobility.m6simulator.service;

import com.hcmut.emobility.m6simulator.domain.DeviceReading;
import com.hcmut.emobility.m6simulator.domain.ProcessingResult;
import com.hcmut.emobility.m6simulator.domain.SimulationRunState;
import com.hcmut.emobility.m6simulator.domain.SimulatorScenario;
import com.hcmut.emobility.m6simulator.dto.SimulationRunView;
import com.hcmut.emobility.m6simulator.dto.SimulatorStatusView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DeviceSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(DeviceSimulatorService.class);

    private final SyntheticReadingFactory readingFactory;
    private final DeviceEventService deviceEventService;
    private final Environment environment;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile SimulationRunView latestRun;

    public DeviceSimulatorService(SyntheticReadingFactory readingFactory,
                                  DeviceEventService deviceEventService,
                                  Environment environment) {
        this.readingFactory = readingFactory;
        this.deviceEventService = deviceEventService;
        this.environment = environment;
    }

    public SimulationRunView runScenario(SimulatorScenario scenario, long seed) {
        Objects.requireNonNull(scenario, "scenario");
        if (!running.compareAndSet(false, true)) {
            throw new SimulationAlreadyRunningException();
        }

        UUID runId = UUID.randomUUID();
        OffsetDateTime startedAt = OffsetDateTime.now();
        List<SimulationRunView.OutboundEventView> outbound = new ArrayList<>();
        int processed = 0;
        int duplicates = 0;
        int rejected = 0;
        int readingCount = 0;
        latestRun = new SimulationRunView(runId, scenario, seed, startedAt, null,
                readingCount, processed, duplicates, rejected, outbound, null);
        log.info("M6_SIMULATION_STARTED runId={} scenario={} seed={}", runId, scenario, seed);

        try {
            List<DeviceReading> readings = readingFactory.createReadings(scenario, seed);
            readingCount = readings.size();
            latestRun = new SimulationRunView(runId, scenario, seed, startedAt, null,
                    readingCount, processed, duplicates, rejected, outbound, null);
            log.info("M6_SIMULATION_BATCH_CREATED runId={} scenario={} readingCount={}",
                    runId, scenario, readingCount);
            for (DeviceReading reading : readings) {
                ProcessingResult result = deviceEventService.processReading(reading, runId);
                switch (result.status()) {
                    case PROCESSED -> processed++;
                    case DUPLICATE -> duplicates++;
                    case REJECTED -> rejected++;
                }
                for (ProcessingResult.OutboundEvent event : result.outboundEvents()) {
                    outbound.add(new SimulationRunView.OutboundEventView(event.eventId(), event.eventType(),
                            event.resourceType().name(), event.resourceId()));
                }
                latestRun = new SimulationRunView(runId, scenario, seed, startedAt, null,
                        readingCount, processed, duplicates, rejected, outbound, null);
            }
            latestRun = new SimulationRunView(runId, scenario, seed, startedAt, OffsetDateTime.now(),
                    readingCount, processed, duplicates, rejected, outbound, null);
            log.info("M6_SIMULATION_SUCCEEDED runId={} scenario={} processedCount={} duplicateCount={} rejectedCount={} outboundCount={}",
                    runId, scenario, processed, duplicates, rejected, outbound.size());
            return latestRun;
        } catch (RuntimeException ex) {
            String error = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName() : ex.getMessage();
            latestRun = new SimulationRunView(runId, scenario, seed, startedAt, OffsetDateTime.now(),
                    readingCount, processed, duplicates, rejected, outbound, error);
            log.error("M6_SIMULATION_FAILED runId={} scenario={} readingCount={} processedCount={} duplicateCount={} rejectedCount={} outboundCount={} error={}",
                    runId, scenario, readingCount, processed, duplicates, rejected, outbound.size(), error, ex);
            throw ex;
        } finally {
            running.set(false);
        }
    }

    public SimulatorStatusView status() {
        boolean schedulerEnabled = Boolean.TRUE.equals(
                environment.getProperty("app.simulator.scheduler-enabled", Boolean.class));
        Long intervalMs = environment.getProperty("app.simulator.interval-ms", Long.class);
        SimulationRunState state = running.get()
                ? SimulationRunState.RUNNING
                : latestRun == null ? SimulationRunState.IDLE
                : latestRun.error() == null ? SimulationRunState.SUCCEEDED : SimulationRunState.FAILED;
        return new SimulatorStatusView(schedulerEnabled, schedulerEnabled ? intervalMs : null, state, latestRun);
    }
}
