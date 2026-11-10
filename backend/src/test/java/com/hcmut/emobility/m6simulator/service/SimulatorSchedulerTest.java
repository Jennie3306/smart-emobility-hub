package com.hcmut.emobility.m6simulator.service;

import com.hcmut.emobility.m6simulator.domain.SimulatorScenario;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class SimulatorSchedulerTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(SchedulerTestConfig.class);

    @Test
    void schedulerBeanDoesNotExistWhenDisabled() {
        contexts.withPropertyValues("app.simulator.scheduler-enabled=false").run(context ->
                assertTrue(context.getBeansOfType(SimulatorScheduler.class).isEmpty()));
    }

    @Test
    void enabledSchedulerUsesTheSharedNormalTickPipeline() {
        contexts.withPropertyValues(
                "app.simulator.scheduler-enabled=true",
                "app.simulator.interval-ms=3600000",
                "app.simulator.seed=17").run(context -> {
            assertFalse(context.getBeansOfType(SimulatorScheduler.class).isEmpty());
            verify(context.getBean(DeviceSimulatorService.class), timeout(5_000))
                    .runScenario(SimulatorScenario.NORMAL_TICK, 17L);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @Import(SimulatorScheduler.class)
    static class SchedulerTestConfig {
        @Bean
        DeviceSimulatorService deviceSimulatorService() {
            return mock(DeviceSimulatorService.class);
        }
    }
}
