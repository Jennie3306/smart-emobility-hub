package com.hcmut.emobility.m2reservation.dto;

import com.hcmut.emobility.m2reservation.domain.Reservation;

import java.time.OffsetDateTime;

/** remainingSeconds is computed on the server - the frontend counts down from it, independent of the user's clock. */
public record ReservationResponse(Long id, String resourceType, Long resourceId, String vehicleCode, Long hubId,
                                  String state, String cancelReason, OffsetDateTime expiresAt,
                                  long remainingSeconds) {
    public static ReservationResponse from(Reservation r, OffsetDateTime now, String vehicleCode) {
        return new ReservationResponse(r.getId(), r.getResourceType().name(), r.getResourceId(), vehicleCode,
                r.getHubId(), r.getState().name(),
                r.getCancelReason() == null ? null : r.getCancelReason().name(),
                r.getExpiresAt(), r.remainingSeconds(now));
    }
}
