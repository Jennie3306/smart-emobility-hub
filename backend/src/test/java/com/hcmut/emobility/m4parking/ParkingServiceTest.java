package com.hcmut.emobility.m4parking;

import com.hcmut.emobility.m2reservation.domain.Reservation;
import com.hcmut.emobility.m2reservation.domain.ResourceType;
import com.hcmut.emobility.m2reservation.service.ReservationCore;
import com.hcmut.emobility.m4parking.domain.ParkingBookingLog;
import com.hcmut.emobility.m4parking.domain.ParkingSpace;
import com.hcmut.emobility.m4parking.domain.PrivateVehicle;
import com.hcmut.emobility.m4parking.dto.BookParkingRequest;
import com.hcmut.emobility.m4parking.dto.ParkingBookingResponse;
import com.hcmut.emobility.m4parking.dto.RegisterVehicleRequest;
import com.hcmut.emobility.m4parking.repository.ParkingBookingLogRepository;
import com.hcmut.emobility.m4parking.repository.ParkingSpaceRepository;
import com.hcmut.emobility.m4parking.repository.PrivateVehicleRepository;
import com.hcmut.emobility.m4parking.service.ParkingService;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.events.ChargingRequestedEvent;
import com.hcmut.emobility.shared.events.DomainEvent;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.hcmut.emobility.m4parking.domain.ParkingSpace.Status.BLOCKED;
import static com.hcmut.emobility.m4parking.domain.ParkingSpace.Status.FREE;
import static com.hcmut.emobility.m4parking.domain.ParkingSpace.Status.OCCUPIED;
import static com.hcmut.emobility.m4parking.domain.ParkingSpace.Status.RESERVED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service M4 voi Mockito: Reservation core cua M2, repository va EventPublisher la mock.
 * Ky thuat: phan hoach tuong duong (xe hop le / khong hop le), gia tri bien (suc chua 39/40 cua Hub),
 * kiem thu chuyen trang thai (check-in, no-show, su co tru sac).
 * Lien quan: FR-M4-01..04, BR-M4-01..06; TC-M4-22..35 (TC-M4-08..21 nam o ParkingSpaceStateTest).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParkingServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-20T08:00:00+07:00");
    private static final long STUDENT = 7L;
    private static final long OTHER_STUDENT = 8L;
    private static final long HUB = 1L;

    @Mock private ParkingSpaceRepository spaces;
    @Mock private ParkingBookingLogRepository bookings;
    @Mock private PrivateVehicleRepository vehicles;
    @Mock private ReservationCore reservations;
    @Mock private EventPublisher events;
    @Mock private JdbcTemplate jdbc;

    private ParkingService service;

    @BeforeEach
    void setUp() {
        service = new ParkingService(spaces, bookings, vehicles, reservations, events, jdbc,
                Clock.fixed(NOW.toInstant(), NOW.getOffset()));
        when(jdbc.queryForList(anyString(), eq(Integer.class), any())).thenReturn(List.of(40)); // Hub 1: 40 cho
        when(spaces.save(any(ParkingSpace.class))).thenAnswer(i -> i.getArgument(0));
        when(bookings.save(any(ParkingBookingLog.class))).thenAnswer(i -> i.getArgument(0));
        when(vehicles.save(any(PrivateVehicle.class))).thenAnswer(i -> i.getArgument(0));
        when(vehicles.saveAndFlush(any(PrivateVehicle.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ------------------------------------------------------------------ FR-M4-02 dat cho

    @Test
    @DisplayName("TC-M4-22: dat cho hop le -> M2 giu cho den gio bat dau + 15 phut, cho RESERVED, bao M1")
    void bookHoldsSpaceUntilCheckInDeadline() {
        vehicle(3L, STUDENT, PrivateVehicle.VehicleType.E_MOTORBIKE);
        ParkingSpace withCharger = space(1L, ParkingSpace.Type.BIKE, 1L);
        ParkingSpace plain = space(9L, ParkingSpace.Type.BIKE, null);
        freeSpaces(withCharger, plain);
        reserveReturns(500L);

        ParkingBookingResponse booking = service.book(STUDENT, personal(3L, false, null));

        assertEquals(9L, booking.spaceId(), "khong sac thi uu tien cho khong co tru sac");
        assertEquals(RESERVED, plain.getStatus());
        assertEquals(500L, plain.getCurrentReservationId());
        assertEquals(FREE, withCharger.getStatus());
        assertEquals(NOW.plusHours(1).plusMinutes(15), booking.checkInDeadline());
        verify(reservations).reserveWindow(STUDENT, ResourceType.PARKING_SPACE, 9L, HUB,
                NOW.plusHours(1), NOW.plusHours(3), NOW.plusHours(1).plusMinutes(15));
        verify(events).publish(argThat(e -> e instanceof ResourceStateUpdatedEvent u
                && "PARKING_SPACE".equals(u.getResourceType()) && "RESERVED".equals(u.getNewState())));
        verify(events, never()).publish(any(ChargingRequestedEvent.class));
    }

    @Test
    @DisplayName("TC-M4-23: dat cho kem sac -> chon cho co tru sac va phat ChargingRequestedEvent cho M5")
    void bookAndChargePublishesChargingRequest() {
        vehicle(3L, STUDENT, PrivateVehicle.VehicleType.E_MOTORBIKE);
        ParkingSpace withCharger = space(1L, ParkingSpace.Type.BIKE, 1L);
        ParkingSpace plain = space(9L, ParkingSpace.Type.BIKE, null);
        freeSpaces(withCharger, plain);
        reserveReturns(501L);

        service.book(STUDENT, personal(3L, true, 30));

        assertEquals(RESERVED, withCharger.getStatus());
        assertEquals(FREE, plain.getStatus());
        ArgumentCaptor<DomainEvent> published = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events, times(2)).publish(published.capture());
        ChargingRequestedEvent request = published.getAllValues().stream()
                .filter(ChargingRequestedEvent.class::isInstance).map(ChargingRequestedEvent.class::cast)
                .findFirst().orElseThrow();
        assertEquals("PERSONAL", request.getVehicleKind());
        assertEquals(Long.valueOf(3L), request.getVehicleId());
        assertEquals(Long.valueOf(1L), request.getParkingSpaceId());
        assertEquals(Integer.valueOf(30), request.getBatteryLevel());
        assertEquals(NOW.plusHours(1), request.getArriveAt());
        assertEquals(NOW.plusHours(3), request.getLeaveAt());
    }

    @Test
    @DisplayName("TC-M4-24 (BVA, BR-M4-01): 40/40 luot dat trong khung gio thi tu choi, 39/40 thi cho dat")
    void hubCapacityBoundary() {
        vehicle(3L, STUDENT, PrivateVehicle.VehicleType.E_MOTORBIKE);
        freeSpaces(space(9L, ParkingSpace.Type.BIKE, null));
        when(bookings.countOverlapping(eq(HUB), eq(ParkingBookingLog.Status.ACTIVE), any(), any())).thenReturn(40L);

        BusinessException full = assertThrows(BusinessException.class,
                () -> service.book(STUDENT, personal(3L, false, null)));
        assertEquals("HUB_FULL", full.getCode());
        verify(reservations, never()).reserveWindow(any(), any(), any(), any(), any(), any(), any());

        when(bookings.countOverlapping(eq(HUB), eq(ParkingBookingLog.Status.ACTIVE), any(), any())).thenReturn(39L);
        reserveReturns(502L);
        assertDoesNotThrow(() -> service.book(STUDENT, personal(3L, false, null)));
    }

    @Test
    @DisplayName("TC-M4-25 (EP, BR-M4-02): xe ngung hoat dong hoac xe cua nguoi khac thi khong duoc dat")
    void onlyOwnActiveVehicleCanBook() {
        vehicle(3L, STUDENT, PrivateVehicle.VehicleType.E_MOTORBIKE).deactivate();
        vehicle(4L, OTHER_STUDENT, PrivateVehicle.VehicleType.E_MOTORBIKE);

        assertEquals("VEHICLE_INACTIVE", assertThrows(BusinessException.class,
                () -> service.book(STUDENT, personal(3L, false, null))).getCode());
        BusinessException notMine = assertThrows(BusinessException.class,
                () -> service.book(STUDENT, personal(4L, false, null)));
        assertEquals("VEHICLE_NOT_REGISTERED", notMine.getCode());
        assertEquals(HttpStatus.FORBIDDEN, notMine.getStatus());
        verify(reservations, never()).reserveWindow(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-M4-26 (BR-M4-03): da co luot dat giao khung gio tai cung Hub thi tu choi")
    void duplicateBookingInSameWindowIsRejected() {
        vehicle(3L, STUDENT, PrivateVehicle.VehicleType.E_MOTORBIKE);
        when(bookings.countStudentOverlapping(eq(STUDENT), eq(HUB), eq(ParkingBookingLog.Status.ACTIVE), any(), any()))
                .thenReturn(1L);

        assertEquals("DUPLICATE_BOOKING", assertThrows(BusinessException.class,
                () -> service.book(STUDENT, personal(3L, false, null))).getCode());
    }

    @Test
    @DisplayName("TC-M4-27: khung gio va muc pin khong hop le bi tu choi voi loi 400")
    void invalidWindowAndBatteryAreRejected() {
        vehicle(3L, STUDENT, PrivateVehicle.VehicleType.E_MOTORBIKE);

        assertBadRequest("INVALID_TIME_SLOT", window(NOW.minusMinutes(10), NOW.plusHours(1)));
        assertBadRequest("BOOKING_TOO_FAR_AHEAD", window(NOW.plusHours(25), NOW.plusHours(26)));
        assertBadRequest("INVALID_BOOKING_DURATION", window(NOW.plusHours(1), NOW.plusHours(1).plusMinutes(20)));
        assertBadRequest("BATTERY_ALREADY_FULL", personal(3L, true, 90));
    }

    // ------------------------------------------------------------------ FR-M4-03 check-in / check-out

    @Test
    @DisplayName("TC-M4-28: check-in chi mo tu 15 phut truoc gio bat dau den 15 phut sau; dung han thi RESERVED -> OCCUPIED")
    void checkInWindow() {
        ParkingSpace early = reservedSpace(9L, 600L);
        booking(70L, 600L, early, NOW.plusMinutes(30));
        assertEquals("CHECK_IN_TOO_EARLY", assertThrows(BusinessException.class,
                () -> service.checkIn(STUDENT, 70L)).getCode());

        ParkingSpace late = reservedSpace(10L, 601L);
        booking(71L, 601L, late, NOW.minusMinutes(20));
        assertEquals("CHECK_IN_EXPIRED", assertThrows(BusinessException.class,
                () -> service.checkIn(STUDENT, 71L)).getCode());

        ParkingSpace onTime = reservedSpace(11L, 602L);
        ParkingBookingLog booking = booking(72L, 602L, onTime, NOW.minusMinutes(5));
        service.checkIn(STUDENT, 72L);

        verify(reservations).checkIn(602L, STUDENT);
        verify(reservations, never()).checkIn(600L, STUDENT);
        assertEquals(OCCUPIED, onTime.getStatus());
        assertEquals(NOW, booking.getCheckInTime());
        assertEquals(RESERVED, early.getStatus());
    }

    @Test
    @DisplayName("TC-M4-29: huy truoc khi check-in -> M2 huy reservation, cho ve FREE, luot dat CANCELLED")
    void cancelReleasesSpace() {
        ParkingSpace space = reservedSpace(9L, 600L);
        ParkingBookingLog booking = booking(70L, 600L, space, NOW.plusHours(1));

        service.cancel(STUDENT, 70L);

        verify(reservations).cancel(600L, STUDENT);
        assertEquals(FREE, space.getStatus());
        assertEquals(ParkingBookingLog.Status.CANCELLED, booking.getStatus());
    }

    // ------------------------------------------------------------------ FR-M4-04 no-show

    @Test
    @DisplayName("TC-M4-30 (UC-M4-04): ReservationExpired -> cho FREE, luot dat NO_SHOW; nhan lai lan hai thi bo qua")
    void noShowReleasesSpaceOnce() {
        ParkingSpace space = reservedSpace(9L, 600L);
        ParkingBookingLog booking = booking(70L, 600L, space, NOW.minusMinutes(20));

        service.applyRelease(600L, ParkingBookingLog.Status.NO_SHOW);
        service.applyRelease(600L, ParkingBookingLog.Status.NO_SHOW);

        assertEquals(FREE, space.getStatus());
        assertNull(space.getCurrentReservationId());
        assertEquals(ParkingBookingLog.Status.NO_SHOW, booking.getStatus());
        verify(events, times(1)).publish(any(ResourceStateUpdatedEvent.class));
    }

    @Test
    @DisplayName("TC-M4-31 (UC-M4-04 3b): xe da check-in thi su kien no-show khong giai phong cho")
    void noShowIsIgnoredAfterCheckIn() {
        ParkingSpace space = reservedSpace(9L, 600L);
        space.checkIn(600L);
        ParkingBookingLog booking = booking(70L, 600L, space, NOW.minusMinutes(20));
        booking.markCheckedIn(NOW.minusMinutes(18));

        service.applyRelease(600L, ParkingBookingLog.Status.NO_SHOW);

        assertEquals(OCCUPIED, space.getStatus());
        assertTrue(booking.isActive());
    }

    // ------------------------------------------------------------------ BR-M4-06 su co tru sac

    @Test
    @DisplayName("TC-M4-32 (BR-M4-06): tru sac hong khi cho dang RESERVED -> khoa cho, huy reservation qua M2")
    void chargerFailureCancelsReservedBooking() {
        ParkingSpace space = reservedSpace(1L, 600L);
        space.setChargerPointId(1L);
        when(spaces.findByChargerPointId(1L)).thenReturn(Optional.of(space));
        ParkingBookingLog booking = booking(70L, 600L, space, NOW.plusHours(1));

        service.handleChargerFailed(1L);

        assertEquals(BLOCKED, space.getStatus());
        assertTrue(space.isBlockedByChargerFailure());
        assertNull(space.getCurrentReservationId());
        assertEquals(ParkingBookingLog.Status.CANCELLED, booking.getStatus());
        verify(reservations).cancelForBlockedResource(600L);
    }

    @Test
    @DisplayName("TC-M4-33 (BR-M4-06): tru sac hong khi xe dang do -> khoa cho nhung giu luot dat; check-out van duoc")
    void chargerFailureWhileOccupiedKeepsBookingUntilCheckOut() {
        ParkingSpace space = reservedSpace(1L, 600L);
        space.setChargerPointId(1L);
        space.checkIn(600L);
        when(spaces.findByChargerPointId(1L)).thenReturn(Optional.of(space));
        ParkingBookingLog booking = booking(70L, 600L, space, NOW.minusMinutes(30));
        booking.markCheckedIn(NOW.minusMinutes(25));

        service.handleChargerFailed(1L);
        assertEquals(BLOCKED, space.getStatus());
        assertEquals(600L, space.getCurrentReservationId());
        verify(reservations, never()).cancelForBlockedResource(any());

        service.checkOut(STUDENT, 70L);
        verify(reservations).completeByResource(ResourceType.PARKING_SPACE, 1L);
        assertEquals(BLOCKED, space.getStatus());
        assertNull(space.getCurrentReservationId());
        assertEquals(ParkingBookingLog.Status.COMPLETED, booking.getStatus());
    }

    @Test
    @DisplayName("TC-M4-34: tru sac hoat dong lai chi mo cho bi khoa vi tru sac, khong mo cho Operator khoa")
    void chargerRecoveryOnlyUnblocksChargerBlocks() {
        ParkingSpace chargerBlocked = space(1L, ParkingSpace.Type.BIKE, 1L);
        chargerBlocked.block(ParkingSpace.BlockReason.CHARGER_FAILURE);
        ParkingSpace operatorBlocked = space(2L, ParkingSpace.Type.BIKE, 2L);
        operatorBlocked.block();
        when(spaces.findByChargerPointId(1L)).thenReturn(Optional.of(chargerBlocked));
        when(spaces.findByChargerPointId(2L)).thenReturn(Optional.of(operatorBlocked));
        when(spaces.lockById(1L)).thenReturn(Optional.of(chargerBlocked));
        when(spaces.lockById(2L)).thenReturn(Optional.of(operatorBlocked));

        service.handleChargerRecovered(1L);
        service.handleChargerRecovered(2L);

        assertEquals(FREE, chargerBlocked.getStatus());
        assertNull(chargerBlocked.getBlockedReason());
        assertEquals(BLOCKED, operatorBlocked.getStatus());
    }

    // ------------------------------------------------------------------ FR-M4-01 xe ca nhan

    @Test
    @DisplayName("TC-M4-35 (FR-M4-01): bien so duoc chuan hoa, trung bien so bi tu choi, xe dang dat cho khong xoa duoc")
    void vehicleRegistrationRules() {
        var created = service.registerVehicle(STUDENT,
                new RegisterVehicleRequest(" 59x1 123.45 ", PrivateVehicle.VehicleType.E_MOTORBIKE, "Evo200", 40));
        assertEquals("59X1123.45", created.licensePlate());
        assertEquals(PrivateVehicle.Status.ACTIVE, created.status());

        when(vehicles.existsByLicensePlate("59X1123.45")).thenReturn(true);
        assertEquals("LICENSE_PLATE_EXISTS", assertThrows(BusinessException.class, () -> service.registerVehicle(STUDENT,
                new RegisterVehicleRequest("59X1123.45", PrivateVehicle.VehicleType.E_MOTORBIKE, "Evo", null))).getCode());

        assertEquals("INVALID_LICENSE_PLATE", assertThrows(BusinessException.class, () -> service.registerVehicle(STUDENT,
                new RegisterVehicleRequest("@@", PrivateVehicle.VehicleType.E_CAR, "VF3", null))).getCode());

        PrivateVehicle inUse = vehicle(3L, STUDENT, PrivateVehicle.VehicleType.E_CAR);
        when(bookings.existsByPersonalVehicleIdAndStatus(3L, ParkingBookingLog.Status.ACTIVE)).thenReturn(true);
        assertEquals("VEHICLE_IN_USE", assertThrows(BusinessException.class,
                () -> service.deactivateVehicle(STUDENT, 3L)).getCode());
        assertTrue(inUse.isActive());
        assertEquals(ParkingSpace.Type.CAR, inUse.getVehicleType().requiredSpaceType());
        assertFalse(PrivateVehicle.VehicleType.E_SCOOTER.requiredSpaceType() == ParkingSpace.Type.CAR);
    }

    @Test
    @DisplayName("TC-M4-39 (BVA, bang 4.1 'ket thuc khung gio'): dung gio roi bai + 15 phut chua check-out; qua 1 phut thi Timer tu check-out")
    void autoCheckOutAfterWindowEnds() {
        ParkingSpace space = reservedSpace(9L, 600L);
        space.checkIn(600L);
        // Khung gio 05:45 -> 07:45, gio roi bai + 15 phut = 08:00 = NOW
        ParkingBookingLog booking = booking(70L, 600L, space, NOW.minusHours(2).minusMinutes(15));
        booking.markCheckedIn(NOW.minusHours(2));

        assertFalse(service.autoCheckOut(70L), "dung bien 15 phut thi chua check-out");
        assertEquals(OCCUPIED, space.getStatus());

        ParkingService later = new ParkingService(spaces, bookings, vehicles, reservations, events, jdbc,
                Clock.fixed(NOW.plusMinutes(1).toInstant(), NOW.getOffset()));
        assertTrue(later.autoCheckOut(70L));
        assertEquals(FREE, space.getStatus());
        assertEquals(ParkingBookingLog.Status.COMPLETED, booking.getStatus());
        assertEquals(NOW.plusMinutes(1), booking.getCheckOutTime());
        verify(reservations).completeByResource(ResourceType.PARKING_SPACE, 9L);

        assertFalse(later.autoCheckOut(70L), "luot da dong thi khong lam gi them");
    }

    // ------------------------------------------------------------------ helpers

    private static ParkingSpace space(long id, ParkingSpace.Type type, Long chargerPointId) {
        ParkingSpace space = new ParkingSpace(HUB, String.format("P-%02d", id), type);
        ReflectionTestUtils.setField(space, "id", id);
        if (chargerPointId != null) space.setChargerPointId(chargerPointId);
        return space;
    }

    private ParkingSpace reservedSpace(long id, long reservationId) {
        ParkingSpace space = space(id, ParkingSpace.Type.BIKE, null);
        space.book(reservationId);
        when(spaces.lockById(id)).thenReturn(Optional.of(space));
        when(spaces.findById(id)).thenReturn(Optional.of(space));
        return space;
    }

    private void freeSpaces(ParkingSpace... free) {
        when(spaces.findByHubIdAndTypeAndStatusOrderById(eq(HUB), any(), eq(FREE))).thenReturn(List.of(free));
        for (ParkingSpace space : free) when(spaces.lockById(space.getId())).thenReturn(Optional.of(space));
    }

    private PrivateVehicle vehicle(long id, long owner, PrivateVehicle.VehicleType type) {
        PrivateVehicle vehicle = PrivateVehicle.register(owner, "59X1-" + (10000 + id), type, "Evo200", 40, NOW);
        ReflectionTestUtils.setField(vehicle, "id", id);
        when(vehicles.findById(id)).thenReturn(Optional.of(vehicle));
        return vehicle;
    }

    private ParkingBookingLog booking(long id, long reservationId, ParkingSpace space, OffsetDateTime slotStart) {
        ParkingBookingLog booking = ParkingBookingLog.open(reservationId, space, STUDENT,
                ParkingBookingLog.VehicleKind.PERSONAL, 3L, slotStart, slotStart.plusHours(2), false, NOW.minusHours(1));
        ReflectionTestUtils.setField(booking, "id", id);
        when(bookings.findById(id)).thenReturn(Optional.of(booking));
        when(bookings.findByReservationId(reservationId)).thenReturn(Optional.of(booking));
        return booking;
    }

    private void reserveReturns(long reservationId) {
        Reservation reservation = mock(Reservation.class);
        when(reservation.getId()).thenReturn(reservationId);
        when(reservations.reserveWindow(any(), any(), any(), any(), any(), any(), any())).thenReturn(reservation);
    }

    private static BookParkingRequest personal(long vehicleId, boolean charge, Integer battery) {
        return new BookParkingRequest(HUB, NOW.plusHours(1), NOW.plusHours(3), ParkingBookingLog.VehicleKind.PERSONAL,
                vehicleId, null, null, charge, battery);
    }

    private static BookParkingRequest window(OffsetDateTime start, OffsetDateTime end) {
        return new BookParkingRequest(HUB, start, end, ParkingBookingLog.VehicleKind.PERSONAL, 3L, null, null, false, null);
    }

    private void assertBadRequest(String code, BookParkingRequest request) {
        BusinessException error = assertThrows(BusinessException.class, () -> service.book(STUDENT, request));
        assertEquals(code, error.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }
}
