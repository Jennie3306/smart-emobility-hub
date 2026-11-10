package com.hcmut.emobility.m1monitoring.service;

import com.hcmut.emobility.m1monitoring.domain.*;
import com.hcmut.emobility.m1monitoring.dto.Dtos.*;
import com.hcmut.emobility.m1monitoring.repository.AlertRepository;
import com.hcmut.emobility.m1monitoring.repository.HubRepository;
import com.hcmut.emobility.m1monitoring.repository.ResourceStateRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Noi chua business logic cua M1. Controller khong tinh toan gi, tac vu dinh ky
 * cung goi lai dung cac ham o day (UC-M1-05 duoc «include» boi bon use case khac).
 */
@Service
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    public static final String PARKING = "PARKING_SPACE";    // tu vung cua M6
    public static final String CHARGING = "CHARGING_POINT";

    private final HubRepository hubs;
    private final AlertRepository alerts;
    private final ResourceStateRepository resources;

    public MonitoringService(HubRepository hubs, AlertRepository alerts,
                             ResourceStateRepository resources) {
        this.hubs = hubs;
        this.alerts = alerts;
        this.resources = resources;
    }

    /**
     * UC-M1-05. Ty le cho do = (OCCUPIED + RESERVED) / tong so cho, lam tron xuong (BR-M1-01).
     * Ty le tru sac = IN_USE / so tru con hoat dong, lam tron xuong (BR-M1-02).
     *
     * Hub khong co cho do nao tra ve null chu khong phai 0, de man hinh hien
     * "khong co du lieu" thay vi mot so 0 gay hieu nham.
     */
    public UtilisationDto computeUtilisation(HubCounts c) {
        boolean anomaly = c.usedSlots() > c.totalSlots() || c.pointsInUse() > c.operationalPoints();
        if (anomaly) {
            log.warn("[M1] Du lieu khong nhat quan o hub {}: dung {}/{} cho, {}/{} tru",
                    c.hubId(), c.usedSlots(), c.totalSlots(), c.pointsInUse(), c.operationalPoints());
        }

        Integer parkingPercent = c.totalSlots() == 0 ? null
                : (int) Math.min(100, c.usedSlots() * 100 / c.totalSlots());
        Integer chargingPercent = c.operationalPoints() == 0 ? null
                : (int) Math.min(100, c.pointsInUse() * 100 / c.operationalPoints());

        return new UtilisationDto(parkingPercent, chargingPercent,
                (int) c.freeSlots(), (int) c.totalSlots(),
                (int) c.freePoints(), (int) c.operationalPoints(), anomaly);
    }

    /** Dem theo trang thai cho tat ca hub, mot truy van duy nhat. */
    @Transactional(readOnly = true)
    public Map<Long, HubCounts> countsPerHub() {
        Map<Long, HubCounts> result = new HashMap<>();
        for (Object[] r : resources.countByHub()) {
            long hubId = ((Number) r[0]).longValue();
            result.put(hubId, new HubCounts(hubId,
                    ((Number) r[1]).longValue(), ((Number) r[2]).longValue(),
                    ((Number) r[3]).longValue(), ((Number) r[4]).longValue(),
                    ((Number) r[5]).longValue(), ((Number) r[6]).longValue()));
        }
        return result;
    }

    /** UC-M1-01. Danh sach hub kem so lieu, dung cho man hinh sinh vien. */
    @Transactional(readOnly = true)
    public List<HubSummaryDto> getHubSummaries() {
        Map<Long, Integer> available = availableVehiclesPerHub();
        Map<Long, HubCounts> counts = countsPerHub();
        Set<Long> hubsWithAlert = alerts.findOpen().stream()
                .map(Alert::getHubId).collect(Collectors.toSet());

        return hubs.findAllByOrderByIdAsc().stream()
                .map(h -> new HubSummaryDto(
                        h.getId(), h.getName(), h.getLocation(),
                        available.getOrDefault(h.getId(), 0),
                        computeUtilisation(counts.getOrDefault(h.getId(), HubCounts.empty(h.getId()))),
                        hubsWithAlert.contains(h.getId())))
                .toList();
    }

    /** UC-M1-02. Chi tiet mot hub. */
    @Transactional(readOnly = true)
    public HubDetailDto getHubDetail(Long hubId) {
        Hub hub = hubs.findById(hubId)
                .orElseThrow(() -> new BusinessException("HUB_NOT_FOUND",
                        "Hub not found: " + hubId, HttpStatus.NOT_FOUND));

        List<VehicleDto> vehicles = hubs.findVehiclesOfHub(hubId).stream()
                .map(r -> new VehicleDto(
                        ((Number) r[0]).longValue(), (String) r[1], (String) r[2],
                        ((Number) r[3]).intValue(), (String) r[4]))
                .toList();

        List<SlotDto> slots = resources.findOfHub(hubId, PARKING).stream()
                .map(s -> new SlotDto(s.getResourceId(), s.getResourceId().intValue(), s.getState()))
                .toList();

        List<PointDto> points = resources.findOfHub(hubId, CHARGING).stream()
                .map(p -> new PointDto(p.getResourceId(), p.getResourceId().intValue(), p.getState()))
                .toList();

        int availableVehicles = (int) vehicles.stream()
                .filter(v -> "AVAILABLE".equals(v.state())).count();

        HubCounts c = countsPerHub().getOrDefault(hubId, HubCounts.empty(hubId));

        return new HubDetailDto(hub.getId(), hub.getName(), hub.getLocation(),
                availableVehicles, computeUtilisation(c), vehicles, slots, points);
    }

    /**
     * UC-M1-04. Mot lan goi tra ve du ba vung cua dashboard, thay vi ba lan goi:
     * so lan di lai giam, va ba vung chac chan lay tu cung mot lan doc.
     */
    @Transactional(readOnly = true)
    public DashboardDto getDashboard(List<AlertDto> openAlerts) {
        List<HubSummaryDto> summaries = getHubSummaries();

        Map<String, Long> byState = new LinkedHashMap<>();
        for (Object[] row : hubs.findVehicleCountByState()) {
            byState.put((String) row[0], ((Number) row[1]).longValue());
        }

        return new DashboardDto(byState, summaries.size(), openAlerts.size(),
                summaries, openAlerts, OffsetDateTime.now());
    }

    /** So xe kha dung theo hub (BR-M1-08). */
    @Transactional(readOnly = true)
    public Map<Long, Integer> availableVehiclesPerHub() {
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : hubs.countAvailableVehiclesPerHub()) {
            result.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return result;
    }

    /** Du lieu day xuong dashboard khi mot hub doi trang thai. */
    @Transactional(readOnly = true)
    public Optional<HubStatusDto> buildHubStatus(Long hubId) {
        if (!hubs.existsById(hubId)) return Optional.empty();
        HubCounts c = countsPerHub().getOrDefault(hubId, HubCounts.empty(hubId));
        return Optional.of(new HubStatusDto(hubId,
                availableVehiclesPerHub().getOrDefault(hubId, 0),
                computeUtilisation(c)));
    }
}
