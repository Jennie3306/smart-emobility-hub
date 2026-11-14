package com.hcmut.emobility.m8simulation;

import com.fasterxml.jackson.databind.*;
import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.m8simulation.domain.AgentModels.*;
import com.hcmut.emobility.m8simulation.service.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentPlannerTest {
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final SimulationEngine engine=new SimulationEngine();
    final SimulationPlanTool tool=new SimulationPlanTool(engine);
    final Snapshot snapshot=new Snapshot("fixed",Instant.EPOCH,List.of(new Hub(1,"A",10,2),new Hub(2,"B",10,2)),
        List.of(new Vehicle(1,2L,90,"AVAILABLE"),new Vehicle(2,2L,90,"AVAILABLE"),new Vehicle(3,2L,90,"AVAILABLE")),List.of(),"MANUAL_SCENARIO",null,
        List.of(new CapacityState(1,10,9,1,2,0),new CapacityState(2,10,3,7,2,0)));
    final Scenario scenario=new Scenario("test",1,false,10,60,1,30,false,42);
    JsonNode call(String id,String args) throws Exception {return mapper.readTree("{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\""+id+"\",\"type\":\"function\",\"function\":{\"name\":\"simulate_plan\",\"arguments\":"+mapper.writeValueAsString(args)+"}}]}");}
    String dispatch(int n){return "{\"actions\":[{\"action\":\"dispatch\",\"fromHubId\":2,\"toHubId\":1,\"amount\":"+n+",\"reason\":\"test\"}]}";}
    @Test void toolUsesObservedParkingAndDoesNotMutateSnapshot() {
        assertFalse(tool.evaluate(snapshot,scenario,List.of(new PlanAction("dispatch",2,1,2,"too many"))).valid());
        var result=tool.evaluate(snapshot,scenario,List.of(new PlanAction("dispatch",2,1,1,"fits")));
        assertTrue(result.valid());assertTrue(result.kpi().failed()<10);
        assertEquals(10,result.kpi().requests());assertEquals(2L,snapshot.vehicles().getFirst().hubId());
        assertFalse(tool.evaluate(snapshot,scenario,List.of(new PlanAction("dispatch",2,1,1,"a"),new PlanAction("dispatch",2,1,1,"b"))).valid());
    }
    @Test void modelReceivesToolFeedbackAndStopsAtThreeThenRetryContinues() throws Exception {
        List<List<Map<String,Object>>> seen=new ArrayList<>();
        AgentPlanner.Model model=messages->{seen.add(List.copyOf(messages));return call("call"+seen.size(),dispatch(seen.size()==1?2:1));};
        var planner=new AgentPlanner(mapper,tool,model);
        var first=planner.extend(snapshot,scenario,null);
        assertEquals(3,seen.size());assertEquals(3,first.totalRounds());assertEquals("ROUND_LIMIT",first.status());
        assertTrue(seen.get(1).stream().anyMatch(m->"tool".equals(m.get("role"))&&m.get("content").toString().contains("parking")));
        assertNotNull(first.best());assertEquals(1,first.best().actions().getFirst().amount());
        var next=planner.extend(snapshot,scenario,first);
        assertEquals(6,next.totalRounds());assertEquals(6,seen.size());assertEquals(first.best().kpi(),next.best().kpi());
        assertTrue(seen.get(3).stream().anyMatch(m->"tool".equals(m.get("role"))));
    }
    @Test void savesStructuredSummaryAlongsideVerifiedKpis() {
        var summary = "The destination has one free parking slot.";
        var planner = new AgentPlanner(mapper, tool, messages -> {
            assertTrue(messages.get(1).get("content").toString().contains("transferableVehicles"));
            var arguments = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(dispatch(1));
            arguments.put("summary", summary);
            return call("structured", arguments.toString());
        });
        var state = planner.extend(snapshot, scenario, null);
        assertEquals(summary, state.best().summary());
        assertEquals(summary, state.rounds().getFirst().summary());
        assertTrue(state.best().kpi().failed() < 10);
    }
    @Test void providerFailureKeepsPreviousBestAndDoesNotRetryAutomatically() throws Exception {
        var first=new AgentPlanner(mapper,tool,m->call("ok",dispatch(1))).extend(snapshot,scenario,null);
        int[] calls={0};
        var failed=new AgentPlanner(mapper,tool,m->{calls[0]++;throw new IllegalStateException("secret provider response");}).extend(snapshot,scenario,first);
        assertEquals(1,calls[0]);assertEquals(first.best(),failed.best());assertEquals("PROVIDER_ERROR",failed.status());
        assertFalse(failed.message().contains("secret"));
    }
    @Test void textOnlyCannotInventAnAcceptedPlan() throws Exception {
        var state=new AgentPlanner(mapper,tool,m->mapper.readTree("{\"role\":\"assistant\",\"content\":\"I solved it\"}")).extend(snapshot,scenario,null);
        assertEquals(1,state.totalRounds());assertNull(state.best());assertEquals("MODEL_STOPPED",state.status());
    }
    @Test void unknownToolAndInvalidArgumentsAreFeedbackNotExecution() throws Exception {
        var state=new AgentPlanner(mapper,tool,m->call("bad","{\"actions\":[],\"injectedRequests\":0}")).extend(snapshot,scenario,null);
        assertNull(state.best());assertTrue(state.rounds().stream().noneMatch(AgentRound::valid));assertEquals(10,scenario.injectedRequests());
    }
}
