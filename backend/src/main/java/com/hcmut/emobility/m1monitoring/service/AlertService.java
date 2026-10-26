package com.hcmut.emobility.m1monitoring.service;

import com.hcmut.emobility.m1monitoring.domain.Alert;
import com.hcmut.emobility.m1monitoring.domain.AlertType;
import com.hcmut.emobility.m1monitoring.dto.Dtos.AlertDto;
import com.hcmut.emobility.m1monitoring.repository.AlertRepository;
import com.hcmut.emobility.m1monitoring.repository.HubRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import org.springframework.http.HttpStatus;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.HubNearCapacityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Quan ly vong doi canh bao: tao, lam tuoi, dong, xac nhan, bo qua. */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alerts;
    private final HubRepository hubs;
    private final EventPublisher events;
    private final DashboardPublisher publisher;

    public AlertService(AlertRepository alerts, HubRepository hubs,
                        EventPublisher events, DashboardPublisher publisher) {
        this.alerts = alerts;
        this.hubs = hubs;
        this.events = events;
        this.publisher = publisher;
    }

    /**
     * FR-M1-07, FR-M1-08. Tao canh bao moi neu chua co canh bao cung loai dang mo.
     * BR-M1-06: da co thi chi cap nhat so lieu, KHONG tao them va KHONG phat lai su kien,
     * de M7 khong bi dội su kien trong suot thoi gian hub con vuot nguong.
     */
    @Transactional
    public Alert raiseOrRefresh(Long hubId, AlertType type, int value) {
        Optional<Alert> existing = alerts.findOpenByHubAndType(hubId, type);
        if (existing.isPresent()) {
            Alert a = existing.get();
            a.refresh(value);
            return a;
        }

        Alert created = alerts.save(Alert.raise(hubId, type, value));
        log.info("[M1] Tao canh bao {} cho hub {} voi chi so {}", type, hubId, value);

        try {
            events.publish(new HubNearCapacityEvent(hubId,
                    type == AlertType.CAPACITY ? "PARKING" : "VEHICLE", value));
        } catch (Exception ex) {
            // Canh bao da luu nen khong mat thong tin; lan danh gia sau se thu lai.
            log.error("[M1] Phat HubNearCapacityEvent that bai: {}", ex.getMessage(), ex);
        }

        publisher.publishAlert(toDto(created));
        return created;
    }

    /** FR-M1-09. Dong canh bao khi dieu kien da het (BR-M1-07). */
    @Transactional
    public void clear(Alert alert) {
        alert.resolve();
        log.info("[M1] Dong canh bao {} cua hub {}", alert.getType(), alert.getHubId());
        publisher.publishAlert(toDto(alert));
    }

    /** FR-M1-11. */
    @Transactional
    public AlertDto acknowledge(Long alertId, String username) {
        Alert alert = alerts.findById(alertId)
                .orElseThrow(() -> new BusinessException("ALERT_NOT_FOUND",
                        "Alert not found: " + alertId, HttpStatus.NOT_FOUND));
        alert.acknowledge(username);
        AlertDto dto = toDto(alert);
        publisher.publishAlert(dto);
        return dto;
    }

    /** FR-M1-11. Dismiss canh bao, bat buoc nhap ly do. */
    @Transactional
    public AlertDto dismiss(Long alertId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("REASON_REQUIRED", "Phai nhap ly do khi bo qua canh bao");
        }
        Alert alert = alerts.findById(alertId)
                .orElseThrow(() -> new BusinessException("ALERT_NOT_FOUND",
                        "Alert not found: " + alertId, HttpStatus.NOT_FOUND));
        alert.dismiss(reason);
        AlertDto dto = toDto(alert);
        publisher.publishAlert(dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<AlertDto> findOpen() {
        return alerts.findOpen().stream().map(this::toDto).toList();
    }

    /** FR-M1-10. Alert history trong khoang thoi gian. */
    @Transactional(readOnly = true)
    public List<AlertDto> findHistory(OffsetDateTime from, OffsetDateTime to, Long hubId) {
        List<Alert> rows = (hubId == null)
                ? alerts.findByRaisedAtBetweenOrderByRaisedAtDesc(from, to)
                : alerts.findByHubIdAndRaisedAtBetweenOrderByRaisedAtDesc(hubId, from, to);
        return rows.stream().map(this::toDto).toList();
    }

    public AlertDto toDto(Alert a) {
        return new AlertDto(a.getId(), a.getHubId(), hubName(a.getHubId()),
                a.getType(), a.getState(), a.getTriggerValue(), a.getRaisedAt(),
                a.getAcknowledgedBy(), a.getAcknowledgedAt(),
                a.getClosedAt(), a.getDismissReason());
    }

    private final Map<Long, String> hubNameCache = new HashMap<>();

    private String hubName(Long hubId) {
        return hubNameCache.computeIfAbsent(hubId,
                id -> hubs.findById(id).map(h -> h.getName()).orElse("Hub " + id));
    }
}
