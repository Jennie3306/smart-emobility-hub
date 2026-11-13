package com.hcmut.emobility.m7dispatch.controller;

import com.hcmut.emobility.m7dispatch.domain.RedistributionTask;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTaskStatus;
import com.hcmut.emobility.m7dispatch.service.RedistributionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Duoi /api/m7/** nen chi OPERATOR goi duoc (SecurityConfig). */
@RestController
@RequestMapping("/api/m7/redistributions")
public class RedistributionController {

    private final RedistributionService service;

    public RedistributionController(RedistributionService service) {
        this.service = service;
    }

    public record CreateTaskRequest(Long vehicleId, Long sourceHubId, Long destinationHubId) { }

    @PostMapping
    public RedistributionTask createTask(@RequestBody CreateTaskRequest request) {
        return service.createRedistributionTask(
                request.vehicleId(), request.sourceHubId(), request.destinationHubId());
    }

    @PutMapping("/{taskId}/status")
    public RedistributionTask updateStatus(@PathVariable Long taskId,
                                           @RequestParam RedistributionTaskStatus status) {
        return service.updateTaskStatus(taskId, status);
    }

    @GetMapping("/{taskId}")
    public RedistributionTask getTaskDetail(@PathVariable Long taskId) {
        return service.getTaskDetail(taskId);
    }

    @GetMapping
    public List<RedistributionTask> getTasks(@RequestParam(required = false) RedistributionTaskStatus status) {
        return service.getTasks(status);
    }
}
