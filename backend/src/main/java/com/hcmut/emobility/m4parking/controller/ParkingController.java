package com.hcmut.emobility.m4parking.controller;

import com.hcmut.emobility.m4parking.dto.BookParkingRequest;
import com.hcmut.emobility.m4parking.dto.ParkingAvailability;
import com.hcmut.emobility.m4parking.dto.ParkingBookingResponse;
import com.hcmut.emobility.m4parking.dto.ParkingHubSummary;
import com.hcmut.emobility.m4parking.dto.ParkingSpaceResponse;
import com.hcmut.emobility.m4parking.dto.PrivateVehicleResponse;
import com.hcmut.emobility.m4parking.dto.RegisterVehicleRequest;
import com.hcmut.emobility.m4parking.dto.UpdateVehicleRequest;
import com.hcmut.emobility.m4parking.service.ParkingService;
import com.hcmut.emobility.shared.auth.AppUser;
import com.hcmut.emobility.shared.auth.AppUserRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * API cua M4. Cac API cua Operator (khoa / mo khoa cho, danh sach luot dat cua Hub) con duoc chan
 * trong SecurityConfig; requireOperator o day la lop bao ve thu hai.
 */
@RestController
@RequestMapping("/api/m4")
public class ParkingController {

    private final ParkingService parking;
    private final AppUserRepository users;

    public ParkingController(ParkingService parking, AppUserRepository users) {
        this.parking = parking;
        this.users = users;
    }

    // ---- Hub va cho do

    @GetMapping("/hubs")
    public List<ParkingHubSummary> hubs() {
        return parking.hubs();
    }

    @GetMapping("/hubs/{hubId}/spaces")
    public List<ParkingSpaceResponse> spaces(@PathVariable Long hubId) {
        return parking.spaces(hubId);
    }

    @GetMapping("/hubs/{hubId}/availability")
    public ParkingAvailability availability(
            @PathVariable Long hubId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endAt) {
        return parking.availability(hubId, startAt, endAt);
    }

    // ---- FR-M4-01 xe ca nhan

    @GetMapping("/vehicles/mine")
    public List<PrivateVehicleResponse> myVehicles(Authentication auth) {
        return parking.myVehicles(currentUserId(auth));
    }

    @PostMapping("/vehicles")
    public ResponseEntity<PrivateVehicleResponse> register(@RequestBody RegisterVehicleRequest body,
                                                           Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parking.registerVehicle(currentUserId(auth), body));
    }

    @PutMapping("/vehicles/{id}")
    public PrivateVehicleResponse update(@PathVariable Long id, @RequestBody UpdateVehicleRequest body,
                                         Authentication auth) {
        return parking.updateVehicle(currentUserId(auth), id, body);
    }

    @PostMapping("/vehicles/{id}/deactivate")
    public PrivateVehicleResponse deactivate(@PathVariable Long id, Authentication auth) {
        return parking.deactivateVehicle(currentUserId(auth), id);
    }

    @PostMapping("/vehicles/{id}/activate")
    public PrivateVehicleResponse activate(@PathVariable Long id, Authentication auth) {
        return parking.activateVehicle(currentUserId(auth), id);
    }

    // ---- FR-M4-02, FR-M4-03 luot dat

    @GetMapping("/bookings/mine")
    public List<ParkingBookingResponse> myBookings(Authentication auth) {
        return parking.myBookings(currentUserId(auth));
    }

    @PostMapping("/bookings")
    public ResponseEntity<ParkingBookingResponse> book(@RequestBody BookParkingRequest body, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parking.book(currentUserId(auth), body));
    }

    @PostMapping("/bookings/{id}/check-in")
    public ParkingBookingResponse checkIn(@PathVariable Long id, Authentication auth) {
        return parking.checkIn(currentUserId(auth), id);
    }

    @PostMapping("/bookings/{id}/check-out")
    public ParkingBookingResponse checkOut(@PathVariable Long id, Authentication auth) {
        return parking.checkOut(currentUserId(auth), id);
    }

    @PostMapping("/bookings/{id}/cancel")
    public ParkingBookingResponse cancel(@PathVariable Long id, Authentication auth) {
        return parking.cancel(currentUserId(auth), id);
    }

    // ---- Operator

    @GetMapping("/hubs/{hubId}/bookings")
    public List<ParkingBookingResponse> hubBookings(@PathVariable Long hubId, Authentication auth) {
        requireOperator(auth);
        return parking.hubBookings(hubId);
    }

    @PostMapping("/spaces/{id}/block")
    public ParkingSpaceResponse block(@PathVariable Long id, Authentication auth) {
        requireOperator(auth);
        return parking.blockByOperator(id);
    }

    @PostMapping("/spaces/{id}/unblock")
    public ParkingSpaceResponse unblock(@PathVariable Long id, Authentication auth) {
        requireOperator(auth);
        return parking.unblockByOperator(id);
    }

    private Long currentUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException("UNAUTHENTICATED", "Sign in to continue", HttpStatus.UNAUTHORIZED);
        }
        return users.findByUsername(auth.getName()).map(AppUser::getId).orElseThrow(() ->
                new BusinessException("UNAUTHENTICATED", "Invalid login session", HttpStatus.UNAUTHORIZED));
    }

    private void requireOperator(Authentication auth) {
        boolean operator = auth != null && auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        if (!operator) {
            throw new BusinessException("ACCESS_DENIED", "This operation requires an Operator account", HttpStatus.FORBIDDEN);
        }
    }
}
