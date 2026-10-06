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

/** Quy uoc duong dan: /api/m<so>/<tai-nguyen> */
@RestController
@RequestMapping("/api/m3/vehicles")
public class VehicleController {

    private final VehicleService service;
    private final RentalVehicleRepository repository;

    public VehicleController(VehicleService service, RentalVehicleRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    /** Danh sach tat ca xe */
    @GetMapping
    public ApiResponse<List<VehicleResponse>> list() {
        List<VehicleResponse> vehicles = repository.findAll().stream()
                .map(VehicleResponse::from)
                .toList();
        return ApiResponse.success(vehicles, "Lay danh sach xe thanh cong");
    }

    /** FR-M3-03: Xem chuyen di hien tai cua user dang dang nhap */
    @GetMapping("/current-trip")
    public ApiResponse<CurrentTripResponse> currentTrip(
            @RequestHeader("X-User-Id") Long userId) {
        CurrentTripResponse trip = service.getCurrentTrip(userId);
        return ApiResponse.success(trip, "Lay thong tin chuyen di thanh cong");
    }

    /** FR-M3-01: Nhan xe (RESERVED -> IN_USE) */
    @PostMapping("/{id}/pick-up")
    public ApiResponse<Void> pickUp(@PathVariable Long id,
                                    @RequestHeader("X-User-Id") Long userId) {
        service.pickUp(id, userId);
        return ApiResponse.success(null, "Nhan xe thanh cong");
    }

    /** FR-M3-02: Tra xe tai Hub */
    @PostMapping("/{id}/return")
    public ApiResponse<Void> returnVehicle(@PathVariable Long id,
                                           @Valid @RequestBody ReturnVehicleRequest request) {
        service.returnVehicle(id, request.hubId(), request.batteryLevel());
        return ApiResponse.success(null, "Tra xe thanh cong");
    }
}