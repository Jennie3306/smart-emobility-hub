package com.hcmut.emobility.m8simulation;
import com.hcmut.emobility.m1monitoring.domain.ResourceState;
import com.hcmut.emobility.m1monitoring.events.MonitoringEventHandler;
import com.hcmut.emobility.m1monitoring.repository.ResourceStateRepository;
import com.hcmut.emobility.m1monitoring.service.*;
import com.hcmut.emobility.shared.events.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MonitoringVocabularyTest {
    @Test void availableChargerBecomesFreeInM1WithoutChangingM6Contract() {
        var resources=mock(ResourceStateRepository.class);
        var state=new ResourceState("CHARGING_POINT",1L,1L,"IN_USE");
        when(resources.findByResourceTypeAndResourceId("CHARGING_POINT",1L)).thenReturn(Optional.of(state));
        var handler=new MonitoringEventHandler(mock(MonitoringService.class),resources,mock(DashboardPublisher.class),new ProcessedEventStore());
        var event=new ResourceStateUpdatedEvent(1L,"CHARGING_POINT",1L,"AVAILABLE");
        handler.onResourceStateUpdated(event);handler.onResourceStateUpdated(event);
        assertEquals("FREE",state.getState());assertEquals("AVAILABLE",event.getNewState());
        verify(resources,times(1)).findByResourceTypeAndResourceId("CHARGING_POINT",1L);
    }
}
