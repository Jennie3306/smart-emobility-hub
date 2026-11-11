package com.hcmut.emobility.m2reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M2 API tests through MockMvc. Related: FR-M2-01, FR-M2-03, NFR-SEC-01, TC-M2-06. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.reservation.expiry-scan-ms=3600000")
class ReservationControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM reservation");
        jdbc.update("DELETE FROM rental_vehicle");
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("TC-M2-06: BVA on minBattery - -1 and 101 -> 400 VALIDATION_FAILED; 0 and 100 -> 200")
    void searchValidatesBattery() throws Exception {
        for (String outside : new String[]{"-1", "101"}) {
            mvc.perform(get("/api/m2/vehicles/search").param("minBattery", outside))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details.minBattery").exists());
        }
        for (String onBoundary : new String[]{"0", "100"}) {
            mvc.perform(get("/api/m2/vehicles/search").param("minBattery", onBoundary))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("NFR-SEC-01: Not logged in -> 401")
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/m2/vehicles/search")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("FR-M2-03: Book through the API -> 201, then read it back via /current")
    void reserveThroughApi() throws Exception {
        jdbc.update("INSERT INTO rental_vehicle(code, type, battery_level, state, current_hub_id, version)"
                + " VALUES ('EB-A01', 'E_BIKE', 90, 'AVAILABLE', 1, 0)");
        long id = jdbc.queryForObject("SELECT id FROM rental_vehicle WHERE code = 'EB-A01'", Long.class);

        mvc.perform(post("/api/m2/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + id + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("CONFIRMED"))
                .andExpect(jsonPath("$.vehicleCode").value("EB-A01"));

        mvc.perform(get("/api/m2/reservations/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingSeconds").isNumber());
    }

    @Test
    @WithMockUser(username = "operator1", roles = "OPERATOR")
    @DisplayName("TC-M2-20: OPERATOR can search vehicles but cannot reserve -> 403 ACCESS_DENIED (NFR-SEC-01, UC-M2-03)")
    void operatorCannotReserve() throws Exception {
        jdbc.update("INSERT INTO rental_vehicle(code, type, battery_level, state, current_hub_id, version)"
                + " VALUES ('EB-A02', 'E_BIKE', 90, 'AVAILABLE', 1, 0)");
        long id = jdbc.queryForObject("SELECT id FROM rental_vehicle WHERE code = 'EB-A02'", Long.class);

        mvc.perform(get("/api/m2/vehicles/search")).andExpect(status().isOk());

        mvc.perform(post("/api/m2/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + id + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post("/api/m2/reservations/1/cancel")).andExpect(status().isForbidden());
        mvc.perform(post("/api/m2/reservations/1/check-in")).andExpect(status().isForbidden());

        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM reservation", Integer.class);
        assertEquals(0, rows);
    }
}
