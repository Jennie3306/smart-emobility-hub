package com.hcmut.emobility.m1monitoring;

import com.hcmut.emobility.m1monitoring.domain.ResourceState;
import com.hcmut.emobility.m1monitoring.events.MonitoringEventHandler;
import com.hcmut.emobility.m1monitoring.repository.ResourceStateRepository;
import com.hcmut.emobility.m1monitoring.service.DashboardPublisher;
import com.hcmut.emobility.m1monitoring.service.MonitoringService;
import com.hcmut.emobility.shared.events.DeviceReadingReportedEvent;
import com.hcmut.emobility.shared.events.ProcessedEventStore;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Appendix A v2 (12/11): M1 mirrors only the state published by the owner (M4 parking space,
 * M5 charging point). Raw M6 readings (DeviceReadingReportedEvent) go to the owners, never to M1.
 */
class OwnerProjectionTest {

    private final ResourceStateRepository resources = mock(ResourceStateRepository.class);
    private final MonitoringEventHandler handler = new MonitoringEventHandler(mock(MonitoringService.class),
            resources, mock(DashboardPublisher.class), new ProcessedEventStore());

    @Test
    @DisplayName("M1 has no listener for raw M6 device readings, so a sensor cannot overwrite the owner's state")
    void m1DoesNotListenToDeviceReadings() {
        boolean listens = Arrays.stream(MonitoringEventHandler.class.getDeclaredMethods())
                .flatMap(m -> Arrays.stream(m.getParameterTypes()))
                .anyMatch(DeviceReadingReportedEvent.class::equals);
        assertFalse(listens);
    }

    @Test
    @DisplayName("State published by M4 / M5 is mirrored immediately")
    void ownerEventsAreMirrored() {
        ResourceState space = new ResourceState("PARKING_SPACE", 1L, 1L, "FREE");
        ResourceState point = new ResourceState("CHARGING_POINT", 1L, 1L, "FREE");
        when(resources.findByResourceTypeAndResourceId("PARKING_SPACE", 1L)).thenReturn(Optional.of(space));
        when(resources.findByResourceTypeAndResourceId("CHARGING_POINT", 1L)).thenReturn(Optional.of(point));

        handler.onResourceStateUpdated(new ResourceStateUpdatedEvent(1L, "PARKING_SPACE", 1L, "RESERVED"));
        handler.onResourceStateUpdated(new ResourceStateUpdatedEvent(1L, "CHARGING_POINT", 1L, "FAULTY"));

        assertEquals("RESERVED", space.getState());
        assertEquals("FAULTY", point.getState());
    }
}
