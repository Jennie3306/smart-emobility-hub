package com.hcmut.emobility.m5charging.service;

import com.hcmut.emobility.m5charging.domain.ChargingPoint;
import com.hcmut.emobility.m5charging.domain.ChargingPointState;
import com.hcmut.emobility.m5charging.domain.ChargingPriorityAudit;
import com.hcmut.emobility.m5charging.domain.ChargingSession;
import com.hcmut.emobility.m5charging.domain.ChargingSessionStatus;
import com.hcmut.emobility.m5charging.domain.PriorityLevel;
import com.hcmut.emobility.m5charging.domain.VehicleKind;
import com.hcmut.emobility.m5charging.dto.ChargingHubSummary;
import com.hcmut.emobility.m5charging.dto.ChargingRequest;
import com.hcmut.emobility.m5charging.dto.ChargingScheduleRequest;
import com.hcmut.emobility.m5charging.dto.ChargingSessionResponse;
import com.hcmut.emobility.m5charging.repository.ChargingPointRepository;
import com.hcmut.emobility.m5charging.repository.ChargingPriorityAuditRepository;
import com.hcmut.emobility.m5charging.repository.ChargingSessionRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.events.ChargingCompletedEvent;
import com.hcmut.emobility.shared.events.DomainEvent;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** M5 application service. M5 owns charging sessions and port assignment; it never calls another module's service. */
@Service
public class ChargingService {

    private static final Logger log = LoggerFactory.getLogger(ChargingService.class);
    private static final Set<ChargingSessionStatus> ACTIVE = EnumSet.of(
            ChargingSessionStatus.REQUESTED, ChargingSessionStatus.QUEUED,
            ChargingSessionStatus.SCHEDULED, ChargingSessionStatus.CHARGING,
            ChargingSessionStatus.INTERRUPTED);
    private static final Set<ChargingSessionStatus> QUEUE_STATES = EnumSet.of(
            ChargingSessionStatus.REQUESTED, ChargingSessionStatus.QUEUED,
            ChargingSessionStatus.INTERRUPTED);
    private static final Set<ChargingSessionStatus> POINT_OCCUPYING = EnumSet.of(
            ChargingSessionStatus.SCHEDULED, ChargingSessionStatus.CHARGING);
    private static final Set<ChargingPointState> OPERATIONAL = EnumSet.of(
            ChargingPointState.AVAILABLE, ChargingPointState.RESERVED, ChargingPointState.IN_USE);

    private final ChargingSessionRepository sessions;
    private final ChargingPointRepository points;
    private final ChargingPriorityAuditRepository audits;
    private final EventPublisher events;
    private final ChargingQueuePublisher queuePublisher;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Value("${app.charging.low-battery-threshold:20}")
    private int lowBatteryThreshold;

    public ChargingService(ChargingSessionRepository sessions,
                           ChargingPointRepository points,
                           ChargingPriorityAuditRepository audits,
                           EventPublisher events,
                           ChargingQueuePublisher queuePublisher,
                           JdbcTemplate jdbc,
                           Clock clock) {
        this.sessions = sessions;
        this.points = points;
        this.audits = audits;
        this.events = events;
        this.queuePublisher = queuePublisher;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** FR-M5-01: create an immediate request, then calculate its queue position and assign a free port. */
    @Transactional
    public ChargingSessionResponse request(Long userId, ChargingRequest request) {
        validateBattery(request.batteryLevel());
        if (request.batteryLevel() >= 90) {
            throw new BusinessException("BATTERY_ALREADY_FULL", "Charging is not accepted when battery level is 90% or higher");
        }
        ensureHubExists(request.hubId());
        ensureVehicleBelongsTo(userId, request.vehicleKind(), request.vehicleId());
        ensureOperationalPort(request.hubId());
        ensureNoActiveSession(request.vehicleKind(), request.vehicleId());
        ChargingSession session = ChargingSession.request(userId, request.hubId(), request.parkingSpaceId(),
                request.vehicleKind(), request.vehicleId(), request.batteryLevel(), request.expectedUseAt(), now());
        session.enqueue(now());
        try {
            session = sessions.saveAndFlush(session);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("ACTIVE_SESSION_EXISTS", "This vehicle already has an unfinished charging session");
        }
        recalculateHub(request.hubId(), now());
        ChargingSession saved = sessions.findById(session.getId()).orElse(session);
        return response(saved, queuePosition(saved));
    }

    /** FR-M5-02: create a personal-EV schedule within an M4 parking reservation. */
    @Transactional
    public ChargingSessionResponse schedule(Long userId, ChargingScheduleRequest request) {
        OffsetDateTime now = now();
        validateBattery(request.batteryLevel());
        if (request.batteryLevel() >= 90) {
            throw new BusinessException("BATTERY_ALREADY_FULL", "Charging is not accepted when battery level is 90% or higher");
        }
        validateScheduleWindow(request.startAt(), request.durationMinutes(), now);
        ensureHubExists(request.hubId());
        ensureVehicleBelongsTo(userId, VehicleKind.PERSONAL, request.vehicleId());
        validateParkingReservation(userId, request.hubId(), request.parkingSpaceId(),
                request.startAt(), request.startAt().plusMinutes(request.durationMinutes()));
        ensureNoActiveSession(VehicleKind.PERSONAL, request.vehicleId());

        long activeSchedules = sessions.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(s -> s.getStatus() == ChargingSessionStatus.SCHEDULED
                        && s.getScheduledStartAt() != null)
                .count();
        if (activeSchedules >= 2) {
            throw new BusinessException("SCHEDULE_LIMIT_EXCEEDED", "Each student may have at most two active charging schedules");
        }

        List<ChargingPoint> candidates = points.lockByHubAndStates(request.hubId(), OPERATIONAL);
        ChargingPoint selected = candidates.stream()
                .filter(p -> (p.getState() == ChargingPointState.AVAILABLE
                        || request.startAt().isAfter(now.plusMinutes(90))))
                .filter(p -> !hasOverlap(p.getId(), request.startAt(), request.startAt().plusMinutes(request.durationMinutes()), now))
                .findFirst()
                .orElseThrow(() -> new BusinessException("CHARGING_CAPACITY_FULL", "No charging point is available for this time window"));

        ChargingSession session = ChargingSession.schedule(userId, request.hubId(), request.parkingSpaceId(),
                VehicleKind.PERSONAL, request.vehicleId(), request.batteryLevel(), request.startAt(),
                request.durationMinutes(), selected.getId(), now);
        try {
            session = sessions.saveAndFlush(session);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("SCHEDULE_CONFLICT", "The selected charging point was just booked by another student");
        }
        return response(session, null);
    }

    /** FR-M5-07: current user's own sessions; queue position is computed with the same ordering as dispatch. */
    @Transactional(readOnly = true)
    public List<ChargingSessionResponse> mine(Long userId) {
        OffsetDateTime now = now();
        return sessions.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(s -> response(s, queuePosition(s), now)).toList();
    }

    /** Operator's full view for one Hub. */
    @Transactional(readOnly = true)
    public List<ChargingSessionResponse> hubSessions(Long hubId) {
        OffsetDateTime now = now();
        return sessions.findByHubIdOrderByCreatedAtDesc(hubId).stream()
                .map(s -> response(s, queuePosition(s), now)).toList();
    }

    @Transactional(readOnly = true)
    public List<ChargingHubSummary> hubs() {
        List<Object[]> rows = jdbc.query("select id, name from hub order by id",
                (rs, row) -> new Object[]{rs.getLong("id"), rs.getString("name")});
        List<ChargingHubSummary> result = new ArrayList<>();
        for (Object[] row : rows) {
            Long id = (Long) row[0];
            String name = (String) row[1];
            List<ChargingPoint> hubPoints = points.findByHubIdOrderById(id);
            long available = hubPoints.stream().filter(p -> p.getState() == ChargingPointState.AVAILABLE).count();
            long charging = hubPoints.stream().filter(p -> p.getState() == ChargingPointState.IN_USE).count();
            long faulty = hubPoints.stream().filter(p -> p.getState() == ChargingPointState.FAULTY
                    || p.getState() == ChargingPointState.UNDER_REPAIR).count();
            long queued = sessions.findByHubIdAndStatusInOrderByCreatedAtAsc(id, QUEUE_STATES).size();
            result.add(new ChargingHubSummary(id, name, hubPoints.size(), available, charging, faulty, queued));
        }
        return result;
    }

    /** FR-M5-03: an Operator override is auditable and cannot preempt a session already charging. */
    @Transactional
    public ChargingSessionResponse changePriority(Long sessionId, Long operatorId,
                                                   PriorityLevel priority, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("PRIORITY_REASON_REQUIRED", "A reason is required for every priority change", HttpStatus.BAD_REQUEST);
        }
        ChargingSession session = lockSession(sessionId);
        String previous = session.getPriorityLevel().name();
        session.changePriority(priority);
        audits.save(new ChargingPriorityAudit(sessionId, operatorId, previous, priority.name(),
                reason.trim(), now()));
        session = sessions.save(session);
        recalculateHub(session.getHubId(), now());
        return response(sessions.findById(sessionId).orElse(session), queuePosition(session));
    }

    /** FR-M5-03: move an unstarted personal schedule only after the replacement slot is confirmed. */
    @Transactional
    public ChargingSessionResponse moveSchedule(Long sessionId, OffsetDateTime startAt,
                                                 int durationMinutes, Long operatorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("PRIORITY_REASON_REQUIRED", "A reason is required for every schedule change", HttpStatus.BAD_REQUEST);
        }
        ChargingSession session = lockSession(sessionId);
        if (session.getStatus() != ChargingSessionStatus.SCHEDULED
                || session.getVehicleKind() != VehicleKind.PERSONAL) {
            throw new BusinessException("SCHEDULE_NOT_MOVABLE", "Only an unstarted personal charging schedule can be moved");
        }
        OffsetDateTime now = now();
        validateScheduleWindow(startAt, durationMinutes, now);
        validateParkingReservation(session.getUserId(), session.getHubId(), session.getParkingSpaceId(),
                startAt, startAt.plusMinutes(durationMinutes));
        List<ChargingPoint> candidates = points.lockByHubAndStates(session.getHubId(), OPERATIONAL);
        ChargingPoint selected = candidates.stream()
                .filter(p -> (p.getState() == ChargingPointState.AVAILABLE || startAt.isAfter(now.plusMinutes(90))))
                .filter(p -> !hasOverlapExcluding(p.getId(), startAt, startAt.plusMinutes(durationMinutes), now, sessionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("CHARGING_CAPACITY_FULL", "No charging point is free for the new time window"));
        Long oldPointId = session.getChargingPointId();
        OffsetDateTime oldStart = session.getScheduledStartAt();
        session.moveSchedule(startAt, durationMinutes, selected.getId());
        sessions.save(session);
        // Recorded as a SCHEDULE_MOVE (V53), with the Operator's own reason, so it is not mistaken for a
        // priority change where the old and new priority happen to be equal (review PR #11).
        String priority = session.getPriorityLevel().name();
        String detail = reason.trim() + " (schedule " + oldStart + " -> " + startAt + ", " + durationMinutes + " min)";
        audits.save(new ChargingPriorityAudit(sessionId, operatorId, priority, priority,
                detail.length() > 500 ? detail.substring(0, 500) : detail, now,
                ChargingPriorityAudit.ChangeType.SCHEDULE_MOVE));
        if (oldPointId != null && !oldPointId.equals(selected.getId())) releaseReservedPoint(oldPointId);
        recalculateHub(session.getHubId(), now);
        return response(session, null);
    }

    /** Start/plug-in confirmation for a scheduled port. */
    @Transactional
    public ChargingSessionResponse start(Long sessionId, Long userId, boolean operator) {
        ChargingSession session = lockSession(sessionId);
        assertOwner(session, userId, operator);
        if (session.getChargingPointId() == null) {
            throw new BusinessException("CHARGING_POINT_NOT_ASSIGNED", "This session has no charging point assigned");
        }
        ChargingPoint point = points.findById(session.getChargingPointId()).orElseThrow(() ->
                new BusinessException("CHARGING_POINT_NOT_FOUND", "Charging point no longer exists", HttpStatus.NOT_FOUND));
        if (point.getState() != ChargingPointState.AVAILABLE && point.getState() != ChargingPointState.RESERVED) {
            throw new BusinessException("CHARGING_POINT_BUSY", "The assigned charging point is not available");
        }
        session.start(now());
        point.startCharging();
        sessions.save(session);
        points.save(point);
        publishResourceState(point, "IN_USE");
        queuePublisher.publish(session.getHubId(), response(session, null));
        return response(session, null);
    }

    /** FR-M5-06: a user may cancel only their own session before charging starts. */
    @Transactional
    public ChargingSessionResponse cancel(Long sessionId, Long userId, boolean operator) {
        ChargingSession session = lockSession(sessionId);
        assertOwner(session, userId, operator);
        Long pointId = session.getChargingPointId();
        session.cancel(now());
        sessions.save(session);
        if (pointId != null) releaseReservedPoint(pointId);
        recalculateHub(session.getHubId(), now());
        return response(session, null);
    }

    /** FR-M5-04/05/09/10: one 60-second tick progresses charging, expires port holds and fills freed ports. */
    @Transactional
    public void runScheduledTasks() {
        OffsetDateTime now = now();
        expirePortHolds(now);
        progressCharging(now);
        List<Long> hubIds = jdbc.query("select id from hub order by id", (rs, row) -> rs.getLong(1));
        hubIds.forEach(id -> recalculateHub(id, now));
    }

    /** Called by the M6 event listener. M6 remains authoritative for the faulty signal. */
    @Transactional
    public void handleChargerFailed(Long chargingPointId, Long hubId) {
        ChargingPoint point = points.findById(chargingPointId).orElse(null);
        if (point == null) {
            log.warn("[M5] ChargerFailed references unknown point {} (hub {})", chargingPointId, hubId);
            return;
        }
        if (!point.getHubId().equals(hubId)) {
            log.warn("[M5] Ignored ChargerFailed with mismatched hub for point {}", chargingPointId);
            return;
        }
        point.markFaulty();
        points.save(point);
        List<ChargingSession> affected = sessions.findByChargingPointIdAndStatusIn(chargingPointId, POINT_OCCUPYING);
        OffsetDateTime now = now();
        for (ChargingSession session : affected) {
            // A booking that has not started keeps its window and moves to another free port (review PR #11).
            if (session.getStatus() == ChargingSessionStatus.SCHEDULED && session.waitsForWindow(now)
                    && moveFutureBookingToAnotherPort(session, chargingPointId, now)) {
                sessions.save(session);
                continue;
            }
            if (session.getStatus() == ChargingSessionStatus.CHARGING) {
                session.updateBatteryForElapsedTime(now); // persist the latest simulator value before interruption.
            }
            session.interrupt(now);
            session.requeueAfterFailure(now); // preserves the original waitingSince.
            sessions.save(session);
        }
        // First reassign any replacement ports. BR-M5-10 grants +20 only to affected sessions
        // that still have no port and therefore remain queued after this dispatch pass.
        recalculateHub(hubId, now);
        boolean bonusApplied = false;
        for (ChargingSession interrupted : affected) {
            ChargingSession refreshed = sessions.findById(interrupted.getId()).orElse(interrupted);
            if (refreshed.getStatus() == ChargingSessionStatus.QUEUED) {
                refreshed.applyFailureBonus();
                sessions.save(refreshed);
                bonusApplied = true;
            }
        }
        if (bonusApplied) recalculateHub(hubId, now);
        // M6 already emits the FAULTY resource update. M5 emits only updates for its own assignment changes.
        queuePublisher.publish(hubId, hubSessions(hubId));
    }

    /**
     * Applies M6 equipment health to the local port. RESERVED and IN_USE belong to M5, so M5's own
     * ResourceStateUpdatedEvent coming back here is a no-op (review PR #11).
     * No dispatch runs here: publishing RESERVED inside this listener would reach M1 before the outer
     * AVAILABLE event and leave the dashboard wrong. The next scheduler tick (runScheduledTasks)
     * assigns a recovered port to the queue.
     */
    @Transactional
    public void synchroniseChargingPoint(Long pointId, Long hubId, String state) {
        ChargingPoint point = points.findById(pointId).orElse(null);
        if (point == null || !point.getHubId().equals(hubId)) return;
        ChargingPointState before = point.getState();
        boolean recovered = point.applyExternalState(state);
        if (point.getState() == before) return;
        points.save(point);
        if (recovered) {
            log.info("[M5] Charging point {} at hub {} is back in service; the next queue tick will use it", pointId, hubId);
        }
    }

    @Transactional
    public void handleVehicleReturned(Long vehicleId, Long hubId, int batteryLevel) {
        if (batteryLevel < lowBatteryThreshold) {
            createAutomaticRentalRequest(vehicleId, hubId, batteryLevel);
        }
    }

    @Transactional
    public void handleVehicleLowBattery(Long vehicleId, int batteryLevel) {
        if (batteryLevel >= lowBatteryThreshold) return;
        List<Long> hubs = jdbc.query("select current_hub_id from rental_vehicle where id = ? and current_hub_id is not null",
                (rs, row) -> rs.getLong(1), vehicleId);
        if (!hubs.isEmpty()) createAutomaticRentalRequest(vehicleId, hubs.get(0), batteryLevel);
    }

    /**
     * M4 -> M5 (ChargingRequestedEvent). Runs in its own transaction after M4's booking has committed.
     * A request M5 cannot accept is logged and reported as false instead of thrown, so M5 never
     * rolls back or fails M4's parking booking (review PR #11).
     *
     * @return true when a charging session was queued.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean handleM4ChargingRequest(Long userId, Long hubId, Long parkingSpaceId,
                                           VehicleKind vehicleKind, Long vehicleId, Integer batteryLevel,
                                           OffsetDateTime arriveAt, OffsetDateTime leaveAt) {
        int level = batteryLevel == null ? 50 : batteryLevel;
        String rejection = null;
        if (hubId == null || vehicleKind == null || vehicleId == null) {
            rejection = "hubId, vehicleKind and vehicleId are required";
        } else if (level < 0 || level > 100) {
            rejection = "battery level " + level + " is outside 0-100";
        } else if (level >= 90) {
            rejection = "battery level " + level + "% is already 90% or higher";
        } else if (sessions.existsByVehicleKindAndVehicleIdAndStatusIn(vehicleKind, vehicleId, ACTIVE)) {
            rejection = "this vehicle already has an unfinished charging session";
        } else if (points.findByHubIdAndStateInOrderById(hubId, OPERATIONAL).isEmpty()) {
            rejection = "hub " + hubId + " has no operational charging point";
        }
        if (rejection != null) {
            log.warn("[M5] Charging request from M4 (parking space {}, {} #{}) not queued: {}",
                    parkingSpaceId, vehicleKind, vehicleId, rejection);
            return false;
        }
        OffsetDateTime now = now();
        // The vehicle is needed again when the parking booking ends (U in the priority formula).
        ChargingSession session = ChargingSession.request(userId, hubId, parkingSpaceId,
                vehicleKind, vehicleId, level, leaveAt, now);
        // A booking for later waits in the queue until arrival; the dispatcher skips it until then.
        session.waitForArrival(arriveAt, leaveAt, now);
        session.enqueue(now);
        sessions.saveAndFlush(session);
        recalculateHub(hubId, now);
        return true;
    }

    @Transactional
    public void cancelParkingLinkedSessions(Long parkingSpaceId) {
        List<ChargingSession> linked = sessions.findByParkingSpaceIdAndStatusIn(parkingSpaceId, ACTIVE);
        for (ChargingSession session : linked) {
            if (session.getStatus() == ChargingSessionStatus.CHARGING) continue;
            Long pointId = session.getChargingPointId();
            session.cancel(now());
            sessions.save(session);
            if (pointId != null) releaseReservedPoint(pointId);
            recalculateHub(session.getHubId(), now());
        }
    }

    @Transactional
    public void applyRecommendation(String recommendationId, String actionType) {
        // Current M7 publisher sends only actionType="REDISTRIBUTE" and no M5 session/slot payload.
        // Do not guess which charging session should be changed; wait for a typed event contract.
        log.info("[M5] Recommendation {} received with actionType={}; no charging-session target is present, so no M5 change was applied",
                recommendationId, actionType);
    }

    @Transactional(readOnly = true)
    public List<ChargingPriorityAudit> auditForSession(Long sessionId) {
        return audits.findBySessionIdOrderByChangedAtDesc(sessionId);
    }

    private void createAutomaticRentalRequest(Long vehicleId, Long hubId, int batteryLevel) {
        if (sessions.existsByVehicleKindAndVehicleIdAndStatusIn(VehicleKind.RENTAL, vehicleId, ACTIVE)) return;
        try {
            ensureOperationalPort(hubId);
            ChargingSession session = ChargingSession.request(null, hubId, null, VehicleKind.RENTAL,
                    vehicleId, batteryLevel, null, now());
            session.enqueue(now());
            sessions.saveAndFlush(session);
            recalculateHub(hubId, now());
        } catch (BusinessException ex) {
            log.info("[M5] Automatic request for rental vehicle {} skipped: {}", vehicleId, ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            log.debug("[M5] Duplicate low-battery request ignored for vehicle {}", vehicleId);
        }
    }

    private void recalculateHub(Long hubId, OffsetDateTime now) {
        List<ChargingSession> waiting = sessions.findByHubIdAndStatusInOrderByCreatedAtAsc(hubId, QUEUE_STATES);
        waiting.forEach(s -> s.setPriorityScore(ChargingPriorityPolicy.calculateScore(
                s.getBatteryLevel(), s.getExpectedUseAt(), s.getWaitingSince(), s.getFailureBonus(),
                s.getPriorityLevel(), now)));
        waiting.sort(ChargingPriorityPolicy.comparator());
        if (!waiting.isEmpty()) sessions.saveAll(waiting);

        for (ChargingSession session : waiting) {
            // A booking re-queued after a charger failure waits for its own start time.
            if (session.waitsForWindow(now)) continue;
            List<ChargingPoint> free = points.lockByHubAndState(hubId, ChargingPointState.AVAILABLE);
            ChargingPoint selected = free.stream()
                    .filter(p -> !hasOverlap(p.getId(), now, now.plusMinutes(90), now))
                    .findFirst().orElse(null);
            if (selected == null) break;
            session.assign(selected.getId(), now, null);
            selected.reserve();
            sessions.save(session);
            points.save(selected);
            publishResourceState(selected, "RESERVED");
        }
        queuePublisher.publish(hubId, sessions.findByHubIdOrderByCreatedAtDesc(hubId).stream()
                .map(s -> response(s, queuePosition(s), now)).toList());
    }

    private boolean hasOverlap(Long pointId, OffsetDateTime start, OffsetDateTime end, OffsetDateTime now) {
        List<ChargingSession> assigned = sessions.findByChargingPointIdAndStatusIn(pointId, POINT_OCCUPYING);
        for (ChargingSession current : assigned) {
            OffsetDateTime currentStart = current.getScheduledStartAt() != null
                    ? current.getScheduledStartAt()
                    : current.getChargingStartedAt() != null ? current.getChargingStartedAt() : current.getAssignedAt();
            if (currentStart == null) currentStart = now;
            OffsetDateTime currentEnd = current.getScheduledEndAt() != null
                    ? current.getScheduledEndAt() : currentStart.plusMinutes(90);
            if (start.isBefore(currentEnd) && end.isAfter(currentStart)) return true;
        }
        return false;
    }

    private boolean hasOverlapExcluding(Long pointId, OffsetDateTime start, OffsetDateTime end,
                                        OffsetDateTime now, Long excludedSessionId) {
        List<ChargingSession> assigned = sessions.findByChargingPointIdAndStatusIn(pointId, POINT_OCCUPYING);
        for (ChargingSession current : assigned) {
            if (current.getId().equals(excludedSessionId)) continue;
            OffsetDateTime currentStart = current.getScheduledStartAt() != null
                    ? current.getScheduledStartAt()
                    : current.getChargingStartedAt() != null ? current.getChargingStartedAt() : current.getAssignedAt();
            if (currentStart == null) currentStart = now;
            OffsetDateTime currentEnd = current.getScheduledEndAt() != null
                    ? current.getScheduledEndAt() : currentStart.plusMinutes(90);
            if (start.isBefore(currentEnd) && end.isAfter(currentStart)) return true;
        }
        return false;
    }

    private void expirePortHolds(OffsetDateTime now) {
        List<ChargingSession> expired = sessions.findByStatusAndHoldExpiresAtLessThanEqual(
                ChargingSessionStatus.SCHEDULED, now);
        for (ChargingSession candidate : expired) {
            ChargingSession session = sessions.lockById(candidate.getId()).orElse(null);
            if (session == null || session.getStatus() != ChargingSessionStatus.SCHEDULED
                    || session.getHoldExpiresAt() == null || session.getHoldExpiresAt().isAfter(now)) continue;
            Long pointId = session.getChargingPointId();
            Long hubId = session.getHubId();
            if (session.isAutomatic()) {
                // Nobody re-sends an automatic low-battery request, so it keeps its place in the queue
                // (original waitingSince) until an Operator starts it (review PR #11).
                session.returnToQueue(now);
            } else {
                session.cancel(now);
            }
            sessions.save(session);
            if (pointId != null) releaseReservedPoint(pointId);
            recalculateHub(hubId, now);
        }
    }

    /** Same port rules as schedule(): a port busy now is acceptable only for a window more than 90 minutes away. */
    private boolean moveFutureBookingToAnotherPort(ChargingSession session, Long failedPointId, OffsetDateTime now) {
        OffsetDateTime start = session.getScheduledStartAt();
        OffsetDateTime end = session.getScheduledEndAt();
        if (start == null || end == null) return false;
        ChargingPoint replacement = points.lockByHubAndStates(session.getHubId(), OPERATIONAL).stream()
                .filter(p -> !p.getId().equals(failedPointId))
                .filter(p -> p.getState() == ChargingPointState.AVAILABLE || start.isAfter(now.plusMinutes(90)))
                .filter(p -> !hasOverlapExcluding(p.getId(), start, end, now, session.getId()))
                .findFirst().orElse(null);
        if (replacement == null) return false;
        session.moveToPort(replacement.getId());
        log.info("[M5] Booking CS-{} moved from failed point {} to point {} for the same window", session.getId(),
                failedPointId, replacement.getId());
        return true;
    }

    private void progressCharging(OffsetDateTime now) {
        for (ChargingSession session : sessions.findByStatusIn(Set.of(ChargingSessionStatus.CHARGING))) {
            session.updateBatteryForElapsedTime(now);
            if (session.shouldComplete(now)) {
                Long pointId = session.getChargingPointId();
                int finalBattery = session.getBatteryLevel();
                session.complete(now);
                sessions.save(session);
                if (pointId != null) {
                    ChargingPoint point = points.findById(pointId).orElse(null);
                    if (point != null) {
                        point.release();
                        points.save(point);
                        if (point.getState() == ChargingPointState.AVAILABLE) publishResourceState(point, "FREE");
                    }
                }
                events.publish(new ChargingCompletedEvent(session.getId(), session.getHubId(), pointId,
                        session.getVehicleKind().name(), session.getVehicleId(), finalBattery));
                queuePublisher.publish(session.getHubId(), response(session, null, now));
            } else {
                sessions.save(session);
            }
        }
    }

    private void releaseReservedPoint(Long pointId) {
        ChargingPoint point = points.findById(pointId).orElse(null);
        if (point != null && point.getState() == ChargingPointState.RESERVED) {
            point.release();
            points.save(point);
            publishResourceState(point, "FREE");
        }
    }

    private void publishResourceState(ChargingPoint point, String state) {
        events.publish(new ResourceStateUpdatedEvent(point.getHubId(), "CHARGING_POINT", point.getId(), state));
    }

    /**
     * Review PR #11: a student may only charge a vehicle they actually hold, otherwise anyone could lock
     * out the real owner with ACTIVE_SESSION_EXISTS. Read-only checks, like validateParkingReservation:
     * a personal EV must be registered to the student and ACTIVE in M4 (personal_vehicle); a rental vehicle
     * must be reserved or picked up by the student through M2 (reservation).
     */
    private void ensureVehicleBelongsTo(Long userId, VehicleKind kind, Long vehicleId) {
        Integer count = kind == VehicleKind.PERSONAL
                ? jdbc.queryForObject("""
                        select count(*) from personal_vehicle
                         where id = ? and student_id = ? and status = 'ACTIVE'
                        """, Integer.class, vehicleId, userId)
                : jdbc.queryForObject("""
                        select count(*) from reservation
                         where user_id = ? and resource_type = 'VEHICLE' and resource_id = ?
                           and state in ('CONFIRMED','CHECKED_IN')
                        """, Integer.class, userId, vehicleId);
        if (count == null || count == 0) {
            throw new BusinessException("VEHICLE_NOT_OWNED", kind == VehicleKind.PERSONAL
                    ? "Register this personal vehicle in Parking (M4) before charging it"
                    : "You can only charge a rental vehicle you have reserved or picked up", HttpStatus.FORBIDDEN);
        }
    }

    private void validateParkingReservation(Long userId, Long hubId, Long parkingSpaceId,
                                            OffsetDateTime startAt, OffsetDateTime endAt) {
        Integer count = jdbc.queryForObject("""
                select count(*) from reservation
                 where user_id = ? and resource_type = 'PARKING_SPACE' and resource_id = ?
                   and hub_id = ? and state in ('CONFIRMED','CHECKED_IN')
                   and start_at <= ? and end_at >= ?
                """, Integer.class, userId, parkingSpaceId, hubId, startAt, endAt);
        if (count == null || count == 0) {
            throw new BusinessException("PARKING_RESERVATION_REQUIRED",
                    "A valid parking reservation at the same Hub must cover the charging window");
        }
    }

    private void validateScheduleWindow(OffsetDateTime startAt, Integer durationMinutes, OffsetDateTime now) {
        if (durationMinutes == null || (durationMinutes != 30 && durationMinutes != 60 && durationMinutes != 90)) {
            throw new BusinessException("INVALID_SCHEDULE_DURATION", "A charging schedule must last 30, 60 or 90 minutes", HttpStatus.BAD_REQUEST);
        }
        if (startAt.isBefore(now) || startAt.isAfter(now.plusHours(24))) {
            throw new BusinessException("SCHEDULE_OUT_OF_RANGE", "A charging schedule must start within the next 24 hours", HttpStatus.BAD_REQUEST);
        }
        if (startAt.getSecond() != 0 || startAt.getNano() != 0 || startAt.getMinute() % 30 != 0) {
            throw new BusinessException("INVALID_TIME_SLOT", "Charging schedules must start on a 30-minute boundary", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateBattery(Integer batteryLevel) {
        if (batteryLevel == null || batteryLevel < 0 || batteryLevel > 100) {
            throw new BusinessException("INVALID_BATTERY_LEVEL", "Battery level must be between 0 and 100", HttpStatus.BAD_REQUEST);
        }
    }

    private void ensureHubExists(Long hubId) {
        Integer count = jdbc.queryForObject("select count(*) from hub where id = ?", Integer.class, hubId);
        if (count == null || count == 0) {
            throw new BusinessException("HUB_NOT_FOUND", "Hub not found: " + hubId, HttpStatus.NOT_FOUND);
        }
    }

    private void ensureOperationalPort(Long hubId) {
        if (points.findByHubIdAndStateInOrderById(hubId, OPERATIONAL).isEmpty()) {
            throw new BusinessException("NO_OPERATIONAL_CHARGER", "This Hub has no operational charging point");
        }
    }

    private void ensureNoActiveSession(VehicleKind kind, Long vehicleId) {
        if (sessions.existsByVehicleKindAndVehicleIdAndStatusIn(kind, vehicleId, ACTIVE)) {
            throw new BusinessException("ACTIVE_SESSION_EXISTS", "This vehicle already has an unfinished charging session");
        }
    }

    private ChargingSession lockSession(Long id) {
        return sessions.lockById(id).orElseThrow(() ->
                new BusinessException("CHARGING_SESSION_NOT_FOUND", "Charging session not found: " + id, HttpStatus.NOT_FOUND));
    }

    private void assertOwner(ChargingSession session, Long userId, boolean operator) {
        if (!operator && (session.getUserId() == null || !session.getUserId().equals(userId))) {
            throw new BusinessException("ACCESS_DENIED", "This charging session does not belong to you", HttpStatus.FORBIDDEN);
        }
    }

    private Integer queuePosition(ChargingSession session) {
        return queuePosition(session, now());
    }

    private Integer queuePosition(ChargingSession session, OffsetDateTime now) {
        if (session.getStatus() != ChargingSessionStatus.QUEUED
                && session.getStatus() != ChargingSessionStatus.REQUESTED
                && session.getStatus() != ChargingSessionStatus.INTERRUPTED) return null;
        List<ChargingSession> waiting = new ArrayList<>(sessions.findByHubIdAndStatusInOrderByCreatedAtAsc(
                session.getHubId(), QUEUE_STATES));
        waiting.forEach(s -> s.setPriorityScore(ChargingPriorityPolicy.calculateScore(
                s.getBatteryLevel(), s.getExpectedUseAt(), s.getWaitingSince(), s.getFailureBonus(),
                s.getPriorityLevel(), now)));
        waiting.sort(ChargingPriorityPolicy.comparator());
        for (int i = 0; i < waiting.size(); i++) if (waiting.get(i).getId().equals(session.getId())) return i + 1;
        return null;
    }

    private ChargingSessionResponse response(ChargingSession session, Integer position) {
        return response(session, position, now());
    }

    private ChargingSessionResponse response(ChargingSession session, Integer position, OffsetDateTime now) {
        return ChargingSessionResponse.from(session, position, now);
    }

    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
}
