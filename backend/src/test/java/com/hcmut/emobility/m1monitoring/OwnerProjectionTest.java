package com.hcmut.emobility.m1monitoring;

import com.hcmut.emobility.m1monitoring.domain.ResourceState;
import com.hcmut.emobility.m1monitoring.events.MonitoringEventHandler;
import com.hcmut.emobility.m1monitoring.repository.ResourceStateRepository;
import com.hcmut.emobility.m1monitoring.service.DashboardPublisher;
import com.hcmut.emobility.m1monitoring.service.MonitoringService;
import com.hcmut.emobility.shared.events.ProcessedEventStore;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chot 12/11: M1 chi phan chieu trang thai do chu so huu phat (M4 cho do, M5 tru sac);
 * so doc thiet bi cua M6 cho hai loai tai nguyen nay bi bo qua.
 */
class OwnerProjectionTest {

    private final ResourceStateRepository resources = mock(ResourceStateRepository.class);
    private final MonitoringEventHandler handler = new MonitoringEventHandler(mock(MonitoringService.class),
            resources, mock(DashboardPublisher.class), new ProcessedEventStore());

    @Test
    @DisplayName("So doc M6 'FREE' cho cho do dang co xe khong ghi de trang thai M4 da phat")
    void deviceReadingDoesNotOverrideParkingOwner() {
        ResourceState space = new ResourceState("PARKING_SPACE", 1L, 1L, "OCCUPIED");
        when(resources.findByResourceTypeAndResourceId("PARKING_SPACE", 1L)).thenReturn(Optional.of(space));

        handler.onResourceStateUpdated(new ResourceStateUpdatedEvent(1L, "PARKING_SPACE", 1L, "FREE",
                ResourceStateUpdatedEvent.SOURCE_DEVICE));

        assertEquals("OCCUPIED", space.getState());
        verify(resources, never()).findByResourceTypeAndResourceId("PARKING_SPACE", 1L);
    }

    @Test
    @DisplayName("Trang thai do M4 / M5 phat thi M1 cap nhat ngay")
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
