package com.hcmut.emobility.m8simulation;
import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.m8simulation.service.SnapshotProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"app.m8.store-file=target/m8-security-test/runs.json","app.m8.monitor-initial-delay-ms=3600000"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class SimulationSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean SnapshotProvider snapshots;
    @Test void anonymousIsRejected() throws Exception { mvc.perform(get("/api/m8/snapshot")).andExpect(status().isUnauthorized()); }
    @Test @WithMockUser(roles="STUDENT") void studentIsRejected() throws Exception { mvc.perform(get("/api/m8/snapshot")).andExpect(status().isForbidden()); }
    @Test @WithMockUser(roles="STUDENT") void studentCannotExtendPlanning() throws Exception {mvc.perform(post("/api/m8/runs/run/agent/retry?expectedRounds=0")).andExpect(status().isForbidden());}
    @Test @WithMockUser(roles="STUDENT") void studentCannotApplyDispatch() throws Exception {
        mvc.perform(post("/api/m8/runs/run/recommendations/rec/apply")).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(roles="OPERATOR") void operatorCanSeeApplyEnabled() throws Exception {
        mvc.perform(get("/api/m8/status")).andExpect(status().isOk()).andExpect(jsonPath("$.applyEnabled").value(true));
    }
    @Test void anonymousCannotExtendPlanning() throws Exception {mvc.perform(post("/api/m8/runs/run/agent/retry?expectedRounds=0")).andExpect(status().isUnauthorized());}
    @Test @WithMockUser(roles="OPERATOR") void operatorCanReadSnapshot() throws Exception {
        when(snapshots.capture()).thenReturn(new Snapshot("fixture",Instant.EPOCH,List.of(new Hub(1,"A",10,2)),List.of(),List.of(),"test"));
        mvc.perform(get("/api/m8/snapshot")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("fixture"));
    }
    @Test @WithMockUser(roles="OPERATOR") void rejectsOutOfBoundsScenario() throws Exception {
        mvc.perform(post("/api/m8/runs").contentType("application/json").content("{\"name\":\"bad\",\"hubId\":1,\"injectedRequests\":10001,\"durationMins\":60,\"arrivalWindowMins\":1,\"tripMins\":30}"))
                .andExpect(status().isBadRequest());
    }
}
