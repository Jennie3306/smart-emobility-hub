package com.hcmut.emobility.m2reservation.controller;

import com.hcmut.emobility.m2reservation.domain.Reservation;
import com.hcmut.emobility.m2reservation.domain.ResourceType;
import com.hcmut.emobility.m2reservation.dto.ReservationResponse;
import com.hcmut.emobility.m2reservation.dto.ReserveVehicleRequest;
import com.hcmut.emobility.m2reservation.service.ReservationService;
import com.hcmut.emobility.m2reservation.service.VehicleSearchService;
import com.hcmut.emobility.shared.auth.AppUser;
import com.hcmut.emobility.shared.auth.AppUserRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/m2/reservations")
public class ReservationController {

    private final ReservationService service;
    private final VehicleSearchService vehicles;
    private final AppUserRepository users;
    private final Clock clock;

    public ReservationController(ReservationService service, VehicleSearchService vehicles,
                                 AppUserRepository users, Clock clock) {
        this.service = service;
        this.vehicles = vehicles;
        this.users = users;
        this.clock = clock;
    }

    /** FR-M2-03: 201 on success; 409 RESOURCE_CONFLICT / RESERVATION_LIMIT_EXCEEDED / VEHICLE_NOT_AVAILABLE. */
    @PostMapping
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveVehicleRequest request,
                                                       Authentication auth) {
        Reservation reservation = service.reserveVehicle(currentUserId(auth), request.vehicleId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(reservation));
    }

    /** FR-M2-07: the active vehicle reservation; 204 if there is none. */
    @GetMapping("/current")
    public ResponseEntity<ReservationResponse> current(Authentication auth) {
        return service.findActive(currentUserId(auth), ResourceType.VEHICLE)
                .map(r -> ResponseEntity.ok(toResponse(r)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Reads one of the caller's reservations - the frontend uses it to learn why a reservation just ended. */
    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable Long id, Authentication auth) {
        return toResponse(service.getOwned(id, currentUserId(auth)));
    }

    /** FR-M2-04: 403 ACCESS_DENIED; 409 RESERVATION_EXPIRED / ALREADY_CHECKED_IN / INVALID_STATE_TRANSITION. */
    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id, Authentication auth) {
        return toResponse(service.cancel(id, currentUserId(auth)));
    }

    /** Vehicle pick-up (option 2 of VD-07): 409 RESERVATION_EXPIRED when now >= expiresAt. */
    @PostMapping("/{id}/check-in")
    public ReservationResponse checkIn(@PathVariable Long id, Authentication auth) {
        return toResponse(service.checkIn(id, currentUserId(auth)));
    }

    /** userId comes from the login session - NEVER from the body/header, so nobody can book for someone else (NFR-SEC-01). */
    private Long currentUserId(Authentication auth) {
        return users.findByUsername(auth.getName()).map(AppUser::getId).orElseThrow(() ->
                new BusinessException("UNAUTHENTICATED", "Phien dang nhap khong hop le", HttpStatus.UNAUTHORIZED));
    }

    private ReservationResponse toResponse(Reservation r) {
        String code = r.getResourceType() == ResourceType.VEHICLE ? vehicles.findCode(r.getResourceId()) : null;
        return ReservationResponse.from(r, OffsetDateTime.now(clock), code);
    }
}
