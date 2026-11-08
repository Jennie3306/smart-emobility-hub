package com.hcmut.emobility.m7dispatch.controller;

import com.hcmut.emobility.m7dispatch.domain.RedistributionTask;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTaskStatus;
import com.hcmut.emobility.m7dispatch.service.RedistributionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/redistributions")
public class RedistributionController {

    private final RedistributionService service;

    @Autowired
    public RedistributionController(RedistributionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RedistributionTask> createTask(
            @RequestParam String vehicleId,
            @RequestParam String sourceHubId,
            @RequestParam String destinationHubId) {
        return ResponseEntity.ok(service.createRedistributionTask(vehicleId, sourceHubId, destinationHubId));
    }

    @PostMapping("/recommendations/{recommendationId}/approve")
    public ResponseEntity<RedistributionTask> approveRecommendation(@PathVariable String recommendationId) {
        return ResponseEntity.ok(service.approveRecommendation(recommendationId));
    }

    @PutMapping("/{taskId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable String taskId,
            @RequestParam RedistributionTaskStatus status) {
        service.updateTaskStatus(taskId, status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<RedistributionTask> getTaskDetail(@PathVariable String taskId) {
        return ResponseEntity.ok(service.getTaskDetail(taskId));
    }

    @GetMapping
    public ResponseEntity<List<RedistributionTask>> getTasksByStatus(
            @RequestParam RedistributionTaskStatus status) {
        return ResponseEntity.ok(service.getTasksByStatus(status));
    }
}