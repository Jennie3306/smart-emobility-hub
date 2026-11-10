package com.hcmut.emobility.m2reservation.service;

import com.hcmut.emobility.m2reservation.domain.CancelReason;
import com.hcmut.emobility.m2reservation.domain.Reservation;
import com.hcmut.emobility.m2reservation.domain.ReservationState;
import com.hcmut.emobility.m2reservation.domain.ResourceType;
import com.hcmut.emobility.m2reservation.repository.ReservationRepository;
import com.hcmut.emobility.m2reservation.repository.VehicleSearchRepository;
import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;
import com.hcmut.emobility.m3vehicle.domain.VehicleState;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.ReservationCancelledEvent;
import com.hcmut.emobility.shared.events.ReservationExpiredEvent;
import com.hcmut.emobility.shared.events.ReservationConfirmedEvent;
import com.hcmut.emobility.shared.events.ReservationCheckedInEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
public class ReservationService implements ReservationCore {

    private final ReservationRepository repository;
    private final VehicleSearchRepository vehicles;
    private final EventPublisher publisher;
    private final Clock clock;

    @Value("${app.reservation.hold-minutes}")
    private int holdMinutes;   // BR-M2-02

    public ReservationService(ReservationRepository repository, VehicleSearchRepository vehicles,
                              EventPublisher publisher, Clock clock) {
        this.repository = repository;
        this.vehicles = vehicles;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Reserves a vehicle for a student (FR-M2-03).
     * Pre-condition: the vehicle exists and is AVAILABLE (BR-M2-04).
     * Post-condition: same as reserve().
     * Throws: VEHICLE_NOT_FOUND (404), VEHICLE_NOT_AVAILABLE and the errors of reserve() (409).
     */
    @Transactional
    public Reservation reserveVehicle(Long userId, Long vehicleId) {
        RentalVehicle vehicle = vehicles.findById(vehicleId).orElseThrow(() ->
                new BusinessException("VEHICLE_NOT_FOUND", "Vehicle not found: " + vehicleId, HttpStatus.NOT_FOUND));
        if (vehicle.getState() != VehicleState.AVAILABLE) {
            throw new BusinessException("VEHICLE_NOT_AVAILABLE",
                    "Vehicle state is " + vehicle.getState() + "; booking is unavailable");
        }
        return reserve(userId, ResourceType.VEHICLE, vehicleId, vehicle.getCurrentHubId());
    }

    /**
     * Reserves a resource - M2 calls it for vehicles, M4 for parking spaces (FR-M2-03, FR-M2-06).
     * Pre-condition: the user has no active reservation of this type (BR-M2-03); the resource is not reserved (BR-M2-01).
     * Post-condition: Reservation is CONFIRMED with expiresAt = now + holdMinutes.
     * Throws: RESERVATION_LIMIT_EXCEEDED, RESOURCE_CONFLICT (409).
     */
    @Override
    @Transactional
    public Reservation reserve(Long userId, ResourceType type, Long resourceId, Long hubId) {
        // These pre-checks only pick the right error code. The UNIQUE constraint in saveActive decides who wins.
        if (findActive(userId, type).isPresent()) {
            throw new BusinessException("RESERVATION_LIMIT_EXCEEDED", "You already have an active reservation");
        }
        if (repository.findFirstByResourceTypeAndResourceIdAndStateIn(
                type, resourceId, ReservationState.ACTIVE).isPresent()) {
            throw new BusinessException("RESOURCE_CONFLICT", "This resource was just reserved by another user");
        }

        OffsetDateTime now = now();
        Reservation reservation = Reservation.create(userId, type, resourceId, hubId, now);
        reservation.confirm(now, holdMinutes);
        saveActive(reservation);
        publisher.publish(new ReservationConfirmedEvent(reservation.getId(),userId,type.name(),resourceId,hubId));
        return reservation;
    }

    /**
     * A student cancels their own reservation (FR-M2-04, BR-M2-05).
     * Throws: ACCESS_DENIED (403), RESERVATION_EXPIRED, ALREADY_CHECKED_IN, INVALID_STATE_TRANSITION (409).
     */
    @Override
    @Transactional
    public Reservation cancel(Long reservationId, Long requesterId) {
        Reservation reservation = getOwned(reservationId, requesterId);
        OffsetDateTime now = now();
        if (reservation.getState() == ReservationState.EXPIRED
                || (reservation.getState() == ReservationState.CONFIRMED && reservation.isOverdue(now))) {
            throw new BusinessException("RESERVATION_EXPIRED", "Reservation has expired");
        }
        if (reservation.getState() == ReservationState.CHECKED_IN) {
            throw new BusinessException("ALREADY_CHECKED_IN", "You already picked up the vehicle. Return it to finish the trip.");
        }
        reservation.cancel(CancelReason.USER_REQUEST, now);
        saveActive(reservation);
        publisher.publish(new ReservationCancelledEvent(reservation.getId(),
                reservation.getResourceType().name(), reservation.getResourceId()));
        return reservation;
    }

    /** Pick up the vehicle / parking space: CONFIRMED -> CHECKED_IN (BR-M2-07). Throws: ACCESS_DENIED, RESERVATION_EXPIRED. */
    @Override
    @Transactional
    public Reservation checkIn(Long reservationId, Long requesterId) {
        Reservation reservation = getOwned(reservationId, requesterId);
        reservation.checkIn(now());
        saveActive(reservation);
        publisher.publish(new ReservationCheckedInEvent(reservation.getId(),requesterId,reservation.getResourceType().name(),reservation.getResourceId(),reservation.getHubId()));
        return reservation;
    }

    /** FR-M2-09: on return, close the CHECKED_IN reservation of that resource. Does nothing if there is none. */
    @Override
    @Transactional
    public Optional<Reservation> completeByResource(ResourceType type, Long resourceId) {
        Optional<Reservation> found = repository.findFirstByResourceTypeAndResourceIdAndStateIn(
                type, resourceId, EnumSet.of(ReservationState.CHECKED_IN));
        found.ifPresent(reservation -> {
            reservation.complete(now());
            saveActive(reservation);
        });
        return found;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reservation> findActive(Long userId, ResourceType type) {
        return repository.findFirstByUserIdAndResourceTypeAndStateIn(userId, type, ReservationState.ACTIVE);
    }

    /** Reads a reservation owned by the caller. Throws: RESERVATION_NOT_FOUND (404), ACCESS_DENIED (403). */
    @Transactional(readOnly = true)
    public Reservation getOwned(Long reservationId, Long requesterId) {
        Reservation reservation = repository.findById(reservationId).orElseThrow(() ->
                new BusinessException("RESERVATION_NOT_FOUND", "Reservation not found: " + reservationId,
                        HttpStatus.NOT_FOUND));
        if (!reservation.isOwnedBy(requesterId)) {
            throw new BusinessException("ACCESS_DENIED", "This reservation does not belong to you", HttpStatus.FORBIDDEN);
        }
        return reservation;
    }

    @Transactional(readOnly = true)
    public List<Long> findOverdueIds() {
        return repository.findOverdueIds(now());
    }

    /**
     * FR-M2-05 (actor Timer): expires one reservation; each call runs in its own transaction.
     * Pre-condition: the reservation is CONFIRMED and now >= expiresAt.
     * Post-condition: EXPIRED, keys released, ReservationExpiredEvent published. Returns false if the state just changed.
     * Throws: CONCURRENT_UPDATE when the student checks in at the same moment (optimistic lock).
     */
    @Transactional
    public boolean expire(Long reservationId) {
        Reservation reservation = repository.findById(reservationId).orElse(null);
        OffsetDateTime now = now();
        if (reservation == null || reservation.getState() != ReservationState.CONFIRMED
                || !reservation.isOverdue(now)) {
            return false;
        }
        reservation.expire(now);
        saveActive(reservation);
        publisher.publish(new ReservationExpiredEvent(reservation.getId(),
                reservation.getResourceType().name(), reservation.getResourceId()));
        return true;
    }

    /**
     * FR-M2-08 (actor IoT Simulator): a vehicle fails while its reservation is CONFIRMED.
     * Pre-condition: a CONFIRMED reservation exists on the failed vehicle (if CHECKED_IN, M3 handles the trip).
     * Post-condition: switched to a vehicle at the same hub with the same type, expiresAt kept (BR-M2-08);
     *                 with no replacement, CANCELLED with VEHICLE_FAILED and ReservationCancelledEvent published.
     * Throws: nothing - returns an empty Optional when no reservation matches (handling the same event again changes nothing).
     */
    @Transactional
    public Optional<Reservation> handleVehicleFailed(Long vehicleId) {
        Optional<Reservation> found = repository.findFirstByResourceTypeAndResourceIdAndStateIn(
                ResourceType.VEHICLE, vehicleId, EnumSet.of(ReservationState.CONFIRMED));
        found.ifPresent(reservation -> {
            Optional<RentalVehicle> replacement = vehicles.findById(vehicleId)
                    .flatMap(failed -> vehicles.findReplacements(reservation.getHubId(), failed.getType(), vehicleId)
                            .stream().findFirst());
            if (replacement.isPresent()) {
                reservation.reassignResource(replacement.get().getId());
                saveActive(reservation);
                publisher.publish(new ReservationConfirmedEvent(reservation.getId(),reservation.getUserId(),ResourceType.VEHICLE.name(),replacement.get().getId(),reservation.getHubId()));
            } else {
                reservation.cancel(CancelReason.VEHICLE_FAILED, now());
                saveActive(reservation);
                publisher.publish(new ReservationCancelledEvent(reservation.getId(),
                        ResourceType.VEHICLE.name(), vehicleId));
            }
        });
        return found;
    }

    /**
     * Saves and flushes immediately so database errors surface here.
     * The UNIQUE constraints on active_resource_key / active_user_key are the last line of defence when two
     * parallel requests both pass the pre-checks - the database lets only one row win.
     */
    private Reservation saveActive(Reservation reservation) {
        try {
            return repository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("RESOURCE_CONFLICT", "This resource was just reserved by another user");
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException("CONCURRENT_UPDATE", "Reservation changed. Please refresh.");
        } catch (ConcurrencyFailureException e) {
            // Hai giao dich cung chen vao mot khoa UNIQUE: tuy CSDL co the bao khoa/timeout
            // (H2) thay vi trung khoa (PostgreSQL). Ca hai deu la tranh chap tai nguyen.
            throw new BusinessException("RESOURCE_CONFLICT", "This resource was just reserved by another user");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
    /** Reconcile the legacy direct M3 pickup endpoint without invoking another module's service. */
    @Transactional
    public void pickedUp(Long vehicleId,Long userId) {
        var r=repository.findFirstByResourceTypeAndResourceIdAndStateIn(ResourceType.VEHICLE,vehicleId,EnumSet.of(ReservationState.CONFIRMED));
        r.ifPresent(reservation -> {
            if(!reservation.isOwnedBy(userId)) throw new BusinessException("ACCESS_DENIED","Reservation owner mismatch",HttpStatus.FORBIDDEN);
            reservation.checkIn(now());saveActive(reservation);
        });
    }
}
