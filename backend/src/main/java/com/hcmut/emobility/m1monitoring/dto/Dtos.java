package com.hcmut.emobility.m1monitoring.dto;

import com.hcmut.emobility.m1monitoring.domain.AlertState;
import com.hcmut.emobility.m1monitoring.domain.AlertType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Toan bo DTO cua M1 gom trong mot file cho de doi chieu voi class diagram.
 * Dung record: bat bien, Jackson tu serialize duoc.
 */
public final class Dtos {

    private Dtos() { }

    /** Ket qua tinh ty le su dung cua mot hub (BR-M1-01, BR-M1-02). */
    public record UtilisationDto(
            Integer parkingPercent,      // null khi hub khong co cho do nao
            Integer chargingPercent,     // null khi khong con tru sac hoat dong
            int freeSlots, int totalSlots,
            int freePoints, int operationalPoints,
            boolean dataAnomaly          // du lieu khong nhat quan, da ghi log
    ) { }

    public record HubSummaryDto(
            Long id, String name, String location,
            int availableVehicles,
            UtilisationDto utilisation,
            boolean hasOpenAlert
    ) { }

    public record VehicleDto(Long id, String code, String type, int batteryLevel, String state) { }

    public record SlotDto(Long id, int slotNo, String state) { }

    public record PointDto(Long id, int pointNo, String state) { }

    public record HubDetailDto(
            Long id, String name, String location,
            int availableVehicles,
            UtilisationDto utilisation,
            List<VehicleDto> vehicles,
            List<SlotDto> parkingSlots,
            List<PointDto> chargingPoints
    ) { }

    public record AlertDto(
            Long id, Long hubId, String hubName,
            AlertType type, AlertState state,
            int triggerValue,
            OffsetDateTime raisedAt,
            String acknowledgedBy, OffsetDateTime acknowledgedAt,
            OffsetDateTime closedAt, String dismissReason
    ) { }

    public record DashboardDto(
            Map<String, Long> vehicleCountByState,
            int totalHubs,
            int openAlertCount,
            List<HubSummaryDto> hubs,
            List<AlertDto> openAlerts,
            OffsetDateTime generatedAt
    ) { }

    /** Ban tin day xuong /topic/hubs khi mot hub doi trang thai (FR-M1-06). */
    public record HubStatusDto(Long hubId, int availableVehicles, UtilisationDto utilisation) { }

    public record DismissRequest(String reason) { }
}
