package com.hcmut.emobility.m8simulation;
import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.m8simulation.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.nio.file.Path;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class BookingVolumeTest {
    @Test void repeatsCapturedCountsAndPreservesProvenance(@TempDir Path dir) throws Exception {
        var snapshot=new Snapshot("frozen",Instant.EPOCH,List.of(new Hub(1,"A",10,2)),List.of(new Vehicle(1,1L,90,"AVAILABLE")),List.of(),"MANUAL_SCENARIO",
            new BookingWindow(Instant.EPOCH.minusSeconds(1800),Instant.EPOCH,30,List.of(new HubDemand(1,3,0))),List.of());
        var provider=mock(SnapshotProvider.class);when(provider.capture()).thenReturn(snapshot);
        var assistant=mock(ScenarioAssistant.class);
        when(assistant.recommend(any(),any(),any(),any())).thenReturn(new ScenarioAssistant.Proposals(List.of(),"LOCAL_RULES",List.of()));
        var store=new RunStore(new ObjectMapper().findAndRegisterModules(),dir.resolve("runs.json").toString());
        var service=new SimulationService(provider,new SimulationEngine(),store,assistant);
        var scenario=new Scenario("repeat",1,false,100,60,30,30,false,42);
        Run first=service.run(scenario,null,true);
        assertEquals(3,first.scenario().injectedRequests());assertEquals("M2_BOOKING_VOLUME_REPEAT",first.snapshot().demandSource());
        Run second=service.run(scenario,first.id(),true);
        assertEquals(first.kpi(),second.kpi());verify(provider,times(1)).capture();
        var monitor=new NetworkMonitor(provider,new SimulationEngine());monitor.check();
        assertEquals(true,monitor.status(false).get("demandConnected"));
    }
    @Test void legacySnapshotRejectsBookingMode(@TempDir Path dir) throws Exception {
        var snapshot=new Snapshot("old",Instant.EPOCH,List.of(new Hub(1,"A",10,2)),List.of(),List.of(),"test");
        var provider=mock(SnapshotProvider.class);when(provider.capture()).thenReturn(snapshot);
        var service=new SimulationService(provider,new SimulationEngine(),new RunStore(new ObjectMapper().findAndRegisterModules(),dir.resolve("runs.json").toString()),mock(ScenarioAssistant.class));
        assertThrows(IllegalArgumentException.class,()->service.run(new Scenario("x",1,false,1,30,30,30,false,42),null,true));
    }

    @Test void mixedDemandUsesCapturedBookingsOnlyForTheHubThatSelectedThem(@TempDir Path dir) throws Exception {
        var snapshot = new Snapshot("frozen", Instant.EPOCH, List.of(new Hub(1,"A",10,2), new Hub(2,"B",10,2)),
                List.of(), List.of(), "test",
                new BookingWindow(Instant.EPOCH.minusSeconds(1800), Instant.EPOCH, 30,
                        List.of(new HubDemand(1,3,0), new HubDemand(2,99,0))), List.of());
        var provider = mock(SnapshotProvider.class);
        when(provider.capture()).thenReturn(snapshot);
        var assistant = new ScenarioAssistant(new ObjectMapper(), jakarta.validation.Validation
                .buildDefaultValidatorFactory().getValidator(), "", "", "");
        var service = new SimulationService(provider, new SimulationEngine(),
                new RunStore(new ObjectMapper().findAndRegisterModules(), dir.resolve("runs.json").toString()), assistant);
        var scenario = new Scenario("Mixed", 30, 42, List.of(
                new HubSettings(1, false, 0, 10, 15, false, true),
                new HubSettings(2, true, 7, 5, 20, false, false, 2, 8)));
        var run = service.run(scenario, null);
        assertEquals("MIXED_SCENARIO", run.snapshot().demandSource());
        assertEquals(10, run.kpi().requests());
        assertEquals(3, run.scenario().settings().getFirst().injectedRequests());
        assertEquals(scenario.settings().getLast(), run.scenario().settings().getLast());
        assertEquals(run, service.list().getFirst());
    }
}
