package com.hcmut.emobility.m6simulator.controller;

import com.hcmut.emobility.m6simulator.domain.SimulationRunState;
import com.hcmut.emobility.m6simulator.domain.SimulatorScenario;
import com.hcmut.emobility.m6simulator.dto.SimulationRunView;
import com.hcmut.emobility.m6simulator.dto.SimulatorStatusView;
import com.hcmut.emobility.m6simulator.service.DeviceSimulatorService;
import com.hcmut.emobility.m6simulator.service.SimulationAlreadyRunningException;
import com.hcmut.emobility.shared.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SimulatorController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origin=http://localhost:5173")
class SimulatorControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DeviceSimulatorService service;

    @Test
    void simulatorApiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/m6/simulator/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotReadSimulatorStatus() throws Exception {
        mvc.perform(get("/api/m6/simulator/status").with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorCanReadStatusAndRunScenario() throws Exception {
        SimulationRunView run = new SimulationRunView(UUID.randomUUID(), SimulatorScenario.NORMAL_TICK,
                42, null, null, 3, 3, 0, 0, List.of(), null);
        when(service.status()).thenReturn(new SimulatorStatusView(false, null,
                SimulationRunState.IDLE, null));
        when(service.runScenario(SimulatorScenario.NORMAL_TICK, 42L)).thenReturn(run);

        mvc.perform(get("/api/m6/simulator/status").with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/m6/simulator/scenarios/NORMAL_TICK")
                        .with(user("operator").roles("OPERATOR"))
                        .contentType("application/json")
                        .content("{\"seed\":42}"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownScenarioReturnsValidationErrorWithAllowedValues() throws Exception {
        mvc.perform(post("/api/m6/simulator/scenarios/UNKNOWN")
                        .with(user("operator").roles("OPERATOR"))
                        .contentType("application/json")
                        .content("{\"seed\":42}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.scenario").isArray())
                .andExpect(jsonPath("$.details.scenario[0]").value("NORMAL_TICK"))
                .andExpect(jsonPath("$.details.scenario[1]").value("VEHICLE_FAILURE"))
                .andExpect(jsonPath("$.details.scenario[2]").value("CHARGER_FAILURE"));
    }

    @Test
    void malformedAndEmptyRequestBodiesReturnValidationError() throws Exception {
        for (String body : List.of("{", "")) {
            mvc.perform(post("/api/m6/simulator/scenarios/NORMAL_TICK")
                            .with(user("operator").roles("OPERATOR"))
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details.request").exists());
        }
    }

    @Test
    void missingOrNullSeedReturnsValidationError() throws Exception {
        for (String body : List.of("{}", "{\"seed\":null}")) {
            mvc.perform(post("/api/m6/simulator/scenarios/NORMAL_TICK")
                            .with(user("operator").roles("OPERATOR"))
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void concurrentScenarioRequestReturnsConflictCode() throws Exception {
        when(service.runScenario(SimulatorScenario.NORMAL_TICK, 42L))
                .thenThrow(new SimulationAlreadyRunningException());

        mvc.perform(post("/api/m6/simulator/scenarios/NORMAL_TICK")
                        .with(user("operator").roles("OPERATOR"))
                        .contentType("application/json")
                        .content("{\"seed\":42}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIMULATOR_ALREADY_RUNNING"));
    }
}
