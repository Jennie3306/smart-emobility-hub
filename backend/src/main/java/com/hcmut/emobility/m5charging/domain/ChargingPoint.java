package com.hcmut.emobility.m5charging.domain;

import com.hcmut.emobility.shared.common.BusinessException;
import org.springframework.http.HttpStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** M5 owns port assignment; M6 remains the source of equipment-health incidents. */
@Entity
@Table(name = "charging_point")
public class ChargingPoint {

    @Id
    private Long id; // Must match M1's CHARGING_POINT resource id.

    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargingPointState state;

    @Version
    private Long version;

    protected ChargingPoint() { }

    public ChargingPoint(Long id, Long hubId, ChargingPointState state) {
        this.id = id;
        this.hubId = hubId;
        this.state = state;
    }

    public boolean isOperational() {
        return state == ChargingPointState.AVAILABLE
                || state == ChargingPointState.RESERVED
                || state == ChargingPointState.IN_USE;
    }

    public boolean isAvailable() { return state == ChargingPointState.AVAILABLE; }

    public void reserve() {
        if (state != ChargingPointState.AVAILABLE) {
            throw new BusinessException("CHARGING_POINT_BUSY", "Charging point is not available: " + id, HttpStatus.CONFLICT);
        }
        state = ChargingPointState.RESERVED;
    }

    public void startCharging() {
        if (state != ChargingPointState.RESERVED && state != ChargingPointState.AVAILABLE) {
            throw new BusinessException("CHARGING_POINT_BUSY", "Charging point is not ready: " + id, HttpStatus.CONFLICT);
        }
        state = ChargingPointState.IN_USE;
    }

    public void release() {
        if (state != ChargingPointState.FAULTY && state != ChargingPointState.UNDER_REPAIR) {
            state = ChargingPointState.AVAILABLE;
        }
    }

    public void markFaulty() { state = ChargingPointState.FAULTY; }
    public void markUnderRepair() { state = ChargingPointState.UNDER_REPAIR; }

    /**
     * Recovery transition: FAULTY or UNDER_REPAIR -> AVAILABLE once M6 reports the device healthy.
     * {@link #release()} deliberately keeps a broken port broken, so recovery needs its own method.
     *
     * @return true when the port has just come back into service.
     */
    public boolean recover() {
        if (state != ChargingPointState.FAULTY && state != ChargingPointState.UNDER_REPAIR) return false;
        state = ChargingPointState.AVAILABLE;
        return true;
    }

    /**
     * Applies equipment-health telemetry from M6 (DeviceReadingReportedEvent, Appendix A v2). Only health is
     * taken from outside: RESERVED and IN_USE are M5's own assignment decisions, so an AVAILABLE or IN_USE
     * reading never overwrites a port that M5 has reserved or started.
     *
     * @return true when a FAULTY or UNDER_REPAIR port has just become AVAILABLE again.
     */
    public boolean applyExternalState(String value) {
        if (value == null) return false;
        switch (value.trim().toUpperCase()) {
            case "AVAILABLE", "FREE", "IDLE" -> { return recover(); }
            case "FAULTY" -> markFaulty();
            case "UNDER_REPAIR" -> markUnderRepair();
            default -> { /* RESERVED, IN_USE and unknown vocabulary: M5 owns assignment, ignore. */ }
        }
        return false;
    }

    public Long getId() { return id; }
    public Long getHubId() { return hubId; }
    public ChargingPointState getState() { return state; }
}
