package com.hcmut.emobility.m6simulator.service;

import com.hcmut.emobility.m6simulator.domain.*;
import com.hcmut.emobility.m6simulator.dto.ReportIncidentRequest;
import com.hcmut.emobility.m6simulator.dto.TransitionIncidentRequest;
import com.hcmut.emobility.m6simulator.repository.IncidentRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.events.ChargerFailedEvent;
import com.hcmut.emobility.shared.events.DomainEvent;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.VehicleFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * FR-M6-01, FR-M6-02, FR-M6-05. Records Incidents and walks them through BR-M6-04.
 *
 * The Incident row is committed first and the failure event is published afterwards, so a consumer never
 * reacts to an Incident that was rolled back (BR-M6-09). M6 only announces the failure; the owner of the
 * resource locks it (BR-M6-08): M3 moves the vehicle to MAINTENANCE, M5 marks the port FAULTY, M4 blocks the
 * space attached to the port. Resolving an Incident does not bring the device back by itself: the next
 * device reading does, through the owner (Appendix A v2).
 */
@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);
    public static final Set<IncidentStatus> ACTIVE =
            EnumSet.of(IncidentStatus.REPORTED, IncidentStatus.ASSIGNED, IncidentStatus.IN_PROGRESS);
    static final String DEVICE_REPORTER = "DEVICE";

    private final IncidentRepository incidents;
    private final EventPublisher events;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public IncidentService(IncidentRepository incidents, EventPublisher events, JdbcTemplate jdbc,
                           PlatformTransactionManager transactionManager, Optional<Clock> clock) {
        this.incidents = incidents;
        this.events = events;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock.orElse(Clock.systemDefaultZone());
    }

    /** FR-M6-01, UC-M6-01. Operator report. */
    public Incident report(ReportIncidentRequest request, String reporter) {
        validateReport(request);
        Long hubId = checkResource(request.resourceType(), request.resourceId(), request.hubId());
        String description = request.description() == null ? null : request.description().trim();

        Incident saved = create(request.resourceType(), request.resourceId(), hubId, request.reason(),
                request.severity(), description, IncidentSource.OPERATOR, reporter);
        publishFailure(saved);
        log.info("M6_INCIDENT_REPORTED id={} resourceType={} resourceId={} severity={} by={}",
                saved.getId(), saved.getResourceType(), saved.getResourceId(), saved.getSeverity(), reporter);
        return saved;
    }

    /**
     * FR-M6-05, UC-M6-05. Called by DeviceEventService for a valid failure reading.
     *
     * @return true when a new Incident was recorded and the caller must announce the failure;
     *         false when the resource already has an active Incident (BR-M6-06): nothing is created and no
     *         failure event is published again.
     */
    public boolean openForDeviceFailure(DeviceReading reading) {
        IncidentSeverity severity = IncidentSeverity.forDeviceFailure(reading.resourceType(), reading.failureReason());
        if (severity == null) {
            throw new BusinessException("INVALID_DEVICE_PAYLOAD", "Not a device failure: "
                    + reading.resourceType() + " / " + reading.failureReason(), HttpStatus.BAD_REQUEST);
        }
        try {
            Incident created = create(reading.resourceType(), reading.resourceId(), reading.hubId(),
                    reading.failureReason(), severity,
                    "Device reported " + reading.observedState() + " (" + reading.failureReason() + ")",
                    IncidentSource.DEVICE, DEVICE_REPORTER);
            log.info("M6_INCIDENT_RECORDED id={} inboundEventId={} resourceType={} resourceId={} severity={}",
                    created.getId(), reading.eventId(), reading.resourceType(), reading.resourceId(), severity);
            return true;
        } catch (ActiveIncidentExistsException existing) {
            log.info("M6_INCIDENT_ALREADY_ACTIVE inboundEventId={} existingIncidentId={}",
                    reading.eventId(), existing.getExistingIncidentId());
            return false;
        }
    }

    /** FR-M6-02, UC-M6-02. */
    public Incident transition(Long id, TransitionIncidentRequest request, String actor) {
        if (request == null || request.action() == null || request.expectedVersion() == null) {
            throw new BusinessException("VALIDATION_FAILED", "action and expectedVersion are required",
                    HttpStatus.BAD_REQUEST);
        }
        String assignee = request.assigneeUserId() == null ? null : request.assigneeUserId().trim();
        if (request.action() == IncidentAction.ASSIGN) {
            if (assignee == null || assignee.isEmpty()) {
                throw new BusinessException("VALIDATION_FAILED", "assigneeUserId is required for ASSIGN",
                        HttpStatus.BAD_REQUEST);
            }
            if (!isOperator(assignee)) {
                throw new BusinessException("ASSIGNEE_NOT_FOUND", "No operator account named " + assignee,
                        HttpStatus.BAD_REQUEST);
            }
        }

        Incident result = tx.execute(status -> {
            Incident incident = incidents.findById(id).orElseThrow(() -> notFound(id));
            if (!incident.getVersion().equals(request.expectedVersion())) {
                throw new BusinessException("STALE_VERSION", "Incident #" + id + " has changed (version "
                        + incident.getVersion() + "); reload it and try again");
            }
            // Graph first (INVALID_STATE_TRANSITION), then the active-Incident rule for REOPEN (TC-M6-63).
            incident.getStatus().transition(request.action());
            if (request.action() == IncidentAction.REOPEN) {
                incidents.findFirstByResourceTypeAndResourceIdAndStatusIn(
                                incident.getResourceType(), incident.getResourceId(), ACTIVE)
                        .ifPresent(other -> { throw new ActiveIncidentExistsException(other); });
            }
            incident.apply(request.action(), assignee, request.note(), now());
            return incidents.saveAndFlush(incident);
        });
        log.info("M6_INCIDENT_TRANSITION id={} action={} status={} by={}",
                id, request.action(), result.getStatus(), actor);
        return result;
    }

    public Incident get(Long id) {
        return incidents.findById(id).orElseThrow(() -> notFound(id));
    }

    /** FR-M6-02: list, newest first, filtered by status, resource type and severity (each optional). */
    public List<Incident> list(IncidentStatus status, ResourceType resourceType, IncidentSeverity severity) {
        return incidents.findAllByOrderByReportedAtDescIdDesc().stream()
                .filter(i -> status == null || i.getStatus() == status)
                .filter(i -> resourceType == null || i.getResourceType() == resourceType)
                .filter(i -> severity == null || i.getSeverity() == severity)
                .toList();
    }

    // ------------------------------------------------------------------ internals

    /** One transaction: check BR-M6-06 and insert. The partial unique index settles a concurrent race. */
    private Incident create(ResourceType type, Long resourceId, Long hubId, String reason,
                            IncidentSeverity severity, String description, IncidentSource source, String reporter) {
        try {
            return tx.execute(status -> {
                incidents.findFirstByResourceTypeAndResourceIdAndStatusIn(type, resourceId, ACTIVE)
                        .ifPresent(existing -> { throw new ActiveIncidentExistsException(existing); });
                return incidents.saveAndFlush(Incident.report(type, resourceId, hubId, reason, severity,
                        description, source, reporter, now()));
            });
        } catch (DataIntegrityViolationException race) {
            // Another request inserted the active Incident between our check and our insert (TC-M6-59).
            Incident winner = incidents.findFirstByResourceTypeAndResourceIdAndStatusIn(type, resourceId, ACTIVE)
                    .orElseThrow(() -> race);
            throw new ActiveIncidentExistsException(winner);
        }
    }

    private void publishFailure(Incident incident) {
        DomainEvent event = incident.getResourceType() == ResourceType.VEHICLE
                ? new VehicleFailedEvent(incident.getResourceId(), incident.getHubId(), incident.getReason())
                : new ChargerFailedEvent(incident.getResourceId(), incident.getHubId(), incident.getReason());
        events.publish(event);
    }

    private void validateReport(ReportIncidentRequest r) {
        if (r == null || r.hubId() == null || r.resourceType() == null || r.resourceId() == null
                || r.reason() == null || r.reason().isBlank()) {
            throw new BusinessException("VALIDATION_FAILED",
                    "hubId, resourceType, resourceId and reason are required", HttpStatus.BAD_REQUEST);
        }
        if (r.severity() == null) {
            throw new BusinessException("VALIDATION_FAILED",
                    "severity is required: LOW, MEDIUM, HIGH or CRITICAL (BR-M6-10)", HttpStatus.BAD_REQUEST);
        }
        if (!FailureReasonCodes.allowsOperatorReport(r.resourceType(), r.reason())) {
            throw new BusinessException("VALIDATION_FAILED", "Reason " + r.reason() + " is not allowed for "
                    + r.resourceType() + " (BR-M6-03)", HttpStatus.BAD_REQUEST);
        }
        if ("MANUAL_REPORT".equals(r.reason()) && (r.description() == null || r.description().isBlank())) {
            throw new BusinessException("VALIDATION_FAILED", "A description is required for MANUAL_REPORT",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * BR-M6-09: the resource must exist and belong to the hub. Read-only lookups in the owners' tables
     * (Appendix A, principle 5). A vehicle that is on a trip has no hub; the reported hub is then kept.
     */
    private Long checkResource(ResourceType type, Long resourceId, Long hubId) {
        List<Long> hubs = switch (type) {
            case VEHICLE -> jdbc.query("SELECT current_hub_id FROM rental_vehicle WHERE id = ?",
                    (rs, i) -> rs.getObject(1, Long.class), resourceId);
            case CHARGING_POINT -> jdbc.query("SELECT hub_id FROM charging_point WHERE id = ?",
                    (rs, i) -> rs.getObject(1, Long.class), resourceId);
            case PARKING_SPACE -> throw new BusinessException("VALIDATION_FAILED",
                    "Only vehicles and charging points can have an Incident (BR-M6-03)", HttpStatus.BAD_REQUEST);
        };
        if (hubs.isEmpty()) {
            throw new BusinessException("RESOURCE_NOT_FOUND", type + " " + resourceId + " does not exist",
                    HttpStatus.NOT_FOUND);
        }
        Long actualHub = hubs.get(0);
        if (actualHub != null && !actualHub.equals(hubId)) {
            throw new BusinessException("RESOURCE_HUB_MISMATCH", type + " " + resourceId + " is at hub "
                    + actualHub + ", not at hub " + hubId, HttpStatus.BAD_REQUEST);
        }
        Integer hubCount = jdbc.queryForObject("SELECT COUNT(*) FROM hub WHERE id = ?", Integer.class, hubId);
        if (hubCount == null || hubCount == 0) {
            throw new BusinessException("HUB_NOT_FOUND", "Hub " + hubId + " does not exist", HttpStatus.NOT_FOUND);
        }
        return hubId;
    }

    private boolean isOperator(String username) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username = ? AND role = 'OPERATOR'",
                Integer.class, username);
        return n != null && n > 0;
    }

    private BusinessException notFound(Long id) {
        return new BusinessException("INCIDENT_NOT_FOUND", "Incident not found: " + id, HttpStatus.NOT_FOUND);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
