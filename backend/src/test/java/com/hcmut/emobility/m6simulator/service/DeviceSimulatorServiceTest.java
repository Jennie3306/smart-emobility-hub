package com.hcmut.emobility.m6simulator.service;

import com.hcmut.emobility.m6simulator.domain.DeviceReading;
import com.hcmut.emobility.m6simulator.domain.ProcessingResult;
import com.hcmut.emobility.m6simulator.domain.ResourceType;
import com.hcmut.emobility.m6simulator.domain.SimulationRunState;
import com.hcmut.emobility.m6simulator.domain.SimulatorScenario;
import com.hcmut.emobility.m6simulator.dto.SimulationRunView;
import com.hcmut.emobility.m6simulator.dto.SimulatorStatusView;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceSimulatorServiceTest {

    private static final SimulatorScenario SCENARIO = SimulatorScenario.NORMAL_TICK;

    @Test
    void normalTickRunsThreeReadingsThroughTheDevicePipeline() {
        DeviceEventService deviceEvents = mock(DeviceEventService.class);
        Environment environment = mock(Environment.class);
        when(deviceEvents.processReading(any(DeviceReading.class), any())).thenAnswer(invocation ->
                ProcessingResult.processed(invocation.<DeviceReading>getArgument(0).eventId(), List.of()));
        DeviceSimulatorService service = new DeviceSimulatorService(
                new SyntheticReadingFactory(), deviceEvents, environment);

        SimulationRunView run = service.runScenario(SCENARIO, 42);
        SimulatorStatusView status = service.status();

        assertEquals(3, run.readingCount());
        assertEquals(3, run.processedCount());
        assertEquals(0, run.rejectedCount());
        assertNotNull(run.completedAt());
        assertEquals(SimulationRunState.SUCCEEDED, status.state());
        assertEquals(run.runId(), status.latestRun().runId());
        verify(deviceEvents, times(3)).processReading(any(DeviceReading.class), any());
    }

    @Test
    void sameFailureScenarioHasFreshRunAndInboundIds() {
        DeviceEventService deviceEvents = mock(DeviceEventService.class);
        Environment environment = mock(Environment.class);
        when(deviceEvents.processReading(any(DeviceReading.class), any())).thenAnswer(invocation ->
                ProcessingResult.processed(invocation.<DeviceReading>getArgument(0).eventId(), List.of()));
        DeviceSimulatorService service = new DeviceSimulatorService(
                new SyntheticReadingFactory(), deviceEvents, environment);

        SimulationRunView first = service.runScenario(SimulatorScenario.VEHICLE_FAILURE, 42);
        SimulationRunView second = service.runScenario(SimulatorScenario.VEHICLE_FAILURE, 42);

        assertEquals(1, first.readingCount());
        assertEquals(1, second.readingCount());
        assertNotNull(first.runId());
        assertNotEquals(first.runId(), second.runId());
        verify(deviceEvents, times(2)).processReading(any(DeviceReading.class), any());
    }

    @Test
    void factoryFailureRecordsFailedRunAndReleasesGuardForNextRun() {
        SyntheticReadingFactory factory = mock(SyntheticReadingFactory.class);
        DeviceEventService deviceEvents = mock(DeviceEventService.class);
        Environment environment = mock(Environment.class);
        when(factory.createReadings(SCENARIO, 42L))
                .thenThrow(new IllegalStateException("factory failed"))
                .thenReturn(List.of(reading(1), reading(2), reading(3)));
        when(deviceEvents.processReading(any(DeviceReading.class), any())).thenAnswer(invocation ->
                ProcessingResult.processed(invocation.<DeviceReading>getArgument(0).eventId(), List.of()));
        DeviceSimulatorService service = new DeviceSimulatorService(factory, deviceEvents, environment);

        assertThrows(IllegalStateException.class, () -> service.runScenario(SCENARIO, 42));

        SimulationRunView failed = service.status().latestRun();
        assertEquals(SimulationRunState.FAILED, service.status().state());
        assertNotNull(failed.completedAt());
        assertEquals(0, failed.readingCount());
        assertEquals(0, failed.processedCount());
        assertEquals("factory failed", failed.error());

        SimulationRunView recovered = service.runScenario(SCENARIO, 42);
        assertEquals(3, recovered.processedCount());
        assertEquals(SimulationRunState.SUCCEEDED, service.status().state());
    }

    @Test
    void concurrentRunIsRejectedAndGuardIsReleasedAfterFirstRunCompletes() throws Exception {
        SyntheticReadingFactory factory = mock(SyntheticReadingFactory.class);
        DeviceEventService deviceEvents = mock(DeviceEventService.class);
        Environment environment = mock(Environment.class);
        CountDownLatch batchRequested = new CountDownLatch(1);
        CountDownLatch releaseBatch = new CountDownLatch(1);
        when(factory.createReadings(any(SimulatorScenario.class), anyLong())).thenAnswer(invocation -> {
            batchRequested.countDown();
            if (!releaseBatch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test timed out waiting to release the batch");
            }
            return List.of(reading(1));
        });
        when(deviceEvents.processReading(any(DeviceReading.class), any())).thenAnswer(invocation ->
                ProcessingResult.processed(invocation.<DeviceReading>getArgument(0).eventId(), List.of()));
        DeviceSimulatorService service = new DeviceSimulatorService(factory, deviceEvents, environment);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<SimulationRunView> activeRun = null;
        try {
            activeRun = executor.submit(() -> service.runScenario(SCENARIO, 42));
            assertTrue(batchRequested.await(5, TimeUnit.SECONDS), "first run should enter the factory");
            assertThrows(SimulationAlreadyRunningException.class,
                    () -> service.runScenario(SCENARIO, 43));

            releaseBatch.countDown();
            assertNotNull(activeRun.get(5, TimeUnit.SECONDS));
            assertEquals(SimulationRunState.SUCCEEDED, service.status().state());
            assertEquals(1, service.runScenario(SCENARIO, 44).readingCount());
        } finally {
            releaseBatch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void processingFailureKeepsCountsFromEarlierReadings() {
        SyntheticReadingFactory factory = mock(SyntheticReadingFactory.class);
        DeviceEventService deviceEvents = mock(DeviceEventService.class);
        Environment environment = mock(Environment.class);
        when(factory.createReadings(SCENARIO, 42L))
                .thenReturn(List.of(reading(1), reading(2), reading(3)));
        when(deviceEvents.processReading(any(DeviceReading.class), any()))
                .thenReturn(ProcessingResult.processed(reading(1).eventId(), List.of()))
                .thenThrow(new IllegalStateException("publisher failed"));
        DeviceSimulatorService service = new DeviceSimulatorService(factory, deviceEvents, environment);

        assertThrows(IllegalStateException.class, () -> service.runScenario(SCENARIO, 42));

        SimulationRunView failed = service.status().latestRun();
        assertEquals(SimulationRunState.FAILED, service.status().state());
        assertEquals(3, failed.readingCount());
        assertEquals(1, failed.processedCount());
        assertEquals("publisher failed", failed.error());
    }

    private static DeviceReading reading(long suffix) {
        return new DeviceReading(new UUID(0, suffix), 1L, ResourceType.VEHICLE, 1L,
                "AVAILABLE", 80, null, OffsetDateTime.parse("2026-10-02T10:00:00+07:00"));
    }
}
