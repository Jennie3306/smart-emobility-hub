package com.hcmut.emobility.m4parking.domain;

import com.hcmut.emobility.shared.common.BusinessException;
import jakarta.persistence.*;

/**
 * Entity ParkingSpace - quan ly thong tin va trang thai cho do xe tai Hub (Module M4).
 *
 * State Machine:
 *   FREE -> RESERVED -> OCCUPIED -> FREE
 *   FREE / RESERVED / OCCUPIED -> BLOCKED (khi gap su co, vi du ChargerFailed tu M6)
 *   BLOCKED -> FREE (khi su co duoc khac phuc)
 *
 * Tuan thu cac Business Rules:
 *   - BR-M4-01: Gioi han suc chua Hub theo khung gio
 *   - BR-M4-04: Giai phong cho do neu no-show qua 15 phut
 *   - BR-M4-05: Chi chuyen sang OCCUPIED khi co reservation hop le
 *   - BR-M4-06: Chuyen sang BLOCKED khi gap su co, khong cho phep dat cho hoac check-in
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
                case FREE -> target == RESERVED || target == BLOCKED;
                case RESERVED -> target == OCCUPIED || target == FREE || target == BLOCKED;
                case OCCUPIED -> target == FREE || target == BLOCKED;
                case BLOCKED -> target == FREE;
            };
        }
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
     */
    public void changeStatus(Status newStatus) {
        if (!this.status.canMoveTo(newStatus)) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Khong the chuyen cho do tu trang thai " + this.status + " sang " + newStatus);
        }
        this.status = newStatus;
    }

    /**
     * Dat cho do (UC-M4-02): chuyen sang RESERVED va luu ma reservation.
     */
    public void book(Long reservationId) {
        changeStatus(Status.RESERVED);
        this.currentReservationId = reservationId;
    }

    /**
     * Check-in cho do (UC-M4-03 / BR-M4-05): chuyen sang OCCUPIED khi sinh vien toi Hub.
     */
    public void checkIn() {
        changeStatus(Status.OCCUPIED);
    }

    /**
     * Giai phong cho do (UC-M4-03, UC-M4-04): tra cho ve FREE khi check-out hoac no-show/huy reservation.
     */
    public void release() {
        changeStatus(Status.FREE);
        this.currentReservationId = null;
    }

    /**
     * Khoa cho do khi co su co (BR-M4-06): chuyen sang BLOCKED.
     */
    public void block() {
        changeStatus(Status.BLOCKED);
    }

    /**
     * Mo khoa cho do sau khi bao tri hoan tat: chuyen ve FREE.
     */
    public void unblock() {
        changeStatus(Status.FREE);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public Long getHubId() {
        return hubId;
    }

    public void setHubId(Long hubId) {
        this.hubId = hubId;
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

    public void setType(Type type) {
        this.type = type;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Long getCurrentReservationId() {
        return currentReservationId;
    }

    public void setCurrentReservationId(Long currentReservationId) {
        this.currentReservationId = currentReservationId;
    }

    public Long getChargerPointId() {
        return chargerPointId;
    }

    public void setChargerPointId(Long chargerPointId) {
        this.chargerPointId = chargerPointId;
    }

    public Long getVersion() {
        return version;
    }
}
