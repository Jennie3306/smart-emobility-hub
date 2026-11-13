package com.hcmut.emobility.m7dispatch.service;

import com.hcmut.emobility.m7dispatch.client.ExternalDtos;
import com.hcmut.emobility.m7dispatch.client.HubClient;
import com.hcmut.emobility.m7dispatch.client.VehicleClient;
import com.hcmut.emobility.m7dispatch.domain.RecommendationStatus;
import com.hcmut.emobility.m7dispatch.domain.RedistributionRecommendation;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTask;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTaskStatus;
import com.hcmut.emobility.m7dispatch.repository.RedistributionRecommendationRepository;
import com.hcmut.emobility.m7dispatch.repository.RedistributionTaskRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.contracts.ParkingDock;
import com.hcmut.emobility.shared.contracts.RedistributionRecommendations;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.RecommendationApprovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * FR-M7-02 and FR-M7-04: redistribution proposals and the Operator's decision on them.
 *
 * Proposals come from two places. M7's own rule reacts to M1's HubNearCapacityEvent (proposeForAlert);
 * the What-if module registers its simulated proposals through the shared contract (propose).
 * A proposal names hubs and a number of vehicles; approve() picks the vehicles and creates one task per vehicle
 * through RedistributionService, which checks BR-M7-01..03 for each of them.
 */
@Service
public class RecommendationService implements RedistributionRecommendations {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final EnumSet<RecommendationStatus> OPEN =
            EnumSet.of(RecommendationStatus.PENDING, RecommendationStatus.PARTIAL);
    private static final EnumSet<RedistributionTaskStatus> NOT_YET_LEFT = EnumSet.of(RedistributionTaskStatus.CREATED);

    private final RedistributionRecommendationRepository recommendations;
    private final RedistributionTaskRepository tasks;
    private final RedistributionService dispatch;
    private final VehicleClient vehicles;
    private final HubClient hubs;
    private final ParkingDock parkingDock;
    private final JdbcTemplate jdbc;
    private final EventPublisher events;

    /** Available vehicles a donor Hub keeps after a proposal, so it does not fall into a shortage itself. */
    private final int keepAtDonor;
    /** Available vehicles a short Hub should reach (M1 clears a low-vehicle alert at this level). */
    private final int targetAvailable;
    /** Parking utilisation a full Hub should fall below (M1 clears a capacity alert below this level). */
    private final int parkingTargetPercent;

    public RecommendationService(RedistributionRecommendationRepository recommendations,
                                 RedistributionTaskRepository tasks,
                                 RedistributionService dispatch,
                                 VehicleClient vehicles,
                                 HubClient hubs,
                                 ParkingDock parkingDock,
                                 JdbcTemplate jdbc,
                                 EventPublisher events,
                                 @Value("${app.dispatch.keep-at-donor:4}") int keepAtDonor,
                                 @Value("${app.dispatch.target-available:4}") int targetAvailable,
                                 @Value("${app.dispatch.parking-target-percent:75}") int parkingTargetPercent) {
        this.recommendations = recommendations;
        this.tasks = tasks;
        this.dispatch = dispatch;
        this.vehicles = vehicles;
        this.hubs = hubs;
        this.parkingDock = parkingDock;
        this.jdbc = jdbc;
        this.events = events;
        this.keepAtDonor = keepAtDonor;
        this.targetAvailable = targetAvailable;
        this.parkingTargetPercent = parkingTargetPercent;
    }

    // ------------------------------------------------------------------ FR-M7-04: proposal from an M1 alert

    /**
     * FR-M7-04 (non-interactive, trigger HubNearCapacityEvent). Runs in its own transaction after M1's alert is
     * committed, so a problem here never undoes the alert.
     *
     * VEHICLE: the Hub is short of vehicles -> bring vehicles from the Hub with the most spare available vehicles.
     * PARKING: the Hub is nearly full -> move available rental vehicles out to the Hub with the most free spaces,
     *          but never so many that the Hub itself becomes short of vehicles.
     *
     * @return the new proposal, or empty when nothing sensible can be proposed (reason is logged)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<RedistributionRecommendation> proposeForAlert(Long hubId, String resourceType, String alertRef) {
        boolean shortage = "VEHICLE".equals(resourceType);
        if (shortage ? recommendations.existsByDestinationHubIdAndStatusIn(hubId, OPEN)
                     : recommendations.existsBySourceHubIdAndStatusIn(hubId, OPEN)) {
            log.info("[M7] Hub {} already has an open proposal for {}; no new one", hubId, resourceType);
            return Optional.empty();
        }
        Map<Long, Integer> available = vehicles.availableCountPerHub();
        Proposal p = shortage ? forShortage(hubId, available) : forCrowding(hubId, available);
        if (p == null) return Optional.empty();

        RedistributionRecommendation saved = recommendations.save(RedistributionRecommendation.pending(
                RedistributionRecommendation.SOURCE_M1_ALERT, alertRef, p.from, p.to, p.amount, p.reason,
                LocalDateTime.now()));
        log.info("[M7] Proposal #{}: move {} vehicle(s) from hub {} to hub {} ({})",
                saved.getId(), p.amount, p.from, p.to, resourceType);
        return Optional.of(saved);
    }

    private record Proposal(Long from, Long to, int amount, String reason) { }

    private Proposal forShortage(Long hubId, Map<Long, Integer> available) {
        int have = available.getOrDefault(hubId, 0);
        int need = Math.max(1, targetAvailable - have);
        Long donor = null;
        int best = 0;
        for (Map.Entry<Long, Integer> e : available.entrySet()) {
            if (e.getKey().equals(hubId)) continue;
            int spare = spare(e.getKey(), e.getValue());
            if (spare > best) { best = spare; donor = e.getKey(); }
        }
        if (donor == null) {
            log.info("[M7] Hub {} is short of vehicles but no Hub can spare one (keeps {})", hubId, keepAtDonor);
            return null;
        }
        int amount = Math.min(Math.min(need, best), room(hubId));
        if (amount <= 0) {
            log.info("[M7] Hub {} is short of vehicles but has no free parking space for more", hubId);
            return null;
        }
        return new Proposal(donor, hubId, amount, "Hub has " + have + " available vehicle(s); bring " + amount
                + " so it reaches " + targetAvailable + ". The donor Hub keeps at least " + keepAtDonor + ".");
    }

    private Proposal forCrowding(Long hubId, Map<Long, Integer> available) {
        int[] spaces = parkingCounts(hubId);
        int total = spaces[0], used = spaces[1];
        if (total == 0) return null;
        int maxUsed = (int) Math.ceil(total * parkingTargetPercent / 100.0) - 1;
        int need = used - maxUsed;
        int movable = spare(hubId, available.getOrDefault(hubId, 0));
        if (need <= 0 || movable <= 0) {
            log.info("[M7] Hub {} is nearly full but no rental vehicle can leave without causing a shortage", hubId);
            return null;
        }
        Long target = null;
        int bestRoom = 0;
        for (ParkingDock.HubVacancy v : parkingDock.vacancies()) {
            if (v.hubId().equals(hubId)) continue;
            int room = room(v.hubId());
            if (room > bestRoom) { bestRoom = room; target = v.hubId(); }
        }
        if (target == null) return null;
        int amount = Math.min(Math.min(need, movable), bestRoom);
        return new Proposal(hubId, target, amount, "Hub uses " + used + " of " + total + " spaces; moving " + amount
                + " rental vehicle(s) out frees space towards " + parkingTargetPercent + "%.");
    }

    /** Available vehicles a Hub can give away: available - already promised to leave - kept. */
    private int spare(Long hubId, int availableNow) {
        long leaving = tasks.countBySourceHubIdAndStatusIn(hubId, NOT_YET_LEFT);
        return (int) (availableNow - leaving - keepAtDonor);
    }

    /** BR-M7-03: free spaces not yet promised to an incoming vehicle. */
    private int room(Long hubId) {
        return hubs.checkDestinationCapacity(hubId)
                .map(h -> h.freeSpaces() - h.incomingVehicles())
                .orElse(0);
    }

    /** [total spaces, used spaces] of M4's shared pool; read-only lookup (Appendix A, principle 5). */
    private int[] parkingCounts(Long hubId) {
        return jdbc.queryForObject("SELECT COUNT(*), COALESCE(SUM(CASE WHEN status IN ('OCCUPIED', 'RESERVED') "
                        + "THEN 1 ELSE 0 END), 0) FROM parking_space WHERE hub_id = ?",
                (rs, i) -> new int[]{rs.getInt(1), rs.getInt(2)}, hubId);
    }

    // ------------------------------------------------------------------ contract for M8

    @Override
    @Transactional
    public String propose(String source, String externalRef, long fromHubId, long toHubId, int amount, String reason) {
        if (source == null || source.isBlank() || fromHubId == toHubId || amount < 1) {
            throw new BusinessException("VALIDATION_FAILED",
                    "source, two different hubs and amount >= 1 are required", HttpStatus.BAD_REQUEST);
        }
        if (externalRef != null) {
            Optional<RedistributionRecommendation> existing = recommendations.findBySourceAndExternalRef(source, externalRef);
            if (existing.isPresent()) return String.valueOf(existing.get().getId());
        }
        requireHub(fromHubId);
        requireHub(toHubId);
        RedistributionRecommendation saved = recommendations.save(RedistributionRecommendation.pending(
                source, externalRef, fromHubId, toHubId, amount, reason, LocalDateTime.now()));
        return String.valueOf(saved.getId());
    }

    @Override
    @Transactional
    public ApprovalResult approve(String recommendationId, String approvedBy) {
        RedistributionRecommendation rec = find(recommendationId);
        if (!rec.getStatus().isOpen()) {
            throw new BusinessException("RECOMMENDATION_ALREADY_RESOLVED",
                    "Recommendation " + recommendationId + " is already " + rec.getStatus());
        }
        boolean firstApproval = rec.getStatus() == RecommendationStatus.PENDING;
        int remaining = rec.getAmount() - rec.getTaskCount();

        Set<Long> busy = new HashSet<>(tasks.findActiveVehicleIds());
        List<Long> candidates = vehicles.availableVehiclesAt(rec.getSourceHubId()).stream()
                .filter(id -> !busy.contains(id)).toList();
        // Never empty the source Hub: one available vehicle stays there.
        int limit = Math.min(remaining, Math.max(0, candidates.size() - 1));

        List<Long> created = new ArrayList<>();
        String stopReason = null;
        for (Long vehicleId : candidates) {
            if (created.size() >= limit) break;
            try {
                RedistributionTask task = dispatch.createRedistributionTask(
                        vehicleId, rec.getSourceHubId(), rec.getDestinationHubId());
                task.setRecommendationId(String.valueOf(rec.getId()));
                created.add(tasks.save(task).getTaskId());
            } catch (BusinessException ex) {
                if ("DESTINATION_FULL".equals(ex.getCode()) || "HUB_NOT_FOUND".equals(ex.getCode())) {
                    stopReason = ex.getMessage();
                    break;
                }
                log.info("[M7] Recommendation {}: vehicle {} skipped ({})", rec.getId(), vehicleId, ex.getCode());
            }
        }

        rec.setTaskCount(rec.getTaskCount() + created.size());
        String message = null;
        if (rec.getTaskCount() >= rec.getAmount()) {
            rec.setStatus(RecommendationStatus.APPROVED);
        } else {
            message = stopReason != null ? stopReason
                    : "Only " + created.size() + " of " + remaining + " vehicle(s) could be sent now: the source "
                      + "Hub keeps one available vehicle and vehicles already in a task are skipped.";
            if (rec.getTaskCount() > 0) rec.setStatus(RecommendationStatus.PARTIAL);
        }
        rec.setNote(message);
        if (!created.isEmpty()) {
            rec.setResolvedAt(LocalDateTime.now());
            rec.setResolvedBy(approvedBy);
            if (firstApproval) {
                events.publish(new RecommendationApprovedEvent(String.valueOf(rec.getId()), "REDISTRIBUTE"));
            }
        }
        recommendations.save(rec);
        log.info("[M7] Recommendation {} approved by {}: {} task(s), status {}",
                rec.getId(), approvedBy, created.size(), rec.getStatus());
        return new ApprovalResult(String.valueOf(rec.getId()), rec.getStatus().name(), created, message);
    }

    // ------------------------------------------------------------------ Operator screen

    /** FR-M7-02: the Operator rejects a proposal; no task is created. */
    @Transactional
    public RedistributionRecommendation reject(String recommendationId, String rejectedBy, String reason) {
        RedistributionRecommendation rec = find(recommendationId);
        if (rec.getStatus() != RecommendationStatus.PENDING) {
            throw new BusinessException("RECOMMENDATION_ALREADY_RESOLVED",
                    "Only a pending recommendation can be rejected; this one is " + rec.getStatus());
        }
        rec.setStatus(RecommendationStatus.REJECTED);
        rec.setResolvedAt(LocalDateTime.now());
        rec.setResolvedBy(rejectedBy);
        rec.setNote(reason == null || reason.isBlank() ? null : reason.trim());
        return recommendations.save(rec);
    }

    @Transactional(readOnly = true)
    public List<RedistributionRecommendation> list(RecommendationStatus status) {
        return status == null ? recommendations.findAllByOrderByCreatedAtDescIdDesc()
                              : recommendations.findByStatusOrderByCreatedAtDescIdDesc(status);
    }

    @Transactional(readOnly = true)
    public RedistributionRecommendation get(String recommendationId) {
        return find(recommendationId);
    }

    private RedistributionRecommendation find(String recommendationId) {
        Long id;
        try {
            id = Long.valueOf(recommendationId);
        } catch (NumberFormatException | NullPointerException ex) {
            throw notFound(recommendationId);
        }
        return recommendations.findById(id).orElseThrow(() -> notFound(recommendationId));
    }

    private void requireHub(long hubId) {
        if (hubs.checkDestinationCapacity(hubId).isEmpty()) {
            throw new BusinessException("HUB_NOT_FOUND", "Hub not found: " + hubId, HttpStatus.NOT_FOUND);
        }
    }

    private static BusinessException notFound(String id) {
        return new BusinessException("RECOMMENDATION_NOT_FOUND", "Recommendation not found: " + id, HttpStatus.NOT_FOUND);
    }
}
