package com.hcmut.emobility.m8simulation;

import com.fasterxml.jackson.databind.*;
import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.m8simulation.service.*;
import com.hcmut.emobility.shared.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentSessionTest {
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    @Test void retryPersistsContextAndRejectsStaleOrConcurrentRequests(@TempDir Path dir) throws Exception {
        var snapshot=new Snapshot("fixed",Instant.EPOCH,List.of(new Hub(1,"A",10,2)),List.of(),List.of(),"MANUAL_SCENARIO");
        var scenario=new Scenario("saved",1,true,10,60,30,30,false,42);
        var result=new SimulationEngine().simulate(snapshot,scenario);
        var run=new Run("run",Instant.EPOCH,scenario,snapshot,result.kpi(),result.kpi(),result.timeline(),List.of(),"LOCAL_RULES",List.of());
        var file=dir.resolve("runs.json");var store=new RunStore(mapper,file.toString());store.save(run);
        var assistant=mock(ScenarioAssistant.class);when(assistant.configured()).thenReturn(true);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var planner=new AgentPlanner(mapper,new SimulationPlanTool(new SimulationEngine()),messages->{entered.countDown();release.await(5,TimeUnit.SECONDS);return mapper.readTree("{\"content\":\"done\"}");});
        var service=new SimulationService(mock(SnapshotProvider.class),new SimulationEngine(),store,assistant,planner);
        try(var executor=Executors.newSingleThreadExecutor()) {
            var pending=executor.submit(()->service.retry("run",0));assertTrue(entered.await(3,TimeUnit.SECONDS));
            assertThrows(BusinessException.class,()->service.retry("run",0));release.countDown();
            var updated=pending.get(5,TimeUnit.SECONDS);assertEquals(1,updated.agent().totalRounds());
            assertEquals(run.snapshot(),updated.snapshot());assertEquals(run.scenario(),updated.scenario());assertEquals(run.kpi(),updated.kpi());
            assertEquals(updated,new RunStore(mapper,file.toString()).get("run"));
            assertThrows(BusinessException.class,()->service.retry("run",0));
            assertEquals(2,service.retry("run",1).agent().totalRounds());
        }
    }
    @Test void legacyJsonWithoutAgentLoads() throws Exception {
        var fixture=new AgentPlannerTest();var result=fixture.engine.simulate(fixture.snapshot,fixture.scenario);
        var run=new Run("old",Instant.EPOCH,fixture.scenario,fixture.snapshot,result.kpi(),result.kpi(),result.timeline(),List.of(),"LOCAL_RULES",List.of());
        var json=mapper.valueToTree(run);((com.fasterxml.jackson.databind.node.ObjectNode)json).remove("agent");
        assertNull(mapper.treeToValue(json,Run.class).agent());
    }
}
