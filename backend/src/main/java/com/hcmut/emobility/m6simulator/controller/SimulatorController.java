package com.hcmut.emobility.m6simulator.controller;

import com.hcmut.emobility.m6simulator.domain.SimulatorScenario;
import com.hcmut.emobility.m6simulator.dto.RunScenarioRequest;
import com.hcmut.emobility.m6simulator.dto.SimulationRunView;
import com.hcmut.emobility.m6simulator.dto.SimulatorStatusView;
import com.hcmut.emobility.m6simulator.service.DeviceSimulatorService;
import com.hcmut.emobility.m6simulator.service.SimulationAlreadyRunningException;
import com.hcmut.emobility.shared.common.ApiError;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/m6/simulator")
public class SimulatorController {

    private final DeviceSimulatorService service;

    public SimulatorController(DeviceSimulatorService service) {
        this.service = service;
    }

    @PostMapping("/scenarios/{scenario}")
    public SimulationRunView runScenario(@PathVariable SimulatorScenario scenario,
                                         @Valid @RequestBody RunScenarioRequest request) {
        return service.runScenario(scenario, request.seed());
    }

    @GetMapping("/status")
    public SimulatorStatusView status() {
        return service.status();
    }

    @ExceptionHandler(SimulationAlreadyRunningException.class)
    public ResponseEntity<ApiError> alreadyRunning(SimulationAlreadyRunningException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("SIMULATOR_ALREADY_RUNNING", exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> invalidScenario(MethodArgumentTypeMismatchException exception) {
        var scenarios = Arrays.stream(SimulatorScenario.values()).map(Enum::name).toList();
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_FAILED",
                "Invalid simulator scenario", Map.of("scenario", scenarios)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidRequestBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_FAILED",
                "JSON body is missing or malformed", Map.of("request", "JSON body is missing or malformed")));
    }
}
