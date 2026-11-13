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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DispatchApplyTest {
    @TempDir Path dir;
    private final M7DispatchClient m7 = mock(M7DispatchClient.class);
    private final SnapshotProvider snapshots = mock(SnapshotProvider.class);

    @Test void retryCreatesOnlyMissingTasksAndReplayReturnsOriginalIds() throws Exception {
        var store = store();
        var apply = new DispatchApplyService(snapshots, store, m7);
        when(m7.activeVehicles("session")).thenReturn(Set.of());
        when(m7.create("session", 1, 10, 20)).thenReturn(101L);
        when(m7.create("session", 2, 10, 20))
                .thenThrow(new M7DispatchClient.Rejected("DESTINATION_FULL")).thenReturn(102L);
        when(snapshots.capture()).thenReturn(snapshot());

        var partial = apply.apply("run", "rec", "session").recommendations().getFirst();
        assertEquals("PARTIAL", partial.status());
        assertTrue(partial.dispatch().message().contains("DESTINATION_FULL"));
        assertEquals(List.of(new DispatchTask(1, 101)), partial.dispatch().tasks());
        var reloaded = new DispatchApplyService(snapshots, openStore(), m7);
        var finished = reloaded.apply("run", "rec", "session");
        assertEquals("APPLIED", finished.recommendations().getFirst().status());
        assertEquals(List.of(new DispatchTask(1, 101), new DispatchTask(2, 102)),
                finished.recommendations().getFirst().dispatch().tasks());
        assertEquals(finished, reloaded.apply("run", "rec", "session"));
        verify(m7, times(1)).create("session", 1, 10, 20);
        verify(m7, times(2)).create("session", 2, 10, 20);
    }

    @Test void ambiguousOutcomeBlocksReplayEvenAfterReload() throws Exception {
        var store = store();
        when(m7.activeVehicles("session")).thenReturn(Set.of());
        when(m7.create("session", 1, 10, 20)).thenThrow(new IllegalStateException("lost response"));
        when(snapshots.capture()).thenReturn(snapshot());

        var result = new DispatchApplyService(snapshots, store, m7).apply("run", "rec", "session");
        assertEquals("NEEDS_REVIEW", result.recommendations().getFirst().status());
        assertEquals(result, new DispatchApplyService(snapshots, openStore(), m7)
                .apply("run", "rec", "session"));
        verify(m7, times(1)).create("session", 1, 10, 20);
        verify(m7, never()).create("session", 2, 10, 20);
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
