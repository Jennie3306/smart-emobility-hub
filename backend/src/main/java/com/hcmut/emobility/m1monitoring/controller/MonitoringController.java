package com.hcmut.emobility.m1monitoring.controller;

import com.hcmut.emobility.m1monitoring.dto.Dtos.*;
import com.hcmut.emobility.m1monitoring.service.AlertService;
import com.hcmut.emobility.m1monitoring.service.MonitoringService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Lop mong: kiem tra tham so, goi service, tra DTO. Khong tinh toan nghiep vu.
 * Phan quyen o SecurityConfig: /api/m1/dashboard va /api/m1/alerts chi cho OPERATOR (BR-M1-10).
 */
@RestController
@RequestMapping("/api/m1")
public class MonitoringController {

    private final MonitoringService monitoring;
    private final AlertService alerts;

    public MonitoringController(MonitoringService monitoring, AlertService alerts) {
        this.monitoring = monitoring;
        this.alerts = alerts;
    }

    /** UC-M1-01, UC-M1-03. Loc tuy chon: co xe / con cho trong. */
    @GetMapping("/hubs")
    public List<HubSummaryDto> getHubs(
            @RequestParam(required = false) Boolean hasVehicle,
            @RequestParam(required = false) Boolean hasFreeSlot) {

        return monitoring.getHubSummaries().stream()
                .filter(h -> hasVehicle == null || !hasVehicle || h.availableVehicles() > 0)
                .filter(h -> hasFreeSlot == null || !hasFreeSlot || h.utilisation().freeSlots() > 0)
                .toList();
    }

    /** UC-M1-02. */
    @GetMapping("/hubs/{hubId}")
    public HubDetailDto getHub(@PathVariable Long hubId) {
        return monitoring.getHubDetail(hubId);
    }

    /** UC-M1-04. Mot lan goi cho ca ba vung man hinh. */
    @GetMapping("/dashboard")
    public DashboardDto getDashboard() {
        return monitoring.getDashboard(alerts.findOpen());
    }

    /** UC-M1-08 (FR-M1-10). Mac dinh 7 ngay gan nhat. */
    @GetMapping("/alerts")
    public List<AlertDto> getAlertHistory(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) Long hubId) {

        OffsetDateTime end = (to == null) ? OffsetDateTime.now() : to;
        OffsetDateTime start = (from == null) ? end.minusDays(7) : from;
        return alerts.findHistory(start, end, hubId);
    }

    /** FR-M1-11. */
    @PostMapping("/alerts/{alertId}/acknowledge")
    public AlertDto acknowledge(@PathVariable Long alertId, Principal principal) {
        return alerts.acknowledge(alertId, principal.getName());
    }

    /** FR-M1-11. */
    @PostMapping("/alerts/{alertId}/dismiss")
    public AlertDto dismiss(@PathVariable Long alertId, @RequestBody DismissRequest request) {
        return alerts.dismiss(alertId, request.reason());
    }
}
