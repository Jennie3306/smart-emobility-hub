package com.hcmut.emobility.m8simulation;
import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.m8simulation.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class RecommendationTest {
    private final SimulationEngine engine=new SimulationEngine();
    private final ScenarioAssistant assistant=new ScenarioAssistant(new ObjectMapper().findAndRegisterModules(),Validation.buildDefaultValidatorFactory().getValidator(),"","","");
    @Test void rerouteProposalIsVerifiedAgainstTheSameSnapshot() {
        var snapshot=new Snapshot("s",Instant.EPOCH,List.of(new Hub(1,"Hub A",10,2),new Hub(2,"Hub B",10,2)),List.of(new Vehicle(1,1L,90,"AVAILABLE"),new Vehicle(2,2L,90,"AVAILABLE")),List.of(),"test");
        var scenario=new Scenario("closed",1,true,10,60,1,30,false,42);
        var proposals=assistant.recommend(snapshot,scenario,engine.simulate(snapshot,scenario).kpi(),engine);
        assertEquals(1,proposals.items().size());assertEquals("reroute",proposals.items().getFirst().action());
        assertEquals(80,proposals.items().getFirst().projectedFailurePercent());
    }
    @Test void dispatchKeepsOneDonorVehicleAndRejectsFullDestination() {
        var vehicles=List.of(new Vehicle(1,2L,90,"AVAILABLE"),new Vehicle(2,2L,90,"AVAILABLE"),new Vehicle(3,2L,90,"AVAILABLE"));
        var snapshot=new Snapshot("s",Instant.EPOCH,List.of(new Hub(1,"A",10,2),new Hub(2,"B",10,2)),vehicles,List.of(),"test",null,List.of(new CapacityState(1,10,0,10,2,0)));
        var scenario=new Scenario("demand",1,false,10,60,1,30,false,42);
        var proposals=assistant.recommend(snapshot,scenario,engine.simulate(snapshot,scenario).kpi(),engine);
        var dispatch=proposals.items().stream().filter(r->r.action().equals("dispatch")).findFirst().orElseThrow();
        assertEquals(2,dispatch.amount());assertEquals(60,dispatch.projectedFailurePercent());
        var full=new Snapshot("s",Instant.EPOCH,List.of(new Hub(1,"A",0,2),new Hub(2,"B",10,2)),vehicles,List.of(),"test");
        assertTrue(assistant.recommend(full,scenario,engine.simulate(full,scenario).kpi(),engine).items().stream().noneMatch(r->r.action().equals("dispatch")));
    }

    @Test void observedParkingCapsDispatchEvenWithFewAssignedRentals() {
        var vehicles=List.of(new Vehicle(1,2L,90,"AVAILABLE"),new Vehicle(2,2L,90,"AVAILABLE"),new Vehicle(3,2L,90,"AVAILABLE"));
        var hubs=List.of(new Hub(1,"A",40,2),new Hub(2,"B",10,2));
        var scenario=new Scenario("demand",1,false,10,60,1,30,false,42);
        for(int free:List.of(0,1)) {
            var snapshot=new Snapshot("s",Instant.EPOCH,hubs,vehicles,List.of(),"test",null,List.of(new CapacityState(1,40,40-free,free,2,0)));
            var dispatches=assistant.recommend(snapshot,scenario,engine.simulate(snapshot,scenario).kpi(),engine).items().stream().filter(r->r.action().equals("dispatch")).toList();
            if(free==0) assertTrue(dispatches.isEmpty());
            else {assertEquals(1,dispatches.size());assertEquals(1,dispatches.getFirst().amount());assertTrue(dispatches.getFirst().reason().contains("live revalidation"));}
        }
        var missing=new Snapshot("s",Instant.EPOCH,hubs,vehicles,List.of(),"test");
        assertTrue(assistant.recommend(missing,scenario,engine.simulate(missing,scenario).kpi(),engine).items().stream().noneMatch(r->r.action().equals("dispatch")));
    }
    @Test void localParserDoesNotExecuteAndIdentifiesHubAndRequestCount() {
        var snapshot=new Snapshot("s",Instant.EPOCH,List.of(new Hub(1,"Hub Ga Metro",40,8)),List.of(),List.of(),"test");
        var parsed=assistant.parse("Mưa ngập Hub Ga Metro, 500 sinh viên trong 120 phút",snapshot);
        assertEquals("LOCAL_RULES",parsed.source()); assertEquals(500,parsed.scenario().injectedRequests());assertTrue(parsed.scenario().disabled());
        assertThrows(IllegalArgumentException.class,()->assistant.parse("Hub Ga Metro 10001 request",snapshot));
    }
}
