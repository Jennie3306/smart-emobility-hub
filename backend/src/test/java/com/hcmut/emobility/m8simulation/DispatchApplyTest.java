package com.hcmut.emobility.m8simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcmut.emobility.m8simulation.domain.AgentModels.*;
import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.m8simulation.service.*;
import com.hcmut.emobility.shared.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import com.hcmut.emobility.shared.contracts.RedistributionRecommendations;
import com.hcmut.emobility.shared.contracts.RedistributionRecommendations.ApprovalResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DispatchApplyTest {
    @TempDir Path dir;
    private final RedistributionRecommendations m7 = mock(RedistributionRecommendations.class);

    @Test void partialRetryReusesM7RecommendationAndAccumulatesTaskIds() throws Exception {
        var apply = new DispatchApplyService(store(), m7);
        when(m7.propose("M8_SIMULATION", "rec", 10, 20, 2, "Demand")).thenReturn("75");
        when(m7.approve("75", "operator"))
                .thenReturn(new ApprovalResult("75", "PARTIAL", List.of(101L), "Destination full"),
                        new ApprovalResult("75", "APPROVED", List.of(102L), null));

        var partial = apply.apply("run", "rec", "operator").recommendations().getFirst();
        assertEquals("PARTIAL", partial.status());
        assertEquals("75", partial.dispatch().m7RecommendationId());
        assertEquals(List.of(new DispatchTask(null, 101)), partial.dispatch().tasks());
        var reloaded = new DispatchApplyService(openStore(), m7);
        var finished = reloaded.apply("run", "rec", "operator");
        assertEquals("APPROVED", finished.recommendations().getFirst().status());
        assertEquals(List.of(new DispatchTask(null, 101), new DispatchTask(null, 102)),
                finished.recommendations().getFirst().dispatch().tasks());
        assertEquals(finished, reloaded.apply("run", "rec", "operator"));
        verify(m7, times(1)).propose("M8_SIMULATION", "rec", 10, 20, 2, "Demand");
        verify(m7, times(2)).approve("75", "operator");
    }

    @Test void resolvedExternallyRequiresReviewWithoutCreatingAnotherProposal() throws Exception {
        var apply = new DispatchApplyService(store(), m7);
        when(m7.propose("M8_SIMULATION", "rec", 10, 20, 2, "Demand")).thenReturn("75");
        when(m7.approve("75", "operator")).thenThrow(new BusinessException(
                "RECOMMENDATION_ALREADY_RESOLVED", "Recommendation is already approved"));

        var result = apply.apply("run", "rec", "operator");
        assertEquals("NEEDS_REVIEW", result.recommendations().getFirst().status());
        assertEquals(result, new DispatchApplyService(openStore(), m7).apply("run", "rec", "operator"));
        verify(m7, times(1)).approve("75", "operator");
    }

    @Test void savedAiPlanExposesStableDispatchCardAndKeepsRerouteInSimulation() throws Exception {
        var evaluation = new PlanEvaluation(true, List.of(new PlanAction("reroute", 20, 0, 0, "Reroute"),
                new PlanAction("dispatch", 10, 20, 2, "Move two vehicles")),
                new Kpi(10, 8, 2, 20, 0, 2, List.of()), "Tested");
        var agent = new AgentState(1, "ROUND_LIMIT", "", List.of(), evaluation);
        var run = new Run("run", Instant.EPOCH, null, snapshot(), null, null, List.of(), List.of(),
                "TOOL_AGENT", List.of(), agent);
        var store = openStore();
        store.save(run);
        assertEquals(1, run.recommendations().size());
        assertEquals("dispatch", run.recommendations().getFirst().action());
        assertEquals(run.recommendations(), openStore().get("run").recommendations());
    }

    private RunStore openStore() throws Exception {
        return new RunStore(new ObjectMapper().findAndRegisterModules(), dir.resolve("runs.json").toString());
    }

    private RunStore store() throws Exception {
        var store = openStore();
        var rec = new Recommendation("rec", "dispatch", 10, 20, 2, "Demand", "PENDING", 20);
        store.save(new Run("run", Instant.EPOCH, null, snapshot(), null, null, List.of(), List.of(rec),
                "LOCAL_RULES", List.of()));
        return store;
    }

    private Snapshot snapshot() {
        return new Snapshot("live", Instant.EPOCH, List.of(new Hub(10,"A",10,0), new Hub(20,"B",10,0)),
                List.of(new Vehicle(3,10L,100,"AVAILABLE"), new Vehicle(1,10L,80,"AVAILABLE"),
                        new Vehicle(2,10L,60,"AVAILABLE"), new Vehicle(4,10L,10,"AVAILABLE")), List.of(), "test");
    }
}
