package com.hcmut.emobility.m3vehicle;
import com.hcmut.emobility.m3vehicle.domain.*;
import com.hcmut.emobility.m3vehicle.repository.*;
import com.hcmut.emobility.m3vehicle.service.*;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.common.BusinessException;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
class TripOwnershipTest {
    @Test void anotherUserCannotReturnAnActiveTrip() {
        var vehicles=mock(RentalVehicleRepository.class); var sessions=mock(RentalSessionRepository.class);
        var vehicle=mock(RentalVehicle.class); var publisher=mock(EventPublisher.class); var hubs=mock(HubServiceClient.class);
        when(vehicles.findById(1L)).thenReturn(Optional.of(vehicle));
        when(sessions.findByVehicleIdAndStatus(1L,SessionStatus.ACTIVE)).thenReturn(Optional.of(RentalSession.start(1L,2L)));
        var service=new VehicleService(vehicles,sessions,publisher,hubs);
        BusinessException e=assertThrows(BusinessException.class,()->service.returnVehicle(1L,1L,80,3L));
        assertEquals("TRIP_OWNER_MISMATCH",e.getCode()); verifyNoInteractions(hubs,publisher); verify(vehicle,never()).updateBattery(anyInt());
    }
}
