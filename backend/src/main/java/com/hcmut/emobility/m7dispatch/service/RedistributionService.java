package com.hcmut.emobility.m7dispatch.service;

import com.hcmut.emobility.m7dispatch.client.ExternalDtos;
import com.hcmut.emobility.m7dispatch.client.HubClient;
import com.hcmut.emobility.m7dispatch.client.RecommendationClient;
import com.hcmut.emobility.m7dispatch.client.VehicleClient;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTask;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTaskStatus;
import com.hcmut.emobility.m7dispatch.repository.RedistributionTaskRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.events.DispatchCompletedEvent;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.RecommendationApprovedEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * FR-M7-01..03. Tao, duyet va theo doi nhiem vu dieu chuyen xe.
 * M7 khong doi trang thai xe: khi nhiem vu DONE, M7 phat DispatchCompletedEvent
 * va M3 tu chuyen xe sang hub dich, M1 tu cap nhat dashboard hai hub.
 */
@Service
public class RedistributionService {

    /** BR-M7-01: xe dang duoc dat hoac dang co nguoi su dung thi khong dieu chuyen. */
    private static final Set<String> NOT_MOVABLE = Set.of("RESERVED", "BOOKED", "IN_USE");

    private final RedistributionTaskRepository repository;
    private final EventPublisher events;
    private final VehicleClient vehicleClient;
    private final HubClient hubClient;
    private final RecommendationClient recommendationClient;

    public RedistributionService(RedistributionTaskRepository repository,
                                 EventPublisher events,
                                 VehicleClient vehicleClient,
                                 HubClient hubClient,
                                 RecommendationClient recommendationClient) {
        this.repository = repository;
        this.events = events;
        this.vehicleClient = vehicleClient;
        this.hubClient = hubClient;
        this.recommendationClient = recommendationClient;
    }

    /** FR-M7-01, UC-M7-01. Kiem tra rang buoc roi tao nhiem vu o trang thai CREATED. */
    @Transactional
    public RedistributionTask createRedistributionTask(Long vehicleId, Long sourceHubId, Long destinationHubId) {
        if (vehicleId == null || sourceHubId == null || destinationHubId == null) {
            throw new BusinessException("VALIDATION_FAILED",
                    "vehicleId, sourceHubId and destinationHubId are required", HttpStatus.BAD_REQUEST);
        }
        if (sourceHubId.equals(destinationHubId)) {
            throw new BusinessException("SAME_HUB",
                    "Source and destination hub must differ", HttpStatus.BAD_REQUEST);
        }

        ExternalDtos.VehicleInfo vehicle = vehicleClient.checkVehicle(vehicleId)
                .orElseThrow(() -> new BusinessException("VEHICLE_NOT_FOUND",
                        "Vehicle not found: " + vehicleId, HttpStatus.NOT_FOUND));
        if (vehicle.state() != null && NOT_MOVABLE.contains(vehicle.state().toUpperCase())) {
            throw new BusinessException("VEHICLE_RESERVED",
                    "Vehicle " + vehicleId + " is reserved or in use (BR-M7-01)");
        }
        if (vehicle.currentHubId() != null && !vehicle.currentHubId().equals(sourceHubId)) {
            throw new BusinessException("VEHICLE_NOT_AT_SOURCE",
                    "Vehicle " + vehicleId + " is at hub " + vehicle.currentHubId()
                            + ", not at source hub " + sourceHubId);
        }

        if (repository.findActiveByVehicleId(vehicleId).isPresent()) {
            throw new BusinessException("TASK_ALREADY_ACTIVE",
                    "Vehicle " + vehicleId + " already has an active redistribution task (BR-M7-02)");
        }

        ExternalDtos.HubInfo destination = hubClient.checkDestinationCapacity(destinationHubId)
                .orElseThrow(() -> new BusinessException("HUB_NOT_FOUND",
                        "Hub not found: " + destinationHubId, HttpStatus.NOT_FOUND));
        if (destination.isFull()) {
            throw new BusinessException("DESTINATION_FULL",
                    "Destination hub " + destinationHubId + " is full (BR-M7-03)");
        }

        RedistributionTask task = new RedistributionTask();
        task.setVehicleId(vehicleId);
        task.setSourceHubId(sourceHubId);
        task.setDestinationHubId(destinationHubId);
        task.setStatus(RedistributionTaskStatus.CREATED);
        task.setCreatedAt(LocalDateTime.now());
        return repository.save(task);
    }

    /** FR-M7-02, UC-M7-02. Operator duyet khuyen nghi -> tao nhiem vu tu khuyen nghi do (BR-M7-04). */
    @Transactional
    public RedistributionTask approveRecommendation(String recommendationId) {
        ExternalDtos.Recommendation rec = recommendationClient.getRecommendation(recommendationId)
                .orElseThrow(() -> new BusinessException("RECOMMENDATION_NOT_FOUND",
                        "Recommendation not found or no longer valid: " + recommendationId,
                        HttpStatus.NOT_FOUND));

        RedistributionTask task = createRedistributionTask(
                rec.vehicleId(), rec.sourceHubId(), rec.destinationHubId());
        task.setRecommendationId(recommendationId);
        task = repository.save(task);

        events.publish(new RecommendationApprovedEvent(recommendationId, "REDISTRIBUTE"));
        return task;
    }

    /** FR-M7-03, UC-M7-03. Chuyen trang thai theo bang o muc 4.1; DONE thi phat DispatchCompletedEvent. */
    @Transactional
    public RedistributionTask updateTaskStatus(Long taskId, RedistributionTaskStatus newStatus) {
        RedistributionTask task = getTaskDetail(taskId);
        RedistributionTaskStatus current = task.getStatus();
        if (newStatus == null || !current.canMoveTo(newStatus)) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Cannot move task from " + current + " to " + newStatus);
        }

        task.setStatus(newStatus);
        task.setUpdatedAt(LocalDateTime.now());
        RedistributionTask saved = repository.save(task);

        if (newStatus == RedistributionTaskStatus.DONE) {
            events.publish(new DispatchCompletedEvent(saved.getTaskId(), saved.getVehicleId(),
                    saved.getSourceHubId(), saved.getDestinationHubId()));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public RedistributionTask getTaskDetail(Long taskId) {
        return repository.findById(taskId)
                .orElseThrow(() -> new BusinessException("TASK_NOT_FOUND",
                        "Redistribution task not found: " + taskId, HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<RedistributionTask> getTasks(RedistributionTaskStatus status) {
        return status == null
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByStatusOrderByCreatedAtDesc(status);
    }
}
