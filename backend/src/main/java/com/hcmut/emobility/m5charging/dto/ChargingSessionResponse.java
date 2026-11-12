package com.hcmut.emobility.m5charging.dto;

import com.hcmut.emobility.m5charging.domain.ChargingSession;
import com.hcmut.emobility.m5charging.domain.ChargingSessionStatus;
import com.hcmut.emobility.m5charging.domain.PriorityLevel;
import com.hcmut.emobility.m5charging.domain.VehicleKind;

import java.time.OffsetDateTime;

public record ChargingSessionResponse(
        Long id,
        Long userId,
        Long hubId,
        Long parkingSpaceId,
        VehicleKind vehicleKind,
        Long vehicleId,
        int batteryLevel,
        ChargingSessionStatus status,
        PriorityLevel priorityLevel,
        double priorityScore,
        Long chargingPointId,
        OffsetDateTime expectedUseAt,
        OffsetDateTime scheduledStartAt,
        OffsetDateTime scheduledEndAt,
        OffsetDateTime holdExpiresAt,
        OffsetDateTime createdAt,
        Integer queuePosition,
        Integer waitingMinutes,
        Integer estimatedWaitMinutes
) {
    public static ChargingSessionResponse from(ChargingSession session, Integer queuePosition,
                                                OffsetDateTime now) {
        int waiting = session.getWaitingSince() == null ? 0
                : (int) Math.max(0, java.time.Duration.between(session.getWaitingSince(), now).toMinutes());
        return new ChargingSessionResponse(session.getId(), session.getUserId(), session.getHubId(),
                session.getParkingSpaceId(), session.getVehicleKind(), session.getVehicleId(),
                session.getBatteryLevel(), session.getStatus(), session.getPriorityLevel(),
                session.getPriorityScore(), session.getChargingPointId(), session.getExpectedUseAt(),
                session.getScheduledStartAt(), session.getScheduledEndAt(), session.getHoldExpiresAt(),
                session.getCreatedAt(), queuePosition, waiting,
                queuePosition == null ? null : queuePosition * 15);
    }
}
