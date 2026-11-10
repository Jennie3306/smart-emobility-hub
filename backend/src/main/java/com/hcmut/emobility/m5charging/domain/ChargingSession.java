package com.hcmut.emobility.m5charging.domain;

import com.hcmut.emobility.shared.common.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.OffsetDateTime;

/** Aggregate root for a charging request, queue entry or scheduled charging slot. */
@Entity
@Table(name = "charging_session", indexes = {
        @Index(name = "idx_m5_session_hub_state", columnList = "hub_id,status,created_at"),
        @Index(name = "idx_m5_session_point_state", columnList = "charging_point_id,status")
})
public class ChargingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId; // null for automatic low-battery requests created by M3 events.

    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    @Column(name = "parking_space_id")
    private Long parkingSpaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_kind", nullable = false, length = 20)
    private VehicleKind vehicleKind;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "battery_level", nullable = false)
    private int batteryLevel;

    @Column(name = "start_battery_level", nullable = false)
    private int startBatteryLevel;

    @Column(name = "target_battery_level", nullable = false)
    private int targetBatteryLevel = 90;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargingSessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_level", nullable = false, length = 20)
    private PriorityLevel priorityLevel = PriorityLevel.NORMAL;

    @Column(name = "priority_score", nullable = false)
    private double priorityScore;

    @Column(name = "failure_bonus", nullable = false)
    private int failureBonus;

    @Column(name = "active_vehicle_key", unique = true, length = 80)
    private String activeVehicleKey;

    @Column(name = "charging_point_id")
    private Long chargingPointId;

    @Column(name = "expected_use_at")
    private OffsetDateTime expectedUseAt;

    @Column(name = "scheduled_start_at")
    private OffsetDateTime scheduledStartAt;

    @Column(name = "scheduled_end_at")
    private OffsetDateTime scheduledEndAt;

    @Column(name = "waiting_since", nullable = false)
    private OffsetDateTime waitingSince;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "hold_expires_at")
    private OffsetDateTime holdExpiresAt;

    @Column(name = "charging_started_at")
    private OffsetDateTime chargingStartedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Version
    private Long version;

    protected ChargingSession() { }

    public static ChargingSession request(Long userId, Long hubId, Long parkingSpaceId,
                                           VehicleKind vehicleKind, Long vehicleId,
                                           int batteryLevel, OffsetDateTime expectedUseAt,
                                           OffsetDateTime now) {
        ChargingSession session = new ChargingSession();
        session.userId = userId;
        session.hubId = hubId;
        session.parkingSpaceId = parkingSpaceId;
        session.vehicleKind = vehicleKind;
        session.vehicleId = vehicleId;
        session.batteryLevel = batteryLevel;
        session.startBatteryLevel = batteryLevel;
        session.expectedUseAt = expectedUseAt;
        session.status = ChargingSessionStatus.REQUESTED;
        session.priorityLevel = PriorityLevel.NORMAL;
        session.waitingSince = now;
        session.createdAt = now;
        session.activeVehicleKey = vehicleKind + ":" + vehicleId;
        return session;
    }

    public static ChargingSession schedule(Long userId, Long hubId, Long parkingSpaceId,
                                            VehicleKind vehicleKind, Long vehicleId,
                                            int batteryLevel, OffsetDateTime startAt,
                                            int durationMinutes, Long chargingPointId,
                                            OffsetDateTime now) {
        ChargingSession session = request(userId, hubId, parkingSpaceId, vehicleKind,
                vehicleId, batteryLevel, startAt, now);
        session.scheduledStartAt = startAt;
        session.scheduledEndAt = startAt.plusMinutes(durationMinutes);
        session.assign(chargingPointId, now, startAt);
        return session;
    }

    public void enqueue(OffsetDateTime now) {
        if (status != ChargingSessionStatus.REQUESTED && status != ChargingSessionStatus.INTERRUPTED) {
            throw invalid("Only REQUESTED or INTERRUPTED sessions can enter the queue");
        }
        status = ChargingSessionStatus.QUEUED;
        if (waitingSince == null) waitingSince = now;
    }

    public void assign(Long pointId, OffsetDateTime now, OffsetDateTime startAt) {
        if (status != ChargingSessionStatus.REQUESTED && status != ChargingSessionStatus.QUEUED
                && status != ChargingSessionStatus.INTERRUPTED) {
            throw invalid("Only waiting sessions can be scheduled");
        }
        status = ChargingSessionStatus.SCHEDULED;
        chargingPointId = pointId;
        assignedAt = now;
        if (startAt != null) scheduledStartAt = startAt;
        OffsetDateTime holdFrom = scheduledStartAt == null ? now : scheduledStartAt;
        holdExpiresAt = holdFrom.plusMinutes(10);
    }

    public void start(OffsetDateTime now) {
        if (status != ChargingSessionStatus.SCHEDULED) {
            throw invalid("Only SCHEDULED sessions can start charging");
        }
        if (holdExpiresAt != null && !now.isBefore(holdExpiresAt)) {
            throw new BusinessException("CHARGING_HOLD_EXPIRED", "The 10-minute charging-point hold has expired");
        }
        if (scheduledStartAt != null && now.isBefore(scheduledStartAt)) {
            throw new BusinessException("TOO_EARLY", "The scheduled charging window has not started yet");
        }
        status = ChargingSessionStatus.CHARGING;
        chargingStartedAt = now;
        startBatteryLevel = batteryLevel;
        holdExpiresAt = null;
    }

    public void updateBatteryForElapsedTime(OffsetDateTime now) {
        if (status != ChargingSessionStatus.CHARGING || chargingStartedAt == null) return;
        long elapsedMinutes = Math.max(0, Duration.between(chargingStartedAt, now).toMinutes());
        batteryLevel = (int) Math.min(targetBatteryLevel, startBatteryLevel + elapsedMinutes);
    }

    public boolean shouldComplete(OffsetDateTime now) {
        if (status != ChargingSessionStatus.CHARGING || chargingStartedAt == null) return false;
        return batteryLevel >= targetBatteryLevel
                || Duration.between(chargingStartedAt, now).toMinutes() >= 90
                || (scheduledEndAt != null && !now.isBefore(scheduledEndAt));
    }

    public void complete(OffsetDateTime now) {
        if (status != ChargingSessionStatus.CHARGING) throw invalid("Only CHARGING sessions can complete");
        status = ChargingSessionStatus.COMPLETED;
        completedAt = now;
        activeVehicleKey = null;
        chargingPointId = null;
        holdExpiresAt = null;
    }

    public void interrupt(OffsetDateTime now) {
        if (status != ChargingSessionStatus.CHARGING && status != ChargingSessionStatus.SCHEDULED) {
            throw invalid("Only CHARGING or SCHEDULED sessions can be interrupted");
        }
        status = ChargingSessionStatus.INTERRUPTED;
        chargingPointId = null;
        holdExpiresAt = null;
        // A failed scheduled slot is converted into an immediate queue request.
        // Clear its old window so a past hold cannot expire the replacement assignment.
        scheduledStartAt = null;
        scheduledEndAt = null;
        failureBonus = 0; // Applied only if the failed session remains queued after reassignment.
        if (waitingSince == null) waitingSince = now;
    }

    public void applyFailureBonus() {
        if (status != ChargingSessionStatus.QUEUED) {
            throw invalid("Failure compensation applies only to a session still waiting in the queue");
        }
        failureBonus = 20;
    }

    public void cancel(OffsetDateTime now) {
        if (status == ChargingSessionStatus.CHARGING) {
            throw invalid("A CHARGING session cannot be cancelled");
        }
        if (status != ChargingSessionStatus.REQUESTED && status != ChargingSessionStatus.QUEUED
                && status != ChargingSessionStatus.SCHEDULED && status != ChargingSessionStatus.INTERRUPTED) {
            throw invalid("This session is already finished");
        }
        status = ChargingSessionStatus.CANCELLED;
        cancelledAt = now;
        activeVehicleKey = null;
        chargingPointId = null;
        holdExpiresAt = null;
    }

    public void changePriority(PriorityLevel next) {
        if (status == ChargingSessionStatus.CHARGING || status.isTerminal()) {
            throw invalid("Priority cannot be changed after charging has started or finished");
        }
        priorityLevel = next;
    }

    public void moveSchedule(OffsetDateTime startAt, int durationMinutes, Long nextPointId) {
        if (status != ChargingSessionStatus.SCHEDULED) {
            throw invalid("Only SCHEDULED sessions can be moved");
        }
        scheduledStartAt = startAt;
        scheduledEndAt = startAt.plusMinutes(durationMinutes);
        holdExpiresAt = startAt.plusMinutes(10);
        expectedUseAt = startAt;
        chargingPointId = nextPointId;
    }

    public void setPriorityScore(double value) { priorityScore = value; }
    public void setPriorityLevel(PriorityLevel value) { priorityLevel = value; }
    public void requeueAfterFailure(OffsetDateTime now) {
        if (status != ChargingSessionStatus.INTERRUPTED) throw invalid("Only INTERRUPTED sessions can be re-queued");
        status = ChargingSessionStatus.QUEUED;
        if (waitingSince == null) waitingSince = now;
    }

    private BusinessException invalid(String message) {
        return new BusinessException("INVALID_STATE_TRANSITION", message);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getHubId() { return hubId; }
    public Long getParkingSpaceId() { return parkingSpaceId; }
    public VehicleKind getVehicleKind() { return vehicleKind; }
    public Long getVehicleId() { return vehicleId; }
    public int getBatteryLevel() { return batteryLevel; }
    public int getStartBatteryLevel() { return startBatteryLevel; }
    public ChargingSessionStatus getStatus() { return status; }
    public PriorityLevel getPriorityLevel() { return priorityLevel; }
    public double getPriorityScore() { return priorityScore; }
    public int getFailureBonus() { return failureBonus; }
    public String getActiveVehicleKey() { return activeVehicleKey; }
    public Long getChargingPointId() { return chargingPointId; }
    public OffsetDateTime getExpectedUseAt() { return expectedUseAt; }
    public OffsetDateTime getScheduledStartAt() { return scheduledStartAt; }
    public OffsetDateTime getScheduledEndAt() { return scheduledEndAt; }
    public OffsetDateTime getWaitingSince() { return waitingSince; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public OffsetDateTime getHoldExpiresAt() { return holdExpiresAt; }
    public OffsetDateTime getChargingStartedAt() { return chargingStartedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
}
