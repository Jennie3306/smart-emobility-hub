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
                if (closed.contains(origin) && r.hub().reroute()) {
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
        var extras=new HashMap<Long,Extra>(); int[] chargingQueue=new int[scenario.durationMins()];
        for (Hub h:snapshot.hubs()) {
            var s=settings.get(h.id()); var cap=capacity(snapshot,h.id());
            extras.put(h.id(),new Extra(s==null?null:parking(s,cap,scenario),charging(s,cap,scenario,chargingQueue)));
        }
        Map<Long, Integer> failuresByOrigin = new HashMap<>();
        for (var request : queue) failuresByOrigin.merge(request.hub().hubId(), 1, Integer::sum);
        for (Hub h:snapshot.hubs()) {
            int hs=servedBy.getOrDefault(h.id(),0), hf=failuresByOrigin.getOrDefault(h.id(),0);
            hubKpis.add(new HubKpi(h.id(),h.name(),hs+hf,hs,hf,hs==0?0:waitsBy.getOrDefault(h.id(),0)*1.0/hs,
                    available.get(h.id()),peaks.get(h.id()),extras.get(h.id()).parkingRequests(),extras.get(h.id()).parkingTurnedAway(),extras.get(h.id()).parkingPeakPercent(),
                    extras.get(h.id()).chargingRequests(),extras.get(h.id()).chargingServed(),extras.get(h.id()).chargingUnserved(),
                    extras.get(h.id()).usable(),extras.get(h.id()).utilization()));
        }
        int pReq=0,pTurned=0,cReq=0,cServed=0,cUnserved=0,peakCharging=0; long cWait=0;
        for (var e:extras.values()) {
            pReq+=e.parkingRequests(); pTurned+=e.parkingTurnedAway(); cReq+=e.chargingRequests(); cServed+=e.chargingServed();
            cUnserved+=e.chargingUnserved(); cWait+=e.chargingWait();
        }
        for (int q:chargingQueue) peakCharging=Math.max(peakCharging,q);
        return new EngineResult(new Kpi(totalRequests,served,failed,
                totalRequests==0?0:failed*100.0/totalRequests,
                served==0?0:totalWait*1.0/served,peakQueue,List.copyOf(hubKpis),
                pReq,pReq-pTurned,pTurned,cReq,cServed,cUnserved,cServed==0?0:cWait*1.0/cServed,peakCharging),List.copyOf(timeline));
    }

    private record Extra(Parking parking,Charging charging) {
        int parkingRequests() { return parking==null?0:parking.requests(); }
        int parkingTurnedAway() { return parking==null?0:parking.turnedAway(); }
        double parkingPeakPercent() { return parking==null?0:parking.peakPercent(); }
        int chargingRequests() { return charging.requests(); }
        int chargingServed() { return charging.served(); }
        int chargingUnserved() { return charging.requests()-charging.served(); }
        long chargingWait() { return charging.waitSum(); }
        int usable() { return charging.usable(); }
        Double utilization() { return charging.utilization(); }
    }
    private record Parking(int requests,int turnedAway,double peakPercent) {}
    private record Charging(int requests,int served,long waitSum,int usable,Double utilization) {}
    private static final long PARKING_SALT=0x5DEECE66DL, CHARGING_SALT=0x2545F4914F6CDD1DL;
    private static Random stream(Scenario scenario,long hubId,long salt) {
        return new Random(scenario.seed()*0x9E3779B97F4A7C15L + hubId*0xBF58476D1CE4E5B9L + salt);
    }
    private static CapacityState capacity(Snapshot snapshot,long hubId) {
        return snapshot.capacities().stream().filter(c->c.hubId()==hubId).findFirst().orElse(null);
    }
    /** Private cars take a free space on arrival and leave after parkingStayMins; a full hub turns them away. */
    private static Parking parking(HubSettings hub,CapacityState cap,Scenario scenario) {
        if (hub.parkingArrivals()==0) return new Parking(0,0,0);
        int duration=scenario.durationMins(), stay=hub.parkingStayMins();
        int[] counts=new int[duration], departures=new int[duration+stay+1];
        Random random=stream(scenario,hub.hubId(),PARKING_SALT);
        for (int i=0;i<hub.parkingArrivals();i++) counts[random.nextInt(hub.arrivalWindowMins())]++;
        int free=cap.freeSlots(), used=cap.usedSlots(), peak=used, turned=0;
        for (int minute=0;minute<duration;minute++) {
            free+=departures[minute]; used-=departures[minute];
            for (int i=0;i<counts[minute];i++) {
                if (free>0) { free--; used++; departures[minute+stay]++; peak=Math.max(peak,used); } else turned++;
            }
        }
        return new Parking(hub.parkingArrivals(),turned,peak*100.0/cap.totalSlots());
    }
    /** Chargers are a fixed pool for the horizon; sessions queue first-come-first-served. */
    private static Charging charging(HubSettings hub,CapacityState cap,Scenario scenario,int[] queueTotals) {
        int operational=cap==null?0:cap.operationalChargers(), busy=cap==null?0:cap.busyChargers();
        int usable=Math.max(0,operational-(hub==null?0:hub.failedChargers())-busy);
        int duration=scenario.durationMins();
        if (hub==null || hub.chargingRequests()==0) return new Charging(0,0,0,usable,usable==0?null:0.0);
        int mins=hub.chargeMins();
        int[] counts=new int[duration], finishing=new int[duration+mins+1];
        Random random=stream(scenario,hub.hubId(),CHARGING_SALT);
        for (int i=0;i<hub.chargingRequests();i++) counts[random.nextInt(hub.arrivalWindowMins())]++;
        var queue=new ArrayDeque<Integer>(); int inUse=0, served=0; long waitSum=0, busyMinutes=0;
        for (int minute=0;minute<duration;minute++) {
            inUse-=finishing[minute];
            for (int i=0;i<counts[minute];i++) queue.add(minute);
            while (!queue.isEmpty() && inUse<usable) {
                waitSum+=minute-queue.poll(); inUse++; served++; finishing[minute+mins]++;
            }
            busyMinutes+=inUse; queueTotals[minute]+=queue.size();
        }
        return new Charging(hub.chargingRequests(),served,waitSum,usable,usable==0?null:busyMinutes*100.0/((long)usable*duration));
    }

    static SortedMap<Long, HubSettings> validateSettings(Snapshot snapshot, Scenario scenario) {
        var settings = new TreeMap<Long, HubSettings>();
        for (var hub : scenario.settings()) {
            if (snapshot.hubs().stream().noneMatch(h -> h.id() == hub.hubId()))
                throw new IllegalArgumentException("Hub does not exist in snapshot");
            if (settings.putIfAbsent(hub.hubId(), hub) != null)
                throw new IllegalArgumentException("Each affected hub must be unique");
            String name=snapshot.hubs().stream().filter(h -> h.id() == hub.hubId()).findFirst().orElseThrow().name();
            var cap=capacity(snapshot,hub.hubId());
            if (hub.parkingArrivals() > 0 && hub.parkingStayMins() < 1)
                throw new IllegalArgumentException("Parking stay must be at least 1 minute when parking arrivals are set");
            if (hub.chargingRequests() > 0 && hub.chargeMins() < 1)
                throw new IllegalArgumentException("Charge duration must be at least 1 minute when charging requests are set");
            if (hub.parkingArrivals() > 0 && (cap == null || cap.totalSlots() <= 0))
                throw new IllegalArgumentException("Parking data is unavailable for " + name + " in this snapshot.");
            int operational = cap == null ? 0 : cap.operationalChargers();
            if (hub.failedChargers() > operational)
                throw new IllegalArgumentException(name + " has only " + operational + " operational chargers.");
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
