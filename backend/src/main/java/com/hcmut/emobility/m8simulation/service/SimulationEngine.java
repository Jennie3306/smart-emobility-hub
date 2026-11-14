package com.hcmut.emobility.m8simulation.service;

import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import org.springframework.stereotype.Component;
import java.util.*;

/** One-minute steps. Requests wait until horizon; served trips return to their origin after tripMins.
 * Only AVAILABLE vehicles with battery >=20 are eligible. No entities/events/database writes. */
@Component
public class SimulationEngine {
    private record Request(int arrival, HubSettings hub) {}
    private record Return(int minute, long hubId) {}
    public EngineResult simulate(Snapshot snapshot, Scenario scenario) {
        var settings = validateSettings(snapshot, scenario);
        Map<Long,Integer> available = new LinkedHashMap<>(), parked = new LinkedHashMap<>(), servedBy = new HashMap<>();
        Map<Long,Double> peaks = new HashMap<>();
        for (Hub h : snapshot.hubs()) { available.put(h.id(),0); parked.put(h.id(),0); }
        for (Vehicle v : snapshot.vehicles()) if (v.hubId()!=null && available.containsKey(v.hubId())) {
            parked.merge(v.hubId(),1,Integer::sum);
            if (v.state().equals("AVAILABLE") && v.battery()>=20) available.merge(v.hubId(),1,Integer::sum);
        }
        for (Hub h : snapshot.hubs()) peaks.put(h.id(), h.capacity()==0 ? 0.0 : parked.get(h.id())*100.0/h.capacity());
        var arrivals = new TreeMap<Long, int[]>();
        int totalRequests = 0;
        for (var hub : settings.values()) {
            int[] counts = new int[scenario.durationMins()];
            Random random = new Random(settings.size() == 1 ? scenario.seed() : scenario.seed() ^ Long.rotateLeft(hub.hubId(), 32));
            for (int i = 0; i < hub.injectedRequests(); i++) counts[random.nextInt(hub.arrivalWindowMins())]++;
            arrivals.put(hub.hubId(), counts);
            totalRequests += hub.injectedRequests();
        }
        var queue = new ArrayDeque<Request>(); var returns = new ArrayList<Return>();
        var timeline = new ArrayList<Point>(); var waitsBy = new HashMap<Long,Integer>();
        int served=0,totalWait=0,peakQueue=0;
        for (int minute=0;minute<scenario.durationMins();minute++) {
            for (Iterator<Return> it=returns.iterator();it.hasNext();) {
                Return r=it.next(); if (r.minute()<=minute) { available.merge(r.hubId(),1,Integer::sum); it.remove(); }
            }
            final int tick = minute;
            var closed = settings.values().stream().filter(h -> h.closedAt(tick)).map(HubSettings::hubId)
                    .collect(java.util.stream.Collectors.toSet());
            for (var hub : settings.values())
                for (int i = 0; i < arrivals.get(hub.hubId())[minute]; i++) queue.add(new Request(minute, hub));
            peakQueue=Math.max(peakQueue,queue.size());
            var blocked = new HashSet<Long>();
            for (var it = queue.iterator(); it.hasNext();) {
                Request r = it.next();
                long origin = r.hub().hubId();
                if (blocked.contains(origin)) continue;
                Long target = !closed.contains(origin) && available.get(origin)>0 ? origin : null;
                if (target==null && r.hub().reroute()) {
                    target=snapshot.hubs().stream().map(Hub::id)
                        .filter(id -> id!=origin && !closed.contains(id) && available.get(id)>0).findFirst().orElse(null);
                }
                if (target==null) {
                    blocked.add(origin);
                    if (blocked.size() == settings.size()) break;
                    continue;
                }
                it.remove(); int wait=minute-r.arrival();
                available.merge(target,-1,Integer::sum); returns.add(new Return(minute+r.hub().tripMins(),target));
                served++; totalWait+=wait; servedBy.merge(target,1,Integer::sum); waitsBy.merge(target,wait,Integer::sum);
            }
            timeline.add(new Point(minute,served,queue.size(),minute==scenario.durationMins()-1 ? queue.size():0));
        }
        int failed=queue.size(); var hubKpis=new ArrayList<HubKpi>();
        Map<Long, Integer> failuresByOrigin = new HashMap<>();
        for (var request : queue) failuresByOrigin.merge(request.hub().hubId(), 1, Integer::sum);
        for (Hub h:snapshot.hubs()) {
            int hs=servedBy.getOrDefault(h.id(),0), hf=failuresByOrigin.getOrDefault(h.id(),0);
            hubKpis.add(new HubKpi(h.id(),h.name(),hs+hf,hs,hf,hs==0?0:waitsBy.getOrDefault(h.id(),0)*1.0/hs,
                    available.get(h.id()),peaks.get(h.id())));
        }
        return new EngineResult(new Kpi(totalRequests,served,failed,
                totalRequests==0?0:failed*100.0/totalRequests,
                served==0?0:totalWait*1.0/served,peakQueue,List.copyOf(hubKpis)),List.copyOf(timeline));
    }

    static SortedMap<Long, HubSettings> validateSettings(Snapshot snapshot, Scenario scenario) {
        var settings = new TreeMap<Long, HubSettings>();
        for (var hub : scenario.settings()) {
            if (snapshot.hubs().stream().noneMatch(h -> h.id() == hub.hubId()))
                throw new IllegalArgumentException("Hub does not exist in snapshot");
            if (settings.putIfAbsent(hub.hubId(), hub) != null)
                throw new IllegalArgumentException("Each affected hub must be unique");
            if (hub.arrivalWindowMins() > scenario.durationMins())
                throw new IllegalArgumentException("Arrival window must not exceed duration");
            if (hub.disabled() && (hub.closeFromMins() >= scenario.durationMins()
                    || (hub.reopenAtMins() != null && (hub.reopenAtMins() <= hub.closeFromMins()
                    || hub.reopenAtMins() > scenario.durationMins()))))
                throw new IllegalArgumentException("Closure must start before reopening and fit within the simulation horizon");
        }
        return settings;
    }
}
