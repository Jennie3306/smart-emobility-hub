package com.hcmut.emobility.m7dispatch.controller;

import com.hcmut.emobility.m7dispatch.domain.RecommendationStatus;
import com.hcmut.emobility.m7dispatch.domain.RedistributionRecommendation;
import com.hcmut.emobility.m7dispatch.service.RecommendationService;
import com.hcmut.emobility.shared.contracts.ParkingDock;
import com.hcmut.emobility.shared.contracts.RedistributionRecommendations.ApprovalResult;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** FR-M7-02. Under /api/m7/**, so only OPERATOR can call it (SecurityConfig). */
@RestController
@RequestMapping("/api/m7/recommendations")
public class RecommendationController {

    private final RecommendationService service;
    private final ParkingDock parkingDock;

    public RecommendationController(RecommendationService service, ParkingDock parkingDock) {
        this.service = service;
        this.parkingDock = parkingDock;
    }

    public record RecommendationView(Long id, String source, String externalRef,
                                     Long sourceHubId, String sourceHubName,
                                     Long destinationHubId, String destinationHubName,
                                     int amount, int taskCount, String reason, RecommendationStatus status,
                                     String note, LocalDateTime createdAt, LocalDateTime resolvedAt,
                                     String resolvedBy) { }

    public record RejectRequest(String reason) { }

    @GetMapping
    public List<RecommendationView> list(@RequestParam(required = false) RecommendationStatus status) {
        Map<Long, String> names = hubNames();
        return service.list(status).stream().map(r -> view(r, names)).toList();
    }

    @PostMapping("/{id}/approve")
    public ApprovalResult approve(@PathVariable String id, Principal principal) {
        return service.approve(id, principal.getName());
    }

    @PostMapping("/{id}/reject")
    public RecommendationView reject(@PathVariable String id, @RequestBody(required = false) RejectRequest request,
                                     Principal principal) {
        return view(service.reject(id, principal.getName(), request == null ? null : request.reason()), hubNames());
    }

    private Map<Long, String> hubNames() {
        return parkingDock.vacancies().stream()
                .collect(Collectors.toMap(ParkingDock.HubVacancy::hubId, ParkingDock.HubVacancy::hubName, (a, b) -> a));
    }

    private static RecommendationView view(RedistributionRecommendation r, Map<Long, String> names) {
        return new RecommendationView(r.getId(), r.getSource(), r.getExternalRef(),
                r.getSourceHubId(), names.get(r.getSourceHubId()),
                r.getDestinationHubId(), names.get(r.getDestinationHubId()),
                r.getAmount(), r.getTaskCount(), r.getReason(), r.getStatus(), r.getNote(),
                r.getCreatedAt(), r.getResolvedAt(), r.getResolvedBy());
    }
}
