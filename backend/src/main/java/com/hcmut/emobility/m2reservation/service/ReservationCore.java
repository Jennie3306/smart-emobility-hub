package com.hcmut.emobility.m2reservation.service;

import com.hcmut.emobility.m2reservation.domain.Reservation;
import com.hcmut.emobility.m2reservation.domain.ResourceType;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * The ONLY interface another module may call directly (M4 - SETUP.md section F).
 * Possible BusinessException codes: RESERVATION_LIMIT_EXCEEDED, RESOURCE_CONFLICT,
 * RESERVATION_EXPIRED, ALREADY_CHECKED_IN, INVALID_STATE_TRANSITION, CONCURRENT_UPDATE (409),
 * ACCESS_DENIED (403), RESERVATION_NOT_FOUND (404).
 */
public interface ReservationCore {

    Reservation reserve(Long userId, ResourceType type, Long resourceId, Long hubId);

    /**
     * M4 (FR-M4-02): reserves a resource for a booked time window. The hold ends at checkInDeadline
     * (BR-M4-04: slot start + 15 minutes) instead of now + hold-minutes, so a booking for later today is
     * not expired by the Timer before it starts. Same errors as reserve(), plus INVALID_TIME_SLOT (400).
     */
    Reservation reserveWindow(Long userId, ResourceType type, Long resourceId, Long hubId,
                              OffsetDateTime startAt, OffsetDateTime endAt, OffsetDateTime checkInDeadline);

    /**
     * BR-M4-06: the reserved resource was blocked by an incident. A CONFIRMED reservation is cancelled with
     * RESOURCE_BLOCKED and ReservationCancelledEvent is published. Empty when there is nothing to cancel
     * (already closed, or CHECKED_IN - the vehicle is already parked there).
     */
    Optional<Reservation> cancelForBlockedResource(Long reservationId);

    Reservation cancel(Long reservationId, Long requesterId);

    Reservation checkIn(Long reservationId, Long requesterId);

    Optional<Reservation> completeByResource(ResourceType type, Long resourceId);

    Optional<Reservation> findActive(Long userId, ResourceType type);
}
