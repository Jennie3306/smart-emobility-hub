package com.hcmut.emobility.m3vehicle.domain;

import com.hcmut.emobility.shared.common.BusinessException;
import jakarta.persistence.*;

@Entity
@Table(name = "rental_vehicle")
public class RentalVehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleType type;

    @Column(name = "battery_level", nullable = false)
    private int batteryLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleState state;

    @Column(name = "current_hub_id")
    private Long currentHubId;

    @Version
    private Long version;   // optimistic lock - chong cap nhat dong thoi

    protected RentalVehicle() { }

    public void changeState(VehicleState newState) {
        if (!this.state.canMoveTo(newState)) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Khong the chuyen xe tu " + state + " sang " + newState);
        }
        this.state = newState;
    }

    public void updateBattery(int level) {
        this.batteryLevel = Math.max(0, Math.min(100, level));
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public VehicleType getType() { return type; }
    public int getBatteryLevel() { return batteryLevel; }
    public VehicleState getState() { return state; }
    public Long getCurrentHubId() { return currentHubId; }
    public void setCurrentHubId(Long hubId) { this.currentHubId = hubId; }
}