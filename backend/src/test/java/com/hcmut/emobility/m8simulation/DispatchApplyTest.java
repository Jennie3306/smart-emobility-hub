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
import com.hcmut.emobility.shared.contracts.RedistributionRecommendations.Decision;

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

    @Test void resolvedExternallyFollowsM7DecisionWithoutCreatingAnotherProposal() throws Exception {
        var apply = new DispatchApplyService(store(), m7);
        when(m7.propose("M8_SIMULATION", "rec", 10, 20, 2, "Demand")).thenReturn("75");
        when(m7.approve("75", "operator")).thenThrow(new BusinessException(
                "RECOMMENDATION_ALREADY_RESOLVED", "Recommendation is already approved"));
        when(m7.decision("75")).thenReturn(new Decision("75", "APPROVED", List.of(201L, 202L), null));

        var result = apply.apply("run", "rec", "operator").recommendations().getFirst();
        assertEquals("APPROVED", result.status());
        assertEquals(List.of(new DispatchTask(null, 201), new DispatchTask(null, 202)), result.dispatch().tasks());
        new DispatchApplyService(openStore(), m7).apply("run", "rec", "operator");
        verify(m7, times(1)).approve("75", "operator");
        verify(m7, times(1)).propose(any(), any(), anyLong(), anyLong(), anyInt(), any());
    }

    @Test void missingM7RecommendationStillRequiresReview() throws Exception {
        var apply = new DispatchApplyService(store(), m7);
        when(m7.propose("M8_SIMULATION", "rec", 10, 20, 2, "Demand")).thenReturn("75");
        when(m7.approve("75", "operator")).thenThrow(new BusinessException("RECOMMENDATION_NOT_FOUND", "gone"));
        when(m7.decision("75")).thenThrow(new BusinessException("RECOMMENDATION_NOT_FOUND", "gone"));

        var result = apply.apply("run", "rec", "operator");
        assertEquals("NEEDS_REVIEW", result.recommendations().getFirst().status());
        assertEquals(result, new DispatchApplyService(openStore(), m7).apply("run", "rec", "operator"));
        verify(m7, times(1)).approve("75", "operator");
    }

    // M7 rejected the proposal while M8 still showed PENDING: Apply closes it instead of leaving it in NEEDS_REVIEW.
    @Test void applyAfterM7RejectClosesProposalInsteadOfSticking() throws Exception {
        var store = store(withM7Id("PENDING", "75"));
        var apply = new DispatchApplyService(store, m7);
        when(m7.approve("75", "operator")).thenThrow(new BusinessException(
                "RECOMMENDATION_ALREADY_RESOLVED", "Recommendation 75 is already REJECTED"));
        when(m7.decision("75")).thenReturn(new Decision("75", "REJECTED", List.of(), "Not needed"));

        var rec = apply.apply("run", "rec", "operator").recommendations().getFirst();
        assertEquals("DISCARDED", rec.status());
        assertTrue(rec.dispatch().message().contains("rejected"));
        assertThrows(BusinessException.class, () -> apply.discard("run", "rec", "operator"));
        verify(m7, never()).propose(any(), any(), anyLong(), anyLong(), anyInt(), any());
    }

    // A proposal waiting in NEEDS_REVIEW can be resolved from both Apply and Discard.
    @Test void stuckReviewIsResolvedFromM7Decision() throws Exception {
        when(m7.decision("75")).thenReturn(new Decision("75", "REJECTED", List.of(), null));
        var viaApply = new DispatchApplyService(store(withM7Id("NEEDS_REVIEW", "75")), m7).apply("run", "rec", "operator");
        assertEquals("DISCARDED", viaApply.recommendations().getFirst().status());

        when(m7.withdraw(eq("75"), eq("operator"), any())).thenReturn(new Decision("75", "REJECTED", List.of(), null));
        var viaDiscard = new DispatchApplyService(store(withM7Id("NEEDS_REVIEW", "75")), m7).discard("run", "rec", "operator");
        assertEquals("DISCARDED", viaDiscard.recommendations().getFirst().status());
        verify(m7, never()).approve(any(), any());
    }

    @Test void unconfirmedReviewRetriesApprovalWhenM7IsStillPending() throws Exception {
        when(m7.decision("75")).thenReturn(new Decision("75", "PENDING", List.of(), null));
        when(m7.approve("75", "operator")).thenReturn(new ApprovalResult("75", "APPROVED", List.of(301L, 302L), null));
        var rec = new DispatchApplyService(store(withM7Id("NEEDS_REVIEW", "75")), m7)
                .apply("run", "rec", "operator").recommendations().getFirst();
        assertEquals("APPROVED", rec.status());
        assertEquals(2, rec.dispatch().tasks().size());
    }

    // Discarding in M8 withdraws the M7 proposal so it can no longer be approved there.
    @Test void discardWithdrawsRegisteredM7Proposal() throws Exception {
        when(m7.withdraw(eq("75"), eq("operator"), any())).thenReturn(new Decision("75", "REJECTED", List.of(), null));
        var apply = new DispatchApplyService(store(withM7Id("PENDING", "75")), m7);
        var rec = apply.discard("run", "rec", "operator").recommendations().getFirst();
        assertEquals("DISCARDED", rec.status());
        verify(m7).withdraw(eq("75"), eq("operator"), any());
        assertEquals("DISCARDED", openStore().get("run").recommendations().getFirst().status());
        assertThrows(BusinessException.class, () -> apply.discard("run", "rec", "operator"));
    }

    @Test void discardAfterM7ApprovalFollowsM7() throws Exception {
        when(m7.withdraw(eq("75"), eq("operator"), any())).thenReturn(new Decision("75", "APPROVED", List.of(401L, 402L), null));
        var rec = new DispatchApplyService(store(withM7Id("PENDING", "75")), m7)
                .discard("run", "rec", "operator").recommendations().getFirst();
        assertEquals("APPROVED", rec.status());
        assertEquals(2, rec.dispatch().tasks().size());
    }

    @Test void discardWithoutM7ProposalDoesNotCallM7() throws Exception {
        var rec = new DispatchApplyService(store(), m7).discard("run", "rec", "operator").recommendations().getFirst();
        assertEquals("DISCARDED", rec.status());
        verifyNoInteractions(m7);
    }

    // M8 marks a plan APPLIED once its M7 tasks have finished.
    @Test void syncMarksAppliedWhenAllM7TasksFinished() throws Exception {
        var store = store(withM7Id("APPROVED", "75"));
        var apply = new DispatchApplyService(store, m7);
        when(m7.decision("75")).thenReturn(new Decision("75", "APPROVED", List.of(1L, 2L), null, 1, 1));

        var rec = apply.sync(store.list()).getFirst().recommendations().getFirst();
        assertEquals("APPLIED", rec.status());
        assertEquals("1 of 2 tasks completed in M7; 1 failed.", rec.dispatch().message());
        assertEquals("APPLIED", openStore().get("run").recommendations().getFirst().status());
        apply.sync(openStore().list());
        verify(m7, times(1)).decision("75"); // APPLIED is final: M8 stops asking M7
    }

    @Test void syncKeepsApprovedWhileTasksRunOrAllFailed() throws Exception {
        when(m7.decision("75")).thenReturn(new Decision("75", "APPROVED", List.of(1L, 2L), null, 1, 0));
        var running = new DispatchApplyService(store(withM7Id("APPROVED", "75")), m7);
        var rec = running.sync(openStore().list()).getFirst().recommendations().getFirst();
        assertEquals("APPROVED", rec.status());
        assertEquals("Approved in M7. 1 of 2 tasks finished.", rec.dispatch().message());

        when(m7.decision("75")).thenReturn(new Decision("75", "APPROVED", List.of(1L, 2L), null, 0, 2));
        var failed = running.sync(openStore().list()).getFirst().recommendations().getFirst();
        assertEquals("APPROVED", failed.status());
        assertTrue(failed.dispatch().message().contains("failed; no vehicle was moved"));
    }

    @Test void syncFollowsRejectInM7AndLeavesPendingAlone() throws Exception {
        when(m7.decision("75")).thenReturn(new Decision("75", "PENDING", List.of(), null));
        var store = store(withM7Id("PENDING", "75"));
        var apply = new DispatchApplyService(store, m7);
        assertEquals("PENDING", apply.sync(store.list()).getFirst().recommendations().getFirst().status());

        when(m7.decision("75")).thenReturn(new Decision("75", "REJECTED", List.of(), "No need"));
        assertEquals("DISCARDED", apply.sync(openStore().list()).getFirst().recommendations().getFirst().status());
    }

    @Test void syncSurvivesUnreachableM7AndSkipsRunsWithoutM7() throws Exception {
        when(m7.decision("75")).thenThrow(new IllegalStateException("M7 down"));
        var store = store(withM7Id("APPROVED", "75"));
        var runs = store.list();
        assertEquals(runs, new DispatchApplyService(store, m7).sync(runs));

        var plain = store();
        new DispatchApplyService(plain, m7).sync(plain.list());
        verify(m7, times(1)).decision(any());
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

    private Recommendation withM7Id(String status, String m7Id) {
        return new Recommendation("rec", "dispatch", 10, 20, 2, "Demand", "PENDING", 20)
                .withDispatch(status, new DispatchProgress(List.of(), null, null, null, m7Id));
    }

    private RunStore store() throws Exception {
        return store(new Recommendation("rec", "dispatch", 10, 20, 2, "Demand", "PENDING", 20));
    }

    private RunStore store(Recommendation rec) throws Exception {
        var store = openStore();
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
