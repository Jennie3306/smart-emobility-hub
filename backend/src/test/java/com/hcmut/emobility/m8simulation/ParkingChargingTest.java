package com.hcmut.emobility.m8simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hcmut.emobility.m8simulation.domain.AgentModels.PlanAction;
import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.m8simulation.service.*;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.contracts.RedistributionRecommendations;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParkingChargingTest {
    final SimulationEngine engine = new SimulationEngine();
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final ScenarioAssistant assistant = new ScenarioAssistant(mapper, Validation.buildDefaultValidatorFactory().getValidator(), "", "", "");
    final List<Hub> hubs = List.of(new Hub(1, "A", 10, 2), new Hub(2, "B", 10, 2));

    Snapshot snap(CapacityState... capacities) {
        return new Snapshot("s", Instant.EPOCH, hubs, List.of(), List.of(), "test", null, List.of(capacities));
    }
    Snapshot snapWithVehicles(List<Vehicle> vehicles, CapacityState... capacities) {
        return new Snapshot("s", Instant.EPOCH, hubs, vehicles, List.of(), "test", null, List.of(capacities));
    }
    CapacityState cap(long hub, int total, int used, int chargers, int busy) {
        return new CapacityState(hub, total, used, total - used, chargers, busy, total - used, 0);
    }
    HubSettings parking(int arrivals, int window, int stay) {
        return new HubSettings(1, false, 0, window, 30, false, false, 0, null, arrivals, stay, 0, 0, 0);
    }
    HubSettings charging(int requests, int mins, int failed) {
        return new HubSettings(1, false, 0, 1, 30, false, false, 0, null, 0, 0, requests, mins, failed);
    }
    Scenario scenario(HubSettings... settings) { return new Scenario("t", 60, 42, List.of(settings)); }

    // ---- parking

    @Test void parkingBeyondFreeSlotsIsTurnedAwayAndConserved() {
        var snapshot = snap(cap(1, 10, 7, 2, 0));
        var kpi = engine.simulate(snapshot, scenario(parking(10, 1, 600))).kpi();
        assertEquals(10, kpi.parkingRequests());
        assertEquals(3, kpi.parkingServed());
        assertEquals(7, kpi.parkingTurnedAway());
        assertEquals(kpi.parkingRequests(), kpi.parkingServed() + kpi.parkingTurnedAway());
        var hub = kpi.hubs().getFirst();
        assertEquals(7, hub.parkingTurnedAway());
        assertEquals(100.0, hub.parkingPeakPercent(), 1e-9);
        assertEquals(0, kpi.hubs().getLast().parkingRequests());
        assertEquals(0, kpi.failed());
    }
    @Test void shortStayReleasesSpacesThatLongStayKeeps() {
        var snapshot = snap(cap(1, 10, 7, 2, 0));
        int longStay = engine.simulate(snapshot, scenario(parking(10, 60, 600))).kpi().parkingTurnedAway();
        int shortStay = engine.simulate(snapshot, scenario(parking(10, 60, 1))).kpi().parkingTurnedAway();
        assertEquals(7, longStay);
        assertTrue(shortStay < longStay, "short stay " + shortStay + " vs long stay " + longStay);
    }
    @Test void fullHubTurnsEveryoneAway() {
        var kpi = engine.simulate(snap(cap(1, 10, 10, 2, 0)), scenario(parking(5, 10, 30))).kpi();
        assertEquals(5, kpi.parkingTurnedAway());
        assertEquals(0, kpi.parkingServed());
        assertEquals(100.0, kpi.hubs().getFirst().parkingPeakPercent(), 1e-9);
    }
    @Test void parkingIsReproducibleAndDoesNotMutateTheSnapshot() {
        var snapshot = snap(cap(1, 10, 5, 2, 0));
        var before = snapshot.capacities();
        var scenario = scenario(parking(20, 30, 15));
        assertEquals(engine.simulate(snapshot, scenario), engine.simulate(snapshot, scenario));
        assertEquals(before, snapshot.capacities());
    }
    @Test void parkingWithoutCapacityDataIsRejected() {
        var noCapacity = snap(cap(2, 10, 0, 2, 0));
        var ex = assertThrows(IllegalArgumentException.class, () -> engine.simulate(noCapacity, scenario(parking(1, 1, 10))));
        assertEquals("Parking data is unavailable for A in this snapshot.", ex.getMessage());
        var empty = snap(cap(1, 0, 0, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> engine.simulate(empty, scenario(parking(1, 1, 10))));
    }
    @Test void parkingNeedsAStayAndChargingNeedsADuration() {
        var snapshot = snap(cap(1, 10, 0, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> engine.simulate(snapshot, scenario(parking(1, 1, 0))));
        assertThrows(IllegalArgumentException.class, () -> engine.simulate(snapshot, scenario(charging(1, 0, 0))));
    }

    // ---- charging

    @Test void singleChargerServesQueueInOrderAndReportsWaitAndUtilization() {
        var kpi = engine.simulate(snap(cap(1, 10, 0, 1, 0)), scenario(charging(3, 10, 0))).kpi();
        assertEquals(3, kpi.chargingServed());
        assertEquals(0, kpi.chargingUnserved());
        assertEquals(10.0, kpi.meanChargingWaitMins(), 1e-9);   // waits 0, 10, 20
        assertEquals(2, kpi.peakChargingQueue());                 // two waiting once the first one is plugged in
        var hub = kpi.hubs().getFirst();
        assertEquals(1, hub.usableChargers());
        assertEquals(50.0, hub.chargerUtilizationPercent(), 1e-9); // 30 busy minutes of 60
    }
    @Test void requestsStillQueuedAtTheHorizonAreUnserved() {
        var kpi = engine.simulate(snap(cap(1, 10, 0, 1, 0)), scenario(charging(3, 30, 0))).kpi();
        assertEquals(2, kpi.chargingServed());
        assertEquals(1, kpi.chargingUnserved());
        assertEquals(15.0, kpi.meanChargingWaitMins(), 1e-9);    // waits 0 and 30; the third never starts
        assertEquals(100.0, kpi.hubs().getFirst().chargerUtilizationPercent(), 1e-9);
        assertEquals(1, kpi.totalUnserved());
    }
    @Test void noUsableChargerLeavesEveryoneUnservedWithoutUtilization() {
        var kpi = engine.simulate(snap(cap(1, 10, 0, 2, 2)), scenario(charging(3, 10, 0))).kpi();
        assertEquals(0, kpi.chargingServed());
        assertEquals(3, kpi.chargingUnserved());
        assertEquals(0, kpi.meanChargingWaitMins());
        assertEquals(0, kpi.hubs().getFirst().usableChargers());
        assertNull(kpi.hubs().getFirst().chargerUtilizationPercent());
    }
    @Test void failedChargersBoundary() {
        var snapshot = snap(cap(1, 10, 0, 2, 0));
        assertEquals(2, engine.simulate(snapshot, scenario(charging(1, 10, 0))).kpi().hubs().getFirst().usableChargers());
        assertEquals(1, engine.simulate(snapshot, scenario(charging(1, 10, 1))).kpi().hubs().getFirst().usableChargers());
        var all = engine.simulate(snapshot, scenario(charging(1, 10, 2))).kpi();
        assertEquals(0, all.hubs().getFirst().usableChargers());
        assertEquals(1, all.chargingUnserved());
        var ex = assertThrows(IllegalArgumentException.class, () -> engine.simulate(snapshot, scenario(charging(1, 10, 3))));
        assertEquals("A has only 2 operational chargers.", ex.getMessage());
        var noCapacity = snap();
        assertThrows(IllegalArgumentException.class, () -> engine.simulate(noCapacity, scenario(charging(0, 0, 1))));
    }
    @Test void busyChargersReduceTheUsablePool() {
        var kpi = engine.simulate(snap(cap(1, 10, 0, 3, 1)), scenario(charging(1, 10, 1))).kpi();
        assertEquals(1, kpi.hubs().getFirst().usableChargers());
    }

    // ---- independence and compatibility

    @Test void newDemandLeavesRentalKpisUntouched() {
        var snapshot = snapWithVehicles(List.of(new Vehicle(1, 1L, 90, "AVAILABLE"), new Vehicle(2, 1L, 90, "AVAILABLE")),
                cap(1, 10, 2, 2, 0));
        var rental = new HubSettings(1, false, 10, 20, 15, false, false);
        var plain = engine.simulate(snapshot, new Scenario("t", 60, 42, List.of(rental))).kpi();
        var mixed = engine.simulate(snapshot, new Scenario("t", 60, 42, List.of(new HubSettings(1, false, 10, 20, 15, false, false, 0, null,
                8, 20, 5, 10, 0)))).kpi();
        assertEquals(10, plain.requests());
        assertEquals(0, plain.parkingRequests() + plain.chargingRequests() + plain.parkingTurnedAway() + plain.chargingUnserved());
        assertEquals(plain.requests(), mixed.requests());
        assertEquals(plain.served(), mixed.served());
        assertEquals(plain.failed(), mixed.failed());
        assertEquals(plain.failurePercent(), mixed.failurePercent());
        assertEquals(plain.meanWaitMins(), mixed.meanWaitMins());
        assertEquals(plain.peakQueue(), mixed.peakQueue());
    }
    @Test void openedBaselineKeepsParkingChargingAndFailedChargers() {
        var s = new HubSettings(1, true, 3, 5, 10, true, true, 2, 8, 4, 20, 6, 15, 1).opened();
        assertFalse(s.disabled());
        assertEquals(List.of(4, 20, 6, 15, 1), List.of(s.parkingArrivals(), s.parkingStayMins(), s.chargingRequests(), s.chargeMins(), s.failedChargers()));
        var kept = new HubSettings(1, false, 3, 5, 10, false, false).withRequests(9, true).withReroute();
        assertEquals(0, kept.parkingArrivals());
        var withDemand = new HubSettings(1, false, 3, 5, 10, false, false, 0, null, 4, 20, 6, 15, 1);
        var copy = withDemand.withRequests(9, true).withReroute();
        assertEquals(List.of(4, 20, 6, 15, 1), List.of(copy.parkingArrivals(), copy.parkingStayMins(), copy.chargingRequests(), copy.chargeMins(), copy.failedChargers()));
    }
    @Test void runSavedBeforeTheNewFieldsStillLoadsWithZeroDefaults() throws Exception {
        var snapshot = snap(cap(1, 10, 0, 2, 0));
        var scenario = new Scenario("old", 60, 42, List.of(new HubSettings(1, false, 5, 10, 15, false, false)));
        var result = engine.simulate(snapshot, scenario);
        var run = new Run("old", Instant.EPOCH, scenario, snapshot, result.kpi(), result.kpi(), result.timeline(), List.of(), "LOCAL_RULES", List.of());
        var json = (ObjectNode) mapper.valueToTree(run);
        assertFalse(json.path("kpi").has("totalUnserved"));
        for (var name : List.of("parkingRequests", "parkingServed", "parkingTurnedAway", "chargingRequests", "chargingServed",
                "chargingUnserved", "meanChargingWaitMins", "peakChargingQueue")) {
            assertTrue(json.path("kpi").has(name), name);
            ((ObjectNode) json.path("kpi")).remove(name);
            ((ObjectNode) json.path("baseline")).remove(name);
        }
        for (var kpi : List.of(json.path("kpi"), json.path("baseline")))
            for (var hub : kpi.path("hubs"))
                ((ObjectNode) hub).remove(List.of("parkingRequests", "parkingTurnedAway", "parkingPeakPercent", "chargingRequests",
                        "chargingServed", "chargingUnserved", "usableChargers", "chargerUtilizationPercent"));
        for (var hub : json.path("scenario").path("affectedHubs"))
            ((ObjectNode) hub).remove(List.of("parkingArrivals", "parkingStayMins", "chargingRequests", "chargeMins", "failedChargers"));
        var loaded = mapper.treeToValue(json, Run.class);
        assertEquals(0, loaded.kpi().parkingRequests());
        assertEquals(0, loaded.kpi().chargingUnserved());
        assertEquals(5, loaded.kpi().requests());
        assertEquals(0, loaded.scenario().settings().getFirst().failedChargers());
        assertEquals(0, loaded.scenario().settings().getFirst().chargeMins());
        assertEquals(0, loaded.kpi().hubs().getFirst().usableChargers());
        assertNull(loaded.kpi().hubs().getFirst().chargerUtilizationPercent());
        assertEquals(5, loaded.scenario().settings().getFirst().injectedRequests());
    }

    // ---- dispatch relieves parking

    List<Vehicle> fleet() {
        return List.of(new Vehicle(1, 1L, 90, "AVAILABLE"), new Vehicle(2, 1L, 90, "AVAILABLE"),
                new Vehicle(3, 1L, 90, "AVAILABLE"), new Vehicle(4, 1L, 90, "AVAILABLE"));
    }
    Snapshot saturated() {
        return snapWithVehicles(fleet(), cap(1, 10, 10, 2, 0), cap(2, 10, 0, 2, 0));
    }
    @Test void dispatchOutOfASaturatedHubFreesParking() {
        var snapshot = saturated();
        var scenario = scenario(parking(3, 1, 600));
        var tool = new SimulationPlanTool(engine);
        assertEquals(3, engine.simulate(snapshot, scenario).kpi().parkingTurnedAway());
        var one = tool.evaluate(snapshot, scenario, List.of(new PlanAction("dispatch", 1, 2, 1, "move one")));
        assertTrue(one.valid());
        assertEquals(2, one.kpi().parkingTurnedAway());
        var three = tool.evaluate(snapshot, scenario, List.of(new PlanAction("dispatch", 1, 2, 3, "move three")));
        assertEquals(0, three.kpi().parkingTurnedAway());
        assertEquals(CapacityStateOf(snapshot, 1), CapacityStateOf(saturated(), 1));
        assertEquals(10, CapacityStateOf(snapshot, 1).usedSlots());
        assertEquals(0, CapacityStateOf(snapshot, 2).usedSlots());
    }
    @Test void dispatchIntoAFullHubCostsItParking() {
        var snapshot = snapWithVehicles(fleet(), cap(1, 10, 5, 2, 0), cap(2, 10, 8, 2, 0));
        var scenario = new Scenario("t", 60, 42, List.of(new HubSettings(1, false, 0, 1, 30, false, false),
                new HubSettings(2, false, 0, 1, 30, false, false, 0, null, 2, 600, 0, 0, 0)));
        var tool = new SimulationPlanTool(engine);
        assertEquals(0, engine.simulate(snapshot, scenario).kpi().parkingTurnedAway());
        var moved = tool.evaluate(snapshot, scenario, List.of(new PlanAction("dispatch", 1, 2, 2, "fill B")));
        assertTrue(moved.valid());
        assertEquals(2, moved.kpi().parkingTurnedAway());
    }
    @Test void localRuleProposesMovingRentalVehiclesOutOfASaturatedHub() {
        var snapshot = saturated();
        var scenario = scenario(parking(3, 1, 600));
        var kpi = engine.simulate(snapshot, scenario).kpi();
        var proposals = assistant.recommend(snapshot, scenario, kpi, engine);
        var dispatch = proposals.items().stream().filter(r -> r.action().equals("dispatch")).findFirst().orElseThrow();
        assertEquals(1, dispatch.fromHubId());
        assertEquals(2, dispatch.toHubId());
        assertEquals(3, dispatch.amount());
        assertTrue(dispatch.reason().startsWith("Free parking at the saturated hub by moving rental vehicles out."));
    }
    @Test void localRuleStaysQuietWhenNothingFreesParking() {
        var snapshot = snapWithVehicles(List.of(new Vehicle(1, 1L, 90, "AVAILABLE")), cap(1, 10, 10, 2, 0), cap(2, 10, 0, 2, 0));
        var scenario = scenario(parking(3, 1, 600));
        var proposals = assistant.recommend(snapshot, scenario, engine.simulate(snapshot, scenario).kpi(), engine);
        assertTrue(proposals.items().isEmpty());
    }

    // ---- charging advice

    @Test void chargingAdviceRedirectsToAHubWithFreeChargers() {
        var snapshot = snap(cap(1, 10, 0, 1, 0), cap(2, 10, 0, 4, 0));
        var scenario = scenario(charging(3, 30, 0));
        var kpi = engine.simulate(snapshot, scenario).kpi();
        assertEquals(1, kpi.chargingUnserved());
        var advice = assistant.chargingAdvice(snapshot, scenario, kpi, engine);
        assertEquals(1, advice.size());
        var item = advice.getFirst();
        assertEquals("charging_redirect", item.action());
        assertEquals(1, item.fromHubId());
        assertEquals(2, item.toHubId());
        assertEquals(1, item.amount());
        assertEquals("PENDING", item.status());
        assertTrue(item.reason().contains("Display only"));
        assertEquals(1, scenario.settings().size());   // input scenario untouched
    }
    @Test void chargingAdviceIsAbsentWhenNoOtherHubHasFreeChargersOrNothingIsUnserved() {
        var jammed = snap(cap(1, 10, 0, 1, 0), cap(2, 10, 0, 2, 2));
        var scenario = scenario(charging(3, 30, 0));
        assertTrue(assistant.chargingAdvice(jammed, scenario, engine.simulate(jammed, scenario).kpi(), engine).isEmpty());
        var fine = snap(cap(1, 10, 0, 3, 0), cap(2, 10, 0, 4, 0));
        assertTrue(assistant.chargingAdvice(fine, scenario, engine.simulate(fine, scenario).kpi(), engine).isEmpty());
    }
    @Test void chargingAdviceDoesNotImproveWhenTargetIsAlsoSaturatedByItsOwnDemand() {
        var snapshot = snap(cap(1, 10, 0, 1, 0), cap(2, 10, 0, 1, 0));
        var scenario = new Scenario("t", 60, 42, List.of(charging(3, 30, 0),
                new HubSettings(2, false, 0, 1, 30, false, false, 0, null, 0, 0, 3, 30, 0)));
        var kpi = engine.simulate(snapshot, scenario).kpi();
        assertEquals(2, kpi.chargingUnserved());
        assertTrue(assistant.chargingAdvice(snapshot, scenario, kpi, engine).isEmpty());
    }
    @Test void serviceAppendsChargingAdviceAndWarnsThatDemandIsHypothetical(@TempDir Path dir) throws Exception {
        var snapshot = snap(cap(1, 10, 0, 1, 0), cap(2, 10, 0, 4, 0));
        var provider = mock(SnapshotProvider.class);
        when(provider.capture()).thenReturn(snapshot);
        var service = new SimulationService(provider, engine, new RunStore(mapper, dir.resolve("runs.json").toString()), assistant);
        var run = service.run(scenario(charging(3, 30, 0)), null);
        assertTrue(run.recommendations().stream().anyMatch(r -> r.action().equals("charging_redirect")));
        assertTrue(run.warnings().stream().anyMatch(w -> w.startsWith("Parking and charging demand are hypothetical")));
        assertEquals(run, service.list().getFirst());
        var plain = service.run(new Scenario("t", 60, 42, List.of(new HubSettings(1, false, 2, 10, 15, false, false))), null);
        assertTrue(plain.warnings().stream().noneMatch(w -> w.startsWith("Parking and charging demand are hypothetical")));
    }
    @Test void chargingAdviceCannotBeAppliedButCanBeDiscardedWithoutM7(@TempDir Path dir) throws Exception {
        var m7 = mock(RedistributionRecommendations.class);
        var store = new RunStore(mapper, dir.resolve("runs.json").toString());
        var advice = new Recommendation("c1", "charging_redirect", 1, 2, 1, "Display only", "PENDING", 0);
        store.save(new Run("run", Instant.EPOCH, null, snap(), null, null, List.of(), List.of(advice), "LOCAL_RULES", List.of()));
        var apply = new DispatchApplyService(store, m7);
        var ex = assertThrows(BusinessException.class, () -> apply.apply("run", "c1", "operator"));
        assertEquals("NOT_APPLICABLE", ex.getCode());
        var discarded = apply.discard("run", "c1", "operator");
        assertEquals("DISCARDED", discarded.recommendations().getFirst().status());
        verifyNoInteractions(m7);
    }

    private static CapacityState CapacityStateOf(Snapshot snapshot, long hubId) {
        return snapshot.capacities().stream().filter(c -> c.hubId() == hubId).findFirst().orElseThrow();
    }
}
