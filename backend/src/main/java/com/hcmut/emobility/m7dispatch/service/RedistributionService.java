package com.hcmut.emobility.m7dispatch.service;

import com.hcmut.emobility.m7dispatch.domain.*;
import com.hcmut.emobility.m7dispatch.repository.RedistributionTaskRepository;
import com.hcmut.emobility.m7dispatch.client.*;
import com.hcmut.emobility.m7dispatch.events.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RedistributionService {
    
    private final RedistributionTaskRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final VehicleClient vehicleClient;
    private final HubClient hubClient;
    private final RecommendationClient recommendationClient;

    @Autowired
    public RedistributionService(RedistributionTaskRepository repository, 
                                 ApplicationEventPublisher eventPublisher,
                                 VehicleClient vehicleClient, 
                                 HubClient hubClient, 
                                 RecommendationClient recommendationClient) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.vehicleClient = vehicleClient;
        this.hubClient = hubClient;
        this.recommendationClient = recommendationClient;
    }

    public RedistributionTask createRedistributionTask(String vehicleId, String sourceHubId, String destinationHubId) {
        ExternalDtos.VehicleInfo vehicleInfo = vehicleClient.checkVehicle(vehicleId);
        if (vehicleInfo.getStatus().equalsIgnoreCase("booked")) {
            throw new RuntimeException("Xe đã được đặt (BR-M7-01)");
        }

        Optional<RedistributionTask> activeTask = repository.findActiveByVehicleId(vehicleId);
        if (activeTask.isPresent()) {
            throw new RuntimeException("Xe đã có lệnh điều chuyển (BR-M7-02)");
        }

        ExternalDtos.HubInfo hubInfo = hubClient.checkDestinationCapacity(destinationHubId);
        if (hubInfo.getCurrentVehicleCount() >= hubInfo.getCapacity()) {
            throw new RuntimeException("Hub đích đã đầy (BR-M7-03)");
        }

        RedistributionTask task = new RedistributionTask();
        task.setVehicleId(vehicleId);
        task.setSourceHubId(sourceHubId);
        task.setDestinationHubId(destinationHubId);
        task.setStatus(RedistributionTaskStatus.CREATED);
        task.setCreatedAt(LocalDateTime.now());
                
        return repository.save(task);
    }

    public RedistributionTask approveRecommendation(String recommendationId) {
        ExternalDtos.Recommendation rec = recommendationClient.getRecommendation(recommendationId);
        
        RedistributionTask task = createRedistributionTask(
                rec.getVehicleId(), rec.getSourceHubId(), rec.getDestinationHubId()
        );
        
        // Fix đỏ: Thêm Events. vào trước class
        eventPublisher.publishEvent(new Events.RecommendationApprovedEvent(recommendationId, task.getTaskId()));
        return task;
    }

    public void updateTaskStatus(String taskId, RedistributionTaskStatus newStatus) {
        Optional<RedistributionTask> taskOpt = repository.findById(taskId);
        if (!taskOpt.isPresent()) {
            throw new RuntimeException("Không tìm thấy task");
        }
        
        RedistributionTask task = taskOpt.get();
            
        if (task.getStatus() == RedistributionTaskStatus.DONE || task.getStatus() == RedistributionTaskStatus.FAILED) {
            if (newStatus == RedistributionTaskStatus.IN_PROGRESS) {
                throw new RuntimeException("Không thể chuyển trạng thái ngược về InProgress");
            }
        }

        task.setStatus(newStatus);
        task.setUpdatedAt(LocalDateTime.now());
        repository.save(task);

        if (newStatus == RedistributionTaskStatus.DONE) {
            // Fix đỏ: Thêm Events. vào trước class
            eventPublisher.publishEvent(new Events.DispatchCompletedEvent(
                task.getTaskId(), task.getVehicleId(), task.getDestinationHubId()
            ));
        }
    }

    public List<RedistributionTask> getTasksByStatus(RedistributionTaskStatus status) {
        return repository.findByStatus(status);
    }
}