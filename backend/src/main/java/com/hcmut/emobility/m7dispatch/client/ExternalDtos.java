package com.hcmut.emobility.m7dispatch.client;
import lombok.Data;
import lombok.AllArgsConstructor;

public class ExternalDtos {

    @Data @AllArgsConstructor
    public static class VehicleInfo {
        private String vehicleId;
        private String status;
    }

    @Data @AllArgsConstructor
    public static class HubInfo {
        private String hubId;
        private int capacity;
        private int currentVehicleCount;
    }

    @Data @AllArgsConstructor
    public static class Recommendation {
        private String recommendationId;
        private String vehicleId;
        private String sourceHubId;
        private String destinationHubId;
    }
}