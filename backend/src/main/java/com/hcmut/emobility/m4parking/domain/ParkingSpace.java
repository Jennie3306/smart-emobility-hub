package com.hcmut.emobility.m4parking.domain;

import com.hcmut.emobility.shared.common.BusinessException;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;

/**
 * Entity ParkingSpace - quan ly thong tin va trang thai cho do xe tai Hub (Module M4).
 *
 * State Machine:
 *   FREE -> RESERVED -> OCCUPIED -> FREE            (booking: personal or rental vehicle, via M2)
 *   FREE -> OCCUPIED -> FREE                         (rental vehicle docked / undocked, via ParkingDock)
 *   FREE / RESERVED / OCCUPIED -> BLOCKED (khi gap su co, vi du ChargerFailed tu M6)
 *   BLOCKED -> FREE (khi su co duoc khac phuc); BLOCKED -> OCCUPIED (khac phuc, xe thue van dang do o do)
 *
 * Tuan thu cac Business Rules:
 *   - BR-M4-01: Gioi han suc chua Hub theo khung gio
 *   - BR-M4-04: Giai phong cho do neu no-show qua 15 phut
 *   - BR-M4-05: Chi chuyen sang OCCUPIED khi co reservation hop le hoac mot xe thue dang do (docked)
 *   - BR-M4-08: Mot Hub co mot quy cho do chung; xe thue dang dung o Hub chiem mot cho (Appendix A v2)
 *   - BR-M4-06: Chuyen sang BLOCKED khi gap su co, khong cho phep dat cho hoac check-in
 *   - BR-M4-07: Chan cac chuyen trang thai khong hop le theo state machine
 */
@Entity
@Table(name = "parking_space")
public class ParkingSpace {

    /**
     * Trang thai cho do xe theo state machine cua M4.
     */
    public enum Status {
        FREE,
        RESERVED,
        OCCUPIED,
        BLOCKED;

        /**
         * Kiem tra chuyen trang thai hop le theo thiet ke State Machine M4.
         */
        public boolean canMoveTo(Status target) {
            return switch (this) {
                case FREE -> target == RESERVED || target == OCCUPIED || target == BLOCKED;
                case RESERVED -> target == OCCUPIED || target == FREE || target == BLOCKED;
                case OCCUPIED -> target == FREE || target == BLOCKED;
                case BLOCKED -> target == FREE || target == OCCUPIED;
            };
        }
    }

    /**
     * Ly do khoa cho (V41). Chi cho bi khoa vi tru sac hong moi tu mo lai khi M6 bao tru sac hoat dong;
     * cho do Operator khoa thi phai do Operator mo.
     */
    public enum BlockReason {
        CHARGER_FAILURE,
        OPERATOR
    }

    /**
     * Loai phuong tien cho cho do xe (xe oto dien hoac xe may/dap dien).
     */
    public enum Type {
        CAR,
        BIKE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    @Column(name = "space_code", nullable = false, length = 30)
    private String spaceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type = Type.BIKE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.FREE;

    @Column(name = "current_reservation_id")
    private Long currentReservationId;

    @Column(name = "charger_point_id")
    private Long chargerPointId;

    @Enumerated(EnumType.STRING)
    @Column(name = "blocked_reason", length = 20)
    private BlockReason blockedReason;

    /** Rental vehicle standing on this space (V42, BR-M4-08); null for a booked or free space. */
    @Column(name = "docked_vehicle_id", unique = true)
    private Long dockedVehicleId;

    @Version
    private Long version;

    protected ParkingSpace() {
    }

    public ParkingSpace(Long hubId, String spaceCode, Type type) {
        this.hubId = hubId;
        this.spaceCode = spaceCode;
        this.type = type;
        this.status = Status.FREE;
    }

    /**
     * Chuyen trang thai cho do xe, chan cac chuyen trang thai khong hop le.
     * Giu private de moi thay doi trang thai bat buoc di qua cac method nghiep vu.
     */
    private void changeStatus(Status newStatus) {
        if (!this.status.canMoveTo(newStatus)) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "A parking space cannot move from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
    }

    /**
     * Dat cho do (UC-M4-02): chuyen sang RESERVED va luu ma reservation.
     */
    public void book(Long reservationId) {
        requireReservationId(reservationId);
        changeStatus(Status.RESERVED);
        this.currentReservationId = reservationId;
    }

    /**
     * Check-in cho do (UC-M4-03 / BR-M4-05): chuyen sang OCCUPIED khi sinh vien toi Hub.
     * Chi cho phep check-in dung reservation dang gan voi cho.
     */
    public void checkIn(Long reservationId) {
        requireReservationId(reservationId);
        if (this.status != Status.RESERVED) {
            // FREE -> OCCUPIED is only for a docked rental vehicle (dock); a booking must be RESERVED first.
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Only a RESERVED space can be checked in; current state: " + this.status);
        }
        requireMatchingReservation(reservationId);
        changeStatus(Status.OCCUPIED);
    }

    /**
     * BR-M4-08: a rental vehicle arrives at the Hub (returned by a student or delivered by M7) and stands
     * on this free space. FREE -> OCCUPIED without a reservation.
     */
    public void dock(Long vehicleId) {
        if (vehicleId == null) {
            throw new BusinessException("INVALID_VEHICLE_ID", "Rental vehicle id is required", HttpStatus.BAD_REQUEST);
        }
        if (status != Status.FREE) {
            throw new BusinessException("SPACE_NOT_AVAILABLE", "Space " + spaceCode + " is " + status);
        }
        changeStatus(Status.OCCUPIED);
        this.dockedVehicleId = vehicleId;
    }

    /**
     * BR-M4-08: the docked rental vehicle leaves (picked up or moved away). OCCUPIED -> FREE; on a BLOCKED
     * space the vehicle is only detached and the space stays blocked.
     */
    public void undock(Long vehicleId) {
        if (vehicleId == null || !vehicleId.equals(this.dockedVehicleId)) {
            throw new BusinessException("VEHICLE_NOT_DOCKED",
                    "Rental vehicle " + vehicleId + " is not parked on space " + spaceCode);
        }
        this.dockedVehicleId = null;
        if (status == Status.OCCUPIED) changeStatus(Status.FREE);
    }

    /**
     * Giai phong cho do (UC-M4-03, UC-M4-04): tra cho ve FREE khi check-out hoac no-show/huy reservation.
     * Chi cho phep giai phong tu RESERVED hoac OCCUPIED va phai khop ma reservation dang gan (D-M4-02, BR-M4-06).
     */
    public void release(Long reservationId) {
        requireReservationId(reservationId);
        if (status != Status.RESERVED && status != Status.OCCUPIED) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Chi giai phong cho dang RESERVED hoac OCCUPIED, hien tai: " + status);
        }
        requireMatchingReservation(reservationId);
        changeStatus(Status.FREE);
        this.currentReservationId = null;
    }

    private static void requireReservationId(Long reservationId) {
        if (reservationId == null) {
            // Du lieu dau vao sai, khong phai xung dot trang thai: tra 400 thay vi 409 mac dinh.
            throw new BusinessException("INVALID_RESERVATION_ID",
                    "The reservation id is required", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireMatchingReservation(Long reservationId) {
        if (!reservationId.equals(this.currentReservationId)) {
            throw new BusinessException("RESERVATION_MISMATCH",
                    "Reservation " + reservationId + " does not match this space (current: "
                            + this.currentReservationId + ")");
        }
    }

    /**
     * Cho dang BLOCKED: reservation da ket thuc (check-out, huy, het han) nhung cho van khoa (BR-M4-06, D-M4-02).
     * Phai khop ma reservation dang gan, de su kien tre cua reservation cu khong go nham reservation hien tai.
     */
    public void detachReservation(Long reservationId) {
        requireReservationId(reservationId);
        if (status != Status.BLOCKED) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Chi bo lien ket khi cho dang BLOCKED");
        }
        requireMatchingReservation(reservationId);
        this.currentReservationId = null;
    }

    /**
     * Khoa cho do khi co su co (BR-M4-06): chuyen sang BLOCKED.
     */
    public void block() {
        block(BlockReason.OPERATOR);
    }

    /**
     * Khoa cho voi ly do cu the (BR-M4-06). Reservation dang gan (neu co) duoc giu lai de service
     * huy qua M2 hoac go ra khi check-out.
     */
    public void block(BlockReason reason) {
        changeStatus(Status.BLOCKED);
        this.blockedReason = reason == null ? BlockReason.OPERATOR : reason;
    }

    /** Cho bi khoa vi tru sac gan voi cho bi hong (ChargerFailed tu M6). */
    public boolean isBlockedByChargerFailure() {
        return status == Status.BLOCKED && blockedReason == BlockReason.CHARGER_FAILURE;
    }

    /**
     * Mo khoa cho do sau khi bao tri hoan tat: chuyen ve FREE.
     * Chi mo khoa khi khong con reservation nao gan voi cho (D-M4-01, BR-M4-06).
     */
    public void unblock() {
        if (currentReservationId != null) {
            throw new BusinessException("SPACE_STILL_IN_USE",
                    "Cho do van gan reservation " + currentReservationId);
        }
        // A rental vehicle that stood here during the incident is still parked: back to OCCUPIED, not FREE.
        changeStatus(dockedVehicleId != null ? Status.OCCUPIED : Status.FREE);
        this.blockedReason = null;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public Long getHubId() {
        return hubId;
    }

    public String getSpaceCode() {
        return spaceCode;
    }

    public void setSpaceCode(String spaceCode) {
        this.spaceCode = spaceCode;
    }

    public Type getType() {
        return type;
    }

    public Status getStatus() {
        return status;
    }

    public Long getCurrentReservationId() {
        return currentReservationId;
    }

    public Long getChargerPointId() {
        return chargerPointId;
    }

    public BlockReason getBlockedReason() {
        return blockedReason;
    }

    public Long getDockedVehicleId() {
        return dockedVehicleId;
    }

    public void setChargerPointId(Long chargerPointId) {
        this.chargerPointId = chargerPointId;
    }

    public Long getVersion() {
        return version;
    }
}
