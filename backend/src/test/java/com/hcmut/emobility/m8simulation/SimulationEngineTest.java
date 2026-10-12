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
        assertEquals(10,result.kpi().served()+result.kpi().failed()); assertEquals(4,result.kpi().served());
    }
    @Test void reroutingUsesOtherHubsAndBatteryThresholdIsRespected() {
        var result=engine.simulate(snapshot,scenario(true,true));
        assertEquals(2,result.kpi().served()); assertEquals(0,result.kpi().hubs().getFirst().served());
        assertEquals(15,result.kpi().meanWaitMins());
    }
    @Test void zeroDemandHasFiniteZeroKpis() {
        var result=engine.simulate(snapshot,new Scenario("zero",1,false,0,60,1,30,false,42));
        assertEquals(0,result.kpi().failurePercent()); assertEquals(0,result.kpi().meanWaitMins());
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
