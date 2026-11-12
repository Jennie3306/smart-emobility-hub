package com.hcmut.emobility.m4parking.service;

import com.hcmut.emobility.m2reservation.domain.Reservation;
import com.hcmut.emobility.m2reservation.domain.ResourceType;
import com.hcmut.emobility.m2reservation.service.ReservationCore;
import com.hcmut.emobility.m4parking.domain.ParkingBookingLog;
import com.hcmut.emobility.m4parking.domain.ParkingSpace;
import com.hcmut.emobility.m4parking.domain.PrivateVehicle;
import com.hcmut.emobility.m4parking.dto.BookParkingRequest;
import com.hcmut.emobility.m4parking.dto.ParkingAvailability;
import com.hcmut.emobility.m4parking.dto.ParkingBookingResponse;
import com.hcmut.emobility.m4parking.dto.ParkingHubSummary;
import com.hcmut.emobility.m4parking.dto.ParkingSpaceResponse;
import com.hcmut.emobility.m4parking.dto.PrivateVehicleResponse;
import com.hcmut.emobility.m4parking.dto.RegisterVehicleRequest;
import com.hcmut.emobility.m4parking.dto.UpdateVehicleRequest;
import com.hcmut.emobility.m4parking.repository.ParkingBookingLogRepository;
import com.hcmut.emobility.m4parking.repository.ParkingSpaceRepository;
import com.hcmut.emobility.m4parking.repository.PrivateVehicleRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.events.ChargingRequestedEvent;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * M4 - dat cho do cho xe thue va xe ca nhan (FR-M4-01..04).
 *
 * Quyen giu cho nam o Reservation core cua M2 (reserveWindow / checkIn / cancel / completeByResource);
 * M4 giu trang thai vat ly cua ParkingSpace va lich su trong parking_booking_log.
 * Moi thay doi trang thai cua mot cho deu khoa dong parking_space truoc (lockSpace), nen cac luong
 * dong thoi tren cung mot cho chay lan luot. Hai sinh vien cung dat cho cuoi cung thi khoa UNIQUE
 * active_resource_key cua M2 chi cho mot nguoi thang (UC-M4-02 luong ngoai le 5a).
 *
 * applyRelease / applyCheckIn la idempotent: chung duoc goi ca tu luong cua M4 lan tu listener khi M2
 * phat su kien (M2 phat su kien dong bo ngay trong luc M4 goi core), nen lan goi thu hai khong lam gi.
 */
@Service
public class ParkingService {

    private static final Logger log = LoggerFactory.getLogger(ParkingService.class);

    static final String PARKING_SPACE = "PARKING_SPACE";
    static final int MIN_BOOKING_MINUTES = 30;
    static final int MAX_BOOKING_HOURS = 12;
    static final int BOOK_AHEAD_HOURS = 24;
    /** Cho phep dat bat dau tu vai phut truoc (sinh vien bam "dat ngay"). */
    static final int START_GRACE_MINUTES = 5;
    /** Check-in som toi da 15 phut truoc gio bat dau. */
    static final int EARLY_CHECK_IN_MINUTES = 15;
    /**
     * Bang chuyen trang thai 4.1: Occupied -> Free khi "xe roi cong / ket thuc khung gio". Xe chua check-out
     * thi Timer tu check-out sau gio roi bai 15 phut (doi xung voi 15 phut no-show cua BR-M4-04).
     */
    static final int CHECK_OUT_GRACE_MINUTES = 15;
    /** M5 tu choi sac khi pin tu 90% tro len. */
    static final int FULL_BATTERY = 90;

    private final ParkingSpaceRepository spaces;
    private final ParkingBookingLogRepository bookings;
    private final PrivateVehicleRepository vehicles;
    private final ReservationCore reservations;
    private final EventPublisher events;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ParkingService(ParkingSpaceRepository spaces, ParkingBookingLogRepository bookings,
                          PrivateVehicleRepository vehicles, ReservationCore reservations,
                          EventPublisher events, JdbcTemplate jdbc, Clock clock) {
        this.spaces = spaces;
        this.bookings = bookings;
        this.vehicles = vehicles;
        this.reservations = reservations;
        this.events = events;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ FR-M4-01 xe ca nhan

    @Transactional(readOnly = true)
    public List<PrivateVehicleResponse> myVehicles(Long studentId) {
        return vehicles.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(PrivateVehicleResponse::from).toList();
    }

    /** FR-M4-01. Throws: INVALID_LICENSE_PLATE, VALIDATION_FAILED (400), LICENSE_PLATE_EXISTS (409). */
    @Transactional
    public PrivateVehicleResponse registerVehicle(Long studentId, RegisterVehicleRequest request) {
        if (request == null) throw badRequest("VALIDATION_FAILED", "Vehicle details are required");
        PrivateVehicle vehicle = PrivateVehicle.register(studentId, request.licensePlate(), request.vehicleType(),
                request.model(), request.batteryLevel(), now());
        if (vehicles.existsByLicensePlate(vehicle.getLicensePlate())) {
            throw new BusinessException("LICENSE_PLATE_EXISTS", "This license plate is already registered");
        }
        try {
            vehicle = vehicles.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("LICENSE_PLATE_EXISTS", "This license plate is already registered");
        }
        log.info("[M4] Student {} registered personal vehicle {} ({})", studentId, vehicle.getId(),
                vehicle.getLicensePlate());
        return PrivateVehicleResponse.from(vehicle);
    }

    @Transactional
    public PrivateVehicleResponse updateVehicle(Long studentId, Long vehicleId, UpdateVehicleRequest request) {
        if (request == null) throw badRequest("VALIDATION_FAILED", "Vehicle details are required");
        PrivateVehicle vehicle = ownedVehicle(studentId, vehicleId);
        vehicle.update(request.vehicleType(), request.model(), request.batteryLevel());
        return PrivateVehicleResponse.from(vehicles.save(vehicle));
    }

    /** "Xoa" xe: ngung su dung. Throws VEHICLE_IN_USE (409) khi xe dang co luot dat hieu luc. */
    @Transactional
    public PrivateVehicleResponse deactivateVehicle(Long studentId, Long vehicleId) {
        PrivateVehicle vehicle = ownedVehicle(studentId, vehicleId);
        if (bookings.existsByPersonalVehicleIdAndStatus(vehicleId, ParkingBookingLog.Status.ACTIVE)) {
            throw new BusinessException("VEHICLE_IN_USE", "This vehicle has an active parking booking");
        }
        vehicle.deactivate();
        return PrivateVehicleResponse.from(vehicles.save(vehicle));
    }

    @Transactional
    public PrivateVehicleResponse activateVehicle(Long studentId, Long vehicleId) {
        PrivateVehicle vehicle = ownedVehicle(studentId, vehicleId);
        vehicle.activate();
        return PrivateVehicleResponse.from(vehicles.save(vehicle));
    }

    // ------------------------------------------------------------------ Hub va cho do

    @Transactional(readOnly = true)
    public List<ParkingHubSummary> hubs() {
        List<HubRow> rows = jdbc.query("select id, name, parking_slots from hub order by id",
                (rs, rowNum) -> new HubRow(rs.getLong("id"), rs.getString("name"), rs.getInt("parking_slots")));
        List<ParkingHubSummary> result = new ArrayList<>();
        for (HubRow hub : rows) {
            List<ParkingSpace> hubSpaces = spaces.findByHubIdOrderById(hub.id());
            result.add(new ParkingHubSummary(hub.id(), hub.name(), hub.parkingSlots(),
                    count(hubSpaces, ParkingSpace.Status.FREE),
                    count(hubSpaces, ParkingSpace.Status.RESERVED),
                    count(hubSpaces, ParkingSpace.Status.OCCUPIED),
                    count(hubSpaces, ParkingSpace.Status.BLOCKED),
                    hubSpaces.stream().filter(s -> s.getStatus() == ParkingSpace.Status.FREE
                            && s.getChargerPointId() != null).count()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ParkingSpaceResponse> spaces(Long hubId) {
        hubCapacity(hubId);
        return spaces.findByHubIdOrderById(hubId).stream().map(ParkingSpaceResponse::from).toList();
    }

    /** BR-M4-01: suc chua con lai trong khung gio, de man hinh hien truoc khi sinh vien bam dat. */
    @Transactional(readOnly = true)
    public ParkingAvailability availability(Long hubId, OffsetDateTime startAt, OffsetDateTime endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw badRequest("INVALID_TIME_SLOT", "The booking must end after it starts");
        }
        int capacity = hubCapacity(hubId);
        long active = bookings.countOverlapping(hubId, ParkingBookingLog.Status.ACTIVE, startAt, endAt);
        long free = count(spaces.findByHubIdOrderById(hubId), ParkingSpace.Status.FREE);
        return new ParkingAvailability(hubId, startAt, endAt, capacity, active, Math.max(0, capacity - active), free);
    }

    // ------------------------------------------------------------------ FR-M4-02 dat cho

    /**
     * FR-M4-02, UC-M4-02.
     * Tien dieu kien: khung gio hop le; xe hop le (BR-M4-02); sinh vien chua co luot dat giao khung gio tai Hub
     * (BR-M4-03); Hub con suc chua (BR-M4-01); co cho FREE dung loai, khong BLOCKED (BR-M4-06).
     * Hau dieu kien: reservation CONFIRMED o M2 voi han check-in = gio bat dau + 15 phut (BR-M4-04);
     * ParkingSpace FREE -> RESERVED; mot dong parking_booking_log ACTIVE; ResourceStateUpdatedEvent cho M1;
     * neu yeu cau sac thi ChargingRequestedEvent cho M5.
     * Throws: INVALID_TIME_SLOT, BOOKING_TOO_FAR_AHEAD, INVALID_BOOKING_DURATION, BATTERY_LEVEL_REQUIRED,
     * BATTERY_ALREADY_FULL (400); VEHICLE_NOT_REGISTERED, RENTAL_VEHICLE_NOT_YOURS (403);
     * VEHICLE_INACTIVE, DUPLICATE_BOOKING, HUB_FULL, SPACE_BLOCKED, SPACE_NOT_AVAILABLE, SPACE_TYPE_MISMATCH,
     * SPACE_HAS_NO_CHARGER, NO_SPACE_AVAILABLE (409); loi cua M2: RESERVATION_LIMIT_EXCEEDED, RESOURCE_CONFLICT.
     */
    @Transactional
    public ParkingBookingResponse book(Long studentId, BookParkingRequest request) {
        if (request == null) throw badRequest("VALIDATION_FAILED", "Booking details are required");
        OffsetDateTime now = now();
        OffsetDateTime startAt = request.startAt();
        OffsetDateTime endAt = request.endAt();
        validateWindow(startAt, endAt, now);
        int capacity = hubCapacity(request.hubId());
        VehicleChoice vehicle = resolveVehicle(studentId, request);

        boolean charging = Boolean.TRUE.equals(request.requestCharging());
        if (charging) {
            if (vehicle.batteryLevel() == null) {
                throw badRequest("BATTERY_LEVEL_REQUIRED", "Enter the battery level to request charging");
            }
            if (vehicle.batteryLevel() >= FULL_BATTERY) {
                throw badRequest("BATTERY_ALREADY_FULL", "Charging is not accepted at 90% battery or higher");
            }
        }

        if (bookings.countStudentOverlapping(studentId, request.hubId(), ParkingBookingLog.Status.ACTIVE,
                startAt, endAt) > 0) {
            throw new BusinessException("DUPLICATE_BOOKING",
                    "You already have a parking booking at this Hub in the same time window");
        }
        if (bookings.countOverlapping(request.hubId(), ParkingBookingLog.Status.ACTIVE, startAt, endAt) >= capacity) {
            throw new BusinessException("HUB_FULL", "This Hub is fully booked for the selected time window");
        }

        ParkingSpace space = pickSpace(request.hubId(), vehicle.spaceType(), request.spaceId(), charging);
        Reservation reservation = reservations.reserveWindow(studentId, ResourceType.PARKING_SPACE, space.getId(),
                request.hubId(), startAt, endAt, ParkingBookingLog.deadlineFor(startAt));
        space.book(reservation.getId());
        spaces.save(space);
        ParkingBookingLog booking = bookings.save(ParkingBookingLog.open(reservation.getId(), space, studentId,
                vehicle.kind(), vehicle.personalVehicleId(), startAt, endAt, charging, now));
        publishSpace(space);
        if (charging) {
            events.publish(new ChargingRequestedEvent(studentId, space.getHubId(), space.getId(),
                    vehicle.kind().name(), vehicle.vehicleId(), vehicle.batteryLevel(), startAt, endAt));
        }
        log.info("[M4] Student {} booked space {} at hub {} ({} - {}), reservation {}{}", studentId,
                space.getSpaceCode(), space.getHubId(), startAt, endAt, reservation.getId(),
                charging ? ", charging requested" : "");
        return ParkingBookingResponse.of(booking, space);
    }

    // ------------------------------------------------------------------ FR-M4-03 check-in / check-out

    /**
     * FR-M4-03, UC-M4-03. Throws: BOOKING_NOT_FOUND (404), ACCESS_DENIED (403), BOOKING_NOT_ACTIVE,
     * ALREADY_CHECKED_IN, CHECK_IN_TOO_EARLY, CHECK_IN_EXPIRED, SPACE_BLOCKED (409), loi cua M2.
     */
    @Transactional
    public ParkingBookingResponse checkIn(Long studentId, Long bookingId) {
        ParkingBookingLog booking = ownedBooking(studentId, bookingId);
        if (!booking.isActive()) throw notActive(booking);
        if (booking.isCheckedIn()) {
            throw new BusinessException("ALREADY_CHECKED_IN", "You have already checked in");
        }
        OffsetDateTime now = now();
        if (now.isBefore(booking.getSlotStart().minusMinutes(EARLY_CHECK_IN_MINUTES))) {
            throw new BusinessException("CHECK_IN_TOO_EARLY",
                    "Check-in opens " + EARLY_CHECK_IN_MINUTES + " minutes before your booked time");
        }
        if (!now.isBefore(booking.getCheckInDeadline())) {
            // UC-M4-03 3a: Timer cua M2 se danh dau EXPIRED va listener giai phong cho (UC-M4-04).
            throw new BusinessException("CHECK_IN_EXPIRED",
                    "The check-in deadline (15 minutes after the start time) has passed");
        }
        ParkingSpace space = lockSpace(booking.getSpaceId());
        if (space.getStatus() == ParkingSpace.Status.BLOCKED) {
            throw new BusinessException("SPACE_BLOCKED", "This parking space is blocked because of an incident");
        }
        reservations.checkIn(booking.getReservationId(), studentId);
        applyCheckIn(booking.getReservationId(), now);
        return ParkingBookingResponse.of(booking, space);
    }

    /** FR-M4-03 luong thay the 1a: check-out -> reservation COMPLETED, cho ve FREE. */
    @Transactional
    public ParkingBookingResponse checkOut(Long studentId, Long bookingId) {
        ParkingBookingLog booking = ownedBooking(studentId, bookingId);
        if (!booking.isActive()) throw notActive(booking);
        if (!booking.isCheckedIn()) {
            throw new BusinessException("NOT_CHECKED_IN", "Check in before checking out");
        }
        ParkingSpace space = lockSpace(booking.getSpaceId());
        finishStay(booking, space, now());
        return ParkingBookingResponse.of(booking, space);
    }

    /**
     * Check-out tu dong (Timer, bang chuyen trang thai 4.1 "ket thuc khung gio"): luot da check-in ma qua
     * gio roi bai + 15 phut van chua check-out thi dong lai, tra cho ve FREE, reservation COMPLETED.
     * Nho vay suc chua theo khung gio (BR-M4-01) khop voi so cho that su dang dung.
     * Tra ve true khi da check-out; false neu luot dat da dong hoac chua toi han.
     */
    @Transactional
    public boolean autoCheckOut(Long bookingId) {
        ParkingBookingLog booking = bookings.findById(bookingId).orElse(null);
        if (booking == null || !booking.isActive() || !booking.isCheckedIn()) return false;
        OffsetDateTime now = now();
        if (!now.isAfter(booking.getSlotEnd().plusMinutes(CHECK_OUT_GRACE_MINUTES))) return false;
        finishStay(booking, lockSpace(booking.getSpaceId()), now);
        log.info("[M4] Booking {} at space {} checked out automatically: window ended at {}", booking.getId(),
                booking.getSpaceId(), booking.getSlotEnd());
        return true;
    }

    /** UC-M4-03 luong 1a: reservation COMPLETED o M2, cho ve FREE (hoac chi go reservation neu cho dang khoa). */
    private void finishStay(ParkingBookingLog booking, ParkingSpace space, OffsetDateTime now) {
        if (reservations.completeByResource(ResourceType.PARKING_SPACE, space.getId()).isEmpty()) {
            log.warn("[M4] No CHECKED_IN reservation in M2 for space {} (reservation {}); closing the booking anyway",
                    space.getId(), booking.getReservationId());
        }
        releaseSpace(space, booking.getReservationId());
        booking.close(ParkingBookingLog.Status.COMPLETED, now);
        bookings.save(booking);
    }

    /** Sinh vien huy truoc khi check-in. M2 tu choi khi da qua han (RESERVATION_EXPIRED). */
    @Transactional
    public ParkingBookingResponse cancel(Long studentId, Long bookingId) {
        ParkingBookingLog booking = ownedBooking(studentId, bookingId);
        if (!booking.isActive()) throw notActive(booking);
        if (booking.isCheckedIn()) {
            throw new BusinessException("ALREADY_CHECKED_IN", "You are already parked here. Check out instead.");
        }
        reservations.cancel(booking.getReservationId(), studentId);
        applyRelease(booking.getReservationId(), ParkingBookingLog.Status.CANCELLED, now());
        return ParkingBookingResponse.of(booking, spaces.findById(booking.getSpaceId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<ParkingBookingResponse> myBookings(Long studentId) {
        return withSpaces(bookings.findByStudentIdOrderByCreatedAtDesc(studentId));
    }

    @Transactional(readOnly = true)
    public List<ParkingBookingResponse> hubBookings(Long hubId) {
        hubCapacity(hubId);
        return withSpaces(bookings.findTop50ByHubIdOrderByCreatedAtDesc(hubId));
    }

    // ------------------------------------------------------------------ Su kien tu M2 (idempotent)

    /** ReservationCheckedInEvent (PARKING_SPACE): RESERVED -> OCCUPIED (BR-M4-05). */
    @Transactional
    public void applyCheckIn(Long reservationId) {
        applyCheckIn(reservationId, now());
    }

    /**
     * FR-M4-04, UC-M4-04: ReservationExpired (NO_SHOW) / ReservationCancelled (CANCELLED) tu M2,
     * hoac check-out (COMPLETED). Giai phong cho va dong luot dat; goi lai lan hai thi bo qua (3a).
     */
    @Transactional
    public void applyRelease(Long reservationId, ParkingBookingLog.Status outcome) {
        applyRelease(reservationId, outcome, now());
    }

    // ------------------------------------------------------------------ Su co (BR-M4-06)

    /** ChargerFailedEvent tu M6: khoa cho gan voi tru sac do; reservation chua check-in bi huy qua M2. */
    @Transactional
    public void handleChargerFailed(Long chargerPointId) {
        spaces.findByChargerPointId(chargerPointId).ifPresent(found ->
                blockSpace(lockSpace(found.getId()), ParkingSpace.BlockReason.CHARGER_FAILURE));
    }

    /** M6 bao tru sac hoat dong lai: mo cho bi khoa vi tru sac, neu khong con xe nao dang do o do. */
    @Transactional
    public void handleChargerRecovered(Long chargerPointId) {
        spaces.findByChargerPointId(chargerPointId).ifPresent(found -> {
            if (!found.isBlockedByChargerFailure() || found.getCurrentReservationId() != null) return;
            ParkingSpace space = lockSpace(found.getId());
            if (!space.isBlockedByChargerFailure() || space.getCurrentReservationId() != null) return;
            space.unblock();
            spaces.save(space);
            publishSpace(space);
            log.info("[M4] Charger {} is back; space {} unblocked", chargerPointId, space.getSpaceCode());
        });
    }

    /** Operator khoa cho (bao tri). Throws SPACE_NOT_FOUND (404), SPACE_ALREADY_BLOCKED (409). */
    @Transactional
    public ParkingSpaceResponse blockByOperator(Long spaceId) {
        ParkingSpace space = lockSpace(spaceId);
        if (space.getStatus() == ParkingSpace.Status.BLOCKED) {
            throw new BusinessException("SPACE_ALREADY_BLOCKED", "This space is already blocked");
        }
        blockSpace(space, ParkingSpace.BlockReason.OPERATOR);
        return ParkingSpaceResponse.from(space);
    }

    /** Operator mo khoa sau bao tri. Throws SPACE_NOT_BLOCKED, SPACE_STILL_IN_USE (xe van dang do) (409). */
    @Transactional
    public ParkingSpaceResponse unblockByOperator(Long spaceId) {
        ParkingSpace space = lockSpace(spaceId);
        if (space.getStatus() != ParkingSpace.Status.BLOCKED) {
            throw new BusinessException("SPACE_NOT_BLOCKED", "This space is not blocked");
        }
        space.unblock();
        spaces.save(space);
        publishSpace(space);
        return ParkingSpaceResponse.from(space);
    }

    /**
     * Dong bo luc khoi dong (chot 12/11): phat trang thai moi cho do mot lan de mo hinh doc cua M1 (va M8)
     * bat dau dung bang so lieu M4 dang giu, ke ca tren database da seed truoc khi co M4.
     */
    @Transactional
    public int publishAllSpaceStates() {
        List<ParkingSpace> all = spaces.findAll();
        all.forEach(this::publishSpace);
        return all.size();
    }

    // ------------------------------------------------------------------ Timer (UC-M4-04)

    /** Luot da check-in, qua gio roi bai + 15 phut ma chua check-out. */
    @Transactional(readOnly = true)
    public List<Long> overstayBookingIds() {
        return bookings.findOverstays(ParkingBookingLog.Status.ACTIVE, now().minusMinutes(CHECK_OUT_GRACE_MINUTES))
                .stream().map(ParkingBookingLog::getId).toList();
    }

    @Transactional(readOnly = true)
    public List<Long> overdueReservationIds() {
        return bookings.findOverdueNoShows(ParkingBookingLog.Status.ACTIVE, now().minusMinutes(1)).stream()
                .map(ParkingBookingLog::getReservationId).toList();
    }

    /**
     * Luoi an toan cho UC-M4-04: luot dat qua han ma M4 chua nhan su kien ket thuc tu M2.
     * Neu M2 van giu reservation (CONFIRMED) thi de Timer cua M2 danh dau EXPIRED; neu M2 da dong
     * reservation (su kien bi bo lo) thi M4 tu giai phong cho. Tra ve true khi da giai phong.
     */
    @Transactional
    public boolean releaseIfReservationEnded(Long reservationId) {
        ParkingBookingLog booking = bookings.findByReservationId(reservationId).orElse(null);
        if (booking == null || !booking.isActive() || booking.isCheckedIn()) return false;
        boolean stillHeld = reservations.findActive(booking.getStudentId(), ResourceType.PARKING_SPACE)
                .map(r -> r.getId().equals(reservationId)).orElse(false);
        if (stillHeld) return false;
        applyRelease(reservationId, ParkingBookingLog.Status.NO_SHOW, now());
        return true;
    }

    // ------------------------------------------------------------------ noi bo

    void applyCheckIn(Long reservationId, OffsetDateTime now) {
        ParkingBookingLog booking = bookings.findByReservationId(reservationId).orElse(null);
        if (booking == null) {
            log.warn("[M4] Check-in for reservation {} that has no parking booking", reservationId);
            return;
        }
        if (!booking.isActive() || booking.isCheckedIn()) return;
        ParkingSpace space = lockSpace(booking.getSpaceId());
        if (space.getStatus() == ParkingSpace.Status.BLOCKED) {
            // BR-M4-06: khong cho chiem cho dang bi khoa, ke ca khi check-in di thang qua API cua M2.
            throw new BusinessException("SPACE_BLOCKED", "This parking space is blocked because of an incident");
        }
        if (space.getStatus() == ParkingSpace.Status.RESERVED) {
            space.checkIn(reservationId);
            spaces.save(space);
            publishSpace(space);
        }
        booking.markCheckedIn(now);
        bookings.save(booking);
        log.info("[M4] Reservation {} checked in at space {}", reservationId, space.getSpaceCode());
    }

    void applyRelease(Long reservationId, ParkingBookingLog.Status outcome, OffsetDateTime now) {
        ParkingBookingLog booking = bookings.findByReservationId(reservationId).orElse(null);
        if (booking == null) {
            // UC-M4-04 2a: khong co luot dat - van giai phong cho nao con giu reservation nay.
            spaces.findByCurrentReservationId(reservationId).ifPresentOrElse(
                    found -> releaseSpace(lockSpace(found.getId()), reservationId),
                    () -> log.warn("[M4] Release for reservation {}: no booking and no space holds it", reservationId));
            return;
        }
        if (!booking.isActive()) {
            log.debug("[M4] Reservation {} already closed as {}", reservationId, booking.getStatus());
            return;
        }
        if (outcome == ParkingBookingLog.Status.NO_SHOW && booking.isCheckedIn()) {
            // UC-M4-04 3b: xe da check-in, khong giai phong vi no-show.
            log.info("[M4] Ignoring no-show for reservation {}: already checked in", reservationId);
            return;
        }
        ParkingBookingLog.Status finalOutcome =
                outcome == ParkingBookingLog.Status.COMPLETED && !booking.isCheckedIn()
                        ? ParkingBookingLog.Status.CANCELLED : outcome;
        releaseSpace(lockSpace(booking.getSpaceId()), reservationId);
        booking.close(finalOutcome, now);
        bookings.save(booking);
        log.info("[M4] Booking {} (reservation {}) closed as {}", booking.getId(), reservationId, finalOutcome);
    }

    /** Tra cho ve FREE neu no van giu reservation nay; cho dang BLOCKED thi chi go reservation ra. */
    private void releaseSpace(ParkingSpace space, Long reservationId) {
        if (!reservationId.equals(space.getCurrentReservationId())) return;
        if (space.getStatus() == ParkingSpace.Status.BLOCKED) {
            space.detachReservation(reservationId);
            spaces.save(space);
            return;
        }
        space.release(reservationId);
        spaces.save(space);
        publishSpace(space);
    }

    private void blockSpace(ParkingSpace space, ParkingSpace.BlockReason reason) {
        if (space.getStatus() == ParkingSpace.Status.BLOCKED) return;
        ParkingSpace.Status before = space.getStatus();
        Long reservationId = space.getCurrentReservationId();
        space.block(reason);
        spaces.save(space);
        publishSpace(space);
        log.warn("[M4] Space {} at hub {} blocked ({}), was {}", space.getSpaceCode(), space.getHubId(), reason, before);
        if (before == ParkingSpace.Status.RESERVED && reservationId != null) {
            // BR-M4-06: sinh vien chua toi -> huy reservation qua M2 (RESOURCE_BLOCKED). Su kien
            // ReservationCancelled quay lai listener cua M4 (go reservation, dong luot dat) va M5 (huy sac).
            reservations.cancelForBlockedResource(reservationId);
            applyRelease(reservationId, ParkingBookingLog.Status.CANCELLED, now());
        }
        // OCCUPIED: xe dang do; luot dat van ACTIVE, check-out se go reservation khoi cho bi khoa.
    }

    private ParkingSpace pickSpace(Long hubId, ParkingSpace.Type type, Long requestedSpaceId, boolean needCharger) {
        if (requestedSpaceId != null) {
            ParkingSpace space = lockSpace(requestedSpaceId);
            if (!space.getHubId().equals(hubId)) {
                throw new BusinessException("SPACE_NOT_FOUND", "This space is not at the selected Hub", HttpStatus.NOT_FOUND);
            }
            if (space.getStatus() == ParkingSpace.Status.BLOCKED) {
                throw new BusinessException("SPACE_BLOCKED", "This space is blocked because of an incident. Choose another one.");
            }
            if (space.getStatus() != ParkingSpace.Status.FREE) {
                throw new BusinessException("SPACE_NOT_AVAILABLE", "This space was just booked. Choose another one.");
            }
            if (space.getType() != type) {
                throw new BusinessException("SPACE_TYPE_MISMATCH", "This space is for " + space.getType() + " vehicles");
            }
            if (needCharger && space.getChargerPointId() == null) {
                throw new BusinessException("SPACE_HAS_NO_CHARGER", "Choose a space with a charging point to charge");
            }
            return space;
        }
        // UC-M4-02 3a: tu chon. Khong sac thi uu tien cho khong co tru sac de danh cho nguoi can sac.
        List<ParkingSpace> candidates = spaces.findByHubIdAndTypeAndStatusOrderById(hubId, type, ParkingSpace.Status.FREE).stream()
                .filter(s -> !needCharger || s.getChargerPointId() != null)
                .sorted(Comparator.comparing((ParkingSpace s) -> needCharger || s.getChargerPointId() == null ? 0 : 1)
                        .thenComparing(ParkingSpace::getId))
                .toList();
        for (ParkingSpace candidate : candidates) {
            ParkingSpace locked = lockSpace(candidate.getId());
            if (locked.getStatus() == ParkingSpace.Status.FREE) return locked;
        }
        throw new BusinessException("NO_SPACE_AVAILABLE", needCharger
                ? "No free space with a charging point at this Hub. Try another Hub or book without charging."
                : "No free " + type + " space at this Hub. Try another Hub.");
    }

    private VehicleChoice resolveVehicle(Long studentId, BookParkingRequest request) {
        if (request.vehicleKind() == null) throw badRequest("VALIDATION_FAILED", "Choose a personal or rental vehicle");
        if (request.vehicleKind() == ParkingBookingLog.VehicleKind.PERSONAL) {
            if (request.personalVehicleId() == null) {
                throw badRequest("VALIDATION_FAILED", "Choose one of your registered vehicles");
            }
            PrivateVehicle vehicle = vehicles.findById(request.personalVehicleId())
                    .filter(v -> v.isOwnedBy(studentId))
                    .orElseThrow(() -> new BusinessException("VEHICLE_NOT_REGISTERED",
                            "Register this vehicle before booking a space", HttpStatus.FORBIDDEN));
            if (!vehicle.isActive()) {
                throw new BusinessException("VEHICLE_INACTIVE", "This vehicle is inactive. Reactivate it or register it again.");
            }
            Integer battery = request.batteryLevel() != null ? request.batteryLevel() : vehicle.getBatteryLevel();
            return new VehicleChoice(ParkingBookingLog.VehicleKind.PERSONAL, vehicle.getId(), vehicle.getId(),
                    vehicle.getVehicleType().requiredSpaceType(), battery);
        }
        if (request.rentalVehicleId() == null) {
            throw badRequest("VALIDATION_FAILED", "Enter the rental vehicle you are using");
        }
        // Chi dat cho cho xe thue ma sinh vien dang giu (reservation VEHICLE con hieu luc o M2).
        boolean holdsVehicle = reservations.findActive(studentId, ResourceType.VEHICLE)
                .map(r -> r.getResourceId().equals(request.rentalVehicleId())).orElse(false);
        if (!holdsVehicle) {
            throw new BusinessException("RENTAL_VEHICLE_NOT_YOURS",
                    "You can only book parking for a rental vehicle you have reserved or picked up", HttpStatus.FORBIDDEN);
        }
        Integer battery = request.batteryLevel() != null ? request.batteryLevel() : rentalBattery(request.rentalVehicleId());
        // Doi xe thue chi co E_BIKE / E_SCOOTER (V1) -> cho BIKE.
        return new VehicleChoice(ParkingBookingLog.VehicleKind.RENTAL, request.rentalVehicleId(), null,
                ParkingSpace.Type.BIKE, battery);
    }

    private Integer rentalBattery(Long rentalVehicleId) {
        List<Integer> levels = jdbc.queryForList("select battery_level from rental_vehicle where id = ?",
                Integer.class, rentalVehicleId);
        return levels.isEmpty() ? null : levels.get(0);
    }

    private void validateWindow(OffsetDateTime startAt, OffsetDateTime endAt, OffsetDateTime now) {
        if (startAt == null || endAt == null) throw badRequest("VALIDATION_FAILED", "Choose the arrival and leaving time");
        if (!endAt.isAfter(startAt)) throw badRequest("INVALID_TIME_SLOT", "The booking must end after it starts");
        if (startAt.isBefore(now.minusMinutes(START_GRACE_MINUTES))) {
            throw badRequest("INVALID_TIME_SLOT", "The arrival time is in the past");
        }
        if (startAt.isAfter(now.plusHours(BOOK_AHEAD_HOURS))) {
            throw badRequest("BOOKING_TOO_FAR_AHEAD", "Bookings open " + BOOK_AHEAD_HOURS + " hours in advance");
        }
        long minutes = Duration.between(startAt, endAt).toMinutes();
        if (minutes < MIN_BOOKING_MINUTES || minutes > MAX_BOOKING_HOURS * 60L) {
            throw badRequest("INVALID_BOOKING_DURATION",
                    "A booking must last between " + MIN_BOOKING_MINUTES + " minutes and " + MAX_BOOKING_HOURS + " hours");
        }
    }

    /** Throws HUB_NOT_FOUND (404). */
    private int hubCapacity(Long hubId) {
        if (hubId == null) throw badRequest("VALIDATION_FAILED", "Choose a Hub");
        List<Integer> slots = jdbc.queryForList("select parking_slots from hub where id = ?", Integer.class, hubId);
        if (slots.isEmpty()) {
            throw new BusinessException("HUB_NOT_FOUND", "Hub not found: " + hubId, HttpStatus.NOT_FOUND);
        }
        return slots.get(0);
    }

    private ParkingSpace lockSpace(Long spaceId) {
        return spaces.lockById(spaceId).orElseThrow(() ->
                new BusinessException("SPACE_NOT_FOUND", "Parking space not found: " + spaceId, HttpStatus.NOT_FOUND));
    }

    private PrivateVehicle ownedVehicle(Long studentId, Long vehicleId) {
        PrivateVehicle vehicle = vehicles.findById(vehicleId).orElseThrow(() ->
                new BusinessException("VEHICLE_NOT_FOUND", "Vehicle not found: " + vehicleId, HttpStatus.NOT_FOUND));
        if (!vehicle.isOwnedBy(studentId)) {
            throw new BusinessException("ACCESS_DENIED", "This vehicle does not belong to you", HttpStatus.FORBIDDEN);
        }
        return vehicle;
    }

    private ParkingBookingLog ownedBooking(Long studentId, Long bookingId) {
        ParkingBookingLog booking = bookings.findById(bookingId).orElseThrow(() ->
                new BusinessException("BOOKING_NOT_FOUND", "Booking not found: " + bookingId, HttpStatus.NOT_FOUND));
        if (!booking.isOwnedBy(studentId)) {
            throw new BusinessException("ACCESS_DENIED", "This booking does not belong to you", HttpStatus.FORBIDDEN);
        }
        return booking;
    }

    private List<ParkingBookingResponse> withSpaces(List<ParkingBookingLog> list) {
        Map<Long, ParkingSpace> byId = spaces.findAllById(list.stream().map(ParkingBookingLog::getSpaceId).distinct().toList())
                .stream().collect(Collectors.toMap(ParkingSpace::getId, Function.identity()));
        return list.stream().map(b -> ParkingBookingResponse.of(b, byId.get(b.getSpaceId()))).toList();
    }

    private void publishSpace(ParkingSpace space) {
        events.publish(new ResourceStateUpdatedEvent(space.getHubId(), PARKING_SPACE, space.getId(),
                space.getStatus().name()));
    }

    private static long count(List<ParkingSpace> list, ParkingSpace.Status status) {
        return list.stream().filter(s -> s.getStatus() == status).count();
    }

    private static BusinessException notActive(ParkingBookingLog booking) {
        return new BusinessException("BOOKING_NOT_ACTIVE", "This booking is already " + booking.getStatus());
    }

    private static BusinessException badRequest(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private record HubRow(Long id, String name, int parkingSlots) { }

    /** Xe da xac thuc cho luot dat: loai, id xe gui sang M5, id xe ca nhan luu log, loai cho, muc pin. */
    private record VehicleChoice(ParkingBookingLog.VehicleKind kind, Long vehicleId, Long personalVehicleId,
                                 ParkingSpace.Type spaceType, Integer batteryLevel) { }
}
