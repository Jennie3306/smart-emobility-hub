package com.hcmut.emobility.m7dispatch.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Nhiem vu dieu chuyen mot xe tu Hub nguon sang Hub dich.
 * Id kieu Long de khop hop dong su kien o Phu luc A
 * (DispatchCompletedEvent dung Long cho taskId, vehicleId, hubId).
 */
@Entity
@Table(name = "redistribution_tasks")
public class RedistributionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskId;

    @Column(nullable = false)
    private Long vehicleId;

    @Column(nullable = false)
    private Long sourceHubId;

    @Column(nullable = false)
    private Long destinationHubId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RedistributionTaskStatus status;

    /** Khuyen nghi cua M8 sinh ra nhiem vu nay; null neu Operator tao tay. */
    @Column(length = 64)
    private String recommendationId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

    public Long getSourceHubId() { return sourceHubId; }
    public void setSourceHubId(Long sourceHubId) { this.sourceHubId = sourceHubId; }

    public Long getDestinationHubId() { return destinationHubId; }
    public void setDestinationHubId(Long destinationHubId) { this.destinationHubId = destinationHubId; }

    public RedistributionTaskStatus getStatus() { return status; }
    public void setStatus(RedistributionTaskStatus status) { this.status = status; }

    public String getRecommendationId() { return recommendationId; }
    public void setRecommendationId(String recommendationId) { this.recommendationId = recommendationId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
