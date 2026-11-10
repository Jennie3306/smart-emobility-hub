package com.hcmut.emobility.m7dispatch.client;

/** Du lieu M7 can hoi tu module khac truoc khi tao nhiem vu. Chi doc. */
public final class ExternalDtos {

    private ExternalDtos() { }

    /** state theo tu vung cua M3: AVAILABLE, RESERVED, IN_USE, MAINTENANCE, IN_TRANSIT. */
    public record VehicleInfo(Long vehicleId, String state, Long currentHubId) { }

    /** capacity = so cho do cua hub; currentVehicleCount = so xe dang thuoc hub. */
    public record HubInfo(Long hubId, int capacity, int currentVehicleCount) {
        public boolean isFull() { return currentVehicleCount >= capacity; }
    }

    /** Khuyen nghi dieu chuyen mot xe cu the (tu M8). */
    public record Recommendation(String recommendationId, Long vehicleId,
                                 Long sourceHubId, Long destinationHubId) { }
}
