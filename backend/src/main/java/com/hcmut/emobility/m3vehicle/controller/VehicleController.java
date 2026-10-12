package com.hcmut.emobility.m3vehicle.controller;

import com.hcmut.emobility.m3vehicle.dto.CurrentTripResponse;
import com.hcmut.emobility.m3vehicle.dto.ReturnVehicleRequest;
import com.hcmut.emobility.m3vehicle.dto.VehicleResponse;
import com.hcmut.emobility.m3vehicle.repository.RentalVehicleRepository;
import com.hcmut.emobility.m3vehicle.service.VehicleService;
import com.hcmut.emobility.shared.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.security.Principal;
import com.hcmut.emobility.shared.auth.AppUserRepository;

/** Quy uoc duong dan: /api/m<so>/<tai-nguyen> */
@RestController
@RequestMapping("/api/m3/vehicles")
public class VehicleController {

    private final VehicleService service;
    private final AppUserRepository users;
    private final RentalVehicleRepository repository;

    public VehicleController(VehicleService service, RentalVehicleRepository repository, AppUserRepository users) {
        this.service = service;
        this.repository = repository;
        this.users = users;
    }

    /** Danh sach tat ca xe */
    @GetMapping
    public ApiResponse<List<VehicleResponse>> list() {
        List<VehicleResponse> vehicles = repository.findAll().stream()
                .map(VehicleResponse::from)
                .toList();
        return ApiResponse.success(vehicles, "Vehicles retrieved");
    }

    /** FR-M3-03: Xem chuyen di hien tai cua user dang dang nhap */
    @GetMapping("/current-trip")
    public ApiResponse<CurrentTripResponse> currentTrip(
            Principal principal) {
        CurrentTripResponse trip = service.getCurrentTrip(userId(principal));
        return ApiResponse.success(trip, "Current trip retrieved");
    }

    /** FR-M3-01: Nhan xe (RESERVED -> IN_USE) */
    @PostMapping("/{id}/pick-up")
    public ApiResponse<Void> pickUp(@PathVariable Long id,
                                    Principal principal) {
        service.pickUp(id, userId(principal));
        return ApiResponse.success(null, "Vehicle picked up");
    }

    /** FR-M3-02: Tra xe tai Hub */
    @PostMapping("/{id}/return")
    public ApiResponse<Void> returnVehicle(@PathVariable Long id,
                                           @Valid @RequestBody ReturnVehicleRequest request, Principal principal) {
        service.returnVehicle(id, request.hubId(), request.batteryLevel(), userId(principal));
        return ApiResponse.success(null, "Vehicle returned");
    }
    private Long userId(Principal principal) {
        return users.findByUsername(principal.getName()).orElseThrow().getId();
    }
}
