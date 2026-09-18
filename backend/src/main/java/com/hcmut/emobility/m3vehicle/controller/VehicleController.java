package com.hcmut.emobility.m3vehicle.controller;

import com.hcmut.emobility.m3vehicle.dto.ReturnVehicleRequest;
import com.hcmut.emobility.m3vehicle.dto.VehicleResponse;
import com.hcmut.emobility.m3vehicle.repository.RentalVehicleRepository;
import com.hcmut.emobility.m3vehicle.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
    public List<VehicleResponse> list() {
        return repository.findAll().stream().map(VehicleResponse::from).toList();
    }

    @PostMapping("/{id}/pick-up")
    public ResponseEntity<Void> pickUp(@PathVariable Long id) {
        service.pickUp(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/return")
    public ResponseEntity<Void> returnVehicle(@PathVariable Long id,
                                              @Valid @RequestBody ReturnVehicleRequest request) {
        service.returnVehicle(id, request.hubId(), request.batteryLevel());
        return ResponseEntity.noContent().build();
    }
}
