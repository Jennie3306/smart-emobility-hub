package com.hcmut.emobility.m8simulation.service;

import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.m8simulation.domain.AgentModels.AgentState;
import org.springframework.beans.factory.annotation.Autowired;
import com.hcmut.emobility.shared.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
public class SimulationService {
    private final SnapshotProvider snapshots; private final SimulationEngine engine;
    private final RunStore store; private final ScenarioAssistant assistant;
    private final AgentPlanner planner;
    private final Set<String> planning=java.util.concurrent.ConcurrentHashMap.newKeySet();
    @Autowired
    public SimulationService(SnapshotProvider snapshots,SimulationEngine engine,RunStore store,ScenarioAssistant assistant,AgentPlanner planner) {
        this.snapshots=snapshots;this.engine=engine;this.store=store;this.assistant=assistant;this.planner=planner;
    }
    public SimulationService(SnapshotProvider snapshots,SimulationEngine engine,RunStore store,ScenarioAssistant assistant) {
        this(snapshots,engine,store,assistant,null);
    }
    public Snapshot snapshot() { return snapshots.capture(); }
    public List<Run> list() { return store.list(); }
    public Run run(Scenario scenario,String snapshotRunId) throws IOException {
        return run(scenario,snapshotRunId,false);
    }
    public Run run(Scenario scenario,String snapshotRunId,boolean repeatRecentBookings) throws IOException {
        Snapshot snapshot=snapshotRunId==null?snapshots.capture():store.get(snapshotRunId).snapshot();
        List<HubSettings> settings = scenario.settings();
        boolean repeats = repeatRecentBookings || settings.stream().anyMatch(HubSettings::repeatBookings);
        boolean manual = !repeatRecentBookings && settings.stream().anyMatch(h -> !h.repeatBookings());
        if(repeats) {
            if(snapshot.bookingWindow()==null) throw new IllegalArgumentException("This older snapshot has no M2 booking data. Capture a new snapshot.");
            var demand = snapshot.bookingWindow().hubs().stream()
                    .collect(java.util.stream.Collectors.toMap(HubDemand::hubId, HubDemand::bookings));
            scenario = scenario.withSettings(settings.stream()
                    .map(h -> repeatRecentBookings || h.repeatBookings() ? h.withRequests(demand.getOrDefault(h.hubId(), 0), true) : h)
                    .toList());
        }
        snapshot=new Snapshot(snapshot.id(),snapshot.capturedAt(),snapshot.hubs(),snapshot.vehicles(),snapshot.deviceStates(),
                repeats ? (manual ? "MIXED_SCENARIO" : "M2_BOOKING_VOLUME_REPEAT") : "MANUAL_SCENARIO",snapshot.bookingWindow(),snapshot.capacities());
        assistant.validate(scenario,snapshot);
        EngineResult result=engine.simulate(snapshot,scenario);
        Kpi baseline=engine.simulate(snapshot,scenario.withSettings(scenario.settings().stream().map(HubSettings::opened).toList())).kpi();
        AgentState agent=assistant.configured()?planner.extend(snapshot,scenario,null):null;
        var proposals=agent==null?assistant.recommend(snapshot,scenario,result.kpi(),engine)
            :new ScenarioAssistant.Proposals(List.of(),"TOOL_AGENT",List.of("AI plans call simulate_plan and use actual KPI feedback. Only improving validated plans are retained. No live execution."));
        var warnings=new ArrayList<>(proposals.warnings());
        if (repeats) warnings.add("Booking-based demand repeats each selected hub's accepted M2 bookings from the captured 30-minute window.");
        if (manual) warnings.add("Manually entered demand represents hypothetical rental requests.");
        warnings.add("Trips return to origin; charging, battery depletion and travel times are not modeled.");
        if(snapshot.deviceStates().size()>0) warnings.add("M6 device observations are displayed separately; database vehicle state is authoritative for the engine.");
        Run run=new Run(UUID.randomUUID().toString(),Instant.now(),scenario,snapshot,result.kpi(),baseline,result.timeline(),proposals.items(),proposals.source(),List.copyOf(warnings),agent);
        store.save(run); return run;
    }
    public Run discard(String runId,String recommendationId) throws IOException {
        synchronized (store) { return discardLocked(runId, recommendationId); }
    }
    private Run discardLocked(String runId,String recommendationId) throws IOException {
        Run r=store.get(runId);
        Recommendation item=r.recommendations().stream().filter(a -> a.id().equals(recommendationId)).findFirst().orElseThrow();
        if(!item.status().equals("PENDING")) throw new BusinessException("ALREADY_RESOLVED","This proposal has already been resolved");
        var items=r.recommendations().stream().map(a -> a.id().equals(recommendationId)?new Recommendation(a.id(),a.action(),a.fromHubId(),a.toHubId(),a.amount(),a.reason(),"DISCARDED",a.projectedFailurePercent()):a).toList();
        Run updated=new Run(r.id(),r.createdAt(),r.scenario(),r.snapshot(),r.kpi(),r.baseline(),r.timeline(),items,r.recommendationSource(),r.warnings(),r.agent());
        store.save(updated); return updated;
    }
    public ParseResult parse(String text) { return assistant.parse(text,snapshots.capture()); }
    public boolean aiConfigured() { return assistant.configured(); }
    public Run retry(String runId,int expectedRounds) throws IOException {
        if(!assistant.configured()) throw new BusinessException("AI_NOT_CONFIGURED","Configure the AI provider before retrying.",HttpStatus.CONFLICT);
        if(!planning.add(runId)) throw new BusinessException("PLANNING_IN_PROGRESS","This run is already being planned.",HttpStatus.CONFLICT);
        try {
            Run r=store.get(runId);
            ensurePlanningEditable(r);
            if(expectedRounds!=(r.agent()==null?0:r.agent().totalRounds())) throw new BusinessException("STALE_AGENT_STATE","Reload this saved run before retrying.",HttpStatus.CONFLICT);
            AgentState next=planner.extend(r.snapshot(),r.scenario(),r.agent());
            return saveAgent(runId,next);
        } finally {planning.remove(runId);}
    }
    private Run saveAgent(String runId,AgentState agent) throws IOException {
        synchronized (store) { return saveAgentLocked(runId, agent); }
    }
    private Run saveAgentLocked(String runId,AgentState agent) throws IOException {
        // Re-read after the network calls so a concurrent Discard is preserved.
        Run r=store.get(runId);
        ensurePlanningEditable(r);
        Run updated=new Run(r.id(),r.createdAt(),r.scenario(),r.snapshot(),r.kpi(),r.baseline(),r.timeline(),r.recommendations(),"TOOL_AGENT",r.warnings(),agent);
        store.save(updated);return updated;
    }
    private void ensurePlanningEditable(Run run) {
        if (run.recommendations().stream().anyMatch(r -> List.of("PARTIAL", "APPLIED", "APPROVED", "NEEDS_REVIEW").contains(r.status()))) {
            throw new BusinessException("DISPATCH_ALREADY_STARTED", "Start a new scenario to explore another plan after dispatch has started.");
        }
    }
}
