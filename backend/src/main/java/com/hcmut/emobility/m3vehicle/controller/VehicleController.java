package com.hcmut.emobility.m3vehicle.controller;

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

    @GetMapping
    public ApiResponse<List<VehicleResponse>> list() {
        List<VehicleResponse> vehicles = repository.findAll().stream()
                .map(VehicleResponse::from)
                .toList();
        return ApiResponse.success(vehicles, "Lay danh sach xe thanh cong");
    }

    @PostMapping("/{id}/pick-up")
    public ApiResponse<Void> pickUp(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        service.pickUp(id, userId);
        // Trả về data null kèm thông báo thành công
        return ApiResponse.success(null, "Nhan xe thanh cong");
    }

    @PostMapping("/{id}/return")
    public ApiResponse<Void> returnVehicle(@PathVariable Long id,
                                           @Valid @RequestBody ReturnVehicleRequest request) {
        service.returnVehicle(id, request.hubId(), request.batteryLevel());
        return ApiResponse.success(null, "Tra xe thanh cong");
    }
}