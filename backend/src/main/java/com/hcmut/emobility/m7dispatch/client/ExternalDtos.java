package com.hcmut.emobility.m7dispatch.client;

/** Du lieu M7 can hoi tu module khac truoc khi tao nhiem vu. Chi doc. */
public final class ExternalDtos {

    private ExternalDtos() { }

    /** state theo tu vung cua M3: AVAILABLE, RESERVED, IN_USE, MAINTENANCE, IN_TRANSIT. */
    public record VehicleInfo(Long vehicleId, String state, Long currentHubId) { }

    /**
     * BR-M7-03 (Appendix A v2): freeSpaces = free spaces in the destination Hub's shared parking pool (M4);
     * incomingVehicles = vehicles already on their way there in active tasks. Full when every free space is
     * already promised to an incoming vehicle.
     */
    public record HubInfo(Long hubId, int freeSpaces, int incomingVehicles) {
        public boolean isFull() { return incomingVehicles >= freeSpaces; }
    }

    /** Khuyen nghi dieu chuyen mot xe cu the (tu M8). */
    public record Recommendation(String recommendationId, Long vehicleId,
                                 Long sourceHubId, Long destinationHubId) { }
}
