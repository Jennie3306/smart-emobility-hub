package com.hcmut.emobility.m8simulation.service;

import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
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
    public SimulationService(SnapshotProvider snapshots,SimulationEngine engine,RunStore store,ScenarioAssistant assistant) {
        this.snapshots=snapshots; this.engine=engine; this.store=store; this.assistant=assistant;
    }
    public Snapshot snapshot() { return snapshots.capture(); }
    public List<Run> list() { return store.list(); }
    public Run run(Scenario scenario,String snapshotRunId) throws IOException {
        return run(scenario,snapshotRunId,false);
    }
    public Run run(Scenario scenario,String snapshotRunId,boolean repeatRecentBookings) throws IOException {
        Snapshot snapshot=snapshotRunId==null?snapshots.capture():store.get(snapshotRunId).snapshot();
        if(repeatRecentBookings) {
            if(snapshot.bookingWindow()==null) throw new IllegalArgumentException("This older snapshot has no M2 booking data. Capture a new snapshot.");
            long affectedHubId=scenario.hubId();
            int bookings=snapshot.bookingWindow().hubs().stream().filter(h->h.hubId()==affectedHubId).mapToInt(HubDemand::bookings).findFirst().orElse(0);
            scenario=new Scenario(scenario.name(),scenario.hubId(),scenario.disabled(),bookings,scenario.durationMins(),scenario.arrivalWindowMins(),scenario.tripMins(),scenario.reroute(),scenario.seed());
        }
        snapshot=new Snapshot(snapshot.id(),snapshot.capturedAt(),snapshot.hubs(),snapshot.vehicles(),snapshot.deviceStates(),
                repeatRecentBookings?"M2_BOOKING_VOLUME_REPEAT":"MANUAL_SCENARIO",snapshot.bookingWindow(),snapshot.capacities());
        assistant.validate(scenario,snapshot);
        EngineResult result=engine.simulate(snapshot,scenario);
        Kpi baseline=engine.simulate(snapshot,new Scenario(scenario.name(),scenario.hubId(),false,scenario.injectedRequests(),
                scenario.durationMins(),scenario.arrivalWindowMins(),scenario.tripMins(),scenario.reroute(),scenario.seed())).kpi();
        var proposals=assistant.recommend(snapshot,scenario,result.kpi(),engine);
        var warnings=new ArrayList<>(proposals.warnings());
        warnings.add(repeatRecentBookings?"Repeats accepted M2 booking volume from the captured 30-minute window; this is not a forecast of all attempted requests.":"Demand is manually specified, not measured arrival traffic.");
        warnings.add("Trips return to origin; charging, battery depletion and travel times are not modeled.");
        if(snapshot.deviceStates().size()>0) warnings.add("M6 device observations are displayed separately; database vehicle state is authoritative for the engine.");
        Run run=new Run(UUID.randomUUID().toString(),Instant.now(),scenario,snapshot,result.kpi(),baseline,result.timeline(),proposals.items(),proposals.source(),List.copyOf(warnings));
        store.save(run); return run;
    }
    public synchronized Run discard(String runId,String recommendationId) throws IOException {
        Run r=store.get(runId);
        Recommendation item=r.recommendations().stream().filter(a -> a.id().equals(recommendationId)).findFirst().orElseThrow();
        if(!item.status().equals("PENDING")) throw new BusinessException("ALREADY_RESOLVED","This proposal has already been resolved");
        var items=r.recommendations().stream().map(a -> a.id().equals(recommendationId)?new Recommendation(a.id(),a.action(),a.fromHubId(),a.toHubId(),a.amount(),a.reason(),"DISCARDED",a.projectedFailurePercent()):a).toList();
        Run updated=new Run(r.id(),r.createdAt(),r.scenario(),r.snapshot(),r.kpi(),r.baseline(),r.timeline(),items,r.recommendationSource(),r.warnings());
        store.save(updated); return updated;
    }
    public void apply(String runId,String recommendationId) {
        Run r=store.get(runId);
        r.recommendations().stream().filter(a -> a.id().equals(recommendationId)).findFirst().orElseThrow();
        throw new BusinessException("DISPATCH_NOT_CONNECTED","M7 execution and live-state revalidation are not connected. The proposal has not been applied.",HttpStatus.CONFLICT);
    }
    public ParseResult parse(String text) { return assistant.parse(text,snapshots.capture()); }
    public boolean aiConfigured() { return assistant.configured(); }
}
