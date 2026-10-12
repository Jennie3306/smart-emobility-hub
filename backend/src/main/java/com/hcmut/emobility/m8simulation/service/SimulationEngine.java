package com.hcmut.emobility.m8simulation.service;

import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import org.springframework.stereotype.Component;
import java.util.*;

/** One-minute steps. Requests wait until horizon; served trips return to their origin after tripMins.
 * Only AVAILABLE vehicles with battery >=20 are eligible. No entities/events/database writes. */
@Component
public class SimulationEngine {
    private record Request(int arrival) {}
    private record Return(int minute, long hubId) {}
    public EngineResult simulate(Snapshot snapshot, Scenario scenario) {
        if (snapshot.hubs().stream().noneMatch(h -> h.id()==scenario.hubId()))
            throw new IllegalArgumentException("Hub does not exist in snapshot");
        if (scenario.arrivalWindowMins()>scenario.durationMins())
            throw new IllegalArgumentException("Arrival window must not exceed duration");
        Map<Long,Integer> available = new LinkedHashMap<>(), parked = new LinkedHashMap<>(), servedBy = new HashMap<>();
        Map<Long,Double> peaks = new HashMap<>();
        for (Hub h : snapshot.hubs()) { available.put(h.id(),0); parked.put(h.id(),0); }
        for (Vehicle v : snapshot.vehicles()) if (v.hubId()!=null && available.containsKey(v.hubId())) {
            parked.merge(v.hubId(),1,Integer::sum);
            if (v.state().equals("AVAILABLE") && v.battery()>=20) available.merge(v.hubId(),1,Integer::sum);
        }
        for (Hub h : snapshot.hubs()) peaks.put(h.id(), h.capacity()==0 ? 0.0 : parked.get(h.id())*100.0/h.capacity());
        int[] arrivals = new int[scenario.durationMins()];
        Random random = new Random(scenario.seed());
        for (int i=0;i<scenario.injectedRequests();i++) arrivals[random.nextInt(scenario.arrivalWindowMins())]++;
        var queue = new ArrayDeque<Request>(); var returns = new ArrayList<Return>();
        var timeline = new ArrayList<Point>(); var waitsBy = new HashMap<Long,Integer>();
        int served=0,totalWait=0,peakQueue=0;
        for (int minute=0;minute<scenario.durationMins();minute++) {
            for (Iterator<Return> it=returns.iterator();it.hasNext();) {
                Return r=it.next(); if (r.minute()<=minute) { available.merge(r.hubId(),1,Integer::sum); it.remove(); }
            }
            for (int i=0;i<arrivals[minute];i++) queue.add(new Request(minute));
            peakQueue=Math.max(peakQueue,queue.size());
            while (!queue.isEmpty()) {
                Long target = (!scenario.disabled() && available.get(scenario.hubId())>0) ? scenario.hubId() : null;
                if (target==null && scenario.reroute()) {
                    target=snapshot.hubs().stream().map(Hub::id)
                        .filter(id -> id!=scenario.hubId() && available.get(id)>0).findFirst().orElse(null);
                }
                if (target==null) break;
                Request r=queue.remove(); int wait=minute-r.arrival();
                available.merge(target,-1,Integer::sum); returns.add(new Return(minute+scenario.tripMins(),target));
                served++; totalWait+=wait; servedBy.merge(target,1,Integer::sum); waitsBy.merge(target,wait,Integer::sum);
            }
            timeline.add(new Point(minute,served,queue.size(),minute==scenario.durationMins()-1 ? queue.size():0));
        }
        int failed=queue.size(); var hubKpis=new ArrayList<HubKpi>();
        for (Hub h:snapshot.hubs()) {
            int hs=servedBy.getOrDefault(h.id(),0), hf=h.id()==scenario.hubId()?failed:0;
            hubKpis.add(new HubKpi(h.id(),h.name(),hs+hf,hs,hf,hs==0?0:waitsBy.getOrDefault(h.id(),0)*1.0/hs,
                    available.get(h.id()),peaks.get(h.id())));
        }
        return new EngineResult(new Kpi(scenario.injectedRequests(),served,failed,
                scenario.injectedRequests()==0?0:failed*100.0/scenario.injectedRequests(),
                served==0?0:totalWait*1.0/served,peakQueue,List.copyOf(hubKpis)),List.copyOf(timeline));
    }
}
