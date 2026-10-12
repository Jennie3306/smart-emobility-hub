package com.hcmut.emobility.m8simulation.service;

import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

/** Captures network state every 30 minutes. No inferred live demand without M2. */
@Service
public class NetworkMonitor {
    private final SnapshotProvider snapshots;
    private volatile Instant lastChecked;
    private volatile List<String> warnings=List.of("M2 demand feed is not connected; live request overload cannot be forecast yet.");
    public NetworkMonitor(SnapshotProvider snapshots) { this.snapshots=snapshots; }
    @Scheduled(fixedDelayString="${app.m8.monitor-delay-ms:1800000}",initialDelayString="${app.m8.monitor-initial-delay-ms:10000}")
    public void check() {
        try {
            Snapshot s=snapshots.capture(); var alerts=new ArrayList<String>();
            for(Hub h:s.hubs()) {
                long parked=s.vehicles().stream().filter(v -> Objects.equals(v.hubId(),h.id())).count();
                if(parked>h.capacity()) alerts.add(h.name()+": assigned vehicles exceed parking capacity.");
            }
            alerts.add("M2 demand feed is not connected; live request overload cannot be forecast yet.");
            warnings=List.copyOf(alerts); lastChecked=Instant.now();
        } catch(RuntimeException e) { warnings=List.of("Network snapshot unavailable; this check did not complete."); }
    }
    public Map<String,Object> status(boolean aiConfigured) {
        var result=new LinkedHashMap<String,Object>(); result.put("lastChecked",lastChecked);result.put("warnings",warnings);
        result.put("intervalMins",30);result.put("demandConnected",false);result.put("applyEnabled",false);result.put("aiConfigured",aiConfigured);
        return result;
    }
}
