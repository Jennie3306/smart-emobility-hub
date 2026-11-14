package com.hcmut.emobility.m8simulation.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    public record HubSettings(@Positive long hubId, boolean disabled, @Min(0) @Max(10000) int injectedRequests,
                              @Min(1) @Max(1440) int arrivalWindowMins, @Min(1) @Max(240) int tripMins,
                              boolean reroute, boolean repeatBookings,
                              @Min(0) int closeFromMins, @Min(1) Integer reopenAtMins) {
        public HubSettings(long hubId, boolean disabled, int injectedRequests, int arrivalWindowMins, int tripMins,
                           boolean reroute, boolean repeatBookings) {
            this(hubId, disabled, injectedRequests, arrivalWindowMins, tripMins, reroute, repeatBookings, 0, null);
        }
        public HubSettings withRequests(int requests, boolean repeat) {
            return new HubSettings(hubId, disabled, requests, arrivalWindowMins, tripMins, reroute, repeat, closeFromMins, reopenAtMins);
        }
        public HubSettings withReroute() {
            return new HubSettings(hubId, disabled, injectedRequests, arrivalWindowMins, tripMins, true, repeatBookings, closeFromMins, reopenAtMins);
        }
        public HubSettings opened() {
            return new HubSettings(hubId, false, injectedRequests, arrivalWindowMins, tripMins, reroute, repeatBookings);
        }
        public boolean closedAt(int minute) {
            return disabled && minute >= closeFromMins && (reopenAtMins == null || minute < reopenAtMins);
        }
    }
    public record Scenario(@NotBlank @Size(max=100) String name, @Positive long hubId, boolean disabled,
                           @Min(0) @Max(10000) int injectedRequests,
                           @Min(1) @Max(1440) int durationMins,
                           @Min(1) @Max(1440) int arrivalWindowMins,
                           @Min(1) @Max(240) int tripMins, boolean reroute, long seed,
                           @Size(min=1) List<@NotNull @Valid HubSettings> affectedHubs) {
        public Scenario {
            if (affectedHubs != null) {
                affectedHubs = java.util.Collections.unmodifiableList(new ArrayList<>(affectedHubs));
                if (!affectedHubs.isEmpty() && affectedHubs.getFirst() != null) {
                    var first = affectedHubs.getFirst();
                    hubId = first.hubId(); disabled = first.disabled(); injectedRequests = first.injectedRequests();
                    arrivalWindowMins = first.arrivalWindowMins(); tripMins = first.tripMins(); reroute = first.reroute();
                }
            }
        }
        public Scenario(String name, long hubId, boolean disabled, int injectedRequests, int durationMins,
                        int arrivalWindowMins, int tripMins, boolean reroute, long seed) {
            this(name, hubId, disabled, injectedRequests, durationMins, arrivalWindowMins, tripMins, reroute, seed, null);
        }
        public Scenario(String name, int durationMins, long seed, List<HubSettings> hubs) {
            this(name, 0, false, 0, durationMins, 0, 0, false, seed, hubs);
        }
        public List<HubSettings> settings() {
            return affectedHubs == null
                    ? List.of(new HubSettings(hubId, disabled, injectedRequests, arrivalWindowMins, tripMins, reroute, false))
                    : affectedHubs;
        }
        public Scenario withSettings(List<HubSettings> hubs) {
            return new Scenario(name, durationMins, seed, hubs);
        }
        public boolean closedAtStart(long id) {
            return settings().stream().anyMatch(h -> h.hubId() == id && h.closedAt(0));
        }
    }
    public record HubKpi(long hubId, String name, int requests, int served, int failed, double meanWaitMins,
                         int availableVehicles, double peakParkingPercent) {}
    public record Kpi(int requests, int served, int failed, double failurePercent, double meanWaitMins,
                      int peakQueue, List<HubKpi> hubs) {}
    public record Point(int minute, int served, int waiting, int failed) {}
    public record EngineResult(Kpi kpi, List<Point> timeline) {}
    public record DispatchTask(Long vehicleId, long taskId) {}
    public record DispatchProgress(List<DispatchTask> tasks, Long unconfirmedVehicleId, String attemptId, String message, String m7RecommendationId) {
        public DispatchProgress { tasks = List.copyOf(tasks); }
    }
    public record Recommendation(String id, String action, long fromHubId, long toHubId, int amount,
                                 String reason, String status, double projectedFailurePercent, DispatchProgress dispatch) {
        public Recommendation(String id, String action, long fromHubId, long toHubId, int amount,
                              String reason, String status, double projectedFailurePercent) {
            this(id, action, fromHubId, toHubId, amount, reason, status, projectedFailurePercent, null);
        }
        public Recommendation withDispatch(String status, DispatchProgress progress) {
            return new Recommendation(id, action, fromHubId, toHubId, amount, reason, status, projectedFailurePercent, progress);
        }
    }
    public record Run(String id, Instant createdAt, Scenario scenario, Snapshot snapshot, Kpi kpi,
                      Kpi baseline, List<Point> timeline, List<Recommendation> recommendations,
                      String recommendationSource, List<String> warnings,AgentModels.AgentState agent) {
        public Run {
            if (agent != null && agent.best() != null && agent.best().valid() && agent.best().kpi() != null) {
                var items = new ArrayList<>(recommendations.stream().filter(r -> !r.id().startsWith("agent-")).toList());
                var actions = agent.best().actions();
                for (int i = 0; i < actions.size(); i++) {
                    var action = actions.get(i);
                    if (!"dispatch".equals(action.action())) continue;
                    String key = id + ":" + i + ":" + action.fromHubId() + ":" + action.toHubId() + ":" + action.amount();
                    String actionId = "agent-" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
                    items.add(recommendations.stream().filter(r -> r.id().equals(actionId)
                                    && (r.dispatch() != null || !"PENDING".equals(r.status())))
                            .findFirst().orElseGet(() -> new Recommendation(actionId, "dispatch", action.fromHubId(),
                                    action.toHubId(), action.amount(), action.reason(), "PENDING", agent.best().kpi().failurePercent())));
                }
                recommendations = List.copyOf(items);
            }
        }

        public Run(String id,Instant createdAt,Scenario scenario,Snapshot snapshot,Kpi kpi,Kpi baseline,List<Point> timeline,
                   List<Recommendation> recommendations,String recommendationSource,List<String> warnings) {
            this(id,createdAt,scenario,snapshot,kpi,baseline,timeline,recommendations,recommendationSource,warnings,null);
        }
        public Run withRecommendations(List<Recommendation> items) {
            return new Run(id, createdAt, scenario, snapshot, kpi, baseline, timeline, items, recommendationSource, warnings, agent);
        }
    }
    public record ParseRequest(@NotBlank @Size(max=4000) String text) {}
    public record ParseResult(Scenario scenario, String source, List<String> warnings) {}
}
