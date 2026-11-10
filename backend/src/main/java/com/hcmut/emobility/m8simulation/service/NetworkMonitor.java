package com.hcmut.emobility.m8simulation.service;

import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

/** Repeats observed booking volume against a consistent current snapshot. It is not an arrival forecast. */
@Service
public class NetworkMonitor {
    private final SnapshotProvider snapshots;
    private final SimulationEngine engine;
    public record Forecast(long hubId,String hubName,int recentBookings,int activeHolds,int projectedServed,int projectedFailed) {}
    private record State(Instant lastChecked,List<String> warnings,List<Forecast> forecasts,boolean connected) {}
    private volatile State state=new State(null,List.of("The first booking-volume check has not run yet."),List.of(),false);
    public NetworkMonitor(SnapshotProvider snapshots,SimulationEngine engine) { this.snapshots=snapshots;this.engine=engine; }
    @Scheduled(fixedDelayString="${app.m8.monitor-delay-ms:1800000}",initialDelayString="${app.m8.monitor-initial-delay-ms:10000}")
    public synchronized void check() {
        try {
            Snapshot s=snapshots.capture(); var alerts=new ArrayList<String>();var forecasts=new ArrayList<Forecast>();
            for(Hub h:s.hubs()) {
                HubDemand demand=s.bookingWindow()==null?null:s.bookingWindow().hubs().stream().filter(d->d.hubId()==h.id()).findFirst().orElse(null);
                if(demand==null) continue;
                if(demand.bookings()>10000) {alerts.add(h.name()+": booking volume exceeds this model's 10,000-request limit.");continue;}
                var scenario=new Scenario("Repeat recent bookings",h.id(),false,demand.bookings(),30,30,30,false,42);
                var kpi=engine.simulate(s,scenario).kpi();
                forecasts.add(new Forecast(h.id(),h.name(),demand.bookings(),demand.activeHolds(),kpi.served(),kpi.failed()));
                if(kpi.failed()>0) alerts.add(h.name()+": "+kpi.failed()+" requests would remain unserved if the last 30-minute booking volume repeated, without rerouting.");
            }
            if(s.bookingWindow()==null) alerts.add("This snapshot has no M2 booking window.");
            else if(forecasts.stream().allMatch(f->f.recentBookings()==0)) alerts.add("No accepted bookings in the last 30 minutes. Unrecorded searches and failed attempts are not counted.");
            state=new State(s.capturedAt(),List.copyOf(alerts),List.copyOf(forecasts),s.bookingWindow()!=null);
        } catch(RuntimeException e) { state=new State(state.lastChecked(),List.of("Network snapshot unavailable; this check did not complete."),List.of(),false); }
    }
    public Map<String,Object> status(boolean aiConfigured) {
        State current=state;
        var result=new LinkedHashMap<String,Object>(); result.put("lastChecked",current.lastChecked());result.put("warnings",current.warnings());result.put("forecasts",current.forecasts());
        result.put("intervalMins",30);result.put("demandConnected",current.connected());result.put("applyEnabled",false);result.put("aiConfigured",aiConfigured);
        result.put("demandDescription","Accepted M2 bookings in the past 30 minutes; not all attempted demand.");
        return result;
    }
}
