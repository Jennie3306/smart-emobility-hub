package com.hcmut.emobility.m7dispatch.events;
import lombok.Data;
import lombok.AllArgsConstructor;

public class Events {
    
    @Data @AllArgsConstructor
    public static class HubNearCapacityEvent {
        private String hubId;
        private int currentCapacity;
        private int maxCapacity;
    }

    @Data @AllArgsConstructor
    public static class DispatchCompletedEvent {
        private String taskId;
        private String vehicleId;
        private String destinationHubId;
    }

    @Data @AllArgsConstructor
    public static class RecommendationApprovedEvent {
        private String recommendationId;
        private String taskId;
    }
}