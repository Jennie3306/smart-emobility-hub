package com.hcmut.emobility.m4parking.domain;

import com.hcmut.emobility.shared.common.BusinessException;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Xe ca nhan cua sinh vien (FR-M4-01, BR-M4-02) - bang personal_vehicle (V40).
 * Ten class theo class diagram muc 6.1 cua Template_M4.
 */
@Entity
@Table(name = "personal_vehicle")
public class PrivateVehicle {

    public enum VehicleType {
        E_MOTORBIKE, E_BIKE, E_SCOOTER, E_CAR;

        /** Loai cho do phu hop: o to dien do o cho CAR, cac loai con lai do o cho BIKE. */
        public ParkingSpace.Type requiredSpaceType() {
            return this == E_CAR ? ParkingSpace.Type.CAR : ParkingSpace.Type.BIKE;
        }
    }

    public enum Status { ACTIVE, INACTIVE }

    /** Bien so: chu, so, dau gach ngang va dau cham; 5-20 ky tu sau khi chuan hoa. */
    private static final Pattern PLATE = Pattern.compile("^[A-Z0-9][A-Z0-9.\\-]{3,18}[A-Z0-9]$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "license_plate", nullable = false, unique = true, length = 20)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Version
    private Long version;

    protected PrivateVehicle() { }

    /** FR-M4-01: dang ky xe moi, mac dinh ACTIVE. */
    public static PrivateVehicle register(Long studentId, String licensePlate, VehicleType type,
                                          String model, Integer batteryLevel, OffsetDateTime now) {
        if (studentId == null) {
            throw new BusinessException("VALIDATION_FAILED", "Student is required", HttpStatus.BAD_REQUEST);
        }
        PrivateVehicle vehicle = new PrivateVehicle();
        vehicle.studentId = studentId;
        vehicle.licensePlate = normalisePlate(licensePlate);
        vehicle.status = Status.ACTIVE;
        vehicle.createdAt = now;
        vehicle.update(type, model, batteryLevel);
        return vehicle;
    }

    /** Sua thong tin xe; bien so khong doi duoc vi la khoa duy nhat. */
    public void update(VehicleType type, String newModel, Integer newBatteryLevel) {
        if (type == null) {
            throw new BusinessException("VALIDATION_FAILED", "Vehicle type is required", HttpStatus.BAD_REQUEST);
        }
        String trimmed = newModel == null ? "" : newModel.trim();
        if (trimmed.isEmpty() || trimmed.length() > 60) {
            throw new BusinessException("VALIDATION_FAILED", "Model must have 1-60 characters", HttpStatus.BAD_REQUEST);
        }
        if (newBatteryLevel != null && (newBatteryLevel < 0 || newBatteryLevel > 100)) {
            throw new BusinessException("VALIDATION_FAILED", "Battery level must be between 0 and 100",
                    HttpStatus.BAD_REQUEST);
        }
        this.vehicleType = type;
        this.model = trimmed;
        this.batteryLevel = newBatteryLevel;
    }

    /** "Xoa" xe = ngung su dung: lich su dat cho van tham chieu toi xe nen khong xoa dong. */
    public void deactivate() {
        this.status = Status.INACTIVE;
    }

    public void activate() {
        this.status = Status.ACTIVE;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isOwnedBy(Long userId) {
        return studentId.equals(userId);
    }

    public static String normalisePlate(String raw) {
        String plate = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (!PLATE.matcher(plate).matches()) {
            throw new BusinessException("INVALID_LICENSE_PLATE",
                    "License plate must have 5-20 letters or digits (dots and dashes allowed)", HttpStatus.BAD_REQUEST);
        }
        return plate;
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public String getLicensePlate() { return licensePlate; }
    public VehicleType getVehicleType() { return vehicleType; }
    public String getModel() { return model; }
    public Integer getBatteryLevel() { return batteryLevel; }
    public Status getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
