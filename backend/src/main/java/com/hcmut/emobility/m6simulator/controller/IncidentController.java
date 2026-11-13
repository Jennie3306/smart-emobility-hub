package com.hcmut.emobility.m6simulator.controller;

import com.hcmut.emobility.m6simulator.domain.IncidentSeverity;
import com.hcmut.emobility.m6simulator.domain.IncidentStatus;
import com.hcmut.emobility.m6simulator.domain.ResourceType;
import com.hcmut.emobility.m6simulator.dto.IncidentView;
import com.hcmut.emobility.m6simulator.dto.ReportIncidentRequest;
import com.hcmut.emobility.m6simulator.dto.TransitionIncidentRequest;
import com.hcmut.emobility.m6simulator.service.ActiveIncidentExistsException;
import com.hcmut.emobility.m6simulator.service.IncidentService;
import com.hcmut.emobility.shared.common.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/** FR-M6-01, FR-M6-02. Under /api/m6/**, so only OPERATOR can call it (SecurityConfig, M6-D08). */
@RestController
@RequestMapping("/api/m6/incidents")
public class IncidentController {

    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<IncidentView> report(@RequestBody ReportIncidentRequest request, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(IncidentView.of(service.report(request, principal.getName())));
    }

    @GetMapping
    public List<IncidentView> list(@RequestParam(required = false) IncidentStatus status,
                                   @RequestParam(required = false) ResourceType resourceType,
                                   @RequestParam(required = false) IncidentSeverity severity) {
        return service.list(status, resourceType, severity).stream().map(IncidentView::of).toList();
    }

    @GetMapping("/{id}")
    public IncidentView get(@PathVariable Long id) {
        return IncidentView.of(service.get(id));
    }

    @PatchMapping("/{id}")
    public IncidentView transition(@PathVariable Long id, @RequestBody TransitionIncidentRequest request,
                                   Principal principal) {
        return IncidentView.of(service.transition(id, request, principal.getName()));
    }

    @ExceptionHandler(ActiveIncidentExistsException.class)
    public ResponseEntity<ApiError> activeExists(ActiveIncidentExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(ex.getCode(), ex.getMessage(),
                Map.of("existingIncidentId", ex.getExistingIncidentId(),
                        "existingStatus", ex.getExistingStatus().name())));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> badRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_FAILED",
                "Request body or parameter is missing or has an unknown value", Map.of()));
    }
}
