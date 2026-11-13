package com.hcmut.emobility.m3vehicle;

import com.hcmut.emobility.m3vehicle.domain.RentalSession;
import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;
import com.hcmut.emobility.m3vehicle.domain.SessionStatus;
import com.hcmut.emobility.m3vehicle.domain.VehicleState;
import com.hcmut.emobility.m3vehicle.dto.CurrentTripResponse;
import com.hcmut.emobility.m3vehicle.repository.RentalSessionRepository;
import com.hcmut.emobility.m3vehicle.repository.RentalVehicleRepository;
import com.hcmut.emobility.m3vehicle.service.VehicleService;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.contracts.ParkingDock;
import com.hcmut.emobility.shared.events.DomainEvent;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.VehicleLowBatteryEvent;
import com.hcmut.emobility.shared.events.VehiclePickedUpEvent;
import com.hcmut.emobility.shared.events.VehicleReturnedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Service-level tests of the rental lifecycle (UC-M3-01..03). Repositories, the M4 parking pool and the event
 * publisher are mocks; the vehicle and the trip are real domain objects so that state changes are observed.
 * Related: FR-M3-01..04, BR-M3-01..03, TC-M3-04..10.
 */
class VehicleServiceTest {

    private static final long VEHICLE = 1L, STUDENT = 7L, OTHER = 8L, HUB = 2L;
    private static final int THRESHOLD = 20;   // app.charging.low-battery-threshold

    private RentalVehicleRepository vehicles;
    private RentalSessionRepository sessions;
    private EventPublisher publisher;
    private ParkingDock dock;
    private VehicleService service;

    @BeforeEach
    void setUp() {
        vehicles = mock(RentalVehicleRepository.class);
        sessions = mock(RentalSessionRepository.class);
        publisher = mock(EventPublisher.class);
        dock = mock(ParkingDock.class);
        service = new VehicleService(vehicles, sessions, publisher, dock);
        ReflectionTestUtils.setField(service, "lowBatteryThreshold", THRESHOLD);
    }

    private RentalVehicle vehicle(VehicleState state, int battery, Long reservedBy, Long hubId) {
        try {
            var ctor = RentalVehicle.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            RentalVehicle v = ctor.newInstance();
            ReflectionTestUtils.setField(v, "id", VEHICLE);
            ReflectionTestUtils.setField(v, "code", "EB-T01");
            ReflectionTestUtils.setField(v, "state", state);
            ReflectionTestUtils.setField(v, "batteryLevel", battery);
            ReflectionTestUtils.setField(v, "reservedByUserId", reservedBy);
            ReflectionTestUtils.setField(v, "currentHubId", hubId);
            when(vehicles.findById(VEHICLE)).thenReturn(Optional.of(v));
            return v;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<DomainEvent> published() {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeast(0)).publish(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("TC-M3-04 (FR-M3-01): pick-up of an own RESERVED vehicle -> IN_USE, ACTIVE trip, space released")
    void pickUpStartsTheTrip() {
        RentalVehicle v = vehicle(VehicleState.RESERVED, 80, STUDENT, HUB);

        service.pickUp(VEHICLE, STUDENT);

        assertEquals(VehicleState.IN_USE, v.getState());
        assertNull(v.getCurrentHubId());
        assertNull(v.getReservedByUserId());
        verify(dock).undockRentalVehicle(VEHICLE);
        ArgumentCaptor<RentalSession> trip = ArgumentCaptor.forClass(RentalSession.class);
        verify(sessions).save(trip.capture());
        assertEquals(SessionStatus.ACTIVE, trip.getValue().getStatus());
        assertEquals(STUDENT, trip.getValue().getUserId());
        assertTrue(published().stream().anyMatch(e -> e instanceof VehiclePickedUpEvent));
    }

    @Test
    @DisplayName("TC-M3-05 (BR-M3-01): pick-up of a vehicle that is not RESERVED -> VEHICLE_NOT_AVAIL")
    void pickUpRequiresAReservation() {
        RentalVehicle v = vehicle(VehicleState.AVAILABLE, 80, null, HUB);

        BusinessException e = assertThrows(BusinessException.class, () -> service.pickUp(VEHICLE, STUDENT));

        assertEquals("VEHICLE_NOT_AVAIL", e.getCode());
        assertEquals(VehicleState.AVAILABLE, v.getState());
        verifyNoInteractions(dock, publisher);
    }

    @ParameterizedTest(name = "battery {0}% -> {1}")
    @CsvSource({"19, BATTERY_TOO_LOW", "20, OK"})
    @DisplayName("TC-M3-06 (BR-M3-02, BVA): pick-up battery boundary at the 20% threshold")
    void pickUpBatteryBoundary(int battery, String expected) {
        RentalVehicle v = vehicle(VehicleState.RESERVED, battery, STUDENT, HUB);

        if ("OK".equals(expected)) {
            service.pickUp(VEHICLE, STUDENT);
            assertEquals(VehicleState.IN_USE, v.getState());
        } else {
            BusinessException e = assertThrows(BusinessException.class, () -> service.pickUp(VEHICLE, STUDENT));
            assertEquals(expected, e.getCode());
            assertEquals(VehicleState.RESERVED, v.getState());
            verify(sessions, never()).save(any());
        }
    }

    @Test
    @DisplayName("TC-M3-07 (BR-M3-01): pick-up by a student who did not reserve -> RESERVATION_MISMATCH")
    void pickUpByAnotherStudentIsRefused() {
        RentalVehicle v = vehicle(VehicleState.RESERVED, 80, STUDENT, HUB);

        BusinessException e = assertThrows(BusinessException.class, () -> service.pickUp(VEHICLE, OTHER));

        assertEquals("RESERVATION_MISMATCH", e.getCode());
        assertEquals(VehicleState.RESERVED, v.getState());
        assertEquals(STUDENT, v.getReservedByUserId());
        verifyNoInteractions(dock, publisher);
    }

    @ParameterizedTest(name = "battery {0}% -> {1}")
    @CsvSource({"20, AVAILABLE", "19, LOW_BATTERY"})
    @DisplayName("TC-M3-08 (FR-M3-02, FR-M3-04, BVA): return docks the vehicle and sets the state by battery")
    void returnSetsStateByBattery(int battery, VehicleState expected) {
        RentalVehicle v = vehicle(VehicleState.IN_USE, 60, null, null);
        RentalSession trip = RentalSession.start(VEHICLE, STUDENT);
        when(sessions.findByVehicleIdAndStatus(VEHICLE, SessionStatus.ACTIVE)).thenReturn(Optional.of(trip));

        service.returnVehicle(VEHICLE, HUB, battery, STUDENT);

        assertEquals(expected, v.getState());
        assertEquals(HUB, v.getCurrentHubId());
        assertEquals(battery, v.getBatteryLevel());
        assertEquals(SessionStatus.COMPLETED, trip.getStatus());
        verify(dock).dockRentalVehicle(VEHICLE, HUB);
        List<DomainEvent> events = published();
        assertTrue(events.stream().anyMatch(e -> e instanceof VehicleReturnedEvent));
        assertEquals(expected == VehicleState.LOW_BATTERY,
                events.stream().anyMatch(e -> e instanceof VehicleLowBatteryEvent));
    }

    @Test
    @DisplayName("TC-M3-09 (BR-M3-03): return to a full Hub -> HUB_FULL, vehicle stays IN_USE, trip stays ACTIVE")
    void returnToAFullHubIsRefused() {
        RentalVehicle v = vehicle(VehicleState.IN_USE, 60, null, null);
        RentalSession trip = RentalSession.start(VEHICLE, STUDENT);
        when(sessions.findByVehicleIdAndStatus(VEHICLE, SessionStatus.ACTIVE)).thenReturn(Optional.of(trip));
        when(dock.dockRentalVehicle(VEHICLE, HUB)).thenThrow(new BusinessException("HUB_FULL", "full"));
        when(dock.vacancies()).thenReturn(List.of());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.returnVehicle(VEHICLE, HUB, 60, STUDENT));

        assertEquals("HUB_FULL", e.getCode());
        assertEquals(VehicleState.IN_USE, v.getState());
        assertEquals(SessionStatus.ACTIVE, trip.getStatus());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("TC-M3-10 (FR-M3-03): current trip with and without an ACTIVE trip")
    void currentTrip() {
        vehicle(VehicleState.IN_USE, 64, null, null);
        when(sessions.findByUserIdAndStatus(STUDENT, SessionStatus.ACTIVE))
                .thenReturn(Optional.of(RentalSession.start(VEHICLE, STUDENT)));
        when(sessions.findByUserIdAndStatus(OTHER, SessionStatus.ACTIVE)).thenReturn(Optional.empty());

        CurrentTripResponse trip = service.getCurrentTrip(STUDENT);
        assertEquals("EB-T01", trip.vehicleCode());
        assertEquals(64, trip.batteryLevel());
        assertNotNull(trip.startTime());

        BusinessException e = assertThrows(BusinessException.class, () -> service.getCurrentTrip(OTHER));
        assertEquals("NO_ACTIVE_TRIP", e.getCode());
    }
}
