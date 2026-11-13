package com.hcmut.emobility.m4parking.domain;

import com.hcmut.emobility.shared.common.BusinessException;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Mot luot dat cho do (bang parking_booking_log, V40). Reservation cua M2 giu quyen su dung cho;
 * bang nay luu khung gio, xe, thoi diem check-in/check-out va ket qua cuoi cung de lam lich su.
 */
@Entity
@Table(name = "parking_booking_log")
public class ParkingBookingLog {

    public enum Status { ACTIVE, COMPLETED, NO_SHOW, CANCELLED }

    public enum VehicleKind { PERSONAL, RENTAL }

    /** BR-M4-04: qua 15 phut sau gio bat dau ma chua check-in thi la no-show. */
    public static final int NO_SHOW_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false, unique = true)
    private Long reservationId;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_kind", nullable = false, length = 10)
    private VehicleKind vehicleKind;

    @Column(name = "personal_vehicle_id")
    private Long personalVehicleId;

    @Column(name = "slot_start", nullable = false)
    private OffsetDateTime slotStart;

    @Column(name = "slot_end", nullable = false)
    private OffsetDateTime slotEnd;

    @Column(name = "check_in_deadline", nullable = false)
    private OffsetDateTime checkInDeadline;

    @Column(name = "charging_requested", nullable = false)
    private boolean chargingRequested;

    @Column(name = "check_in_time")
    private OffsetDateTime checkInTime;

    @Column(name = "check_out_time")
    private OffsetDateTime checkOutTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ParkingBookingLog() { }

    public static ParkingBookingLog open(Long reservationId, ParkingSpace space, Long studentId,
                                         VehicleKind kind, Long personalVehicleId,
                                         OffsetDateTime slotStart, OffsetDateTime slotEnd,
                                         boolean chargingRequested, OffsetDateTime now) {
        ParkingBookingLog log = new ParkingBookingLog();
        log.reservationId = reservationId;
        log.spaceId = space.getId();
        log.hubId = space.getHubId();
        log.studentId = studentId;
        log.vehicleKind = kind;
        // ck_booking_vehicle: chi xe ca nhan moi luu id xe.
        log.personalVehicleId = kind == VehicleKind.PERSONAL ? personalVehicleId : null;
        log.slotStart = slotStart;
        log.slotEnd = slotEnd;
        log.checkInDeadline = deadlineFor(slotStart);
        log.chargingRequested = chargingRequested;
        log.status = Status.ACTIVE;
        log.createdAt = now;
        return log;
    }

    public static OffsetDateTime deadlineFor(OffsetDateTime slotStart) {
        return slotStart.plusMinutes(NO_SHOW_MINUTES);
    }

    public void markCheckedIn(OffsetDateTime now) {
        requireActive();
        if (checkInTime == null) checkInTime = now;
    }

    /** Ket thuc luot dat: COMPLETED (check-out), NO_SHOW (qua han) hoac CANCELLED (huy / cho bi khoa). */
    public void close(Status outcome, OffsetDateTime now) {
        requireActive();
        if (outcome == null || outcome == Status.ACTIVE) {
            throw new BusinessException("INVALID_STATE_TRANSITION", "A booking must close as COMPLETED, NO_SHOW or CANCELLED");
        }
        if (outcome == Status.COMPLETED && checkInTime == null) {
            throw new BusinessException("INVALID_STATE_TRANSITION", "Check in before checking out");
        }
        if (outcome == Status.COMPLETED) checkOutTime = now;
        this.status = outcome;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isCheckedIn() {
        return checkInTime != null;
    }

    public boolean isOwnedBy(Long userId) {
        return studentId.equals(userId);
    }

    private void requireActive() {
        if (status != Status.ACTIVE) {
            throw new BusinessException("INVALID_STATE_TRANSITION", "This booking is already " + status);
        }
    }

    public Long getId() { return id; }
    public Long getReservationId() { return reservationId; }
    public Long getSpaceId() { return spaceId; }
    public Long getHubId() { return hubId; }
    public Long getStudentId() { return studentId; }
    public VehicleKind getVehicleKind() { return vehicleKind; }
    public Long getPersonalVehicleId() { return personalVehicleId; }
    public OffsetDateTime getSlotStart() { return slotStart; }
    public OffsetDateTime getSlotEnd() { return slotEnd; }
    public OffsetDateTime getCheckInDeadline() { return checkInDeadline; }
    public boolean isChargingRequested() { return chargingRequested; }
    public OffsetDateTime getCheckInTime() { return checkInTime; }
    public OffsetDateTime getCheckOutTime() { return checkOutTime; }
    public Status getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
