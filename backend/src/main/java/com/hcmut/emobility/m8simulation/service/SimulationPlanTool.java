package com.hcmut.emobility.m8simulation.service;

import com.hcmut.emobility.m8simulation.domain.AgentModels.*;
import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** Only simulates a complete candidate on fresh copies. No operational services or writes. */
@Service
public class SimulationPlanTool {
    private final SimulationEngine engine;
    public SimulationPlanTool(SimulationEngine engine) {this.engine=engine;}
    public PlanEvaluation evaluate(Snapshot snapshot,Scenario scenario,List<PlanAction> actions) {
        if(actions==null || actions.size()>4) return rejected("Use at most four actions.");
        List<Vehicle> vehicles=new ArrayList<>(snapshot.vehicles());
        Map<Long,Integer> remaining=new HashMap<>();
        for(CapacityState c:snapshot.capacities()) remaining.put(c.hubId(),Math.max(0,Math.min(c.freeSlots(),c.totalSlots()-c.usedSlots())));
        boolean reroute=scenario.reroute();Set<Long> moved=new HashSet<>();
        for(PlanAction a:actions) {
            if(a==null || a.reason()==null || a.reason().length()>500) return rejected("Each action needs a reason of at most 500 characters.");
            if("reroute".equals(a.action())) {
                if(reroute || a.fromHubId()!=scenario.hubId() || a.toHubId()!=0 || a.amount()!=0)
                    return rejected("Reroute requires fromHubId=affected hub, toHubId=0, amount=0 and must not already be enabled.");
                reroute=true;continue;
            }
            if(!"dispatch".equals(a.action())) return rejected("Only reroute and dispatch are allowed; no execution tool exists.");
            Hub target=snapshot.hubs().stream().filter(h->h.id()==a.toHubId()).findFirst().orElse(null);
            if(target==null || a.fromHubId()==a.toHubId() || a.amount()<1
                    || (scenario.disabled() && (a.fromHubId()==scenario.hubId() || a.toHubId()==scenario.hubId())))
                return rejected("Dispatch needs distinct known open hubs and a positive amount.");
            long occupied=vehicles.stream().filter(v->Objects.equals(v.hubId(),a.toHubId())).count();
            if(a.amount()>remaining.getOrDefault(a.toHubId(),0) || occupied+a.amount()>target.capacity())
                return rejected("Insufficient captured parking space; M4 reservations still require live revalidation.");
            var donor=vehicles.stream().filter(v->Objects.equals(v.hubId(),a.fromHubId()) && "AVAILABLE".equals(v.state()) && v.battery()>=20).toList();
            var eligible=donor.stream().filter(v->!moved.contains(v.id())).toList();
            if(a.amount()>donor.size()-1 || a.amount()>eligible.size()) return rejected("Leave one eligible vehicle at the donor; do not move a vehicle twice.");
            Set<Long> ids=new HashSet<>();eligible.stream().limit(a.amount()).forEach(v->ids.add(v.id()));moved.addAll(ids);
            vehicles=vehicles.stream().map(v->ids.contains(v.id())?new Vehicle(v.id(),a.toHubId(),v.battery(),v.state()):v).toList();
            remaining.compute(a.toHubId(),(id,n)->n-a.amount());
            // Released donor space is not treated as authoritative parking availability.
        }
        Snapshot copy=new Snapshot(snapshot.id(),snapshot.capturedAt(),snapshot.hubs(),vehicles,snapshot.deviceStates(),snapshot.demandSource(),snapshot.bookingWindow(),snapshot.capacities());
        Scenario fixed=new Scenario(scenario.name(),scenario.hubId(),scenario.disabled(),scenario.injectedRequests(),scenario.durationMins(),scenario.arrivalWindowMins(),scenario.tripMins(),reroute,scenario.seed());
        return new PlanEvaluation(true,List.copyOf(actions),engine.simulate(copy,fixed).kpi(),
            "Simulation only. Demand, seed and time parameters are unchanged. Parking uses provisional M1 observations; M4/M7 must revalidate before execution.");
    }
    private PlanEvaluation rejected(String reason) {return new PlanEvaluation(false,List.of(),null,reason);}
}
