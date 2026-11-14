package com.hcmut.emobility.m8simulation.service;

import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import java.util.*;

final class StartingFleet {
    private StartingFleet() {}

    static Snapshot override(Snapshot snapshot, String input) {
        if (input == null || input.isBlank()) return snapshot;
        var counts = new LinkedHashMap<Long, Integer>();
        for (String entry : input.split(",", -1)) {
            if (!entry.matches("[0-9]{1,18}:[0-9]{1,4}")) {
                throw new IllegalArgumentException("Invalid starting vehicle count.");
            }
            var fields = entry.split(":");
            long hubId = Long.parseLong(fields[0]);
            int count = Integer.parseInt(fields[1]);
            if (count > 1000 || snapshot.hubs().stream().noneMatch(h -> h.id() == hubId)
                    || counts.putIfAbsent(hubId, count) != null) {
                throw new IllegalArgumentException("Select a unique hub and enter 0 to 1000 starting vehicles.");
            }
        }
        var vehicles = new ArrayList<>(snapshot.vehicles());
        var deltas = new HashMap<Long, Integer>();
        long nextId = vehicles.stream().mapToLong(Vehicle::id).filter(id -> id < 0).min().orElse(0) - 1;
        for (var entry : counts.entrySet()) {
            long hubId = entry.getKey();
            var eligible = vehicles.stream().filter(v -> Objects.equals(v.hubId(), hubId)
                    && "AVAILABLE".equals(v.state()) && v.battery() >= 20).toList();
            int delta = entry.getValue() - eligible.size();
            if (delta == 0) continue;
            if (delta > 0) {
                var hub = snapshot.hubs().stream().filter(h -> h.id() == hubId).findFirst().orElseThrow();
                int assigned = (int) vehicles.stream().filter(v -> Objects.equals(v.hubId(), hubId)).count();
                int free = snapshot.capacities().stream().filter(c -> c.hubId() == hubId && c.totalSlots() > 0
                                && c.usedSlots() >= 0 && c.freeSlots() >= 0 && c.usedSlots() <= c.totalSlots()
                                && c.freeSlots() <= c.totalSlots() - c.usedSlots())
                        .mapToInt(CapacityState::freeSlots).findFirst().orElse(0);
                int limit = eligible.size() + Math.max(0, Math.min(free, hub.capacity() - assigned));
                if (entry.getValue() > limit) {
                    throw new IllegalArgumentException(hub.name() + " can start with at most " + limit
                            + " available vehicles based on parking in this snapshot.");
                }
            }
            deltas.put(hubId, delta);
            if (delta < 0) {
                var removed = new HashSet<>(eligible.subList(entry.getValue(), eligible.size()));
                vehicles.removeIf(removed::contains);
            } else {
                for (int i = 0; i < delta; i++) vehicles.add(new Vehicle(nextId--, hubId, 100, "AVAILABLE"));
            }
        }
        if (deltas.isEmpty()) return snapshot;
        var capacities = snapshot.capacities().stream().map(c -> {
            int delta = deltas.getOrDefault(c.hubId(), 0);
            if (delta == 0) return c;
            return new CapacityState(c.hubId(), c.totalSlots(), Math.max(0, c.usedSlots() + delta),
                    Math.clamp(c.freeSlots() - delta, 0, Math.max(0, c.freeSlots() + c.usedSlots())),
                    c.operationalChargers(), c.busyChargers());
        }).toList();
        return new Snapshot(UUID.randomUUID().toString(), snapshot.capturedAt(), snapshot.hubs(), vehicles,
                snapshot.deviceStates(), snapshot.demandSource(), snapshot.bookingWindow(), capacities, true);
    }
}
