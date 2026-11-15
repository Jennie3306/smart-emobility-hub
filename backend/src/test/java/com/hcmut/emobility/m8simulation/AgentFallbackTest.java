package com.hcmut.emobility.m8simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcmut.emobility.m8simulation.domain.AgentModels.*;
import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.m8simulation.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A failing AI provider must not leave a run without any recommendation. */
class AgentFallbackTest {
    @TempDir Path dir;
    private final Snapshot snapshot = new Snapshot("s", Instant.EPOCH, List.of(new Hub(1, "A", 10, 0), new Hub(2, "B", 10, 0)),
            List.of(new Vehicle(1, 2L, 90, "AVAILABLE"), new Vehicle(2, 2L, 90, "AVAILABLE")), List.of(), "test");
    private final Scenario scenario = new Scenario("demand", 1, false, 10, 60, 1, 30, false, 42);
    private final Recommendation local = new Recommendation("local-1", "dispatch", 2, 1, 1, "Local rule", "PENDING", 60);

    private Run runWith(String agentStatus, PlanEvaluation best) throws Exception {
        var snapshots = mock(SnapshotProvider.class);
        when(snapshots.capture()).thenReturn(snapshot);
        var assistant = mock(ScenarioAssistant.class);
        when(assistant.configured()).thenReturn(true);
        when(assistant.recommend(any(), any(), any(), any()))
                .thenReturn(new ScenarioAssistant.Proposals(List.of(local), "LOCAL_RULES", List.of()));
        var planner = mock(AgentPlanner.class);
        when(planner.extend(any(), any(), isNull())).thenReturn(new AgentState(1, agentStatus, "failed", List.of(), best));
        var store = new RunStore(new ObjectMapper().findAndRegisterModules(), dir.resolve(agentStatus + ".json").toString());
        return new SimulationService(snapshots, new SimulationEngine(), store, assistant, planner).run(scenario, null);
    }

    @Test void providerErrorFallsBackToLabeledLocalRules() throws Exception {
        for (String status : List.of("PROVIDER_ERROR", "INVALID_RESPONSE")) {
            var run = runWith(status, null);
            assertEquals(List.of(local), run.recommendations(), status);
            assertEquals("LOCAL_RULES", run.recommendationSource());
            assertTrue(run.warnings().stream().anyMatch(w -> w.contains("AI planning failed (" + status + ")")));
            assertEquals(status, run.agent().status());
        }
    }

    @Test void modelThatStopsKeepsToolAgentWithoutLocalRules() throws Exception {
        var run = runWith("MODEL_STOPPED", null);
        assertEquals("TOOL_AGENT", run.recommendationSource());
        assertTrue(run.recommendations().isEmpty());
    }
}
