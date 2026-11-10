package com.hcmut.emobility.m7dispatch.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "redistribution_tasks")
@Data
public class RedistributionTask {
    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    private String taskId;
    
    private String vehicleId;
    private String sourceHubId;
    private String destinationHubId;
    
    @Enumerated(EnumType.STRING)
    private RedistributionTaskStatus status;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}