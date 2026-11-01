package com.hcmut.emobility.m8simulation.domain;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class SimulationModels {
    private SimulationModels() {}
    public record Hub(long id, String name, int capacity, int chargingPoints) {}
    public record Vehicle(long id, Long hubId, int battery, String state) {}
    public record DeviceState(long hubId, String type, long resourceId, String state, Instant observedAt) {}
    public record HubDemand(long hubId, int bookings, int activeHolds) {}
    public record BookingWindow(Instant from,Instant to,int windowMins,List<HubDemand> hubs) {
        public BookingWindow { hubs=List.copyOf(hubs); }
    }
    public record CapacityState(long hubId,int totalSlots,int usedSlots,int freeSlots,int operationalChargers,int busyChargers) {}
    public record Snapshot(String id, Instant capturedAt, List<Hub> hubs, List<Vehicle> vehicles,
                           List<DeviceState> deviceStates, String demandSource,BookingWindow bookingWindow,List<CapacityState> capacities) {
        public Snapshot(String id,Instant capturedAt,List<Hub> hubs,List<Vehicle> vehicles,List<DeviceState> devices,String source) {
            this(id,capturedAt,hubs,vehicles,devices,source,null,List.of());
        }
        public Snapshot { hubs = List.copyOf(hubs); vehicles = List.copyOf(vehicles); deviceStates = List.copyOf(deviceStates); capacities=capacities==null?List.of():List.copyOf(capacities); }
    }
    public record Scenario(@NotBlank @Size(max=100) String name, @Positive long hubId, boolean disabled,
                           @Min(0) @Max(10000) int injectedRequests,
                           @Min(1) @Max(1440) int durationMins,
                           @Min(1) @Max(1440) int arrivalWindowMins,
                           @Min(1) @Max(240) int tripMins, boolean reroute, long seed) {}
    public record HubKpi(long hubId, String name, int requests, int served, int failed, double meanWaitMins,
                         int availableVehicles, double peakParkingPercent) {}
    public record Kpi(int requests, int served, int failed, double failurePercent, double meanWaitMins,
                      int peakQueue, List<HubKpi> hubs) {}
    public record Point(int minute, int served, int waiting, int failed) {}
    public record EngineResult(Kpi kpi, List<Point> timeline) {}
    public record Recommendation(String id, String action, long fromHubId, long toHubId, int amount,
                                 String reason, String status, double projectedFailurePercent) {}
    public record Run(String id, Instant createdAt, Scenario scenario, Snapshot snapshot, Kpi kpi,
                      Kpi baseline, List<Point> timeline, List<Recommendation> recommendations,
                      String recommendationSource, List<String> warnings,AgentModels.AgentState agent) {
        public Run(String id,Instant createdAt,Scenario scenario,Snapshot snapshot,Kpi kpi,Kpi baseline,List<Point> timeline,
                   List<Recommendation> recommendations,String recommendationSource,List<String> warnings) {
            this(id,createdAt,scenario,snapshot,kpi,baseline,timeline,recommendations,recommendationSource,warnings,null);
        }
    }
    public record ParseRequest(@NotBlank @Size(max=4000) String text) {}
    public record ParseResult(Scenario scenario, String source, List<String> warnings) {}
}
