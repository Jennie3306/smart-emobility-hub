package com.hcmut.emobility.m6simulator.incident;

import com.hcmut.emobility.m6simulator.domain.*;
import com.hcmut.emobility.m6simulator.dto.ReportIncidentRequest;
import com.hcmut.emobility.m6simulator.dto.TransitionIncidentRequest;
import com.hcmut.emobility.m6simulator.service.ActiveIncidentExistsException;
import com.hcmut.emobility.m6simulator.service.DeviceEventService;
import com.hcmut.emobility.m6simulator.service.IncidentService;
import com.hcmut.emobility.shared.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Incident API and storage (FR-M6-01, FR-M6-02, FR-M6-05; BR-M6-03/04/06/09/10).
 * Vehicle 1 and charging point 1 at hub 1 are the resources DemoDeviceCatalog knows.
 * IDs of the M6 report: TC-M6-25 (vehicle), 26 (charger), 29, 34..41, 44, 57, 58, 61, 62, 63.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IncidentFlowTest {

    @Autowired IncidentService incidents;
    @Autowired DeviceEventService devices;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM incident");
        jdbc.update("INSERT INTO hub(id, name, location) SELECT 1, 'Hub Ga Metro', 'Test' "
                + "WHERE NOT EXISTS (SELECT 1 FROM hub WHERE id = 1)");
        jdbc.update("DELETE FROM rental_vehicle WHERE id = 1");
        jdbc.update("INSERT INTO rental_vehicle(id, code, type, battery_level, state, current_hub_id, version) "
                + "VALUES (1, 'M6-INC-1', 'E_BIKE', 80, 'AVAILABLE', 1, 0)");
        jdbc.update("DELETE FROM charging_point WHERE id = 1");
        jdbc.update("INSERT INTO charging_point(id, hub_id, state, version) VALUES (1, 1, 'AVAILABLE', 0)");
        jdbc.update("DELETE FROM app_user WHERE username IN ('op-m6-a', 'op-m6-b')");
        jdbc.update("INSERT INTO app_user(username, password_hash, full_name, role) "
                + "VALUES ('op-m6-a', 'x', 'Operator A', 'OPERATOR'), ('op-m6-b', 'x', 'Operator B', 'OPERATOR')");
    }

    private ReportIncidentRequest vehicleReport(String reason, IncidentSeverity severity) {
        return new ReportIncidentRequest(1L, ResourceType.VEHICLE, 1L, reason, severity, "Brake does not work");
    }

    private Incident move(Incident i, IncidentAction action, String assignee) {
        return incidents.transition(i.getId(), new TransitionIncidentRequest(action, i.getVersion(), assignee, null), "op-m6-a");
    }

    @Test
    @DisplayName("TC-M6-25 Operator reports a broken vehicle: Incident REPORTED, VehicleFailed reaches M3")
    void manualVehicleReport() {
        Incident created = incidents.report(vehicleReport("MECHANICAL", IncidentSeverity.HIGH), "op-m6-a");

        assertEquals(IncidentStatus.REPORTED, created.getStatus());
        assertEquals(IncidentSource.OPERATOR, created.getSource());
        assertEquals("op-m6-a", created.getReportedBy());
        assertNotNull(created.getReportedAt());
        assertEquals("MAINTENANCE", jdbc.queryForObject("SELECT state FROM rental_vehicle WHERE id = 1", String.class));
    }

    @Test
    @DisplayName("TC-M6-26 Operator reports a charging point: ChargerFailed reaches M5, the port is FAULTY")
    void manualChargerReport() {
        incidents.report(new ReportIncidentRequest(1L, ResourceType.CHARGING_POINT, 1L, "OFFLINE",
                IncidentSeverity.MEDIUM, null), "op-m6-a");

        assertEquals("FAULTY", jdbc.queryForObject("SELECT state FROM charging_point WHERE id = 1", String.class));
    }

    @Test
    @DisplayName("TC-M6-27/28/29/60 invalid report: missing severity, wrong reason, MANUAL_REPORT without text, unknown resource")
    void invalidReports() {
        assertEquals("VALIDATION_FAILED", assertThrows(BusinessException.class,
                () -> incidents.report(vehicleReport("MECHANICAL", null), "op-m6-a")).getCode());
        assertEquals("VALIDATION_FAILED", assertThrows(BusinessException.class,
                () -> incidents.report(vehicleReport("OVERHEAT", IncidentSeverity.LOW), "op-m6-a")).getCode());
        assertEquals("VALIDATION_FAILED", assertThrows(BusinessException.class,
                () -> incidents.report(new ReportIncidentRequest(1L, ResourceType.VEHICLE, 1L, "MANUAL_REPORT",
                        IncidentSeverity.LOW, " "), "op-m6-a")).getCode());
        assertEquals("RESOURCE_NOT_FOUND", assertThrows(BusinessException.class,
                () -> incidents.report(new ReportIncidentRequest(1L, ResourceType.VEHICLE, 987654L, "MECHANICAL",
                        IncidentSeverity.HIGH, null), "op-m6-a")).getCode());
        assertEquals(0, incidents.list(null, null, null).size());
        assertEquals("AVAILABLE", jdbc.queryForObject("SELECT state FROM rental_vehicle WHERE id = 1", String.class));
    }

    @Test
    @DisplayName("TC-M6-57 a second report while one is active is refused with the existing Incident")
    void duplicateActiveReport() {
        Incident first = incidents.report(vehicleReport("MECHANICAL", IncidentSeverity.HIGH), "op-m6-a");

        ActiveIncidentExistsException ex = assertThrows(ActiveIncidentExistsException.class,
                () -> incidents.report(vehicleReport("MANUAL_REPORT", IncidentSeverity.LOW), "op-m6-b"));

        assertEquals(first.getId(), ex.getExistingIncidentId());
        assertEquals(1, incidents.list(null, ResourceType.VEHICLE, null).size());
        assertEquals(IncidentSeverity.HIGH, incidents.get(first.getId()).getSeverity());
    }

    @Test
    @DisplayName("TC-M6-34..37 lifecycle ASSIGN -> START -> RESOLVE -> CLOSE with timestamps and versions")
    void fullLifecycle() {
        Incident i = incidents.report(vehicleReport("MECHANICAL", IncidentSeverity.HIGH), "op-m6-a");
        long v0 = i.getVersion();

        i = move(i, IncidentAction.ASSIGN, "op-m6-b");
        assertEquals(IncidentStatus.ASSIGNED, i.getStatus());
        assertEquals("op-m6-b", i.getAssignee());
        assertNotNull(i.getAssignedAt());
        assertNotEquals(v0, i.getVersion());

        i = move(i, IncidentAction.START, null);
        assertNotNull(i.getStartedAt());
        i = move(i, IncidentAction.RESOLVE, null);
        assertNotNull(i.getResolvedAt());
        i = move(i, IncidentAction.CLOSE, null);
        assertEquals(IncidentStatus.CLOSED, i.getStatus());
        assertNotNull(i.getClosedAt());

        Incident closed = i;
        assertEquals("INVALID_STATE_TRANSITION", assertThrows(BusinessException.class,
                () -> move(closed, IncidentAction.REOPEN, null)).getCode());     // TC-M6-39
    }

    @Test
    @DisplayName("TC-M6-41/42/44 ASSIGN without assignee, missing version, stale version are refused")
    void refusedTransitions() {
        Incident i = incidents.report(vehicleReport("MECHANICAL", IncidentSeverity.HIGH), "op-m6-a");
        long v0 = i.getVersion();

        assertEquals("VALIDATION_FAILED", assertThrows(BusinessException.class,
                () -> incidents.transition(i.getId(), new TransitionIncidentRequest(IncidentAction.ASSIGN, v0, null, null), "op-m6-a")).getCode());
        assertEquals("VALIDATION_FAILED", assertThrows(BusinessException.class,
                () -> incidents.transition(i.getId(), new TransitionIncidentRequest(IncidentAction.ASSIGN, null, "op-m6-b", null), "op-m6-a")).getCode());
        assertEquals("ASSIGNEE_NOT_FOUND", assertThrows(BusinessException.class,
                () -> incidents.transition(i.getId(), new TransitionIncidentRequest(IncidentAction.ASSIGN, v0, "nobody", null), "op-m6-a")).getCode());

        Incident assigned = move(i, IncidentAction.ASSIGN, "op-m6-b");
        assertEquals("STALE_VERSION", assertThrows(BusinessException.class,
                () -> incidents.transition(i.getId(), new TransitionIncidentRequest(IncidentAction.START, v0, null, null), "op-m6-a")).getCode());
        assertEquals(IncidentStatus.ASSIGNED, incidents.get(i.getId()).getStatus());
        assertEquals(IncidentStatus.IN_PROGRESS, move(assigned, IncidentAction.START, null).getStatus());
    }

    @Test
    @DisplayName("TC-M6-38/62/63 reopen, a new report after RESOLVED, and reopen refused while another is active")
    void reopenRules() {
        Incident old = incidents.report(vehicleReport("MECHANICAL", IncidentSeverity.HIGH), "op-m6-a");
        old = move(move(move(old, IncidentAction.ASSIGN, "op-m6-b"), IncidentAction.START, null), IncidentAction.RESOLVE, null);

        Incident fresh = incidents.report(vehicleReport("MANUAL_REPORT", IncidentSeverity.LOW), "op-m6-a");
        assertNotEquals(old.getId(), fresh.getId());

        Incident resolved = old;
        ActiveIncidentExistsException ex = assertThrows(ActiveIncidentExistsException.class,
                () -> move(resolved, IncidentAction.REOPEN, null));
        assertEquals(fresh.getId(), ex.getExistingIncidentId());
        assertEquals(IncidentStatus.RESOLVED, incidents.get(old.getId()).getStatus());

        Incident f = move(move(move(fresh, IncidentAction.ASSIGN, "op-m6-a"), IncidentAction.START, null), IncidentAction.RESOLVE, null);
        move(f, IncidentAction.CLOSE, null);
        Incident reopened = move(incidents.get(old.getId()), IncidentAction.REOPEN, null);
        assertEquals(IncidentStatus.IN_PROGRESS, reopened.getStatus());
        assertNull(reopened.getResolvedAt());
    }

    @Test
    @DisplayName("TC-M6-58/61 device failures: one Incident with mapped severity; a new failure while active is not announced")
    void deviceFailures() {
        ProcessingResult first = devices.processReading(new DeviceReading(UUID.randomUUID(), 1L, ResourceType.CHARGING_POINT,
                1L, "FAULTY", null, "OVERHEAT", OffsetDateTime.now()));
        ProcessingResult second = devices.processReading(new DeviceReading(UUID.randomUUID(), 1L, ResourceType.CHARGING_POINT,
                1L, "FAULTY", null, "OFFLINE", OffsetDateTime.now()));

        assertEquals(1, first.outboundEvents().size());
        assertEquals("ACTIVE_INCIDENT_EXISTS", second.reasonCode());
        assertTrue(second.outboundEvents().isEmpty());
        var list = incidents.list(IncidentStatus.REPORTED, ResourceType.CHARGING_POINT, null);
        assertEquals(1, list.size());
        assertEquals(IncidentSeverity.CRITICAL, list.get(0).getSeverity());
        assertEquals(IncidentSource.DEVICE, list.get(0).getSource());
        assertEquals(1, incidents.list(null, null, IncidentSeverity.CRITICAL).size());
    }
}
