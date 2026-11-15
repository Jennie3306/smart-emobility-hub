package com.hcmut.emobility.m3vehicle;

import com.hcmut.emobility.m3vehicle.domain.RentalSession;
import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;
import com.hcmut.emobility.m3vehicle.domain.SessionStatus;
import com.hcmut.emobility.m3vehicle.domain.VehicleState;
import com.hcmut.emobility.m3vehicle.events.ReservationVehicleHandler;
import com.hcmut.emobility.m3vehicle.events.VehicleEventHandler;
import com.hcmut.emobility.m3vehicle.repository.RentalSessionRepository;
import com.hcmut.emobility.m3vehicle.repository.RentalVehicleRepository;
import com.hcmut.emobility.m3vehicle.service.VehicleService;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.ProcessedEventStore;
import com.hcmut.emobility.shared.events.ReservationConfirmedEvent;
import com.hcmut.emobility.shared.events.VehicleFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VehicleEventHandlerTest {

    private static final long VEHICLE = 1L, STUDENT = 7L, HUB = 2L;
    private static final int THRESHOLD = 20;

    private RentalVehicleRepository vehicles;
    private RentalSessionRepository sessions;

    private VehicleEventHandler vehicleHandler;
    private ReservationVehicleHandler reservationHandler;

    @BeforeEach
    void setUp() {
        vehicles = mock(RentalVehicleRepository.class);
        sessions = mock(RentalSessionRepository.class);
        ProcessedEventStore processed = mock(ProcessedEventStore.class);
        EventPublisher publisher = mock(EventPublisher.class);
        VehicleService service = mock(VehicleService.class);

        when(processed.markIfNew(eq("M3"), any())).thenReturn(true);

        vehicleHandler = new VehicleEventHandler(vehicles, sessions, processed, publisher, THRESHOLD);
        reservationHandler = new ReservationVehicleHandler(vehicles, sessions, service, THRESHOLD);
    }

    private RentalVehicle vehicle(VehicleState state, int battery, Long hubId) {
        try {
            var ctor = RentalVehicle.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            RentalVehicle v = ctor.newInstance();
            ReflectionTestUtils.setField(v, "id", VEHICLE);
            ReflectionTestUtils.setField(v, "code", "EB-T01");
            ReflectionTestUtils.setField(v, "state", state);
            ReflectionTestUtils.setField(v, "batteryLevel", battery);
            ReflectionTestUtils.setField(v, "reservedByUserId", null);
            ReflectionTestUtils.setField(v, "currentHubId", hubId);
            when(vehicles.findById(VEHICLE)).thenReturn(Optional.of(v));
            return v;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("TC-M3-11: VehicleFailed on IN_USE vehicle moves to MAINTENANCE and closes ACTIVE session")
    void vehicleFailedClosesActiveSession() {
        RentalVehicle v = vehicle(VehicleState.IN_USE, 75, null);
        RentalSession activeTrip = RentalSession.start(VEHICLE, STUDENT);
        when(sessions.findByVehicleIdAndStatus(VEHICLE, SessionStatus.ACTIVE))
                .thenReturn(Optional.of(activeTrip));

        vehicleHandler.onVehicleFailed(new VehicleFailedEvent(VEHICLE, null, "MECHANICAL"));

        assertEquals(VehicleState.MAINTENANCE, v.getState());
        assertEquals(SessionStatus.COMPLETED, activeTrip.getStatus());
        assertNotNull(activeTrip.getEndTime());
    }

    @ParameterizedTest(name = "battery {0}% -> {1}")
    @CsvSource({"19, BATTERY_TOO_LOW", "20, OK"})
    @DisplayName("TC-M3-12 (BVA): reservation confirmation checks 20% battery threshold")
    void reservationConfirmedChecksBatteryThreshold(int battery, String expected) {
        RentalVehicle v = vehicle(VehicleState.AVAILABLE, battery, HUB);
        ReservationConfirmedEvent event = new ReservationConfirmedEvent(100L, STUDENT, "VEHICLE", VEHICLE, HUB);

        if ("OK".equals(expected)) {
            reservationHandler.confirmed(event);
            assertEquals(VehicleState.RESERVED, v.getState());
            assertEquals(STUDENT, v.getReservedByUserId());
        } else {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reservationHandler.confirmed(event));
            assertEquals(expected, ex.getCode());
            assertEquals(VehicleState.AVAILABLE, v.getState());
            assertNull(v.getReservedByUserId());
        }
    }
}