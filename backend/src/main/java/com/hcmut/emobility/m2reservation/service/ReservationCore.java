package com.hcmut.emobility.m2reservation.service;

import com.hcmut.emobility.m2reservation.domain.Reservation;
import com.hcmut.emobility.m2reservation.domain.ResourceType;

import java.util.Optional;

/**
 * The ONLY interface another module may call directly (M4 - SETUP.md section F).
 * Possible BusinessException codes: RESERVATION_LIMIT_EXCEEDED, RESOURCE_CONFLICT,
 * RESERVATION_EXPIRED, ALREADY_CHECKED_IN, INVALID_STATE_TRANSITION, CONCURRENT_UPDATE (409),
 * ACCESS_DENIED (403), RESERVATION_NOT_FOUND (404).
 */
public interface ReservationCore {

    Reservation reserve(Long userId, ResourceType type, Long resourceId, Long hubId);

    Reservation cancel(Long reservationId, Long requesterId);

    Reservation checkIn(Long reservationId, Long requesterId);

    Optional<Reservation> completeByResource(ResourceType type, Long resourceId);

    Optional<Reservation> findActive(Long userId, ResourceType type);
}
