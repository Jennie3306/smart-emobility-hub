package com.hcmut.emobility.m8simulation;
import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.m8simulation.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineTest {
    SimulationEngine engine=new SimulationEngine();
    Snapshot snapshot=new Snapshot("fixed",Instant.EPOCH,List.of(new Hub(1,"A",10,2),new Hub(2,"B",10,2)),
            List.of(new Vehicle(1,1L,90,"AVAILABLE"),new Vehicle(2,2L,90,"AVAILABLE"),new Vehicle(3,1L,10,"AVAILABLE")),List.of(),"test");
    Scenario scenario(boolean disabled,boolean reroute) { return new Scenario("test",1,disabled,10,60,1,30,reroute,42); }
    @Test void closedHubWithoutReroutingFailsEveryRequestAndDoesNotMutateSnapshot() {
        var before=snapshot.vehicles(); var result=engine.simulate(snapshot,scenario(true,false));
        assertEquals(10,result.kpi().failed()); assertEquals(0,result.kpi().served()); assertEquals(before,snapshot.vehicles());
    }
    @Test void sameSeedIsReproducibleAndConservesRequests() {
        var result=engine.simulate(snapshot,scenario(false,true));
        assertEquals(result,engine.simulate(snapshot,scenario(false,true)));
        assertEquals(10,result.kpi().served()+result.kpi().failed()); assertEquals(2,result.kpi().served());
        assertEquals(0,result.kpi().hubs().getLast().served());
    }
    @Test void reroutingUsesOtherHubsAndBatteryThresholdIsRespected() {
        var result=engine.simulate(snapshot,scenario(true,true));
        assertEquals(2,result.kpi().served()); assertEquals(0,result.kpi().hubs().getFirst().served());
        assertEquals(15,result.kpi().meanWaitMins());
    }
    @Test void plannerCannotEnableRerouting() {
        var tool = new SimulationPlanTool(engine);
        var action = new com.hcmut.emobility.m8simulation.domain.AgentModels.PlanAction("reroute", 1, 0, 0, "Try another hub");
        assertFalse(tool.evaluate(snapshot, scenario(true, false), List.of(action)).valid());
    }
    @Test void zeroDemandHasFiniteZeroKpis() {
        var result=engine.simulate(snapshot,new Scenario("zero",1,false,0,60,1,30,false,42));
        assertEquals(0,result.kpi().failurePercent()); assertEquals(0,result.kpi().meanWaitMins());
    }
    @Test void independentlyConfiguredHubsShareVehiclesAndNeverRerouteToAClosedHub() {
        var scenario = new Scenario("Multiple hubs", 10, 42, List.of(
                new HubSettings(1, true, 2, 1, 3, false, false),
                new HubSettings(2, false, 2, 1, 5, true, false)));

        var result = engine.simulate(snapshot, scenario);
        assertEquals(4, result.kpi().requests());
        assertEquals(2, result.kpi().served());
        assertEquals(2, result.kpi().hubs().getFirst().failed());
        assertEquals(2, result.kpi().hubs().getLast().served());
        assertEquals(2.5, result.kpi().meanWaitMins());
        assertEquals(result, engine.simulate(snapshot, scenario));
    }
    @Test void waitingRequestsCanUseTheirHubAsSoonAsItReopens() {
        var scenario = new Scenario("Scheduled closure", 10, 42, List.of(
                new HubSettings(1, true, 1, 1, 3, false, false, 0, 4),
                new HubSettings(2, true, 1, 1, 5, false, false, 2, 6)));
        var result = engine.simulate(snapshot, scenario);
        assertEquals(1, result.timeline().getFirst().served());
        assertEquals(1, result.timeline().get(3).served());
        assertEquals(2, result.timeline().get(4).served());
        assertEquals(2, result.kpi().meanWaitMins());
    }
    @Test void rejectsUnknownHubAndInvalidArrivalWindow() {
        assertThrows(IllegalArgumentException.class,()->engine.simulate(snapshot,new Scenario("bad",99,false,1,60,1,30,false,42)));
        assertThrows(IllegalArgumentException.class,()->engine.simulate(snapshot,new Scenario("bad",1,false,1,60,61,30,false,42)));
    }
    @Test void storedRunSurvivesRestart(@TempDir Path dir) throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules(); var file=dir.resolve("runs.json");
        var result=engine.simulate(snapshot,scenario(false,false));
        var run=new Run("run",Instant.EPOCH,scenario(false,false),snapshot,result.kpi(),result.kpi(),result.timeline(),List.of(),"LOCAL_RULES",List.of());
        new RunStore(mapper,file.toString()).save(run);
        assertEquals(run,new RunStore(mapper,file.toString()).get("run"));
    }
}
